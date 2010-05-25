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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListDecorator;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListRenderer;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class ToBeMergedDialog extends DialogWrapper {
  public static final int MERGE_ALL_CODE = 222;
  private final JPanel myPanel;
  private final Project myProject;
  private final PageEngine<List<CommittedChangeList>> myListsEngine;
  private JList myRevisionsList;
  private RepositoryChangesBrowser myRepositoryChangesBrowser;
  private Splitter mySplitter;

  private final QuantitySelection<Long> myWiseSelection;
  private final MergeChecker myMergeChecker;

  private final Set<Change> myAlreadyMerged;

  public ToBeMergedDialog(final Project project, final List<CommittedChangeList> lists, final String title, MergeChecker mergeChecker) {
    super(project, true);
    myMergeChecker = mergeChecker;
    setTitle(title);
    myProject = project;

    myListsEngine = new BasePageEngine<CommittedChangeList>(lists, ourPageSize);

    myPanel = new JPanel(new BorderLayout());
    myWiseSelection = new QuantitySelection<Long>(true);
    myAlreadyMerged = new HashSet<Change>();
    setOKButtonText("Merge Selected");
    initUI();
    init();
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), new DialogWrapperAction("Merge All") {
      @Override
      protected void doAction(ActionEvent e) {
        close(MERGE_ALL_CODE);
      }
    }, getCancelAction()};
  }

  public List<CommittedChangeList> getSelected() {
    final SelectionResult<Long> selected = myWiseSelection.getSelected();
    final SelectionResult<Long> unselected = myWiseSelection.getUnselected();

    final List<CommittedChangeList> result = new LinkedList<CommittedChangeList>();
    result.addAll(myListsEngine.getCurrent());
    while (myListsEngine.hasNext()) {
      result.addAll(myListsEngine.next());
    }
    // todo: can be made faster
    if (selected.isAll()) {
      final Set<Long> excluded = unselected.getMarked();
      for (Iterator<CommittedChangeList> iterator = result.iterator(); iterator.hasNext();) {
        final CommittedChangeList list = iterator.next();
        if (excluded.contains(list.getNumber())) iterator.remove();
      }
    } else {
      final Set<Long> included = selected.getMarked();
      for (Iterator<CommittedChangeList> iterator = result.iterator(); iterator.hasNext();) {
        final CommittedChangeList list = iterator.next();
        if (! included.contains(list.getNumber())) iterator.remove();
      }
    }
    return result;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "org.jetbrains.idea.svn.dialogs.ToBeMergedDialog";
  }

  private void initUI() {
    myRevisionsList = new JList();
    final PagedListWithActions.InnerComponentManager<CommittedChangeList> listsManager =
      new PagedListWithActions.InnerComponentManager<CommittedChangeList>() {
        public Component getComponent() {
          return myRevisionsList;
        }
        public void setData(List<CommittedChangeList> committedChangeLists) {
          myRevisionsList.setListData(ArrayUtil.toObjectArray(committedChangeLists));
        }
        public void refresh() {
          myRevisionsList.revalidate();
          myRevisionsList.repaint();
        }
      };
    final PagedListWithActions<CommittedChangeList> byRevisions =
      new PagedListWithActions<CommittedChangeList>(myListsEngine, listsManager, new MySelectAll(), new MyUnselectAll());

    mySplitter = new Splitter(false, 0.7f);
    mySplitter.setFirstComponent(byRevisions.getComponent());
    myRevisionsList.setListData(ArrayUtil.toObjectArray(myListsEngine.getCurrent()));

    myRevisionsList.setCellRenderer(new MyListCellRenderer());

    myRepositoryChangesBrowser = new RepositoryChangesBrowser(myProject, Collections.<CommittedChangeList>emptyList(), Collections.<Change>emptyList(), null);
    myRepositoryChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myRevisionsList);
    setChangesDecorator();
    mySplitter.setSecondComponent(myRepositoryChangesBrowser);
    mySplitter.setDividerWidth(2);

    addRevisionListListeners();

    myPanel.add(mySplitter, BorderLayout.CENTER);
  }

  private void setChangesDecorator() {
    myRepositoryChangesBrowser.setDecorator(new ChangeNodeDecorator() {
      public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
      }
      public List<Pair<String, Stress>> stressPartsOfFileName(Change change, String parentPath) {
        return null;
      }
      public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
        if (myAlreadyMerged.contains(change)) {
          renderer.append(" [already merged] ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
      }
    });
  }

  private void addRevisionListListeners() {
    final int checkboxWidth = new JCheckBox().getPreferredSize().width;
    myRevisionsList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        final int idx = myRevisionsList.locationToIndex(e.getPoint());
        if (idx >= 0) {
          final Rectangle baseRect = myRevisionsList.getCellBounds(idx, idx);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (baseRect.contains(e.getPoint())) {
            final SvnChangeList changeList = (SvnChangeList)myRevisionsList.getModel().getElementAt(idx);
            final long number = changeList.getNumber();
            toggleInclusion(number);
            myRevisionsList.repaint(baseRect);
            e.consume();
          }
        }
      }
    });
    myRevisionsList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (KeyEvent.VK_SPACE == e.getKeyCode()) {
          final Object[] selected = myRevisionsList.getSelectedValues();
          if (selected == null || selected.length == 0) return;

          for (Object o : selected) {
            if (o instanceof SvnChangeList) {
              final SvnChangeList changeList = (SvnChangeList) o;
              toggleInclusion(changeList.getNumber());
            }
          }
          myRevisionsList.repaint();
          e.consume();
        }
      }
    });

    myRevisionsList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final Object[] objects = myRevisionsList.getSelectedValues();
        myRepositoryChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
        myAlreadyMerged.clear();
        if (objects != null && objects.length > 0) {
          final List<CommittedChangeList> lists =
            ObjectsConvertor.convert(Arrays.asList(objects), new Convertor<Object, CommittedChangeList>() {
              public CommittedChangeList convert(Object o) {
                if (o instanceof CommittedChangeList) {
                  final CommittedChangeList cl = (CommittedChangeList)o;
                  final Collection<String> notMerged = myMergeChecker.getNotMergedPaths(cl.getNumber());
                  final SvnChangeList svnList = (SvnChangeList) cl;

                  final Collection<String> forCheck = new HashSet<String>();
                  forCheck.addAll(svnList.getAddedPaths());
                  forCheck.addAll(svnList.getChangedPaths());
                  forCheck.addAll(svnList.getDeletedPaths());
                  for (String path : forCheck) {
                    if (! notMerged.contains(path)) {
                      myAlreadyMerged.add(((SvnChangeList) cl).getByPath(path));
                    }
                  }
                  return cl;
                }
                return null;
              }
            }, ObjectsConvertor.NOT_NULL);
          final List<Change> changes = CommittedChangesTreeBrowser.collectChanges(lists, false);
          myRepositoryChangesBrowser.setChangesToDisplay(changes);
        }

        mySplitter.doLayout();
        myRepositoryChangesBrowser.repaint();
      }
    });
  }

  private void toggleInclusion(final long number) {
    if (myWiseSelection.isSelected(number)) {
      myWiseSelection.remove(number);
    } else {
      myWiseSelection.add(number);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private class MySelectAll extends AnAction {
    private MySelectAll() {
      super("Select All", "Select All", IconLoader.getIcon("/actions/selectall.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myWiseSelection.setAll();
      myRevisionsList.repaint();
    }
  }

  private class MyUnselectAll extends AnAction {
    private MyUnselectAll() {
      super("Unselect All", "Unselect All", IconLoader.getIcon("/actions/unselectall.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myWiseSelection.clearAll();
      myRevisionsList.repaint();
    }
  }

  private class MyListCellRenderer extends ColoredListCellRenderer {
    private final JPanel myPanel;
    private final CommittedChangeListRenderer myRenderer;
    private JCheckBox myCheckBox;

    private MyListCellRenderer() {
      myPanel = new JPanel(new BorderLayout());
      myCheckBox = new JCheckBox();
      myCheckBox.setEnabled(true);
      myCheckBox.setSelected(true);
      myRenderer = new CommittedChangeListRenderer(myProject, Collections.<CommittedChangeListDecorator>emptyList());
    }

    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      myPanel.removeAll();
      // 7-8, a hack
      if (value instanceof SvnChangeList) {
        myRenderer.clear();
        myRenderer.setBackground(selected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
        myRenderer.setForeground(selected ? UIUtil.getListSelectionForeground() : UIUtil.getListForeground());
        final SvnChangeList changeList = (SvnChangeList)value;
        myRenderer.renderChangeList(list, changeList);
        
        myCheckBox.setBackground(myRenderer.getBackground());
        myCheckBox.setForeground(myRenderer.getForeground());
        myPanel.setBackground(myRenderer.getBackground());
        myCheckBox.setSelected(myWiseSelection.isSelected(changeList.getNumber()));
        myPanel.add(myCheckBox, BorderLayout.WEST);
        myPanel.add(myRenderer, BorderLayout.CENTER);
      }
    }

    @Override
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      super.getListCellRendererComponent(list, value, index, selected, hasFocus);
      return myPanel;
    }
  }

  private final static int ourPageSize = 20;
}
