// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.fir.uast.test.*
import org.jetbrains.kotlin.fir.testGenerator.codeinsight.generateK2CodeInsightTests
import org.jetbrains.kotlin.idea.fir.analysis.providers.AbstractIdeKotlinAnnotationsResolverTest
import org.jetbrains.kotlin.idea.fir.analysis.providers.sessions.AbstractSessionsInvalidationTest
import org.jetbrains.kotlin.idea.fir.analysis.providers.trackers.AbstractProjectWideOutOfBlockKotlinModificationTrackerTest
import org.jetbrains.kotlin.idea.fir.codeInsight.AbstractK2MultiModuleLineMarkerTest
import org.jetbrains.kotlin.idea.fir.completion.*
import org.jetbrains.kotlin.idea.fir.completion.test.handlers.AbstractFirKeywordCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.completion.test.handlers.AbstractHighLevelBasicCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.completion.test.handlers.AbstractHighLevelJavaCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.completion.test.handlers.AbstractK2CompletionCharFilterTest
import org.jetbrains.kotlin.idea.fir.completion.wheigher.AbstractHighLevelWeigherTest
import org.jetbrains.kotlin.idea.fir.documentation.AbstractFirQuickDocTest
import org.jetbrains.kotlin.idea.fir.findUsages.AbstractFindUsagesFirTest
import org.jetbrains.kotlin.idea.fir.findUsages.AbstractFindUsagesWithDisableComponentSearchFirTest
import org.jetbrains.kotlin.idea.fir.findUsages.AbstractKotlinFindUsagesWithLibraryFirTest
import org.jetbrains.kotlin.idea.fir.findUsages.AbstractKotlinFindUsagesWithStdlibFirTest
import org.jetbrains.kotlin.idea.fir.imports.AbstractFirJvmOptimizeImportsTest
import org.jetbrains.kotlin.idea.fir.low.level.api.AbstractFirLibraryModuleDeclarationResolveTest
import org.jetbrains.kotlin.idea.fir.parameterInfo.AbstractFirParameterInfoTest
import org.jetbrains.kotlin.idea.fir.quickfix.AbstractHighLevelQuickFixMultiFileTest
import org.jetbrains.kotlin.idea.fir.quickfix.AbstractHighLevelQuickFixTest
import org.jetbrains.kotlin.idea.fir.search.AbstractHLImplementationSearcherTest
import org.jetbrains.kotlin.idea.fir.shortenRefs.AbstractFirShortenRefsTest
import org.jetbrains.kotlin.idea.k2.refactoring.rename.AbstractFirRenameTest
import org.jetbrains.kotlin.parcelize.ide.test.AbstractParcelizeK2QuickFixTest
import org.jetbrains.kotlin.testGenerator.generator.TestGenerator
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.Patterns.DIRECTORY
import org.jetbrains.kotlin.testGenerator.model.Patterns.JAVA
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_KTS_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOT_AND_FIR_PREFIX
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_FIR_PREFIX
import org.jetbrains.kotlin.idea.fir.resolve.*

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    generateK2Tests()
}

fun generateK2Tests(isUpToDateCheck: Boolean = false) {
    System.setProperty("java.awt.headless", "true")
    TestGenerator.write(assembleWorkspace(), isUpToDateCheck)
}

