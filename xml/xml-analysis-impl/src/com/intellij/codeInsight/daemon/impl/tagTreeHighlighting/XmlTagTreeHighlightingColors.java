/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.util.ui.PlatformColors;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlTagTreeHighlightingColors {
  private static ColorKey[] ourColorKeys = null;

  private static final Color[] DEFAULT_COLORS = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, PlatformColors.BLUE, Color.MAGENTA};

  private XmlTagTreeHighlightingColors() {
  }

  @NotNull
  public static ColorKey[] getColorKeys() {
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
