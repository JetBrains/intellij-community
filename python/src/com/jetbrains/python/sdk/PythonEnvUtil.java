package com.jetbrains.python.sdk;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author traff
 */
public class PythonEnvUtil {
  public static final String PYTHONPATH = "PYTHONPATH";
  public static final String PYTHONUNBUFFERED = "PYTHONUNBUFFERED";
  public static final String PYTHONIOENCODING = "PYTHONIOENCODING";

  private PythonEnvUtil() {
  }

  public static Map<String, String> setPythonUnbuffered(@NotNull Map<String, String> envs) {
    envs.put(PYTHONUNBUFFERED, "1");
    return envs;
  }

  public static Map<String, String> setPythonIOEncoding(@NotNull Map<String, String> envs, @NotNull String encoding) {
    envs.put(PYTHONIOENCODING, encoding);
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
    if (StringUtil.isNotEmpty(source)) {
      assert source != null;
      Set<String> vals = Sets.newHashSet(source.split(File.pathSeparator));
      if (!vals.contains(value)) {
        return value + File.pathSeparatorChar + source;
      }
      else {
        return source;
      }
    }
    return value;
  }

  public static void addToEnv(Map<String, String> envs, String key, Collection<String> values) {
    for (String val : values) {
      addToEnv(envs, key, val);
    }
  }

  public static void addToEnv(Map<String, String> envs, String key, String value) {
    if (envs.containsKey(key)) {
      envs.put(key, appendToPathEnvVar(envs.get(key), value));
    }
    else {
      envs.put(key, value);
    }
  }

  public static void addToPythonPath(Map<String, String> envs, Collection<String> values) {
    addToEnv(envs, PYTHONPATH, values);
  }

  public static void addToPythonPath(Map<String, String> envs, String value) {
    addToEnv(envs, PYTHONPATH, value);
  }


  @Nullable
  public static List<String> getPythonPathList(Map<String, String> envs) {
    String pythonPath = envs.get(PYTHONPATH);
    if (pythonPath != null) {
      String[] paths = pythonPath.split(Character.toString(File.pathSeparatorChar));
      return Lists.newArrayList(paths);
    }
    else {
      return null;
    }
  }
}
