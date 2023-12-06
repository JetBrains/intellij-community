// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.module.Module
import com.intellij.util.SmartFMap

interface TestFixtureExtension {
    fun setUp(module: Module)
    fun tearDown()

    companion object {
        @Volatile
        private var instances = SmartFMap.emptyMap<String, TestFixtureExtension>()

        fun loadFixture(className: String, module: Module): TestFixtureExtension {
            instances[className]?.let { return it }

            return (Class.forName(className).getDeclaredConstructor().newInstance() as TestFixtureExtension).apply {
                this.setUp(module)
                instances = instances.plus(className, this)
            }
        }

        inline fun <reified T : TestFixtureExtension> loadFixture(module: Module) = loadFixture(T::class.qualifiedName!!, module) as T

        fun getFixture(className: String) = instances[className]

        inline fun <reified T : TestFixtureExtension> getFixture() = getFixture(T::class.qualifiedName!!) as? T

        fun unloadFixture(className: String) {
            instances[className]?.let {
                it.tearDown()
                instances = instances.minus(className)
            }
        }

        inline fun <reified T : TestFixtureExtension> unloadFixture() = unloadFixture(T::class.qualifiedName!!)
    }
}