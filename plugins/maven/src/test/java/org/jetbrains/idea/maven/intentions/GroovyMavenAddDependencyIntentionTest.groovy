/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.intentions

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.idea.maven.dom.MavenDomTestCase
import org.jetbrains.idea.maven.dom.intentions.GroovyAddMavenDependencyQuickFix
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement

/**
 * @author Sang-Jin Park
 */
class GroovyMavenAddDependencyIntentionTest extends MavenDomTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    importProject("""
"<groupId>test</groupId>" +
"<artifactId>project</artifactId>" +
"<version>1</version>"
""")
  }

  public void testAddDependencyVariableDeclaration() {
    doTest("""
class A {
  def foo() {
    Fo<caret>o x = null
  }
}
""", "Foo")
  }

  public void testAddDependencyWithQualifier() {
    doTest("""
class A {
  def foo() {
    java.xxx<caret>x.Foo foo
  }
}
""", "java.xxxx.Foo")
  }

  public void testAddDependencyNotAClass() {
    doTest("""
class A {
  def foo() {
    return foo<caret>Xxx
  }
}
""", null)
  }

  public void testAddDependencyFromExtendsWithGeneric() {
    doTest("""
class A extends Fo<caret>o<String> {
  def foo() { }
}
""", "Foo")
  }

  public void testAddDependencyFromClassInsideGeneric() {
    doTest("""
class A extends List<Fo<caret>o> {
  def foo() { }
}
""", "Foo")
  }

  public void testAddDependencyFromClassInsideGenericWithExtends() {
    doTest("""
class A extends List<? extends Fo<caret>o> {
  def foo() { }
}
""", "Foo")
  }

  private void doTest(String classText, @Nullable String referenceText) {
    def file = createProjectSubFile("src/main/groovy/A.groovy", classText)

    myFixture.configureFromExistingVirtualFile(file)
    def element = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), GrReferenceElement)

    assert element.resolve() == null

    GroovyAddMavenDependencyQuickFix fix = new GroovyAddMavenDependencyQuickFix(element)

    if (referenceText == null) {
      assert !fix.isAvailable(myProject, myFixture.editor, myFixture.file)
    }
    else {
      assert fix.referenceText == referenceText
    }
  }

}
