// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.CommonBundle;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil;
import org.intellij.plugins.markdown.ui.split.SplitFileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;

public class MarkdownPreviewFileEditor extends UserDataHolderBase implements FileEditor {
  private static final long PARSING_CALL_TIMEOUT_MS = 50L;
  private static final long RENDERING_DELAY_MS = 20L;

  private static @Nullable Boolean ourIsDefaultMarkdownPreviewSettings = null;

  private final Project myProject;
  private final VirtualFile myFile;
  private final @Nullable Document myDocument;

  private final JPanel myHtmlPanelWrapper;
  private @Nullable MarkdownHtmlPanel myPanel;
  private @Nullable MarkdownHtmlPanelProvider.ProviderInfo myLastPanelProviderInfo = null;
  private final Alarm myPooledAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  private final Alarm mySwingAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

  private final Object REQUESTS_LOCK = new Object();
  private @Nullable Runnable myLastScrollRequest = null;
  private @Nullable Runnable myLastHtmlOrRefreshRequest = null;

  private volatile int myLastScrollOffset;
  private @NotNull String myLastRenderedHtml = "";

  private Editor mainEditor;

  public MarkdownPreviewFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
    myDocument = FileDocumentManager.getInstance().getDocument(myFile);

    if (myDocument != null) {
      myDocument.addDocumentListener(new DocumentListener() {

        @Override
        public void beforeDocumentChange(@NotNull DocumentEvent e) {
          myPooledAlarm.cancelAllRequests();
        }

        @Override
        public void documentChanged(@NotNull DocumentEvent e) {
          myPooledAlarm.addRequest(() -> updateHtml(), PARSING_CALL_TIMEOUT_MS);
        }
      }, this);
    }

    myHtmlPanelWrapper = new JPanel(new BorderLayout());

