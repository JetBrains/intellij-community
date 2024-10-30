// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.fir.uast.test.*
import org.jetbrains.kotlin.fir.testGenerator.codeinsight.generateK2CodeInsightTests
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.AbstractIdeKotlinAnnotationsResolverTest
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.dependents.AbstractModuleDependentsTest
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.inheritors.AbstractDirectInheritorsProviderTest
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.inheritors.AbstractSealedInheritorsProviderTest
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.sessions.AbstractGlobalSessionInvalidationTest
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.sessions.AbstractLocalSessionInvalidationTest
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.trackers.AbstractProjectWideOutOfBlockKotlinModificationTrackerTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.fir.AbstractK2JsBasicCompletionLegacyStdlibTest
import org.jetbrains.kotlin.idea.fir.actions.AbstractK2AddImportActionTest
import org.jetbrains.kotlin.idea.fir.actions.AbstractK2BytecodeToolWindowTest
import org.jetbrains.kotlin.idea.fir.codeInsight.AbstractK2MultiModuleLineMarkerTest
import org.jetbrains.kotlin.idea.fir.completion.*
import org.jetbrains.kotlin.idea.fir.completion.kmpBasic.AbstractKotlinKmpCompletionTest
import org.jetbrains.kotlin.idea.fir.completion.test.handlers.*
import org.jetbrains.kotlin.idea.fir.completion.wheigher.AbstractHighLevelWeigherTest
import org.jetbrains.kotlin.idea.fir.copyPaste.AbstractFirKotlinToKotlinMultiDollarStringsCopyPasteTest
import org.jetbrains.kotlin.idea.fir.copyPaste.AbstractFirLiteralKotlinToKotlinCopyPasteTest
import org.jetbrains.kotlin.idea.fir.copyPaste.AbstractFirLiteralTextToKotlinCopyPasteTest
import org.jetbrains.kotlin.idea.fir.documentation.AbstractFirQuickDocMultiplatformTest
import org.jetbrains.kotlin.idea.fir.documentation.AbstractFirQuickDocTest
import org.jetbrains.kotlin.idea.fir.externalAnnotations.AbstractK2ExternalAnnotationTest
import org.jetbrains.kotlin.idea.fir.findUsages.*
import org.jetbrains.kotlin.idea.fir.folding.AbstractFirFoldingTest
import org.jetbrains.kotlin.idea.fir.imports.AbstractK2JvmOptimizeImportsTest
import org.jetbrains.kotlin.idea.fir.imports.AbstractK2AutoImportTest
import org.jetbrains.kotlin.idea.fir.imports.AbstractK2FilteringAutoImportTest
import org.jetbrains.kotlin.idea.fir.imports.AbstractK2JsOptimizeImportsTest
import org.jetbrains.kotlin.idea.fir.kmp.AbstractK2KmpLightFixtureHighlightingTest
import org.jetbrains.kotlin.idea.fir.navigation.AbstractFirGotoDeclarationTest
import org.jetbrains.kotlin.idea.fir.navigation.AbstractFirGotoRelatedSymbolMultiModuleTest
import org.jetbrains.kotlin.idea.fir.navigation.AbstractFirGotoTest
import org.jetbrains.kotlin.idea.fir.navigation.AbstractFirGotoTypeDeclarationTest
import org.jetbrains.kotlin.idea.fir.parameterInfo.AbstractFirParameterInfoTest
import org.jetbrains.kotlin.idea.fir.projectView.AbstractK2ProjectViewTest
import org.jetbrains.kotlin.idea.fir.resolve.*
import org.jetbrains.kotlin.idea.fir.search.AbstractHLImplementationSearcherTest
import org.jetbrains.kotlin.idea.fir.search.AbstractKotlinBuiltInsResolveScopeEnlargerTest
import org.jetbrains.kotlin.idea.fir.search.AbstractScopeEnlargerTest
import org.jetbrains.kotlin.idea.fir.shortenRefs.AbstractFirShortenRefsTest
import org.jetbrains.kotlin.idea.k2.copyright.AbstractFirUpdateKotlinCopyrightTest
import org.jetbrains.kotlin.idea.k2.refactoring.rename.AbstractFirMultiModuleRenameTest
import org.jetbrains.kotlin.idea.k2.refactoring.rename.AbstractFirRenameTest
import org.jetbrains.kotlin.idea.k2.refactoring.rename.AbstractK2InplaceRenameTest
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import org.jetbrains.kotlin.j2k.k2.AbstractK2JavaToKotlinConverterMultiFileTest
import org.jetbrains.kotlin.j2k.k2.AbstractK2JavaToKotlinConverterPartialTest
import org.jetbrains.kotlin.j2k.k2.AbstractK2JavaToKotlinConverterSingleFileFullJDKTest
import org.jetbrains.kotlin.j2k.k2.AbstractK2JavaToKotlinConverterSingleFileTest
import org.jetbrains.kotlin.parcelize.ide.test.AbstractParcelizeK2QuickFixTest
import org.jetbrains.kotlin.testGenerator.generator.TestGenerator
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*
import org.jetbrains.kotlin.testGenerator.model.Patterns.DIRECTORY
import org.jetbrains.kotlin.testGenerator.model.Patterns.JAVA
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_KTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_KTS_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOT_AND_FIR_PREFIX
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_FIR_PREFIX
import org.jetbrains.kotlin.testGenerator.model.Patterns.TEST

