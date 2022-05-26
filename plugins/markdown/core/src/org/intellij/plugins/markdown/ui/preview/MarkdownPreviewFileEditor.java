// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings;
import org.intellij.plugins.markdown.settings.MarkdownSettings;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil;
import org.intellij.plugins.markdown.ui.split.SplitFileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import java.util.function.Supplier;

public final class MarkdownPreviewFileEditor extends UserDataHolderBase implements FileEditor {
  private static final long PARSING_CALL_TIMEOUT_MS = 50L;
  private static final long RENDERING_DELAY_MS = 20L;
  public static final Key<MarkdownHtmlPanel> PREVIEW_BROWSER = Key.create("PREVIEW_BROWSER");

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

    final var messageBusConnection = myProject.getMessageBus().connect(this);
    final var settingsChangedListener = new MyUpdatePanelOnSettingsChangedListener();
    messageBusConnection.subscribe(MarkdownSettings.ChangeListener.TOPIC, settingsChangedListener);
    messageBusConnection.subscribe(MarkdownExtensionsSettings.ChangeListener.TOPIC, fromSettingsDialog -> {
      if (!fromSettingsDialog) {
        mySwingAlarm.addRequest(() -> {
          if (myPanel != null) {
            myPanel.reloadWithOffset(mainEditor.getCaretModel().getOffset());
          }
        }, 0, ModalityState.stateForComponent(getComponent()));
      }
    });
  }

  private void setupScrollHelper() {
    final var actualEditor = (mainEditor instanceof EditorImpl)? (EditorImpl)mainEditor : null;
    if (actualEditor == null) {
      return;
    }
    final var scrollPane = actualEditor.getScrollPane();
    final var helper = new PreciseVerticalScrollHelper(
      actualEditor,
      () -> (myPanel instanceof MarkdownHtmlPanelEx)? (MarkdownHtmlPanelEx)myPanel : null
    );
    scrollPane.addMouseWheelListener(helper);
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
  public @Nullable FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @Override
  public void dispose() {
    if (myPanel != null) {
      Disposer.dispose(myPanel);
    }
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
          final var fileSystem = myFile.getFileSystem();
          myPanel.setHtml(myLastRenderedHtml, mainEditor.getCaretModel().getOffset(), fileSystem.getNioPath(myFile));
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
      this.putUserData(PREVIEW_BROWSER, null);
    }
  }

  private void attachHtmlPanel() {
    final var settings = MarkdownSettings.getInstance(myProject);
    myPanel = retrievePanelProvider(settings).createHtmlPanel(myProject, myFile);
    myHtmlPanelWrapper.add(myPanel.getComponent(), BorderLayout.CENTER);
    if (myHtmlPanelWrapper.isShowing()) myHtmlPanelWrapper.validate();
    myHtmlPanelWrapper.repaint();
    myLastRenderedHtml = "";
    this.putUserData(PREVIEW_BROWSER, myPanel);
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

    String layout = ((SplitFileEditor.MyFileEditorState)state).getSplitLayout();
    return layout == null || !layout.equals("FIRST"); //todo[kb] remove after migration to the new state model
  }

  private class MyUpdatePanelOnSettingsChangedListener implements MarkdownSettings.ChangeListener {
    @Override
    public void beforeSettingsChanged(@NotNull MarkdownSettings settings) {}

    @Override
    public void settingsChanged(@NotNull MarkdownSettings settings) {
      mySwingAlarm.addRequest(() -> {
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
      }, 0, ModalityState.stateForComponent(getComponent()));
    }
  }

  private static class PreciseVerticalScrollHelper extends MouseAdapter {
    private final @NotNull EditorImpl editor;
    private final @NotNull Supplier<MarkdownHtmlPanelEx> htmlPanelSupplier;
    private int lastOffset = 0;

    private PreciseVerticalScrollHelper(@NotNull EditorImpl editor, @NotNull Supplier<MarkdownHtmlPanelEx> htmlPanelSupplier) {
      this.editor = editor;
      this.htmlPanelSupplier = htmlPanelSupplier;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent event) {
      final var currentOffset = editor.getScrollingModel().getVerticalScrollOffset();
      if (lastOffset == currentOffset) {
        boundaryReached(event);
      } else {
        lastOffset = currentOffset;
      }
    }

    private void boundaryReached(MouseWheelEvent event) {
      final var actualPanel = htmlPanelSupplier.get();
      if (actualPanel == null) {
        return;
      }
      if (event.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
        final var multiplier = Registry.intValue("markdown.experimental.boundary.precise.scroll.multiplier", 1);
        final var amount = event.getScrollAmount() * event.getWheelRotation() * multiplier;
        actualPanel.scrollBy(0, amount);
      }
    }
  }
}
