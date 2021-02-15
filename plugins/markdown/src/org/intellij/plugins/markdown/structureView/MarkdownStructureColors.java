// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.structureView;

import com.intellij.openapi.editor.colors.TextAttributesKey;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public final class MarkdownStructureColors {
  public static final TextAttributesKey MARKDOWN_HEADER = createTextAttributesKey("MARKDOWN_HEADER");
  public static final TextAttributesKey MARKDOWN_HEADER_BOLD = createTextAttributesKey("MARKDOWN_HEADER_BOLD");
}