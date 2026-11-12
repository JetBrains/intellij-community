/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.lang.xpath.xslt;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.xpath.XPathTestUtil;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.xslt.refactoring.VariableInlineHandler;
import org.intellij.lang.xpath.xslt.refactoring.extractTemplate.XsltExtractTemplateAction;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class XsltRefactoringTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(XPathTestUtil.getTestDataPath("xslt/refactoring"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = true;
    settings.getIndentOptions(XmlFileType.INSTANCE).INDENT_SIZE = 2;
  }

  @Test
  public void testExtractTemplate() {
    doExtractTemplate();
  }

  @Test
  public void testExtractTemplateWithComment() {
    doExtractTemplate();
  }

  @Test
  public void testExtractTemplateOneVar() {
    doExtractTemplate();
  }

  @Test
  public void testExtractTemplateOneVar2() {
    doExtractTemplate();
  }

  @Test
  public void testExtractTemplateTwoVars() {
    doExtractTemplate();
  }

  @Test
  public void testExtractTemplateTwoVars2() {
    doExtractTemplate();
  }

  @Test
  public void testExtractTemplateUnresolvedVar() {
    doExtractTemplate();
  }

  @Test
  public void testExtractTemplateUnresolvedVar2() {
    doExtractTemplate();
  }

  private void doExtractTemplate() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testFileName + ".xsl");

    final XsltExtractTemplateAction action = new XsltExtractTemplateAction();
    assertTrue(EdtTestUtil.runInEdtAndGet(() -> action.invokeImpl(myFixture.getEditor(), myFixture.getFile(), "foo")));

    myFixture.checkResultByFile(testFileName + "_after.xsl");
  }

  @Test
  public void testInlineVariable() {
    doInlineVariable();
  }

  @Test
  public void testInlineVariableOnDecl() {
    doInlineVariable();
  }

  private void doInlineVariable() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testFileName + ".xsl");

    EdtTestUtil.runInEdtAndWait(() -> {
      final XPathVariable variable = findVariable();
      assertNotNull(variable);
      VariableInlineHandler.invoke(variable, myFixture.getEditor());
    });

    myFixture.checkResultByFile(testFileName + "_after.xsl");
  }

  @Nullable
  private XPathVariable findVariable() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiElement elementAtCaret = myFixture.getElementAtCaret();
    XPathVariableReference ref = PsiTreeUtil.getParentOfType(elementAtCaret, XPathVariableReference.class, false);
    if (ref != null) {
      return ref.resolve();
    }
    XPathVariable variable = PsiTreeUtil.getParentOfType(elementAtCaret, XPathVariable.class, false);
    if (variable != null) {
      return variable;
    }
    final PsiReference[] references = elementAtCaret.getReferences();
    for (PsiReference reference : references) {
      final PsiElement var = reference.resolve();
      if (var instanceof XPathVariable) {
        return (XPathVariable)var;
      }
    }
    return null;
  }
}
