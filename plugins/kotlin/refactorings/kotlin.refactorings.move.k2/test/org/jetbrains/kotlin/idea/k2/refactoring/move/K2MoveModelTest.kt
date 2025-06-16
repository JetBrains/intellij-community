// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.assertInstanceOf
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveModel
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveTargetModel
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.*

class K2MoveModelTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun `test empty file from source directory without target move`() {
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            package foo
        """.trimIndent()) as KtFile
        val barFile = myFixture.addFileToProject("Bar.kt", """
            import foo.Bar
        """.trimIndent()) as KtFile
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barFile), null)!!
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        assertTrue(moveModel.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Files
        assertSize(2, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
    }

    fun `test declaration move from source directory to directory outside source root as files`() {
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            package foo
            class Foo
            class Bar
        """.trimIndent()) as KtFile
        val nonSourceDir = runWriteAction { fooFile.containingDirectory?.parent }
        val moveModel = K2MoveModel.create(arrayOf(fooFile.declarations.first()), targetContainer = nonSourceDir)!!
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        assertTrue(moveModel.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Files
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
    }

    fun `test file from source directory to file move`() {
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barFile = myFixture.addFileToProject("Bar.kt", """
            class Bar { }
        """.trimIndent()) as KtFile
        val moveModel = K2MoveModel.create(arrayOf(fooFile), barFile)!!
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertTrue(moveModel.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Foo")
        assertInstanceOf<K2MoveTargetModel.File>(moveDeclarationsModel.target)
        assert((moveDeclarationsModel.target as K2MoveTargetModel.File).fileName == "Bar.kt")
    }

    fun `test file from source directory to class move`() {
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barClass = (myFixture.addFileToProject("Bar.kt", """
            class Bar { }
        """.trimIndent()) as KtFile).declarations.firstOrNull()
        val moveModel = K2MoveModel.create(arrayOf(fooFile), barClass)!!
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertTrue(moveModel.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Foo")
        assertInstanceOf<K2MoveTargetModel.File>(moveDeclarationsModel.target)
        assert((moveDeclarationsModel.target as K2MoveTargetModel.File).fileName == "Bar.kt")
    }

    fun `test file from source directory to source directory move`() {
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barDir = runWriteAction { fooFile.containingDirectory?.createSubdirectory("bar") }
        val moveModel = K2MoveModel.create(arrayOf(fooFile), barDir)!!
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        assertTrue(moveModel.isValidRefactoring())
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(1, moveFilesModel.source.elements)
        val sourceElement = moveFilesModel.source.elements.firstOrNull()
        assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
        assert(moveFilesModel.target.pkgName.asString() == "bar")
    }

    fun `test single class and file from source directory without target move`() {
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barClass = (myFixture.addFileToProject("Bar.kt", """
            class Bar { }
        """.trimIndent()) as KtFile).declarations.single()
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barClass), null)!!
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        assertFalse(moveModel.isValidRefactoring())
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val firstElem = moveFilesModel.source.elements.first()
        assertEquals("Foo.kt", firstElem.name)
        val lastElem = moveFilesModel.source.elements.last()
        assertEquals("Bar.kt", lastElem.name)
    }

    fun `test single object and file from source directory without target move`() {
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barClass = (myFixture.addFileToProject("Bar.kt", """
            object Bar { }
        """.trimIndent()) as KtFile).declarations.single()
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barClass), null)!!
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        assertFalse(moveModel.isValidRefactoring())
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val firstElem = moveFilesModel.source.elements.first()
        assertEquals("Foo.kt", firstElem.name)
        val lastElem = moveFilesModel.source.elements.last()
        assertEquals("Bar.kt", lastElem.name)
    }

    fun `test java class and kotlin class from source directory without target move`() {
        val fooFile = myFixture.addFileToProject("Foo.java", """
            public class Foo { }
        """.trimIndent()) as PsiFile
        val barClass = (myFixture.addFileToProject("Bar.kt", """
            object Bar { }
        """.trimIndent()) as KtFile).declarations.single()
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barClass), null)!!
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        assertFalse(moveModel.isValidRefactoring())
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val firstElem = moveFilesModel.source.elements.first()
        assertEquals("Foo.java", firstElem.name)
        val lastElem = moveFilesModel.source.elements.last()
        assertEquals("Bar.kt", lastElem.name)
    }

    fun `test directory and kotlin class from source directory without target move`() {
        myFixture.addFileToProject("a/JavaFoo.java", """
            package a;
            
            public class JavaFoo { }
        """.trimIndent()) as PsiFile
        val directory = (myFixture.addFileToProject("a/KotlinFoo.kt", """
            package a
            
            class KotlinFoo { }
        """.trimIndent()) as KtFile).containingDirectory as PsiDirectory
        val barClass = (myFixture.addFileToProject("Bar.kt", """
            object Bar { }
        """.trimIndent()) as KtFile).declarations.single()
        val moveModel = K2MoveModel.create(arrayOf(directory, barClass), null)!!
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        assertTrue(moveModel.isValidRefactoring())
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val firstElem = moveFilesModel.source.elements.first()
        assertEquals("a", firstElem.name)
        val lastElem = moveFilesModel.source.elements.last()
        assertEquals("Bar.kt", lastElem.name)
    }

    fun `test multiple classes from source directory without target move`() {
        val fooClass = (myFixture.addFileToProject("Foo.kt", """
            package a
            
            class Foo { }
        """.trimIndent()) as KtFile).declarations.single()
        val barClass = (myFixture.addFileToProject("Bar.kt", """
            package a
            
            class Bar { }
        """.trimIndent()) as KtFile).declarations.single()
        val moveModel = K2MoveModel.create(arrayOf(fooClass, barClass), null)!!
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        assertFalse(moveModel.isValidRefactoring())
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val firstElem = moveFilesModel.source.elements.first()
        assertEquals("Foo.kt", firstElem.name)
        val lastElem = moveFilesModel.source.elements.last()
        assertEquals("Bar.kt", lastElem.name)
        assertEquals("a", moveFilesModel.target.pkgName.asString())
    }

    fun `test single class and file from source directory to source directory move`() {
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val barClass = (myFixture.addFileToProject("Bar.kt", """
            class Bar { }
        """.trimIndent()) as KtFile).declarations.single()
        val barDir = runWriteAction { fooFile.containingDirectory?.createSubdirectory("bar") }
        val moveModel = K2MoveModel.create(arrayOf(fooFile, barClass), barDir)!!
        assertInstanceOf<K2MoveModel.Files>(moveModel)
        assertTrue(moveModel.isValidRefactoring())
        val moveFilesModel = moveModel as K2MoveModel.Files
        assertSize(2, moveFilesModel.source.elements)
        val firstElem = moveFilesModel.source.elements.first()
        assertEquals("Foo.kt", firstElem.name)
        val lastElem = moveFilesModel.source.elements.last()
        assertEquals("Bar.kt", lastElem.name)
        assertEquals("bar", moveFilesModel.target.pkgName.asString())
    }

    fun `test file from non-source directory move`() {
        try {
            PsiTestUtil.removeSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
            val fooFile = myFixture.addFileToProject("Foo.kt", """
            package bar
            
            class Foo { }
        """.trimIndent()) as KtFile
            val moveModel = K2MoveModel.create(arrayOf(fooFile), null)!!
            assertInstanceOf<K2MoveModel.Declarations>(moveModel)
            assertFalse(moveModel.isValidRefactoring())
            val moveFilesModel = moveModel as K2MoveModel.Declarations
            assertSize(1, moveFilesModel.source.elements)
            val sourceElement = moveFilesModel.source.elements.firstOrNull()
            assert(sourceElement is KtClass && sourceElement.name == "Foo")
            assertEquals("bar", moveFilesModel.target.pkgName.asString())
            assertInstanceOf<K2MoveTargetModel.Declarations>(moveFilesModel.target)
            assert((moveFilesModel.target as K2MoveTargetModel.Declarations).fileName == "Foo.kt")
        } finally {
            PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        }
    }

    fun `test multiple files with the same packages from non-source directory move`() {
        try {
            PsiTestUtil.removeSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
            val fooFile = myFixture.addFileToProject("Foo.kt", """
            package foo
            
            class Foo { }
        """.trimIndent()) as KtFile
            val barFile = myFixture.addFileToProject("Bar.kt", """
            package foo
            
            class Bar { }
        """.trimIndent()) as KtFile
            val moveModel = K2MoveModel.create(arrayOf(fooFile, barFile), null)!!
            assertInstanceOf<K2MoveModel.Files>(moveModel)
            assertFalse(moveModel.isValidRefactoring())
            val moveFilesModel = moveModel as K2MoveModel.Files
            assertSize(2, moveFilesModel.source.elements)
            val sourceElement = moveFilesModel.source.elements.firstOrNull()
            assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
            assertEquals("foo", moveFilesModel.target.pkgName.asString())
        } finally {
            PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        }
    }

    fun `test multiple files with different packages from non-source directory move`() {
        try {
            PsiTestUtil.removeSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
            val fooFile = myFixture.addFileToProject("Foo.kt", """
            package foo
            
            class Foo { }
        """.trimIndent()) as KtFile
            val barFile = myFixture.addFileToProject("Bar.kt", """
            package bar
            
            class Bar { }
        """.trimIndent()) as KtFile
            val moveModel = K2MoveModel.create(arrayOf(fooFile, barFile), null)!!
            assertInstanceOf<K2MoveModel.Files>(moveModel)
            assertTrue(moveModel.isValidRefactoring())
            val moveFilesModel = moveModel as K2MoveModel.Files
            assertSize(2, moveFilesModel.source.elements)
            val sourceElement = moveFilesModel.source.elements.firstOrNull()
            assert(sourceElement is KtFile && sourceElement.name == "Foo.kt")
            assertEquals("foo", moveFilesModel.target.pkgName.asString())
        } finally {
            PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().getFile("")!!)
        }
    }

    fun `test move top level declaration`() {
        val barClass = (myFixture.addFileToProject("NotBar.kt", """
            package foo
            
            class Bar { }
        """.trimIndent()) as KtFile).declarations.first()
        val moveModel = K2MoveModel.create(arrayOf(barClass), null)!!
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertTrue(moveModel.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Bar")
        val targetElement = moveDeclarationsModel.target.pkgName
        assertEquals("foo", targetElement.asString())
    }

    fun `test move top level declaration into directory`() {
        val barClass = (myFixture.addFileToProject("a/FooBar.kt", """
            package a
            
            class Bar { }
            
            class Foo { }
        """.trimIndent()) as KtFile).declarations.first()
        val bDirectory = (myFixture.addFileToProject("b/Other.kt", """
            package b
            
            class Other { }
        """.trimIndent()) as KtFile).parent
        val moveModel = K2MoveModel.create(arrayOf(barClass), bDirectory)!!
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertTrue(moveModel.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Bar")
        val targetElement = moveDeclarationsModel.target.pkgName
        assertEquals("b", targetElement.asString())
    }


    fun `test move enum entry should fail`() {
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

    fun `test move multiple nested class should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class Outer<caret> {
                class Foo { }
                class Bar { }
            }
        """.trimIndent())
        val outerClass = myFixture.elementAtCaret as KtClass
        val nestedClasses = outerClass.declarations.filterIsInstance<KtClass>()
        assertEquals(2, nestedClasses.size)
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(nestedClasses.toTypedArray(), null)
        }
    }

    fun `test move nested class`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class OuterFoo {
                class Ba<caret>r { }
            }
        """.trimIndent())
        val nestedClass = myFixture.elementAtCaret as KtNamedDeclaration
        val moveModel = K2MoveModel.create(arrayOf(nestedClass), null)
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertTrue(moveModel!!.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Bar")
        val targetElement = moveDeclarationsModel.target.pkgName
        assertEquals("foo", targetElement.asString())
    }

    fun `test move nested inner class`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class OuterFoo {
                inner class Ba<caret>r { }
            }
        """.trimIndent())
        val nestedClass = myFixture.elementAtCaret as KtNamedDeclaration
        val moveModel = K2MoveModel.create(arrayOf(nestedClass), null)
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertTrue(moveModel!!.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Bar")
        val targetElement = moveDeclarationsModel.target.pkgName
        assertEquals("foo", targetElement.asString())
    }

    fun `test move instance method`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class OuterFoo {
                fun fo<caret>o() { }
            }
        """.trimIndent())
        val instanceMethod = myFixture.elementAtCaret as KtNamedDeclaration
        val moveModel = K2MoveModel.create(arrayOf(instanceMethod), null)
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertTrue(moveModel!!.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtFunction && sourceElement.name == "foo")
        val targetElement = moveDeclarationsModel.target.pkgName
        assertEquals("foo", targetElement.asString())
    }

    fun `test move multiple instance methods should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class Outer<caret> {
                fun foo() {}
                fun bar() {}
            }
        """.trimIndent())
        val outerClass = myFixture.elementAtCaret as KtClass
        val nestedFunctions = outerClass.declarations.filterIsInstance<KtFunction>()
        assertEquals(2, nestedFunctions.size)
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(nestedFunctions.toTypedArray(), null)
        }
    }

    fun `test move object method`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            object OuterFoo {
                fun fo<caret>o() { }
            }
        """.trimIndent())
        val instanceMethod = myFixture.elementAtCaret as KtNamedDeclaration
        val moveModel = K2MoveModel.create(arrayOf(instanceMethod), null)
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertTrue(moveModel!!.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtFunction && sourceElement.name == "foo")
        val targetElement = moveDeclarationsModel.target.pkgName
        assertEquals("foo", targetElement.asString())
    }

    fun `test move companion object method`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class Foo {
                companion object {
                    fun fo<caret>o() { }
                } 
            }
        """.trimIndent())
        val instanceMethod = myFixture.elementAtCaret as KtNamedDeclaration
        val moveModel = K2MoveModel.create(arrayOf(instanceMethod), null)
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertTrue(moveModel!!.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtFunction && sourceElement.name == "foo")
        val targetElement = moveDeclarationsModel.target.pkgName
        assertEquals("foo", targetElement.asString())
    }

    fun `test move member property`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class Foo {
                val foo<caret> = 5
            }
        """.trimIndent())
        val instanceProperty = myFixture.elementAtCaret as KtNamedDeclaration
        val moveModel = K2MoveModel.create(arrayOf(instanceProperty), null)
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertTrue(moveModel!!.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtProperty && sourceElement.name == "foo")
        val targetElement = moveDeclarationsModel.target.pkgName
        assertEquals("foo", targetElement.asString())
    }

    fun `test move multiple member properties should fal`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            package foo
            
            class Outer<caret> {
                val foo = 5
                val bar = 5
            }
        """.trimIndent())
        val outerClass = myFixture.elementAtCaret as KtClass
        val nestedProperties = outerClass.declarations.filterIsInstance<KtProperty>()
        assertEquals(2, nestedProperties.size)
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(nestedProperties.toTypedArray(), null)
        }
    }

    fun `test move multiple files to non directory should fail`() {
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
        val fooFile = myFixture.addFileToProject("Foo.kt", """
            class Foo { }
        """.trimIndent()) as KtFile
        val moveModel = K2MoveModel.create(arrayOf(fooFile, fooFile.declarations.first()), null)!!
        assertInstanceOf<K2MoveModel.Declarations>(moveModel)
        assertFalse(moveModel.isValidRefactoring())
        val moveDeclarationsModel = moveModel as K2MoveModel.Declarations
        assertSize(1, moveDeclarationsModel.source.elements)
        val sourceElement = moveDeclarationsModel.source.elements.firstOrNull()
        assert(sourceElement is KtClass && sourceElement.name == "Foo")
    }

    fun `test moving override function should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            open class Base {
                open fun fn() {}
            }
            
            open class Derived : Base() {
                override fun fn<caret>() {
                    super.fn()
                }
            }
        """.trimIndent()) as KtFile
        val overrideFunction = myFixture.elementAtCaret as KtNamedFunction
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(overrideFunction), null)
        }
    }

    fun `test moving open function should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            open class Base {
                open fun fn<caret>() {}
            }
            
            open class Derived : Base() {
                override fun fn() {
                    super.fn()
                }
            }
        """.trimIndent()) as KtFile
        val openFunction = myFixture.elementAtCaret as KtNamedFunction
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(openFunction), null)
        }
    }

    fun `test moving abstract class function should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            open class Base {
                abstract fun fn<caret>()
            }
        """.trimIndent()) as KtFile
        val abstractFunction = myFixture.elementAtCaret as KtNamedFunction
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(abstractFunction), null)
        }
    }

    fun `test moving abstract interface function should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            interface Foo {
                fun fn<caret>()
            }
        """.trimIndent()) as KtFile
        val abstractFunction = myFixture.elementAtCaret as KtNamedFunction
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(abstractFunction), null)
        }
    }

    fun `test moving open interface function should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            interface Foo {
                fun fn<caret>() {}
            }
        """.trimIndent()) as KtFile
        val openFunction = myFixture.elementAtCaret as KtNamedFunction
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(openFunction), null)
        }
    }

    fun `test moving companion object should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            class Foo {
                companion object<caret> { }
            }
        """.trimIndent()) as KtFile
        val element = myFixture.elementAtCaret
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(element), null)
        }
    }

    fun `test moving override property should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            open class Base {
                open val v = 1
            }
            
            open class Derived : Base() {
                override val v<caret> = super.v
            }
        """.trimIndent())
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(myFixture.elementAtCaret), null)
        }
    }

    fun `test moving open property should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            open class Base {
                open val v<caret> = 1
            }
        """.trimIndent())
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(myFixture.elementAtCaret), null)
        }
    }

    fun `test moving abstract property should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            open class Base {
                abstract val v<caret> : Int
            }
        """.trimIndent())
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(myFixture.elementAtCaret), null)
        }
    }

    fun `test moving abstract interface property should fail`() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            interface IFace {
                val v<caret> : Int
            }
        """.trimIndent())
        assertThrows(RefactoringErrorHintException::class.java) {
            K2MoveModel.create(arrayOf(myFixture.elementAtCaret), null)
        }
    }
}