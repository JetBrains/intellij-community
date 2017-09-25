/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.refactoring.RefactoringOptions;
import org.intellij.lang.xpath.xslt.refactoring.XsltExtractFunctionAction;

import java.util.Set;

public class Xslt2RefactoringTest extends TestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(myFixture.getProject()).getCurrentSettings();
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = true;
    settings.getIndentOptions(StdFileTypes.XML).INDENT_SIZE = 2;
  }

  public void testExtractFunction() {
    doExtractFunction();
  }

  public void testExtractFunction2() {
    doExtractFunction();
  }

  private void doExtractFunction() {
    myFixture.configureByFile(getTestFileName() + ".xsl");
    final Editor editor = myFixture.getEditor();

    assertTrue("Selection required", editor.getSelectionModel().hasSelection());
    editor.getCaretModel().moveToOffset(editor.getSelectionModel().getSelectionStart());

    final XsltExtractFunctionAction action = new XsltExtractFunctionAction() {
      @Override
      protected RefactoringOptions getSettings(XPathExpression expression, Set<XPathExpression> matchingExpressions) {
        return new RefactoringOptions() {
          @Override
          public boolean isCanceled() {
            return false;
          }

          @Override
          public String getName() {
            return "f:foo";
          }
        };
      }
    };

    final PsiFile file = InjectedLanguageUtil.findInjectedPsiNoCommit(myFixture.getFile(), editor.getCaretModel().getOffset());
    final Editor editorWindow = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, file);
    assertTrue(editorWindow instanceof EditorWindow);

    action.invoke(myFixture.getProject(), editorWindow, file, null);

    myFixture.checkResultByFile(getTestFileName() + "_after.xsl");
  }

  @Override
  protected String getSubPath() {
    return "xslt2/refactoring";
  }
}
