// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListRenderer;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowser;
import com.intellij.openapi.vcs.changes.issueLinks.AbstractBaseTagMouseListener;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.mergeinfo.ListMergeStatus;
import org.jetbrains.idea.svn.mergeinfo.MergeCheckResult;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser.collectChanges;
import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.isEmpty;
import static java.util.Collections.singletonList;
import static java.util.Collections.synchronizedMap;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnBundle.messagePointer;
import static org.jetbrains.idea.svn.integrate.MergeCalculatorTask.getBunchSize;
import static org.jetbrains.idea.svn.integrate.MergeCalculatorTask.loadChangeLists;

public class ToBeMergedDialog extends DialogWrapper {
  public static final int MERGE_ALL_CODE = 222;
  private final JPanel myPanel;
  @NotNull private final MergeContext myMergeContext;
  @NotNull private final ListTableModel<SvnChangeList> myRevisionsModel;
  private TableView<SvnChangeList> myRevisionsList;
  private CommittedChangesBrowser myRepositoryChangesBrowser;
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
                          @NotNull List<SvnChangeList> changeLists,
                          @NotNull MergeChecker mergeChecker,
                          boolean allStatusesCalculated,
                          boolean allListsLoaded) {
    super(mergeContext.getProject(), true);
    myMergeContext = mergeContext;
    myAllListsLoaded = allListsLoaded;
    myStatusMap = synchronizedMap(new HashMap<>());
    myMergeChecker = mergeChecker;
    myAllStatusesCalculated = allStatusesCalculated;
    setTitle(myMergeContext.getMergeTitle());

    myRevisionsModel = new ListTableModel<>(new ColumnInfo[]{FAKE_COLUMN}, changeLists);
    myPanel = new JPanel(new BorderLayout());
    myWiseSelection = new QuantitySelection<>(allStatusesCalculated);
    myAlreadyMerged = new HashSet<>();
    setOKButtonText(message("button.merge.selected"));
    initUI();
    init();
    enableLoadButtons();

    if (!myAllStatusesCalculated) {
      refreshListStatus(changeLists);
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
    int totalRows = myRevisionsModel.getRowCount();

    return totalRows > 0 ? myRevisionsModel.getItem(totalRows - 1).getNumber() : 0;
  }

  public void addMoreLists(@NotNull List<SvnChangeList> changeLists) {
    myRevisionsModel.addRows(changeLists);
    myRevisionsList.revalidate();
    myRevisionsList.repaint();
    myMore100Action.setEnabled(true);
    myMore500Action.setEnabled(true);
    // TODO: This is necessary because myMore500Action was hidden in MoreXAction.actionPerformed()
    myMore500Action.setVisible(true);
    refreshListStatus(changeLists);
  }

  private boolean myDisposed;

  @Override
  protected void dispose() {
    super.dispose();
    myDisposed = true;
  }

  private void refreshListStatus(@NotNull final List<SvnChangeList> changeLists) {
    if (myDisposed) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      int cnt = 10;
      for (SvnChangeList list : changeLists) {
        // at the moment we calculate only "merged" since we don;t have branch copy point
        myStatusMap.put(list.getNumber(), toListMergeStatus(myMergeChecker.checkList(list)));

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
  private static ListMergeStatus toListMergeStatus(@NotNull MergeCheckResult mergeCheckResult) {
    return switch (mergeCheckResult) {
      case MERGED -> ListMergeStatus.MERGED;
      case NOT_EXISTS -> ListMergeStatus.ALIEN;
      default -> ListMergeStatus.REFRESHING;
    };
  }

  @Override
  protected Action @NotNull [] createActions() {
    if (myAllStatusesCalculated) {
      return new Action[]{getOKAction(), new DialogWrapperAction(message("button.merge.all")) {
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
  public List<SvnChangeList> getSelected() {
    Set<Long> selected = myWiseSelection.getSelected();
    Set<Long> unselected = myWiseSelection.getUnselected();
    // todo: can be made faster
    Condition<SvnChangeList> filter =
      myWiseSelection.areAllSelected() ? list -> !unselected.contains(list.getNumber()) : list -> selected.contains(list.getNumber());

    return filter(myRevisionsModel.getItems(), filter);
  }

  @Override
  protected String getDimensionServiceKey() {
    // TODO: Currently class is in other package, but key to persist dimension is preserved.
    // TODO: Rename later to some "neutral" constant not to be confusing relative to current class location.
    return "org.jetbrains.idea.svn.dialogs.ToBeMergedDialog";
  }

  private void initUI() {
    final ListSelectionListener selectionListener = e -> {
      List<SvnChangeList> changeLists = myRevisionsList.getSelectedObjects();

      myAlreadyMerged.clear();
      for (SvnChangeList changeList : changeLists) {
        myAlreadyMerged.addAll(getAlreadyMergedPaths(changeList));
      }
      myRepositoryChangesBrowser.setChangesToDisplay(collectChanges(changeLists, false));

      mySplitter.doLayout();
      myRepositoryChangesBrowser.repaint();
    };
    final MyListCellRenderer listCellRenderer = new MyListCellRenderer();
    myRevisionsList = new TableView<>() {
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
    TableViewSpeedSearch<SvnChangeList> search = new TableViewSpeedSearch<>(myRevisionsList, null) {
      @Override
      protected String getItemText(@NotNull SvnChangeList element) {
        return element.getComment();
      }
    };
    search.setupListeners();
    myRevisionsList.setModelAndUpdateColumns(myRevisionsModel);
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

    myMore100Action = new MoreXAction(100);
    myMore500Action = new MoreXAction(500);

    BorderLayoutPanel panel = JBUI.Panels.simplePanel()
      .addToCenter(ScrollPaneFactory.createScrollPane(myRevisionsList))
      .addToTop(createToolbar().getComponent());

    mySplitter = new Splitter(false, 0.7f);
    mySplitter.setFirstComponent(panel);

    myRepositoryChangesBrowser = new CommittedChangesBrowser(myMergeContext.getProject());
    myRepositoryChangesBrowser.getDiffAction().registerCustomShortcutSet(myRevisionsList, null);
    myRepositoryChangesBrowser.setChangeNodeDecorator(createChangesDecorator());
    mySplitter.setSecondComponent(myRepositoryChangesBrowser);
    mySplitter.setDividerWidth(2);

    addRevisionListListeners();

    myPanel.add(mySplitter, BorderLayout.CENTER);
  }

  @NotNull
  private ActionToolbar createToolbar() {
    DefaultActionGroup actions = new DefaultActionGroup(new MySelectAll(), new MyUnselectAll(), myMore100Action, myMore500Action);

    return ActionManager.getInstance().createActionToolbar("SvnToBeMerged", actions, true);
  }

  @NotNull
  private List<Change> getAlreadyMergedPaths(@NotNull SvnChangeList svnChangeList) {
    Collection<String> notMerged = myMergeChecker.getNotMergedPaths(svnChangeList);

    return isEmpty(notMerged) ? ContainerUtil.emptyList() : svnChangeList.getAffectedPaths().stream()
      .filter(path -> !notMerged.contains(path))
      .map(svnChangeList::getByPath)
      .collect(Collectors.toList());
  }

  private ChangeNodeDecorator createChangesDecorator() {
    return new ChangeNodeDecorator() {
      @Override
      public void decorate(@NotNull Change change, @NotNull SimpleColoredComponent component, boolean isShowFlatten) {
      }

      @Override
      public void preDecorate(@NotNull Change change, @NotNull ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
        if (myAlreadyMerged.contains(change)) {
          renderer.append(" [" + message("label.already.merged") + "] ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
      }
    };
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
            toggleInclusion(myRevisionsModel.getRowValue(idx));
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
          List<SvnChangeList> selected = myRevisionsList.getSelectedObjects();
          if (!selected.isEmpty()) {
            selected.forEach(ToBeMergedDialog.this::toggleInclusion);
            myRevisionsList.repaint();
            e.consume();
          }
        }
      }
    });
  }

  private void toggleInclusion(@NotNull SvnChangeList list) {
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

  private final class MoreXAction extends MoreAction {
    private final int myQuantity;

    private MoreXAction(final int quantity) {
      super(message("button.load.quantity", quantity));
      myQuantity = quantity;
    }

    @Override
    public void perform() {
      // TODO: This setVisible() is necessary because MoreXAction shows "Loading..." text when disabled
      myMore500Action.setVisible(false);
      myMore100Action.setEnabled(false);
      myMore500Action.setEnabled(false);

      new LoadChangeListsTask(getLastNumber(), myQuantity).queue();
    }
  }

  private final class MySelectAll extends DumbAwareAction {
    private MySelectAll() {
      super(
        messagePointer("action.Subversion.SelectAllRevisions.text"),
        messagePointer("action.Subversion.SelectAllRevisions.description"),
        AllIcons.Actions.Selectall
      );
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myWiseSelection.setAll();
      myRevisionsList.repaint();
    }
  }

  private final class MyUnselectAll extends DumbAwareAction {
    private MyUnselectAll() {
      super(
        messagePointer("action.Subversion.UnselectAllRevisions.text"),
        messagePointer("action.Subversion.UnselectAllRevisions.description"),
        AllIcons.Actions.Unselectall
      );
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myWiseSelection.clearAll();
      myRevisionsList.repaint();
    }
  }

  private class LoadChangeListsTask extends Task.Backgroundable {

    private final long myStartNumber;
    private final int myQuantity;
    private List<SvnChangeList> myLists;
    private boolean myIsLastListLoaded;

    LoadChangeListsTask(long startNumber, int quantity) {
      super(myMergeContext.getProject(), message("progress.title.loading.recent.branch.revisions", myMergeContext.getBranchName()), true);
      myStartNumber = startNumber;
      myQuantity = quantity;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        Pair<List<SvnChangeList>, Boolean> loadResult = loadChangeLists(myMergeContext, myStartNumber, getBunchSize(myQuantity));

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
      myLists = ContainerUtil.emptyList();
      myIsLastListLoaded = false;
    }

    private void updateDialog() {
      addMoreLists(myLists);
      if (myIsLastListLoaded) {
        setAllListsLoaded();
      }
    }
  }

  private final class MyListCellRenderer implements TableCellRenderer {
    private final JPanel myPanel;
    private final CommittedChangeListRenderer myRenderer;
    private final JCheckBox myCheckBox;

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

    private void customizeCellRenderer(JTable table, Object value, boolean selected) {
      myPanel.removeAll();
      myPanel.setBackground(null);
      myRenderer.clear();
      myRenderer.setBackground(null);

      // 7-8, a hack
      if (value instanceof SvnChangeList changeList) {
        myRenderer.renderChangeList(table, changeList);

        final Color bg = selected ? UIUtil.getTableSelectionBackground(true) : UIUtil.getTableBackground();
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
    public Component getTableCellRendererComponent(
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

  private static final ColumnInfo<SvnChangeList, SvnChangeList> FAKE_COLUMN = new ColumnInfo<>("") {
    @Override
    public SvnChangeList valueOf(SvnChangeList changeList) {
      return changeList;
    }
  };
}
