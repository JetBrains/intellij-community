package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.PostHighlightingPass;
import com.intellij.mock.MockProgressInidicator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiDocumentManager;

import java.util.ArrayList;

public abstract class DaemonAnalyzerTestCase extends CodeInsightTestCase {
  protected void doTest(String filePath, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFile(filePath);
    doDoTest(checkWarnings, checkInfos);
  }
  protected void doTest(String filePath, String projectRoot, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFile(filePath, projectRoot);
    doDoTest(checkWarnings, checkInfos);
  }

  protected void doTest(VirtualFile vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(new VirtualFile[] { vFile }, checkWarnings, checkInfos );
  }

  protected void doTest(VirtualFile[] vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFiles(vFile,null);
    doDoTest(checkWarnings, checkInfos);
  }

  protected void doDoTest(boolean checkWarnings, boolean checkInfos) {
    ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(),checkWarnings, checkInfos);

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    myFile.getText(); //to load text
    VirtualFileFilter javaFilesFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS;
      }
    };
    myPsiManager.setAssertOnFileLoadingFilter(javaFilesFilter); // check repository work

    HighlightInfo[] infos = doHighlighting();

    myPsiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    data.checkResult(infos, myEditor.getDocument().getText());
  }

  protected HighlightInfo[] doHighlighting() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    Document document = myEditor.getDocument();
    GeneralHighlightingPass action1 = new GeneralHighlightingPass(
      myProject, myFile, document, 0, myFile.getTextLength(), false, true);
    action1.doCollectInformation(new MockProgressInidicator());
    HighlightInfo[] highlights1 = action1.getHighlights();

    PostHighlightingPass action2 = new PostHighlightingPass(myProject, myFile, myEditor, 0, myFile.getTextLength(), false);
    action2.doCollectInformation(new MockProgressInidicator());
    HighlightInfo[] highlights2 = action2.getHighlights();

    ArrayList<HighlightInfo> list = new ArrayList<HighlightInfo>();
    for (int i = 0; i < highlights1.length; i++) {
      list.add(highlights1[i]);
    }
    for (int i = 0; i < highlights2.length; i++) {
      list.add(highlights2[i]);
    }
    return list.toArray(new HighlightInfo[list.size()]);
  }

}