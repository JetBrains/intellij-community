// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.suggested

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.fileTypes.FileType
import com.intellij.refactoring.suggested.BaseSuggestedRefactoringChangeListenerTest
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.ImportPath

class KotlinSuggestedRefactoringChangeListenerTest : BaseSuggestedRefactoringChangeListenerTest() {
    override val fileType: FileType
        get() = KotlinFileType.INSTANCE

    fun test1() {
        setup("fun foo(<caret>) {}")

        perform("editingStarted: 'foo()'", "nextSignature: 'foo(p)'") { myFixture.type("p") }

        perform { myFixture.type(":") }
        perform("nextSignature: 'foo(p: S)'") { myFixture.type(" S") }
        perform("nextSignature: 'foo(p: Str)'") { myFixture.type("tr") }
        perform("nextSignature: 'foo(p: String)'") { myFixture.type("ing") }
        perform("nextSignature: 'foo(p: String, )'") { myFixture.type(", ") }
    }

    fun testCompletion() {
        setup("fun foo(<caret>) {}")

        perform("editingStarted: 'foo()'", "nextSignature: 'foo(p: DoubleArra)'") { myFixture.type("p: DoubleArra") }
        perform("nextSignature: 'foo(p: DoubleArray)'") { myFixture.completeBasic() }
    }

    fun testChangeOutsideSignature() {
        setup("fun foo(<caret>) {}")

        perform("editingStarted: 'foo()'", "nextSignature: 'foo(p: A)'") { myFixture.type("p: A") }
        perform("reset") {
            insertString(editor.document.textLength, "\nval")
        }
    }

    fun testEditOtherSignature() {
        setup("fun foo(<caret>) {}\nfun bar() = 0")

        val otherFunction = (file as KtFile).declarations[1] as KtNamedFunction
        val offset = otherFunction.valueParameterList!!.startOffset + 1
        val marker = editor.document.createRangeMarker(offset, offset)

        perform("editingStarted: 'foo()'", "nextSignature: 'foo(p: A)'") { myFixture.type("p: A") }

        perform("reset", "editingStarted: 'bar()'", "nextSignature: 'bar(p1: String)'") {
            assert(marker.isValid)
            insertString(marker.startOffset, "p1: String")
        }
    }

    fun testChangeInAnotherFile() {
        setup("fun foo(<caret>) {}")

        perform("editingStarted: 'foo()'", "nextSignature: 'foo(p: A)'") { myFixture.type("p: A") }
        perform("reset") {
            setup("")
            myFixture.type(" ")
        }
    }

    fun testAddImport() {
        setup("fun foo(<caret>) {}")

        perform("editingStarted: 'foo()'", "nextSignature: 'foo(p: Any)'") {
            myFixture.type("p: Any")
        }
        perform("nextSignature: 'foo(p: Any)'") {
            addImport("java.util.ArrayList")
        }
        perform("nextSignature: 'foo(p: Any, p2: String)'") {
            myFixture.type(", p2: String")
        }
        perform("nextSignature: 'foo(p: Any, p2: String)'") {
            addImport("java.util.Date")
        }
    }

    fun testAddImportWithBlankLineInsertion() {
        setup(
            """
                import foo.bar
                fun foo(<caret>) {}
            """.trimIndent()
        )

        perform("editingStarted: 'foo()'", "nextSignature: 'foo(p: ArrayList)'") {
            myFixture.type("p: ArrayList")
        }
        perform("nextSignature: 'foo(p: ArrayList)'") {
            addImport("java.util.ArrayList")
        }
        perform("nextSignature: 'foo(p: ArrayList<String>)'") {
            myFixture.type("<String>")
        }
        perform("nextSignature: 'foo(p: ArrayList<String>, p2: Any)'") {
            myFixture.type(", p2: Any")
        }
    }

    fun testAddImportWithBlankLinesRemoval() {
        setup(
            """
                import foo.bar
                
                
                
                fun foo(<caret>) {}
            """.trimIndent()
        )

        perform("editingStarted: 'foo()'", "nextSignature: 'foo(p: ArrayList)'") {
            myFixture.type("p: ArrayList")
        }
        perform("nextSignature: 'foo(p: ArrayList)'") {
            addImport("java.util.ArrayList")
        }
        perform("nextSignature: 'foo(p: ArrayList<String>)'") {
            myFixture.type("<String>")
        }
        perform("nextSignature: 'foo(p: ArrayList<String>, p2: Any)'") {
            myFixture.type(", p2: Any")
        }
    }

