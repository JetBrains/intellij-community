### Starter for IntelliJ IDEA based IDE's


#### Overview

In general - this package helps you write tests/code, that will start IDE from installer in external process.
Aside from that, you may find useful functionality as below:

* execution commands in plugins (list of available commands described below)
* implementing your custom command to be invoked later in tests
* execution custom code (though, you cannot use external libraries here)
* integration with CI (optional)
* collecting test artifacts
* reporting of artifacts to CI (optional)
* run a test with profiler (not yet included)


#### Supported products

* IDEA
* GoLand
* WebStorm
* PhpStorm
* DataGrip
* PyCharm
* RubyMine
* Android Studio


##### How to setup

Configure maven repositories in your `build.gradle` file

```
repositories {
  maven { url = "https://cache-redirector.jetbrains.com/maven-central" }
  maven { url = "https://cache-redirector.jetbrains.com/intellij-dependencies" }

  maven { url = "https://www.jetbrains.com/intellij-repository/releases" }
}
```

Instead of `maven { url = "https://cache-redirector.jetbrains.com/maven-central" }` you may use `mavenCentral()`

If you're sure, that you need more recent version of packages, you might use
`maven { url = "https://www.jetbrains.com/intellij-repository/snapshots" }`
OR
`maven { url = "https://www.jetbrains.com/intellij-repository/nightly" }`

But don't forget to change accordingly version of the packages as such:
* nightly -> LATEST-TRUNK-SNAPSHOT
* snapshots -> LATEST-EAP-SNAPSHOT
* releases -> semver package version

Minimal setup example:

```
dependencies {
  testImplementation("com.jetbrains.intellij.ide:ide-starter:LATEST-EAP-SNAPSHOT")
  testImplementation("com.jetbrains.intellij.performanceTesting:performance-testing-commands:LATEST-EAP-SNAPSHOT")
  
  testImplementation("junit:junit:4.13.2")
}
```

To make sure, that you will not get problem with Kotlin Duration and Kotlin Path API, add the following

```
compileTestKotlin {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11

  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_11.toString()
    freeCompilerArgs += [
      "-Xopt-in=kotlin.io.path.ExperimentalPathApi",
      "-Xopt-in=kotlin.time.ExperimentalTime"
    ]
  }
}

```



##### Run with JUnit4

[Example of simple test, that will download IntelliJ IDEA and start import of gradle project](https://github.com/JetBrains/intellij-community/tree/master/tools/intellij.ide.starter/testSrc/com/intellij/ide/starter/tests/examples)

Don't forget to create appropriate classes for JUnit4StarterRule, IdeaCases.

##### Run with JUnit5

The key difference between running tests on JUnit4 and JUnit5 here will be migration from ExternalResource (in JUnit4) to TempDir (in JUnit5).  
Though, for now we don't have an example for JUnit5StarterRule.

[JUnit4StarterRule implementation example is here](https://github.com/JetBrains/intellij-community/blob/master/tools/intellij.ide.starter/testSrc/com/intellij/ide/starter/tests/examples/junit4/JUnit4StarterRule.kt)



##### Available commands from plugins

Dependency `performance-testing-commands`
- waitForSmartMode()
- flushIndexes()
- setupProjectSdk(sdkName: String, sdkType: String, sdkPath: String)
- setupProjectSdk(sdkObject: SdkObject)
- setupProjectJdk(sdkName: String, sdkPath: String) = setupProjectSdk(sdkName, "JavaSDK", sdkPath)
- openFile(relativePath: String)
- openProject(projectPath: Path)
- reopenProject()
- goto(line: String, column: String)
- findUsages()
- inspectCode()
- checkOnRedCode()
- exitApp(forceExit: Boolean = true)
- exitAppWithTimeout(timeoutInSeconds: Long)
- memoryDump()
- dumpProjectFiles()
- compareProjectFiles(firstDir: String, secondDir: String)
- cleanCaches()
- doComplete()
- doComplete(times: Int)
- openProjectView()
- pressKey(key: String)
- delayType(command: String)
- doLocalInspection()
- altEnter(intention: String)
- callAltEnter(times: Int, intention: String = "")
- createAllServicesAndExtensions()
- runConfiguration(command: String)
- openFileWithTerminate(relativePath: String, terminateIdeInSeconds: Long)
- searchEverywhere(text: String)
- storeIndices()
- compareIndices()
- recoveryAction(action: RecoveryActionType)

Dependency `performance-testing-maven-commands`
- importMavenProject()

Dependency `performance-testing-gradle-commands`
- importGradleProject()

#### What behaviour might be extended / modified

Everything, that initializes via DI framework (Kodein DI) might be modified in your code for your need.   
[DI container initialization](https://github.com/JetBrains/intellij-community/blob/master/tools/intellij.ide.starter/src/com/intellij/ide/starter/di/diContainer.kt)  
For example, you might write your own implementation of CIServer and provide it via DI.

E.g:
```
di = DI {
      extend(di)
      bindSingleton<CIServer>(overrides = true) { YourImplementationOfCI() }
}
```