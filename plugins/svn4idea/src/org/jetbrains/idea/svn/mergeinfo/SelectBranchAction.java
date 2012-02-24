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
package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.changes.committed.LabeledComboBoxAction;
import com.intellij.util.Consumer;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;

import javax.swing.*;

public class SelectBranchAction extends LabeledComboBoxAction implements SelectRootListener, Getter<WCInfoWithBranches.Branch> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.mergeinfo.SelectBranchAction");
  private final DecoratorManager myManager;
  private WCInfoWithBranches mySelectedRoot;
  public static final DefaultComboBoxModel EMPTY = new DefaultComboBoxModel();
  private final Consumer<String> mySelectionListener;

  public SelectBranchAction(final DecoratorManager manager, final Consumer<String> selectionListener) {
    super(SvnBundle.message("committed.changes.action.merge.highlighting.select.branch"));
    myManager = manager;
    mySelectionListener = selectionListener;
  }

  protected void selectionChanged(final Object selection) {
    mySelectionListener.consume(((WCInfoWithBranches.Branch) selection).getUrl());
    myManager.repaintTree();
  }

  public void selectionChanged(final WCInfoWithBranches wcInfoWithBranches) {
    final boolean valueChanges = (wcInfoWithBranches != null) && (!wcInfoWithBranches.equals(mySelectedRoot));
    mySelectedRoot = wcInfoWithBranches;

    if (valueChanges) {
      final ComboBoxModel model = createModel();
      setModel(model);
      myManager.repaintTree();
    }
  }

  public void force(final WCInfoWithBranches info) {
    mySelectedRoot = info;

    if (info == null) {
      setModel(EMPTY);
    } else {

      final WCInfoWithBranches.Branch selected = (WCInfoWithBranches.Branch) getSelected();
      final ComboBoxModel model = createModel();
      setModel(model);

      if (selected != null) {
        boolean selectedSet = false;
        for (int i = 0; i < model.getSize(); i++) {
          final WCInfoWithBranches.Branch element = (WCInfoWithBranches.Branch) model.getElementAt(i);
          if (selected.equals(element)) {
            model.setSelectedItem(element);
            selectedSet = true;
          }
        }
        if (! selectedSet) {
          model.setSelectedItem(model.getElementAt(0));
        }
      } else {
        if (model.getSize() > 0) {
          model.setSelectedItem(model.getElementAt(0));
        }
      }
    }

    myManager.repaintTree();
  }

  protected ComboBoxModel createModel() {
    if (mySelectedRoot == null) {
      return EMPTY;
    }

    final DefaultComboBoxModel model = new DefaultComboBoxModel(mySelectedRoot.getBranches().toArray());
    if (model.getSize() > 0) {
      selectionChanged(model.getElementAt(0));
    }
    return model;
  }

  public void enable(final boolean value) {
    myManager.repaintTree();
  }

  public WCInfoWithBranches.Branch get() {
    return (WCInfoWithBranches.Branch) getSelected();
  }
}
