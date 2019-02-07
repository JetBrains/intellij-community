// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

/**
 * An application-level extension of "com.intellij.embeddedTokenHighlighter" extension point.
 */
public interface EmbeddedTokenHighlighter {
  /**
   * @return a map of text attributes to be used for highlighting specific non-XML token types that can occur inside XML/HTML
   */
  @NotNull
  MultiMap<IElementType, TextAttributesKey> getEmbeddedTokenAttributes();
}
