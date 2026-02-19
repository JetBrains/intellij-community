// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.testGenerator.generator.methods

import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.testGenerator.generator.Code
import org.jetbrains.kotlin.testGenerator.generator.TestMethod
import org.jetbrains.kotlin.testGenerator.generator.appendAnnotation
import org.jetbrains.kotlin.testGenerator.generator.appendBlock
import org.jetbrains.kotlin.testGenerator.generator.appendModifiers
import org.jetbrains.kotlin.testGenerator.model.TAnnotation
import javax.lang.model.element.Modifier

internal class KotlinPluginModeMethod(
    private val pluginMode: KotlinPluginMode,
) : TestMethod {

    override val methodName: String
        get() = "getPluginMode"

    override fun Code.render() {
        val classSimpleName = KotlinPluginMode::class.simpleName

        // do not add imports due to local name collisions
        appendAnnotation(TAnnotation<Override>(), useQualifiedName = true)
        appendAnnotation(TAnnotation<NotNull>(), useQualifiedName = true)

        appendModifiers(setOf(Modifier.PUBLIC, Modifier.FINAL))
        appendBlock("$classSimpleName $methodName()") {
            append("return $classSimpleName.${pluginMode.name};")
        }
    }
}