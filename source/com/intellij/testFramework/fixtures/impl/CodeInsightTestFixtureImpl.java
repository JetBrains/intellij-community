/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.codeInsight.completion.CodeCompletionHandler;
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.PostHighlightingPass;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class CodeInsightTestFixtureImpl implements CodeInsightTestFixture {

  private PsiManagerImpl myPsiManager;
  private PsiFile myFile;
  private Editor myEditor;
  private String myTestDataPath;

  protected final TempDirTestFixture myTempDirFixture = new TempDirTextFixtureImpl() {

    protected File createTempDirectory() {
      return super.createTempDirectory();
/*
      final File file = new File(myTestDataPath + "/temp");
        file.mkdir();
      return file;
*/
    }
  };
  private final IdeaProjectTestFixture myProjectFixture;

  public CodeInsightTestFixtureImpl(IdeaProjectTestFixture projectFixture) {
    myProjectFixture = projectFixture;
  }

  public void setTestDataPath(String dataPath) {
    myTestDataPath = dataPath;
  }

  public String getTempDirPath() {
    return myTempDirFixture.getTempDirPath();
  }

  public void testHighlighting(final boolean checkWarnings,
                               final boolean checkInfos,
                               final boolean checkWeakWarnings,
                               final String... filePaths) {

    new WriteCommandAction(myProjectFixture.getProject()) {

      protected void run(final Result result) throws Throwable {
        configureByFiles(filePaths);
        collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings);
      }
    }.execute();
  }

  public void testHighlighting(final String... filePaths) {
    testHighlighting(true, true, true, filePaths);
  }

  public void testCompletion(String fileBefore, String fileAfter) {
    configureByFile(fileBefore);
    new CodeCompletionHandler().invoke(getProject(), myEditor, myFile);
    checkResultByFile(fileAfter, false);
  }

  public void setUp() throws Exception {
    myProjectFixture.setUp();
    myPsiManager = (PsiManagerImpl)PsiManager.getInstance(getProject());
  }

  public void tearDown() throws Exception {
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      editorManager.closeFile(openFile);
    }
    myEditor = null;
    myTempDirFixture.tearDown();
    myProjectFixture.tearDown();
  }

  protected void configureByFiles(@NonNls String... filePaths) {
    myFile = null;
    myEditor = null;
    for (String filePath : filePaths) {
      configureByFile(filePath);
    }
  }

  protected void configureByFile(@NonNls String filePath) {
    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    TestCase.assertNotNull("file " + fullPath + " not found", vFile);
    configureByFile(vFile);
  }

  protected void configureByFile(VirtualFile file) {
    VirtualFile copy = myTempDirFixture.copyFile(file);
    if (myFile == null) myFile = myPsiManager.findFile(copy);
    if (myEditor == null) myEditor = createEditor(copy);
  }

  @Nullable
  protected Editor createEditor(VirtualFile file) {
    final Project project = getProject();
    final FileEditorManager instance = FileEditorManager.getInstance(project);
    if (file.getFileType() != null && file.getFileType().isBinary()) {
      return null;
    }
    return instance.openTextEditor(new OpenFileDescriptor(project, file, 0), false);
  }

  protected Collection<HighlightInfo> collectAndCheckHighlightings(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) {
    final Project project = getProject();
    ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos);

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ((PsiFileImpl)myFile).calcTreeElement(); //to load text

    //to initialize caches
    myPsiManager.getCacheManager().getFilesWithWord("XXX", UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(project), true);
    VirtualFileFilter javaFilesFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS;
      }
    };
    myPsiManager.setAssertOnFileLoadingFilter(javaFilesFilter); // check repository work

    Collection<HighlightInfo> infos = doHighlighting();

    myPsiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    data.checkResult(infos, myEditor.getDocument().getText());

    return infos;
  }

  protected Collection<HighlightInfo> doHighlighting() {
    final Project project = myProjectFixture.getProject();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    Document document = myEditor.getDocument();
    GeneralHighlightingPass action1 = new GeneralHighlightingPass(project, myFile, document, 0, myFile.getTextLength(), false, true);
    action1.doCollectInformation(new MockProgressIndicator());
    Collection<HighlightInfo> highlights1 = action1.getHighlights();

    PostHighlightingPass action2 = new PostHighlightingPass(project, myFile, myEditor, 0, myFile.getTextLength(), false);
    action2.doCollectInformation(new MockProgressIndicator());
    Collection<HighlightInfo> highlights2 = action2.getHighlights();

/*
    Collection<HighlightInfo> highlights3 = null;
    if (myAvailableTools.size() > 0) {
      LocalInspectionsPass inspectionsPass = new LocalInspectionsPass(myProject, myFile, myEditor.getDocument(), 0, myFile.getTextLength());
      inspectionsPass.doCollectInformation(new MockProgressIndicator());
      highlights3 = inspectionsPass.getHighlights();
    }
*/
    ArrayList<HighlightInfo> list = new ArrayList<HighlightInfo>();
    for (HighlightInfo info : highlights1) {
      list.add(info);
    }

    for (HighlightInfo info : highlights2) {
      list.add(info);
    }
/*
    if (highlights3 != null) {
      for (HighlightInfo info : highlights3) {
        list.add(info);
      }
    }
*/
    return list;
  }

  public String getTestDataPath() {
    return myTestDataPath;
  }

  protected Project getProject() {
    return myProjectFixture.getProject();
  }

  protected void checkResultByFile(@NonNls String filePath, boolean stripTrailingSpaces) {

    Project project = myProjectFixture.getProject();

    project.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    if (stripTrailingSpaces) {
      ((DocumentEx)myEditor.getDocument()).stripTrailingSpaces(false);
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    TestCase.assertNotNull("Cannot find file " + fullPath, vFile);
    String fileText = null;
    try {
      fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
    }
    catch (IOException e) {
      TestCase.fail("Cannot load " + vFile);
    }
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
      ((DocumentEx)document1).stripTrailingSpaces(false);
      newFileText1 = document1.getText();
    }

    String text = myFile.getText();
    text = StringUtil.convertLineSeparators(text, "\n");

    TestCase.assertEquals("Text mismatch in file " + filePath, newFileText1, text);

    if (caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(newFileText, caretMarker.getStartOffset());
      int caretCol = caretMarker.getStartOffset() - StringUtil.lineColToOffset(newFileText, caretLine, 0);

      TestCase.assertEquals("caretLine", caretLine + 1, myEditor.getCaretModel().getLogicalPosition().line + 1);
      TestCase.assertEquals("caretColumn", caretCol + 1, myEditor.getCaretModel().getLogicalPosition().column + 1);
    }

    if (selStartMarker != null && selEndMarker != null) {
      int selStartLine = StringUtil.offsetToLineNumber(newFileText, selStartMarker.getStartOffset());
      int selStartCol = selStartMarker.getStartOffset() - StringUtil.lineColToOffset(newFileText, selStartLine, 0);

      int selEndLine = StringUtil.offsetToLineNumber(newFileText, selEndMarker.getEndOffset());
      int selEndCol = selEndMarker.getEndOffset() - StringUtil.lineColToOffset(newFileText, selEndLine, 0);

      TestCase.assertEquals("selectionStartLine", selStartLine + 1,
                            StringUtil.offsetToLineNumber(newFileText, myEditor.getSelectionModel().getSelectionStart()) + 1);

      TestCase.assertEquals("selectionStartCol", selStartCol + 1, myEditor.getSelectionModel().getSelectionStart() -
                                                                  StringUtil.lineColToOffset(newFileText, selStartLine, 0) + 1);

      TestCase.assertEquals("selectionEndLine", selEndLine + 1,
                            StringUtil.offsetToLineNumber(newFileText, myEditor.getSelectionModel().getSelectionEnd()) + 1);

      TestCase.assertEquals("selectionEndCol", selEndCol + 1,
                            myEditor.getSelectionModel().getSelectionEnd() - StringUtil.lineColToOffset(newFileText, selEndLine, 0) + 1);
    }
    else {
      TestCase.assertTrue("has no selection", !myEditor.getSelectionModel().hasSelection());
    }
  }

}
