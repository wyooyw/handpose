set PACKAGE_ROOT_PATH=D:\SourceCode\mindspore\pretrain\handpose\mindspore-lite-1.6.1-win-x64
set PATH=%PACKAGE_ROOT_PATH%\tools\converter\lib;%PATH%
cd D:\SourceCode\mindspore\pretrain\handpose\mindspore-lite-1.6.1-win-x64\tools\converter\converter
call converter_lite --fmk=MINDIR --modelFile=D:\SourceCode\mindspore\pretrain\handpose\mobilenet_v2_1.0_224.mindir --outputFile=D:\SourceCode\mindspore\pretrain\handpose\mobilenet_handpose --inputDataFormat=NCHW
cd ../../../../