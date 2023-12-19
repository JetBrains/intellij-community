// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.openapi.application.runWriteAction
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.assertInstanceOf
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveModel
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

class K2MoveModelTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    fun `test file from source directory to file move`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent())
        val barFile = myFixture.addFileToProject("Bar.kt", """
            class Bar { }
        """.trimIndent())
        val moveModel = K2MoveModel.create(arrayOf(fooFile), barFile)
        assertInstanceOf<K2MoveModel.Members>(moveModel)
        val moveMembersModel = moveModel as K2MoveModel.Members
        assertSize(1, moveMembersModel.source.elements)
        val sourceElement = moveMembersModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Foo")
        val targetElement = moveMembersModel.target.fileName
        assert(targetElement == "Bar.kt")
    }

    fun `test file from source directory to source directory move`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent())
        val barDir = runWriteAction { fooFile.containingDirectory?.createSubdirectory("bar") }
        val moveModel = K2MoveModel.create(arrayOf(fooFile), barDir)
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(1, moveFilesModel.source.elements)
        val sourceElement = moveFilesModel.source.elements.firstOrNull()
        assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
        val targetElement = moveFilesModel.target.pkgName
        assert(targetElement.asString() == "bar")
    }

    fun `test file from non-source directory move`() {
        PsiTestUtil.removeSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            package bar
            
            class Foo { }
        """.trimIndent())
        val moveModel = K2MoveModel.create(arrayOf(fooFile), null)
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(1, moveFilesModel.source.elements)
        val sourceElement = moveFilesModel.source.elements.firstOrNull()
        assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
        val targetElement = moveFilesModel.target.pkgName
        assert(targetElement.asString() == "bar")
    }

    fun `test multiple files with the same packages from non-source directory move`() {
        PsiTestUtil.removeSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            package foo
            
            class Foo { }
        """.trimIndent())
        val barFile = myFixture.addFileToProject("Bar.kt", """
            package foo
            
            class Bar { }
        """.trimIndent())
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barFile), null)
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val sourceElement = moveFilesModel.source.elements.firstOrNull()
        assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
        val targetElement = moveFilesModel.target.pkgName
        assert(targetElement.asString() == "foo")
    }

    fun `test multiple files with different packages from non-source directory move`() {
        PsiTestUtil.removeSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            package foo
            
            class Foo { }
        """.trimIndent())
        val barFile = myFixture.addFileToProject("Bar.kt", """
            package bar
            
            class Bar { }
        """.trimIndent())
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barFile), null)
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val sourceElement = moveFilesModel.source.elements.firstOrNull()
        assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
        val targetElement = moveFilesModel.target.pkgName
        assert(targetElement.asString() == "")
    }

    fun `test move enum entry should fail`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        myFixture.configureByText("Foo.kt", """
            package foo
            
            enum Foo {
                B<caret>AR, FOOBAR
            }
        """.trimIndent())
        val barEnumEntry = myFixture.elementAtCaret
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(barEnumEntry), null)
        }
    }
}