package com.intellij.codeInsight;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestData;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

/**
 * @author Mike
 */
public abstract class CodeInsightTestCase extends PsiTestCase {
  protected Editor myEditor;

  public CodeInsightTestCase() {
    myRunCommandForTest = true;
  }

  protected Editor createEditor(VirtualFile file) {
    return FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
  }

  protected void tearDown() throws Exception {
    FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (int i = 0; i < openFiles.length; i++) {
      editorManager.closeFile(openFiles[i]);
    }
    myEditor = null;
    super.tearDown();
  }

  protected PsiTestData createData() {
    return new CodeInsightTestData();
  }

  public static final String CARET_MARKER = "<caret>";
  public static final String SELECTION_START_MARKER = "<selection>";
  public static final String SELECTION_END_MARKER = "</selection>";

  protected void configureByFile(String filePath) throws Exception {
    configureByFile(filePath, null);
  }
  protected void configureByFile(String filePath, String projectRoot) throws Exception {
    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", vFile);

    File projectFile = projectRoot == null ? null : new File(getTestDataPath() + projectRoot);

    configureByFile(vFile, projectFile);
  }

  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  protected void configureByFile(final VirtualFile vFile) throws IOException {
    configureByFile(vFile, null);
  }

  protected VirtualFile configureByFiles(final VirtualFile[] vFiles, final File projectRoot) throws IOException {
    myFile = null;
    myEditor = null;
    
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();
    if (clearModelBeforeConfiguring()) {
      rootModel.clear();
    }
    File dir = createTempDirectory();
    myFilesToDelete.add(dir);
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
    final VirtualFile[] newVFiles = new VirtualFile[vFiles.length];
    final RangeMarker[] caretMarkers = new RangeMarker[vFiles.length];
    final RangeMarker[] selStartMarkers = new RangeMarker[vFiles.length];
    final RangeMarker[] selEndMarkers = new RangeMarker[vFiles.length];
    final String[] newFileTexts = new String[vFiles.length];

    for (int i = 0; i < vFiles.length; i++) {
      VirtualFile vFile = vFiles[i];

      assertNotNull(vFile);
      String fileText = new String(vFile.contentsToCharArray());
      fileText = StringUtil.convertLineSeparators(fileText, "\n");
      Document document = EditorFactory.getInstance().createDocument(fileText);

      int caretIndex = fileText.indexOf(CARET_MARKER);
      int selStartIndex = fileText.indexOf(SELECTION_START_MARKER);
      int selEndIndex = fileText.indexOf(SELECTION_END_MARKER);

      final RangeMarker caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
      final RangeMarker selStartMarker = selStartIndex >= 0 ? document.createRangeMarker(selStartIndex, selStartIndex) : null;
      final RangeMarker selEndMarker = selEndIndex >= 0 ? document.createRangeMarker(selEndIndex, selEndIndex) : null;

      if (caretMarker != null) {
        document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
      }
      if (selStartMarker != null) {
        document.deleteString(selStartMarker.getStartOffset(), selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
      }
      if (selEndMarker != null) {
        document.deleteString(selEndMarker.getStartOffset(), selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
      }

      String newFileText = document.getText();

      final VirtualFile newVFile;
      if (projectRoot == null) {
        newVFile = vDir.createChildData(this, vFile.getName());
        Writer writer = newVFile.getWriter(this);
        writer.write(newFileText);
        writer.close();
      }
      else {
        FileUtil.copyDir(projectRoot, dir);
        //vFile.getPath().substring(PathManagerEx.getTestDataPath().length())
        newVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(vDir.getPath() + vFile.getPath().substring(projectRoot.getPath().length()));
      }

      newVFiles[i]=newVFile;
      newFileTexts[i]=newFileText;
      selEndMarkers[i]=selEndMarker;
      selStartMarkers[i]=selStartMarker;
      caretMarkers[i]=caretMarker;
    }

    final ContentEntry contentEntry = rootModel.addContentEntry(vDir);
    if (isAddDirToSource()) contentEntry.addSourceFolder(vDir, false);
    rootModel.commit();

    for (int i = 0; i < newVFiles.length; i++) {
      VirtualFile newVFile = newVFiles[i];

      PsiFile file = myPsiManager.findFile(newVFile);
      if (myFile==null) myFile = file;

      Editor editor = createEditor(newVFile);
      if (myEditor==null) myEditor = editor;

      if (caretMarkers[i] != null) {
        int caretLine = StringUtil.offsetToLineNumber(newFileTexts[i], caretMarkers[i].getStartOffset());
        int caretCol = caretMarkers[i].getStartOffset() - StringUtil.lineColToOffset(newFileTexts[i], caretLine, 0);
        LogicalPosition pos = new LogicalPosition(caretLine, caretCol);
        editor.getCaretModel().moveToLogicalPosition(pos);
      }

      if (selStartMarkers[i] != null) {
        editor.getSelectionModel().setSelection(selStartMarkers[i].getStartOffset(), selEndMarkers[i].getStartOffset());
      }
    }

    return vDir;
  }

  protected File createTempDirectory() throws IOException {
    File dir = FileUtil.createTempDirectory("unitTest", null);
    return dir;
  }

  protected boolean isAddDirToSource() {
    return true;
  }

  protected VirtualFile configureByFile(final VirtualFile vFile, final File projectRoot) throws IOException {
    return configureByFiles(new VirtualFile[] {vFile},projectRoot);
  }

  protected boolean clearModelBeforeConfiguring() {
    return true;
  }

  protected void setupCursorAndSelection(Editor editor) {
    Document document = editor.getDocument();
    final String text = document.getText();

    int caretIndex = text.indexOf(CARET_MARKER);
    int selStartIndex = text.indexOf(SELECTION_START_MARKER);
    int selEndIndex = text.indexOf(SELECTION_END_MARKER);

    final RangeMarker caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
    final RangeMarker selStartMarker = selStartIndex >= 0 ? document.createRangeMarker(selStartIndex, selStartIndex) : null;
    final RangeMarker selEndMarker = selEndIndex >= 0 ? document.createRangeMarker(selEndIndex, selEndIndex) : null;

    if (caretMarker != null) {
      document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
    }
    if (selStartMarker != null) {
      document.deleteString(selStartMarker.getStartOffset(), selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
    }
    if (selEndMarker != null) {
      document.deleteString(selEndMarker.getStartOffset(), selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
    }

    final String newText = document.getText();

    if (caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(newText, caretMarker.getStartOffset());
      int caretCol = caretMarker.getStartOffset() - StringUtil.lineColToOffset(newText, caretLine, 0);
      LogicalPosition pos = new LogicalPosition(caretLine, caretCol);
      editor.getCaretModel().moveToLogicalPosition(pos);
    }

    if (selStartMarker != null) {
      editor.getSelectionModel().setSelection(selStartMarker.getStartOffset(), selEndMarker.getStartOffset());
    }

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  protected void configure(String path, String dataName) throws Exception {
    super.configure(path, dataName);

    myEditor = createEditor(myFile.getVirtualFile());

    com.intellij.codeInsight.CodeInsightTestData data = (com.intellij.codeInsight.CodeInsightTestData) myTestDataBefore;

    int selectionStart;
    int selectionEnd;

    LogicalPosition pos = new LogicalPosition(data.getLineNumber() - 1, data.getColumnNumber() - 1);
    myEditor.getCaretModel().moveToLogicalPosition(pos);

    selectionStart = selectionEnd = myEditor.getCaretModel().getOffset();

    if (data.getSelectionStartColumnNumber() >= 0) {
      selectionStart = myEditor.logicalPositionToOffset(new LogicalPosition(data.getSelectionEndLineNumber() - 1, data.getSelectionStartColumnNumber() - 1));
      selectionEnd = myEditor.logicalPositionToOffset(new LogicalPosition(data.getSelectionEndLineNumber() - 1, data.getSelectionEndColumnNumber() - 1));
    }

    myEditor.getSelectionModel().setSelection(selectionStart, selectionEnd);
  }

  protected void checkResultByFile(String filePath) throws Exception {
    checkResultByFile(filePath, false);
  }

  protected void checkResultByFile(String filePath, boolean stripTrailingSpaces) throws Exception {
    if (stripTrailingSpaces) {
      ((DocumentEx) myEditor.getDocument()).stripTrailingSpaces(false);
    }

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("Cannot find file " + fullPath, vFile);
    String fileText = new String(vFile.contentsToCharArray());
    fileText = StringUtil.convertLineSeparators(fileText, "\n");
    Document document = EditorFactory.getInstance().createDocument(fileText);

    int caretIndex = fileText.indexOf(CARET_MARKER);
    int selStartIndex = fileText.indexOf(SELECTION_START_MARKER);
    int selEndIndex = fileText.indexOf(SELECTION_END_MARKER);

    final RangeMarker caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
    final RangeMarker selStartMarker = selStartIndex >= 0 ? document.createRangeMarker(selStartIndex, selStartIndex) : null;
    final RangeMarker selEndMarker = selEndIndex >= 0 ? document.createRangeMarker(selEndIndex, selEndIndex) : null;

    if (caretMarker != null) {
      document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
    }
    if (selStartMarker != null) {
      document.deleteString(selStartMarker.getStartOffset(), selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
    }
    if (selEndMarker != null) {
      document.deleteString(selEndMarker.getStartOffset(), selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
    }

    String newFileText = document.getText();
    String newFileText1 = newFileText;
    if (stripTrailingSpaces) {
      Document document1 = EditorFactory.getInstance().createDocument(newFileText);
      ((DocumentEx) document1).stripTrailingSpaces(false);
      newFileText1 = document1.getText();
    }

    String text = myFile.getText();
    text = StringUtil.convertLineSeparators(text, "\n");

    assertEquals("Text mismatch in file " + filePath, newFileText1, text);

    if (caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(newFileText, caretMarker.getStartOffset());
      int caretCol = caretMarker.getStartOffset() - StringUtil.lineColToOffset(newFileText, caretLine, 0);

      assertEquals("caretLine", caretLine + 1, myEditor.getCaretModel().getLogicalPosition().line + 1);
      assertEquals("caretColumn", caretCol + 1, myEditor.getCaretModel().getLogicalPosition().column + 1);
    }

    if (selStartMarker != null && selEndMarker != null) {
      int selStartLine = StringUtil.offsetToLineNumber(newFileText, selStartMarker.getStartOffset());
      int selStartCol = selStartMarker.getStartOffset() - StringUtil.lineColToOffset(newFileText, selStartLine, 0);

      int selEndLine = StringUtil.offsetToLineNumber(newFileText, selEndMarker.getEndOffset());
      int selEndCol = selEndMarker.getEndOffset() - StringUtil.lineColToOffset(newFileText, selEndLine, 0);

      assertEquals(
          "selectionStartLine",
          selStartLine + 1,
          StringUtil.offsetToLineNumber(newFileText, myEditor.getSelectionModel().getSelectionStart()) + 1);

      assertEquals(
          "selectionStartCol",
          selStartCol + 1,
          myEditor.getSelectionModel().getSelectionStart() - StringUtil.lineColToOffset(newFileText, selStartLine, 0) + 1);

      assertEquals(
          "selectionEndLine",
          selEndLine + 1,
          StringUtil.offsetToLineNumber(newFileText, myEditor.getSelectionModel().getSelectionEnd()) + 1);

      assertEquals(
          "selectionEndCol",
          selEndCol + 1,
          myEditor.getSelectionModel().getSelectionEnd() - StringUtil.lineColToOffset(newFileText, selEndLine, 0) + 1);
    }
    else {
      assertTrue("has no selection", !myEditor.getSelectionModel().hasSelection());
    }
  }

  protected void checkResult(String dataName) throws Exception {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    super.checkResult(dataName);

    com.intellij.codeInsight.CodeInsightTestData data = (com.intellij.codeInsight.CodeInsightTestData) myTestDataAfter;

    if (data.getColumnNumber() >= 0) {
      assertEquals(dataName + ":caretColumn", data.getColumnNumber(), myEditor.getCaretModel().getLogicalPosition().column + 1);
    }
    if (data.getLineNumber() >= 0) {
      assertEquals(dataName + ":caretLine", data.getLineNumber(), myEditor.getCaretModel().getLogicalPosition().line + 1);
    }

    int selectionStart = myEditor.getSelectionModel().getSelectionStart();
    int selectionEnd = myEditor.getSelectionModel().getSelectionEnd();
    LogicalPosition startPosition = myEditor.offsetToLogicalPosition(selectionStart);
    LogicalPosition endPosition = myEditor.offsetToLogicalPosition(selectionEnd);

    if (data.getSelectionStartColumnNumber() >= 0) {
      assertEquals(dataName + ":selectionStartColumn", data.getSelectionStartColumnNumber(), startPosition.column + 1);
    }
    if (data.getSelectionStartLineNumber() >= 0) {
      assertEquals(dataName + ":selectionStartLine", data.getSelectionStartLineNumber(), startPosition.line + 1);
    }
    if (data.getSelectionEndColumnNumber() >= 0) {
      assertEquals(dataName + ":selectionEndColumn", data.getSelectionEndColumnNumber(), endPosition.column + 1);
    }
    if (data.getSelectionEndLineNumber() >= 0) {
      assertEquals(dataName + ":selectionEndLine", data.getSelectionEndLineNumber(), endPosition.line + 1);
    }
  }

  public Object getData(String dataId) {
    if (dataId.equals(DataConstants.EDITOR)) {
      return myEditor;
    }
    else {
      return super.getData(dataId);
    }
  }

  protected VirtualFile getVirtualFile(String filePath) {
    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", vFile);
    return vFile;
  }
}
