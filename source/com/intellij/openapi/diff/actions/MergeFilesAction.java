/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.peer.PeerFactory;

public class MergeFilesAction extends AnAction{
  public void update(AnActionEvent e) {
    DataContext context = e.getDataContext();
    Project project = (Project)context.getData(DataConstants.PROJECT);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    VirtualFile[] files = (VirtualFile[])context.getData(DataConstants.VIRTUAL_FILE_ARRAY);
    if (files == null || files.length != 3){
      e.getPresentation().setEnabled(false);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext context = e.getDataContext();
    VirtualFile[] files = (VirtualFile[])context.getData(DataConstants.VIRTUAL_FILE_ARRAY);
    if (files == null || files.length != 3){
      return;
    }

    DiffRequestFactory diffRequestFactory = PeerFactory.getInstance().getDiffRequestFactory();

    VirtualFile file = files[1];
    if(FileDocumentManager.getInstance().getDocument(files[0]) != null && FileDocumentManager.getInstance().getDocument(files[1]) != null && FileDocumentManager.getInstance().getDocument(files[2]) != null){
      String originalText = createValidContent(FileDocumentManager.getInstance().getDocument(file).getText());
      String leftText = FileDocumentManager.getInstance().getDocument(files[0]).getText();
      String rightText = FileDocumentManager.getInstance().getDocument(files[2]).getText();

      Project project = (Project)context.getData(DataConstants.PROJECT);
      final MergeRequest diffData = diffRequestFactory.createMergeRequest(leftText, rightText, originalText, file, project,
                                                                          ActionButtonPresentation.createApplyButton());
      diffData.setVersionTitles(new String[]{files[0].getPresentableUrl(),
        files[1].getPresentableUrl(),
        files[2].getPresentableUrl()});
      diffData.setWindowTitle(DiffBundle.message("merge.files.dialog.title"));
      diffData.setHelpId("cvs.merge");
      DiffManager.getInstance().getDiffTool().show(diffData);
    }
    else {
      Messages.showErrorDialog(DiffBundle.message("merge.dialog.cannot.load.file.error.message", ""),
                               DiffBundle.message("merge.files.dialog.title"));
    }
  }

  private String createValidContent(String str) {
    String[] strings = LineTokenizer.tokenize(str.toCharArray(), false, false);
    StringBuffer result = new StringBuffer();
    for (int i = 0; i < strings.length; i++) {
      String string = strings[i];
      if (i != 0) result.append('\n');
      result.append(string);
    }
    return result.toString();
  }

}
