// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public class RestPreviewFileEditor extends UserDataHolderBase implements FileEditor {
  private final static long PARSING_CALL_TIMEOUT_MS = 50L;
  private final static String NO_PREVIEW = "<h2>No preview available.</h2><br/><br/>";

  private final static long RENDERING_DELAY_MS = 20L;

  @NotNull
  private final RestPreviewPanel myPanel;
  @NotNull
  private final VirtualFile myFile;
  private final Project myProject;
  @Nullable
  private final Document myDocument;
  @NotNull
  private final Alarm myPooledAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  @NotNull
  private final Alarm mySwingAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

  private final Object REQUESTS_LOCK = new Object();
  @Nullable
  private Runnable myLastRequest = null;

  @NotNull
  private String myLastRenderedHtml = "";

  public RestPreviewFileEditor(@NotNull VirtualFile file, Project project) {
    myFile = file;
    myProject = project;
    myDocument = FileDocumentManager.getInstance().getDocument(myFile);
    myPanel = RestSettings.getInstance().getCurrentPanel().equals(RestConfigurable.SWING) ?
              new RestSwingHtmlPanel() :
              new RestJavaFxHtmlPanel();

    if (myDocument != null) {
      myDocument.addDocumentListener(new DocumentListener() {

        @Override
        public void beforeDocumentChange(DocumentEvent e) {
          myPooledAlarm.cancelAllRequests();
        }

        @Override
        public void documentChanged(final DocumentEvent e) {
          myPooledAlarm.addRequest(() -> updateHtml(), PARSING_CALL_TIMEOUT_MS);
        }
      }, this);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return "ReStructuredText HTML Preview";
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
    myPooledAlarm.cancelAllRequests();
    myPooledAlarm.addRequest(() -> updateHtml(), 0);
  }

  /**
   * Is always run from pooled thread
   */
  private void updateHtml() {
    if (!myFile.isValid() || myDocument == null || Disposer.isDisposed(this)) {
      return;
    }

    final Pair<String, String> htmlAndError = RestPreviewProvider.getProviders()[0].toHtml(myDocument.getText(), myFile, myProject);
    if (htmlAndError == null) return;

    String html = htmlAndError.getFirst();
    if (html.isEmpty()) {
      html = NO_PREVIEW + htmlAndError.getSecond();
    }

    // EA-75860: The lines to the top may be processed slowly; Since we're in pooled thread, we can be disposed already.
    if (!myFile.isValid() || Disposer.isDisposed(this)) {
      return;
    }

    synchronized (REQUESTS_LOCK) {
      if (myLastRequest != null) {
        mySwingAlarm.cancelRequest(myLastRequest);
      }
      String finalHtml = html;
      myLastRequest = () -> {
        if (!finalHtml.equals(myLastRenderedHtml)) {
          myLastRenderedHtml = finalHtml;
          myPanel.setHtml(myLastRenderedHtml);
        }

        myPanel.render();
        synchronized (REQUESTS_LOCK) {
          myLastRequest = null;
        }
      };
      mySwingAlarm.addRequest(myLastRequest, RENDERING_DELAY_MS, ModalityState.stateForComponent(getComponent()));
    }
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
    Disposer.dispose(myPanel);
  }
}