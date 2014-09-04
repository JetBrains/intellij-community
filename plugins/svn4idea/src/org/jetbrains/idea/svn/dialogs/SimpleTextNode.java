/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author Konstantin Kolosovsky.
 */
public class SimpleTextNode extends DefaultMutableTreeNode {

  private final boolean myIsError;

  public SimpleTextNode(@NotNull String text) {
    this(text, false);
  }

  public SimpleTextNode(@NotNull String text, boolean isError) {
    super(text);
    myIsError = isError;
  }

  public boolean isError() {
    return myIsError;
  }

  @NotNull
  public String getText() {
    return (String)getUserObject();
  }
}
