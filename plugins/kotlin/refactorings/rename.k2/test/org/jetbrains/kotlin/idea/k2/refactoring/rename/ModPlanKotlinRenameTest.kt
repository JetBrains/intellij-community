// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanConflictKind
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.impl.ModPlanFlow
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.ModPlanRenameTestSupport
import org.jetbrains.kotlin.idea.refactoring.rename.AutomaticOverloadsRenamerFactory
import org.jetbrains.kotlin.idea.refactoring.rename.AutomaticParameterRenamerFactory
import org.jetbrains.kotlin.idea.refactoring.rename.ModPlanKotlinRenameOptions
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

/**
 * The rename of Kotlin declarations on the ModPlan engine: what the legacy rename has not, such as an exclusion, a scope, the option values
 * and the choice of the related renames. The analysis runs on a background thread, as the MCP tool runs it.
 */
class ModPlanKotlinRenameTest : AbstractModPlanKotlinRenameTest() {
    fun testExcludedUsageKeepsItsName() {
        val tool = myFixture.addFileToProject("Tool.kt", "fun tool() {}")
        val user = myFixture.addFileToProject("User.kt", "fun f() {\n    tool()\n    tool()\n}")
        val plan = plan(declaration<KtNamedFunction>(tool, "tool"), "work")

        execute { planWithout(placesAt(plan, user, user.text.lastIndexOf("tool"))) }

        assertEquals("fun work() {}", tool.text)
        assertEquals("fun f() {\n    work()\n    tool()\n}", user.text)
    }

    fun testTwoExcludedUsagesKeepBothNames() {
        val tool = myFixture.addFileToProject("Tool.kt", "fun tool() {}")
        val user = myFixture.addFileToProject("User.kt", "fun f() {\n    tool()\n    tool()\n    tool()\n}")
        val plan = plan(declaration<KtNamedFunction>(tool, "tool"), "work")

        execute { planWithout(placesAt(plan, user, user.text.indexOf("tool")) + placesAt(plan, user, user.text.lastIndexOf("tool"))) }

        assertEquals("fun work() {}", tool.text)
        assertEquals("fun f() {\n    tool()\n    work()\n    tool()\n}", user.text)
    }

    fun testLocalScopeKeepsTheUsagesOutOfIt() {
        val tool = myFixture.addFileToProject("Tool.kt", "fun tool() {}\nfun inner() { tool() }")
        val user = myFixture.addFileToProject("User.kt", "fun f() { tool() }")
        val function = declaration<KtNamedFunction>(tool, "tool")
        val request = runReadActionBlocking {
            val values = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.NEW_NAME, "work").with(ModPlanRenameOptions.SUBSTITUTE_TARGET, true)
            ModPlanRequest(project, listOf(SymbolPointer.of(function)), values, ModPlanRequest.searchScopeSupplier(LocalSearchScope(tool)))
        }

        execute { ModPlanRenameRefactoring.getInstance().plan(request) }

