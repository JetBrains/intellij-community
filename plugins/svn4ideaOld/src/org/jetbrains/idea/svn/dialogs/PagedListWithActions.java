/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PagedListWithActions<T> {
  private final JPanel myPanel;
  private final PageEngine<List<T>> myEngine;
  private final InnerComponentManager<T> myComponentManager;
  private final AnAction[] myOtherActions;

  public PagedListWithActions(PageEngine<List<T>> engine,
                              final InnerComponentManager<T> componentManager,
                              final AnAction... otherActions) {
    myEngine = engine;
    myComponentManager = componentManager;
    myOtherActions = otherActions;
    myPanel = new JPanel(new BorderLayout());
    createView();
  }

  private void createView() {
    myComponentManager.setData(myEngine.getCurrent());
    //myList.setListData(ArrayUtil.toObjectArray(myEngine.getCurrent()));
    myPanel.add(ActionManager.getInstance().createActionToolbar("merge all", createListActions(), true).getComponent(), BorderLayout.NORTH);
    final JScrollPane scroll = ScrollPaneFactory
      .createScrollPane(myComponentManager.getComponent(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    myPanel.add(scroll, BorderLayout.CENTER);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  private ActionGroup createListActions() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new MyPrevious());
    group.add(new MyNext());
    group.addAll(myOtherActions);
    return group;
  }

  private class MyNext extends AnAction {
    private MyNext() {
      super("Next Page", "Next Page", IconLoader.getIcon("/actions/nextfile.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final List<T> data = myEngine.next();
      myComponentManager.setData(data);
      myComponentManager.refresh();
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myEngine.hasNext());
      e.getPresentation().setVisible(myEngine.hasNext());
    }
  }

  private class MyPrevious extends AnAction {
    private MyPrevious() {
      super("Previous Page", "Previous Page", IconLoader.getIcon("/actions/prevfile.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final List<T> data = myEngine.previous();
      myComponentManager.setData(data);
      myComponentManager.refresh();
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myEngine.hasPrevious());
      e.getPresentation().setVisible(myEngine.hasPrevious());
    }
  }

  public interface InnerComponentManager<T> {
    Component getComponent();
    void setData(List<T> list);
    void refresh();
  }
}
