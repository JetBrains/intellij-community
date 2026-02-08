// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.DataManager
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.IntroduceParameterRefactoring
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import com.intellij.refactoring.introduceField.ElementToWorkOn
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor
import com.intellij.refactoring.introduceParameter.Util
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.JavaNameSuggestionUtil
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionOptions
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.idea.test.util.findElementByCommentPrefix
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.Assert
import java.io.File

const val DUMMY_FUN_NAME = "__dummyTestFun__"

abstract class AbstractExtractionTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = JAVA_LATEST

    override fun setUp() {
        super.setUp()
        Registry.Companion.get("kotlin.extract.function.default.name").setValue(DUMMY_FUN_NAME, testRootDisposable)
    }

    val fixture: JavaCodeInsightTestFixture get() = myFixture

    protected abstract fun getIntroduceVariableHandler(): RefactoringActionHandler

    protected abstract fun getExtractFunctionHandler(
        explicitPreviousSibling: PsiElement?,
        expectedNames: List<String>,
        expectedReturnTypes: List<String>,
        expectedDescriptors: String,
        expectedTypes: String,
        acceptAllScopes: Boolean,
        extractionOptions: ExtractionOptions
    ): AbstractExtractKotlinFunctionHandler

    protected open fun doIntroduceVariableTest(unused: String) {
        doTestIfNotDisabledByFileDirective(isIntroduceVariableTest = true) { file ->
            TemplateManagerImpl.setTemplateTesting(getTestRootDisposable())

            file as KtFile

            val introduceVariableHandler =
                LanguageRefactoringSupport.getInstance()
                    .forLanguage(KotlinLanguage.INSTANCE).introduceVariableHandler as KotlinIntroduceVariableHandler
            introduceVariableHandler.occurenceReplaceChoice =
                if (InTextDirectivesUtils.isDirectiveDefined(file.text, "// REPLACE_SINGLE_OCCURRENCE")) {
                    OccurrencesChooser.ReplaceChoice.NO
                } else {
                    OccurrencesChooser.ReplaceChoice.ALL
                }

            KotlinCommonRefactoringSettings.Companion.getInstance().INTRODUCE_SPECIFY_TYPE_EXPLICITLY =
                InTextDirectivesUtils.isDirectiveDefined(file.text, "// SPECIFY_TYPE_EXPLICITLY")

            getIntroduceVariableHandler().invoke(
                fixture.project,
                fixture.editor,
                file,
                DataManager.getInstance().getDataContext(fixture.editor.component)
            )
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

            val inplaceVariableNames = InTextDirectivesUtils.findListWithPrefixes(file.text, "// INPLACE_VARIABLE_NAME:")
                .takeUnless { it.isEmpty() }
            val templateState = TemplateManagerImpl.getTemplateState(editor)

            if (inplaceVariableNames != null) {
                templateState as TemplateState

                WriteCommandAction.runWriteCommandAction(project) {
                    for (inplaceVariableName in inplaceVariableNames) {
                        val range = templateState.currentVariableRange ?: error("No variable range was found")
                        templateState.editor.document.replaceString(range.startOffset, range.endOffset, inplaceVariableName)
                        templateState.nextTab()
                    }
                }
            }

            if (templateState?.isFinished == false) {
                project.executeCommand("") { templateState.gotoEnd(false) }
            }
        }
    }

    protected abstract fun doExtractSuperTest(unused: String, isInterface: Boolean)

    protected fun doExtractSuperclassTest(path: String) = doExtractSuperTest(path, false)

    protected fun doExtractInterfaceTest(path: String) = doExtractSuperTest(path, true)

    protected fun doIntroduceJavaParameterTest(unused: String) {
        // Copied from com.intellij.refactoring.IntroduceParameterTest.perform()
        doTest(checkAdditionalAfterdata = true) { file ->
            file as PsiJavaFile

            var elementToWorkOn: ElementToWorkOn? = null
            ElementToWorkOn.processElementToWorkOn(
                editor,
                file,
                "Introduce parameter",
                null,
                project,
                object : ElementToWorkOn.ElementsProcessor<ElementToWorkOn> {
                    override fun accept(e: ElementToWorkOn): Boolean {
                        return true
                    }

                    override fun pass(e: ElementToWorkOn?) {
                        if (e != null) {
                            elementToWorkOn = e
                        }
                    }
                })

            val expr = elementToWorkOn!!.expression
            val localVar = elementToWorkOn!!.localVariable

            val context = expr ?: localVar
            val method = Util.getContainingMethod(context) ?: throw AssertionError("No containing method found")

            val applyToSuper = InTextDirectivesUtils.isDirectiveDefined(file.getText(), "// APPLY_TO_SUPER")
            val methodToSearchFor = if (applyToSuper) method.findDeepestSuperMethods().firstIsInstance<KtLightMethod>() else method

            val (initializer, occurrences) =
                if (expr == null) {
                    localVar.initializer!! to CodeInsightUtil.findReferenceExpressions(method, localVar)
                } else {
                    expr to ExpressionOccurrenceManager(expr, method, null).findExpressionOccurrences()
                }
            val type = initializer.type

            val parametersToRemove = Util.findParametersToRemove(method, initializer, occurrences)

            val codeStyleManager = JavaCodeStyleManager.getInstance(project)
            val info = codeStyleManager.suggestUniqueVariableName(
                codeStyleManager.suggestVariableName(VariableKind.PARAMETER, localVar?.name, initializer, type),
                expr,
                true
            )
            val suggestedNames = JavaNameSuggestionUtil.appendUnresolvedExprName(
                JavaCompletionUtil.completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, info),
                initializer
            )

            IntroduceParameterProcessor(
                project,
                method,
                methodToSearchFor,
                initializer,
                expr,
                localVar,
                true,
                suggestedNames.first(),
                IntroduceVariableBase.JavaReplaceChoice.ALL,
                IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE,
                false,
                false,
                false,
                null,
                parametersToRemove
            ).run()

            editor.selectionModel.removeSelection()
        }
    }

    protected abstract fun doIntroducePropertyTest(unused: String)

    protected fun doExtractFunctionTest(unused: String) {
        doTestIfNotDisabledByFileDirective { file -> doExtractFunction(myFixture, file as KtFile) }
    }

    protected fun doIntroduceTypeAliasTest(unused: String) {
        doTestIfNotDisabledByFileDirective { file ->
            file as KtFile

            val explicitPreviousSibling = file.findElementByCommentPrefix("// SIBLING:")
            val fileText = file.text
            val aliasName = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// NAME:")
            val aliasVisibility = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// VISIBILITY:")?.let {
                KtPsiFactory(project).createModifierList(it).firstChild.node.elementType as KtModifierKeywordToken
            }
            val editor = fixture.editor
            getIntroduceTypeAliasHandler(explicitPreviousSibling, aliasName, aliasVisibility).invoke(project, editor, file, null)
        }
    }

    protected abstract fun getIntroduceTypeAliasHandler(
        explicitPreviousSibling: PsiElement?,
        aliasName: String?,
        aliasVisibility: KtModifierKeywordToken?
    ): RefactoringActionHandler

    protected abstract fun doIntroduceConstantTest(unused: String)


    protected fun doTestIfNotDisabledByFileDirective(isIntroduceVariableTest: Boolean = false, action: (PsiFile) -> Unit) {
        val disableTestDirective = IgnoreTests.DIRECTIVES.of(pluginMode)

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            dataFilePath(),
            disableTestDirective,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) { isTestEnabled ->
            doTest(generateMissingFiles = isTestEnabled, action = action, isIntroduceVariableTest = isIntroduceVariableTest)
        }
    }

    protected fun doTest(
        checkAdditionalAfterdata: Boolean = true,
        generateMissingFiles: Boolean = true,
        isIntroduceVariableTest: Boolean = false,
        action: (PsiFile) -> Unit
    ) {
        val mainFile = File(testDataDirectory, fileName())

        PluginTestCaseBase.addJdk(myFixture.projectDisposable, IdeaTestUtil::getMockJdk18)

        if (mainFile.extension == KotlinParserDefinition.STD_SCRIPT_SUFFIX) {
            val virtualFile = VfsUtil.findFileByIoFile(mainFile, true)!!
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile)!!
            updateScriptDependenciesSynchronously(ktFile)
        }

        fixture.testDataPath = mainFile.parent


        val mainFileName = mainFile.name
        val mainFileBaseName = FileUtil.getNameWithoutExtension(mainFileName)
        val extraFiles = mainFile.parentFile.listFiles { _, name ->
            name != mainFileName && name.startsWith("$mainFileBaseName.") && (name.endsWith(".kt") || name.endsWith(".java"))
        }
        val extraFilesToPsi = extraFiles.associateBy { fixture.configureByFile(it.name) }
        val fileText = FileUtil.loadFile(mainFile, true)

        val expectedTargetContainers =
            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// TARGET_CONTAINER: ")

        withCustomCompilerOptions(fileText, project, module) {
            ConfigLibraryUtil.configureLibrariesByDirective(module, fileText)

            val addKotlinRuntime = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// WITH_STDLIB") != null
            if (addKotlinRuntime) {
                ConfigLibraryUtil.configureKotlinRuntimeAndSdk(module, projectDescriptor.sdk!!)
            }

            try {
                val actualTargetContainers = mutableListOf<String>()
                KotlinTestHelpers.registerChooserInterceptor(fixture.testRootDisposable) { options ->
                    actualTargetContainers.clear()
                    actualTargetContainers += options
                    if (isIntroduceVariableTest) options.last() else options.first()
                }

                val extractTestFiles = ExtractTestFiles(mainFile.path, fixture.configureByFile(mainFileName), extraFilesToPsi, isFirPlugin)
                checkExtract(
                    extractTestFiles,
                    checkAdditionalAfterdata,
                    generateMissingFiles,
                    action,
                )

                extractTestFiles.afterFile.takeIf { it.exists() }?.let { afterFile ->
                    val caretAndSelectionState = EditorTestUtil.extractCaretAndSelectionMarkers(DocumentImpl(afterFile.readText()))
                    if (caretAndSelectionState.hasExplicitCaret()) {
                        EditorTestUtil.verifyCaretAndSelectionState(editor, caretAndSelectionState)
                    }
                }

                if (expectedTargetContainers.isNotEmpty()) {
                    assertEquals(expectedTargetContainers, actualTargetContainers)
                }
            } finally {
                ConfigLibraryUtil.unconfigureLibrariesByDirective(module, fileText)

                if (addKotlinRuntime) {
                    ConfigLibraryUtil.unConfigureKotlinRuntimeAndSdk(module, projectDescriptor.sdk!!)
                }
            }
        }
    }

    fun doExtractFunction(fixture: CodeInsightTestFixture, file: KtFile) {
        val explicitPreviousSibling = file.findElementByCommentPrefix("// SIBLING:")
        val fileText = file.getText() ?: ""
        val expectedNames = InTextDirectivesUtils.findListWithPrefixes(fileText, "// SUGGESTED_NAMES: ")
        val expectedReturnTypes = InTextDirectivesUtils.findListWithPrefixes(fileText, "// SUGGESTED_RETURN_TYPES: ")
        val expectedDescriptors =
            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// PARAM_DESCRIPTOR: ").joinToString()
        val expectedTargetContainers =
            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// TARGET_CONTAINER: ")
        val expectedTypes =
            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// PARAM_TYPES: ").joinToString { "[$it]" }

        val extractionOptions = InTextDirectivesUtils.findListWithPrefixes(fileText, "// OPTIONS: ").let {
            if (it.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                val args = it.map(String::toBoolean).toTypedArray() as Array<Any?>
                ExtractionOptions::class.java.constructors.first { ctor -> ctor.parameterTypes.size == args.size }.newInstance(*args) as ExtractionOptions
            } else ExtractionOptions.DEFAULT
        }

        val editor = fixture.editor
        val handler =
            getExtractFunctionHandler(
                explicitPreviousSibling,
                expectedNames,
                expectedReturnTypes,
                expectedDescriptors,
                expectedTypes,
                acceptAllScopes = expectedTargetContainers.isNotEmpty(),
                extractionOptions
            )
        handler.selectElements(editor, file) { elements, previousSibling ->
            handler.doInvoke(editor, file, elements, explicitPreviousSibling ?: previousSibling)
        }
    }

    fun checkExtract(
        files: ExtractTestFiles,
        checkAdditionalAfterdata: Boolean = false,
        generateMissingFiles: Boolean = true,
        action: (PsiFile) -> Unit
    ) {
        val conflictFile = files.conflictFile
        val afterFile = files.afterFile

        try {
            runInEdtAndWait {
                runReadAction {
                    updateScriptDependenciesSynchronously(files.mainFile)
                }
            }

            action(files.mainFile)

            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

            assert(!conflictFile.exists()) { "Conflict file $conflictFile should not exist" }
            KotlinTestUtils.assertEqualsToFile(afterFile, files.mainFile.text!!) { it.removeCaret() }

            if (checkAdditionalAfterdata) {
                for ((extraPsiFile, extraFile) in files.extraFilesToPsi) {
                    val expectedFile = getAfterFile(extraFile.path, files.isFirPlugin)
                    KotlinTestUtils.assertEqualsToFile(expectedFile, extraPsiFile.text)
                }
            }
        } catch (e: Throwable) {
            val message = when {
                e is BaseRefactoringProcessor.ConflictsInTestsException -> {
                    e.messages.sorted().joinToString(" ")
                }

                e is CommonRefactoringUtil.RefactoringErrorHintException ||
                        e is TestLoggerFactory.TestLoggerAssertionError ||
                        e is RuntimeException && e::class.java == RuntimeException::class.java -> e.message!!

                else -> throw e
            }.replace("\n", " ")

            assertEqualsToFile(conflictFile, message, generateMissingFiles)
        }
    }

    protected abstract fun updateScriptDependenciesSynchronously(psiFile: PsiFile)

}

