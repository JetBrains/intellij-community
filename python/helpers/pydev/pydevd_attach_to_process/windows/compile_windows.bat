call "C:\Program Files (x86)\Microsoft Visual Studio 14.0\VC\vcvarsall.bat" x86
cl -DUNICODE -D_UNICODE /EHsc /Zi /O1 /LD attach.cpp /link /DEBUG /OPT:REF /OPT:ICF /out:attach_x86.dll
copy attach_x86.dll ..\attach_x86.dll /Y
copy attach_x86.pdb ..\attach_x86.pdb /Y



call "C:\Program Files (x86)\Microsoft Visual Studio 14.0\VC\vcvarsall.bat" x86_amd64
cl -DUNICODE -D_UNICODE /EHsc /Zi /O1 /W3 /LD attach.cpp /link /DEBUG /OPT:REF /OPT:ICF /out:attach_amd64.dll
copy attach_amd64.dll ..\attach_amd64.dll /Y
copy attach_amd64.pdb ..\attach_amd64.pdb /Y

del *.lib
del *.obj
del *.pdb
del *.dll
del *.exp