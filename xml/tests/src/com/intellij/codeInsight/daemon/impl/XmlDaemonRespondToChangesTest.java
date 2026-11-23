// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ExternalResourceManagerExBase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.util.Collection;
import java.util.List;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class XmlDaemonRespondToChangesTest {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typing/";

  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(XmlTestUtil.getXmlTestDataPath());

  @Test
  public void testClearingHighlightersOnType() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd",
                                                              XmlTestUtil.getXmlTestDataPath() + "/xml/web-app_2_4.xsd",
                                                              fixture.getTestRootDisposable());
    fixture.configureByFile(BASE_PATH + fixture.getTestName(false) + ".xml");

    Collection<HighlightInfo> errors = fixture.highlightErrors();
    assertEquals(1, errors.size());
    assertEquals("Element dddddddddd is not allowed here", errors.iterator().next().getDescription());

    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE));

    List<HighlightInfo> afterTyping = ReadAction.computeBlocking(
      () -> DaemonCodeAnalyzerImpl.getHighlights(fixture.getEditor().getDocument(), HighlightSeverity.ERROR, fixture.getProject()));
    assertThat(afterTyping).hasSize(1);
    HighlightInfo error = afterTyping.getFirst();
    assertEquals("Element dddddddddd is not allowed here", error.getDescription());
  }

  @Test
  public void testPasteIntoEmptyFileWithErrorElementOutsideVisibleAreaMustClearThatErrorElement() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByText(XmlFileType.INSTANCE, "");

    List<HighlightInfo> errors = fixture.doHighlighting(HighlightSeverity.ERROR);
    assertThat(errors).hasSize(1);
    HighlightInfo error = errors.getFirst();
    assertNotNull(error);

    Editor myEditor = fixture.getEditor();
    Document document = myEditor.getDocument();
    @Language("XML")
    String text = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                  "<root>" + StringUtil.repeat("\n", 43) + "</root>";

    WriteCommandAction.runWriteCommandAction(fixture.getProject(), ()->document.setText(text));

    EdtTestUtil.runInEdtAndWait(() -> {
      // repros for shrinked editor only for some reason
      ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(100, 100);
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      ((EditorImpl)myEditor).getScrollPane().getViewport().setViewPosition(new Point(0, 0));
      ((EditorImpl)myEditor).getScrollPane().getViewport().setExtentSize(new Dimension(100, 100));
      myEditor.getCaretModel().moveToOffset(document.getTextLength());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    });

    assertThat(fixture.doHighlighting(HighlightSeverity.ERROR)).isEmpty();
  }
}