private fun assembleWorkspace(): TWorkspace = workspace {
    generateK2CodeInsightTests()
    generateK2Fe10BindingsTests()
    generateK2NavigationTests()
    generateK2DebuggerTests()
    generateK2HighlighterTests()
    generateK2RefactoringsTests()
    generateK2SearchTests()
    generateK2RefIndexTests()

    testGroup("base/fir/analysis-api-providers") {
        testClass<AbstractProjectWideOutOfBlockKotlinModificationTrackerTest> {
            model("outOfBlockProjectWide", pattern = KT_WITHOUT_DOTS or Patterns.JAVA)
        }

        testClass<AbstractSessionsInvalidationTest> {
            model("sessionInvalidation", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractIdeKotlinAnnotationsResolverTest> {
            model("annotationsResolver", pattern = KT_WITHOUT_DOTS)
        }
    }

    testGroup("compiler-plugins/parcelize/tests/k2", testDataPath = "../testData") {
        testClass<AbstractParcelizeK2QuickFixTest> {
            model("quickfix", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"))
        }
    }

    testGroup("fir-low-level-api-ide-impl") {
        testClass<AbstractFirLibraryModuleDeclarationResolveTest> {
            model("libraryModuleResolve", isRecursive = false)
        }
    }

    testGroup("fir", testDataPath = "../idea/tests/testData") {
        testClass<AbstractFirReferenceResolveTest> {
            model("resolve/references", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractFirReferenceResolveWithLibTest> {
            model("resolve/referenceWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractFirReferenceResolveWithCompiledLibTest> {
            model("resolve/referenceWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractFirReferenceResolveWithCrossLibTest> {
            model("resolve/referenceWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractFirReferenceResolveInJavaTest> {
            model("resolve/referenceInJava/binaryAndSource", pattern = JAVA)
            model("resolve/referenceInJava/sourceOnly", pattern = JAVA)
        }

        testClass<AbstractFirReferenceToCompiledKotlinResolveInJavaTest> {
            model("resolve/referenceInJava/binaryAndSource", pattern = JAVA)
        }

        testClass<AbstractHighLevelQuickFixTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$")
            model("quickfix/abstract", pattern = pattern)
            model("quickfix/addExclExclCall", pattern = pattern)
            model("quickfix/addInitializer", pattern = pattern)
            model("quickfix/addPropertyAccessors", pattern = pattern)
            model("quickfix/checkArguments", pattern = pattern, isRecursive = false)
            model("quickfix/conflictingImports", pattern = pattern)
            model("quickfix/expressions", pattern = pattern)
            model("quickfix/lateinit", pattern = pattern)
            model("quickfix/localVariableWithTypeParameters", pattern = pattern)
            model("quickfix/modifiers", pattern = pattern, isRecursive = false)
            model("quickfix/modifiers/addOpenToClassDeclaration", pattern = pattern)
            model("quickfix/nullables", pattern = pattern)
            model("quickfix/override", pattern = pattern, isRecursive = false)
            model("quickfix/override/typeMismatchOnOverride", pattern = pattern, isRecursive = false)
            model("quickfix/removeRedundantSpreadOperator", pattern = pattern)
            model("quickfix/replaceInfixOrOperatorCall", pattern = pattern)
            model("quickfix/replaceWithArrayCallInAnnotation", pattern = pattern)
            model("quickfix/replaceWithDotCall", pattern = pattern)
            model("quickfix/replaceWithSafeCall", pattern = pattern)
            model("quickfix/specifyVisibilityInExplicitApiMode", pattern = pattern)
            model("quickfix/supercalls", pattern = pattern)
            model("quickfix/surroundWithArrayOfForNamedArgumentsToVarargs", pattern = pattern)
            model("quickfix/variables/changeMutability", pattern = pattern, isRecursive = false)
            model("quickfix/variables/removeValVarFromParameter", pattern = pattern)
            model("quickfix/when", pattern = pattern)
            model("quickfix/wrapWithSafeLetCall", pattern = pattern)
            model("quickfix/typeAddition", pattern = pattern)
            model("quickfix/typeMismatch/casts", pattern = pattern)
            model("quickfix/typeMismatch/componentFunctionReturnTypeMismatch", pattern = pattern)
            model("quickfix/typeMismatch/typeMismatchOnReturnedExpression", pattern = pattern)
            model("quickfix/toString", pattern = pattern)
            model("quickfix/specifySuperType", pattern = pattern)
        }

        testClass<AbstractHighLevelQuickFixMultiFileTest> {
            model(
                "quickfix/autoImports",
                pattern = Patterns.forRegex("""^(\w+)\.((before\.Main\.\w+)|(test))$"""),
                testMethodName = "doTestWithExtraFile"
            )
        }


        testClass<AbstractFirShortenRefsTest> {
            model("shortenRefsFir", pattern = KT_WITHOUT_DOTS, testMethodName = "doTestWithMuting")
        }

        testClass<AbstractFirParameterInfoTest> {
            model(
                "parameterInfo", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"), isRecursive = true,
                excludedDirectories = listOf("withLib1/sharedLib", "withLib2/sharedLib", "withLib3/sharedLib")
            )
        }

        testClass<AbstractFirJvmOptimizeImportsTest> {
            model("editor/optimizeImports/jvm", pattern = KT_WITHOUT_DOTS)
            model("editor/optimizeImports/common", pattern = KT_WITHOUT_DOTS)
        }
    }

    testGroup("fir", testDataPath = "../completion/testData") {
        testClass<AbstractHighLevelJvmBasicCompletionTest> {
            model("basic/common", pattern = KT_WITHOUT_FIR_PREFIX)
            model("basic/java", pattern = KT_WITHOUT_FIR_PREFIX)
            model("../../idea-fir/testData/completion/basic/common", testClassName = "CommonFir")
        }

        testClass<AbstractHighLevelBasicCompletionHandlerTest> {
            model("handlers/basic", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
            model("handlers", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX, isRecursive = false)
        }

        testClass<AbstractHighLevelJavaCompletionHandlerTest> {
            model("handlers/injava", pattern = Patterns.JAVA)
        }

        testClass<AbstractFirKeywordCompletionHandlerTest> {
            model("handlers/keywords", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
        }

        testClass<AbstractHighLevelWeigherTest> {
            model("weighers/basic", pattern = KT_OR_KTS_WITHOUT_DOTS)
        }

        testClass<AbstractHighLevelMultiFileJvmBasicCompletionTest> {
            model("basic/multifile", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractK2MultiPlatformCompletionTest> {
            model("multiPlatform", isRecursive = false, pattern = DIRECTORY)
        }

        testClass<AbstractK2CompletionCharFilterTest> {
            model("handlers/charFilter", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
        }

        testClass<AbstractFirKeywordCompletionTest> {
            model("keywords", isRecursive = false, pattern = KT_WITHOUT_FIR_PREFIX)
            model(
                "../../idea-fir/testData/completion/keywords",
                testClassName = "KeywordsFir",
                isRecursive = false,
                pattern = KT_WITHOUT_FIR_PREFIX
            )
        }
        testClass<AbstractFirWithLibBasicCompletionTest> {
            model("basic/withLib", isRecursive = false, pattern = KT_WITHOUT_FIR_PREFIX)
        }
    }

    testGroup("fir", testDataPath = "../code-insight/testData") {
        testClass<AbstractK2MultiModuleLineMarkerTest> {
            model("linemarkers", isRecursive = false, pattern = DIRECTORY)
        }
    }

    testGroup("refactorings/rename.k2", testDataPath = "../../idea/tests/testData") {
        testClass<AbstractFirRenameTest> {
            model("refactoring/rename", pattern = Patterns.TEST, flatten = true)
        }
    }

    testGroup("fir", testDataPath = "../idea/tests/testData/findUsages") {
        testClass<AbstractFindUsagesFirTest> {
            model("kotlin", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|kts)$"""))
            model("java", pattern = Patterns.forRegex("""^(.+)\.0\.java$"""))
            model("propertyFiles", pattern = Patterns.forRegex("""^(.+)\.0\.properties$"""))
        }

        testClass<AbstractFindUsagesWithDisableComponentSearchFirTest> {
            model("kotlin/conventions/components", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|kts)$"""))
        }

        testClass<AbstractKotlinFindUsagesWithLibraryFirTest> {
            model("libraryUsages", pattern = Patterns.forRegex("""^(.+)\.0\.kt$"""))
        }

        testClass<AbstractKotlinFindUsagesWithStdlibFirTest> {
            model("stdlibUsages", pattern = Patterns.forRegex("""^(.+)\.0\.kt$"""))
        }
    }

    testGroup("fir") {
        testClass<AbstractHLImplementationSearcherTest> {
            model("search/implementations", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractFirQuickDocTest> {
            model("quickDoc", pattern = Patterns.forRegex("""^([^_]+)\.(kt|java)$"""))
        }

        testClass<AbstractK2ReferenceResolveWithResolveExtensionTest> {
            model("extensions/references", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractK2JvmBasicCompletionTestWithResolveExtension> {
            model("extensions/completion", pattern = KT_WITHOUT_DOTS)
        }
    }

    testGroup("uast/uast-kotlin-fir/tests") {
        testClass<AbstractFirUastDeclarationTest> {
            model("declaration")
        }

        testClass<AbstractFirUastTypesTest> {
            model("type")
        }

        testClass<AbstractFirUastValuesTest> {
            model("value")
        }
    }

    testGroup("uast/uast-kotlin-fir/tests", testDataPath = "../../uast-kotlin/tests/testData") {
        testClass<AbstractFirLegacyUastDeclarationTest> {
            model("")
        }

        testClass<AbstractFirLegacyUastIdentifiersTest> {
            model("")
        }

        testClass<AbstractFirLegacyUastResolveEverythingTest> {
            model("")
        }

        testClass<AbstractFirLegacyUastTypesTest> {
            model("")
        }

        testClass<AbstractFirLegacyUastValuesTest> {
            model("")
        }
    }
}
