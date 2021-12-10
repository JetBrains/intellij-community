// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.intellij.plugins.markdown.lang.stubs.impl.MarkdownHeaderStubElement;
import org.intellij.plugins.markdown.lang.stubs.impl.MarkdownHeaderStubElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Please use {@link MarkdownHeader} instead.
 */
@Deprecated
public class MarkdownHeaderImpl extends MarkdownHeader {
  public MarkdownHeaderImpl(@NotNull ASTNode node) {
    super(node);
  }

  public MarkdownHeaderImpl(MarkdownHeaderStubElement stub, MarkdownHeaderStubElementType type) {
    super(stub, type);
  }

  public int getHeaderNumber() {
    return getLevel();
  }
}
