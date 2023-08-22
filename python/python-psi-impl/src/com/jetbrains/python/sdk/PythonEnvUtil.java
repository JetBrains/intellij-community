/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class PythonEnvUtil {
  @SuppressWarnings("SpellCheckingInspection") public static final String PYTHONPATH = "PYTHONPATH";
  @SuppressWarnings("SpellCheckingInspection") public static final String PYTHONUNBUFFERED = "PYTHONUNBUFFERED";
  @SuppressWarnings("SpellCheckingInspection") public static final String PYTHONIOENCODING = "PYTHONIOENCODING";
  @SuppressWarnings("SpellCheckingInspection") public static final String IPYTHONENABLE = "IPYTHONENABLE";
  @SuppressWarnings("SpellCheckingInspection") public static final String PYTHONDONTWRITEBYTECODE = "PYTHONDONTWRITEBYTECODE";
  @SuppressWarnings("SpellCheckingInspection") public static final String PYVENV_LAUNCHER = "__PYVENV_LAUNCHER__";

  private PythonEnvUtil() { }

  public static Map<String, String> setPythonUnbuffered(@NotNull Map<String, String> env) {
    env.put(PYTHONUNBUFFERED, "1");
    return env;
  }

  public static Map<String, String> setPythonIOEncoding(@NotNull Map<String, String> env, @NotNull String encoding) {
    env.put(PYTHONIOENCODING, encoding);
    return env;
  }

  /**
   * Resets the environment variables that affect the way the Python interpreter searches for its settings and libraries.
   */
  public static Map<String, String> resetHomePathChanges(@NotNull String homePath, @NotNull Map<String, String> env) {
    if (System.getenv(PYVENV_LAUNCHER) != null || EnvironmentUtil.getEnvironmentMap().containsKey(PYVENV_LAUNCHER)) {
      env.put(PYVENV_LAUNCHER, homePath);
    }
    return env;
  }

  /**
   * Adds a value to the end os a path-like environment variable, using system-dependent path separator.
   *
   * @param source path-like string to append to
   * @param value  what to append
   * @param asPrefix if true, adds a value as a prefix, otherwise as a suffix
   * @return modified path-like string
   */

  @NotNull
  public static String addToPathEnvVar(@Nullable String source, @NotNull String value, boolean asPrefix) {
    if (StringUtil.isEmpty(source)) return value;
    Set<String> paths = Sets.newHashSet(source.split(File.pathSeparator));
    if (!paths.contains(value)) {
      if (asPrefix) {
        return value + File.pathSeparator + source;
      }
      else {
        return source + File.pathSeparator + value;
      }
    }
    else {
      return source;
    }
  }

  public static void addPathsToEnv(@NotNull Map<String, String> env, String key, @NotNull Collection<String> values) {
    for (String val : values) {
      addPathToEnv(env, key, val);
    }
  }

  public static void addPathToEnv(@NotNull Map<String, String> env, String key, String value) {
    if (!StringUtil.isEmpty(value)) {
      if (env.containsKey(key)) {
        env.put(key, addToPathEnvVar(env.get(key), value, false));
      }
      else {
        env.put(key, value);
      }
    }
  }

  public static void addToPythonPath(@NotNull Map<String, String> env, @NotNull Collection<String> values) {
    addPathsToEnv(env, PYTHONPATH, values);
  }

  public static void addToPythonPath(@NotNull Map<String, String> env, String value) {
    addPathToEnv(env, PYTHONPATH, value);
  }

  public static void mergePythonPath(@NotNull Map<String, String> from, @NotNull Map<String, String> to) {
    String value = from.get(PYTHONPATH);
    if (value != null) {
      Set<String> paths = Sets.newHashSet(value.split(File.pathSeparator));
      addToPythonPath(to, paths);
    }
  }

  @NotNull
  public static Map<String, String> setPythonDontWriteBytecode(@NotNull Map<String, String> env) {
    env.put(PYTHONDONTWRITEBYTECODE, "1");
    return env;
  }

  public static Collection<String> appendSystemPythonPath(@NotNull Collection<String> pythonPath) {
    return appendSystemEnvPaths(pythonPath, PYTHONPATH);
  }

  public static Collection<String> appendSystemEnvPaths(@NotNull Collection<String> pythonPath, String envname) {
    String syspath = System.getenv(envname);
    if (syspath != null) {
      pythonPath.addAll(Lists.newArrayList(syspath.split(File.pathSeparator)));
    }
    return pythonPath;
  }

  public static void initPythonPath(@NotNull Map<String, String> envs, boolean passParentEnvs, @NotNull Collection<String> pythonPathList) {
    if (passParentEnvs && !envs.containsKey(PYTHONPATH)) {
      pythonPathList = appendSystemPythonPath(pythonPathList);
    }
    addToPythonPath(envs, pythonPathList);
  }

  public static void addToEnv(final String key, String value, Map<String, String> envs) {
    addPathToEnv(envs, key, value);
  }

  public static void setupEncodingEnvs(Map<String, String> envs, @NotNull Charset charset) {
    final String encoding = charset.name();
    setPythonIOEncoding(envs, encoding);
  }
}
