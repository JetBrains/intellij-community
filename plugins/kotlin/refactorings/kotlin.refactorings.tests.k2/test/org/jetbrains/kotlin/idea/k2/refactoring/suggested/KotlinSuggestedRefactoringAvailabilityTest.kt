// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.suggested

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.refactoring.suggested.BaseSuggestedRefactoringAvailabilityTest
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

class KotlinSuggestedRefactoringAvailabilityTest : BaseSuggestedRefactoringAvailabilityTest(), ExpectedPluginModeProvider {
    override val fileType: LanguageFileType
        get() = KotlinFileType.INSTANCE

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin {
            super.setUp()
        }
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testNotAvailableWithSyntaxError() {
        doTest(
            """
                fun foo(p1: Int<caret>) {
                    foo(1)
                }
                
                fun bar() {
                    foo(2)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Disabled
        ) {
            type(", p2: Any")
            type(", p")
        }
    }

    fun testInsertTrailingComma() {
        doTest(
            """
                fun foo(p1: Int<caret>) {
                    foo(1)
                }
                
                fun bar() {
                    foo(2)
                }
            """.trimIndent(),
            expectedAvailability = Availability.NotAvailable
        ) {
            type(",")
        }
    }

    fun testChangeNonVirtualPropertyType() {
        doTest(
            "val v: <caret>String = \"\"",
            expectedAvailability = Availability.Disabled
        ) {
            replaceTextAtCaret("String", "Any")
        }
    }

    fun testChangeParameterTypeNonVirtual() {
        doTest(
            "fun foo(p: <caret>String) {}",
            expectedAvailability = Availability.Disabled
        ) {
            replaceTextAtCaret("String", "Any")
        }
    }

    fun testChangeReturnTypeNonVirtual() {
        doTest(
            "fun foo(): <caret>String = \"\"",
            expectedAvailability = Availability.Disabled
        ) {
            replaceTextAtCaret("String", "Any")
        }
    }

    fun testChangeLocalVariableType() {
        doTest(
            """
                fun foo() {
                    val local: <caret>Int
                    local = 10
                } 
            """.trimIndent(),
            expectedAvailability = Availability.NotAvailable
        ) {
            replaceTextAtCaret("Int", "Long")
        }
    }

    fun testAddLocalVariableType() {
        doTest(
            """
                fun foo() {
                    var local<caret> = 10
                } 
            """.trimIndent(),
            expectedAvailability = Availability.NotAvailable
        ) {
            type(": Long")
        }
    }

    fun testTypeLocalVariableBeforeExpression() {
        doTest(
            """
            """.trimIndent(),
            expectedAvailability = Availability.NotAvailable
        ) {
            type("val ")
            type("cod")
            type("e = ")
        }
    }

    fun testChangeParameterTypeAndName() {
        doTest(
            """
                interface I {
                    fun foo(p: <caret>Int)
                } 
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages"))
        ) {
            replaceTextAtCaret("Int", "String")
            editor.caretModel.moveToOffset(editor.caretModel.offset - "p: ".length)
            replaceTextAtCaret("p", "pNew")
        }
    }

