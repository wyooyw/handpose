import mindspore.dataset as ds
import mindspore.dataset.vision.c_transforms as transforms
from mindvision.dataset import DownLoad
import mindspore.nn as nn
from mindspore import Model
from mindspore import load_checkpoint, load_param_into_net

from mindvision.classification.models import mobilenet_v2
from mindvision.engine.loss import CrossEntropySmooth
from mindvision.engine.callback import ValAccMonitor
from mindspore.train.callback import TimeMonitor
import matplotlib.pyplot as plt
import numpy as np
from PIL import Image
from mindspore import export, Tensor

def create_dataset(path, batch_size=10, train=True, image_size=224):
    dataset = ds.ImageFolderDataset(path, num_parallel_workers=4, class_indexing={"ok": 0, "thumbup": 1})
    # 图像增强
    mean = [0.485 * 255, 0.456 * 255, 0.406 * 255]
    std = [0.229 * 255, 0.224 * 255, 0.225 * 255]
    if train:
        trans = [
            transforms.RandomCropDecodeResize(image_size, scale=(0.08, 1.0), ratio=(0.75, 1.333)),
            transforms.RandomHorizontalFlip(prob=0.5),
            transforms.Normalize(mean=mean, std=std),
            transforms.HWC2CHW()
        ]
    else:
        trans = [
            transforms.Decode(),
            transforms.Resize(256),
            transforms.CenterCrop(image_size),
            transforms.Normalize(mean=mean, std=std),
            transforms.HWC2CHW()
        ]

    dataset = dataset.map(operations=trans, input_columns="image", num_parallel_workers=4)
    dataset = dataset.batch(batch_size, drop_remainder=True)
    return dataset

# 加载训练集和验证集
train_path = "./datasets/handpose/train"
val_path = "./datasets/handpose/val"
dataset_train = create_dataset(train_path, train=True)
dataset_val = create_dataset(val_path, train=False)

# 获取预训练模型
models_url = "https://download.mindspore.cn/vision/classification/mobilenet_v2_1.0_224.ckpt"
dl = DownLoad()

# 创建模型
network = mobilenet_v2(num_classes=2, resize=224)
param_dict = load_checkpoint("./mobilenet_v2_1.0_224.ckpt")

# 删除最后一层参数
filter_list = [x.name for x in network.head.classifier.get_parameters()]
def filter_ckpt_parameter(origin_dict, param_filter):
    for key in list(origin_dict.keys()):
        for name in param_filter:
            if name in key:
                print("Delete parameter from checkpoint: ", key)
                del origin_dict[key]
                break
filter_ckpt_parameter(param_dict, filter_list)

# 加载预训练模型参数作为网络初始化权重
load_param_into_net(network, param_dict)

# 定义优化器
network_opt = nn.Momentum(params=network.trainable_params(), learning_rate=0.01, momentum=0.9)

# 定义损失函数
network_loss = CrossEntropySmooth(sparse=True, reduction="mean", smooth_factor=0.1, classes_num=2)

# 定义评价指标
metrics = {"Accuracy": nn.Accuracy()}

# 初始化模型
model = Model(network, loss_fn=network_loss, optimizer=network_opt, metrics=metrics)



num_epochs = 10

# 训练完成后保存验证精度最高的ckpt文件到当前目录下
model.train(num_epochs,
            dataset_train,
            callbacks=[ValAccMonitor(model, dataset_val, num_epochs), TimeMonitor()])


def visualize_model(path):
    image = Image.open(path).convert("RGB")
    image = image.resize((224, 224))
    plt.imshow(image)

    # 归一化处理
    mean = np.array([0.485 * 255, 0.456 * 255, 0.406 * 255])
    std = np.array([0.229 * 255, 0.224 * 255, 0.225 * 255])
    image = np.array(image)
    image = (image - mean) / std
    image = image.astype(np.float32)

    image = np.transpose(image, (2, 0, 1))
    image = np.expand_dims(image, axis=0)

    # 定义并加载网络
    net = mobilenet_v2(num_classes=2, resize=224)
    param_dict = load_checkpoint("./best.ckpt")
    load_param_into_net(net, param_dict)
    model = Model(net)

    # 模型预测
    pre = model.predict(Tensor(image))
    result = np.argmax(pre)

    class_name = {0: "ok", 1: "thumbup"}
    plt.title(f"Predict: {class_name[result]}")
    return result

image1 = "./datasets/handpose/infer/gesture-ok-2021-03-07_23-07-50-26_34112.jpg"

plt.figure(figsize=(15, 7))
plt.subplot(1, 2, 1)
visualize_model(image1)

image2 = "./datasets/handpose/infer/gesture-thumbUp-2021-03-07_23-07-54-28_22014.jpg"

plt.subplot(1, 2, 2)
visualize_model(image2)

plt.show()

# 模型导出为mindir格式
net = mobilenet_v2(num_classes=2, resize=224)
param_dict = load_checkpoint("best.ckpt")
load_param_into_net(net, param_dict)

# 将模型由ckpt格式导出为MINDIR格式
input_np = np.random.uniform(0.0, 1.0, size=[1, 3, 224, 224]).astype(np.float32)
export(net, Tensor(input_np), file_name="mobilenet_v2_1.0_224", file_format="MINDIR")