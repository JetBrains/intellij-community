// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.javafx;

import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.javafx.JavaFxHtmlPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.extensions.javafx.MarkdownJavaFXPreviewExtension;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel;
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer;
import org.intellij.plugins.markdown.ui.preview.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class MarkdownJavaFxHtmlPanel extends JavaFxHtmlPanel implements MarkdownHtmlPanel {
  private static final List<String> BASE_SCRIPTS = ContainerUtil.immutableList(
    "scrollToElement.js",
    "processLinks.js"
  );

  private static final List<MarkdownJavaFXPreviewExtension> EXTENSIONS =
    MarkdownJavaFXPreviewExtension.getAllSorted();

  private static final List<String> SCRIPTS = ContainerUtil.concat(
    BASE_SCRIPTS,
    EXTENSIONS.stream()
      .flatMap(extension -> extension.getScripts().stream())
      .collect(Collectors.toList())
  );

  private static final List<String> STYLES = EXTENSIONS.stream()
    .flatMap(extension -> extension.getStyles().stream())
    .collect(Collectors.toList());

  private static final NotNullLazyValue<String> SCRIPTING_LINES = new NotNullLazyValue<String>() {
    @NotNull
    @Override
    protected String compute() {
      return SCRIPTS.stream()
        .map(s -> "<script src=\"" + PreviewStaticServer.getStaticUrl(s) + "\"></script>")
        .reduce((s, s2) -> s + "\n" + s2)
        .orElseGet(String::new);
    }
  };

  private static final NotNullLazyValue<String> STYLES_LINES = new NotNullLazyValue<String>() {
    @NotNull
    @Override
    protected String compute() {
      return STYLES.stream()
        .map(s -> "<link rel=\"stylesheet\" href=\"" + PreviewStaticServer.getStaticUrl(s) + "\"/>")
        .reduce((s, s2) -> s + "\n" + s2)
        .orElseGet(String::new);
    }
  };

  private static final NotNullLazyValue<String> CSP = new NotNullLazyValue<String>() {
    @NotNull
    @Override
    protected String compute() {
      return PreviewStaticServer.createCSP(
        ContainerUtil.map(SCRIPTS, s -> PreviewStaticServer.getStaticUrl(s)),
        ContainerUtil.map(STYLES, s -> PreviewStaticServer.getStaticUrl(s)));
    }
  };

  @NotNull
  private final ScrollPreservingListener myScrollPreservingListener = new ScrollPreservingListener();
  @NotNull
  private final BridgeSettingListener myBridgeSettingListener = new BridgeSettingListener();

  public MarkdownJavaFxHtmlPanel() {
    super();
    runInPlatformWhenAvailable(() -> {
      if (myWebView != null) {
        updateFontSmoothingType(
          myWebView, MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isUseGrayscaleRendering()
        );
      }
    });
    PreviewStaticServer.getInstance().setResourceProvider(resourceProvider);
    subscribeForGrayscaleSetting();
  }

  @Override
  protected void registerListeners(@NotNull WebEngine engine) {
    engine.getLoadWorker().stateProperty().addListener(myBridgeSettingListener);
    engine.getLoadWorker().stateProperty().addListener(myScrollPreservingListener);
  }

  private void subscribeForGrayscaleSetting() {
    MessageBusConnection settingsConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
    MarkdownApplicationSettings.SettingsChangedListener settingsChangedListener =
      new MarkdownApplicationSettings.SettingsChangedListener() {
        @Override
        public void beforeSettingsChanged(@NotNull final MarkdownApplicationSettings settings) {
          runInPlatformWhenAvailable(() -> {
            if (myWebView != null) {
              updateFontSmoothingType(myWebView, settings.getMarkdownPreviewSettings().isUseGrayscaleRendering());
            }
          });
        }
      };
    settingsConnection.subscribe(MarkdownApplicationSettings.SettingsChangedListener.TOPIC, settingsChangedListener);
  }

  private static void updateFontSmoothingType(@NotNull WebView view, boolean isGrayscale) {
    final FontSmoothingType typeToSet;
    if (isGrayscale) {
      typeToSet = FontSmoothingType.GRAY;
    }
    else {
      typeToSet = FontSmoothingType.LCD;
    }
    view.fontSmoothingTypeProperty().setValue(typeToSet);
  }


  private static final MyResourceProvider resourceProvider = new MyResourceProvider();

  @NotNull
  @Override
  protected String prepareHtml(@NotNull String html) {
    return ImageRefreshFix.setStamps(
      html.replace(
        "<head>",
        "<head>" +
        "<meta http-equiv=\"Content-Security-Policy\" content=\"" + CSP + "\"/>" +
        STYLES_LINES.getValue() + "\n" + SCRIPTING_LINES.getValue()
      )
    );
  }

  @Override
  public void scrollToMarkdownSrcOffset(final int offset) {
    runInPlatformWhenAvailable(() -> {
      getWebViewGuaranteed().getEngine().executeScript(
        "if ('__IntelliJTools' in window) " +
        "__IntelliJTools.scrollToOffset(" + offset + ", '" + HtmlGenerator.Companion.getSRC_ATTRIBUTE_NAME() + "');"
      );
      final Object result = getWebViewGuaranteed().getEngine().executeScript(
        "document.documentElement.scrollTop || (document.body && document.body.scrollTop)");
      if (result instanceof Number) {
        myScrollPreservingListener.myScrollY = ((Number)result).intValue();
      }
    });
  }

  @Override
  public void dispose() {
    runInPlatformWhenAvailable(() -> {
      getWebViewGuaranteed().getEngine().getLoadWorker().stateProperty().removeListener(myScrollPreservingListener);
      getWebViewGuaranteed().getEngine().getLoadWorker().stateProperty().removeListener(myBridgeSettingListener);
    });
  }

  private static class MyResourceProvider implements ResourceProvider {
    @Override
    public boolean canProvide(@NotNull String resourceName) {
      return BASE_SCRIPTS.contains(resourceName) ||
             EXTENSIONS.stream()
               .anyMatch(extension -> extension.getResourceProvider().canProvide(resourceName));
    }

    @Nullable
    @Override
    public Resource loadResource(@NotNull String resourceName) {
      if (BASE_SCRIPTS.contains(resourceName)) {
        return ResourceProvider.loadInternalResource(MarkdownJavaFxHtmlPanel.class, resourceName, null);
      }
      ResourceProvider provider = EXTENSIONS.stream()
        .filter(extension -> extension.getResourceProvider().canProvide(resourceName))
        .map(extension -> extension.getResourceProvider())
        .findFirst()
        .orElse(null);
      if (provider == null) {
        return null;
      }
      return provider.loadResource(resourceName);
    }
  }

  @SuppressWarnings("unused")
  public static class JavaPanelBridge {
    static final JavaPanelBridge INSTANCE = new JavaPanelBridge();
    private static final NotificationGroup MARKDOWN_NOTIFICATION_GROUP = NotificationGroup
      .toolWindowGroup("Markdown headers group", ToolWindowId.MESSAGES_WINDOW, true,
                       MarkdownBundle.message("markdown.navigate.to.header.group"));

    public void openInExternalBrowser(@NotNull String link) {
      SafeOpener.openLink(link);
    }

    public void log(@Nullable String text) {
      Logger.getInstance(JavaPanelBridge.class).warn(text);
    }
  }

  private class BridgeSettingListener implements ChangeListener<State> {
    @Override
    public void changed(ObservableValue<? extends State> observable, State oldValue, State newValue) {
      JSObject win
        = (JSObject)getWebViewGuaranteed().getEngine().executeScript("window");
      win.setMember("JavaPanelBridge", JavaPanelBridge.INSTANCE);
    }
  }

  private class ScrollPreservingListener implements ChangeListener<State> {
    volatile int myScrollY = 0;

    @Override
    public void changed(ObservableValue<? extends State> observable, State oldValue, State newValue) {
      if (newValue == State.RUNNING) {
        final Object result =
          getWebViewGuaranteed().getEngine().executeScript("document.documentElement.scrollTop || document.body.scrollTop");
        if (result instanceof Number) {
          myScrollY = ((Number)result).intValue();
        }
      }
      else if (newValue == State.SUCCEEDED) {
        getWebViewGuaranteed().getEngine()
          .executeScript("document.documentElement.scrollTop = ({} || document.body).scrollTop = " + myScrollY);
      }
    }
  }
}
