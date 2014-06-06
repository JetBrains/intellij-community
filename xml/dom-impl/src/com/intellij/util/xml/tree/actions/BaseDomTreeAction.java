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

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.xml.tree.DomModelTreeView;

/**
 * User: Sergey.Vasiliev
 */
abstract public class BaseDomTreeAction extends AnAction {
  private DomModelTreeView myTreeView;

  protected BaseDomTreeAction() {
  }

  protected BaseDomTreeAction(DomModelTreeView treeView) {
    myTreeView = treeView;
  }

  @Override
  final public void update(AnActionEvent e) {
    final DomModelTreeView treeView = getTreeView(e);

    if (treeView != null) {
      update(e, treeView);
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  protected DomModelTreeView getTreeView(AnActionEvent e) {
    if (myTreeView != null) return myTreeView;

    return DomModelTreeView.DATA_KEY.getData(e.getDataContext());
  }

  @Override
  final public void actionPerformed(AnActionEvent e) {
    final DomModelTreeView treeView = getTreeView(e);
    if (treeView != null) {
      actionPerformed(e, treeView);
    }
  }

  public abstract void actionPerformed(AnActionEvent e, DomModelTreeView treeView);

  public abstract void update(AnActionEvent e, DomModelTreeView treeView);
}

