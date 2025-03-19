// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.model

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.kotlin.idea.test.kmp.KMPTest
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import kotlin.reflect.KClass

interface TSuite {
    val abstractTestClass: Class<*>
    val defaultGeneratedClassFqName: String

    val platforms: List<KMPTestPlatform>

    val generatedPackagePostfix: String?

    val generatedClassPackage: String
        get() {
            val defaultPackage = defaultGeneratedClassFqName.substringBeforeLast('.')
            generatedPackagePostfix?.let { return "$defaultPackage.$it" }
            return defaultPackage
        }

    fun generatedClassFqName(platform: KMPTestPlatform): String {
        return "$generatedClassPackage.${generatedClassShortName(platform)}"
    }

    fun generatedClassShortName(platform: KMPTestPlatform): String {
        val shortName = defaultGeneratedClassFqName.substringAfterLast('.')
        if (platform == KMPTestPlatform.Unspecified) return shortName
        return platform.name + shortName
    }

    /**
     * suite is common for all variations of plugin
     */
    val commonSuite: Boolean

    val models: List<TModel>
    val annotations: List<TAnnotation>
    val imports: Set<String>

    val indexingMode: List<IndexingMode>
}

interface MutableTSuite : TSuite {
    override val models: MutableList<TModel>
    override val annotations: MutableList<TAnnotation>
    override val imports: MutableSet<String>
}

class TSuiteImpl(
    override val abstractTestClass: Class<*>,
    override val defaultGeneratedClassFqName: String,
    override val commonSuite: Boolean,
    override val indexingMode: List<IndexingMode>,
    override val platforms: List<KMPTestPlatform>,
    override val generatedPackagePostfix: String?,
) : MutableTSuite {
    init {
        require(platforms.isNotEmpty())

        if (platforms.any { it.isSpecified }) {
            require(KMPTest::class.java.isAssignableFrom(abstractTestClass)) {
                "To use ${KMPTestPlatform::class}, the $abstractTestClass should implement ${KMPTest::class}"
            }
        }
    }

    override val models: MutableList<TModel> = mutableListOf<TModel>()
    override val annotations: MutableList<TAnnotation> = mutableListOf<TAnnotation>()
    override val imports: MutableSet<String> = mutableSetOf<String>()

    init {
      check(defaultGeneratedClassFqName.indexOf('.') > 0) {
          "package name is missed: generatedClassName should be a fully qualified name"
      }
    }
}

inline fun <reified T: Any> MutableTGroup.testClass(
    generatedClassName: String = getDefaultSuiteTestClassName(T::class.java),
    commonSuite: Boolean = true,
    indexingMode: List<IndexingMode> = emptyList(),
    platforms: List<KMPTestPlatform> = listOf(KMPTestPlatform.Unspecified),
    generatedPackagePostfix: String? = null,
    block: MutableTSuite.() -> Unit
) {
    suites += TSuiteImpl(T::class.java, generatedClassName, commonSuite, indexingMode, platforms, generatedPackagePostfix).apply(block)
}

fun MutableTGroup.testClass(
    clazz: KClass<*>,
    generatedClassName: String = getDefaultSuiteTestClassName(clazz.java),
    commonSuite: Boolean = true,
    indexingMode: List<IndexingMode> = emptyList(),
    platforms: List<KMPTestPlatform> = listOf(KMPTestPlatform.Unspecified),
    generatedTestDirectory: String? = null,
    block: MutableTSuite.() -> Unit
) {
    suites += TSuiteImpl(clazz.java, generatedClassName, commonSuite, indexingMode, platforms, generatedTestDirectory).apply(block)
}

@PublishedApi
internal fun getDefaultSuiteTestClassName(clazz: Class<*>): String {
    val packageName = clazz.`package`.name
    val simpleName = clazz.simpleName

    require(simpleName.startsWith("Abstract")) { "Doesn't start with \"Abstract\": $simpleName" }
    return packageName + '.' + simpleName.substringAfter("Abstract") + "Generated"
}