        assertEquals("fun work() {}\nfun inner() { work() }", tool.text)
        assertEquals("fun f() { tool() }", user.text)
    }

    fun testCommentIsKeptByDefault() {
        val tool = myFixture.addFileToProject("Tool.kt", "// tool does it\nfun tool() {}")

        ModPlanRenameTestSupport.renameOnModPlan(project, declaration<KtNamedFunction>(tool, "tool"), "work", ModPlanOptionValues.EMPTY)

        assertEquals("// tool does it\nfun work() {}", tool.text)
    }

    fun testCommentIsRenamedWithTheOptionAndKeptWhenExcluded() {
        val tool = myFixture.addFileToProject("Tool.kt", "// tool does it\n// tool again\nfun tool() {}")
        val options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, true)
        val plan = plan(declaration<KtNamedFunction>(tool, "tool"), "work", options)

        execute { planWithout(placesAt(plan, tool, tool.text.indexOf("tool again"))) }

        assertEquals("// work does it\n// tool again\nfun work() {}", tool.text)
    }

    fun testOverloadKeepsItsNameByDefault() {
        val tool = myFixture.addFileToProject("Tool.kt", "fun tool() {}\nfun tool(count: Int) {}")

        ModPlanRenameTestSupport.renameOnModPlan(project, overloads(tool).first(), "work", ModPlanOptionValues.EMPTY)

        assertEquals("fun work() {}\nfun tool(count: Int) {}", tool.text)
    }

    fun testOverloadIsRenamedByItsOption() {
        val tool = myFixture.addFileToProject("Tool.kt", "fun tool() {}\nfun tool(count: Int) {}")
        val options = renamersOn(AutomaticOverloadsRenamerFactory::class.java)

        ModPlanRenameTestSupport.renameOnModPlan(project, overloads(tool).first(), "work", options)

        assertEquals("fun work() {}\nfun work(count: Int) {}", tool.text)
    }

    fun testOnlyTheChosenParameterOfTheHierarchyIsRenamed() {
        val shapes = myFixture.addFileToProject("Shapes.kt", """
            open class Base { open fun draw(size: Int) {} }
            class Circle : Base() { override fun draw(size: Int) {} }
            class Square : Base() { override fun draw(size: Int) {} }
            """.trimIndent())
        val circleParameter = parameterOf(shapes, "Circle")
        val renamerId = ModPlanRenameTestSupport.renamerIdOf(AutomaticParameterRenamerFactory::class.java)
        val chosen = runReadActionBlocking { ModPlanRenameOptions.relatedRename(renamerId, circleParameter, "width") }!!
        val options = renamersOn(AutomaticParameterRenamerFactory::class.java).with(ModPlanRenameOptions.RELATED_RENAMES, chosen)

        ModPlanRenameTestSupport.renameOnModPlan(project, parameterOf(shapes, "Base"), "width", options)

        assertEquals("""
            open class Base { open fun draw(width: Int) {} }
            class Circle : Base() { override fun draw(width: Int) {} }
            class Square : Base() { override fun draw(size: Int) {} }
            """.trimIndent(), shapes.text)
    }

    /** As the legacy rename, the override that overrides nothing after the rename loses its `override` modifier. */
    fun testOverrideIsRenamedAloneWithoutItsBaseDeclaration() {
        val runners = myFixture.addFileToProject("Runners.kt", """
            open class Runner { open fun run() {} }
            class Worker : Runner() { override fun run() {} }
            """.trimIndent())
        val override = PsiTreeUtil.findChildrenOfType(runners, KtNamedFunction::class.java).last()
        val options = ModPlanOptionValues.EMPTY.with(ModPlanKotlinRenameOptions.RENAME_BASE_DECLARATION, false)

        ModPlanRenameTestSupport.renameOnModPlan(project, override, "execute", options)

        assertEquals("""
            open class Runner { open fun run() {} }
            class Worker : Runner() { fun execute() {} }
            """.trimIndent(), runners.text)
    }

    fun testRedeclarationOfTheNewNameIsABrokenConflict() {
        val worker = myFixture.addFileToProject("Worker.kt", "class Worker {\n    fun run() {}\n    fun execute() {}\n}")

        val plan = plan(declaration<KtNamedFunction>(worker, "run"), "execute")

        assertTrue(plan.conflicts.conflicts.any { it.kind == ModPlanConflictKind.BROKEN })
    }

    /** Each Java usage of a property gets the name of its own accessor, and an excluded one keeps the old name. */
    fun testExcludedSetterUsageKeepsTheNameOfTheOldSetter() {
        val counter = myFixture.addFileToProject("Counter.kt", "class Counter {\n    var hits: Int = 0\n}")
        val caller = myFixture.addFileToProject("Caller.java", """
            public class Caller {
              void f(Counter counter) {
                counter.setHits(counter.getHits());
                counter.setHits(0);
              }
            }
            """.trimIndent())
        val plan = plan(declaration<KtProperty>(counter, "hits"), "count")

        execute { planWithout(placesAt(plan, caller, caller.text.lastIndexOf("setHits"))) }

        assertEquals("class Counter {\n    var count: Int = 0\n}", counter.text)
        assertEquals("""
            public class Caller {
              void f(Counter counter) {
                counter.setCount(counter.getCount());
                counter.setHits(0);
              }
            }
            """.trimIndent(), caller.text)
    }

    /** A name that backticks do not make an identifier: `1tool` is valid, as the rename quotes it. */
    fun testInvalidNameIsRefused() {
        val tool = myFixture.addFileToProject("Tool.kt", "fun tool() {}")

        val plan = ModPlanRenameTestSupport.planOnModPlan(project, declaration<KtNamedFunction>(tool, "tool"), "to`ol", ModPlanOptionValues.EMPTY)

        val refused = assertInstanceOf(plan, ModPlan.RefusedPlan::class.java)
        assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refused.reason)
    }

    fun testRenameOfTheDialogRunsOnModPlan() {
        Registry.get("refactoring.rename.modPlan").setValue(true, testRootDisposable)
        val tool = myFixture.addFileToProject("Tool.kt", "fun tool() {}")
        val user = myFixture.addFileToProject("User.kt", "fun f() { tool() }")

        val result = RenameProcessor(project, declaration<KtNamedFunction>(tool, "tool"), "work", false, false).runOnModPlan(false)

        assertEquals(ModPlanFlow.Result.DONE, result)
        assertEquals("fun work() {}", tool.text)
        assertEquals("fun f() { work() }", user.text)
    }

    private fun overloads(file: PsiFile): List<KtNamedFunction> = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java).toList()

    private fun parameterOf(file: PsiFile, className: String): KtParameter =
        declaration<KtClass>(file, className).declarations.filterIsInstance<KtNamedFunction>().single().valueParameters.single()
}
