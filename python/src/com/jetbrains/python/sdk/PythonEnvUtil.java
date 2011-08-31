package com.jetbrains.python.sdk;

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * @author traff
 */
public class PythonEnvUtil {
  private PythonEnvUtil() {
  }

  public static Map<String, String> setPythonUnbuffered(@NotNull Map<String, String> envs) {
    envs.put("PYTHONUNBUFFERED", "1");
    return envs;
  }

  public static Map<String, String> setPythonIOEncoding(@NotNull Map<String, String> envs, @NotNull String encoding) {
    envs.put("PYTHONIOENCODING", encoding);
    return envs;
  }

  /**
   * @param source
   * @return a copy of source map, or a new map if source is null.
   */
  public static Map<String, String> cloneEnv(@Nullable Map<String, String> source) {
    Map<String, String> new_env;
    if (source != null) {
      new_env = new HashMap<String, String>(source);
    }
    else {
      new_env = new HashMap<String, String>();
    }
    return new_env;
  }

  /**
   * Appends a value to the end os a path-like environment variable, using system-dependent path separator.
   *
   * @param source path-like string to append to
   * @param value  what to append
   * @return modified path-like string
   */
  @NotNull
  public static String appendToPathEnvVar(@Nullable String source, @NotNull String value) {
    if (source != null) {
      source = value + File.pathSeparator + source;
    }
    else {
      source = value;
    }
    return source;
  }
}
