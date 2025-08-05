VM options to run tests locally

```
-Djps.kotlin.home=system/test/kotlin-dist-for-ide/kotlinc-dist-for-ide-from-sources
-Djps.testData.home=community/out/kotlinc-jps-testdata/jps/jps-plugin/testData/
-Dkombo.compiler.tests.mode=true
```

Add the options to the run configuration created from launching compiler tests.
If there is no run gutter in a Kotlin test file you wish to run, try searching for a Java test in the same library instead.
Then replace the qualified name of the test with the desired one.

For using kotlinc version as JPS version change the value of the JPS version property in the `model.properties` file from number to `dev`:
```
jpsPluginVersion=dev
```
After changing the version, regenerate the versions in XMLs by running the `Update Kotlin Compiler Version` run configuration.
This mode is used in bootstrap JPS tests in the Aggregator.

You might need to change the working directory of the run configuration for correct test data detection.
If the working directory of the run configuration is set to module (`$MODULE_WORKING_DIR$`),
it should be replaced with the project root (`$PROJECT_DIR$`).
