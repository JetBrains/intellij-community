VS sln builds .dll files from .cs files.
Make sure .net version is the same as one used by IronPython.
AfterBuild action deletes .pdb files.

Build with Studio or with cmd: "msbuild dllSources.sln  /t:Rebuild /p:Configuration=Release"