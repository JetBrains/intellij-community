// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.IntentionsOrderProvider
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertEqualsToFile
import junit.framework.TestCase
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.K1_ACTIONS_LIST_DIRECTIVE
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.K2_ACTIONS_LIST_DIRECTIVE
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import java.io.File
import kotlin.test.assertTrue


object DirectiveBasedActionUtils {
    const val DISABLE_ERRORS_DIRECTIVE: String = "// DISABLE_ERRORS"
    const val DISABLE_WARNINGS_DIRECTIVE: String = "// DISABLE_WARNINGS"
    const val ENABLE_WARNINGS_DIRECTIVE: String = "// ENABLE_WARNINGS"
    const val PRIORITY_DIRECTIVE = "PRIORITY"

    const val ERROR_DIRECTIVE: String = "// ERROR:"
    const val AFTER_ERROR_DIRECTIVE: String = "// AFTER_ERROR:"

    /**
     * If present in the test data file, checks that
     * - the corresponding quickfix is available at the <caret>,         t
     * - all other quickfixes with names not mentioned in "//ACTION" are not available.
     * When no "// ACTION" directives are present in the file, quickfixes are not checked.
     */
    const val K1_ACTIONS_LIST_DIRECTIVE: String = "// ACTION:"
    const val K2_ACTIONS_LIST_DIRECTIVE: String = "// K2_ACTIONS_LIST:"
    const val IGNORE_IRRELEVANT_ACTIONS_DIRECTIVE: String = "// IGNORE_IRRELEVANT_ACTIONS"

    /**
     * If present in the test data file, checks actions are displayed in the same order as specified in a file
     * There is no action order check by default.
     */
    const val KEEP_ACTIONS_LIST_ORDER_DIRECTIVE: String = "// KEEP_ACTIONS_LIST_ORDER"

    fun checkForUnexpectedErrors(
        file: KtFile,
        directive: String = ERROR_DIRECTIVE,
        diagnosticsProvider: (KtFile) -> Diagnostics = { it.analyzeWithContent().diagnostics }
    ) {
        if (InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, DISABLE_ERRORS_DIRECTIVE).isNotEmpty()) {
            return
        }

