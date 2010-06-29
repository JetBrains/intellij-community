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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.committed.ChangeListFilteringStrategy;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListDecorator;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListsListener;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.NullableFunction;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.AbstractIntegrateChangesAction;
import org.jetbrains.idea.svn.actions.ChangeListsMergerFactory;
import org.jetbrains.idea.svn.actions.RecordOnlyMergerFactory;
import org.jetbrains.idea.svn.actions.ShowSvnMapAction;
import org.jetbrains.idea.svn.dialogs.SvnMapDialog;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.integrate.*;
import org.jetbrains.idea.svn.mergeinfo.MergeInfoHolder;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;

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
  private final Project myProject;
  private final DecoratorManager myManager;
  private final RepositoryLocation myLocation;
  private JPanel myPanel;
  private final Map<String, SvnMergeInfoRootPanelManual> myMergePanels;
  private final Map<String, MergeInfoHolder> myHolders;

  private boolean myHighlightingOn;
  private boolean myFromHereDirection;
  private JPanel myPanelWrapper;
  private final MergePanelFiltering myStrategy;
  private final FilterOutMerged myFilterMerged;
  private final FilterOutNotMerged myFilterNotMerged;
  private final FilterOutAlien myFilterAlien;
  private final IntegrateChangeListsAction myIntegrateAction;
  private final UndoIntegrateChangeListsAction myUndoIntegrateChangeListsAction;
  private JComponent myToolbarComponent;

  private boolean myDisposed;

  private final WcInfoLoader myDataLoader;

  public static final Topic<Runnable> REFRESH_REQUEST = new Topic<Runnable>("REFRESH_REQUEST", Runnable.class);

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
  
  public RootsAndBranches(final Project project, final DecoratorManager manager, final RepositoryLocation location) {
    myProject = project;
    myManager = manager;
    myLocation = location;

    myDataLoader = new WcInfoLoader(myProject, myLocation);

    myMergePanels = new HashMap<String, SvnMergeInfoRootPanelManual>();
    myHolders = new HashMap<String, MergeInfoHolder>();

    myFilterMerged = new FilterOutMerged();
    myFilterNotMerged = new FilterOutNotMerged();
    myFilterAlien = new FilterOutAlien();
    myIntegrateAction = new IntegrateChangeListsAction();
    myUndoIntegrateChangeListsAction = new UndoIntegrateChangeListsAction();

    myPanel = new JPanel(new GridBagLayout());
    myFromHereDirection = true;
    createToolbar();
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.NONE, new Insets(1, 1, 1, 1), 0, 0);
    gb.insets = new Insets(20, 1, 1, 1);
    myPanel.add(new JLabel("Loading..."), gb);
    
    myPanel.setPreferredSize(new Dimension(200, 60));

    myManager.install(this);
    setDirectionToPanels();

    myStrategy = new MergePanelFiltering(getPanel());
  }

  public IntegrateChangeListsAction getIntegrateAction() {
    return myIntegrateAction;
  }

  public UndoIntegrateChangeListsAction getUndoIntegrateAction() {
    return myUndoIntegrateChangeListsAction;
  }

  public void reloadPanels() {
    final Map<Pair<String, String>, SvnMergeInfoRootPanelManual.InfoHolder> states = new HashMap<Pair<String, String>, SvnMergeInfoRootPanelManual.InfoHolder>();
    for (Map.Entry<String, SvnMergeInfoRootPanelManual> entry : myMergePanels.entrySet()) {
      final String localPath = entry.getKey();
      final WCInfoWithBranches wcInfo = entry.getValue().getWcInfo();
      states.put(new Pair<String, String>(localPath, wcInfo.getUrl().toString()), entry.getValue().getInfo());
    }
    createPanels(myLocation, new Runnable() {
      public void run() {
        for (Map.Entry<String, SvnMergeInfoRootPanelManual> entry : myMergePanels.entrySet()) {
          final String localPath = entry.getKey();
          final WCInfoWithBranches wcInfo = entry.getValue().getWcInfo();
          final Pair<String, String> key = new Pair<String, String>(localPath, wcInfo.getUrl().toString());
          final SvnMergeInfoRootPanelManual.InfoHolder infoHolder = states.get(key);
          if (infoHolder !=  null) {
            entry.getValue().initSelection(infoHolder);
          }
        }
      }
    });
  }

  public void turnFromHereHighlighting() {
    myHighlightingOn = true;
    myFromHereDirection = true;
    setDirectionToPanels();
    for (MergeInfoHolder holder : myHolders.values()) {
      holder.updateMixedRevisionsForPanel();
    }

    myManager.repaintTree();
  }

  public void turnFromThereHighlighting() {
    myHighlightingOn = true;
    myFromHereDirection = false;
    setDirectionToPanels();

    myManager.repaintTree();
  }

  private void setDirectionToPanels() {
    for (SvnMergeInfoRootPanelManual panel : myMergePanels.values()) {
      panel.setDirection(myFromHereDirection);
    }
  }

  public void turnOff() {
    myHighlightingOn = false;
    for (SvnMergeInfoRootPanelManual panelManual : myMergePanels.values()) {
      panelManual.setMixedRevisions(false);
    }

    myManager.repaintTree();
  }

  public Icon decorate(final CommittedChangeList list) {
    final MergeInfoHolder.ListMergeStatus status = getStatus(list, false);
    return (status == null) ? MergeInfoHolder.ListMergeStatus.ALIEN.getIcon() : status.getIcon();
  }
  
  private void createPanels(final RepositoryLocation location, final Runnable afterRefresh) {
    final Task.Backgroundable backgroundable = new Task.Backgroundable(myProject, "Subversion: loading working copies data..", false,
                                                                        BackgroundFromStartOption.getInstance()) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        final Map<String, SvnMergeInfoRootPanelManual> panels = new HashMap<String, SvnMergeInfoRootPanelManual>();
        final Map<String, MergeInfoHolder> holders = new HashMap<String, MergeInfoHolder>();
        final JPanel mainPanel = prepareData(panels, holders);
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (myDisposed) return;
            
            myMergePanels.clear();
            myHolders.clear();
            myMergePanels.putAll(panels);
            myHolders.putAll(holders);
            
            if (myPanelWrapper != null) {
              myPanelWrapper.removeAll();
              if (myMergePanels.isEmpty()) {
                final JPanel emptyPanel = new JPanel(new GridBagLayout());
                final GridBagConstraints gb =
                  new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 5, 0, 5), 0, 0);
                final JLabel label = new JLabel("No Subversion 1.5 working copies\nof 1.5 repositories in the project");
                label.setUI(new MultiLineLabelUI());
                emptyPanel.add(label, gb);
                gb.fill = GridBagConstraints.HORIZONTAL;
                myPanelWrapper.add(emptyPanel, gb);
              } else {
                for (MergeInfoHolder holder : myHolders.values()) {
                  holder.updateMixedRevisionsForPanel();
                }
                myPanelWrapper.add(mainPanel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
              }
              myPanelWrapper.repaint();
            } else {
              myPanel = mainPanel;
            }
            if (afterRefresh != null) {
              afterRefresh.run();
            }
          }
        });
      }
    };
    ProgressManager.getInstance().run(backgroundable);
  }

  public void refreshByLists(final List<CommittedChangeList> committedChangeLists) {
    if (! committedChangeLists.isEmpty()) {
      final SvnChangeList svnList = (SvnChangeList) committedChangeLists.get(0);
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
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, svnGroup, true);
    myToolbarComponent = actionToolbar.getComponent();
  }

  private JPanel prepareData(final Map<String, SvnMergeInfoRootPanelManual> panels, final Map<String, MergeInfoHolder> holders) {
      final List<WCInfoWithBranches> roots = myDataLoader.loadRoots();

      final JPanel mainPanel = new JPanel(new GridBagLayout());
      boolean onlyOneRoot = roots.size() == 1;
      final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                                                           new Insets(0,0,0,0), 0, 0);
      mainPanel.add(myToolbarComponent, gb);
      ++ gb.gridy;

      for (final WCInfoWithBranches root : roots) {
        if (root == null) {
          continue;
        }
        final SvnMergeInfoRootPanelManual panel = new SvnMergeInfoRootPanelManual(myProject,
                                                                                  new NullableFunction<WCInfoWithBranches, WCInfoWithBranches>() {
                                                                                    public WCInfoWithBranches fun(final WCInfoWithBranches wcInfoWithBranches) {

                                                                                      final WCInfoWithBranches newInfo =
                                                                                        myDataLoader.reloadInfo(wcInfoWithBranches);
                                                                                      if (newInfo == null) {
                                                                                        // reload all items
                                                                                        myProject.getMessageBus().syncPublisher(SvnMapDialog.WC_CONVERTED).run();
                                                                                        // do not reload right now
                                                                                        return wcInfoWithBranches;
                                                                                      }
                                                                                      return newInfo;
                                                                                    }
                                                                                  }, new Runnable() {
            public void run() {
              final MergeInfoHolder holder = getHolder(root.getPath());
              if (holder != null) {
                holder.refresh(false);
              }
            }
          }, onlyOneRoot, root);
        panels.put(root.getPath(), panel);
        holders.put(root.getPath(), createHolder(panel));

        final JPanel contentPanel = panel.getContentPanel();
        mainPanel.add(contentPanel, gb);
        ++ gb.gridy;
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
    svnGroup.add(new MarkAsMerged());
    svnGroup.add(new MarkAsNotMerged());
    svnGroup.add(myFilterMerged);
    svnGroup.add(myFilterNotMerged);
    svnGroup.add(myFilterAlien);
    svnGroup.add(new ShowSvnMapAction());
    svnGroup.add(new MyRefresh());
    return svnGroup;
  }

  private MergeInfoHolder createHolder(final SvnMergeInfoRootPanelManual panel) {
    return new MergeInfoHolder(myProject, myManager, new Getter<WCInfoWithBranches>() {
      public WCInfoWithBranches get() {
        if (myFromHereDirection) {
          return panel.getWcInfo();
        } else {
          // actually not used
          return null;
        }
      }
    }, new Getter<WCInfoWithBranches.Branch>() {
      public WCInfoWithBranches.Branch get() {
        if (myFromHereDirection) {
          return panel.getBranch();
        } else {
          final WCInfoWithBranches wcInfo = panel.getWcInfo();
          return new WCInfoWithBranches.Branch(wcInfo.getUrl().toString());
        }
      }
    }, new Getter<String>() {
      public String get() {
        if (myFromHereDirection) {
          return panel.getLocalBranch();
        } else {
          final WCInfoWithBranches wcInfo = panel.getWcInfo();
          return wcInfo.getPath();
        }
      }
    }, new Getter<Boolean>() {
      public Boolean get() {
        return myHighlightingOn && panel.isEnabled();
      }
    }, new Consumer<Boolean>() {
      public void consume(final Boolean aBoolean) {
        panel.setMixedRevisions(aBoolean);
      }
    });
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
    return new JBScrollPane(myPanelWrapper, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                   JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
  }

  // todo refactor to get rid of duplicate code dealing with separators
  private String ensureEndsWithSeparator(final String wcPath) {
    return wcPath.endsWith(File.separator) ? wcPath : (wcPath + File.separator);
  }

  public void refresh() {
    final Map<String, CommittedChangeListsListener> refreshers = new HashMap<String, CommittedChangeListsListener>();

    for (Map.Entry<String, MergeInfoHolder> entry : myHolders.entrySet()) {
      final CommittedChangeListsListener refresher = entry.getValue().createRefresher(false);
      if (refresher != null) {
        refreshers.put(ensureEndsWithSeparator(entry.getKey()), refresher);
      }
    }

    if (! refreshers.isEmpty()) {
      myManager.reportLoadedLists(new CommittedChangeListsListener() {
        public void onBeforeStartReport() {
        }

        public boolean report(final CommittedChangeList list) {
          if (list instanceof SvnChangeList) {
            final SvnChangeList svnList = (SvnChangeList) list;
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
            SvnBundle.message("committed.changes.action.merge.highlighting.refresh.description"), IconLoader.getIcon("/actions/sync.png"));
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

  private class HighlightTo extends ToggleAction {
    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setIcon(IconLoader.getIcon("/icons/ShowIntegratedTo.png"));
    }

    public boolean isSelected(final AnActionEvent e) {
      return myHighlightingOn && (! myFromHereDirection);
    }

    public void setSelected(final AnActionEvent e, final boolean state) {
      if (state) {
        turnFromThereHighlighting();
      } else {
        turnOff();
      }
    }
  }

  private class HighlightFrom extends ToggleAction {
    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setIcon(IconLoader.getIcon("/icons/ShowIntegratedFrom.png"));
      presentation.setText(SvnBundle.message("committed.changes.action.enable.merge.highlighting"));
      presentation.setDescription(SvnBundle.message("committed.changes.action.enable.merge.highlighting.description.text"));
    }

    public boolean isSelected(final AnActionEvent e) {
      return myHighlightingOn && myFromHereDirection;
    }

    public void setSelected(final AnActionEvent e, final boolean state) {
      if (state) {
        turnFromHereHighlighting();
      } else {
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
      super(IconLoader.getIcon("/icons/FilterIntegrated.png"), SvnBundle.message("tab.repository.merge.panel.filter.plus"));
    }
  }

  private class FilterOutNotMerged extends CommonFilter {
    private FilterOutNotMerged() {
      super(IconLoader.getIcon("/icons/FilterNotIntegrated.png"), SvnBundle.message("tab.repository.merge.panel.filter.minus"));
    }
  }

  private class FilterOutAlien extends CommonFilter {
    private FilterOutAlien() {
      super(IconLoader.getIcon("/icons/FilterOthers.png"), SvnBundle.message("tab.repository.merge.panel.filter.others"));
    }
  }

  private boolean mergeEnabled(final List<CommittedChangeList> listsList, final boolean forMerge) {
    if ((listsList == null) || (listsList.isEmpty())) {
      return false;
    }
    for (CommittedChangeList list : listsList) {
      if (! mergeEnabled(list, forMerge)) {
        return false;
      }
    }
    return true;
  }

  private boolean mergeEnabled(final CommittedChangeList list, final boolean forMerge) {
    final MergeInfoHolder.ListMergeStatus mergeStatus = getStatus(list, true);
    if ((mergeStatus == null) || (MergeInfoHolder.ListMergeStatus.ALIEN.equals(mergeStatus))) {
      return false;
    } else if (MergeInfoHolder.ListMergeStatus.REFRESHING.equals(mergeStatus)) {
      return true;
    }
    if (forMerge) {
      return MergeInfoHolder.ListMergeStatus.NOT_MERGED.equals(mergeStatus);
    }
    return MergeInfoHolder.ListMergeStatus.MERGED.equals(mergeStatus);
  }

  private class MarkAsMerged extends AbstractIntegrateChangesAction<SelectedChangeListsChecker> {
    private final String myText;
    private final String myDescription;

    private MarkAsMerged() {
      super(false);
      myText = SvnBundle.message("action.mark.list.as.merged.text");
      myDescription = SvnBundle.message("action.mark.list.as.merged.description");
    }

    @NotNull
    protected MergerFactory createMergerFactory(SelectedChangeListsChecker checker) {
      return new RecordOnlyMergerFactory(checker.getSelectedLists(), false);
    }

    @NotNull
    protected SelectedChangeListsChecker createChecker() {
      return new SelectedChangeListsChecker();
    }

    @Override
    protected void updateWithChecker(AnActionEvent e, SelectedCommittedStuffChecker checker) {
      final Presentation presentation = e.getPresentation();
      presentation.setIcon(IconLoader.getIcon("/icons/MarkAsMerged.png"));
      presentation.setText(myText);
      presentation.setDescription(myDescription);
      presentation.setEnabled(presentation.isEnabled() && mergeEnabled(checker.getSelectedLists(), true));
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
  }

  private class MarkAsNotMerged extends AbstractIntegrateChangesAction<SelectedChangeListsChecker> {
    private final String myText;
    private final String myDescription;

    private MarkAsNotMerged() {
      super(false);
      myText = SvnBundle.message("action.mark.list.as.not.merged.title");
      myDescription = SvnBundle.message("action.mark.list.as.not.merged.descrition");
    }

    @NotNull
    protected MergerFactory createMergerFactory(SelectedChangeListsChecker checker) {
      return new RecordOnlyMergerFactory(checker.getSelectedLists(), true);
    }

    @NotNull
    protected SelectedChangeListsChecker createChecker() {
      return new SelectedChangeListsChecker();
    }

    @Override
    protected void updateWithChecker(AnActionEvent e, SelectedCommittedStuffChecker checker) {
      final Presentation presentation = e.getPresentation();
      presentation.setIcon(IconLoader.getIcon("/icons/MarkAsNotMerged.png"));
      presentation.setText(myText);
      presentation.setDescription(myDescription);
      presentation.setEnabled(presentation.isEnabled() && mergeEnabled(checker.getSelectedLists(), false));
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
  }

  private class UndoIntegrateChangeListsAction extends IntegrateChangeListsAction {
    private UndoIntegrateChangeListsAction() {
      super(false);
    }

    @Override
    protected void updateWithChecker(AnActionEvent e, SelectedCommittedStuffChecker checker) {
      e.getPresentation().setIcon(IconLoader.getIcon("/icons/UndoIntegrateToBranch.png"));
      e.getPresentation().setText(SvnBundle.message("undo.integrate.to.branch"));
      e.getPresentation().setDescription(SvnBundle.message("undo.integrate.to.branch.description"));
    }

    @Override
    protected String getDialogTitle() {
      return SvnBundle.message("undo.integrate.to.branch.dialog.title");
    }
  }

  private class IntegrateChangeListsAction extends AbstractIntegrateChangesAction<SelectedChangeListsChecker> {
    private final boolean myDirect;

    public IntegrateChangeListsAction() {
      this(true);
    }

    protected IntegrateChangeListsAction(final boolean direct) {
      super(false);
      myDirect = direct;
    }

    @NotNull
    protected MergerFactory createMergerFactory(final SelectedChangeListsChecker checker) {
      return new ChangeListsMergerFactory(checker.getSelectedLists()) {
            @Override
            public IMerger createMerger(final SvnVcs vcs,
                                        final File target,
                                        final UpdateEventHandler handler,
                                        final SVNURL currentBranchUrl,
                                        String branchName) {
              return new Merger(vcs, myChangeListsList, target, handler, currentBranchUrl, branchName) {
                @Override
                protected SVNRevisionRange createRange() {
                  if (myDirect) {
                    return super.createRange();
                  } else {
                    return new SVNRevisionRange(SVNRevision.create(myLatestProcessed.getNumber()), SVNRevision.create(myLatestProcessed.getNumber() - 1));
                  }
                }
              };
            }
          };
    }

    @NotNull
    protected SelectedChangeListsChecker createChecker() {
      return new SelectedChangeListsChecker();
    }

    @Override
    protected void updateWithChecker(AnActionEvent e, SelectedCommittedStuffChecker checker) {
      e.getPresentation().setIcon(IconLoader.getIcon("/icons/IntegrateToBranch.png"));
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
      return null;
    }
  }

  private SvnMergeInfoRootPanelManual getPanelData(final List<CommittedChangeList> listsList) {
    for (CommittedChangeList list : listsList) {
      if (! (list instanceof SvnChangeList)) {
        return null;
      }
      final SvnChangeList svnList = (SvnChangeList) list;
      final String wcPath = svnList.getWcPath();
      if (wcPath == null) {
        continue;
      }
      return getPanelData(wcPath);
    }
    return null;
  }

  @Nullable
  public MergeInfoHolder.ListMergeStatus getStatus(final CommittedChangeList list, final boolean ignoreEnabled) {
    if (! (list instanceof SvnChangeList)) {
      return null;
    }

    final SvnChangeList svnList = (SvnChangeList) list;
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
    } else {
      holder = getHolder(wcPath);
    }
    if (holder != null) {
      return holder.getDecorator().check(list, ignoreEnabled);
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

    public MergePanelFiltering(final JComponent panel) {
      myPanel = panel;
    }

    public boolean isInitialized() {
      return myInitialized;
    }

    public JComponent getFilterUI() {
      if (! myInitialized) {
        createPanels(myLocation, null);
      }
      myInitialized = true;
      return myPanel;
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
      if ((! myFilterAlien.isSelected(null)) && (! myFilterNotMerged.isSelected(null)) && (! myFilterMerged.isSelected(null))) {
        return changeLists;
      }

      final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
      for (CommittedChangeList list : changeLists) {
        final MergeInfoHolder.ListMergeStatus status = getStatus(list, true);
        if (MergeInfoHolder.ListMergeStatus.REFRESHING.equals(status)) {
          result.add(list);
        } else if ((status == null) || MergeInfoHolder.ListMergeStatus.ALIEN.equals(status)) {
          if (! myFilterAlien.isSelected(null)) {
            result.add(list);
          }
        } else if (MergeInfoHolder.ListMergeStatus.MERGED.equals(status) || MergeInfoHolder.ListMergeStatus.COMMON.equals(status)) {
          if (! myFilterMerged.isSelected(null)) {
            result.add(list);
          }
        } else {
          // not merged
          if (! myFilterNotMerged.isSelected(null)) {
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
