// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.model

import kotlin.reflect.KClass

interface TSuite {
    val abstractTestClass: Class<*>
    val generatedClassName: String

    /**
     * suite is common for all variations of plugin
     */
    val commonSuite: Boolean

    val models: List<TModel>
    val annotations: List<TAnnotation>
    val imports: List<String>
}

interface MutableTSuite : TSuite {
    override val models: MutableList<TModel>
    override val annotations: MutableList<TAnnotation>
    override val imports: MutableList<String>
}

class TSuiteImpl(override val abstractTestClass: Class<*>, override val generatedClassName: String, override val commonSuite: Boolean) : MutableTSuite {
    override val models = mutableListOf<TModel>()
    override val annotations = mutableListOf<TAnnotation>()
    override val imports = mutableListOf<String>()

    init {
      check(generatedClassName.indexOf('.') > 0) {
          "package name is missed: generatedClassName should be a fully qualified name"
      }
    }
}

inline fun <reified T: Any> MutableTGroup.testClass(
    generatedClassName: String = getDefaultSuiteTestClassName(T::class.java),
    commonSuite: Boolean = true,
    block: MutableTSuite.() -> Unit
) {
    suites += TSuiteImpl(T::class.java, generatedClassName, commonSuite).apply(block)
}

fun MutableTGroup.testClass(
    clazz: KClass<*>,
    generatedClassName: String = getDefaultSuiteTestClassName(clazz.java),
    commonSuite: Boolean = true,
    block: MutableTSuite.() -> Unit
) {
    suites += TSuiteImpl(clazz.java, generatedClassName, commonSuite).apply(block)
}

@PublishedApi
internal fun getDefaultSuiteTestClassName(clazz: Class<*>): String {
    val packageName = clazz.`package`.name
    val simpleName = clazz.simpleName

    require(simpleName.startsWith("Abstract")) { "Doesn't start with \"Abstract\": $simpleName" }
    return packageName + '.' + simpleName.substringAfter("Abstract") + "Generated"
}