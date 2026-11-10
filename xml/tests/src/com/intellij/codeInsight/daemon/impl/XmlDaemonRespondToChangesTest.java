// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ExternalResourceManagerExBase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.awt.Point;
import java.util.Collection;
import java.util.List;

public class XmlDaemonRespondToChangesTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typing/";

  @Override
  public @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testClearingHighlightersOnType() throws Exception {
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd",
                                                              getTestDataPath() + "/xml/web-app_2_4.xsd",
                                                              getTestRootDisposable());
    configureByFile(BASE_PATH + getTestName(false) + ".xml");

    Collection<HighlightInfo> errors = highlightErrors();
    assertEquals(1, errors.size());
    assertEquals("Element dddddddddd is not allowed here", errors.iterator().next().getDescription());

    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE);

    List<HighlightInfo> afterTyping = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.ERROR, getProject());
    HighlightInfo error = assertOneElement(afterTyping);
    assertEquals("Element dddddddddd is not allowed here", error.getDescription());
  }

  public void testPasteIntoEmptyFileWithErrorElementOutsideVisibleAreaMustClearThatErrorElement() {
    configureByText(XmlFileType.INSTANCE, "");

    HighlightInfo error = assertOneElement(doHighlighting(HighlightSeverity.ERROR));
    assertNotNull(error);

    Document document = getEditor().getDocument();
    @Language("XML")
    String text = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                  "<root>" + StringUtil.repeat("\n", 43) + "</root>";

    WriteCommandAction.runWriteCommandAction(getProject(), ()->document.setText(text));

    // repros for shrinked editor only for some reason
    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(100, 100);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ((EditorImpl)myEditor).getScrollPane().getViewport().setViewPosition(new Point(0, 0));
    ((EditorImpl)myEditor).getScrollPane().getViewport().setExtentSize(new Dimension(100, 100));
    myEditor.getCaretModel().moveToOffset(document.getTextLength());
    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

    assertEmpty(doHighlighting(HighlightSeverity.ERROR));
  }
}
