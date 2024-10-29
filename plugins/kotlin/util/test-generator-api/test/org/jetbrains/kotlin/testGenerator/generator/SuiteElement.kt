// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.generator

import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.TestIndexingMode
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.testGenerator.generator.methods.GetTestPlatformMethod
import org.jetbrains.kotlin.testGenerator.generator.methods.KotlinPluginModeMethod
import org.jetbrains.kotlin.testGenerator.generator.methods.RunTestMethod
import org.jetbrains.kotlin.testGenerator.generator.methods.SetUpMethod
import org.jetbrains.kotlin.testGenerator.generator.methods.TestCaseMethod
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.junit.runner.RunWith
import java.io.File
import java.util.*
import javax.lang.model.element.Modifier

fun File.toRelativeStringSystemIndependent(base: File): String = toRelativeString(base).toStringSystemIndependent()

fun String.toStringSystemIndependent(): String = if (File.separatorChar == '\\') this.replace('\\', '/') else this

interface TestMethod : RenderElement {
    val methodName: String
}

class SuiteElement private constructor(
    private val group: TGroup, private val suite: TSuite, private val model: TModel,
    private val className: String, private val isNested: Boolean,
    methods: List<TestMethod>, nestedSuites: List<SuiteElement>
) : RenderElement {
    private val methods = methods.sortedBy { it.methodName }
    val nestedSuites = nestedSuites.sortedBy { it.className }

    companion object {
        fun create(group: TGroup, suite: TSuite, model: TModel, className: String, platform: KMPTestPlatform, isNested: Boolean): SuiteElement {
            return collect(group, suite, model, model.depth, className, platform, isNested)
        }

        private fun collect(
            group: TGroup,
            suite: TSuite,
            model: TModel,
            depth: Int,
            className: String,
            platform: KMPTestPlatform,
            isNested: Boolean,
        ): SuiteElement {
            val rootFile = File(group.testDataRoot, model.path)

            val methods = mutableListOf<TestMethod>()
            val nestedSuites = mutableListOf<SuiteElement>()

            for (file in rootFile.listFiles().orEmpty()) {
                if (depth > 0 && !file.isExcluded(model)) {
                    val nestedClassName = file.toJavaIdentifier().capitalizeAsciiOnly()
                    val nestedModel = model.copy(
                        path = file.toRelativeStringSystemIndependent(group.testDataRoot),
                        testClassName = nestedClassName,
                    )

                    val nestedElement = collect(group, suite, nestedModel, depth - 1, nestedClassName, platform, isNested = true)
                    if (nestedElement.methods.isNotEmpty() || nestedElement.nestedSuites.isNotEmpty()) {
                        if (model.flatten) {
                            methods += flatten(nestedElement)
                        } else {
                            nestedSuites += nestedElement
                        }
                        continue
                    }
                }

                if (file.isExcluded(model)) continue

                val match = model.matcher(file.name) ?: continue

                val methodNameBase = getTestMethodNameBase(match.methodName)
                val path = file.toRelativeStringSystemIndependent(group.moduleRoot)
                methods += TestCaseMethod(
                    methodNameBase,
                    if (file.isDirectory) "$path/" else path,
                    file.toRelativeStringSystemIndependent(rootFile),
                    group.isCompilerTestData,
                    model.passTestDataPath,
                    file,
                    model.ignored,
                    model.
                    methodAnnotations
                )
            }

            fun createElement(
                className: String,
                isNested: Boolean,
                testCaseMethods: List<TestMethod>,
                nestedSuites: List<SuiteElement> = emptyList(),
            ): SuiteElement {
                val allMethods = mutableListOf<TestMethod>()
                allMethods += testCaseMethods

                if (testCaseMethods.isNotEmpty()) {
                    allMethods += KotlinPluginModeMethod(group.pluginMode)

                    if (platform.isSpecified) {
                        allMethods += GetTestPlatformMethod(platform)
                    }

                    if (model.passTestDataPath) {
                        allMethods += RunTestMethod(model)
                    }

                    if (group.isCompilerTestData || model.setUpStatements.isNotEmpty()) {
                        allMethods += SetUpMethod(createStatements(group, model))
                    }
                }

                return SuiteElement(group, suite, model, className, isNested, allMethods, nestedSuites)
            }

            if (methods.isNotEmpty()) {
                if (model.classPerTest) {
                    for (method in methods) {
                        val nestedClassName = method.methodName.capitalizeAsciiOnly()
                        nestedSuites += createElement(nestedClassName, isNested = true, listOf(method))
                    }
                    methods.clear()
                } else if (model.bucketSize != null) {
                    // Bucket classes are created even if `methods.size < bucketSize` to preserve stable test order when new tests appear
                    for ((index, methodsForBucket) in methods.chunked(model.bucketSize).withIndex()) {
                        val nestedClassName = "TestBucket${"%03d".format(index + 1)}"
                        nestedSuites += createElement(nestedClassName, isNested = true, methodsForBucket)
                    }
                    methods.clear()
                }
            }

            if (methods.isNotEmpty() && nestedSuites.isNotEmpty()) {
                nestedSuites += createElement("Uncategorized", isNested = true, methods)
                methods.clear()
            }

            return createElement(className, isNested, methods, nestedSuites)
        }

        private fun File.isExcluded(model: TModel): Boolean {
            if (model.excludedDirectories.isNotEmpty()) {
                // Don't generate a directory-based test if the directory is excluded.
                if (isDirectory && name in model.excludedDirectories) return true

                // Don't generate a file-based test if its parent directory is excluded.
                if (isFile) {
                    val p = parentFile.path.toStringSystemIndependent()
                    if (model.excludedDirectories.any { p.contains(it) }) return true
                }
            }
            return false
        }

        private fun createStatements(
            group: TGroup,
            model: TModel,
        ): List<String> = buildList {
            if (group.isCompilerTestData) {
                add(
                    "${TestKotlinArtifacts::compilerTestData.name}(\"${
                        File(
                            group.testDataRoot,
                            model.path
                        ).toRelativeStringSystemIndependent(group.moduleRoot)
                            .substringAfter(TestKotlinArtifacts.compilerTestDataDir.name + "/")
                    }\");"
                )
            }
            addAll(model.setUpStatements)
        }

        private fun flatten(element: SuiteElement): List<TestMethod> {
            val modelFileName = File(element.model.path).name

            val ownMethods = element.methods.filterIsInstance<TestCaseMethod>().map { it.embed(modelFileName) }
            val nestedMethods = element.nestedSuites.flatMap { flatten(it) }
            return ownMethods + nestedMethods
        }

        private fun getTestMethodNameBase(path: String): String {
            return path
                .split(File.pathSeparator)
                .joinToString(File.pathSeparator) { makeJavaIdentifier(it).capitalizeAsciiOnly() }
        }
    }

    fun testDataPath(): File =
        File(group.testDataRoot, model.path)

    fun testCaseMethods(platform: KMPTestPlatform): Map<String, List<TestCaseMethod>> {
        val testDataPath = testDataPath()
        val className = suite.generatedClassFqName(platform) + (if (isNested) "$" + this.className else "")
        return this.nestedSuites.fold<SuiteElement, MutableMap<String, List<TestCaseMethod>>>(
            mutableMapOf<String, List<TestCaseMethod>>(
                className to this.methods.filterIsInstance<TestCaseMethod>().map { it.copy(file = it.testDataPath(testDataPath))})
        ) { acc: MutableMap<String, List<TestCaseMethod>>, curr: SuiteElement ->
            val testDataMethodPaths = curr.testCaseMethods(platform)
            acc += testDataMethodPaths.map<String, List<TestCaseMethod>, Pair<String, List<TestCaseMethod>>> {
                "$className\$${curr.className}" to it.value
            }.toMap<String, List<TestCaseMethod>>()
            acc
        }
    }

    override fun Code.render() {
        if (model.ignored) return

        val testDataPath = testDataPath().toRelativeStringSystemIndependent(group.moduleRoot)

        if (suite.indexingMode.isNotEmpty()) {
            val args = suite.indexingMode
            appendAnnotation(TAnnotation<TestIndexingMode>(*args.toTypedArray()))
        }
        appendAnnotation(TAnnotation<RunWith>(model.runWithClass))
        appendAnnotation(TAnnotation<TestMetadata>(testDataPath))
        suite.annotations.forEach { appendAnnotation(it) }

        val modifiers = EnumSet.of(Modifier.PUBLIC)

        if (isNested) {
            modifiers.add(Modifier.STATIC)
        }

        if (methods.isEmpty()) {
            modifiers.add(Modifier.ABSTRACT)
        }

        appendModifiers(modifiers)
        appendBlock("class $className extends ${suite.abstractTestClass.simpleName}") {
            appendList(methods + nestedSuites, separator = "\n\n")
        }
    }
}