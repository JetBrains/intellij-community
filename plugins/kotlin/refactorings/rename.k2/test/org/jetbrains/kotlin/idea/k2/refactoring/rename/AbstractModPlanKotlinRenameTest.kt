// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanExecutionResult
import com.intellij.refactoring.modplan.ModPlanExecutor
import com.intellij.refactoring.modplan.ModPlanOption
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.testFramework.ModPlanRenameTestSupport
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches
import java.util.concurrent.Callable

/**
 * The base of the tests of the rename on the ModPlan engine in a Kotlin project. The plan is made on a background thread, as the MCP tool
 * makes it, and the write runs on the EDT.
 */
abstract class AbstractModPlanKotlinRenameTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): KotlinLightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        allowAnalysisOnEdt { super.runTestRunnable(testRunnable) }
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    protected fun plan(element: PsiElement, newName: String, options: ModPlanOptionValues = ModPlanOptionValues.EMPTY): ModPlan.AcceptedPlan {
        lastRequest = runReadActionBlocking { ModPlanRenameRefactoring.request(project, element, newName, substitute = true, options) }
        return assertInstanceOf(ModPlanRenameTestSupport.planOnModPlan(project, element, newName, options), ModPlan.AcceptedPlan::class.java)
    }

    /** The request of the last [plan]. */
    private var lastRequest: ModPlanRequest? = null

    /**
     * @return the plan of the request of the last [plan] without [excludedUsages], as the flow plans after a preview; call it in
     * [execute] or [inBackground]
     */
    protected fun planWithout(excludedUsages: List<SmartPsiFileRange>): ModPlan {
        val request = lastRequest!!
        val excluded = runReadActionBlocking { ModPlanRefactoring.excludedUsagesOf(excludedUsages) }
        return ModPlanRenameRefactoring.getInstance()
            .plan(ModPlanChain.withOptionValues(request, request.optionValues().with(ModPlanRefactoring.EXCLUDED_USAGES, excluded)))
    }

    /** Writes the plan that [plan] makes on a background thread. */
    protected fun execute(plan: () -> ModPlan) {
        val accepted = assertInstanceOf(inBackground(plan), ModPlan.AcceptedPlan::class.java)
        assertFalse("unexpected conflicts: ${accepted.conflicts.conflicts.map { it.messages }}", accepted.conflicts.needsConfirmation())
        assertInstanceOf(ModPlanExecutor.execute(accepted.execution, true), ModPlanExecutionResult.Applied::class.java)
    }

    /** @return the places of [plan] in [file] that hold [offset] */
    protected fun placesAt(plan: ModPlan.AcceptedPlan, file: PsiFile, offset: Int): List<SmartPsiFileRange> {
        val places = plan.usages.filter { place ->
            val range = place.range
            place.virtualFile == file.virtualFile && range != null && range.startOffset <= offset && offset < range.endOffset
        }
        assertNotEmpty(places)
        return places
    }

    protected fun <T> inBackground(action: () -> T): T =
        PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable { runReadActionBlocking(action) }))

    protected fun relatedOn(): ModPlanOptionValues = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.RENAME_RELATED, true)

    protected fun renamerSwitch(factoryClass: Class<out AutomaticRenamerFactory>): ModPlanOption<Boolean> =
        ModPlanRenameOptions.optionForRenamer(AutomaticRenamerFactory.EP_NAME.findExtensionOrFail(factoryClass))!!

    protected inline fun <reified T : PsiNamedElement> declaration(file: PsiFile, name: String): T =
        PsiTreeUtil.findChildrenOfType(file, T::class.java).single { it.name == name }
}
