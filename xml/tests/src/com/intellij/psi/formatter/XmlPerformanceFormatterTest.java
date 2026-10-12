// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter;

import com.intellij.codeHighlighting.Pass;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.semantic.SemService;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PerformanceUnitTest
@StressTestApplication
public class XmlPerformanceFormatterTest extends XmlFormatterTestBase {
  private static final String BASE_PATH = "psi/formatter/xml";
  private static final Set<String> ourTestsWithDocumentUpdate = Set.of("Performance3", "Performance4");

  private EditorImpl myEditor;
  private PsiFile myFile;

  @BeforeEach
  void setUp() {
    @SuppressWarnings("UnusedDeclaration") Class clazz = IdeaTestUtil.class;
    SemService.getSemService(getProject()); // insanely expensive
  }

  @AfterEach
  void tearDown() {
    if (myFile != null) {
      VirtualFile virtualFile = myFile.getVirtualFile();
      EdtTestUtil.runInEdtAndWait(() -> {
        ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(virtualFile);
        FileEditorManager.getInstance(getProject()).closeFile(virtualFile);
      });
    }
    myEditor = null;
    myFile = null;
  }

  /**
   * Opens an editor for the file before the check, as {@code FormatterTestCase.doCheckDocumentUpdate} did.
   */
  @Override
  protected void checkDocument(@NotNull PsiFile file, @NotNull String text, @NotNull String textAfter) {
    if (ourTestsWithDocumentUpdate.contains(getTestName(false))) {
      EditorImpl editor = EdtTestUtil.runInEdtAndGet(() -> {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(getProject());
        EditorImpl openedEditor =
          (EditorImpl)fileEditorManager.openTextEditor(new OpenFileDescriptor(getProject(), file.getVirtualFile(), 0), false);
        if (myFile != null) {
          fileEditorManager.closeFile(myFile.getVirtualFile());
        }
        return openedEditor;
      });
      assertNotNull(editor);
      myEditor = editor;
      myFile = file;
    }
    super.checkDocument(file, text, textAfter);
  }

  @Test
  public void testReformatCodeFragment() {
    Benchmark.newBenchmark("reformat code fragment",
                           () -> checkFormattingDoesNotProduceException("performance")).start();
  }

  @Test
  public void testPerformance3() {
    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    try {
      Benchmark.newBenchmark("xml formatter", createTestRunnable()).start();

      highlight();

      final UndoManager undoManager = UndoManager.getInstance(getProject());
      final FileEditor selectedEditor = EdtTestUtil.runInEdtAndGet(() -> editorManager.getSelectedEditor(myFile.getVirtualFile()));

      assertTrue(EdtTestUtil.runInEdtAndGet(() -> undoManager.isUndoAvailable(selectedEditor)));
      Benchmark.newBenchmark("xml formatter undo", () -> EdtTestUtil.runInEdtAndWait(() -> undoManager.undo(selectedEditor)))
        .warmupIterations(0).attempts(1).start();
      EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(getProject()).commitAllDocuments());

      highlight();

      Benchmark.newBenchmark("xml formatter redo", () -> EdtTestUtil.runInEdtAndWait(() -> undoManager.redo(selectedEditor)))
        .warmupIterations(0).attempts(1).start();
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        final VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
        if (selectedFiles.length > 0) editorManager.closeFile(selectedFiles[0]);
      });
    }
  }

  private void highlight() {
    CodeInsightTestFixtureImpl.instantiateAndRun(myFile, myEditor, new int[]{Pass.UPDATE_ALL, Pass.LOCAL_INSPECTIONS}, false);
  }

  @Test
  public void testPerformance4() {
    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    try {
      Benchmark.newBenchmark("xml formatter", createTestRunnable()).start();
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> editorManager.closeFile(editorManager.getSelectedFiles()[0]));
    }
  }

  private ThrowableRunnable<Exception> createTestRunnable() {
    return () -> {
      try {
        doTest();
      }
      finally {
        EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
      }
    };
  }

  @Test
  public void testPerformance5() {
    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    try {
      Benchmark.newBenchmark("xml formatter", createTestRunnable()).start();
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> {
        final VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
        for (VirtualFile selectedFile : selectedFiles) {
          editorManager.closeFile(selectedFile);
        }
      });
    }
  }

  @Test
  public void testPerformance6() {
    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    try {
      Benchmark.newBenchmark("xml formatter", createTestRunnable()).start();
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> {
        final VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
        if (selectedFiles.length > 0) editorManager.closeFile(selectedFiles[0]);
      });
    }
  }

  @Test
  public void testPerformance7() {
    Benchmark.newBenchmark("xml formatter", createTestRunnable()).start();
  }

  @Test
  public void testPerformance() throws Exception {
    CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_KEEP_LINE_BREAKS = true;
    xmlSettings.XML_KEEP_BLANK_LINES = 2;
    doTestDoNotWrap(null, true, 140);
  }

  @Test
  public void testPerformanceIdea148943() throws Exception {
    final String textBefore = loadFile(getTestName(true) + ".xml", null);
    final PsiFile file = createFileFromText(textBefore, "before.xml");
    Benchmark.newBenchmark("IDEA-148943", createAdjustLineIndentInRangeRunnable(file))
      .start();
  }

  private ThrowableRunnable<RuntimeException> createAdjustLineIndentInRangeRunnable(final @NotNull PsiFile file) {
    return () -> WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      assertNotNull(document);
      CodeStyleManager.getInstance(getProject()).adjustLineIndent(file, file.getTextRange());
    });
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
      EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
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
