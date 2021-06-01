// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.synthetic

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.UsefulTestCase
import com.intellij.usages.Usage
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.perf.Parameter
import org.jetbrains.kotlin.idea.perf.util.DefaultProfile
import org.jetbrains.kotlin.idea.perf.util.PerformanceSuite
import org.jetbrains.kotlin.idea.perf.util.PerformanceSuite.TypingConfig
import org.jetbrains.kotlin.idea.perf.util.registerLoadingErrorsHeadlessNotifier
import org.jetbrains.kotlin.idea.perf.util.suite
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.testFramework.commitAllDocuments
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners
import org.junit.runner.RunWith

@RunWith(JUnit3RunnerWithInners::class)
class PerformanceStressTest : UsefulTestCase() {

    override fun setUp() {
        super.setUp()

        testRootDisposable.registerLoadingErrorsHeadlessNotifier()
    }

    fun testFindUsages() {
        // 1. Create 2 classes with the same name, but in a different packages
        // 2. Create 50 packages with a class that uses 50 times one of class from p.1
        // 3. Use one of the classes from p.1 in the only one class at p.2
        // 4. Run find usages of class that has limited usages
        //
        // Find usages have to resolve each reference due to the fact they have same names
        val numberOfFuns = 50
        val numberOfPackagesWithCandidates = 50

        val name = "findUsages${numberOfFuns}_$numberOfPackagesWithCandidates"
        suite(
            suiteName = name,
            config = PerformanceSuite.StatsScopeConfig(name = name)
        ) {
            app {
                warmUpProject()

                project {

                    descriptor {
                        name(name)
                        buildGradle(IDEA_TEST_DATA_DIR.resolve("perfTest/simpleTemplate/"))

                        for (index in 1..2) {
                            kotlinFile("DataClass") {
                                pkg("pkg$index")

                                topClass("DataClass") {
                                    dataClass()

                                    ctorParameter(Parameter("name", "String"))
                                    ctorParameter(Parameter("value", "Int"))
                                }
                            }
                        }

                        for (pkgIndex in 1..numberOfPackagesWithCandidates) {
                            kotlinFile("SomeService") {
                                pkg("pkgX$pkgIndex")
                                // use pkg1 for `pkgX1.SomeService`, and pkg2 for all other `pkgX*.SomeService`
                                import("pkg${if (pkgIndex == 1) 1 else 2}.DataClass")

                                topClass("SomeService") {
                                    for (index in 1..numberOfFuns) {
                                        function("foo$index") {
                                            returnType("DataClass")
                                            param("data", "DataClass")

                                            body("return DataClass(data.name, data.value + $index)")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    profile(DefaultProfile)

                    fixture("src/main/java/pkg1/DataClass.kt").use { fixture ->
                        val typingConfig = PerformanceSuite.CursorConfig(fixture, marker = "DataClass")

                        with(config) {
                            warmup = 8
                            iterations = 15
                        }

                        measure<Set<Usage>>("findUsages", fixture = fixture) {
                            before = {
                                moveCursor(typingConfig)
                            }
                            test = {
                                val findUsages = findUsages(typingConfig)
                                // 1 from import
                                //   + numberOfUsages as function argument
                                //   + numberOfUsages as return type functions
                                //   + numberOfUsages as new instance in a body of function
                                // in a SomeService
                                TestCase.assertEquals(1 + 3 * numberOfFuns, findUsages.size)
                                findUsages
                            }
                            after = {

                            }
                        }
                    }
                }
            }
        }
    }

    fun testLotsOfOverloadedMethods() {
        // KT-35135
        val generatedTypes = mutableListOf(listOf<String>())
        generateTypes(arrayOf("Int", "String", "Long", "List<Int>", "Array<Int>"), generatedTypes)

        suite(
            suiteName = "Lots of overloaded method project",
            config = PerformanceSuite.StatsScopeConfig(name = "kt-35135 project")
        ) {
            app {
                warmUpProject()

                project {
                    descriptor {
                        name("kt-35135")
                        buildGradle(IDEA_TEST_DATA_DIR.resolve("perfTest/simpleTemplate/"))

                        kotlinFile("OverloadX") {
                            pkg("pkg")

                            topClass("OverloadX") {
                                openClass()

                                for (types in generatedTypes) {
                                    function("foo") {
                                        openFunction()
                                        returnType("String")
                                        for ((index, type) in types.withIndex()) {
                                            param("arg$index", type)
                                        }
                                        body("TODO()")
                                    }
                                }
                            }
                        }

                        kotlinFile("SomeClass") {
                            pkg("pkg")

                            topClass("SomeClass") {
                                superClass("OverloadX")

                                body("ov")
                            }
                        }
                    }

                    profile(DefaultProfile)

                    fixture("src/main/java/pkg/SomeClass.kt").use { fixture ->
                        val typingConfig = TypingConfig(
                            fixture,
                            marker = "ov",
                            insertString = "override fun foo(): String = TODO()",
                            delayMs = 50
                        )

                        with(config) {
                            warmup = 8
                            iterations = 15
                        }

                        measure<List<HighlightInfo>>("type override fun foo()", fixture = fixture) {
                            before = {
                                moveCursor(typingConfig)
                            }
                            test = {
                                typeAndHighlight(typingConfig)
                            }
                            after = {
                                fixture.restoreText()
                                commitAllDocuments()
                            }
                        }
                    }
                }
            }
        }
    }

    private tailrec fun generateTypes(types: Array<String>, results: MutableList<List<String>>, index: Int = 0, maxCount: Int = 3000) {
        val newResults = mutableListOf<List<String>>()
        for (list in results) {
            if (list.size < index) continue
            for (t in types) {
                val newList = mutableListOf<String>()
                newList.addAll(list)
                newList.add(t)
                newResults.add(newList.toList())
                if (results.size + newResults.size >= maxCount) {
                    results.addAll(newResults)
                    return
                }
            }
        }
        results.addAll(newResults)
        generateTypes(types, results, index + 1, maxCount)
    }
}
