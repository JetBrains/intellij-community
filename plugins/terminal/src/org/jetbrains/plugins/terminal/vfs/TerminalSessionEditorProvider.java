// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class TerminalSessionEditorProvider implements FileEditorProvider, DumbAware {
  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return file instanceof TerminalSessionVirtualFileImpl;
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) != null) {
      return new TerminalSessionEditor(project, (TerminalSessionVirtualFileImpl)file);
    }
    else {
      TerminalSessionVirtualFileImpl terminalFile = (TerminalSessionVirtualFileImpl)file;
      JBTerminalWidget widget = terminalFile.getTerminalWidget();

      TabInfo tabInfo = new TabInfo(widget).setText(terminalFile.getName());
      TerminalSessionVirtualFileImpl newSessionVirtualFile =
        new TerminalSessionVirtualFileImpl(tabInfo, widget, terminalFile.getSettingsProvider());
      tabInfo
        .setObject(newSessionVirtualFile);

      return new TerminalSessionEditor(project, newSessionVirtualFile);
    }
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return "terminal-session-editor";
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