        checkForUnexpected(file, diagnosticsProvider, directive, "errors", Severity.ERROR)
    }

    fun checkForUnexpectedWarnings(
        file: KtFile,
        disabledByDefault: Boolean = true,
        directiveName: String = Severity.WARNING.name,
        diagnosticsProvider: (KtFile) -> Diagnostics = { it.analyzeWithContent().diagnostics }
    ) {
        if (disabledByDefault && InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, ENABLE_WARNINGS_DIRECTIVE).isEmpty() ||
            !disabledByDefault && InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, DISABLE_WARNINGS_DIRECTIVE).isNotEmpty()
        ) {
            return
        }

        checkForUnexpected(file, diagnosticsProvider, "// $directiveName:", "warnings", Severity.WARNING)
    }

    private fun checkForUnexpected(
        file: KtFile,
        diagnosticsProvider: (KtFile) -> Diagnostics,
        directive: String,
        name: String,
        severity: Severity,
    ) {
        val expected = InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, directive)
            .sorted()
            .map { "$directive $it" }

        val diagnostics = diagnosticsProvider(file)
        val actual = diagnostics
            .filter { it.severity == severity }
            .map { "$directive ${DefaultErrorMessages.render(it).replace("\n", "<br>")}" }
            .sorted()

        if (actual.isEmpty() && expected.isEmpty()) return

        UsefulTestCase.assertOrderedEquals(
            "All actual $name should be mentioned in test data with '$directive' directive. " +
                    "But no unnecessary $name should be mentioned, file:\n${file.text}",
            actual,
            expected,
        )
    }

    fun inspectionChecks(name: String, file: PsiFile) {
        val inspectionNames = InTextDirectivesUtils.findLinesWithPrefixesRemoved(
            /* fileText = */ file.text,
            /* ...prefixes = */ "// INSPECTION-CLASS:",
        ).ifEmpty { return }

        val inspectionManager = InspectionManager.getInstance(file.project)
        val inspections = inspectionNames.map { Class.forName(it).getDeclaredConstructor().newInstance() as AbstractKotlinInspection }
        val problems = mutableListOf<ProblemDescriptor>()
        ProgressManager.getInstance().executeProcessUnderProgress(
            /* process = */ {
                for (inspection in inspections) {
                    problems += inspection.processFile(
                        file,
                        inspectionManager
                    )
                }
            },
            /* progress = */ DaemonProgressIndicator(),
        )

        val directive = "INSPECTION"
        val expected = InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, "// $directive:")
            .sorted()
            .map { "$directive $it" }

        val noInspectionOutputDirective = "NO-INSPECTION-OUTPUT"
        val noInspectionOutput = InTextDirectivesUtils.isDirectiveDefined(file.text, "// $noInspectionOutputDirective")

        val actual = problems
            // lineNumber is 0-based
            .map { "$directive [${it.highlightType.name}:${it.lineNumber + 1}] $it" }
            .sorted()

        if (actual.isEmpty() && expected.isEmpty()) {
            assertTrue(noInspectionOutput, "`$noInspectionOutputDirective` directive has to be defined if there is nothing to report")
            return
        }

        KotlinLightCodeInsightFixtureTestCaseBase.assertOrderedEquals(
            "All actual $name should be mentioned in test data with '$directive' directive. " +
                    "But no unnecessary $name should be mentioned, file:\n${file.text}",
            actual,
            expected,
        )
    }

    fun checkAvailableActionsAreExpected(
        psiFile: PsiFile,
        file: File,
        availableActions: Collection<IntentionAction>,
        actionsListDirectives: Array<String> = arrayOf(K1_ACTIONS_LIST_DIRECTIVE),
    ) {
        checkAvailableActionsAreExpected(
            psiFile = psiFile,
            file = file,
            availableActions = availableActions,
            actionsToExclude = emptyList(),
            actionsListDirectives = actionsListDirectives,
        )
    }

    fun checkAvailableActionsAreExpected(
        psiFile: PsiFile,
        file: File,
        availableActions: Collection<IntentionAction>,
        actionsToExclude: List<String>,
        actionsListDirectives: Array<String> = arrayOf(K1_ACTIONS_LIST_DIRECTIVE),
    ) {
        val fileText = file.readText()
        checkAvailableActionsAreExpected(
            psiFile = psiFile,
            fileText = fileText,
            availableActions = availableActions,
            actionsToExclude = actionsToExclude,
            actionListDirectives = actionsListDirectives
        ) { actionDirective, expectedActionsDirectives, actualActionsDirectives ->
            if (expectedActionsDirectives != actualActionsDirectives) {
                val actual = fileText.let { text ->
                    val lines = text.split('\n')
                    val firstActionIndex = lines.indexOfFirst { it.startsWith(actionDirective) }.takeIf { it != -1 }
                    val textWithoutActions = lines.filterNot { it.startsWith(actionDirective) }
                    textWithoutActions.subList(0, firstActionIndex ?: 1)
                        .plus(actualActionsDirectives)
                        .plus(textWithoutActions.drop(firstActionIndex ?: 1))
                        .joinToString("\n")
                }
                assertEqualsToFile(
                    description = "Some unexpected actions available at current position. Use '$actionDirective' directive in $file",
                    expected = file,
                    actual = actual
                )
            }
        }
    }

    fun checkAvailableActionsAreExpected(psiFile: PsiFile, availableActions: Collection<IntentionAction>) {
        checkAvailableActionsAreExpected(
            psiFile,
            psiFile.text,
            availableActions,
            actionListDirectives = arrayOf(K1_ACTIONS_LIST_DIRECTIVE)
        ) { actionDirective, expectedDirectives, actualActionsDirectives ->
            UsefulTestCase.assertOrderedEquals(
                "Some unexpected actions available at current position. Use '$actionDirective' directive\n",
                actualActionsDirectives,
                expectedDirectives
            )
        }
    }

    fun checkPriority(contents: String, action: Any) {
        val priorityName = InTextDirectivesUtils.findStringWithPrefixes(contents, "// $PRIORITY_DIRECTIVE: ")
        if (priorityName != null) {
            val expectedPriority = enumValueOf<PriorityAction.Priority>(priorityName)
            val actualPriority = (action as? PriorityAction)?.priority
            TestCase.assertTrue(
                "Expected action priority: $expectedPriority\nActual priority: $actualPriority",
                expectedPriority == actualPriority
            )
        }
    }

    private fun checkAvailableActionsAreExpected(
        psiFile: PsiFile,
        fileText: String,
        availableActions: Collection<IntentionAction>,
        actionsToExclude: List<String> = emptyList(),
        actionListDirectives: Array<String>,
        assertion: (actionListDirective: String, expectedDirectives: List<String>, actualActionsDirectives: List<String>) -> Unit,
    ) {
        if (InTextDirectivesUtils.isDirectiveDefined(fileText, IGNORE_IRRELEVANT_ACTIONS_DIRECTIVE)) {
            return
        }

        val (actionListDirective, expectedActions) = actionListDirectives.firstNotNullOfOrNull { actionDirective ->
            val expectedActions = InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, actionDirective)
            // do not check for available fixes if there are no // ACTION
            if (expectedActions.isNotEmpty()) {
                actionDirective to expectedActions
            } else {
                null
            }
        } ?: return

        UsefulTestCase.assertEmpty(
            "Irrelevant actions should not be specified in $actionListDirective directive for they are not checked anyway",
            expectedActions.filter(::isIrrelevantAction),
        )

        val keepActionsOrder = InTextDirectivesUtils.isDirectiveDefined(fileText, KEEP_ACTIONS_LIST_ORDER_DIRECTIVE)
        val actualActions = if (keepActionsOrder) {
            val cachedIntentions = CachedIntentions(psiFile.project, psiFile, null)

            val intentionsOrder = IntentionsOrderProvider.EXTENSION.forLanguage(KotlinLanguage.INSTANCE)
            val sortedIntentions =
                intentionsOrder.getSortedIntentions(cachedIntentions, availableActions.map { IntentionActionWithTextCaching(it) })
            sortedIntentions.map { it.text }.filterOutElementsToExclude(actionsToExclude)
        } else {
            availableActions.map { it.text }.filterOutElementsToExclude(actionsToExclude).sorted()
        }


        val expectedActionsInOrder = if (keepActionsOrder) {
            expectedActions
        } else {
            expectedActions.sorted()
        }

        val expectedActionsDirectives =
            expectedActionsInOrder.filterOutElementsToExclude(actionsToExclude).map { "$actionListDirective $it" }

        val actualActionsDirectives = filterOutIrrelevantActions(actualActions).map { "$actionListDirective $it" }
        assertion(actionListDirective, expectedActionsDirectives, actualActionsDirectives)
    }

    // TODO: Some missing K2 actions are missing. We filter out them out to avoid the test failure caused by the exact action list match.
    //       Remove this list when they are ready. See ACTIONS_NOT_IMPLEMENTED and ACTIONS_DIFFERENT_FROM_K1 in AbstractK2QuickFixTest.
    private fun List<String>.filterOutElementsToExclude(elementsToExclude: List<String>) = if (elementsToExclude.isEmpty()) {
        this
    } else {
        filter { element -> !elementsToExclude.any { element == it } }
    }

    //TODO: hack, implemented because irrelevant actions behave in different ways on build server and locally
    // this behaviour should be investigated and hack can be removed
    private fun filterOutIrrelevantActions(actions: Collection<String>): Collection<String> {
        return actions.filter { !isIrrelevantAction(it) }
    }

    private fun isIrrelevantAction(action: String) = action.isEmpty() || IRRELEVANT_ACTION_PREFIXES.any { action.startsWith(it) }

    private val IRRELEVANT_ACTION_PREFIXES = listOf(
        "Disable ",
        "Edit intention settings",
        "Edit inspection profile setting",
        "Inject language or reference",
        "Suppress '",
        "Run inspection on",
        "Inspection '",
        "Suppress for ",
        "Suppress all ",
        "Edit cleanup profile settings",
        "Fix all '",
        "Cleanup code",
        "Go to ",
        "Show local variable type hints",
        "Show function return type hints",
        "Show property type hints",
        "Show parameter type hints",
        "Show argument name hints",
        "Show hints for suspending calls",
        "Add 'JUnit",
        "Add 'testng",
        "AI",
        "ChatGPT",
        "Codex",
        "LLM",
        CodeInsightBundle.message("assign.intention.shortcut"),
        CodeInsightBundle.message("edit.intention.shortcut"),
        CodeInsightBundle.message("remove.intention.shortcut"),
    )
}

val KotlinPluginMode.actionsListDirectives: Array<String>
    get() = if (this == KotlinPluginMode.K1) {
        arrayOf(K1_ACTIONS_LIST_DIRECTIVE)
    } else {
        arrayOf(K2_ACTIONS_LIST_DIRECTIVE, K1_ACTIONS_LIST_DIRECTIVE)
    }
