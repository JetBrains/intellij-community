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
package org.jetbrains.idea.svn.integrate;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Pair;
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
import com.intellij.ui.ClickListener;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.table.TableView;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.MoreAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.dialogs.BasePageEngine;
import org.jetbrains.idea.svn.dialogs.MergeDialogI;
import org.jetbrains.idea.svn.dialogs.PageEngine;
import org.jetbrains.idea.svn.dialogs.PagedListWithActions;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.mergeinfo.ListMergeStatus;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class ToBeMergedDialog extends DialogWrapper implements MergeDialogI {
  public static final int MERGE_ALL_CODE = 222;
  private final JPanel myPanel;
  private final Project myProject;
  private final PageEngine<List<CommittedChangeList>> myListsEngine;
  private TableView<CommittedChangeList> myRevisionsList;
  private RepositoryChangesBrowser myRepositoryChangesBrowser;
  private Splitter mySplitter;

  private final QuantitySelection<Long> myWiseSelection;

  private final Set<Change> myAlreadyMerged;
  private final PairConsumer<Long, MergeDialogI> myMoreLoader;
  private final MergeChecker myMergeChecker;
  private final boolean myAlreadyCalculatedState;
  private volatile boolean myEverythingLoaded;

  private final Map<Long, ListMergeStatus> myStatusMap;
  private ToBeMergedDialog.MoreXAction myMore100Action;
  private ToBeMergedDialog.MoreXAction myMore500Action;

  public ToBeMergedDialog(final Project project,
                          final List<CommittedChangeList> lists,
                          final String title,
                          final MergeChecker mergeChecker,
                          final PairConsumer<Long, MergeDialogI> moreLoader) {
    super(project, true);
    myMoreLoader = moreLoader;
    myEverythingLoaded = moreLoader == null;
    myStatusMap = Collections.synchronizedMap(new HashMap<Long, ListMergeStatus>());
    myMergeChecker = mergeChecker;
    myAlreadyCalculatedState = moreLoader == null;
    setTitle(title);
    myProject = project;

    // Paging is not used - "Load Xxx" buttons load corresponding new elements and add them to the end of the table. Single (first) page is
    // always used.
    myListsEngine = new BasePageEngine<>(lists, lists.size());

    myPanel = new JPanel(new BorderLayout());
    myWiseSelection = new QuantitySelection<>(myEverythingLoaded);
    myAlreadyMerged = new HashSet<>();
    setOKButtonText("Merge Selected");
    initUI();
    init();
    enableMore();

    if (! myAlreadyCalculatedState) {
      refreshListStatus(lists);
    }
  }

  private void enableMore() {
    myMore100Action.setVisible(!myEverythingLoaded);
    myMore500Action.setVisible(!myEverythingLoaded);
    myMore100Action.setEnabled(!myEverythingLoaded);
    myMore500Action.setEnabled(! myEverythingLoaded);
  }

  @Override
  public void setEverythingLoaded(boolean everythingLoaded) {
    myEverythingLoaded = everythingLoaded;
    myMore100Action.setVisible(false);
    myMore500Action.setVisible(false);
  }

  @Override
  public long getLastNumber() {
    // in current implementation we just have one page with all loaded change lists - myListsEngine.getCurrent()
    CommittedChangeList lastLoadedList = ContainerUtil.getLastItem(myListsEngine.getCurrent());

    return lastLoadedList != null ? lastLoadedList.getNumber() : 0;
  }

  @Override
  public void addMoreLists(final List<CommittedChangeList> list) {
    myListsEngine.getCurrent().addAll(list);
    myRevisionsList.revalidate();
    myRevisionsList.repaint();
    myMore100Action.setEnabled(true);
    myMore500Action.setEnabled(true);
    myMore500Action.setVisible(true);
    refreshListStatus(list);
  }

  private boolean myDisposed;
  @Override
  protected void dispose() {
    super.dispose();
    myDisposed = true;
  }

  private void refreshListStatus(@NotNull final List<CommittedChangeList> changeLists) {
    if (myDisposed) return;
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        int cnt = 10;
        for (CommittedChangeList list : changeLists) {
          // at the moment we calculate only "merged" since we don;t have branch copy point
          myStatusMap.put(list.getNumber(), toListMergeStatus(myMergeChecker.checkList((SvnChangeList)list)));

          -- cnt;
          if (cnt <= 0) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                myRevisionsList.revalidate();
                myRevisionsList.repaint();
              }
            });
            cnt = 10;
          }
        }
        myRevisionsList.revalidate();
        myRevisionsList.repaint();
      }
    });
  }

  @NotNull
  private static ListMergeStatus toListMergeStatus(@NotNull SvnMergeInfoCache.MergeCheckResult mergeCheckResult) {
    ListMergeStatus result;

    switch (mergeCheckResult) {
      case MERGED:
        result = ListMergeStatus.MERGED;
        break;
      case NOT_EXISTS:
        result = ListMergeStatus.ALIEN;
        break;
      default:
        result = ListMergeStatus.REFRESHING;
        break;
    }

    return result;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    if (myAlreadyCalculatedState) {
      return new Action[]{getOKAction(), new DialogWrapperAction("Merge All") {
        @Override
        protected void doAction(ActionEvent e) {
          close(MERGE_ALL_CODE);
        }
      }, getCancelAction()};
    } else {
      return super.createActions();
    }
  }

  @NotNull
  public List<CommittedChangeList> getSelected() {
    final SelectionResult<Long> selected = myWiseSelection.getSelected();
    final SelectionResult<Long> unselected = myWiseSelection.getUnselected();

    final List<CommittedChangeList> result = new LinkedList<>();
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
    // TODO: Currently class is in other package, but key to persist dimension is preserved.
    // TODO: Rename later to some "neutral" constant not to be confusing relative to current class location.
    return "org.jetbrains.idea.svn.dialogs.ToBeMergedDialog";
  }

  private void initUI() {
    final ListSelectionListener selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        final List<CommittedChangeList> changeLists = myRevisionsList.getSelectedObjects();
        myRepositoryChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
        myAlreadyMerged.clear();
        if (!changeLists.isEmpty()) {
          final List<SvnChangeList> svnChangeLists = ContainerUtil.findAll(changeLists, SvnChangeList.class);

          for (SvnChangeList svnChangeList : svnChangeLists) {
            final Collection<String> notMerged = myMergeChecker.getNotMergedPaths(svnChangeList);

            if (!ContainerUtil.isEmpty(notMerged)) {
              for (String path : svnChangeList.getAffectedPaths()) {
                if (!notMerged.contains(path)) {
                  myAlreadyMerged.add(svnChangeList.getByPath(path));
                }
              }
            }
          }

          myRepositoryChangesBrowser.setChangesToDisplay(CommittedChangesTreeBrowser.collectChanges(svnChangeLists, false));
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
    myRevisionsList.setExpandableItemsEnabled(false);
    new TableViewSpeedSearch<CommittedChangeList>(myRevisionsList) {
      @Override
      protected String getItemText(@NotNull CommittedChangeList element) {
        return element.getComment();
      }
    };
    final ListTableModel<CommittedChangeList> flatModel = new ListTableModel<>(FAKE_COLUMN);
    myRevisionsList.setModelAndUpdateColumns(flatModel);
    myRevisionsList.setTableHeader(null);
    myRevisionsList.setShowGrid(false);
    final AbstractBaseTagMouseListener mouseListener = new AbstractBaseTagMouseListener() {
      @Override
      public Object getTagAt(@NotNull MouseEvent e) {
        JTable table = (JTable)e.getSource();
        int row = table.rowAtPoint(e.getPoint());
        int column = table.columnAtPoint(e.getPoint());
        if (row == -1 || column == -1) return null;
        listCellRenderer.customizeCellRenderer(table, table.getValueAt(row, column), table.isRowSelected(row));
        return listCellRenderer.myRenderer.getFragmentTagAt(e.getPoint().x - table.getCellRect(row, column, false).x);
      }
    };
    mouseListener.installOn(myRevisionsList);

    final PagedListWithActions.InnerComponentManager<CommittedChangeList> listsManager =
      new PagedListWithActions.InnerComponentManager<CommittedChangeList>() {
        @Override
        public Component getComponent() {
          return myRevisionsList;
        }
        @Override
        public void setData(List<CommittedChangeList> committedChangeLists) {
          flatModel.setItems(committedChangeLists);
          flatModel.fireTableDataChanged();
        }
        @Override
        public void refresh() {
          myRevisionsList.revalidate();
          myRevisionsList.repaint();
        }
      };
    myMore100Action = new MoreXAction(100);
    myMore500Action = new MoreXAction(500);
    final PagedListWithActions<CommittedChangeList> byRevisions =
      new PagedListWithActions<>(myListsEngine, listsManager, new MySelectAll(), new MyUnselectAll(),
                                 myMore100Action, myMore500Action);

    mySplitter = new Splitter(false, 0.7f);
    mySplitter.setFirstComponent(byRevisions.getComponent());

    flatModel.setItems(myListsEngine.getCurrent());
    flatModel.fireTableDataChanged();

    myRepositoryChangesBrowser = new RepositoryChangesBrowser(myProject, Collections.<CommittedChangeList>emptyList(), Collections.<Change>emptyList(), null);
    myRepositoryChangesBrowser.getDiffAction().registerCustomShortcutSet(myRepositoryChangesBrowser.getDiffAction().getShortcutSet(), myRevisionsList);
    setChangesDecorator();
    mySplitter.setSecondComponent(myRepositoryChangesBrowser);
    mySplitter.setDividerWidth(2);

    addRevisionListListeners();

    myPanel.add(mySplitter, BorderLayout.CENTER);
  }

  private void setChangesDecorator() {
    myRepositoryChangesBrowser.setDecorator(new ChangeNodeDecorator() {
      @Override
      public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
      }
      @Override
      public List<Pair<String, Stress>> stressPartsOfFileName(Change change, String parentPath) {
        return null;
      }
      @Override
      public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
        if (myAlreadyMerged.contains(change)) {
          renderer.append(" [already merged] ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
      }
    });
  }

  private void addRevisionListListeners() {
    final int checkboxWidth = new JCheckBox().getPreferredSize().width;
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        final int idx = myRevisionsList.rowAtPoint(e.getPoint());
        if (idx >= 0) {
          final Rectangle baseRect = myRevisionsList.getCellRect(idx, 0, false);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (baseRect.contains(e.getPoint())) {
            final SvnChangeList changeList = (SvnChangeList)myRevisionsList.getModel().getValueAt(idx, 0);

            toggleInclusion(changeList.getNumber());
            myRevisionsList.repaint(baseRect);
          }
        }
        return true;
      }
    }.installOn(myRevisionsList);

    myRevisionsList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (KeyEvent.VK_SPACE == e.getKeyCode()) {
          final List selected = myRevisionsList.getSelectedObjects();
          if (selected.isEmpty()) {
            return;
          }

          for (Object o : selected) {
            if (o instanceof SvnChangeList) {
              toggleInclusion(((SvnChangeList)o).getNumber());
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

  private class MoreXAction extends MoreAction {
    private final int myQuantity;

    private MoreXAction(final int quantity) {
      super("Load +" + quantity);
      myQuantity = quantity;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myMore500Action.setVisible(false);
      myMore100Action.setEnabled(false);
      myMore500Action.setEnabled(false);
      myMoreLoader.consume(Long.valueOf(myQuantity), ToBeMergedDialog.this);
    }
  }

  private class MySelectAll extends DumbAwareAction {
    private MySelectAll() {
      super("Select All", "Select All", AllIcons.Actions.Selectall);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myWiseSelection.setAll();
      myRevisionsList.repaint();
    }
  }

  private class MyUnselectAll extends DumbAwareAction {
    private MyUnselectAll() {
      super("Unselect All", "Unselect All", AllIcons.Actions.Unselectall);
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
      myRenderer = new CommittedChangeListRenderer(myProject, Collections.<CommittedChangeListDecorator>singletonList(
        new CommittedChangeListDecorator() {
          @Nullable
          @Override
          public Icon decorate(CommittedChangeList list) {
            ListMergeStatus status = myAlreadyCalculatedState
                                     ? ListMergeStatus.NOT_MERGED
                                     : ObjectUtils.notNull(myStatusMap.get(list.getNumber()), ListMergeStatus.REFRESHING);

            return status.getIcon();
          }
        }));
    }

    protected void customizeCellRenderer(JTable table, Object value, boolean selected) {
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

    @Override
    public final Component getTableCellRendererComponent(
      JTable table,
      Object value,
      boolean isSelected,
      boolean hasFocus,
      int row,
      int column
    ) {
      customizeCellRenderer(table, value, isSelected);
      return myPanel;
    }
  }

  private static final ColumnInfo FAKE_COLUMN = new ColumnInfo<CommittedChangeList, CommittedChangeList>("fake column"){
    @Override
    public CommittedChangeList valueOf(CommittedChangeList committedChangeList) {
      return committedChangeList;
    }
  };
}
