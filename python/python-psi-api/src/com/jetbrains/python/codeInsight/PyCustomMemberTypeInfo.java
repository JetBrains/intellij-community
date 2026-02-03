// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Info to add to type of custom member.
 *
 * @author Ilya.Kazakevich
 */
public class PyCustomMemberTypeInfo<K> {
  private final @NotNull Map<Key<K>, K> myCustomInfo = new HashMap<>();

  public PyCustomMemberTypeInfo(final @NotNull Key<K> key, final @NotNull K value) {
    this(Collections.singleton(Pair.create(key, value)));
  }

  public PyCustomMemberTypeInfo(final @NotNull Iterable<? extends Pair<Key<K>, K>> customInfo) {
    for (final Pair<Key<K>, K> pair : customInfo) {
      myCustomInfo.put(pair.first, pair.second);
    }
  }

  public PyCustomMemberTypeInfo(final @NotNull Map<Key<K>, K> customInfo) {
    myCustomInfo.putAll(customInfo);
  }

  @ApiStatus.Internal
  public void fill(final @NotNull UserDataHolder typeToFill) {
    for (final Map.Entry<Key<K>, K> entry : myCustomInfo.entrySet()) {
      typeToFill.putUserData(entry.getKey(), entry.getValue());
    }
  }
}
