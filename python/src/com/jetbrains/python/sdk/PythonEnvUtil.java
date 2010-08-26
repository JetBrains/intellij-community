package com.jetbrains.python.sdk;

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author traff
 */
public class PythonEnvUtil {
  private PythonEnvUtil() {
  }

  public static void setPythonUnbuffered(Map<String, String> envs) {
    envs.put("PYTHONUNBUFFERED", "1");
  }

  /**
   * @param source
   * @return a copy of source map, or a new map if source is null.
   */
  public static Map<String, String> cloneEnv(@Nullable Map<String, String> source) {
    Map<String, String> new_env;
    if (source != null) new_env = new HashMap<String, String>(source);
    else new_env = new HashMap<String, String>();
    return new_env;
  }
}
