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

For example, `// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps_org_jetbrains_kotlinx_kotlinx_coroutines_core_1_10_2//file:kotlinx_coroutines_core_1_10_2.jar)`
Note that it doesn't add transitive dependencies, and you have to attach all the required libs.

### Adding javaagent
Use `// ATTACH_JAVA_AGENT_BY_LABEL:` with the same syntax as in `// ATTACH_LIBRARY_BY_LABEL:`

### Multifile

Use `// FILE: <FILE_NAME>` directive to split a file into several files. You can use Kotlin or Java files.
