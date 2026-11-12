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

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.xpath.XPathTestUtil;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.refactoring.RefactoringOptions;
import org.intellij.lang.xpath.xslt.refactoring.XsltExtractFunctionAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Set;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class Xslt2RefactoringTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(XPathTestUtil.getTestDataPath("xslt2/refactoring"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = true;
    settings.getIndentOptions(XmlFileType.INSTANCE).INDENT_SIZE = 2;
  }

  @Test
  public void testExtractFunction() {
    doExtractFunction();
  }

  @Test
  public void testExtractFunction2() {
    doExtractFunction();
  }

  private void doExtractFunction() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testFileName + ".xsl");
    EdtTestUtil.runInEdtAndWait(() -> {
      final Editor editor = myFixture.getEditor();

      assertTrue(editor.getSelectionModel().hasSelection(), "Selection required");
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
    });

    myFixture.checkResultByFile(testFileName + "_after.xsl");
  }
}
