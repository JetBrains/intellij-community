// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics



object DirectiveBasedActionUtils {
    const val DISABLE_ERRORS_DIRECTIVE = "// DISABLE-ERRORS"
    const val DISABLE_WARNINGS_DIRECTIVE = "// DISABLE-WARNINGS"
    const val ENABLE_WARNINGS_DIRECTIVE = "// ENABLE-WARNINGS"
    const val ACTION_DIRECTIVE = "// ACTION:"

    fun checkForUnexpectedErrors(file: KtFile, diagnosticsProvider: (KtFile) -> Diagnostics = { it.analyzeWithContent().diagnostics }) {
        if (InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, DISABLE_ERRORS_DIRECTIVE).isNotEmpty()) {
            return
        }

        checkForUnexpected(file, diagnosticsProvider, "// ERROR:", "errors", Severity.ERROR)
    }

    fun checkForUnexpectedWarnings(
        file: KtFile,
        disabledByDefault: Boolean = true,
        directiveName: String = Severity.WARNING.name,
        diagnosticsProvider: (KtFile) -> Diagnostics = { it.analyzeWithContent().diagnostics }
    ) {
        if (disabledByDefault && InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, ENABLE_WARNINGS_DIRECTIVE).isEmpty() ||
                !disabledByDefault && InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, DISABLE_WARNINGS_DIRECTIVE).isNotEmpty()) {
            return
        }

        checkForUnexpected(file, diagnosticsProvider, "// $directiveName:", "warnings", Severity.WARNING)
    }

    private fun checkForUnexpected(
        file: KtFile,
        diagnosticsProvider: (KtFile) -> Diagnostics,
        directive: String,
        name: String,
        severity: Severity
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
                    "But no unnecessary $name should be me mentioned, file:\n${file.text}",
            actual,
            expected
        )
    }

    fun inspectionChecks(name: String, file: PsiFile) {
        InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, "// INSPECTION-CLASS:").takeIf { it.isNotEmpty() }?.let { inspectionNames ->
            val inspectionManager = InspectionManager.getInstance(file.project)
            val inspections = inspectionNames.map { Class.forName(it).getDeclaredConstructor().newInstance() as AbstractKotlinInspection }

            val problems = mutableListOf<ProblemDescriptor>()
            ProgressManager.getInstance().executeProcessUnderProgress(
                {
                    for (inspection in inspections) {
                        problems += inspection.processFile(
                            file,
                            inspectionManager
                        )
                    }
                }, DaemonProgressIndicator()
            )
            val directive = "// INSPECTION:"
            val expected = InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, directive)
                .sorted()
                .map { "$directive $it" }

            val actual = problems
                // lineNumber is 0-based
                .map { "$directive [${it.highlightType.name}:${it.lineNumber + 1}] $it" }
                .sorted()

            if (actual.isEmpty() && expected.isEmpty()) return

            KotlinLightCodeInsightFixtureTestCaseBase.assertOrderedEquals(
                "All actual $name should be mentioned in test data with '$directive' directive. " +
                        "But no unnecessary $name should be me mentioned, file:\n${file.text}",
                actual,
                expected
            )
        }
    }

    fun checkAvailableActionsAreExpected(file: PsiFile, availableActions: Collection<IntentionAction>) {
        val expectedActions = InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, ACTION_DIRECTIVE).sorted()

        UsefulTestCase.assertEmpty("Irrelevant actions should not be specified in $ACTION_DIRECTIVE directive for they are not checked anyway",
                                   expectedActions.filter { isIrrelevantAction(it) })

        if (InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, "// IGNORE_IRRELEVANT_ACTIONS").isNotEmpty()) {
            return
        }

        val actualActions = availableActions.map { it.text }.sorted()

        UsefulTestCase.assertOrderedEquals(
            "Some unexpected actions available at current position. Use '$ACTION_DIRECTIVE' directive",
            filterOutIrrelevantActions(actualActions).map { "$ACTION_DIRECTIVE $it" },
            expectedActions.map { "$ACTION_DIRECTIVE $it" }
        )
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
        CodeInsightBundle.message("assign.intention.shortcut"),
        CodeInsightBundle.message("edit.intention.shortcut"),
        CodeInsightBundle.message("remove.intention.shortcut"),
    )
}
