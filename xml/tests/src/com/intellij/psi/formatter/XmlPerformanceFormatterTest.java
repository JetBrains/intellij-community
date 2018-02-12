/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.formatter;

import com.intellij.codeHighlighting.Pass;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.semantic.SemService;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class XmlPerformanceFormatterTest extends XmlFormatterTestBase {
  private static final String BASE_PATH = "psi/formatter/xml";
  private final Set<String> ourTestsWithDocumentUpdate = new HashSet<>(Arrays.asList("Performance3", "Performance4"));

  @Override
  protected boolean doCheckDocumentUpdate() {
    return ourTestsWithDocumentUpdate.contains(getTestName(false));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    @SuppressWarnings("UnusedDeclaration") Class clazz = IdeaTestUtil.class;
    SemService.getSemService(getProject()); // insanely expensive
  }

  public void testReformatCodeFragment() {
    PlatformTestUtil.startPerformanceTest("reformat code fragment", 6300,
                                          () -> checkFormattingDoesNotProduceException("performance")).useLegacyScaling().assertTiming();
  }

  public void testPerformance3() {
    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    try {
      PlatformTestUtil.startPerformanceTest("xml formatter", 6800, createTestRunnable()).useLegacyScaling().assertTiming();

      highlight();

      long start = System.currentTimeMillis();
      final UndoManager undoManager = UndoManager.getInstance(getProject());
      final FileEditor selectedEditor = editorManager.getSelectedEditor(myFile.getVirtualFile());

      assertTrue(undoManager.isUndoAvailable(selectedEditor));
      undoManager.undo(selectedEditor);
      long end = System.currentTimeMillis();

      PlatformTestUtil.assertTiming("Fix xml formatter undo performance problem", 3400, end - start);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

      highlight();

      start = System.currentTimeMillis();
      undoManager.redo(selectedEditor);
      end = System.currentTimeMillis();

      PlatformTestUtil.assertTiming("Fix xml formatter redo performance problem", 3400, end - start);
    }
    finally {
      UIUtil.dispatchAllInvocationEvents();
      final VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
      if (selectedFiles.length > 0) editorManager.closeFile(selectedFiles[0]);
    }
  }

  private void highlight() {
    CodeInsightTestFixtureImpl.instantiateAndRun(myFile, myEditor, new int[]{Pass.UPDATE_ALL, Pass.LOCAL_INSPECTIONS}, false);
  }

  public void testPerformance4() {
    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    try {
      PlatformTestUtil.startPerformanceTest("xml formatter", 20000, createTestRunnable()).useLegacyScaling().assertTiming();
    }
    finally {
      editorManager.closeFile(editorManager.getSelectedFiles()[0]);
    }
  }

  private ThrowableRunnable createTestRunnable() {
    return () -> {
      try {
        doTest();
      }
      finally {
        UIUtil.dispatchAllInvocationEvents();
      }
  };
  }

  public void testPerformance5() {
    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    try {
      PlatformTestUtil.startPerformanceTest("xml formatter", 10000, createTestRunnable()).useLegacyScaling().assertTiming();
    }
    finally {
      final VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
      for (VirtualFile selectedFile : selectedFiles) {
        editorManager.closeFile(selectedFile);
      }
    }
  }

  public void testPerformance6() {
    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    try {
      PlatformTestUtil.startPerformanceTest("xml formatter", 20000, createTestRunnable()).useLegacyScaling().assertTiming();
    }
    finally {
      final VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
      if (selectedFiles.length > 0) editorManager.closeFile(selectedFiles[0]);
    }
  }

  public void testPerformance() throws Exception {
    CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_KEEP_LINE_BREAKS = true;
    xmlSettings.XML_KEEP_BLANK_LINES = 2;
    doTestDoNotWrap(null, true, 140);
  }

  public void testPerformanceIdea148943() throws Exception {
    final String textBefore = loadFile(getTestName(true) + ".xml", null);
    final PsiFile file = createFileFromText(textBefore, "before.xml", PsiFileFactory.getInstance(getProject()));
    PlatformTestUtil.startPerformanceTest("IDEA-148943", 20000, createAdjustLineIndentInRangeRunnable(file))
      .assertTiming();
  }
  
  private static ThrowableRunnable createAdjustLineIndentInRangeRunnable(final @NotNull PsiFile file) {
    return () -> {
      final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      assertNotNull(document);
      CodeStyleManager.getInstance(getProject()).adjustLineIndent(file, file.getTextRange());
    };
  }

  private void doTestDoNotWrap(String resultNumber, boolean align, int rightMargin) throws Exception {
    CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    boolean oldValue = xmlSettings.XML_KEEP_WHITESPACES;
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_KEEP_LINE_BREAKS = true;
    settings.setRightMargin(XMLLanguage.INSTANCE, rightMargin);
    xmlSettings.XML_ALIGN_ATTRIBUTES = align;
    try {
      doTest(resultNumber);
    }
    finally {
      UIUtil.dispatchAllInvocationEvents();
      xmlSettings.XML_KEEP_WHITESPACES = oldValue;
    }
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected String getFileExtension() {
    return "xml";
  }
}
