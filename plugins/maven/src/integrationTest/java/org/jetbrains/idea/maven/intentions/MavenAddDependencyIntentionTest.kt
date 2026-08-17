package org.jetbrains.idea.maven.intentions

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.intentions.AddMavenDependencyQuickFix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.concurrent.Callable

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenAddDependencyIntentionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.importProjectAsync("<groupId>test</groupId>" +
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
      maven.fixture.configureByText("A.java", classText)
      ReadAction.nonBlocking(Callable {
        val element = PsiTreeUtil.getParentOfType(maven.fixture.getFile().findElementAt(maven.fixture.getCaretOffset()),
                                                  PsiJavaCodeReferenceElement::class.java)

        assertNull(element!!.resolve())

        val fix = AddMavenDependencyQuickFix(element)

        if (referenceText == null) {
          assertFalse(fix.isAvailable(maven.project, maven.fixture.getEditor(), maven.fixture.getFile()))
        }
        else {
          assertEquals(fix.getReferenceText(), referenceText)
        }
      }).inSmartMode(maven.project).executeSynchronously()
    }
  }
}