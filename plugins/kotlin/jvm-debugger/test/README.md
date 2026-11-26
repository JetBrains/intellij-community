# Kotlin debugger tests

This module contains generated tests for Kotlin debugger. 
Tests design lets one add debugger markup directly in a code sample. 
For example, breakpoint installation is done with `//Breakpoint!` marker on the line previous to the target one.

Tests are separated into different categories depending on tested functionality, e.g. stepping, evaluation, smart-step-into targets, local variables, highlighting, etc.

To create a new test:
1. Choose the target functionality to test and the corresponding subdirectory inside `testData` folder
2. Create a `<FILE_NAME>.kt` file with the code sample to test
3. Add the markup specific for the target functionality. Check the available capabilities in the nearby files.
4. Create a `<FILE_NAME>.out` file with the expected output. Check the expected output format in the nearby files.
5. Start `Generate Kotlin Tests (K1 + K2)` run configuration

## Markup

### Stepping

1. `//Breakpoint!` installs a breakpoint to the next line. Breakpoint can be configured further:
   1. `lambdaOrdinal=N` -- breakpoint to the `N`th lambda
      1. `lambdaOrdinal=-1` -- basic line breakpoint (no suspension in lambdas)
   2. `condition=<EXPRESSION>` -- conditional breakpoints
   3. See all capabilities in [BreakpointCreator.kt](test/org/jetbrains/kotlin/idea/debugger/test/util/BreakpointCreator.kt)
2. Stepping commands are executed on suspension points. The commands are executed in the order they appear in the file.
   1. Basic stepping commands can be called several times: `// <COMMAND>: N`
      1. `RESUME`, `STEP_INTO`, `STEP_OVER`, `STEP_OUT`
   2. Smart step into `// SMART_STEP_INTO_BY_INDEX: N` steps into `N`th stepping target
   3. See all commands in [SteppingInstruction.kt](test/org/jetbrains/kotlin/idea/debugger/test/util/SteppingInstruction.kt)

### Evaluation
Evaluation commands are executed in the order they appear in the file.

1. Expression to evaluate is stated via `// EXPRESSION: <EXPR>` command
2. The expected results should follow the expression `// RESULT: <RES>: <TYPE>`
3. See [AbstractIrKotlinEvaluateExpressionTest.kt](test/org/jetbrains/kotlin/idea/debugger/test/AbstractIrKotlinEvaluateExpressionTest.kt) for details

### Adding libraries to the classpath
Use `// ATTACH_LIBRARY: maven(<LIB>)` to add a library to the classpath. (deprecated)

For example,  `// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4)` adds coroutines (deprecated)

Use `// ATTACH_LIBRARY_BY_LABEL: classes(<bazel-label>)` to add a library to the classpath.

For example, `// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.10.2.jar)`
Note that it doesn't add transitive dependencies, and you have to attach all the required libs.

### Adding javaagent
Use `// ATTACH_JAVA_AGENT_BY_LABEL:` with the same syntax as in `// ATTACH_LIBRARY_BY_LABEL:`

For the coroutine debug agent, attach `kotlinx-coroutines-core-jvm-<version>.jar`.
The IDE attaches the same jar, and the debugger reads `DebugProbesImpl` from it.
The agent jar also goes to the classpath, so `// ATTACH_LIBRARY_BY_LABEL:` for the same jar is not necessary.

Two other jars do not work:
1. `kotlinx-coroutines-core-<version>.jar` has only the multiplatform metadata. A test with only this jar does not compile.
2. `kotlinx-coroutines-debug-1.10.2.jar` has no agent class. Its manifest points to `AgentPremain` in the core jar.
   It adds the `DebugProbes` API, JUnit timeouts and BlockHound support.
   It also needs `byte-buddy`, `byte-buddy-agent`, `jna` and `jna-platform`, and the test framework adds no transitive dependencies.

Old tests used `kotlinx-coroutines-debug-1.3.8.jar`. It had `byte-buddy` inside, so the core jar was enough next to it.

### Setting a system property of the debuggee
Use `// JVM_SYSTEM_PROPERTY: <key>=<value>`. It adds `-D<key>=<value>` to the debuggee JVM.

The directive came with the move of the coroutine tests from kotlinx-coroutines 1.3.8 to 1.10.2.
Two library changes matter for the tests:
1. Since 1.8.0, the agent records no creation stack traces by default ([#3783](https://github.com/Kotlin/kotlinx.coroutines/issues/3783)).
   A test that checks the `Coroutine Creation Stack Trace` section turns them on:
   ```
   // ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
   // JVM_SYSTEM_PROPERTY: kotlinx.coroutines.debug.enable.creation.stack.trace=true
   ```
2. Since 1.7.2, the agent ignores a coroutine with `EmptyCoroutineContext` ([#3782](https://github.com/Kotlin/kotlinx.coroutines/issues/3782)).
   No property turns this off.
   So `suspend fun main` has no creation stack trace, because its root continuation has the empty context.
   See `xcoroutines/suspendMain.kt`.

### Multifile

Use `// FILE: <FILE_NAME>` directive to split a file into several files. You can use Kotlin or Java files.
