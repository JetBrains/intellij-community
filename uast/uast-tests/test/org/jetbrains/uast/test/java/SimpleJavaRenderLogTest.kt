/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.test.java

import org.junit.Test

class SimpleJavaRenderLogTest : AbstractJavaRenderLogTest() {
    @Test fun testDataClass() = doTest("DataClass/DataClass.java")

    @Test fun testEnumSwitch() = doTest("Simple/EnumSwitch.java")

    @Test fun testLocalClass() = doTest("Simple/LocalClass.java")

    @Test fun testReturnX() = doTest("Simple/ReturnX.java")

    @Test fun testJava() = doTest("Simple/Simple.java")

    @Test fun testClass() = doTest("Simple/SuperTypes.java")

    @Test fun testTryWithResources() = doTest("Simple/TryWithResources.java")

    @Test fun testEnumValueMembers() = doTest("Simple/EnumValueMembers.java")

    @Test fun testQualifiedConstructorCall() = doTest("Simple/QualifiedConstructorCall.java")

    @Test fun testAnonymousClassWithParameters() = doTest("Simple/AnonymousClassWithParameters.java")

    @Test fun testVariableAnnotation() = doTest("Simple/VariableAnnotation.java")
}
