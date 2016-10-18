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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.QuantitySelection;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
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
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.MoreAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.BasePageEngine;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.util.containers.ContainerUtil.newHashMap;
import static com.intellij.util.containers.ContainerUtilRt.emptyList;
import static com.intellij.util.containers.ContainerUtilRt.newArrayList;
import static com.intellij.util.containers.ContainerUtilRt.newHashSet;
import static java.util.Collections.singletonList;
import static java.util.Collections.synchronizedMap;
import static org.jetbrains.idea.svn.integrate.LoadRecentBranchRevisions.getBunchSize;
import static org.jetbrains.idea.svn.integrate.LoadRecentBranchRevisions.loadChangeLists;

public class ToBeMergedDialog extends DialogWrapper {
  public static final int MERGE_ALL_CODE = 222;
  private final JPanel myPanel;
  @NotNull private final MergeContext myMergeContext;
  private final PageEngine<List<CommittedChangeList>> myListsEngine;
  private TableView<CommittedChangeList> myRevisionsList;
  private RepositoryChangesBrowser myRepositoryChangesBrowser;
  private Splitter mySplitter;

  private final QuantitySelection<Long> myWiseSelection;

  private final Set<Change> myAlreadyMerged;
  private final MergeChecker myMergeChecker;
  private final boolean myAllStatusesCalculated;
  private volatile boolean myAllListsLoaded;

  private final Map<Long, ListMergeStatus> myStatusMap;
  private ToBeMergedDialog.MoreXAction myMore100Action;
  private ToBeMergedDialog.MoreXAction myMore500Action;

  public ToBeMergedDialog(@NotNull MergeContext mergeContext,
                          @NotNull List<CommittedChangeList> lists,
                          final String title,
                          @NotNull MergeChecker mergeChecker,
                          boolean allStatusesCalculated,
                          boolean allListsLoaded) {
    super(mergeContext.getProject(), true);
    myMergeContext = mergeContext;
    myAllListsLoaded = allListsLoaded;
    myStatusMap = synchronizedMap(newHashMap());
    myMergeChecker = mergeChecker;
    myAllStatusesCalculated = allStatusesCalculated;
    setTitle(title);

    // Paging is not used - "Load Xxx" buttons load corresponding new elements and add them to the end of the table. Single (first) page is
    // always used.
    myListsEngine = new BasePageEngine<>(lists, lists.size());

    myPanel = new JPanel(new BorderLayout());
    myWiseSelection = new QuantitySelection<>(allStatusesCalculated);
    myAlreadyMerged = newHashSet();
    setOKButtonText("Merge Selected");
    initUI();
    init();
    enableLoadButtons();

    if (!myAllStatusesCalculated) {
      refreshListStatus(lists);
    }
  }

  private void enableLoadButtons() {
    myMore100Action.setVisible(!myAllListsLoaded);
    myMore500Action.setVisible(!myAllListsLoaded);
    myMore100Action.setEnabled(!myAllListsLoaded);
    myMore500Action.setEnabled(!myAllListsLoaded);
  }

  public void setAllListsLoaded() {
    myAllListsLoaded = true;
    enableLoadButtons();
  }

  public long getLastNumber() {
    // in current implementation we just have one page with all loaded change lists - myListsEngine.getCurrent()
    CommittedChangeList lastLoadedList = getLastItem(myListsEngine.getCurrent());

    return lastLoadedList != null ? lastLoadedList.getNumber() : 0;
  }

  public void addMoreLists(final List<CommittedChangeList> list) {
    myListsEngine.getCurrent().addAll(list);
    myRevisionsList.revalidate();
    myRevisionsList.repaint();
    myMore100Action.setEnabled(true);
    myMore500Action.setEnabled(true);
    // TODO: This is necessary because myMore500Action was hidden in MoreXAction.actionPerformed()
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
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      int cnt = 10;
      for (CommittedChangeList list : changeLists) {
        // at the moment we calculate only "merged" since we don;t have branch copy point
        myStatusMap.put(list.getNumber(), toListMergeStatus(myMergeChecker.checkList((SvnChangeList)list)));

        --cnt;
        if (cnt <= 0) {
          ApplicationManager.getApplication().invokeLater(() -> {
            myRevisionsList.revalidate();
            myRevisionsList.repaint();
          });
          cnt = 10;
        }
      }
      myRevisionsList.revalidate();
      myRevisionsList.repaint();
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
    if (myAllStatusesCalculated) {
      return new Action[]{getOKAction(), new DialogWrapperAction("Merge All") {
        @Override
        protected void doAction(ActionEvent e) {
          close(MERGE_ALL_CODE);
        }
      }, getCancelAction()};
    }
    else {
      return super.createActions();
    }
  }

