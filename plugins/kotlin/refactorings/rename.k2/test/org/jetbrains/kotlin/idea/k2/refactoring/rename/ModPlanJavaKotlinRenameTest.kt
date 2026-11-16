// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.lang.Language
import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.ElementsForRename
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.SubjectUsage
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.Substitution
import com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring
import com.intellij.refactoring.rename.naming.AutomaticGetterSetterRenamerFactory
import com.intellij.refactoring.rename.naming.AutomaticInheritorRenamerFactory
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ModPlanRenameTestSupport
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.refactoring.rename.ForeignUsagesRenameProcessor
import org.jetbrains.kotlin.idea.refactoring.rename.ModPlanKotlinRenameOptions
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.concurrent.atomic.AtomicInteger

/**
 * The rename on the ModPlan engine of a Java declaration with Kotlin usages and of a Kotlin declaration with Java usages, where a plan has
 * subjects of both providers. The plain results are in the `rename` data of [FirRenameOnModPlanTestGenerated]; this class tests what only
 * ModPlan has, such as an exclusion, a scope and the conflicts of a plan. The plan is made on a background thread, as the MCP tool makes it.
 */
class ModPlanJavaKotlinRenameTest : AbstractModPlanKotlinRenameTest() {
    fun testExcludedKotlinUsageOfAJavaClassKeepsItsName() {
        val tool = myFixture.addFileToProject("Tool.java", "public class Tool {}")
        val user = myFixture.addFileToProject("User.kt", "fun f(first: Tool, second: Tool) {}")
        val plan = plan(declaration<PsiClass>(tool, "Tool"), "Work")

        execute { planWithout(placesAt(plan, user, user.text.lastIndexOf("Tool"))) }

        assertEquals("public class Work {}", tool.text)
        assertEquals("fun f(first: Work, second: Tool) {}", user.text)
    }

    fun testExcludedJavaUsageOfAKotlinClassKeepsItsName() {
        val tool = myFixture.addFileToProject("Tool.kt", "class Tool")
        val user = myFixture.addFileToProject("User.java", "class User {\n  Tool first;\n  Tool second;\n}")
        val plan = plan(declaration<KtClass>(tool, "Tool"), "Work")

        execute { planWithout(placesAt(plan, user, user.text.lastIndexOf("Tool"))) }

        assertEquals("class Work", tool.text)
        assertEquals("class User {\n  Work first;\n  Tool second;\n}", user.text)
    }

    /** The getter and the setter are subjects of their own, and both refer to the place of `bean.count++`. */
    fun testIncrementOfAPropertyWhoseAccessorsAreRenamedWithTheirFieldIsWrittenOnce() {
        val bean = myFixture.addFileToProject("Bean.java", """
            public class Bean {
              private int count;
              public int getCount() { return count; }
              public void setCount(int value) { count = value; }
            }
            """.trimIndent())
        val user = myFixture.addFileToProject("User.kt", "fun f(bean: Bean) {\n    bean.count++\n}")
        val options = relatedOn().with(renamerSwitch(AutomaticGetterSetterRenamerFactory::class.java), true)
        val plan = plan(declaration<PsiField>(bean, "count"), "hits", options)

        assertSize(1, placesAt(plan, user, user.text.indexOf("count")))
        execute { plan }

        assertEquals("""
            public class Bean {
              private int hits;
              public int getHits() { return hits; }
              public void setHits(int value) { hits = value; }
            }
            """.trimIndent(), bean.text)
        assertEquals("fun f(bean: Bean) {\n    bean.hits++\n}", user.text)
    }

    /** The Java class and its Kotlin inheritor have two providers, and each one replaces its own name in the comment. */
    fun testCommentThatNamesAJavaClassAndItsKotlinInheritorGetsBothNewNames() {
        val shape = myFixture.addFileToProject("Shape.java", "public class Shape {}")
        val shapes = myFixture.addFileToProject("Shapes.kt", "// Shape and RoundShape\nclass RoundShape : Shape()")
        val options = relatedOn().with(renamerSwitch(AutomaticInheritorRenamerFactory::class.java), true)
            .with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, true)

        execute { plan(declaration<PsiClass>(shape, "Shape"), "Figure", options) }

