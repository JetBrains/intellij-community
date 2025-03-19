// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.testGenerator.generator.methods

import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import org.jetbrains.kotlin.testGenerator.generator.Code
import org.jetbrains.kotlin.testGenerator.generator.TestMethod
import org.jetbrains.kotlin.testGenerator.generator.appendAnnotation
import org.jetbrains.kotlin.testGenerator.generator.appendBlock
import org.jetbrains.kotlin.testGenerator.model.TAnnotation

class GetTestPlatformMethod(private val platform: KMPTestPlatform) : TestMethod {
    override val methodName = "getTestPlatform"

    init {
        require(platform.isSpecified)
    }

    override fun Code.render() {
        appendAnnotation(TAnnotation<Override>(), useQualifiedName = true)
        appendBlock("public KMPTestPlatform $methodName()") {
            append("return ${KMPTestPlatform::class.simpleName}.${platform};")
        }
    }
}