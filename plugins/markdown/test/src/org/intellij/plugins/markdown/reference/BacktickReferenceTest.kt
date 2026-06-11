// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.lang.references.backtick.BacktickReference
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BacktickReferenceTest : BasePlatformTestCase() {
  @Test
  fun `test unresolved reference does not yield any errors`() {
    val reference = configureAndGetReferenceAtCaret("There is an `Java<caret>Class` backtick")
    assertTrue(reference is BacktickReference)
    myFixture.checkHighlighting()
  }

  @Test
  fun `test blank code span does not have reference`() {
    val reference = configureAndGetReferenceAtCaret("There is an ` <caret>  ` backtick")
    assertNull(reference)
  }

  @Test
  fun `test reference resolves to original element`() {
    val javaClass = createJavaClass()
    val reference = configureAndGetReferenceAtCaret("There is an `Java<caret>Class` backtick")
    assertTrue(myFixture.psiManager.areElementsEquivalent(javaClass, reference!!.resolve()))
  }

  @Test
  fun `test backtick usage is reported as a dynamic usage`() {
    val javaClass = createJavaClass()
    myFixture.configureByText("some.md", "There is an `JavaClass` backtick")

    val expectations = """
      <root> (1)
       Class
        JavaClass
       Dynamic usages in Project Files (1)
        Value read (1)
         light_idea_test_case (1)
           (1)
           some.md (1)
            1There is an `JavaClass` backtick

    """.trimIndent()
    assertEquals(expectations, myFixture.getUsageViewTreeTextRepresentation(javaClass))
  }

  @Test
  fun `test short symbol references are not resolved`() {
    createFile(
      "JavaClass.java",
      """
        class JavaClass {
           public void short() {}
        }
      """.trimIndent()
    )
    val reference = configureAndGetReferenceAtCaret("some.md", "There is an `sh<caret>ort` backtick")
    assertTrue(reference is BacktickReference)
    assertNull(reference!!.resolve())

    createFile(
      "JavaClass1.java",
      """
        class JavaClass1 {
           public void longlonglong() {}
        }
      """.trimIndent()
    )
    assertResolvesToPsiMethod("some1.md", "There is an `longlo<caret>nglong` backtick")
  }

  @Test
  fun `test short symbol references are resolved if look like code`() {
    createFile(
      "JavaClass.java",
      """
        class JavaClass {
           public void sho_test() {}
        }
      """.trimIndent()
    )
    assertResolvesToPsiMethod("some.md", "There is an `sho<caret>_test` backtick")

    createFile(
      "JavaClass1.java",
      """
        class JavaClass1 {
           public void shoTest() {}
        }
      """.trimIndent()
    )
    assertResolvesToPsiMethod("some1.md", "There is an `sho<caret>Test` backtick")
  }

  @Test
  fun `test renaming original element updates markdown reference`() {
    val javaClass = createJavaClass()
    myFixture.configureByText("some.md", "There is an `JavaClass` backtick")
    myFixture.renameElement(javaClass, "NewJavaClass")
    myFixture.checkResult("There is an `NewJavaClass` backtick")
  }

  @Test
  fun `test qualified class name resolves to class`() {
    val sample = createSampleClass()
    assertResolvesTo("See `com.example.Samp<caret>le` for details", sample)
  }

  @Test
  fun `test qualified name package segment resolves to package`() {
    createSampleClass()
    val reference = configureAndGetReferenceAtCaret("doc.md", "See `com.exa<caret>mple.Sample`")
    val resolved = reference!!.resolve()
    assertInstanceOf(resolved, PsiPackage::class.java)
    assertEquals("com.example", (resolved as PsiPackage).qualifiedName)
  }

  @Test
  fun `test qualified name with hash resolves to method`() {
    val method = createSampleClass().findMethodsByName("doStuff", false).single()
    assertResolvesTo("Call `com.example.Sample#doSt<caret>uff`", method)
  }

  @Test
  fun `test qualified name with hash resolves to variable`() {
    val field = createSampleClass().findFieldByName("varStuff", false)!!
    assertResolvesTo("Call `com.example.Sample#varSt<caret>uff`", field)
  }

  @Test
  fun `test qualified name with dot resolves to method`() {
    val method = createSampleClass().findMethodsByName("doStuff", false).single()
    assertResolvesTo("Call `com.example.Sample.doSt<caret>uff`", method)
  }

  @Test
  fun `test qualified name class part resolves to class when member is present`() {
    val sample = createSampleClass()
    assertResolvesTo("Call `com.example.Samp<caret>le#doStuff`", sample)
  }

  @Test
  fun `test qualified method reference multi resolves overloads`() {
    createFile(
      "com/example/Sample.java",
      """
        package com.example;
        public class Sample {
          public void doStuff() {}
          public void doStuff(int value) {}
        }
      """.trimIndent()
    )
    val reference = configureAndGetReferenceAtCaret("doc.md", "Call `com.example.Sample#doSt<caret>uff`")
    assertInstanceOf(reference, PsiPolyVariantReference::class.java)
    val resolved = (reference as PsiPolyVariantReference).multiResolve(false)
    assertEquals(2, resolved.size)
    assertTrue(resolved.all { it.element is PsiMethod })
  }

  @Test
  fun `test member completion after hash with prefix`() {
    createSampleClass()
    myFixture.configureByText("doc.md", "See `com.example.Sample#do<caret>`")
    myFixture.completeBasic()
    myFixture.checkResult("See `com.example.Sample#doStuff`")
  }

  @Test
  fun `test member completion after bare hash suggests all members`() {
    createSampleClass()
    myFixture.configureByText("doc.md", "See `com.example.Sample#<caret>`")
    val items = myFixture.completeBasic()
    assertNotNull(items)
    assertContainsElements(items!!.map { it.lookupString }, "doStuff", "varStuff")
  }

  @Test
  fun `test member completion deduplicates overloaded methods`() {
    createFile(
      "com/example/Overloaded.java",
      """
        package com.example;
        public class Overloaded {
          public int value;
          public void doStuff() {}
          public void doStuff(int x) {}
        }
      """.trimIndent()
    )
    myFixture.configureByText("doc.md", "See `com.example.Overloaded#<caret>`")
    val items = myFixture.completeBasic()
    assertNotNull(items)
    assertEquals(2, items!!.size)
  }

  @Test
  fun `test package completion`() {
    createSampleClass()
    myFixture.configureByText("doc.md", "See `com.e<caret>`")
    myFixture.completeBasic()
    myFixture.checkResult("See `com.example`")
  }

  @Test
  fun `test path reference resolves from project root`() {
    val target = createFile("community/module-set-plugins/generated/intellij.moduleSet.plugin.main/README.md").parent!!
    val generated = target.parent!!
    val moduleSetPlugins = generated.parent!!
    val community = moduleSetPlugins.parent!!

    assertFileReferenceResolves(
      "docs/references/project-root-community.md",
      "Update of `communit<caret>y/module-set-plugins/generated/intellij.moduleSet.plugin.main/`",
      community
    )
    assertFileReferenceResolves(
      "docs/references/project-root-module-set-plugins.md",
      "Update of `community/module-set<caret>-plugins/generated/intellij.moduleSet.plugin.main/`",
      moduleSetPlugins
    )
    assertFileReferenceResolves(
      "docs/references/project-root-generated.md",
      "Update of `community/module-set-plugins/gene<caret>rated/intellij.moduleSet.plugin.main/`",
      generated
    )
    assertFileReferenceResolves(
      "docs/references/project-root-wrapper.md",
      "Update of `community/module-set-plugins/generated/intellij.moduleSet.plugin.main<caret>/`",
      target
    )
  }

  @Test
  fun `test top level file reference resolves from project root`() {
    val target = createFile("README.md")

    assertFileReferenceResolves(
      "docs/references/top-level-file.md",
      "See `READ<caret>ME.md`",
      target
    )
  }

  @Test
  fun `test skill dir variable path resolves from current skill directory`() {
    val target = createFile("docs/example-skill/scripts/nb.py")

    assertFileReferenceResolves(
      "docs/example-skill/SKILL.md",
      "`$CLAUDE_SKILL_DIR/scripts/nb.<caret>py` is a small CLI",
      target
    )
  }

  @Test
  fun `test skill dir variable segment resolves to current skill directory`() {
    val skillDirectory = createFile("docs/example-skill/scripts/nb.py").parent!!.parent!!

    assertFileReferenceResolves(
      "docs/example-skill/SKILL.md",
      $$"`${CLAUDE_SKILL_D<caret>IR}/scripts/nb.py`",
      skillDirectory
    )
  }

  @Test
  fun `test skill dir variable path provides file variants`() {
    createFile("docs/example-skill/scripts/nb.py")

    val reference = configureAndGetReferenceAtCaret(
      "docs/example-skill/SKILL.md",
      "Run `$CLAUDE_SKILL_DIR/scripts/n<caret>`"
    )

    assertInstanceOf(reference, FileReference::class.java)
    myFixture.completeBasic()
    myFixture.checkResult("Run `$CLAUDE_SKILL_DIR/scripts/nb.py`")
  }

  @Test
  fun `test skill dir variable path does not resolve outside project root`() {
    createFile("../SKILL.md")

    val reference = configureAndGetReferenceAtCaret(
      "docs/example.md",
      "`$CLAUDE_SKILL_DIR/nb.<caret>py`"
    )

    assertFalse(reference is FileReference)
    assertNull(reference?.resolve())
  }

  private fun createJavaClass(): PsiClass {
    val file = createFile("JavaClass.java", "class JavaClass {}")
    return file.children.single { it is PsiClass } as PsiClass
  }

  private fun createSampleClass(): PsiClass {
    val file = createFile(
      "com/example/Sample.java",
      """
        package com.example;
        public class Sample {
          public int varStuff;
          public void doStuff() {}
        }
      """.trimIndent()
    )
    return file.children.single { it is PsiClass } as PsiClass
  }

  private fun createFile(path: String, text: String = ""): PsiFileSystemItem {
    return myFixture.addFileToProject(path, text)
  }

  private fun configureAndGetReferenceAtCaret(text: String): PsiReference? {
    return configureAndGetReferenceAtCaret("some.md", text)
  }

  private fun configureAndGetReferenceAtCaret(fileName: String, text: String): PsiReference? {
    if (fileName.contains('/')) {
      val existingFile = myFixture.addFileToProject(fileName, text)
      myFixture.configureFromExistingVirtualFile(existingFile.virtualFile)
    } else {
      myFixture.configureByText(fileName, text)
    }
    return myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
  }

  private fun assertFileReferenceResolves(fileName: String, text: String, target: PsiFileSystemItem) {
    val reference = configureAndGetReferenceAtCaret(fileName, text)
    if (reference is PsiMultiReference) {
      assertTrue(reference.references.any { it is FileReference })
    } else {
      assertInstanceOf(reference, FileReference::class.java)
    }
    assertTrue(reference!!.isReferenceTo(target))
    assertTrue(myFixture.psiManager.areElementsEquivalent(target, reference.resolve()))
  }

  private fun assertResolvesToPsiMethod(fileName: String, text: String) {
    val reference = configureAndGetReferenceAtCaret(fileName, text)
    assertTrue(reference is BacktickReference)
    assertTrue(reference!!.resolve() is PsiMethod)
  }

  private fun assertResolvesTo(text: String, expected: PsiElement) {
    val reference = configureAndGetReferenceAtCaret("doc.md", text)
    assertNotNull(reference)
    assertTrue(myFixture.psiManager.areElementsEquivalent(expected, reference!!.resolve()))
  }

  companion object {
    private const val CLAUDE_SKILL_DIR = $$"${CLAUDE_SKILL_DIR}"
  }
}