        assertEquals("public class Figure {}", shape.text)
        assertEquals("// Figure and RoundFigure\nclass RoundFigure : Figure()", shapes.text)
    }

    /** As the legacy rename, the override that overrides nothing after the rename loses its `override` modifier. */
    fun testKotlinOverrideOfAJavaMethodIsRenamedAloneWithoutTheBaseMethod() {
        val runner = myFixture.addFileToProject("Runner.java", "public class Runner {\n  public void run() {}\n}")
        val worker = myFixture.addFileToProject("Worker.kt", "class Worker : Runner() {\n    override fun run() {}\n}\nfun f(worker: Worker) { worker.run() }")
        val options = ModPlanOptionValues.EMPTY.with(ModPlanKotlinRenameOptions.RENAME_BASE_DECLARATION, false)

        execute { plan(declaration<KtNamedFunction>(worker, "run"), "execute", options) }

        assertEquals("public class Runner {\n  public void run() {}\n}", runner.text)
        assertEquals("class Worker : Runner() {\n    fun execute() {}\n}\nfun f(worker: Worker) { worker.execute() }", worker.text)
    }

    fun testConflictsInTheJavaAndTheKotlinFileAreBothInThePlan() {
        val base = myFixture.addFileToProject("Base.java", "public class Base {\n  public void run() {}\n  public void execute() {}\n}")
        val worker = myFixture.addFileToProject("Worker.kt", "class Worker : Base() {\n    override fun run() {}\n    override fun execute() {}\n}")

        val plan = plan(declaration<PsiMethod>(base, "run"), "execute")

        val conflictFiles = plan.conflicts.conflicts.mapNotNull { it.location?.virtualFile }
        assertTrue("conflicts: ${plan.conflicts.conflicts.map { it.messages }}", plan.conflicts.needsConfirmation())
        assertContainsElements(conflictFiles, base.virtualFile, worker.virtualFile)
    }

    /** The Kotlin rename does not support a usage that a [ForeignUsagesRenameProcessor] writes. */
    fun testRefusalOfTheKotlinOverrideRefusesThePlanOfTheJavaMethod() {
        val processorPoint = ExtensionPointName<ForeignUsagesRenameProcessor>("org.jetbrains.kotlin.foreignUsagesRenameProcessor")
        ExtensionTestUtil.maskExtensions(processorPoint, listOf(object : ForeignUsagesRenameProcessor() {
            override fun process(element: PsiElement, newName: String, language: Language, allUsages: Collection<UsageInfo>): Boolean = false
            override fun prepare(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {}
        }), testRootDisposable)
        val runner = myFixture.addFileToProject("Runner.java", "public class Runner {\n  public void run() {}\n}")
        val worker = myFixture.addFileToProject("Worker.kt", "class Worker : Runner() {\n    override fun run() {}\n}")

        val plan = ModPlanRenameTestSupport.planOnModPlan(project, declaration<PsiMethod>(runner, "run"), "execute", ModPlanOptionValues.EMPTY)

        val refused = assertInstanceOf(plan, ModPlan.RefusedPlan::class.java)
        assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refused.reason)
        assertEquals(worker.virtualFile, refused.location?.virtualFile)
    }

    /**
     * The engine prepares a subject with every provider, and the provider of a Kotlin-aware getter prepares it with the Java method provider.
     * The Java method provider must prepare the getter once.
     */
    fun testJavaMethodProviderPreparesAKotlinAwareGetterOnce() {
        val bean = myFixture.addFileToProject("Bean.java", "public class Bean {\n  public int getCount() { return 0; }\n}")
        myFixture.addFileToProject("User.kt", "fun f(bean: Bean) = bean.count")
        val getter = declaration<PsiMethod>(bean, "getCount")
        val prepares = countPreparesOfJavaMethodProvider(getter)

        plan(getter, "getHits")

        assertEquals(1, prepares.get())
    }

    /** The overrider of a Kotlin-aware getter is renamed with it, and a second preparation of the getter adds nothing. */
    fun testOverriderOfAKotlinAwareGetterIsRenamedOnceWithTheGetter() {
        val bean = myFixture.addFileToProject("Bean.java", "public class Bean {\n  public int getCount() { return 0; }\n}")
        val child = myFixture.addFileToProject("Child.java", "public class Child extends Bean {\n  @Override public int getCount() { return 1; }\n}")
        val user = myFixture.addFileToProject("User.kt", "fun f(bean: Bean) = bean.count")
        val getter = declaration<PsiMethod>(bean, "getCount")
        val prepares = countPreparesOfJavaMethodProvider(getter)

        execute { plan(getter, "getHits") }

        assertEquals(1, prepares.get())
        assertEquals("public class Bean {\n  public int getHits() { return 0; }\n}", bean.text)
        assertEquals("public class Child extends Bean {\n  @Override public int getHits() { return 1; }\n}", child.text)
        assertEquals("fun f(bean: Bean) = bean.hits", user.text)
    }

    /**
     * Masks the providers so that the one that `ModPlanKotlinAwareJavaGetterRenameProvider` delegates the [getter] to counts its `prepare`
     * calls for the [getter]. The test module does not compile against that provider, so the test finds it by its class name.
     */
    private fun countPreparesOfJavaMethodProvider(getter: PsiMethod): AtomicInteger {
        val subject = runReadActionBlocking { SymbolPointer.of(getter) }
        val providers = ModPlanRenameProvider.EP_NAME.extensionList
        val javaMethodProvider = runReadActionBlocking {
            providers.first { it.javaClass.simpleName != "ModPlanKotlinAwareJavaGetterRenameProvider" && it.isOwner(subject) }
        }
        val prepares = AtomicInteger()
        val countingProviders = providers.map { provider ->
            if (provider === javaMethodProvider) CountingRenameProvider(provider, subject, prepares) else provider
        }
        ExtensionTestUtil.maskExtensions(ModPlanRenameProvider.EP_NAME, countingProviders, testRootDisposable)
        return prepares
    }

    /** Counts the `prepare` calls of [delegate] for [counted], and passes every call to it. */
    private class CountingRenameProvider(
        private val delegate: ModPlanRenameProvider,
        private val counted: SymbolPointer,
        private val prepares: AtomicInteger,
    ) : ModPlanRenameProvider {
        override fun isOwner(subject: SymbolPointer): Boolean = delegate.isOwner(subject)

        override fun isLegacyOnly(subject: SymbolPointer): Boolean = delegate.isLegacyOnly(subject)

        override fun substituteRequest(chainBuilder: ModPlanChainBuilder, subject: SymbolPointer, newName: String): ModPlanChain<Substitution?> =
            delegate.substituteRequest(chainBuilder, subject, newName)

        override fun searchStorage(target: SymbolPointer): ModPlanQuestion.Storage? = delegate.searchStorage(target)

        override fun prepare(
            chainBuilder: ModPlanChainBuilder,
            subject: SymbolPointer,
            elementsForRename: ElementsForRename,
        ): ModPlanChain<ElementsForRename> {
            if (subject.isSameSymbol(counted)) {
                prepares.incrementAndGet()
            }
            return delegate.prepare(chainBuilder, subject, elementsForRename)
        }

        override fun renamers(
            subject: SymbolPointer,
            elementsForRename: ElementsForRename,
            factories: List<AutomaticRenamerFactory>,
            usages: List<SubjectUsage>,
        ): List<AutomaticRenamer> = delegate.renamers(subject, elementsForRename, factories, usages)

        override fun findUsages(subject: SymbolPointer, elementsForRename: ElementsForRename, request: ModPlanRequest): List<SubjectUsage> =
            delegate.findUsages(subject, elementsForRename, request)

        override fun analyze(subject: SymbolPointer, elementsForRename: ElementsForRename, keptUsages: List<SubjectUsage>): ModPlanAnalysisResult =
            delegate.analyze(subject, elementsForRename, keptUsages)
    }

    fun testLocalScopeOfAJavaAndAKotlinFileKeepsTheUsagesOutOfIt() {
        val tool = myFixture.addFileToProject("Tool.java", "public class Tool {\n  public static void run() {}\n  void again() { run(); }\n}")
        val user = myFixture.addFileToProject("User.kt", "fun f() { Tool.run() }")
        val other = myFixture.addFileToProject("Other.kt", "fun g() { Tool.run() }")
        val method = declaration<PsiMethod>(tool, "run")
        val request = runReadActionBlocking {
            val values = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.NEW_NAME, "execute").with(ModPlanRenameOptions.SUBSTITUTE_TARGET, true)
            ModPlanRequest(project, listOf(SymbolPointer.of(method)), values, ModPlanRequest.searchScopeSupplier(LocalSearchScope(arrayOf(tool, user))))
        }

        execute { ModPlanRenameRefactoring.getInstance().plan(request) }

        assertEquals("public class Tool {\n  public static void execute() {}\n  void again() { execute(); }\n}", tool.text)
        assertEquals("fun f() { Tool.execute() }", user.text)
        assertEquals("fun g() { Tool.run() }", other.text)
    }
}
