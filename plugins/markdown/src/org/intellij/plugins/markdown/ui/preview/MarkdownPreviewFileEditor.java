// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.CommonBundle;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.StartupUiUtil;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;
import org.intellij.plugins.markdown.settings.MarkdownCssSettings;
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings;
import org.intellij.plugins.markdown.ui.split.SplitFileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MarkdownPreviewFileEditor extends UserDataHolderBase implements FileEditor {
  private final static long PARSING_CALL_TIMEOUT_MS = 50L;

  private final static long RENDERING_DELAY_MS = 20L;

  final static NotNullLazyValue<PolicyFactory> SANITIZER_VALUE = new NotNullLazyValue<PolicyFactory>() {
    @NotNull
    @Override
    protected PolicyFactory compute() {
      return Sanitizers.BLOCKS
        .and(Sanitizers.FORMATTING)
        .and(new HtmlPolicyBuilder()
               .allowUrlProtocols("source", "file", "http", "https").allowElements("img")
               .allowAttributes("alt", "src", "title").onElements("img")
               .allowAttributes("border", "height", "width").onElements("img")
               .toFactory())
        .and(new HtmlPolicyBuilder()
               .allowUrlProtocols("http", "https").allowElements("input")
               .allowAttributes("type", "class", "checked", "disabled").onElements("input")
               .toFactory())
        .and(new HtmlPolicyBuilder()
               .allowUrlProtocols("http", "https").allowElements("li")
               .allowAttributes("class").onElements("li")
               .toFactory())
        .and(new HtmlPolicyBuilder()
               .allowUrlProtocols("source", "file", "http", "https", "mailto").allowElements("a")
               .allowAttributes("href", "title").onElements("a")
               .toFactory())
        .and(Sanitizers.TABLES)
        .and(new HtmlPolicyBuilder()
               .allowElements("body", "pre", "hr", "code", "tr", "span")
               .allowAttributes(HtmlGenerator.Companion.getSRC_ATTRIBUTE_NAME()).globally()
               .allowAttributes("class").onElements("code", "tr", "span")
               .toFactory())
        .and(new HtmlPolicyBuilder()
               .allowElements("font")
               .allowAttributes("color").onElements("font")
               .toFactory());
    }
  };
  @NotNull
  private final JPanel myHtmlPanelWrapper;
  @Nullable
  private MarkdownHtmlPanel myPanel;
  @Nullable
  private MarkdownHtmlPanelProvider.ProviderInfo myLastPanelProviderInfo = null;
  @NotNull
  private final Project myProject;
  @NotNull
  private final VirtualFile myFile;
  @Nullable
  private final Document myDocument;
  @NotNull
  private final Alarm myPooledAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  @NotNull
  private final Alarm mySwingAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

  private final Object REQUESTS_LOCK = new Object();
  @Nullable
  private Runnable myLastScrollRequest = null;
  @Nullable
  private Runnable myLastHtmlOrRefreshRequest = null;

  private volatile int myLastScrollOffset;
  @NotNull
  private String myLastRenderedHtml = "";

  @Nullable
  private static Boolean ourIsDefaultMarkdownPreviewSettings = null;

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
        public void documentChanged(@NotNull final DocumentEvent e) {
          myPooledAlarm.addRequest(() -> {
            //myLastScrollOffset = e.getOffset();
            updateHtml(true);
          }, PARSING_CALL_TIMEOUT_MS);
        }
      }, this);
    }

    myHtmlPanelWrapper = new JPanel(new BorderLayout());

    myHtmlPanelWrapper.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        mySwingAlarm.addRequest(() -> {
          if (myPanel != null) {
            return;
          }

          attachHtmlPanel();
        }, 0, ModalityState.stateForComponent(getComponent()));
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        mySwingAlarm.addRequest(() -> {
          if (myPanel == null) {
            return;
          }

          detachHtmlPanel();
        }, 0, ModalityState.stateForComponent(getComponent()));
      }
    });

    if (isPreviewShown(project, file)) {
      attachHtmlPanel();
    }

    MessageBusConnection settingsConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
    MarkdownApplicationSettings.SettingsChangedListener settingsChangedListener = new MyUpdatePanelOnSettingsChangedListener();
    settingsConnection.subscribe(MarkdownApplicationSettings.SettingsChangedListener.TOPIC, settingsChangedListener);
  }

  public void scrollToSrcOffset(final int offset) {
    if (myPanel == null) {
      return;
    }

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
        if (myPanel == null) {
          return;
        }

        myLastScrollOffset = offset;
        myPanel.scrollToMarkdownSrcOffset(myLastScrollOffset);
        synchronized (REQUESTS_LOCK) {
          myLastScrollRequest = null;
        }
      };
      mySwingAlarm.addRequest(myLastScrollRequest, RENDERING_DELAY_MS, ModalityState.stateForComponent(getComponent()));
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myHtmlPanelWrapper;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myPanel == null) {
      return null;
    }
    return myPanel.getComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return "Markdown HTML Preview";
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
    if (myPanel == null) {
      return;
    }

    updateHtmlPooled();
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
    if (myPanel == null) {
      return;
    }
    Disposer.dispose(myPanel);
  }

  @NotNull
  private MarkdownHtmlPanelProvider retrievePanelProvider(@NotNull MarkdownApplicationSettings settings) {
    final MarkdownHtmlPanelProvider.ProviderInfo providerInfo = settings.getMarkdownPreviewSettings().getHtmlPanelProviderInfo();

    MarkdownHtmlPanelProvider provider = MarkdownHtmlPanelProvider.createFromInfo(providerInfo);

    if (provider.isAvailable() != MarkdownHtmlPanelProvider.AvailabilityInfo.AVAILABLE) {
      if (ourIsDefaultMarkdownPreviewSettings == null) {
        ourIsDefaultMarkdownPreviewSettings = settings.getMarkdownPreviewSettings() == MarkdownPreviewSettings.DEFAULT;
      }
      settings.setMarkdownPreviewSettings(new MarkdownPreviewSettings(settings.getMarkdownPreviewSettings().getSplitEditorLayout(),
                                                                      MarkdownPreviewSettings.DEFAULT.getHtmlPanelProviderInfo(),
                                                                      settings.getMarkdownPreviewSettings().isUseGrayscaleRendering(),
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
        Arrays.stream(MarkdownHtmlPanelProvider.getProviders()).filter(
          p -> p.isAvailable() == MarkdownHtmlPanelProvider.AvailabilityInfo.AVAILABLE).findFirst().orElse(null));
    }

    myLastPanelProviderInfo = settings.getMarkdownPreviewSettings().getHtmlPanelProviderInfo();
    return provider;
  }


  /**
   * Is always run from pooled thread
   */
  private void updateHtml(final boolean preserveScrollOffset) {
    if (myPanel == null) {
      return;
    }

    if (!myFile.isValid() || myDocument == null || Disposer.isDisposed(this)) {
      return;
    }

    final String html = MarkdownUtil.INSTANCE.generateMarkdownHtml(myFile, myDocument.getText(), myProject);

    // EA-75860: The lines to the top may be processed slowly; Since we're in pooled thread, we can be disposed already.
    if (!myFile.isValid() || Disposer.isDisposed(this)) {
      return;
    }

    synchronized (REQUESTS_LOCK) {
      if (myLastHtmlOrRefreshRequest != null) {
        mySwingAlarm.cancelRequest(myLastHtmlOrRefreshRequest);
      }
      myLastHtmlOrRefreshRequest = () -> {
        if (myPanel == null) {
          return;
        }

        final String currentHtml = "<html><head></head>" + SANITIZER_VALUE.getValue().sanitize(html) + "</html>";
        if (!currentHtml.equals(myLastRenderedHtml)) {
          myLastRenderedHtml = currentHtml;
          myPanel.setHtml(myLastRenderedHtml);

          if (preserveScrollOffset) {
            scrollToSrcOffset(myLastScrollOffset);
          }
        }

        myPanel.render();
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
    updatePanelCssSettings(myPanel, settings.getMarkdownCssSettings());
    myLastRenderedHtml = "";
    updateHtmlPooled();
  }

  private void updateHtmlPooled() {
    myPooledAlarm.cancelAllRequests();
    myPooledAlarm.addRequest(() -> updateHtml(true), 0);
  }

  private void updatePanelCssSettings(@NotNull MarkdownHtmlPanel panel, @NotNull MarkdownCssSettings cssSettings) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    String styles = getCustomStyles();

    if (styles != null) {
      panel.setCSS(styles, MarkdownCssSettings.getDefaultCssSettings(StartupUiUtil.isUnderDarcula()).getStylesheetUri());
    }
    else {
      String inlineCss = cssSettings.isTextEnabled() ? cssSettings.getStylesheetText() : null;
      String customCssURI = cssSettings.isUriEnabled()
                            ? cssSettings.getStylesheetUri()
                            : MarkdownCssSettings.getDefaultCssSettings(StartupUiUtil.isUnderDarcula()).getStylesheetUri();

      panel.setCSS(inlineCss, customCssURI);
    }

    panel.render();
  }

  @Nullable
  private String getCustomStyles() {
    ExtensionPointName<MarkdownPreviewStylesProvider> epName = MarkdownPreviewStylesProvider.Companion.getExtensionPointName();
    List<String> styles = epName.extensions()
      .map(provider -> provider.getStyles(myFile))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    if (styles.isEmpty()) return null;

    if (styles.size() > 1) {
      String providerClasses = epName.extensions()
        .filter(provider -> provider.getStyles(myFile) != null)
        .map(MarkdownPreviewStylesProvider::getClass)
        .map(Class::getName)
        .collect(Collectors.joining(", "));

      Logger.getInstance(MarkdownPreviewFileEditor.class)
        .warn(String.format("Two or more extensions trying to apply custom Markdown preview styles in '%s': %s",
                            myFile.getName(), providerClasses));
    }

    return styles.get(0);
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
          else if (myLastPanelProviderInfo == null
                   || MarkdownHtmlPanelProvider.createFromInfo(myLastPanelProviderInfo).equals(retrievePanelProvider(settings))) {
            detachHtmlPanel();
            attachHtmlPanel();
          }

          updateHtml(true);
          updatePanelCssSettings(myPanel, settings.getMarkdownCssSettings());
        }
      }, 0, ModalityState.stateForComponent(getComponent()));
    }
  }
}