class ExtractTestFiles(
    val mainFile: PsiFile,
    val afterFile: File,
    val conflictFile: File,
    val extraFilesToPsi: Map<PsiFile, File> = emptyMap(),
    val isFirPlugin: Boolean,
) {
    constructor(path: String, mainFile: PsiFile, extraFilesToPsi: Map<PsiFile, File> = emptyMap(), isFirPlugin: Boolean) :
            this(mainFile, getAfterFile(path, isFirPlugin), getConflictsFile(path, isFirPlugin), extraFilesToPsi, isFirPlugin)


}

private fun getConflictsFile(path: String, isFirPlugin: Boolean): File {
    if (isFirPlugin) {
        val firSpecific = File("$path.fir.conflicts")
        //if fir specific after exists, ignore k1 conflicts if any
        if (firSpecific.exists() || File("$path.fir.after").exists()) {
            return firSpecific
        }
    }
    return File("$path.conflicts")
}

private fun getAfterFile(path: String, isFirPlugin: Boolean): File {
    var file = File("$path.after")
    if (isFirPlugin) {
        val firSpecific = File("$path.fir.after")
        if (firSpecific.exists()) {
            file = firSpecific
        }
    }
    return file
}

private fun String.removeCaret(): String = replace("<caret>", "")

private fun assertEqualsToFile(expectedFile: File, actualText: String, generateMissingFiles: Boolean) {
    if (!generateMissingFiles && !expectedFile.exists()) {
        Assert.fail("Expected data file doesn't exist: $expectedFile")
    }
    KotlinTestUtils.assertEqualsToFile(expectedFile, actualText)
}