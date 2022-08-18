// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package property


fun main() {
    A("OK")
}

//FunctionBreakpoint!
class A(val a: String)


// EXPRESSION: a
// RESULT: "OK": Ljava/lang/String;
