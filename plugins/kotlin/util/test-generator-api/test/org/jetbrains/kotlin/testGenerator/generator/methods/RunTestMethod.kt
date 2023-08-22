// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.generator.methods

import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.testGenerator.generator.Code
import org.jetbrains.kotlin.testGenerator.generator.TestMethod
import org.jetbrains.kotlin.testGenerator.generator.appendBlock
import org.jetbrains.kotlin.testGenerator.model.TModel

class RunTestMethod(private val model: TModel) : TestMethod {
    override val methodName = "runTest"

    override fun Code.render() {
        appendBlock("private void $methodName(String testDataFilePath) throws Exception") {
            val args = mutableListOf<String>()

            args += "this::${model.testMethodName}"
            args += "this"

            if (model.targetBackend != TargetBackend.ANY) {
                args += TargetBackend::class.java.simpleName + "." + model.targetBackend.name
            }

            args += "testDataFilePath"

            append("KotlinTestUtils.runTest(${args.joinToString()});")
        }
    }
}