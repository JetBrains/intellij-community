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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;

import java.io.IOException;

public class MergeFilesAction extends AnAction{
  public void update(AnActionEvent e) {
    DataContext context = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(context);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    VirtualFile[] files = DataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    if (files == null || files.length != 3){
      e.getPresentation().setEnabled(false);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext context = e.getDataContext();
    VirtualFile[] files = DataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    if (files == null || files.length != 3){
      return;
    }

    DiffRequestFactory diffRequestFactory = PeerFactory.getInstance().getDiffRequestFactory();

    VirtualFile file = files[1];
    try {
      String originalText = createValidContent(VfsUtil.loadText(file));
      String leftText = VfsUtil.loadText(files[0]);
      String rightText = VfsUtil.loadText(files[2]);

      Project project = DataKeys.PROJECT.getData(context);
      final MergeRequest diffData = diffRequestFactory.createMergeRequest(leftText, rightText, originalText, file, project,
                                                                          ActionButtonPresentation.createApplyButton());
      diffData.setVersionTitles(new String[]{files[0].getPresentableUrl(),
                                             files[1].getPresentableUrl(),
                                             files[2].getPresentableUrl()});
      diffData.setWindowTitle(DiffBundle.message("merge.files.dialog.title"));
      diffData.setHelpId("cvs.merge");
      DiffManager.getInstance().getDiffTool().show(diffData);
    }
    catch (IOException e1) {
      Messages.showErrorDialog(DiffBundle.message("merge.dialog.cannot.load.file.error.message", e1.getLocalizedMessage()),
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
