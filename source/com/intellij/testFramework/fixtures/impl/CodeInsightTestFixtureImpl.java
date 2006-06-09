/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.mock.MockProgressIndicator;

import java.util.Collection;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

/**
 * @author Dmitry Avdeev
 */
public class CodeInsightTestFixtureImpl extends HeavyIdeaTestFixtureImpl implements CodeInsightTestFixture {

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

  public void setTestDataPath(String dataPath) {
    myTestDataPath = dataPath;
  }

  public String getTempDirPath() {
    return myTempDirFixture.getTempDirPath();
  }

  public void test(String filePath, boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) {
    configureByFile(filePath);
    collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings);
  }

  public void test(final String filePath) {
    new WriteCommandAction(getProject()) {

      protected void run(final Result result) throws Throwable {
        test(filePath, true, true, true);

      }
    }.execute();
  }

  public void setUp() throws Exception {
    super.setUp();
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
    super.tearDown();
  }

  protected void configureByFile(String filePath) {
    String fullPath = getTestDataPath() + filePath;

    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    TestCase.assertNotNull("file " + fullPath + " not found", vFile);
    configureByFile(vFile);
  }

  protected void configureByFile(VirtualFile file) {
    VirtualFile copy = myTempDirFixture.copyFile(file);
    myFile = myPsiManager.findFile(copy);
    myEditor = createEditor(copy);
  }

  protected Editor createEditor(VirtualFile file) {
    final FileEditorManager instance = FileEditorManager.getInstance(getProject());
    if (file.getFileType() != null && file.getFileType().isBinary()) {
      return null;
    }
    return instance.openTextEditor(new OpenFileDescriptor(getProject(), file, 0), false);
  }

  protected Collection<HighlightInfo> collectAndCheckHighlightings(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) {
    ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(),checkWarnings, checkWeakWarnings, checkInfos);

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    ((PsiFileImpl)myFile).calcTreeElement(); //to load text

    //to initialize caches
    myPsiManager.getCacheManager().getFilesWithWord("XXX", UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(getProject()), true);
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
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    Document document = myEditor.getDocument();
    GeneralHighlightingPass action1 = new GeneralHighlightingPass(getProject(), myFile, document, 0, myFile.getTextLength(), false, true);
    action1.doCollectInformation(new MockProgressIndicator());
    Collection<HighlightInfo> highlights1 = action1.getHighlights();

    PostHighlightingPass action2 = new PostHighlightingPass(getProject(), myFile, myEditor, 0, myFile.getTextLength(), false);
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
}
