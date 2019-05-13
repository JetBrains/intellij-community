call "C:\Program Files (x86)\Microsoft Visual Studio 14.0\VC\vcvarsall.bat" x86
cl -DUNICODE -D_UNICODE /EHsc /LD attach.cpp /link /out:attach_x86.dll
copy attach_x86.dll ..\attach_x86.dll /Y



call "C:\Program Files (x86)\Microsoft Visual Studio 14.0\VC\vcvarsall.bat" x86_amd64
cl -DUNICODE -D_UNICODE /EHsc /LD attach.cpp /link /out:attach_amd64.dll
copy attach_amd64.dll ..\attach_amd64.dll /Y