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

import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author traff
 */
public class PythonEnvUtil {
  @SuppressWarnings("SpellCheckingInspection") public static final String PYTHONPATH = "PYTHONPATH";
  @SuppressWarnings("SpellCheckingInspection") public static final String PYTHONUNBUFFERED = "PYTHONUNBUFFERED";
  @SuppressWarnings("SpellCheckingInspection") public static final String PYTHONIOENCODING = "PYTHONIOENCODING";
  @SuppressWarnings("SpellCheckingInspection") public static final String IPYTHONENABLE = "IPYTHONENABLE";
  @SuppressWarnings("SpellCheckingInspection") public static final String PYTHONDONTWRITEBYTECODE = "PYTHONDONTWRITEBYTECODE";

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
   * Appends a value to the end os a path-like environment variable, using system-dependent path separator.
   *
   * @param source path-like string to append to
   * @param value  what to append
   * @return modified path-like string
   */
  @NotNull
  public static String appendToPathEnvVar(@Nullable String source, @NotNull String value) {
    if (StringUtil.isEmpty(source)) return value;
    Set<String> paths = Sets.newHashSet(source.split(File.pathSeparator));
    return !paths.contains(value) ? source + File.pathSeparator + value : source;
  }

  public static void addPathsToEnv(@NotNull Map<String, String> env, String key, @NotNull Collection<String> values) {
    for (String val : values) {
      addPathToEnv(env, key, val);
    }
  }

  public static void addPathToEnv(@NotNull Map<String, String> env, String key, String value) {
    if (!StringUtil.isEmpty(value)) {
      if (env.containsKey(key)) {
        env.put(key, appendToPathEnvVar(env.get(key), value));
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
}