    fun testReorderParameters() {
        setup("fun foo(p1: String, p2: Any, p3<caret>: Int) {}")

        perform("editingStarted: 'foo(p1: String, p2: Any, p3: Int)'", "nextSignature: 'foo(p1: String, p3: Int, p2: Any)'") {
            myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)
        }
        perform("nextSignature: 'foo(p3: Int, p1: String, p2: Any)'") {
            myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)
        }
        perform("nextSignature: 'foo(p1: String, p3: Int, p2: Any)'") {
            myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_RIGHT)
        }
    }

    fun testAddParameterViaPsi() {
        setup("fun foo(p1: Int) {}")

        val function = (file as KtFile).declarations.single() as KtFunction
        perform(
            "editingStarted: 'foo(p1: Int)'",
            "nextSignature: 'foo(p1: Int, p2: Int)'"
        ) {
            executeCommand {
                runWriteAction {
                    function.valueParameterList!!.addParameter(KtPsiFactory(project).createParameter("p2: Int"))
                }
            }
        }
    }

    fun testCommentTyping() {
        setup("fun foo(<caret>) {}")

        perform("editingStarted: 'foo()'", "nextSignature: 'foo(p1: Any)'") {
            myFixture.type("p1: Any")
        }

        perform("inconsistentState") {
            myFixture.type("/*")
        }

        perform("inconsistentState") {
            myFixture.type(" this is comment for parameter")
        }

        perform("nextSignature: 'foo(p1: Any/* this is comment for parameter*/)'") {
            myFixture.type("*/")
        }

        perform("inconsistentState") {
            myFixture.type(", p2: Int /*")
        }

        perform("inconsistentState") {
            myFixture.type("this is comment for another parameter")
        }

        perform("nextSignature: 'foo(p1: Any/* this is comment for parameter*/, p2: Int /*this is comment for another parameter*/)'") {
            myFixture.type("*/")
        }
    }

    fun testAddReturnType() {
        setup(
            """
                interface I {
                    fun foo()<caret>
                }    
            """.trimIndent()
        )

        perform("editingStarted: 'foo()'", "nextSignature: 'foo(): String'") { myFixture.type(": String") }
    }

    fun testNewLocal() {
        setup(
            """
                fun foo() {
                    <caret>
                    print(a)
                }
            """.trimIndent()
        )

        perform {
            myFixture.type("val a")
            myFixture.type("bcd")
        }
    }

    fun testNewFunction() {
        setup(
            """
                interface I {
                    <caret>
                }    
            """.trimIndent()
        )

        perform {
            myFixture.type("fun foo_bar123(_p1: Int)")
        }
    }

    fun testNewProperty() {
        setup(
            """
                interface I {
                    <caret>
                }    
            """.trimIndent()
        )

        perform {
            myFixture.type("val prop: I")
            myFixture.type("nt")
        }
    }

    fun testNewLocalWithNewUsage() {
        setup(
            """
                fun foo() {
                    <caret>
                }
            """.trimIndent()
        )

        perform {
            myFixture.type("val a = 10")
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
            myFixture.type("print(a)")
        }

        perform("editingStarted: 'a'", "nextSignature: 'abcd'") {
            val variable = file.findDescendantOfType<KtProperty>()!!
            myFixture.editor.caretModel.moveToOffset(variable.nameIdentifier!!.endOffset)
            myFixture.type("bcd")
        }
    }

    fun testNewLocalBeforeExpression() {
        setup(
            """
                fun foo(p: Int) {
                    <caret>p * p
                }
            """.trimIndent()
        )

        perform {
            myFixture.type("val a")
        }
        perform {
            myFixture.type("bcd = ")
        }
    }

    fun testNewClassWithConstructor() {
        setup("")

        perform {
            myFixture.type("class C")
        }
        perform {
            myFixture.type("(p: Int)")
        }
    }

    fun testNewSecondaryConstructor() {
        setup(
            """
                class C {
                    <caret>
                }
            """.trimIndent()
        )

        perform {
            myFixture.type("constructor(p1: Int)")
        }
        perform {
            myFixture.type("(, p2: String)")
        }
    }

    fun testRenameComponentVar() {
        setup(
            """
                fun f() {
                    val (<caret>a, b) = f()
                }
            """.trimIndent()
        )

        perform("editingStarted: 'a'", "nextSignature: 'newa'") {
            myFixture.type("new")
        }
    }

    private fun addImport(fqName: String) {
        executeCommand {
            runWriteAction {
                (file as KtFile).importList!!.add(KtPsiFactory(project).createImportDirective(ImportPath.fromString(fqName)))
            }
        }
    }
}