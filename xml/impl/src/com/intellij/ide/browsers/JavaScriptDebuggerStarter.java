package com.intellij.ide.browsers;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Don't implement directly, use {@link com.intellij.javascript.debugger.execution.BaseJavaScriptDebuggerStarter}
 */
public interface JavaScriptDebuggerStarter<RC extends RunConfiguration, U> {
  boolean isApplicable(@NotNull RunConfiguration runConfiguration);

  // todo we must pass browser family, otherwise result could be unexpected (by default Chrome will be used, but user can prefer Firefox)
  void start(@NotNull String url, @NotNull RC runConfiguration, @NotNull U userData);

  final class Util {
    static final ExtensionPointName<JavaScriptDebuggerStarter> EP_NAME = ExtensionPointName.create("com.intellij.javaScriptDebuggerStarter");
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
      JavaScriptDebuggerStarter<RC, Object> starter = get(runConfiguration);
      if (starter == null) {
        return false;
      }
      starter.start(url, runConfiguration, NULL_OBJECT);
      return true;
    }
  }
}
