/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class XmlSyncTagTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testStartToEnd() {
    doTest("<div<caret>></div>", "v", "<divv></divv>");
  }

  public void testEndToStart() {
    doTest("<div></div<caret>>", "v", "<divv></divv>");
  }

  public void testStartToEndAndEndToStart() {
    doTest("<div<caret>></div>", "v", "<divv></divv>");
    myFixture.getEditor().getCaretModel().moveToOffset(12);
    type("\b");
    myFixture.checkResult("<div></div>");
  }

  public void testLastCharDeleted() {
    doTest("<div<caret>></div>", "\b\b\b", "<></>");
  }

  public void testLastCharDeletedAndNewAdded() {
    doTest("<a<caret> alt='</>'></a>", "\bb", "<b alt='</>'></b>");
  }

  public void testSelection() {
    doTest("<<selection>div</selection>></div>", "b", "<b></b>");
  }

  public void testMultiCaret() {
    doTest("<div<caret>></div>\n" +
           "<div<caret>></div>\n", "v",
           "<divv></divv>\n" +
           "<divv></divv>\n");
  }

  public void testMultiCaretNested() {
    doTest("<div<caret>>\n" +
           "<div<caret>></div>\n" +
           "</div>", "v",
           "<divv>\n" +
           "<divv></divv>\n" +
           "</divv>");
  }

  public void testSpace() {
    doTest("<div<caret>></div>", " ", "<div ></div>");
  }

  public void testRecommence() {
    doTest("<divv<caret>></div>", "\bd", "<divd></divd>");
  }

  public void testCompletionSimple() {
    doTestCompletion("<html><body></body><b<caret>></b><html>", null,
                     "<html><body></body><body></body><html>");
  }

  public void testCompletionWithLookup() {
    doTestCompletion("<html><body></body><bertran></bertran><b<caret>></b><html>", "e\n",
                     "<html><body></body><bertran></bertran><bertran></bertran><html>");
  }

  public void testUndo() {
    doTest("<div<caret>></div>", "v", "<divv></divv>");
    myFixture.performEditorAction(IdeActions.ACTION_UNDO);
    myFixture.checkResult("<div></div>");
  }

  public void testInjection() {
    final MultiHostInjector injector = new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (context instanceof XmlAttributeValue) {
          registrar.startInjecting(XMLLanguage.INSTANCE)
            .addPlace(null, null, (PsiLanguageInjectionHost)context, new TextRange(1, context.getTextLength() - 1))
            .doneInjecting();
        }
      }

      @NotNull
      @Override
      public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(XmlAttributeValue.class);
      }
    };
    InjectedLanguageManager.getInstance(getProject()).registerMultiHostInjector(injector);
    try {
      doTest("<div injected='<div<caret>></div>'></div>", "v", "<div injected='<divv></divv>'></div>");
    } finally {
      InjectedLanguageManager.getInstance(getProject()).unregisterMultiHostInjector(injector);
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    WebEditorOptions.getInstance().setSyncTagEditing(true);
    myFixture.setCaresAboutInjection(false);
  }

  @Override
  protected void tearDown() throws Exception {
    WebEditorOptions.getInstance().setSyncTagEditing(false);
    super.tearDown();
  }

  private void doTest(final String text, final String toType, final String result) {
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    type(toType);
    myFixture.checkResult(result);
  }

  private void type(String toType) {
    for (int i = 0; i < toType.length(); i++) {
      final char c = toType.charAt(i);
      CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              myFixture.type(c);
            }
          });
        }
      }, "Typing", DocCommandGroupId.noneGroupId(myFixture.getEditor().getDocument()), myFixture.getEditor().getDocument());
    }
  }

  private void doTestCompletion(final String text, final String toType, final String result) {
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            myFixture.completeBasic();
            if (toType != null) myFixture.type(toType);
          }
        });
      }
    }, "Typing", DocCommandGroupId.noneGroupId(myFixture.getEditor().getDocument()), myFixture.getEditor().getDocument());
    myFixture.checkResult(result);
  }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }
}
