package org.jetbrains.idea.maven.intentions

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.intentions.AddMavenDependencyQuickFix
import org.junit.Test
import java.util.concurrent.Callable

class MavenAddDependencyIntentionTest : MavenDomTestCase() {
  override fun setUp() = runBlocking {
    super.setUp()
    importProjectAsync("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<version>1</version>")
  }

  @Test
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
  fun testAddDependencyFromExtendsWithGeneric() {
    doTest("""
             class A extends Fo<caret>o<String> {
               void foo() { }
             }
             
             """.trimIndent(), "Foo")
  }

  @Test
  fun testAddDependencyFromClassInsideGeneric() {
    doTest("""
             class A extends List<Fo<caret>o> {
               void foo() { }
             }
             
             """.trimIndent(), "Foo")
  }

  @Test
  fun testAddDependencyFromClassInsideGenericWithExtends() {
    doTest("""
             class A extends List<? extends Fo<caret>o> {
               void foo() { }
             }
             
             """.trimIndent(), "Foo")
  }

  private fun doTest(classText: String, referenceText: String?) = runBlocking(Dispatchers.EDT) {
    writeIntentReadAction {
      fixture.configureByText("A.java", classText)
      ReadAction.nonBlocking(Callable {
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
      }).inSmartMode(project).executeSynchronously()
    }
  }
}