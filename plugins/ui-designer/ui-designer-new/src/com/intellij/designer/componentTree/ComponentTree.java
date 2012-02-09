/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.componentTree;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NonNls;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author Alexander Lobas
 */
public final class ComponentTree extends Tree implements DataProvider {
  public ComponentTree() {
    super(new DefaultTreeModel(new DefaultMutableTreeNode()));
  }

  @Override
  public Object getData(@NonNls String dataId) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}