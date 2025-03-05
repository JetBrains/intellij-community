// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.util.ui.PlatformColors;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class XmlTagTreeHighlightingColors {
  private static ColorKey[] ourColorKeys = null;

  private static final Color[] DEFAULT_COLORS = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, PlatformColors.BLUE, Color.MAGENTA};

  private XmlTagTreeHighlightingColors() {
  }

  public static synchronized ColorKey @NotNull [] getColorKeys() {
    final int levelCount = WebEditorOptions.getInstance().getTagTreeHighlightingLevelCount();

    if (ourColorKeys == null || ourColorKeys.length != levelCount) {
      ourColorKeys = new ColorKey[levelCount];

      for (int i = 0; i < ourColorKeys.length; i++) {
        ourColorKeys[i] = ColorKey.createColorKey("HTML_TAG_TREE_LEVEL" + i, DEFAULT_COLORS[i % DEFAULT_COLORS.length]);
      }
    }

    return ourColorKeys;
  }
}
