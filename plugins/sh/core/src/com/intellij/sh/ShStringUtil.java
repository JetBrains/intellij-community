// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ShStringUtil {
  private static final char[] ORIGIN_CHARS = new char[]{
      ' ', '!', '"', '#', '$', '&', '\'', '(', ')', '*', ',', ';', '<', '>', '=', '?', '[', '\\', ']', '^', '`', '{', '|', '}'
  };

  public static final Set<Character> ORIGINS_SET = IntStream.range(0, ORIGIN_CHARS.length)
      .mapToObj(i -> ORIGIN_CHARS[i])
      .collect(Collectors.toSet());

  private static final List<String> ENCODED = toStr(ORIGIN_CHARS, '\\');
  private static final List<String> ORIGINS = toStr(ORIGIN_CHARS, null);

  private static List<String> toStr(char[] arr, Character prefix) {
    return IntStream.range(0, arr.length)
        .mapToObj(i -> {
          String v = String.valueOf(arr[i]);
          return prefix != null ? prefix + v : v;
        })
        .collect(Collectors.toList());
  }

  public static @NotNull String quote(String name) {
    return StringUtil.replace(name, ORIGINS, ENCODED);
  }

  @NotNull
  public static String unquote(String afterSlash) {
    return StringUtil.replace(afterSlash, ENCODED, ORIGINS);
  }
}
