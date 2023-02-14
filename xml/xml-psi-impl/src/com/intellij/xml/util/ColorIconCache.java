// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.util.containers.SoftFactoryMap;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ColorIconCache {
  private static final ColorIconCache INSTANCE = new ColorIconCache();
  private static final SoftFactoryMap<Color, ConcurrentMap<Integer, Icon>> cache = new SoftFactoryMap<>() {
    @Override
    protected ConcurrentMap<Integer, Icon> create(@NotNull Color key) {
      return new ConcurrentHashMap<>();
    }
  };

  private ColorIconCache() { }

  public static ColorIconCache getIconCache() {
    return INSTANCE;
  }

  public @NotNull Icon getIcon(@NotNull Color color, int size) {
    return Objects.requireNonNull(cache.get(color)).computeIfAbsent(size, s -> new ColorIcon(s, color, true));
  }
}