// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.openapi.application.runWriteAction
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.assertInstanceOf
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveModel
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K2MoveModelTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    fun `test file from source directory to file move`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barFile = myFixture.addFileToProject("Bar.kt", """
            class Bar { }
        """.trimIndent()) as KtFile
        val moveModel = K2MoveModel.create(arrayOf(fooFile), barFile)
        assertInstanceOf<K2MoveModel.Members>(moveModel)
        val moveMembersModel = moveModel as K2MoveModel.Members
        assertSize(1, moveMembersModel.source.elements)
        val sourceElement = moveMembersModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Foo")
        assert(moveMembersModel.target.fileName == "Bar.kt")
    }

    fun `test file from source directory to source directory move`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barDir = runWriteAction { fooFile.containingDirectory?.createSubdirectory("bar") }
        val moveModel = K2MoveModel.create(arrayOf(fooFile), barDir)
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(1, moveFilesModel.source.elements)
        val sourceElement = moveFilesModel.source.elements.firstOrNull()
        assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
        assert(moveFilesModel.target.pkgName.asString() == "bar")
    }

    fun `test file from non-source directory move`() {
        PsiTestUtil.removeSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            package bar
            
            class Foo { }
        """.trimIndent()) as KtFile
        val moveModel = K2MoveModel.create(arrayOf(fooFile), null)
        assertInstanceOf<K2MoveModel.Members>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Members
        assertSize(1, moveFilesModel.source.elements)
        val sourceElement = moveFilesModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Foo")
        assert(moveFilesModel.target.pkgName.asString() == "bar")
        assert(moveFilesModel.target.fileName == "Foo.kt")
    }

    fun `test multiple files with the same packages from non-source directory move`() {
        PsiTestUtil.removeSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            package foo
            
            class Foo { }
        """.trimIndent()) as KtFile
        val barFile = myFixture.addFileToProject("Bar.kt", """
            package foo
            
            class Bar { }
        """.trimIndent()) as KtFile
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barFile), null)
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val sourceElement = moveFilesModel.source.elements.firstOrNull()
        assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
        assert(moveFilesModel.target.pkgName.asString() == "foo")
    }

    fun `test multiple files with different packages from non-source directory move`() {
        PsiTestUtil.removeSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            package foo
            
            class Foo { }
        """.trimIndent()) as KtFile
        val barFile = myFixture.addFileToProject("Bar.kt", """
            package bar
            
            class Bar { }
        """.trimIndent()) as KtFile
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barFile), null)
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val sourceElement = moveFilesModel.source.elements.firstOrNull()
        assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
        assert(moveFilesModel.target.pkgName.asString() == "foo")
    }

    fun `test move top level declaration`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class B<caret>ar { }
        """.trimIndent())
        val barClass = myFixture.elementAtCaret as KtNamedDeclaration
        val moveModel = K2MoveModel.create(arrayOf(barClass), null)
        assertInstanceOf<K2MoveModel.Members>(moveModel)
        val moveMembersModel = moveModel as K2MoveModel.Members
        assertSize(1, moveMembersModel.source.elements)
        val sourceElement = moveMembersModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Bar")
        val targetElement = moveMembersModel.target.pkgName
        assert(targetElement.asString() == "foo")
    }

    fun `test move enum entry should fail`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            enum Foo {
                B<caret>AR, FOOBAR
            }
        """.trimIndent())
        val barEnumEntry = myFixture.elementAtCaret as KtNamedDeclaration
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(barEnumEntry), null)
        }
    }

    fun `test move nested class should fail`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class Foo {
                class Ba<caret>r { }
            }
        """.trimIndent())
        val nestedClass = myFixture.elementAtCaret as KtNamedDeclaration
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(nestedClass), null)
        }
    }

    fun `test move instance method should fail`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class Foo {
                fun fo<caret>o() { }
            }
        """.trimIndent())
        val instanceMethod = myFixture.elementAtCaret as KtNamedDeclaration
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(instanceMethod), null)
        }
    }

    fun `test move companion object method should fail`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class Foo {
                companion object {
                    fun fo<caret>o() { }
                } 
            }
        """.trimIndent())
        val companionObjectMethod = myFixture.elementAtCaret as KtNamedDeclaration
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(companionObjectMethod), null)
        }
    }
}