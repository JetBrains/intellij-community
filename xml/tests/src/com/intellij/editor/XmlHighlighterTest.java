// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editor;

import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

import java.util.List;

public class XmlHighlighterTest extends LightJavaCodeInsightTestCase {
  private Document doc;
  private EditorEx editor;
  private EditorHighlighter highlighter;
  private int offset;

  private void configure(String s1, String s2, FileType fileType) {
    offset = s1.length();
    doc = new DocumentImpl(s1 + s2);
    editor = (EditorEx)EditorFactory.getInstance().createEditor(doc);
    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, null, null);
    assert syntaxHighlighter != null;
    highlighter = new LexerEditorHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme());
    editor.setHighlighter(highlighter);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EditorFactory.getInstance().releaseEditor(editor);
      editor = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testRecoveryAfterAttributeModification() {
    configure("<root><tag attr=\"", "value\"/></root>", XmlFileType.INSTANCE);

    runTest(() -> {
      doc.insertString(offset, "q");
      doc.insertString(offset + 1, "q");
    });
  }

  public void testRecoveryInXHTML() {
    configure("<html:form action=\"/arquivo/associar\">\n" +
              "  <html:hidden property=\"idAcao\"/>\n" +
              "  ","<html:hidden property=\"possuiMensagem\"/>\n" +
                     "</html:form>", XHtmlFileType.INSTANCE);

    runTest(() -> doc.insertString(offset, "\n"));
  }

  public void testUnclosedCommentAtEnd() {
    configure("<a></a>\n<!--", "", XHtmlFileType.INSTANCE);

    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> doc.insertString(offset, " -")), "", null);
    List<IElementType> newTokens = EditorTestUtil.getAllTokens(highlighter);
    assertSame("Detecting wrong char in comment data", XmlTokenType.XML_BAD_CHARACTER, newTokens.get(newTokens.size() - 1));
  }

  private void runTest(Runnable action) {
    List<IElementType> oldTokens = EditorTestUtil.getAllTokens(highlighter);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(action), "", null);
    List<IElementType> newTokens = EditorTestUtil.getAllTokens(highlighter);
    assertEquals(oldTokens, newTokens);
  }
}