    fun testRenameTwoParameters() {
        doTest(
            """
                interface I {
                    fun foo(<caret>p1: Int, p2: Int)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages"))
        ) {
            replaceTextAtCaret("p1", "p1New")
            editor.caretModel.moveToOffset(editor.caretModel.offset + "p1New: Int, ".length)
            replaceTextAtCaret("p2", "p2New")
        }
    }

    fun testChangeParameterType() {
        doTest(
            """
                class C {
                    open fun foo(p1: <caret>Int, p2: Int) {
                    }
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides"))
        ) {
            replaceTextAtCaret("Int", "Any")
        }
    }

    fun testChangeParameterTypeForAbstract() {
        doTest(
            """
                abstract class C {
                    abstract fun foo(p1: <caret>Int, p2: Int)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations"))
        ) {
            replaceTextAtCaret("Int", "Any")
        }
    }

    fun testChangeParameterTypeInInterface() {
        doTest(
            """
                interface I {
                    fun foo(p1: <caret>Int, p2: Int)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations"))
        ) {
            replaceTextAtCaret("Int", "Any")
        }
    }

    fun testChangeParameterTypeInInterfaceWithBody() {
        doTest(
            """
                interface I {
                    fun foo(p1: <caret>Int, p2: Int) {}
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides"))
        ) {
            replaceTextAtCaret("Int", "Any")
        }
    }
    
    fun testChangeTypeOfPropertyWithImplementationInInterface() {
        doTest(
            """
                interface I {
                    val p: <caret>Int
                        get() = 0
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("p", "overrides"))
        ) {
            replaceTextAtCaret("Int", "Any")
        }
    }

    fun testSpecifyExplicitType() {
        doTest(
            """
                open class C {
                    open fun foo()<caret> = 1
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
            expectedAvailabilityAfterResolve = Availability.NotAvailable
        ) {
            type(": Int")
        }
    }

    fun testSpecifyExplicitTypeWithSignatureChange() {
        doTest(
            """
                open class C {
                    open fun foo()<caret> = 1
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
        ) {
            type(": Long")
        }
    }

    fun testRemoveExplicitType() {
        doTest(
            """
                open class C {
                    open fun foo(): Int<caret> = 1
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
            expectedAvailabilityAfterResolve = Availability.NotAvailable
        ) {
            deleteTextBeforeCaret(": Int")
        }
    }

    fun testRemoveExplicitTypeWithSignatureChange() {
        doTest(
            """
                open class C {
                    open fun foo(): Long<caret> = 1
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "overrides")),
        ) {
            deleteTextBeforeCaret(": Long")
        }
    }

    fun testImportNestedClass() {
        doTest(
            """
                package ppp
                
                class C {
                    class Nested
                }
                
                interface I {
                    fun foo(): <caret>C.Nested
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations")),
            expectedAvailabilityAfterResolve = Availability.NotAvailable
        ) {
            addImport("ppp.C.Nested")
            replaceTextAtCaret("C.Nested", "Nested")
        }
    }

    fun testImportNestedClassForReceiverType() {
        doTest(
            """
                package ppp
                
                class C {
                    class Nested
                }
                
                interface I {
                    fun <caret>C.Nested.foo()
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations")),
            expectedAvailabilityAfterResolve = Availability.NotAvailable
        ) {
            addImport("ppp.C.Nested")
            replaceTextAtCaret("C.Nested", "Nested")
        }
    }

    fun testImportNestedClassForParameterType() {
        doTest(
            """
                package ppp
                
                class C {
                    class Nested
                }
                
                interface I {
                    fun foo(p: <caret>C.Nested)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations")),
            expectedAvailabilityAfterResolve = Availability.NotAvailable
        ) {
            addImport("ppp.C.Nested")
            replaceTextAtCaret("C.Nested", "Nested")
        }
    }

    fun testImportAnotherType() {
        doTest(
            """
                import java.util.ArrayList
                
                interface I {
                    fun foo(): <caret>ArrayList<Int>
                }
            """.trimIndent(),
            expectedAvailability = Availability.NotAvailable,
            expectedAvailabilityAfterResolve = Availability.Available((changeSignatureAvailableTooltip("foo", "implementations")))
        ) {
            replaceTextAtCaret("ArrayList<Int>", "kotlin.collections.ArrayList<Int>")
            removeImport("java.util.ArrayList")
            addImport("kotlin.collections.ArrayList")
            replaceTextAtCaret("kotlin.collections.ArrayList<Int>", "ArrayList<Int>")
        }
    }

    fun testDuplicateProperty() {
        doTest(
            """
                const val <caret>CONST1 = 1
            """.trimIndent(),
            expectedAvailability = Availability.NotAvailable
        ) {
            performAction(IdeActions.ACTION_EDITOR_DUPLICATE)
            replaceTextAtCaret("CONST1", "CONST2")
        }
    }

    fun testDuplicateMethod() {
        doTest(
            """
                class Test {
                    fun <caret>foo(p: Int) {}
                }
            """.trimIndent(),
            expectedAvailability = Availability.NotAvailable
        ) {
            performAction(IdeActions.ACTION_EDITOR_DUPLICATE)
            replaceTextAtCaret("foo", "bar")
        }
    }

    fun testNotDuplicateMethod() {
        doTest(
            """
                class Test {
                    fun <caret>foo(p: Int) {}
                    fun foo(p: String) {}
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(renameAvailableTooltip("foo", "bar"))
        ) {
            replaceTextAtCaret("foo", "bar")
        }
    }


    fun testUnusedLocal() {
        doTest(
            """
                fun foo() {
                    val local<caret> = 0
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(renameAvailableTooltip("local", "local123")),
            expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
        ) {
            type("123")
        }
    }

    fun testPrivateMethod() {
        doTest(
            """
                private fun foo(<caret>) {
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages")),
            expectedAvailabilityAfterBackgroundAmend = Availability.Disabled
        ) {
            type("p: Int")
        }
    }

    fun testAddOptionalParameter() {
        doTest(
            """
                fun foo(p1: Int<caret>) {
                }
                
                fun bar() {
                    foo(1)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Disabled,
        ) {
            type(", p2: Int = 2")
        }
    }

    fun testAddOptionalParameterWithOverrides() {
        doTest(
            """
                interface I {
                    fun foo(p1: Int<caret>)
                }    
                
                class C : I {
                    override fun foo(p1: Int) {
                    }
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "implementations")),
        ) {
            type(", p2: Int = 2")
        }
    }

    fun testAddOptionalParameterNotLast() {
        doTest(
            """
                fun foo(<caret>p1: Int) {
                }
                
                fun bar() {
                    foo(1)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages")),
        ) {
            type("p0: Int = 0, ")
        }
    }

    fun testAddOptionalParameterAndRenameParameter() {
        doTest(
            """
                fun foo(<caret>p1: Int) {
                }
                
                fun bar() {
                    foo(1)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages")),
        ) {
            replaceTextAtCaret("p1", "p1New")
            editor.caretModel.moveToOffset(editor.caretModel.offset + "p1New: Int".length)
            type(", p2: Int = 2")
        }
    }

    fun testAddTwoParameters() {
        doTest(
            """
                fun foo(p1: Int<caret>) {
                }
                
                fun bar() {
                    foo(1)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages")),
        ) {
            type(", p2: Int, p3: Int = 3")
        }
    }

    fun testAddExtensionReceiver() {
        doTest(
            """
                fun <caret>foo(p1: Int) {
                }
                
                fun bar() {
                    foo(1)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages")),
        ) {
            type("String.")
        }
    }

    fun testRemoveExtensionReceiver() {
        doTest(
            """
                fun String.<caret>foo(p1: Int) {
                }
                
                fun bar() {
                    "".foo(1)
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "usages")),
        ) {
            deleteTextBeforeCaret("String.")
        }
    }

    fun testExpectedFunction() {
        ignoreErrors = true
        doTest(
            """
                expect fun foo()<caret>
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "actual declarations")),
        ) {
            type(": Int")
        }
    }

    fun testMemberInsideExpectedClass() {
        ignoreErrors = true
        doTest(
            """
                expect class C {
                    fun foo()<caret>
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "actual declarations")),
        ) {
            type(": Int")
        }
    }

    fun testMemberDeepInsideExpectedClass() {
        ignoreErrors = true
        doTest(
            """
                expect class C {
                    class Nested {
                        fun foo()<caret>
                    }   
                }
            """.trimIndent(),
            expectedAvailability = Availability.Available(changeSignatureAvailableTooltip("foo", "actual declarations")),
        ) {
            type(": Int")
        }
    }

    private fun addImport(fqName: String) = editAction {
        (file as KtFile).importList!!.add(KtPsiFactory(project).createImportDirective(ImportPath.fromString(fqName)))
    }

    private fun removeImport(fqName: String) = editAction {
        (file as KtFile).importList!!.imports.first { it.importedFqName?.asString() == fqName }.delete()
    }
}