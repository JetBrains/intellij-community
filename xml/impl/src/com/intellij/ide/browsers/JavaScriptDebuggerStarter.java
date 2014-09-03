package com.intellij.ide.browsers;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Don't implement - consider to implement {@link com.jetbrains.javascript.debugger.FileUrlMapper} instead of providing mappings directly
 * If you still want to implement - don't implement directly, use {@link com.intellij.javascript.debugger.execution.BaseJavaScriptDebuggerStarter}
 */
public interface JavaScriptDebuggerStarter<RC extends RunConfiguration, U> {
  boolean isApplicable(@NotNull RunConfiguration runConfiguration);

  void start(@NotNull String url, @NotNull RC runConfiguration, @NotNull U userData, @Nullable WebBrowser browser);

  final class Util {
    static final ExtensionPointName<JavaScriptDebuggerStarter> EP_NAME = ExtensionPointName.create("org.jetbrains.javaScriptDebuggerStarter");
    private static final Object NULL_OBJECT = new Object();

    @Nullable
    public static <RC extends RunConfiguration, T> JavaScriptDebuggerStarter<RC, T> get(@NotNull RC runConfiguration) {
      for (JavaScriptDebuggerStarter<?, ?> starter : EP_NAME.getExtensions()) {
        if (starter.isApplicable(runConfiguration)) {
          //noinspection unchecked
          return (JavaScriptDebuggerStarter<RC, T>)starter;
        }
      }
      return null;
    }

    public static <RC extends RunConfiguration> boolean start(@NotNull RC runConfiguration, @NotNull String url) {
      return start(runConfiguration, url, null);
    }

    public static <RC extends RunConfiguration> boolean start(@NotNull RC runConfiguration, @NotNull String url, @Nullable WebBrowser browser) {
      JavaScriptDebuggerStarter<RC, Object> starter = get(runConfiguration);
      if (starter == null) {
        return false;
      }
      starter.start(url, runConfiguration, NULL_OBJECT, browser);
      return true;
    }

    public static <RC extends RunConfiguration> void startDebugOrLaunchBrowser(@NotNull RC runConfiguration, @NotNull StartBrowserSettings settings) {
      String url = settings.getUrl();
      assert url != null;
      startDebugOrLaunchBrowser(runConfiguration, url, settings.getBrowser(), settings.isStartJavaScriptDebugger());
    }

    public static <RC extends RunConfiguration> void startDebugOrLaunchBrowser(@NotNull RC runConfiguration,
                                                                               @NotNull String url,
                                                                               @Nullable WebBrowser browser,
                                                                               boolean startDebugger) {
      if (!startDebugger || !start(runConfiguration, url, browser)) {
        BrowserLauncher.getInstance().browse(url, browser, runConfiguration.getProject());
      }
    }

    public static boolean hasStarters() {
      return EP_NAME.getExtensions().length > 0;
    }
  }
}
