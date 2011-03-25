/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.editor.colors.ColorKey;

import java.awt.*;

/**
 * @author Eugene.Kudelevsky
 */
public class HtmlTagTreeHighlightingColors {
  // todo: support adding any number of keys
  public static final ColorKey[] COLOR_KEYS = new ColorKey[6];

  static {
    final Color[] baseColors = new Color[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA};

    for (int i = 0; i < COLOR_KEYS.length; i++) {
      COLOR_KEYS[i] = ColorKey.createColorKey("HTML_TAG_TREE_LEVEL" + i, baseColors[i % baseColors.length]);
    }
  }

  private HtmlTagTreeHighlightingColors() {
  }
}
