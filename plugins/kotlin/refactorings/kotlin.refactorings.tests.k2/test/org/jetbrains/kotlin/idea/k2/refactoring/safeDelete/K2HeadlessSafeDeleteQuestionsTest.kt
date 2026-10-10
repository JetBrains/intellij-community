// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanExecutionResult
import com.intellij.refactoring.modplan.ModPlanExecutor
import com.intellij.refactoring.modplan.ModPlanOption
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ModPlanTestUtil
import com.intellij.testFramework.SafeDeleteTestSupport.headlessSafeDelete
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import java.util.concurrent.Callable

/**
 * The questions of the Kotlin provider, which the interactive safe delete asks in dialogs, and the cases that the test data of
 * [AbstractK2HeadlessSafeDeleteTest] cannot express: several targets, a file in another package, a non-code file.
 */
class K2HeadlessSafeDeleteQuestionsTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun `test a parameter in a hierarchy asks to delete the hierarchy`() {
        myFixture.configureByText(
            "a.kt",
            """
            open class Base { open fun f(p: Int) {} }
            class Impl : Base() { override fun f(p: Int) {} }
            """.trimIndent()
        )
        val parameter = function("f", "Base").valueParameters.single()

        val hierarchyQuestions = questionsOf(parameter).filter { ModPlanSafeDeleteOptions.DELETE_HIERARCHY in optionsOf(it) }
        assertSize(1, hierarchyQuestions)
    }

    fun `test an overriding function does not ask the hierarchy question`() {
        configureBaseAndImpl()

        val askedOptions = questionsOf(function("f", "Impl")).flatMap { optionsOf(it) }

        assertFalse(ModPlanSafeDeleteOptions.DELETE_HIERARCHY in askedOptions)
    }

    fun `test an overriding function asks about its base function`() {
        configureBaseAndImpl()

        assertTrue(asksAboutSuperDeclarations(questionsOf(function("f", "Impl"))))
        assertFalse(asksAboutSuperDeclarations(questionsOf(function("f", "Base"))))
    }

    fun `test the base declaration is not asked about without related`() {
        configureBaseAndImpl()

        assertFalse(asksAboutSuperDeclarations(questionsOf(function("f", "Impl"), related = false)))
    }

    fun `test choosing the base declaration of a library ends the question`() {
        myFixture.configureByText("a.kt", "class Named { override fun toString() = \"named\" }")
        val question = questionsOf(function("toString", "Named")).filterIsInstance<ModPlanQuestion.Custom>()
            .single { ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS in it.options() }

        val message = try {
            question.ask(project, ModPlanOptionValues.EMPTY)
            null
        } catch (e: RuntimeException) {
            e.message
        }

        assertTrue(message, message != null && message.contains("read-only"))
    }

    fun `test an actual declaration asks to delete the expected one`() {
        myFixture.configureByText(
            "a.kt",
            """
            actual fun foo() {}
            fun bar() {}
            """.trimIndent()
        )

        val actualOptions = questionsOf(function("foo")).flatMap { optionsOf(it) }
        val plainOptions = questionsOf(function("bar")).flatMap { optionsOf(it) }

        assertTrue(ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL in actualOptions)
        assertFalse(ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL in plainOptions)
    }

    fun `test only the declarations that are named delete their base declaration`() {
        myFixture.configureByText(
            "a.kt",
            """
            open class Base { open fun f() {}; open fun g() {} }
            class Impl : Base() { override fun f() {}; override fun g() {} }
            """.trimIndent()
        )
        val optionValues = ModPlanOptionValues.EMPTY
            .with(ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS, true)
            .with(ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF, "Impl.f()")

        headlessSafeDelete(project, listOf(function("f", "Impl"), function("g", "Impl")), optionValues, ignoreConflicts = true)

        assertFalse(file().text, file().text.contains("open fun f()"))
        assertTrue(file().text, file().text.contains("open fun g()"))
        assertFalse(file().text, file().text.contains("override fun"))
    }

    fun `test the answers of two super declaration questions add up`() {
        myFixture.configureByText(
            "a.kt",
            """
            open class Base { open fun f() {}; open fun g() {} }
            class Impl : Base() { override fun f() {}; override fun g() {} }
            """.trimIndent()
        )
        fun questionOf(function: KtNamedFunction) = questionsOf(function).filterIsInstance<ModPlanQuestion.Custom>()
            .single { ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS in it.options() }

        val firstAnswers = questionOf(function("f", "Impl")).ask(project, ModPlanOptionValues.EMPTY)!!
        val bothAnswers = questionOf(function("g", "Impl")).ask(project, firstAnswers)!!

        assertEquals(setOf("Impl.f()", "Impl.g()"), bothAnswers.get(ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF).lines().toSet())
        assertEquals(setOf("Impl.f()", "Impl.g()"), bothAnswers.answeredKeys(ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF))
        assertFalse("the dialog leaves the general answer to the request", bothAnswers.has(ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS))
    }

    fun `test a base declaration deleted with its override is not asked about`() {
        configureBaseAndImpl()
        val targets = listOf(function("f", "Impl"), function("f", "Base"))

        val askedAboutSuperDeclarations = asksAboutSuperDeclarations(questionsOf(targets))
        headlessSafeDelete(project, targets, ModPlanOptionValues.EMPTY, ignoreConflicts = true)

        assertFalse(askedAboutSuperDeclarations)
        assertFalse(file().text, file().text.contains("fun f()"))
    }

    /** As the legacy optimize imports helper, the imports that the deletion leaves unused go too. */
    fun `test the import of the deleted parameter type is removed`() {
        myFixture.addFileToProject("p/Bar.kt", "package p\n\nclass Bar")
        myFixture.configureByText(
            "a.kt",
            """
            import p.Bar

            fun run(bar: Bar) {}
            """.trimIndent()
        )

        headlessSafeDelete(project, listOf(function("run").valueParameters.single()), ModPlanOptionValues.EMPTY)

        assertFalse(file().text, file().text.contains("import p.Bar"))
    }

    fun `test a mention in a non-code file is a note and stays`() {
        val kotlinFile = myFixture.addFileToProject("p/Worker.kt", "package p\n\nclass Worker\nclass Kept\n")
        val notes = myFixture.addFileToProject("notes.txt", "p.Worker is mentioned here")
        val worker = runReadActionBlocking { PsiTreeUtil.findChildrenOfType(kotlinFile, KtClass::class.java).single { it.name == "Worker" } }

        val acceptedPlan = assertInstanceOf(
            planOf(worker, ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, true)),
            ModPlan.AcceptedPlan::class.java
        )

        assertFalse("a mention in a non-code file does not block the deletion", acceptedPlan.conflicts.needsConfirmation())
        assertEquals(notes.virtualFile, acceptedPlan.conflicts.nonCodeUsages().single().location!!.virtualFile)
        assertInstanceOf(ModPlanExecutor.execute(acceptedPlan.execution, true), ModPlanExecutionResult.Applied::class.java)
        assertFalse(kotlinFile.text, kotlinFile.text.contains("class Worker"))
        assertEquals("p.Worker is mentioned here", notes.text)
    }

    fun `test the base declaration question that the request answers is not asked`() {
        configureBaseAndImpl()
        val answeredValues = ModPlanOptionValues.EMPTY
            .with(ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS, true)
            .with(ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF, "Impl.f()")

        assertTrue(asksAboutSuperDeclarations(questionsOf(function("f", "Impl"))))
        assertFalse(asksAboutSuperDeclarations(questionsOf(function("f", "Impl"), optionValues = answeredValues)))
    }

    /** The keys of the answers add up, so a list of other declarations does not answer the question of this one. */
    fun `test the base declaration question is asked when the request names only another declaration`() {
        myFixture.configureByText(
            "a.kt",
            """
            open class Base { open fun f() {}; open fun g() {} }
            class Impl : Base() { override fun f() {}; override fun g() {} }
            """.trimIndent()
        )
        val otherValues = ModPlanOptionValues.EMPTY.with(ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF, "Impl.g()")

        assertTrue(asksAboutSuperDeclarations(questionsOf(function("f", "Impl"), optionValues = otherValues)))
    }

    /** The switch is the general answer of a request: it answers the question of every declaration. */
    fun `test the base declaration switch alone answers the question`() {
        configureBaseAndImpl()
        val keepValues = ModPlanOptionValues.EMPTY.with(ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS, false)

        assertFalse(asksAboutSuperDeclarations(questionsOf(function("f", "Impl"), optionValues = keepValues)))
        headlessSafeDelete(project, listOf(function("f", "Impl")), keepValues)
        assertTrue(file().text, file().text.contains("open fun f()"))
    }

    /** A declaration that the dialog asked about with the answer no is not asked again, and keeps its base declaration. */
    fun `test an asked declaration that is not chosen keeps its base declaration`() {
        configureBaseAndImpl()
        val askedValues = ModPlanOptionValues.EMPTY.withAnswers(ModPlanKotlinSafeDeleteOptions.SUPER_DECLARATIONS_OF, listOf("Impl.f()"), emptyList())

        assertFalse(asksAboutSuperDeclarations(questionsOf(function("f", "Impl"), optionValues = askedValues)))
        headlessSafeDelete(project, listOf(function("f", "Impl")), askedValues)
        assertTrue(file().text, file().text.contains("open fun f()"))
    }

    fun `test the hierarchy question that the request answers is not asked`() {
        myFixture.configureByText(
            "a.kt",
            """
            open class Base { open fun f(p: Int) {} }
            class Impl : Base() { override fun f(p: Int) {} }
            """.trimIndent()
        )
        val parameter = function("f", "Base").valueParameters.single()
        val answeredValues = ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.DELETE_HIERARCHY, false)

        assertTrue(asksAbout(questionsOf(parameter), ModPlanSafeDeleteOptions.DELETE_HIERARCHY))
        assertFalse(asksAbout(questionsOf(parameter, optionValues = answeredValues), ModPlanSafeDeleteOptions.DELETE_HIERARCHY))
    }

    fun `test the expected declaration question that the request answers is not asked`() {
        myFixture.configureByText("a.kt", "actual fun foo() {}")
        val answeredValues = ModPlanOptionValues.EMPTY.with(ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL, true)

        assertTrue(asksAbout(questionsOf(function("foo")), ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL))
        assertFalse(asksAbout(questionsOf(function("foo"), optionValues = answeredValues), ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL))
    }

    fun `test the search form is not asked only when the request answers both options`() {
        myFixture.configureByText("a.kt", "fun unused() {}")
        val commentsOnly = ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true)
        val bothOptions = commentsOnly.with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, false)

        assertTrue(asksAbout(questionsOf(function("unused")), ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS))
        assertTrue(asksAbout(questionsOf(function("unused"), optionValues = commentsOnly), ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE))
        assertFalse(asksAbout(questionsOf(function("unused"), optionValues = bothOptions), ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS))
    }

    fun `test the hierarchy question of one parameter leaves the parameter of another function asked`() {
        myFixture.configureByText(
            "a.kt",
            """
            open class Base { open fun f(p: Int) {}; open fun g(q: Int) {} }
            class Impl : Base() { override fun f(p: Int) {}; override fun g(q: Int) {} }
            """.trimIndent()
        )
        val f = function("f", "Base")
        val askedValues = ModPlanOptionValues.EMPTY.withAnswers(ModPlanSafeDeleteOptions.HIERARCHY_OF, listOf(runReadActionBlocking { declarationKey(f) } + " p"), emptyList())

        val otherParameter = function("g", "Base").valueParameters.single()
        assertTrue(asksAbout(questionsOf(otherParameter, optionValues = askedValues), ModPlanSafeDeleteOptions.HIERARCHY_OF))
        assertFalse(asksAbout(questionsOf(f.valueParameters.single(), optionValues = askedValues), ModPlanSafeDeleteOptions.HIERARCHY_OF))
    }

    private fun asksAbout(questions: List<ModPlanQuestion>, option: ModPlanOption<*>): Boolean = questions.any { option in optionsOf(it) }

    private fun configureBaseAndImpl() {
        myFixture.configureByText(
            "a.kt",
            """
            open class Base { open fun f() {} }
            class Impl : Base() { override fun f() {} }
            """.trimIndent()
        )
    }

    private fun asksAboutSuperDeclarations(questions: List<ModPlanQuestion>): Boolean = questions.any {
        it is ModPlanQuestion.Custom && ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS in it.options()
    }

    private fun function(name: String, className: String? = null): KtNamedFunction = runReadActionBlocking {
        PsiTreeUtil.findChildrenOfType(file(), KtNamedFunction::class.java).single { function ->
            function.name == name && (className == null || function.containingClass()?.name == className)
        }
    }

    private fun file() = myFixture.file

    private fun questionsOf(
        element: PsiElement, related: Boolean = true, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY
    ): List<ModPlanQuestion> = questionsOf(listOf(element), related, optionValues)

    private fun planOf(element: PsiElement, optionValues: ModPlanOptionValues): ModPlan =
        PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable {
            runReadActionBlocking {
                ModPlanSafeDeleteRefactoring.getInstance().plan(ModPlanRequest(project, listOf(SymbolPointer.of(element)), optionValues))
            }
        }))

    private fun optionsOf(question: ModPlanQuestion): List<ModPlanOption<*>> = when (question) {
        is ModPlanQuestion.Form -> question.options()
        is ModPlanQuestion.Custom -> question.options()
    }

    /** @return the questions that a headless run asks, in their order; the options of the request answer them */
    private fun questionsOf(
        elements: List<PsiElement>, related: Boolean = true, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY
    ): List<ModPlanQuestion> {
        val project: Project = project
        val askedQuestions = ArrayList<ModPlanQuestion>()
        PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable {
            runReadActionBlocking {
                val requestOptionValues = if (related) { optionValues } else { optionValues.with(ModPlanSafeDeleteOptions.SEARCH_RELATED, false) }
                val request = ModPlanRequest(project, elements.map(SymbolPointer::of), requestOptionValues)
                ModPlanTestUtil.runAnswering(request, ModPlanSafeDeleteRefactoring.getInstance()::chain) { question, currentValues ->
                    askedQuestions.add(question)
                    currentValues
                }
            }
        }))
        return askedQuestions
    }
}
