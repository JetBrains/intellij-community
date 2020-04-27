// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.ui.PyUiUtil;

public class PythonUiServiceImpl extends PythonUiService {
  @Override
  public void showBalloonInfo(Project project, String message) {
    PyUiUtil.showBalloon(project, message, MessageType.INFO);
  }

  @Override
  public void showBalloonError(Project project, String message) {
    PyUiUtil.showBalloon(project, message, MessageType.ERROR);
  }

  @Override
  public Editor openTextEditor(PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    return FileEditorManager.getInstance(file.getProject()).openTextEditor(
      new OpenFileDescriptor(file.getProject(), virtualFile), true);
  }

  @Override
  public boolean showYesDialog(Project project, String message, String title) {
    return Messages.showYesNoDialog(message, title, Messages.getQuestionIcon()) == Messages.YES;
  }
}
