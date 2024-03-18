package org.jetbrains.idea.maven.intentions

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.intentions.AddMavenDependencyQuickFix
import org.junit.Test
import java.io.IOException

class MavenAddDependencyIntentionTest : MavenDomTestCase() {
  override fun runInDispatchThread() = true

  override fun setUp() = runBlocking {
    super.setUp()
    importProjectAsync("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>")
  }

  @Test
  @Throws(IOException::class)
  fun testAddDependencyVariableDeclaration() {
    doTest("""
             class A {
               void foo() {
                 Fo<caret>o x = null;
               }
             }
             
             """.trimIndent(), "Foo")
  }

  @Test
  @Throws(IOException::class)
  fun testAddDependencyWithQualifier() {
    doTest("""
             class A {
               void foo() {
                 java.xxx<caret>x.Foo foo;
               }
             }
             
             """.trimIndent(), "java.xxxx.Foo")
  }

  @Test
  @Throws(IOException::class)
  fun testAddDependencyNotAClass() {
    doTest("""
             class A {
               void foo() {
                 return foo<caret>Xxx;
               }
             }
             
             """.trimIndent(), null)
  }

  @Test
  @Throws(IOException::class)
  fun testAddDependencyFromExtendsWithGeneric() {
    doTest("""
             class A extends Fo<caret>o<String> {
               void foo() { }
             }
             
             """.trimIndent(), "Foo")
  }

  @Test
  @Throws(IOException::class)
  fun testAddDependencyFromClassInsideGeneric() {
    doTest("""
             class A extends List<Fo<caret>o> {
               void foo() { }
             }
             
             """.trimIndent(), "Foo")
  }

  @Test
  @Throws(IOException::class)
  fun testAddDependencyFromClassInsideGenericWithExtends() {
    doTest("""
             class A extends List<? extends Fo<caret>o> {
               void foo() { }
             }
             
             """.trimIndent(), "Foo")
  }

  @Throws(IOException::class)
  private fun doTest(classText: String, referenceText: String?) {
    val file = createProjectSubFile("src/main/java/A.java", classText)

    fixture.configureFromExistingVirtualFile(file)
    val element = PsiTreeUtil.getParentOfType(fixture.getFile().findElementAt(fixture.getCaretOffset()),
                                              PsiJavaCodeReferenceElement::class.java)

    assertNull(element!!.resolve())

    val fix = AddMavenDependencyQuickFix(element)

    if (referenceText == null) {
      assertFalse(fix.isAvailable(project, fixture.getEditor(), fixture.getFile()))
    }
    else {
      assertEquals(fix.getReferenceText(), referenceText)
    }
  }
}