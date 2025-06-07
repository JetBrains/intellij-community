// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:OptIn(UnsafeCastFunction::class)

package org.jetbrains.kotlin.idea.perf.synthetic

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.UsefulTestCase
import com.intellij.usages.Usage
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig
import org.jetbrains.kotlin.idea.perf.suite.DefaultProfile
import org.jetbrains.kotlin.idea.perf.suite.PerformanceSuite
import org.jetbrains.kotlin.idea.perf.suite.StatsScopeConfig
import org.jetbrains.kotlin.idea.perf.suite.suite
import org.jetbrains.kotlin.idea.perf.util.OutputConfig
import org.jetbrains.kotlin.idea.perf.util.registerLoadingErrorsHeadlessNotifier
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.testFramework.Parameter
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.junit.runner.RunWith

@RunWith(JUnit3RunnerWithInners::class)
open class PerformanceStressTest : UsefulTestCase() {

    override fun runInDispatchThread(): Boolean = false

    protected open fun profileConfig(): ProfilerConfig = ProfilerConfig()

    protected open fun outputConfig(): OutputConfig = OutputConfig()

    protected open fun suiteWithConfig(suiteName: String, name: String? = null, block: PerformanceSuite.StatsScope.() -> Unit) {
        suite(
            suiteName,
            config = StatsScopeConfig(name = name, outputConfig = outputConfig(), profilerConfig = profileConfig()),
            block = block
        )
    }

    override fun setUp() {
        super.setUp()

        testRootDisposable.registerLoadingErrorsHeadlessNotifier()
    }

    fun testFindUsages() = doFindUsagesTest(false)

    fun testFindUsagesWithCompilerReferenceIndex() = doFindUsagesTest(true)

