How to run Python Env Tests:

1. Setup environments (in Unix-like OS):
    1. Go to Gradle script folder: `cd intellij/community/python/setup-test-environment/`
    2. Specify directory for building environments: `export ORG_GRADLE_PROJECT_buildDir=<path to build dir>`
    3. Build python environments: `./gradlew -b build.gradle build`

2. Setup run configuration:
    1. Find saved run configuration Python Tests -> PyEnvTests
    2. Define env variable `PYCHARM_PYTHONS=<path to build dir>/pythons` (dir was specified in 1.2)
    (for example, `PYCHARM_PYTHONS=/home/user/work/testenvs/pythons`)
      
    or:
    3. Define env variable `PYCHARM_PYTHON_ENVS` and provide absolute paths to python executables with your os path separator 
    (for example, `PYCHARM_PYTHON_ENVS=/home/user/.virtualenvs/py27/bin/python:/home/user/.virtualenvs/py36/bin/python`)

3. Run `PyEnvTests` run configuration
