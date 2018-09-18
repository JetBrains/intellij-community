/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.JBTabInnerTerminalWidget;

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
      JBTabInnerTerminalWidget widget =
        terminalFile.getTerminalWidget().getCreateNewSessionAction().apply(null);


      widget.getTabbedWidget().removeTab(widget);


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