    private fun doFindUsagesTest(withCompilerIndex: Boolean) {
        // 1. Create 2 classes with the same name, but in a different packages
        // 2. Create 50 packages with a class that uses 50 times one of class from p.1
        // 3. Use one of the classes from p.1 in the only one class at p.2
        // 4. Run find usages of class that has limited usages
        //
        // Find usages have to resolve each reference due to the fact they have same names
        val numberOfFuns = 50
        for (numberOfPackagesWithCandidates in arrayOf(30, 50, 100)) {
            val name = "findUsages${numberOfFuns}_$numberOfPackagesWithCandidates" + if (withCompilerIndex) "_with_cri" else ""
            suiteWithConfig(name) {
                app {
                    warmUpProject()

                    project {

                        descriptor {
                            name(name)

                            module {
                                kotlinStandardLibrary()

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
                        }

                        profile(DefaultProfile)
                        if (withCompilerIndex) {
                            withCompiler()
                            rebuildProject()
                        }

                        fixture("pkg1/DataClass.kt") {
                            val fixture = this
                            with(fixture.cursorConfig) { marker = "DataClass" }

                            with(config) {
                                warmup = 8
                                iterations = 15
                            }

                            measure<Set<Usage>>(fixture, "findUsages") {
                                before = {
                                    fixture.moveCursor()
                                }
                                test = {
                                    val findUsages = findUsages(fixture.cursorConfig)
                                    // 1 from import
                                    //   + numberOfUsages as function argument
                                    //   + numberOfUsages as return type functions
                                    //   + numberOfUsages as new instance in a body of function
                                    // in a SomeService
                                    assertEquals(1 + 3 * numberOfFuns, findUsages.size)
                                    findUsages
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun testBaseClassAndLotsOfSubClasses() {
        // there is a base class with several open functions
        // and lots of subclasses of it
        // KTIJ-21027

        suiteWithConfig("Base class and lots of subclasses project", "ktij-21027 project") {
            app {
                warmUpProject()

                project {
                    descriptor {
                        name("ktij-21027")

                        val baseClassFunctionNames = (0..10).map { "foo$it" }

                        module {
                            kotlinStandardLibrary()

                            src("src/main/java/")

                            kotlinFile("BaseClass") {
                                pkg("pkg")

                                topClass("BaseClass") {
                                    openClass()

                                    for (fnName in baseClassFunctionNames) {
                                        function(fnName) {
                                            openFunction()
                                            returnType("String")
                                            body("TODO()")
                                        }
                                    }
                                }
                            }

                            for (classIndex in 0..5) {
                                val superClassName = "SomeClass$classIndex"
                                kotlinFile(superClassName) {
                                    pkg("pkg")

                                    topClass(superClassName) {
                                        openClass()
                                        superClass("BaseClass")

                                        for (fnName in baseClassFunctionNames) {
                                            function(fnName) {
                                                overrideFunction()
                                                returnType("String")
                                                body("TODO()")
                                            }
                                        }
                                    }
                                }

                                for (subclassIndex in 0..10) {
                                    val subClassName = "SubClass${classIndex}0${subclassIndex}"
                                    kotlinFile(subClassName) {
                                        pkg("pkg")

                                        topClass(subClassName) {
                                            superClass(superClassName)

                                            for (fnName in baseClassFunctionNames) {
                                                function(fnName) {
                                                    overrideFunction()
                                                    returnType("String")
                                                    body("TODO()")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    profile(DefaultProfile)

                    fixture("src/main/java/pkg/BaseClass.kt") {
                        val fixture = this
                        with(config) {
                            warmup = 8
                            iterations = 15
                        }

                        measureHighlight(fixture)
                    }
                }
            }
        }
    }

    fun testLotsOfModules() {
        suiteWithConfig("Lots of modules project", "KTIJ-26506 project") {
            app {
                warmUpProject()

                project {
                    descriptor {
                        name("ktij-26506")

                        // originally project has 1,5K modules
                        //val numberOfModules = 1500

                        val numberOfModules = 150
                        val numberOfClassesPerModule = 20
                        val numberOfImportedClasses = 30
                        val numberOfFunctionsPerClass = 30

                        for (moduleIndex in 0 until numberOfModules) {
                            module(moduleName = "module$moduleIndex") {
                                kotlinStandardLibrary()

                                src("src/main/java/")

                                for (classIndex in 0..numberOfClassesPerModule) {
                                    val className = "Some${moduleIndex}Class${classIndex}"

                                    kotlinFile(className) {
                                        pkg("com.pkg.module${moduleIndex}")

                                        topClass(className) {
                                            for (functionIndex in 0 until numberOfFunctionsPerClass) {
                                                function("foo${functionIndex}") {
                                                    returnType("String")
                                                    param("p", "Int")
                                                    body("TODO()")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        module(moduleName = "main-module") {
                            kotlinStandardLibrary()

                            for (moduleIndex in 0 until numberOfModules) {
                                moduleDependency(moduleName = "module$moduleIndex")
                            }

                            src("src/main/java/")

                            kotlinFile("Main") {
                                pkg("com.pkg.module.main")

                                val usedClasses = mutableListOf<String>()
                                for (moduleIndex in 0 until numberOfImportedClasses) {
                                    val classIndex = 0
                                    val className = "Some${moduleIndex}Class${classIndex}"
                                    usedClasses += className
                                    import("com.pkg.module$moduleIndex.$className")
                                }

                                topClass("Main") {
                                    for (functionIndex in 0 until numberOfFunctionsPerClass) {
                                        function("foo${functionIndex}") {
                                            returnType("String")
                                            param("p", "Int")
                                            val classToUse = usedClasses[functionIndex % usedClasses.size]
                                            body("""
                                                val c = $classToUse()
                                                return c.toString()
                                            """.trimIndent())
                                        }
                                    }
                                }
                            }
                        }
                    }

                    profile(DefaultProfile)

                    fixture("main-module/src/main/java/com/pkg/module/main/Main.kt") {
                        val fixture = this
                        fixture.highlight()
                    }
                }
            }
        }
    }

    fun testLotsOfOverloadedMethods() {
        // KT-35135
        val generatedTypes = mutableListOf(listOf<String>())
        generateTypes(arrayOf("Int", "String", "Long", "List<Int>", "Array<Int>"), generatedTypes)

        suiteWithConfig("Lots of overloaded method project", "kt-35135 project") {
            app {
                warmUpProject()

                project {
                    descriptor {
                        name("kt-35135")

                        module {
                            kotlinStandardLibrary()

                            src("src/main/java/")

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
                    }

                    profile(DefaultProfile)

                    fixture("src/main/java/pkg/SomeClass.kt"){
                        with(typingConfig) {
                            marker = "ov"
                            insertString = "override fun foo(): String = TODO()"
                            delayMs = 50
                        }

                        with(config) {
                            warmup = 8
                            iterations = 15
                        }

                        measureTypeAndHighlight(this, "type override fun foo()")
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

    fun testManyModulesExample() {
        suiteWithConfig("10 modules", "10 modules") {
            app {
                warmUpProject()

                project {
                    descriptor {
                        name("ten_modules")

                        for (index in 0 until 10) {
                            module("module$index") {
                                kotlinStandardLibrary()

                                for (libIndex in 0 until 10) {
                                    library("log4j$libIndex", "log4j:log4j:1.2.17")
                                }

                                kotlinFile("SomeService$index") {
                                    pkg("pkg")

                                    topClass("SomeService$index") {
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun testFacadeClasses() {
        suiteWithConfig("Lots of facade classes", "kt-53543 project") {
            app {
                warmUpProject()
                project {
                    descriptor {
                        name("KT-53543")

                        module {
                            kotlinStandardLibrary()

                            for (index in 0..50_000) {
                                kotlinFile("AaaaFacade$index") {
                                    for (funIndex in 0..2) {
                                        topFunction("aaaaFunction${index}_$funIndex") {}
                                    }
                                }

                                kotlinFile("AaaaFacadeNon$index") {
                                    topClass("AaaaClass$index") {}
                                }
                            }

                            kotlinFile("SomeKotlinClass") {
                                topFunction("foo") {}
                            }
                        }
                    }

                    profile(DefaultProfile)

                    fixture("SomeKotlinClass.kt"){
                        val fixture = this
                        measure<Unit>("findFacadeClass") {
                            test = {
                                val facadeClass = fixture.psiFile.cast<KtFile>().findFacadeClass()
                                assertNotNull(facadeClass)
                            }
                        }

                        measure<Unit>("getFacadeClassesInPackage") {
                            test = {
                                val classes = KotlinAsJavaSupport.getInstance(fixture.project).getFacadeClassesInPackage(
                                    FqName(""),
                                    GlobalSearchScope.projectScope(fixture.project),
                                )

                                assertEquals(50002, classes.size)
                            }
                        }
                    }
                }
            }
        }
    }
}
