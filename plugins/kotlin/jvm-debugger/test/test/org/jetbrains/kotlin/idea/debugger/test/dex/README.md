This package contains tests that emulate the debug information for `.dex` and `.class` files. 
The generator for these tests can be found here:
- https://github.com/sfs/kotlin-debug-info-extractor
- https://github.com/nikita-nazarov/kotlin-debug-info-extractor (a fork)

Currently, we can't test debugger features on android automatically, because to do so we would have to run 
an emulator locally, launch an app and run a debug session there, which is very tricky to do. 
These tests serve as a workaround for this issue.  

