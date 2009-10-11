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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.changes.committed.LabeledComboBoxAction;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.idea.svn.SvnBranchMapperManager;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public class SelectWCopyComboAction extends LabeledComboBoxAction implements Consumer<String>, Getter<String> {
  private String myBranch;
  private final MessageBusConnection myMessageBusConnection;

  public SelectWCopyComboAction() {
    super(SvnBundle.message("committed.changes.action.merge.highlighting.select.wcopy"));

    myMessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
    myMessageBusConnection.subscribe(SvnBranchMapperManager.WC_ROOTS_CHANGED, new SvnBranchMapperManager.WcRootsChangeConsumer() {
      public void rootsChanged(final String url, final Collection<String> roots) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          changeList(url, roots);
        } else {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              changeList(url, roots);
            }
          });
        }
      }
    });
  }

  private void changeList(final String url, final Collection<String> roots) {
    final String selected = myBranch;
    if ((selected != null) && (selected.equals(url))) {
      final Object[] items = roots.toArray();
      Arrays.sort(items);
      setModelKeepSelection(new DefaultComboBoxModel(items));
    }
  }

  protected void selectionChanged(final Object selection) {
  }

  private void setModelKeepSelection(final DefaultComboBoxModel model) {
    final String selected = (String) getSelected();
    setModel(model);
    if (selected != null) {
      final int size = model.getSize();
      for (int i = 0; i < size; i++) {
        final String current = (String) model.getElementAt(i);
        if (selected.equals(current)) {
          model.setSelectedItem(current);
        }
      }
    }
  }

  // comes from highlight branches action
  public void consume(final String s) {
    if ((myBranch != null) && (myBranch.equals(s))) {
      return;
    }
    myBranch = s;
    setModelKeepSelection(loadItems(myBranch));
  }

  private DefaultComboBoxModel loadItems(final String s) {
    if (s == null) {
      return SelectBranchAction.EMPTY;
    }
    final Set<String> items = SvnBranchMapperManager.getInstance().get(s);
    if (items == null) {
      return SelectBranchAction.EMPTY;
    }
    final String[] itemsArray = ArrayUtil.toStringArray(items);
    Arrays.sort(itemsArray);
    return new DefaultComboBoxModel(itemsArray);
  }

  protected ComboBoxModel createModel() {
    return loadItems(myBranch);
  }

  public void deactivate() {
    myMessageBusConnection.disconnect();
  }

  public String get() {
    return (String)getSelected();
  }

}
