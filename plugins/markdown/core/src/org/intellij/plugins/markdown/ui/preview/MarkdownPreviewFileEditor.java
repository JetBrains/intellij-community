// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings;
import org.intellij.plugins.markdown.settings.MarkdownSettings;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.Objects;

public final class MarkdownPreviewFileEditor extends UserDataHolderBase implements FileEditor {
  private static final long PARSING_CALL_TIMEOUT_MS = 50L;
  private static final long RENDERING_DELAY_MS = 20L;
  public static final Key<WeakReference<MarkdownHtmlPanel>> PREVIEW_BROWSER = Key.create("PREVIEW_BROWSER");

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

  private boolean isDisposed = false;

  public MarkdownPreviewFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
    myDocument = FileDocumentManager.getInstance().getDocument(myFile);

    if (myDocument != null) {
      myDocument.addDocumentListener(new ReparseContentDocumentListener(), this);
    }

    myHtmlPanelWrapper = new JPanel(new BorderLayout());
    myHtmlPanelWrapper.addComponentListener(new AttachPanelOnVisibilityChangeListener());

    attachHtmlPanel();

    final var messageBusConnection = myProject.getMessageBus().connect(this);
    final var settingsChangedListener = new MyUpdatePanelOnSettingsChangedListener();
    messageBusConnection.subscribe(MarkdownSettings.ChangeListener.TOPIC, settingsChangedListener);
    messageBusConnection.subscribe(MarkdownExtensionsSettings.ChangeListener.TOPIC, fromSettingsDialog -> {
      if (!fromSettingsDialog) {
        addImmediateRequest(mySwingAlarm, () -> {
          if (myPanel != null) {
            myPanel.reloadWithOffset(mainEditor.getCaretModel().getOffset());
          }
        });
      }
    });
  }

  private void setupScrollHelper() {
    final var actualEditor = ObjectUtils.tryCast(mainEditor, EditorImpl.class);
    if (actualEditor == null) {
      return;
    }
    final var scrollPane = actualEditor.getScrollPane();
    final var helper = new PreciseVerticalScrollHelper(
      actualEditor,
      () -> ObjectUtils.tryCast(myPanel, MarkdownHtmlPanelEx.class)
    );
    scrollPane.addMouseWheelListener(helper);
    Disposer.register(this, () -> scrollPane.removeMouseWheelListener(helper));
  }

  public void setMainEditor(Editor editor) {
    this.mainEditor = editor;
    if (Registry.is("markdown.experimental.boundary.precise.scroll.enable")) {
      setupScrollHelper();
    }
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
      if (mySwingAlarm.isDisposed()) {
        return;
      }
      if (myLastScrollRequest != null) {
        mySwingAlarm.cancelRequest(myLastScrollRequest);
      }

      myLastScrollRequest = () -> {
        if (myPanel != null) {
          myLastScrollOffset = offset;
          myPanel.scrollToMarkdownSrcOffset(myLastScrollOffset, true);
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
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  public boolean isDisposed() {
    return this.isDisposed;
  }

  @Override
  public void dispose() {
    if (myPanel != null) {
      detachHtmlPanel();
    }
    myLastRenderedHtml = "";
    isDisposed = true;
  }

  @Nullable
  public MarkdownHtmlPanelProvider.ProviderInfo getLastPanelProviderInfo() {
    return myLastPanelProviderInfo;
  }

  private @NotNull MarkdownHtmlPanelProvider retrievePanelProvider(@NotNull MarkdownSettings settings) {
    final var providerInfo = settings.getPreviewPanelProviderInfo();
    var provider = MarkdownHtmlPanelProvider.createFromInfo(providerInfo);
    if (provider.isAvailable() != MarkdownHtmlPanelProvider.AvailabilityInfo.AVAILABLE) {
      final var defaultProvider = MarkdownHtmlPanelProvider.createFromInfo(MarkdownSettings.getDefaultProviderInfo());
      Messages.showMessageDialog(
        myHtmlPanelWrapper,
        MarkdownBundle.message("dialog.message.tried.to.use.preview.panel.provider", providerInfo.getName()),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
      MarkdownSettings.getInstance(myProject).setPreviewPanelProviderInfo(defaultProvider.getProviderInfo());
      provider = Objects.requireNonNull(
        ContainerUtil.find(
          MarkdownHtmlPanelProvider.getProviders(),
          p -> p.isAvailable() == MarkdownHtmlPanelProvider.AvailabilityInfo.AVAILABLE
        )
      );
    }
    myLastPanelProviderInfo = settings.getPreviewPanelProviderInfo();
    return provider;
  }

  // Is always run from pooled thread
  private void updateHtml() {
    if (myPanel == null || myDocument == null || !myFile.isValid() || isDisposed()) {
      return;
    }

    var html = ReadAction.nonBlocking(() -> {
        return MarkdownUtil.INSTANCE.generateMarkdownHtml(myFile, myDocument.getText(), myProject);
      })
      .executeSynchronously();

    // EA-75860: The lines to the top may be processed slowly; Since we're in pooled thread, we can be disposed already.
    if (!myFile.isValid() || isDisposed()) {
      return;
    }

    synchronized (REQUESTS_LOCK) {
      if (mySwingAlarm.isDisposed()) {
        return;
      }
      if (myLastHtmlOrRefreshRequest != null) {
        mySwingAlarm.cancelRequest(myLastHtmlOrRefreshRequest);
      }

      myLastHtmlOrRefreshRequest = () -> {
        if (myPanel == null) return;

        String currentHtml = "<html><head></head>" + html + "</html>";
        if (!currentHtml.equals(myLastRenderedHtml)) {
          myLastRenderedHtml = currentHtml;
          myPanel.setHtml(myLastRenderedHtml, mainEditor.getCaretModel().getOffset(), myFile);
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
    putUserData(PREVIEW_BROWSER, null);
  }

  private void attachHtmlPanel() {
    final var settings = MarkdownSettings.getInstance(myProject);
    myPanel = retrievePanelProvider(settings).createHtmlPanel(myProject, myFile);
    myHtmlPanelWrapper.add(myPanel.getComponent(), BorderLayout.CENTER);
    if (myHtmlPanelWrapper.isShowing()) myHtmlPanelWrapper.validate();
    myHtmlPanelWrapper.repaint();
    myLastRenderedHtml = "";
    putUserData(PREVIEW_BROWSER, new WeakReference<>(myPanel));
    updateHtmlPooled();
  }

  private void updateHtmlPooled() {
    myPooledAlarm.cancelAllRequests();
    myPooledAlarm.addRequest(() -> updateHtml(), 0);
  }

  private void addImmediateRequest(@NotNull Alarm alarm, @NotNull Runnable request) {
    if (!alarm.isDisposed()) {
      alarm.addRequest(request, 0, ModalityState.stateForComponent(getComponent()));
    }
  }

  private class ReparseContentDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
      myPooledAlarm.cancelAllRequests();
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      if (!myPooledAlarm.isDisposed()) {
        myPooledAlarm.addRequest(() -> updateHtml(), PARSING_CALL_TIMEOUT_MS);
      }
    }
  }

  private class AttachPanelOnVisibilityChangeListener extends ComponentAdapter {
    @Override
    public void componentShown(ComponentEvent event) {
      addImmediateRequest(mySwingAlarm, () -> {
        if (myPanel == null) {
          attachHtmlPanel();
        }
      });
    }

    @Override
    public void componentHidden(ComponentEvent event) {
      addImmediateRequest(mySwingAlarm, () -> {
        if (myPanel != null) {
          detachHtmlPanel();
        }
      });
    }
  }

  private class MyUpdatePanelOnSettingsChangedListener implements MarkdownSettings.ChangeListener {
    @Override
    public void beforeSettingsChanged(@NotNull MarkdownSettings settings) { }

    @Override
    public void settingsChanged(@NotNull MarkdownSettings settings) {
      addImmediateRequest(mySwingAlarm, () -> {
        if (settings.getSplitLayout() != TextEditorWithPreview.Layout.SHOW_EDITOR) {
          if (myPanel == null) {
            attachHtmlPanel();
          }
          else if (myLastPanelProviderInfo == null ||
                   MarkdownHtmlPanelProvider.createFromInfo(myLastPanelProviderInfo).equals(retrievePanelProvider(settings))) {
            detachHtmlPanel();
            attachHtmlPanel();
          }
        }
        if (myPanel != null) {
          myPanel.reloadWithOffset(mainEditor.getCaretModel().getOffset());
        }
      });
    }
  }
}
