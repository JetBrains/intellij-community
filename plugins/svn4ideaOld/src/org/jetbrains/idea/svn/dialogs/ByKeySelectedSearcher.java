/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.List;

abstract class ByKeySelectedSearcher {
  private final String myKey;
  protected final List<TreeNode> myChildren;

  protected ByKeySelectedSearcher(final String key, final List<TreeNode> myChildren) {
    myKey = key;
    this.myChildren = myChildren;
  }

  protected abstract IterationResultHolder doChecks(final TreeNode node);

  @Nullable
  public TreeNode getNextSelectedByKey() {
    if (myKey != null) {
      for (final TreeNode node : myChildren) {
        final IterationResultHolder checkResult = doChecks(node);
        if (IterationResultHolder.RESULT.equals(checkResult)) {
          return node;
        }
        else if (IterationResultHolder.SKIP.equals(checkResult)) {
          continue;
        }

        if (myKey.compareTo(node.toString()) <= 0) {
          return node;
        }
      }
    }
    return ((myChildren == null) || myChildren.isEmpty()) ? null : myChildren.get(myChildren.size() - 1);
  }
}