  @NotNull
  public List<CommittedChangeList> getSelected() {
    List<CommittedChangeList> result = newArrayList();
    result.addAll(myListsEngine.getCurrent());
    while (myListsEngine.hasNext()) {
      result.addAll(myListsEngine.next());
    }

    Set<Long> selected = myWiseSelection.getSelected();
    Set<Long> unselected = myWiseSelection.getUnselected();
    // todo: can be made faster
    Predicate<CommittedChangeList> removeCondition =
      myWiseSelection.areAllSelected() ? list -> unselected.contains(list.getNumber()) : list -> !selected.contains(list.getNumber());
    result.removeIf(removeCondition);
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
        List<CommittedChangeList> changeLists = myRevisionsList.getSelectedObjects();
        myRepositoryChangesBrowser.setChangesToDisplay(emptyList());
        myAlreadyMerged.clear();
        if (!changeLists.isEmpty()) {
          List<SvnChangeList> svnChangeLists = findAll(changeLists, SvnChangeList.class);

          for (SvnChangeList svnChangeList : svnChangeLists) {
            myAlreadyMerged.addAll(getAlreadyMergedPaths(svnChangeList));
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

    myRepositoryChangesBrowser =
      new RepositoryChangesBrowser(myMergeContext.getProject(), Collections.<CommittedChangeList>emptyList(), emptyList(), null);
    myRepositoryChangesBrowser.getDiffAction()
      .registerCustomShortcutSet(myRepositoryChangesBrowser.getDiffAction().getShortcutSet(), myRevisionsList);
    setChangesDecorator();
    mySplitter.setSecondComponent(myRepositoryChangesBrowser);
    mySplitter.setDividerWidth(2);

    addRevisionListListeners();

    myPanel.add(mySplitter, BorderLayout.CENTER);
  }

  @NotNull
  private List<Change> getAlreadyMergedPaths(@NotNull SvnChangeList svnChangeList) {
    Collection<String> notMerged = myMergeChecker.getNotMergedPaths(svnChangeList);

    return isEmpty(notMerged) ? emptyList() : svnChangeList.getAffectedPaths().stream()
      .filter(path -> !notMerged.contains(path))
      .map(svnChangeList::getByPath)
      .collect(Collectors.toList());
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
            SvnChangeList changeList = (SvnChangeList)myRevisionsList.getModel().getValueAt(idx, 0);

            toggleInclusion(changeList);
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
          List<CommittedChangeList> selected = myRevisionsList.getSelectedObjects();
          if (!selected.isEmpty()) {
            selected.stream()
              .filter(SvnChangeList.class::isInstance)
              .forEach(ToBeMergedDialog.this::toggleInclusion);
            myRevisionsList.repaint();
            e.consume();
          }
        }
      }
    });
  }

  private void toggleInclusion(@NotNull CommittedChangeList list) {
    long number = list.getNumber();

    if (myWiseSelection.isSelected(number)) {
      myWiseSelection.remove(number);
    }
    else {
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
      // TODO: This setVisible() is necessary because MoreXAction shows "Loading..." text when disabled
      myMore500Action.setVisible(false);
      myMore100Action.setEnabled(false);
      myMore500Action.setEnabled(false);

      new LoadChangeListsTask(getLastNumber(), myQuantity).queue();
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

  private class LoadChangeListsTask extends Task.Backgroundable {

    private final long myStartNumber;
    private final int myQuantity;
    private List<CommittedChangeList> myLists;
    private boolean myIsLastListLoaded;

    public LoadChangeListsTask(long startNumber, int quantity) {
      super(myMergeContext.getProject(), "Loading recent " + myMergeContext.getBranchName() + " revisions", true);
      myStartNumber = startNumber;
      myQuantity = quantity;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        Pair<List<CommittedChangeList>, Boolean> loadResult = loadChangeLists(myMergeContext, myStartNumber, getBunchSize(myQuantity));

        myLists = loadResult.first;
        myIsLastListLoaded = loadResult.second;
      }
      catch (VcsException e) {
        setEmptyData();
        PopupUtil.showBalloonForActiveComponent(e.getMessage(), MessageType.ERROR);
      }
    }

    @Override
    public void onCancel() {
      setEmptyData();
      updateDialog();
    }

    @Override
    public void onSuccess() {
      updateDialog();
    }

    private void setEmptyData() {
      myLists = emptyList();
      myIsLastListLoaded = false;
    }

    private void updateDialog() {
      addMoreLists(myLists);
      if (myIsLastListLoaded) {
        setAllListsLoaded();
      }
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
      myRenderer = new CommittedChangeListRenderer(myMergeContext.getProject(), singletonList(list -> {
        ListMergeStatus status = myAllStatusesCalculated
                                 ? ListMergeStatus.NOT_MERGED
                                 : ObjectUtils.notNull(myStatusMap.get(list.getNumber()), ListMergeStatus.REFRESHING);

        return status.getIcon();
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

  private static final ColumnInfo FAKE_COLUMN = new ColumnInfo<CommittedChangeList, CommittedChangeList>("fake column") {
    @Override
    public CommittedChangeList valueOf(CommittedChangeList committedChangeList) {
      return committedChangeList;
    }
  };
}