    myHtmlPanelWrapper.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        mySwingAlarm.addRequest(() -> {
          if (myPanel == null) {
            attachHtmlPanel();
          }
        }, 0, ModalityState.stateForComponent(getComponent()));
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        mySwingAlarm.addRequest(() -> {
          if (myPanel != null) {
            detachHtmlPanel();
          }
        }, 0, ModalityState.stateForComponent(getComponent()));
      }
    });

    if (isPreviewShown(project, file)) {
      attachHtmlPanel();
    }

    MessageBusConnection settingsConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
    MarkdownApplicationSettings.SettingsChangedListener settingsChangedListener = new MyUpdatePanelOnSettingsChangedListener();
    settingsConnection.subscribe(MarkdownApplicationSettings.SettingsChangedListener.TOPIC, settingsChangedListener);
    settingsConnection.subscribe(MarkdownApplicationSettings.FontChangedListener.TOPIC, createFontChangedListener());
  }

  @NotNull
  private MarkdownApplicationSettings.FontChangedListener createFontChangedListener() {
    return new MarkdownApplicationSettings.FontChangedListener() {
      @Override
      public void fontChanged() {
        if (myPanel != null && mainEditor != null) {
          myPanel.reloadWithOffset(mainEditor.getCaretModel().getOffset());
        }
      }
    };
  }

  public void setMainEditor(Editor editor) {
    this.mainEditor = editor;
  }

  public void scrollToSrcOffset(final int offset) {
    if (myPanel == null) return;

    // Do not scroll if html update request is online
    // This will restrain preview from glitches on editing
    if (!myPooledAlarm.isEmpty()) {
      myLastScrollOffset = offset;
      return;
    }

    synchronized (REQUESTS_LOCK) {
      if (myLastScrollRequest != null) {
        mySwingAlarm.cancelRequest(myLastScrollRequest);
      }

      myLastScrollRequest = () -> {
        if (myPanel != null) {
          myLastScrollOffset = offset;
          myPanel.scrollToMarkdownSrcOffset(myLastScrollOffset);
          synchronized (REQUESTS_LOCK) {
            myLastScrollRequest = null;
          }
        }
      };

      mySwingAlarm.addRequest(myLastScrollRequest, RENDERING_DELAY_MS, ModalityState.stateForComponent(getComponent()));
    }
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myHtmlPanelWrapper;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myPanel != null ? myPanel.getComponent() : null;
  }

  @Override
  public @NotNull String getName() {
    return MarkdownBundle.message("markdown.editor.preview.name");
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
  public void selectNotify() {
    if (myPanel != null) {
      updateHtmlPooled();
    }
  }

  @Override
  public void deselectNotify() { }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }

  @Override
  public @Nullable BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Override
  public @Nullable FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void dispose() {
    if (myPanel != null) {
      Disposer.dispose(myPanel);
    }
  }

  private @NotNull MarkdownHtmlPanelProvider retrievePanelProvider(@NotNull MarkdownApplicationSettings settings) {
    final MarkdownHtmlPanelProvider.ProviderInfo providerInfo = settings.getMarkdownPreviewSettings().getHtmlPanelProviderInfo();

    MarkdownHtmlPanelProvider provider = MarkdownHtmlPanelProvider.createFromInfo(providerInfo);

    if (provider.isAvailable() != MarkdownHtmlPanelProvider.AvailabilityInfo.AVAILABLE) {
      if (ourIsDefaultMarkdownPreviewSettings == null) {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourIsDefaultMarkdownPreviewSettings = settings.getMarkdownPreviewSettings() == MarkdownPreviewSettings.DEFAULT;
      }
      settings.setMarkdownPreviewSettings(new MarkdownPreviewSettings(settings.getMarkdownPreviewSettings().getSplitEditorLayout(),
                                                                      MarkdownPreviewSettings.DEFAULT.getHtmlPanelProviderInfo(),
                                                                      settings.getMarkdownPreviewSettings().isAutoScrollPreview(),
                                                                      settings.getMarkdownPreviewSettings().isVerticalSplit()));

      if (!ourIsDefaultMarkdownPreviewSettings) {
        Messages.showMessageDialog(
          myHtmlPanelWrapper,
          MarkdownBundle.message("dialog.message.tried.to.use.preview.panel.provider", providerInfo.getName()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
      }

      provider = Objects.requireNonNull(
        ContainerUtil.find(
          MarkdownHtmlPanelProvider.getProviders(),
          p -> p.isAvailable() == MarkdownHtmlPanelProvider.AvailabilityInfo.AVAILABLE
        )
      );
    }

    myLastPanelProviderInfo = settings.getMarkdownPreviewSettings().getHtmlPanelProviderInfo();
    return provider;
  }

  // Is always run from pooled thread
  private void updateHtml() {
    if (myPanel == null || myDocument == null || !myFile.isValid() || Disposer.isDisposed(this)) {
      return;
    }

    String html = MarkdownUtil.INSTANCE.generateMarkdownHtml(myFile, myDocument.getText(), myProject);

    // EA-75860: The lines to the top may be processed slowly; Since we're in pooled thread, we can be disposed already.
    if (!myFile.isValid() || Disposer.isDisposed(this)) {
      return;
    }

    synchronized (REQUESTS_LOCK) {
      if (myLastHtmlOrRefreshRequest != null) {
        mySwingAlarm.cancelRequest(myLastHtmlOrRefreshRequest);
      }

      myLastHtmlOrRefreshRequest = () -> {
        if (myPanel == null) return;

        String currentHtml = "<html><head></head>" + html + "</html>";
        if (!currentHtml.equals(myLastRenderedHtml)) {
          myLastRenderedHtml = currentHtml;
          myPanel.setHtml(myLastRenderedHtml, mainEditor.getCaretModel().getOffset());
        }

        synchronized (REQUESTS_LOCK) {
          myLastHtmlOrRefreshRequest = null;
        }
      };

      mySwingAlarm.addRequest(myLastHtmlOrRefreshRequest, RENDERING_DELAY_MS, ModalityState.stateForComponent(getComponent()));
    }
  }

  private void detachHtmlPanel() {
    if (myPanel != null) {
      myHtmlPanelWrapper.remove(myPanel.getComponent());
      Disposer.dispose(myPanel);
      myPanel = null;
    }
  }

  private void attachHtmlPanel() {
    MarkdownApplicationSettings settings = MarkdownApplicationSettings.getInstance();
    myPanel = retrievePanelProvider(settings).createHtmlPanel();
    myHtmlPanelWrapper.add(myPanel.getComponent(), BorderLayout.CENTER);
    myHtmlPanelWrapper.repaint();
    myLastRenderedHtml = "";
    updateHtmlPooled();
  }

  private void updateHtmlPooled() {
    myPooledAlarm.cancelAllRequests();
    myPooledAlarm.addRequest(() -> updateHtml(), 0);
  }

  private static boolean isPreviewShown(@NotNull Project project, @NotNull VirtualFile file) {
    MarkdownSplitEditorProvider provider = FileEditorProvider.EP_FILE_EDITOR_PROVIDER.findExtension(MarkdownSplitEditorProvider.class);
    if (provider == null) {
      return true;
    }

    FileEditorState state = EditorHistoryManager.getInstance(project).getState(file, provider);
    if (!(state instanceof SplitFileEditor.MyFileEditorState)) {
      return true;
    }

    return SplitFileEditor.SplitEditorLayout.valueOf(((SplitFileEditor.MyFileEditorState)state).getSplitLayout()) !=
           SplitFileEditor.SplitEditorLayout.FIRST;
  }

  private class MyUpdatePanelOnSettingsChangedListener implements MarkdownApplicationSettings.SettingsChangedListener {
    @Override
    public void settingsChanged(@NotNull MarkdownApplicationSettings settings) {
      mySwingAlarm.addRequest(() -> {
        if (settings.getMarkdownPreviewSettings().getSplitEditorLayout() != SplitFileEditor.SplitEditorLayout.FIRST) {
          if (myPanel == null) {
            attachHtmlPanel();
          }
          else if (myLastPanelProviderInfo == null ||
                   MarkdownHtmlPanelProvider.createFromInfo(myLastPanelProviderInfo).equals(retrievePanelProvider(settings))) {
            detachHtmlPanel();
            attachHtmlPanel();
          }

          myPanel.reloadWithOffset(mainEditor.getCaretModel().getOffset());
        }
      }, 0, ModalityState.stateForComponent(getComponent()));
    }
  }
}
