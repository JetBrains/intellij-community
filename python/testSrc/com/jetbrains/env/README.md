# Python environment tests (they are using real interpreters)
See https://confluence.jetbrains.com/display/PYINT/PyCharm+tests+on+TeamCity

## Layout
* ``debug`` tests for debugger
* ``python`` all other tests

## How to run Python Env Tests:

1. Setup environments:

    On `Unix`-like operating systems:
    * Go to Gradle script folder: `cd intellij/community/python/setup-test-environment/`
    * Specify directory for building environments: `export ORG_GRADLE_PROJECT_buildDir=<path to build dir>`
    * Check value: `echo $ORG_GRADLE_PROJECT_buildDir`
    * Build python environments: `./gradlew -b build.gradle build`
    
    On `Windows`:
    * Go to Gradle script folder: `cd intellij/community/python/setup-test-environment/`
    * Specify directory for building environments: `set ORG_GRADLE_PROJECT_buildDir <path to build dir>`
    * Check value: `echo %ORG_GRADLE_PROJECT_buildDir%`
    * Build python environments: `gradlew.bat -b build.gradle build`
    
2. Setup run configuration:
    * Find saved run configuration Python Tests -> PyEnvTests
    * Define env variable `PYCHARM_PYTHONS=<path to build dir>/pythons` (defined in `ORG_GRADLE_PROJECT_buildDir`)
    (for example, `PYCHARM_PYTHONS=/home/user/work/testenvs/pythons`)
      
    or:
    * Define env variable `PYCHARM_PYTHON_ENVS` and provide absolute paths to python executables with your os path separator 
    (for example, `PYCHARM_PYTHON_ENVS=/home/user/.virtualenvs/py27/bin/python:/home/user/.virtualenvs/py36/bin/python`)

3. Run `PyEnvTests` run configuration
