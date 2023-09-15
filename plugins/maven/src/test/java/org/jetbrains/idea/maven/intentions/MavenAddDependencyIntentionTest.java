package org.jetbrains.idea.maven.intentions;

import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.intentions.AddMavenDependencyQuickFix;
import org.junit.Test;

import java.io.IOException;

public class MavenAddDependencyIntentionTest extends MavenDomTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  @Test
  public void testAddDependencyVariableDeclaration() throws IOException {
    doTest("""
             class A {
               void foo() {
                 Fo<caret>o x = null;
               }
             }
             """, "Foo");
  }

  @Test
  public void testAddDependencyWithQualifier() throws IOException {
    doTest("""
             class A {
               void foo() {
                 java.xxx<caret>x.Foo foo;
               }
             }
             """, "java.xxxx.Foo");
  }

  @Test
  public void testAddDependencyNotAClass() throws IOException {
    doTest("""
             class A {
               void foo() {
                 return foo<caret>Xxx;
               }
             }
             """, null);
  }

  @Test
  public void testAddDependencyFromExtendsWithGeneric() throws IOException {
    doTest("""
             class A extends Fo<caret>o<String> {
               void foo() { }
             }
             """, "Foo");
  }

  @Test
  public void testAddDependencyFromClassInsideGeneric() throws IOException {
    doTest("""
             class A extends List<Fo<caret>o> {
               void foo() { }
             }
             """, "Foo");
  }

  @Test
  public void testAddDependencyFromClassInsideGenericWithExtends() throws IOException {
    doTest("""
             class A extends List<? extends Fo<caret>o> {
               void foo() { }
             }
             """, "Foo");
  }

  private void doTest(String classText, @Nullable String referenceText) throws IOException {
    var file = createProjectSubFile("src/main/java/A.java", classText);

    myFixture.configureFromExistingVirtualFile(file);
    PsiJavaCodeReferenceElement element = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiJavaCodeReferenceElement.class);

    assertNull(element.resolve());

    AddMavenDependencyQuickFix fix = new AddMavenDependencyQuickFix(element);

    if (referenceText == null) {
      assertFalse(fix.isAvailable(myProject, myFixture.getEditor(), myFixture.getFile()));
    } else {
      assertEquals(fix.getReferenceText(), referenceText);
    }
  }
}