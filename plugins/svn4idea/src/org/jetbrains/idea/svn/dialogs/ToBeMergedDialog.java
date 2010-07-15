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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.QuantitySelection;
import com.intellij.openapi.vcs.SelectionResult;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListDecorator;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListRenderer;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.issueLinks.AbstractBaseTagMouseListener;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class ToBeMergedDialog extends DialogWrapper {
  public static final int MERGE_ALL_CODE = 222;
  private final JPanel myPanel;
  private final Project myProject;
  private final PageEngine<List<CommittedChangeList>> myListsEngine;
  private TableView<CommittedChangeList> myRevisionsList;
  private RepositoryChangesBrowser myRepositoryChangesBrowser;
  private Splitter mySplitter;

  private final QuantitySelection<Long> myWiseSelection;

  private final Set<Change> myAlreadyMerged;
  private final MergeChecker myMergeChecker;

  public ToBeMergedDialog(final Project project, final List<CommittedChangeList> lists, final String title, final MergeChecker mergeChecker) {
    super(project, true);
    myMergeChecker = mergeChecker;
    setTitle(title);
    myProject = project;

    // todo removing pages for a while
    //myListsEngine = new BasePageEngine<CommittedChangeList>(lists, ourPageSize);
    myListsEngine = new BasePageEngine<CommittedChangeList>(lists, lists.size());

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
    final ListSelectionListener selectionListener = new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final List objects = myRevisionsList.getSelectedObjects();
        myRepositoryChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
        myAlreadyMerged.clear();
        if (objects != null && (!objects.isEmpty())) {
          final List<CommittedChangeList> lists =
            ObjectsConvertor.convert(objects, new Convertor<Object, CommittedChangeList>() {
              public CommittedChangeList convert(Object o) {
                if (o instanceof CommittedChangeList) {
                  final CommittedChangeList cl = (CommittedChangeList)o;
                  final Collection<String> notMerged = myMergeChecker.getNotMergedPaths(cl.getNumber());
                  final SvnChangeList svnList = (SvnChangeList)cl;

                  final Collection<String> forCheck = new HashSet<String>();
                  forCheck.addAll(svnList.getAddedPaths());
                  forCheck.addAll(svnList.getChangedPaths());
                  forCheck.addAll(svnList.getDeletedPaths());
                  for (String path : forCheck) {
                    if ((notMerged != null) && (!notMerged.isEmpty()) && !notMerged.contains(path)) {
                      myAlreadyMerged.add(((SvnChangeList)cl).getByPath(path));
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
    };
    final MyListCellRenderer listCellRenderer = new MyListCellRenderer();
    myRevisionsList = new TableView<CommittedChangeList>() {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        return listCellRenderer;
      }

      @Override
      public void valueChanged(ListSelectionEvent e) {
        super.valueChanged(e);
        selectionListener.valueChanged(e);
      }
    };
    new TableViewSpeedSearch(myRevisionsList) {
      @Override
      protected String getElementText(Object element) {
        if (element instanceof CommittedChangeList) {
          return ((CommittedChangeList) element).getComment();
        }
        return null;
      }
    };
    final ListTableModel<CommittedChangeList> flatModel = new ListTableModel<CommittedChangeList>(FAKE_COLUMN);
    myRevisionsList.setModel(flatModel);
    myRevisionsList.setTableHeader(null);
    myRevisionsList.setShowGrid(false);
    final AbstractBaseTagMouseListener mouseListener = new AbstractBaseTagMouseListener() {
      @Override
      protected Object getTagAt(MouseEvent e) {
        Object tag = null;
        JTable table = (JTable)e.getSource();
        int row = table.rowAtPoint(e.getPoint());
        int column = table.columnAtPoint(e.getPoint());
        listCellRenderer.customizeCellRenderer(table, table.getValueAt(row, column), table.isRowSelected(row), false, row, column);
        final ColoredTreeCellRenderer renderer = listCellRenderer.myRenderer;
        final Rectangle rc = table.getCellRect(row, column, false);
        int index = renderer.findFragmentAt(e.getPoint().x - rc.x);
        if (index >= 0) {
          tag = renderer.getFragmentTag(index);
        }
        return tag;
      }
    };
    mouseListener.install(myRevisionsList);

    final PagedListWithActions.InnerComponentManager<CommittedChangeList> listsManager =
      new PagedListWithActions.InnerComponentManager<CommittedChangeList>() {
        public Component getComponent() {
          return myRevisionsList;
        }
        public void setData(List<CommittedChangeList> committedChangeLists) {
          flatModel.setItems(committedChangeLists);
          flatModel.fireTableDataChanged();
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

    flatModel.setItems(myListsEngine.getCurrent());
    flatModel.fireTableDataChanged();

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
        final int idx = myRevisionsList.rowAtPoint(e.getPoint());
        if (idx >= 0) {
          final Rectangle baseRect = myRevisionsList.getCellRect(idx, 0, false);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (baseRect.contains(e.getPoint())) {
            final SvnChangeList changeList = (SvnChangeList)myRevisionsList.getModel().getValueAt(idx, 0);
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
          final List selected = myRevisionsList.getSelectedObjects();
          if (selected == null || selected.isEmpty()) return;

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

  private class MySelectAll extends DumbAwareAction {
    private MySelectAll() {
      super("Select All", "Select All", IconLoader.getIcon("/actions/selectall.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myWiseSelection.setAll();
      myRevisionsList.repaint();
    }
  }

  private class MyUnselectAll extends DumbAwareAction {
    private MyUnselectAll() {
      super("Unselect All", "Unselect All", IconLoader.getIcon("/actions/unselectall.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myWiseSelection.clearAll();
      myRevisionsList.repaint();
    }
  }

  private class MyListCellRenderer implements TableCellRenderer {
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

    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      myPanel.removeAll();
      myPanel.setBackground(null);
      myRenderer.clear();
      myRenderer.setBackground(null);

      // 7-8, a hack
      if (value instanceof SvnChangeList) {
        final SvnChangeList changeList = (SvnChangeList)value;
        myRenderer.renderChangeList(table, changeList);

        final Color bg = selected ? UIUtil.getTableSelectionBackground() : UIUtil.getTableBackground();
        final Color fg = selected ? UIUtil.getTableSelectionForeground() : UIUtil.getTableForeground();

        myRenderer.setBackground(bg);
        myRenderer.setForeground(fg);
        myCheckBox.setBackground(bg);
        myCheckBox.setForeground(fg);

        myPanel.setBackground(bg);
        myPanel.setForeground(fg);

        myCheckBox.setSelected(myWiseSelection.isSelected(changeList.getNumber()));
        myPanel.add(myCheckBox, BorderLayout.WEST);
        myPanel.add(myRenderer, BorderLayout.CENTER);
      }
    }

      public final Component getTableCellRendererComponent(
        JTable table,
        Object value,
        boolean isSelected,
        boolean hasFocus,
        int row,
        int column
      ){
        customizeCellRenderer(table, value, isSelected, hasFocus, row, column);
        return myPanel;
      }
  }

  private final static int ourPageSize = 30;

  private static final ColumnInfo FAKE_COLUMN = new ColumnInfo<CommittedChangeList, CommittedChangeList>("fake column"){
    @Override
    public CommittedChangeList valueOf(CommittedChangeList committedChangeList) {
      return committedChangeList;
    }
  };
}
