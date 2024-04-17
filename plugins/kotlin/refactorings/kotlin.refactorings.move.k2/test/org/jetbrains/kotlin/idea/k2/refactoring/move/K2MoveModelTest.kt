// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Foo")
        assert(moveDeclarationsModel.target.fileName == "Bar.kt")
    }

    fun `test file from source directory to class move`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barClass = (myFixture.addFileToProject("Bar.kt", """
            class Bar { }
        """.trimIndent()) as KtFile).declarations.firstOrNull()
        val moveModel = K2MoveModel.create(arrayOf(fooFile), barClass)
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Foo")
        assert(moveDeclarationsModel.target.fileName == "Bar.kt")
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

    fun `test single class and file from source directory without target move`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barClass = (myFixture.addFileToProject("Bar.kt", """
            class Foo { }
        """.trimIndent()) as KtFile).declarations.single()
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barClass), null)
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val firstElem = moveFilesModel.source.elements.first()
        assert(firstElem.name == "Foo.kt")
        val lastElem = moveFilesModel.source.elements.last()
        assert(lastElem.name == "Bar.kt")
    }

    fun `test single class and file from source directory to source directory move`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barClass = (myFixture.addFileToProject("Bar.kt", """
            class Foo { }
        """.trimIndent()) as KtFile).declarations.single()
        val barDir = runWriteAction { fooFile.containingDirectory?.createSubdirectory("bar") }
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barClass), barDir)
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val firstElem = moveFilesModel.source.elements.first()
        assert(firstElem.name == "Foo.kt")
        val lastElem = moveFilesModel.source.elements.last()
        assert(lastElem.name == "Bar.kt")
        assert(moveFilesModel.target.pkgName.asString() == "bar")
    }

    fun `test file from non-source directory move`() {
        PsiTestUtil.removeSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            package bar
            
            class Foo { }
        """.trimIndent()) as KtFile
        val moveModel = K2MoveModel.create(arrayOf(fooFile), null)
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        val moveFilesModel = moveModel as K2MoveModel.Declarations
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
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Bar")
        val targetElement = moveDeclarationsModel.target.pkgName
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

    fun `test move multiple files to non directory should fail`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barFile = myFixture.addFileToProject("Bar.kt", """
            class Bar { }
        """.trimIndent()) as KtFile
        val fooBarFile = myFixture.addFileToProject("FooBar.kt", """
            class FooBar { }
        """.trimIndent()) as KtFile
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(fooFile, barFile), fooBarFile)
        }
    }

    fun `test moving declarations from multiple files should fail`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFun = (myFixture.addFileToProject("Foo.kt", """
            fun foo { }
        """.trimIndent()) as KtFile).declarations.single()
        val barFun = (myFixture.addFileToProject("Bar.kt", """
            fun bar { }
        """.trimIndent()) as KtFile).declarations.single()
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(fooFun, barFun), null)
        }
    }

    fun `test moving file with declaration from different file should fail`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            fun foo { }
        """.trimIndent()) as KtFile
        val barFun = (myFixture.addFileToProject("Bar.kt", """
            fun bar { }
        """.trimIndent()) as KtFile).declarations.single()
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(fooFile, barFun), null)
        }
    }

    fun `test move declaration and containing file`() {
        PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val moveModel = K2MoveModel.create(arrayOf(fooFile, fooFile.declarations.first()), null)
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Foo")
    }
}