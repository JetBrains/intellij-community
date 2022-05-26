// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.JBTerminalWidget;
import com.jediterm.terminal.ui.TerminalWidgetListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public final class TerminalSessionEditor extends UserDataHolderBase implements FileEditor {
  private static final Logger LOG = Logger.getInstance(TerminalSessionEditor.class);

  private final Project myProject;
  private final TerminalSessionVirtualFileImpl myFile;
  private final TerminalWidgetListener myListener;
  private final Disposable myWidgetParentDisposable = Disposer.newDisposable("terminal widget parent");

  public TerminalSessionEditor(Project project, @NotNull TerminalSessionVirtualFileImpl terminalFile) {
    myProject = project;
    myFile = terminalFile;
    terminalFile.getTerminalWidget().moveDisposable(myWidgetParentDisposable);

    myListener = widget -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        FileEditorManagerEx.getInstanceEx(myProject).closeFile(myFile);
      }, myProject.getDisposed());
    };
    myFile.getTerminalWidget().addListener(myListener);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myFile.getTerminalWidget();
  }

  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    return myFile.getTerminalWidget();
  }

  @Override
  public @NotNull String getName() {
    return myFile.getName();
  }

  @Override
  public void setState(@NotNull FileEditorState state) { }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }

  @Override
  public @Nullable FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @Override
  public void dispose() {
    myFile.getTerminalWidget().removeListener(myListener);
    if (Boolean.TRUE.equals(myFile.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN))) {
      ApplicationManager.getApplication().invokeLater(() -> {
        boolean disposedBefore = Disposer.isDisposed(myFile.getTerminalWidget());
        Disposer.dispose(myWidgetParentDisposable);
        boolean disposedAfter = Disposer.isDisposed(myFile.getTerminalWidget());
        if (disposedBefore != disposedAfter) {
          LOG.error(JBTerminalWidget.class.getSimpleName() + " parent disposable hasn't been changed " +
                    "(disposed before: " + disposedBefore + ", disposed after: " + disposedAfter + ")");
        }
      });
    }
    else {
      Disposer.dispose(myWidgetParentDisposable);
    }
  }
}
