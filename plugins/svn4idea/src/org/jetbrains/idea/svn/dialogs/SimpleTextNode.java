// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author Konstantin Kolosovsky.
 */
public class SimpleTextNode extends DefaultMutableTreeNode {

  private final boolean myIsError;

  public SimpleTextNode(@NlsContexts.Label @NotNull String text) {
    this(text, false);
  }

  public SimpleTextNode(@NlsContexts.Label @NotNull String text, boolean isError) {
    super(text);
    myIsError = isError;
  }

  public boolean isError() {
    return myIsError;
  }

  public @NlsContexts.Label @NotNull String getText() {
    return (String)getUserObject();
  }
}
