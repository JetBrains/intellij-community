// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner;
import org.jetbrains.plugins.terminal.ShellStartupOptions;
import org.jetbrains.plugins.terminal.ShellStartupOptionsKt;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;

final class TerminalSessionEditorProvider implements FileEditorProvider, DumbAware {
  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return file instanceof TerminalSessionVirtualFileImpl;
  }

  @Override
  public boolean acceptRequiresReadAction() {
    return false;
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    TerminalSessionVirtualFileImpl terminalFile = (TerminalSessionVirtualFileImpl)file;
    if (file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) != null) {
      return new TerminalSessionEditor(project, terminalFile);
    }
    else {
      TerminalWidget widget = terminalFile.getTerminalWidget();

      String workingDirectory = TerminalWorkingDirectoryManager.getWorkingDirectory(widget);
      Disposable tempDisposable = Disposer.newDisposable();
      ShellStartupOptions options = ShellStartupOptionsKt.shellStartupOptions(workingDirectory);
      TerminalWidget newWidget = new LocalBlockTerminalRunner(project).startShellTerminalWidget(tempDisposable, options, true);
      TerminalSessionVirtualFileImpl newSessionVirtualFile = new TerminalSessionVirtualFileImpl(terminalFile.getName(),
                                                                                                newWidget,
                                                                                                terminalFile.getSettingsProvider());
      TerminalSessionEditor editor = new TerminalSessionEditor(project, newSessionVirtualFile);
      Disposer.dispose(tempDisposable); // newWidget's parent disposable should be changed now
      return editor;
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
