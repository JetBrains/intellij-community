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

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.xslt.refactoring.VariableInlineHandler;
import org.intellij.lang.xpath.xslt.refactoring.extractTemplate.XsltExtractTemplateAction;
import org.jetbrains.annotations.Nullable;

public class XsltRefactoringTest extends TestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(myFixture.getProject()).getCurrentSettings();
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = true;
    settings.getIndentOptions(StdFileTypes.XML).INDENT_SIZE = 2;
  }

  public void testExtractTemplate() {
    doExtractTemplate();
  }

  public void testExtractTemplateWithComment() {
    doExtractTemplate();
  }

  public void testExtractTemplateOneVar() {
    doExtractTemplate();
  }

  public void testExtractTemplateOneVar2() {
    doExtractTemplate();
  }

  public void testExtractTemplateTwoVars() {
    doExtractTemplate();
  }

  public void testExtractTemplateTwoVars2() {
    doExtractTemplate();
  }

  public void testExtractTemplateUnresolvedVar() {
    doExtractTemplate();
  }

  public void testExtractTemplateUnresolvedVar2() {
    doExtractTemplate();
  }

  private void doExtractTemplate() {
    myFixture.configureByFile(getTestFileName() + ".xsl");

    final XsltExtractTemplateAction action = new XsltExtractTemplateAction();
    assertTrue(action.invokeImpl(myFixture.getEditor(), myFixture.getFile(), "foo"));

    myFixture.checkResultByFile(getTestFileName() + "_after.xsl");
  }

  public void testInlineVariable() {
    doInlineVariable();
  }

  public void testInlineVariableOnDecl() {
    doInlineVariable();
  }

  private void doInlineVariable() {
    myFixture.configureByFile(getTestFileName() + ".xsl");

    final XPathVariable variable = findVariable();
    assertNotNull(variable);
    VariableInlineHandler.invoke(variable, myFixture.getEditor());

    myFixture.checkResultByFile(getTestFileName() + "_after.xsl");
  }

  @Nullable
  private XPathVariable findVariable() {
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

  @Override
  protected String getSubPath() {
    return "xslt/refactoring";
  }
}
