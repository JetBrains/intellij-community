// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.psi.tree.IElementType;

/**
 * @deprecated Please use {@link MarkdownCodeFence} instead.
 */
@Deprecated
public class MarkdownCodeFenceImpl extends MarkdownCodeFence {
  public MarkdownCodeFenceImpl(IElementType type) {
    super(type);
  }
}