fun main(@Suppress("UNUSED_PARAMETER", "unused") args: Array<String>) {
    generateK2Tests()
}

fun assembleK2Workspace(): TWorkspace = assembleWorkspace()

fun generateK2Tests(isUpToDateCheck: Boolean = false) {
    System.setProperty("java.awt.headless", "true")
    TestGenerator.write(assembleWorkspace(), isUpToDateCheck)
}

private fun assembleWorkspace(): TWorkspace = workspace(KotlinPluginMode.K2) {
    generateK2CodeInsightTests()
    generateK2NavigationTests()
    generateK2DebuggerTests()
    generateK2ComposeDebuggerTests()
    generateK2HighlighterTests()
    generateK2GradleBuildScriptHighlighterTests()
    generateK2RefactoringsTests()
    generateK2SearchTests()
    generateK2RefIndexTests()
    generateK2AnalysisApiTests()
    generateK2InjectionTests()

    testGroup("base/fir/analysis-api-platform") {
        testClass<AbstractProjectWideOutOfBlockKotlinModificationTrackerTest> {
            model("outOfBlockProjectWide", pattern = KT_WITHOUT_DOTS or JAVA)
        }

        testClass<AbstractLocalSessionInvalidationTest> {
            model("sessionInvalidation", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractGlobalSessionInvalidationTest> {
            model("sessionInvalidation", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractIdeKotlinAnnotationsResolverTest> {
            model("annotationsResolver", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractModuleDependentsTest> {
            model("moduleDependents", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractDirectInheritorsProviderTest> {
            model("directInheritors", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractSealedInheritorsProviderTest> {
            model("sealedInheritors", pattern = DIRECTORY, isRecursive = false)
        }
    }

    testGroup("compiler-plugins/parcelize/tests/k2", testDataPath = "../testData", category = QUICKFIXES) {
        testClass<AbstractParcelizeK2QuickFixTest> {
            model("quickfix", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"))
        }
    }

    testGroup("fir/tests", testDataPath = "../../idea/tests/testData", category = CODE_INSIGHT) {
        testClass<AbstractK2AddImportActionTest> {
            model("idea/actions/kotlinAddImportAction", pattern = KT_WITHOUT_DOTS)
        }

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

        testClass<AbstractFirReferenceResolveWithCompilerPluginsWithLibTest> {
            model("resolve/referenceWithCompilerPluginsWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractFirReferenceResolveWithCompilerPluginsWithCompiledLibTest> {
            model("resolve/referenceWithCompilerPluginsWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractFirReferenceResolveWithCompilerPluginsWithCrossLibTest> {
            model("resolve/referenceWithCompilerPluginsWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractReferenceResolveInLibrarySourcesFirTest> {
            model("resolve/referenceInLib", isRecursive = false)
        }

        testClass<AbstractFirReferenceResolveInJavaTest> {
            model("resolve/referenceInJava/binaryAndSource", pattern = JAVA)
            model("resolve/referenceInJava/sourceOnly", pattern = JAVA)
        }

        testClass<AbstractFirReferenceToCompiledKotlinResolveInJavaTest> {
            model("resolve/referenceInJava/binaryAndSource", pattern = JAVA)
        }

        testClass<AbstractFirInLibraryResolveEverythingTest> {
            model("resolve/compiled/sources")
        }

        testClass<AbstractFirLiteralTextToKotlinCopyPasteTest> {
            model("copyPaste/plainTextLiteral", pattern = Patterns.forRegex("""^([^.]+)\.txt$"""))
        }

        testClass<AbstractFirLiteralKotlinToKotlinCopyPasteTest> {
            model("copyPaste/literal", pattern = Patterns.forRegex("""^([^.]+)\.kt$"""))
        }

        testClass<AbstractFirKotlinToKotlinMultiDollarStringsCopyPasteTest> {
            model("copyPaste/multiDollar", pattern = Patterns.forRegex("""^([^.]+)\.kt$"""))
        }

        testClass<AbstractFirShortenRefsTest> {
            model("shortenRefsFir", pattern = KT_WITHOUT_DOTS, testMethodName = "doTestWithMuting")
            model("shortenRefs/this", pattern = KT_WITHOUT_DOTS, testMethodName = "doTestWithMuting")
        }

        testClass<AbstractFirParameterInfoTest> {
            model(
                "parameterInfo", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|java)$"), isRecursive = true,
                excludedDirectories = listOf("withLib1/sharedLib", "withLib2/sharedLib", "withLib3/sharedLib")
            )
        }

        testClass<AbstractK2AutoImportTest> {
            model(
                "editor/autoImport", testMethodName = "doTest", testClassName = "WithAutoImport",
                pattern = DIRECTORY, isRecursive = false
            )
        }

        testClass<AbstractK2FilteringAutoImportTest> {
            model(
                "editor/autoImportExtension", testMethodName = "doTest", testClassName = "WithAutoImport",
                pattern = DIRECTORY, isRecursive = false
            )
        }

        testClass<AbstractK2JvmOptimizeImportsTest> {
            model("editor/optimizeImports/jvm", pattern = KT_WITHOUT_DOTS)
            model("editor/optimizeImports/common", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractK2JsOptimizeImportsTest> {
            model("editor/optimizeImports/js", pattern = KT_WITHOUT_DOTS)
            model("editor/optimizeImports/common", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractK2BytecodeToolWindowTest> {
            model("internal/toolWindow", isRecursive = false, pattern = DIRECTORY)
        }

        testClass<AbstractK2ExternalAnnotationTest> {
            model("externalAnnotations", pattern = KT_WITHOUT_DOTS)
        }
    }

    testGroup("fir/tests", testDataPath = "../../idea/tests/testData", category = NAVIGATION) {
        testClass<AbstractFirGotoTypeDeclarationTest> {
            model("navigation/gotoTypeDeclaration", pattern = TEST)
        }

        testClass<AbstractFirGotoTest> {
            model("navigation/gotoClass", testMethodName = "doClassTest")
            model("navigation/gotoSymbol", testMethodName = "doSymbolTest")
        }

        testClass<AbstractFirGotoRelatedSymbolMultiModuleTest> {
            model("navigation/relatedSymbols/multiModule", isRecursive = false, pattern = DIRECTORY)
        }


        testClass<AbstractFirGotoDeclarationTest> {
            model("navigation/gotoDeclaration", pattern = TEST)
        }
    }

    testGroup("fir/tests", testDataPath = "../../completion/testData", category = COMPLETION) {
        testClass<AbstractK2JvmBasicCompletionTest> {
            model("basic/common", pattern = KT_WITHOUT_FIR_PREFIX)
            model("basic/java", pattern = KT_WITHOUT_FIR_PREFIX)
            model("../../idea-fir/testData/completion/basic/common", testClassName = "CommonFir")
        }

        testClass<AbstractK2JvmBasicCompletionFullJdkTest> {
            model("basic/fullJdk", pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractKotlinKmpCompletionTest>(
            platforms = listOf(
                KMPTestPlatform.Js,
                KMPTestPlatform.NativeLinux,
                KMPTestPlatform.CommonNativeJvm,
            ),
        ) {
            model("basic/common", pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractK2JvmBasicCompletionTest>("org.jetbrains.kotlin.idea.fir.completion.K2KDocCompletionTestGenerated") {
            model("kdoc", pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractK2JsBasicCompletionLegacyStdlibTest> {
            model("basic/common", pattern = KT_WITHOUT_FIR_PREFIX)
            model("../../idea-fir/testData/completion/basic/common", testClassName = "CommonFir")
        }

        testClass<AbstractHighLevelBasicCompletionHandlerTest> {
            model("handlers/basic", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
            model("handlers/basic/enum", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
            model("handlers", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX, isRecursive = false)
        }

        testClass<AbstractHighLevelJavaCompletionHandlerTest> {
            model("handlers/injava", pattern = JAVA)
        }

        testClass<AbstractFirKeywordCompletionHandlerTest> {
            model("handlers/keywords", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
        }

        testClass<AbstractFirDumbCompletionTest> {
            model("dumb")
        }

        testClass<AbstractHighLevelWeigherTest> {
            model("weighers/basic", pattern = KT_OR_KTS_WITHOUT_DOTS)
        }

        testClass<AbstractHighLevelMultiFileJvmBasicCompletionTest> {
            model("basic/multifile", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractK2MultiPlatformCompletionTest> {
            model("multiPlatform/actualDeclaration", isRecursive = false, pattern = DIRECTORY)
            model("multiPlatform/classDeclaration", isRecursive = false, pattern = DIRECTORY)
            model("multiPlatform/functionDeclaration", isRecursive = false, pattern = DIRECTORY)
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

        testClass<AbstractFirWithMppStdlibCompletionTest> {
            model("basic/stdlibWithCommon", isRecursive = false, pattern = KT_WITHOUT_FIR_PREFIX)
        }

        // Smart completion does not work in K2, see KTIJ-26166
        testClass<AbstractK2CompletionIncrementalResolveTest> {
            model("incrementalResolve", excludedDirectories = listOf("smart"))
        }
    }

    testGroup("fir/tests", category = HIGHLIGHTING, testDataPath = "../../code-insight/testData") {
        testClass<AbstractK2MultiModuleLineMarkerTest> {
            model("linemarkers", isRecursive = false, pattern = DIRECTORY)
        }
    }

    testGroup("fir/tests", testDataPath = "../../idea/tests/testData", category = CODE_INSIGHT) {
        testClass<AbstractK2ProjectViewTest> {
            model("projectView", pattern = TEST)
        }

        testClass<AbstractFirFoldingTest> {
            model("folding/noCollapse")
            model("folding/checkCollapse", testMethodName = "doSettingsFoldingTest")
        }
    }

    testGroup("refactorings/rename.k2", testDataPath = "../../idea/tests/testData", category = RENAME_REFACTORING) {
        testClass<AbstractFirRenameTest> {
            model("refactoring/rename", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2InplaceRenameTest> {
            model("refactoring/rename/inplace", pattern = KT, flatten = true)
        }
        testClass<AbstractFirMultiModuleRenameTest> {
            model("refactoring/renameMultiModule", pattern = TEST, flatten = true)
        }
    }

    testGroup("fir/tests", testDataPath = "../../idea/tests/testData/findUsages", category = FIND_USAGES) {
        testClass<AbstractFindUsagesFirTest> {
            model("kotlin", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|kts)$"""))
            model("java", pattern = Patterns.forRegex("""^(.+)\.0\.java$"""))
            model("propertyFiles", pattern = Patterns.forRegex("""^(.+)\.0\.properties$"""))
        }

        testClass<AbstractFindUsagesFirTest>(
            platforms = listOf(
                KMPTestPlatform.Js,
                KMPTestPlatform.NativeLinux,
                //KMPTestPlatform.CommonNativeJvm, TODO should be enabled after KTIJ-29715 is fixed
            ),
            generatedPackagePostfix = "kmpFindUsages",
        ) {
            model("kotlin", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|kts)$"""))
        }

        testClass<AbstractFindUsagesWithDisableComponentSearchFirTest> {
            model("kotlin/conventions/components", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|kts)$"""))
        }

        testClass<AbstractKotlinFindUsagesWithLibraryFirTest> {
            model("libraryUsages", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|java)$"""))
        }

        testClass<AbstractKotlinFindUsagesWithStdlibFirTest> {
            model("stdlibUsages", pattern = Patterns.forRegex("""^(.+)\.0\.kt$"""))
        }

        testClass<AbstractKotlinGroupUsagesBySimilarityFirTest> {
            model("similarity/grouping", pattern = KT)
        }

        testClass<AbstractKotlinGroupUsagesBySimilarityFeaturesFirTest> {
            model("similarity/features", pattern = KT)
        }

        testClass<AbstractKotlinScriptFindUsagesFirTest> {
            model("kotlinScript", pattern = Patterns.forRegex("""^(.+)\.0\.kts$"""))
        }

        testClass<AbstractFindUsagesMultiModuleFirTest> {
            model("../multiModuleFindUsages", isRecursive = false, pattern = DIRECTORY)
        }
    }

    testGroup("fir/tests", category = CODE_INSIGHT) {
        testClass<AbstractFirQuickDocTest> {
            model("../../../idea/tests/testData/editor/quickDoc", pattern = Patterns.forRegex("""^([^_]+)\.(kt|java)$"""), isRecursive = false)
        }
        testClass<AbstractFirQuickDocMultiplatformTest> {
            model("../../../idea/tests/testData/editor/quickDoc/multiplatform", pattern = Patterns.forRegex("""^([^_]+)\.(kt|java)$"""))
        }
    }

    testGroup("fir/tests") {
        testClass<AbstractK2ReferenceResolveWithResolveExtensionTest> {
            model("extensions/references", pattern = KT_WITHOUT_DOTS)
        }
        testClass<AbstractAdditionalKDocResolutionProviderTest> {
            model("resolve/additionalKDocReference", pattern = KT_WITHOUT_DOTS)
        }
    }

    testGroup("fir/tests", category = HIGHLIGHTING) {
        testClass<AbstractHLImplementationSearcherTest> {
            model("search/implementations", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractScopeEnlargerTest> {
            model("search/scopeEnlarger", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractKotlinBuiltInsResolveScopeEnlargerTest> {
            model("search/builtInsScopeEnlarger", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractK2MultiModuleHighlightingTest> {
            model("resolve/anchors", isRecursive = false, pattern = Patterns.forRegex("^([^\\._]+)$"))
        }

        testClass<AbstractK2KmpLightFixtureHighlightingTest> {
            model("kmp/highlighting", pattern = KT_WITHOUT_DOTS)
        }
    }

    testGroup("fir/tests", category = COMPLETION) {
        testClass<AbstractK2JvmBasicCompletionTestWithResolveExtension> {
            model("extensions/completion", pattern = KT_WITHOUT_DOTS)
        }
    }

    testGroup("uast/uast-kotlin-fir/tests") {
        testClass<AbstractFirUastDeclarationTest> {
            model("declaration", pattern = KT_OR_KTS)
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

    testGroup("copyright/fir-tests", testDataPath = "../../copyright/tests/testData", category = CODE_INSIGHT) {
        testClass<AbstractFirUpdateKotlinCopyrightTest> {
            model("update", pattern = KT_OR_KTS, testMethodName = "doTest")
        }
    }

    testGroup("j2k/k2/tests", testDataPath = "../../shared/tests/testData", category = J2K) {
        testClass<AbstractK2JavaToKotlinConverterSingleFileTest> {
            model("newJ2k", pattern = Patterns.forRegex("""^([^.]+)\.java$"""))
        }

        testClass<AbstractK2JavaToKotlinConverterSingleFileFullJDKTest> {
            model("fullJDK", pattern = Patterns.forRegex("""^([^.]+)\.java$"""))
        }

        testClass<AbstractK2JavaToKotlinConverterPartialTest> {
            model("partialConverter", pattern = Patterns.forRegex("""^([^.]+)\.java$"""))
        }

        testClass<AbstractK2JavaToKotlinConverterMultiFileTest>(commonSuite = false) {
            model("multiFile", pattern = DIRECTORY, isRecursive = false)
        }
    }
}
