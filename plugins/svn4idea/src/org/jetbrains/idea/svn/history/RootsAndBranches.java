/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.history;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.committed.*;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.JBUI;
import icons.SvnIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.AbstractIntegrateChangesAction;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.integrate.ChangeListsMergerFactory;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.integrate.SelectedChangeListsChecker;
import org.jetbrains.idea.svn.integrate.SelectedCommittedStuffChecker;
import org.jetbrains.idea.svn.mergeinfo.ListMergeStatus;
import org.jetbrains.idea.svn.mergeinfo.MergeInfoHolder;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RootsAndBranches implements CommittedChangeListDecorator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history.RootsAndBranches");

  @NotNull private final SvnVcs myVcs;
  @NotNull private final Project myProject;
  private final DecoratorManager myManager;
  private final RepositoryLocation myLocation;
  private JPanel myPanel;
  private final Map<String, SvnMergeInfoRootPanelManual> myMergePanels;
  private final Map<String, MergeInfoHolder> myHolders;

  private boolean myHighlightingOn;
  private JPanel myPanelWrapper;
  private final MergePanelFiltering myStrategy;
  private final FilterOutMerged myFilterMerged;
  private final FilterOutNotMerged myFilterNotMerged;
  private final FilterOutAlien myFilterAlien;
  private final IntegrateChangeListsAction myIntegrateAction;
  private final IntegrateChangeListsAction myUndoIntegrateChangeListsAction;
  private JComponent myToolbarComponent;

  private boolean myDisposed;

  private final WcInfoLoader myDataLoader;

  public static final Topic<Runnable> REFRESH_REQUEST = new Topic<>("REFRESH_REQUEST", Runnable.class);

  private MergeInfoHolder getHolder(final String key) {
    final MergeInfoHolder holder = myHolders.get(key);
    if (holder != null) {
      return holder;
    }
    return myHolders.get(key.endsWith(File.separator) ? key.substring(0, key.length() - 1) : key + File.separator);
  }

  private SvnMergeInfoRootPanelManual getPanelData(final String key) {
    final SvnMergeInfoRootPanelManual panel = myMergePanels.get(key);
    if (panel != null) {
      return panel;
    }
    return myMergePanels.get(key.endsWith(File.separator) ? key.substring(0, key.length() - 1) : key + File.separator);
  }

  public RootsAndBranches(@NotNull SvnVcs vcs, @NotNull DecoratorManager manager, final RepositoryLocation location) {
    myVcs = vcs;
    myProject = vcs.getProject();
    myManager = manager;
    myLocation = location;

    myDataLoader = new WcInfoLoader(myVcs, myLocation);

    myMergePanels = new HashMap<>();
    myHolders = new HashMap<>();

    myFilterMerged = new FilterOutMerged();
    myFilterNotMerged = new FilterOutNotMerged();
    myFilterAlien = new FilterOutAlien();
    myIntegrateAction = new IntegrateChangeListsAction(true);
    myUndoIntegrateChangeListsAction = new IntegrateChangeListsAction(false);

    myPanel = new JPanel(new GridBagLayout());
    createToolbar();
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.NONE, JBUI.insets(1), 0, 0);
    gb.insets = JBUI.insets(20, 1, 1, 1);
    myPanel.add(new JLabel("Loading..."), gb);

    myPanel.setPreferredSize(JBUI.size(200, 60));

    myManager.install(this);

    myStrategy = new MergePanelFiltering(getPanel());
  }

  public IntegrateChangeListsAction getIntegrateAction() {
    return myIntegrateAction;
  }

  public IntegrateChangeListsAction getUndoIntegrateAction() {
    return myUndoIntegrateChangeListsAction;
  }

  public boolean isHighlightingOn() {
    return myHighlightingOn;
  }

  public void reloadPanels() {
    final Map<Couple<String>, SvnMergeInfoRootPanelManual.InfoHolder> states = new HashMap<>();
    for (Map.Entry<String, SvnMergeInfoRootPanelManual> entry : myMergePanels.entrySet()) {
      final String localPath = entry.getKey();
      final WCInfoWithBranches wcInfo = entry.getValue().getWcInfo();
      states.put(Couple.of(localPath, wcInfo.getUrl().toString()), entry.getValue().getInfo());
    }
    createPanels(myLocation, () -> {
      for (Map.Entry<String, SvnMergeInfoRootPanelManual> entry : myMergePanels.entrySet()) {
        final String localPath = entry.getKey();
        final WCInfoWithBranches wcInfo = entry.getValue().getWcInfo();
        final Couple<String> key = Couple.of(localPath, wcInfo.getUrl().toString());
        final SvnMergeInfoRootPanelManual.InfoHolder infoHolder = states.get(key);
        if (infoHolder != null) {
          entry.getValue().initSelection(infoHolder);
        }
      }
    });
  }

  public void turnFromHereHighlighting() {
    myHighlightingOn = true;
    for (MergeInfoHolder holder : myHolders.values()) {
      holder.updateMixedRevisionsForPanel();
    }

    myManager.repaintTree();
  }

  public void turnOff() {
    myHighlightingOn = false;
    for (SvnMergeInfoRootPanelManual panelManual : myMergePanels.values()) {
      panelManual.setMixedRevisions(false);
    }

    myManager.repaintTree();
  }

  public Icon decorate(final CommittedChangeList list) {
    final ListMergeStatus status = getStatus(list, false);
    return (status == null) ? ListMergeStatus.ALIEN.getIcon() : status.getIcon();
  }

  private void createPanels(final RepositoryLocation location, final Runnable afterRefresh) {
    final Task.Backgroundable backgroundable = new Task.Backgroundable(myProject, "Subversion: loading working copies data..", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        final Map<String, SvnMergeInfoRootPanelManual> panels = new HashMap<>();
        final Map<String, MergeInfoHolder> holders = new HashMap<>();
        final List<WCInfoWithBranches> roots = myDataLoader.loadRoots();
        SwingUtilities.invokeLater(() -> {
          if (myDisposed) return;
          final JPanel mainPanel = prepareData(panels, holders, roots);

          myMergePanels.clear();
          myHolders.clear();
          myMergePanels.putAll(panels);
          myHolders.putAll(holders);

          if (myPanelWrapper != null) {
            myPanelWrapper.removeAll();
            if (myMergePanels.isEmpty()) {
              final JPanel emptyPanel = new JPanel(new GridBagLayout());
              final GridBagConstraints gb =
                new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 5, 0, 5), 0,
                                       0);
              final JLabel label = new JLabel("No Subversion 1.5 working copies\nof 1.5 repositories in the project");
              label.setUI(new MultiLineLabelUI());
              emptyPanel.add(label, gb);
              gb.fill = GridBagConstraints.HORIZONTAL;
              myPanelWrapper.add(emptyPanel, gb);
            }
            else {
              for (MergeInfoHolder holder : myHolders.values()) {
                holder.updateMixedRevisionsForPanel();
              }
              myPanelWrapper.add(mainPanel,
                                 new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                        new Insets(0, 0, 0, 0), 0, 0));
            }
            myPanelWrapper.repaint();
          }
          else {
            myPanel = mainPanel;
          }
          if (afterRefresh != null) {
            afterRefresh.run();
          }
        });
      }
    };
    ProgressManager.getInstance().run(backgroundable);
  }

  public void refreshByLists(final List<CommittedChangeList> committedChangeLists) {
    if (!committedChangeLists.isEmpty()) {
      final SvnChangeList svnList = (SvnChangeList)committedChangeLists.get(0);
      final String wcPath = svnList.getWcPath();
      if (wcPath != null) {
        final MergeInfoHolder holder = getHolder(wcPath);
        if (holder != null) {
          holder.refresh(true);
        }
      }
    }
  }

  private void createToolbar() {
    final DefaultActionGroup svnGroup = createActions();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("SvnRootsAndBranches", svnGroup, true);
    myToolbarComponent = actionToolbar.getComponent();
  }

  private JPanel prepareData(final Map<String, SvnMergeInfoRootPanelManual> panels, final Map<String, MergeInfoHolder> holders,
                             List<WCInfoWithBranches> roots) {
    final JPanel mainPanel = new JPanel(new GridBagLayout());
    boolean onlyOneRoot = roots.size() == 1;
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                                                         new Insets(0, 0, 0, 0), 0, 0);
    mainPanel.add(myToolbarComponent, gb);
    ++gb.gridy;

    for (final WCInfoWithBranches root : roots) {
      if (root == null) {
        continue;
      }
      final SvnMergeInfoRootPanelManual panel = new SvnMergeInfoRootPanelManual(myProject, wcInfoWithBranches -> {
        final WCInfoWithBranches newInfo =
          myDataLoader.reloadInfo(wcInfoWithBranches);
        if (newInfo == null) {
          // reload all items
          BackgroundTaskUtil.syncPublisher(myProject, SvnVcs.WC_CONVERTED).run();
          // do not reload right now
          return wcInfoWithBranches;
        }
        return newInfo;
      }, () -> {
        final MergeInfoHolder holder = getHolder(root.getPath());
        if (holder != null) {
          holder.refresh(false);
        }
      }, onlyOneRoot, root);
      panels.put(root.getPath(), panel);
      holders.put(root.getPath(), createHolder(panel));

      final JPanel contentPanel = panel.getContentPanel();
      mainPanel.add(contentPanel, gb);
      ++gb.gridy;
    }
    if (panels.size() == 1) {
      for (SvnMergeInfoRootPanelManual panel : panels.values()) {
        panel.setOnlyOneRoot(true);
      }
    }
    return mainPanel;
  }

  private DefaultActionGroup createActions() {
    final DefaultActionGroup svnGroup = new DefaultActionGroup();
    svnGroup.add(new HighlightFrom());
    svnGroup.add(myIntegrateAction);
    svnGroup.add(myUndoIntegrateChangeListsAction);
    svnGroup.add(new MarkAsMerged(true));
    svnGroup.add(new MarkAsMerged(false));
    svnGroup.add(myFilterMerged);
    svnGroup.add(myFilterNotMerged);
    svnGroup.add(myFilterAlien);
    svnGroup.add(ActionManager.getInstance().getAction("Svn.Show.Working.Copies"));
    svnGroup.add(new MyRefresh());
    return svnGroup;
  }

  @NotNull
  private MergeInfoHolder createHolder(@NotNull SvnMergeInfoRootPanelManual panel) {
    return new MergeInfoHolder(myProject, myManager, this, panel);
  }

  public JComponent getPanel() {
    myPanelWrapper = new JPanel(new GridBagLayout()) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension oldSize = super.getPreferredSize();
        oldSize.width = 200;
        return oldSize;
      }
    };
    //myPanelWrapper.setPreferredSize(new Dimension(200, 800));
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    myPanelWrapper.add(myPanel, gb);
    return ScrollPaneFactory
      .createScrollPane(myPanelWrapper, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
  }

  // todo refactor to get rid of duplicate code dealing with separators
  private String ensureEndsWithSeparator(final String wcPath) {
    return wcPath.endsWith(File.separator) ? wcPath : (wcPath + File.separator);
  }

  public void refresh() {
    final Map<String, CommittedChangeListsListener> refreshers = new HashMap<>();

    for (Map.Entry<String, MergeInfoHolder> entry : myHolders.entrySet()) {
      final CommittedChangeListsListener refresher = entry.getValue().createRefresher(false);
      if (refresher != null) {
        refreshers.put(ensureEndsWithSeparator(entry.getKey()), refresher);
      }
    }

    if (!refreshers.isEmpty()) {
      myManager.reportLoadedLists(new CommittedChangeListsListener() {
        public void onBeforeStartReport() {
        }

        public boolean report(final CommittedChangeList list) {
          if (list instanceof SvnChangeList) {
            final SvnChangeList svnList = (SvnChangeList)list;
            final String wcPath = svnList.getWcPath();
            if (wcPath != null) {
              final CommittedChangeListsListener refresher = refreshers.get(ensureEndsWithSeparator(wcPath));
              if (refresher != null) {
                refresher.report(list);
              }
            }
          }
          return true;
        }

        public void onAfterEndReport() {
          for (CommittedChangeListsListener refresher : refreshers.values()) {
            refresher.onAfterEndReport();
          }
          myStrategy.notifyListener();
        }
      });
      myManager.repaintTree();
    }
  }


  private class MyRefresh extends AnAction {
    private MyRefresh() {
      super(SvnBundle.message("committed.changes.action.merge.highlighting.refresh.text"),
            SvnBundle.message("committed.changes.action.merge.highlighting.refresh.description"), AllIcons.Actions.Refresh);
    }

    @Override
    public void update(final AnActionEvent e) {
      for (MergeInfoHolder holder : myHolders.values()) {
        if (holder.refreshEnabled(false)) {
          e.getPresentation().setEnabled(true);
          return;
        }
      }
      e.getPresentation().setEnabled(false);
    }

    public void actionPerformed(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);

      refresh();
    }
  }

  private class HighlightFrom extends ToggleAction {
    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setIcon(SvnIcons.ShowIntegratedFrom);
      presentation.setText(SvnBundle.message("committed.changes.action.enable.merge.highlighting"));
      presentation.setDescription(SvnBundle.message("committed.changes.action.enable.merge.highlighting.description.text"));
    }

    public boolean isSelected(final AnActionEvent e) {
      return myHighlightingOn;
    }

    public void setSelected(final AnActionEvent e, final boolean state) {
      if (state) {
        turnFromHereHighlighting();
      }
      else {
        turnOff();
      }
    }
  }

  private abstract class CommonFilter extends ToggleAction {
    private boolean mySelected;
    private final Icon myIcon;

    protected CommonFilter(final Icon icon, final String text) {
      super(text);
      myIcon = icon;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(myHighlightingOn);
      presentation.setIcon(myIcon);
      presentation.setText(getTemplatePresentation().getText());
    }

    public boolean isSelected(final AnActionEvent e) {
      return mySelected;
    }

    public void setSelected(final AnActionEvent e, final boolean state) {
      mySelected = state;
      myStrategy.notifyListener();
    }
  }

  private class FilterOutMerged extends CommonFilter {
    private FilterOutMerged() {
      super(SvnIcons.FilterIntegrated, SvnBundle.message("tab.repository.merge.panel.filter.plus"));
    }
  }

  private class FilterOutNotMerged extends CommonFilter {
    private FilterOutNotMerged() {
      super(SvnIcons.FilterNotIntegrated, SvnBundle.message("tab.repository.merge.panel.filter.minus"));
    }
  }

  private class FilterOutAlien extends CommonFilter {
    private FilterOutAlien() {
      super(SvnIcons.FilterOthers, SvnBundle.message("tab.repository.merge.panel.filter.others"));
    }
  }

  private boolean mergeEnabled(final List<CommittedChangeList> listsList, final boolean forMerge) {
    if ((listsList == null) || (listsList.isEmpty())) {
      return false;
    }
    for (CommittedChangeList list : listsList) {
      if (!mergeEnabled(list, forMerge)) {
        return false;
      }
    }
    return true;
  }

  private boolean mergeEnabled(final CommittedChangeList list, final boolean forMerge) {
    final ListMergeStatus mergeStatus = getStatus(list, true);
    if ((mergeStatus == null) || (ListMergeStatus.ALIEN.equals(mergeStatus))) {
      return false;
    }
    else if (ListMergeStatus.REFRESHING.equals(mergeStatus)) {
      return true;
    }
    if (forMerge) {
      return ListMergeStatus.NOT_MERGED.equals(mergeStatus);
    }
    return ListMergeStatus.MERGED.equals(mergeStatus);
  }

  private class MarkAsMerged extends AbstractIntegrateChangesAction<SelectedChangeListsChecker> {
    private final String myText;
    private final String myDescription;
    private final boolean myMarkAsMerged;

    private MarkAsMerged(boolean markAsMerged) {
      super(false);
      myMarkAsMerged = markAsMerged;
      myText = message("action.mark.list.as.%s.text");
      myDescription = message("action.mark.list.as.%s.description");
    }

    @NotNull
    protected MergerFactory createMergerFactory(SelectedChangeListsChecker checker) {
      return new ChangeListsMergerFactory(checker.getSelectedLists(), true, !myMarkAsMerged, false);
    }

    @NotNull
    protected SelectedChangeListsChecker createChecker() {
      return new SelectedChangeListsChecker();
    }

    @Override
    protected void updateWithChecker(AnActionEvent e, SelectedCommittedStuffChecker checker) {
      final Presentation presentation = e.getPresentation();
      presentation.setIcon(myMarkAsMerged ? SvnIcons.MarkAsMerged : SvnIcons.MarkAsNotMerged);
      presentation.setText(myText);
      presentation.setDescription(myDescription);
      presentation.setEnabled(presentation.isEnabled() && mergeEnabled(checker.getSelectedLists(), myMarkAsMerged));
    }

    @Nullable
    protected String getSelectedBranchUrl(SelectedCommittedStuffChecker checker) {
      final SvnMergeInfoRootPanelManual data = getPanelData(checker.getSelectedLists());
      if (data != null) {
        return data.getBranch().getUrl();
      }
      return null;
    }

    @Nullable
    protected String getSelectedBranchLocalPath(SelectedCommittedStuffChecker checker) {
      final SvnMergeInfoRootPanelManual data = getPanelData(checker.getSelectedLists());
      if (data != null) {
        return data.getLocalBranch();
      }
      return null;
    }

    protected String getDialogTitle() {
      return myText;
    }

    @NotNull
    private String message(@NotNull String key) {
      return SvnBundle.message(String.format(key, myMarkAsMerged ? "merged" : "not.merged"));
    }
  }

  private class IntegrateChangeListsAction extends AbstractIntegrateChangesAction<SelectedChangeListsChecker> {
    private final boolean myIntegrate;

    public IntegrateChangeListsAction(boolean integrate) {
      super(false);
      myIntegrate = integrate;
    }

    @NotNull
    protected MergerFactory createMergerFactory(final SelectedChangeListsChecker checker) {
      return new ChangeListsMergerFactory(checker.getSelectedLists(), false, !myIntegrate, false);
    }

    @NotNull
    protected SelectedChangeListsChecker createChecker() {
      return new SelectedChangeListsChecker();
    }

    @Override
    protected void updateWithChecker(AnActionEvent e, SelectedCommittedStuffChecker checker) {
      if (myIntegrate) {
        e.getPresentation().setIcon(SvnIcons.IntegrateToBranch);
      }
      else {
        e.getPresentation().setIcon(SvnIcons.UndoIntegrateToBranch);
        e.getPresentation().setText(SvnBundle.message("undo.integrate.to.branch"));
        e.getPresentation().setDescription(SvnBundle.message("undo.integrate.to.branch.description"));
      }
    }

    protected String getSelectedBranchUrl(SelectedCommittedStuffChecker checker) {
      final SvnMergeInfoRootPanelManual data = getPanelData(checker.getSelectedLists());
      if (data != null && data.getBranch() != null) {
        return data.getBranch().getUrl();
      }
      return null;
    }

    protected String getSelectedBranchLocalPath(SelectedCommittedStuffChecker checker) {
      final SvnMergeInfoRootPanelManual data = getPanelData(checker.getSelectedLists());
      if (data != null) {
        return data.getLocalBranch();
      }
      return null;
    }

    protected String getDialogTitle() {
      return !myIntegrate ? SvnBundle.message("undo.integrate.to.branch.dialog.title") : null;
    }
  }

  private SvnMergeInfoRootPanelManual getPanelData(final List<CommittedChangeList> listsList) {
    for (CommittedChangeList list : listsList) {
      if (!(list instanceof SvnChangeList)) {
        return null;
      }
      final SvnChangeList svnList = (SvnChangeList)list;
      final String wcPath = svnList.getWcPath();
      if (wcPath == null) {
        continue;
      }
      return getPanelData(wcPath);
    }
    return null;
  }

  @Nullable
  public ListMergeStatus getStatus(final CommittedChangeList list, final boolean ignoreEnabled) {
    if (!(list instanceof SvnChangeList)) {
      return null;
    }

    final SvnChangeList svnList = (SvnChangeList)list;
    final String wcPath = svnList.getWcPath();
    MergeInfoHolder holder = null;
    if (wcPath == null) {
      for (Map.Entry<String, SvnMergeInfoRootPanelManual> entry : myMergePanels.entrySet()) {
        final SvnMergeInfoRootPanelManual panelManual = entry.getValue();
        if ((panelManual.getBranch() != null) && (panelManual.getBranch().getUrl() != null) &&
            svnList.allPathsUnder(panelManual.getBranch().getUrl())) {
          holder = getHolder(entry.getKey());
        }
      }
    }
    else {
      holder = getHolder(wcPath);
    }
    if (holder != null) {
      return holder.check(list, ignoreEnabled);
    }
    return null;
  }

  public MergePanelFiltering getStrategy() {
    return myStrategy;
  }

  public boolean strategyInitialized() {
    return myStrategy.isInitialized();
  }

  private class MergePanelFiltering implements ChangeListFilteringStrategy {
    private final JComponent myPanel;
    private ChangeListener myListener;
    private boolean myInitialized;
    private final static String ourKey = "MERGE_PANEL";

    public MergePanelFiltering(final JComponent panel) {
      myPanel = panel;
    }

    public boolean isInitialized() {
      return myInitialized;
    }

    public JComponent getFilterUI() {
      if (!myInitialized) {
        createPanels(myLocation, null);
      }
      myInitialized = true;
      return myPanel;
    }

    @Override
    public CommittedChangesFilterKey getKey() {
      return new CommittedChangesFilterKey(ourKey, CommittedChangesFilterPriority.MERGE);
    }

    public void setFilterBase(final List<CommittedChangeList> changeLists) {
    }

    public void addChangeListener(final ChangeListener listener) {
      myListener = listener;
    }

    public void removeChangeListener(final ChangeListener listener) {
      myListener = null;
    }

    public void resetFilterBase() {
    }

    public void appendFilterBase(List<CommittedChangeList> changeLists) {
    }

    @NotNull
    public List<CommittedChangeList> filterChangeLists(final List<CommittedChangeList> changeLists) {
      if ((!myFilterAlien.isSelected(null)) && (!myFilterNotMerged.isSelected(null)) && (!myFilterMerged.isSelected(null))) {
        return changeLists;
      }

      final List<CommittedChangeList> result = new ArrayList<>();
      for (CommittedChangeList list : changeLists) {
        final ListMergeStatus status = getStatus(list, true);
        if (ListMergeStatus.REFRESHING.equals(status)) {
          result.add(list);
        }
        else if ((status == null) || ListMergeStatus.ALIEN.equals(status)) {
          if (!myFilterAlien.isSelected(null)) {
            result.add(list);
          }
        }
        else if (ListMergeStatus.MERGED.equals(status) || ListMergeStatus.COMMON.equals(status)) {
          if (!myFilterMerged.isSelected(null)) {
            result.add(list);
          }
        }
        else {
          // not merged
          if (!myFilterNotMerged.isSelected(null)) {
            result.add(list);
          }
        }
      }
      return result;
    }

    public void notifyListener() {
      if (myListener != null) {
        myListener.stateChanged(new ChangeEvent(this));
      }
      myManager.repaintTree();
    }
  }

  public void fireRepaint() {
    myManager.repaintTree();
  }

  public void dispose() {
    myDisposed = true;
  }
}
