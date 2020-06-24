// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalActionProviderBase;
import com.jediterm.terminal.ui.TerminalWidgetListener;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

public final class TerminalSessionEditor extends UserDataHolderBase implements FileEditor {
  private final Project myProject;
  private final TerminalSessionVirtualFileImpl myFile;
  private final TerminalWidgetListener myListener;

  public TerminalSessionEditor(Project project, @NotNull TerminalSessionVirtualFileImpl terminalFile) {
    myProject = project;
    myFile = terminalFile;

    final TabbedSettingsProvider settings = myFile.getSettingsProvider();

    myFile.getTerminalWidget().setNextProvider(new TerminalActionProviderBase() {
      @Override
      public List<TerminalAction> getActions() {
        return Collections.singletonList(
          new TerminalAction(settings.getCloseSessionActionPresentation(), input -> {
            handleCloseSession();
            return true;
          }).withMnemonicKey(KeyEvent.VK_S)
        );
      }
    });

    myListener = widget -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        FileEditorManagerEx.getInstanceEx(myProject).closeFile(myFile);
      }, myProject.getDisposed());
    };
    myFile.getTerminalWidget().addListener(myListener);
  }

  private void handleCloseSession() {
    myFile.getTerminalWidget().close();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myFile.getTerminalWidget();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFile.getTerminalWidget();
  }

  @NotNull
  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public void setState(@NotNull FileEditorState state) {

  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {

  }

  @Override
  public void deselectNotify() {

  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void dispose() {
    myFile.getTerminalWidget().removeListener(myListener);
    Boolean closingToReopen = myFile.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN);
    if (closingToReopen == null || !closingToReopen) {
      myFile.getTerminalWidget().close();
    }
  }
}
