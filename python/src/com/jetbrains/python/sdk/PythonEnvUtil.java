package com.jetbrains.python.sdk;

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
}
