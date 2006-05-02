package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.idea.IdeaTestUtil;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.FilenameFilter;

/**
 * @author dsl
 */
public abstract class MultiFileTestCase extends CodeInsightTestCase {
  public static final MyVirtualFileFilter CVS_FILE_FILTER = new MyVirtualFileFilter();

  protected boolean myDoCompare = true;

  protected void doTest(PerformAction performAction) throws Exception {
    String testName = getTestName(true);
    String root = PathManagerEx.getTestDataPath() + getTestRoot() + testName;

    String rootBefore = root + "/before";
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete, false);
    setupProject(rootDir);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    String rootAfter = root + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));

    performAction.performAction(rootDir, rootDir2);
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    FileDocumentManager.getInstance().saveAllDocuments();

    if (myDoCompare) {
      IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, CVS_FILE_FILTER);
    }
  }

  protected void setupProject(VirtualFile rootDir) {
    PsiTestUtil.addSourceContentToRoots(myModule, rootDir);
  }

  protected abstract String getTestRoot();

  protected interface PerformAction {
    void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception;
  }

  public static class MyVirtualFileFilter implements VirtualFileFilter, FilenameFilter{
    public boolean accept(VirtualFile file) {
      return !file.isDirectory() || !"CVS".equals(file.getName());
    }

    public boolean accept(File dir, String name) {
      return name.indexOf("CVS") == -1;
    }
  }

}
