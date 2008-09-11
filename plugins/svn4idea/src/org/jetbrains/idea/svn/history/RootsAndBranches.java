package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.ChangeListFilteringStrategy;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListDecorator;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListsListener;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.actions.AbstractIntegrateChangesAction;
import org.jetbrains.idea.svn.actions.ChangeListsMergerFactory;
import org.jetbrains.idea.svn.actions.RecordOnlyMergerFactory;
import org.jetbrains.idea.svn.actions.ShowSvnMapAction;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.dialogs.WCPaths;
import org.jetbrains.idea.svn.integrate.Merger;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.integrate.SelectedChangeListsChecker;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.jetbrains.idea.svn.mergeinfo.MergeInfoHolder;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

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
    myMergePanels = new HashMap<String, SvnMergeInfoRootPanelManual>();
    myHolders = new HashMap<String, MergeInfoHolder>();

    myFilterMerged = new FilterOutMerged();
    myFilterNotMerged = new FilterOutNotMerged();
    myFilterAlien = new FilterOutAlien();
    myIntegrateAction = new IntegrateChangeListsAction(createRefreshAfterAction());
    myUndoIntegrateChangeListsAction = new UndoIntegrateChangeListsAction(createRefreshAfterAction());

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

    myManager.repaintTree();
  }

  public Icon decorate(final CommittedChangeList list) {
    final MergeInfoHolder.ListMergeStatus status = getStatus(list, false);
    return (status == null) ? MergeInfoHolder.ListMergeStatus.ALIEN.getIcon() : status.getIcon();
  }
  
  private void createPanels(final RepositoryLocation location, final Runnable afterRefresh) {
    final Task.Backgroundable backgroundable = new Task.Backgroundable(myProject, "Subversion: loading working copies data..", false,
                                                                        new PerformInBackgroundOption() {
                                                                          public boolean shouldStartInBackground() {
                                                                            return true;
                                                                          }
                                                                          public void processSentToBackground() {
                                                                          }
                                                                          public void processRestoredToForeground() {
                                                                          }
                                                                        }) {
      public void run(final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        final JPanel mainPanel = prepareData(location);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myPanelWrapper != null) {
              myPanelWrapper.removeAll();
              myPanelWrapper.add(mainPanel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
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

  private void createToolbar() {
    final DefaultActionGroup svnGroup = createActions();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, svnGroup, true);
    myToolbarComponent = actionToolbar.getComponent();
  }

  private JPanel prepareData(final RepositoryLocation location) {
    synchronized (myMergePanels) {
      myMergePanels.clear();
      myHolders.clear();

      final List<WCInfoWithBranches> roots = loadRoots(location);

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
                                                                                      return reloadInfo(wcInfoWithBranches);
                                                                                    }
                                                                                  }, new Runnable() {
            public void run() {
              final MergeInfoHolder holder = getHolder(root.getPath());
              if (holder != null) {
                holder.refresh(false);
              }
            }
          }, onlyOneRoot, root);
        myMergePanels.put(root.getPath(), panel);
        myHolders.put(root.getPath(), createHolder(panel));

        final JPanel contentPanel = panel.getContentPanel();
        mainPanel.add(contentPanel, gb);
        ++ gb.gridy;
      }
      if (myMergePanels.size() == 1) {
        for (SvnMergeInfoRootPanelManual panel : myMergePanels.values()) {
          panel.setOnlyOneRoot(true);
        }
      }
      return mainPanel;
    }
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

  private static class WCPathsImpl implements WCPaths {
    private final String myPath;
    private final String myUrl;
    private final String myRepo;

    private WCPathsImpl(final String path, final String url, final String repo) {
      myPath = path;
      myUrl = url;
      myRepo = repo;
    }

    public String getRootUrl() {
      return myUrl;
    }

    public String getRepoUrl() {
      return myRepo;
    }

    public String getPath() {
      return myPath;
    }
  }

  private MergeInfoHolder createHolder(final SvnMergeInfoRootPanelManual panel) {
    return new MergeInfoHolder(myProject, myManager, new Getter<WCPaths>() {
      public WCPaths get() {
        if (myFromHereDirection) {
          return panel.getWcInfo();
        } else {
          final WCInfoWithBranches.Branch branch = panel.getBranch();
          final String local = panel.getLocalBranch();
          if ((local != null) && (branch != null)) {
            final SVNURL repoRoot = SvnUtil.getRepositoryRoot(SvnVcs.getInstance(myProject), new File(local));
            if (repoRoot != null) {
              return new WCPathsImpl(local, branch.getUrl(), repoRoot.toString());
            }
          }
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
    });
  }

  public JComponent getPanel() {
    myPanelWrapper = new JPanel(new GridBagLayout());
    myPanelWrapper.setPreferredSize(new Dimension(200, 800));
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    myPanelWrapper.add(myPanel, gb);
    final JScrollPane scrollPane = new JScrollPane(myPanelWrapper, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                   JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    final JPanel wrapper = new JPanel(new GridBagLayout());
    gb.fill = GridBagConstraints.BOTH;
    wrapper.add(scrollPane, gb);
    return wrapper;
  }

  /**
   *
   * @param location filled in case when showing hostory for folder/file
   * @return
   */
  public List<WCInfoWithBranches> loadRoots(final RepositoryLocation location) {
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    if (vcs == null) {
      return Collections.emptyList();
    }
    final SvnFileUrlMapping urlMapping = vcs.getSvnFileUrlMapping();
    final List<WCInfo> wcInfoList = vcs.getAllWcInfos();

    final List<WCInfoWithBranches> result = new ArrayList<WCInfoWithBranches>();
    for (WCInfo info : wcInfoList) {
      final WCInfoWithBranches wcInfoWithBranches = createInfo(info, location, vcs, urlMapping);
      result.add(wcInfoWithBranches);
    }
    return result;
  }

  @Nullable
  public WCInfoWithBranches reloadInfo(final WCInfoWithBranches info) {
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    if (vcs == null) {
      return null;
    }
    final SvnFileUrlMapping urlMapping = vcs.getSvnFileUrlMapping();
    final File file = new File(info.getPath());
    final Pair<String, SvnFileUrlMapping.RootUrlInfo> infoPair = urlMapping.getWcRootForFilePath(file);
    if (infoPair == null) {
      return null;
    }
    final SvnFileUrlMapping.RootUrlInfo rootInfo = infoPair.getSecond();
    final WCInfo wcInfo = new WCInfo(infoPair.getFirst(), rootInfo.getAbsoluteUrlAsUrl(), SvnFormatSelector.getWorkingCopyFormat(file),
                                     rootInfo.getRepositoryUrl(), SvnUtil.isWorkingCopyRoot(file));
    return createInfo(wcInfo, myLocation, vcs, urlMapping);
  }

  @Nullable
  private WCInfoWithBranches createInfo(final WCInfo info, final RepositoryLocation location, final SvnVcs vcs,
                                        final SvnFileUrlMapping urlMapping) {
    if (! WorkingCopyFormat.ONE_DOT_FIVE.equals(info.getFormat())) {
      return null;
    }

    final String url = info.getUrl().toString();
    if ((location != null) && (! location.toPresentableString().startsWith(url)) &&
        (! url.startsWith(location.toPresentableString()))) {
      return null;
    }
    if (!SvnUtil.checkRepositoryVersion15(vcs, url)) {
      return null;
    }

    // check of WC version
    final RootMixedInfo rootForUrl = urlMapping.getWcRootForUrl(url);
    if (rootForUrl == null) {
      return null;
    }
    final VirtualFile root = rootForUrl.getParentVcsRoot();
    final VirtualFile wcRoot = rootForUrl.getFile();
    if (wcRoot == null) {
      return null;
    }
    final SvnBranchConfiguration configuration;
    try {
      configuration = SvnBranchConfigurationManager.getInstance(myProject).get(wcRoot);
    }
    catch (VcsException e) {
      LOG.info(e);
      return null;
    }
    if (configuration == null) {
      return null;
    }

    final List<WCInfoWithBranches.Branch> items = createBranchesList(url, configuration);
    return new WCInfoWithBranches(info.getPath(), info.getUrl(), info.getFormat(),
                                                                         info.getRepositoryRoot(), info.isIsWcRoot(), items, root);
  }

  private List<WCInfoWithBranches.Branch> createBranchesList(final String url, final SvnBranchConfiguration configuration) {
    final List<WCInfoWithBranches.Branch> items = new ArrayList<WCInfoWithBranches.Branch>();

    final String trunkUrl = configuration.getTrunkUrl();
    if (! SVNPathUtil.isAncestor(trunkUrl, url)) {
      items.add(new WCInfoWithBranches.Branch(trunkUrl));
    }
    final Map<String,List<SvnBranchItem>> branchMap = configuration.getLoadedBranchMap(myProject);
    for (Map.Entry<String, List<SvnBranchItem>> entry : branchMap.entrySet()) {
      for (SvnBranchItem branchItem : entry.getValue()) {
        if (! SVNPathUtil.isAncestor(branchItem.getUrl(), url)) {
          items.add(new WCInfoWithBranches.Branch(branchItem.getUrl()));
        }
      }
    }

    Collections.sort(items, new Comparator<WCInfoWithBranches.Branch>() {
      public int compare(final WCInfoWithBranches.Branch o1, final WCInfoWithBranches.Branch o2) {
        return Comparing.compare(o1.getUrl(), o2.getUrl());
      }
    });
    return items;
  }

  public void refresh() {
    final Map<String, CommittedChangeListsListener> refreshers = new HashMap<String, CommittedChangeListsListener>();

    for (Map.Entry<String, MergeInfoHolder> entry : myHolders.entrySet()) {
      final CommittedChangeListsListener refresher = entry.getValue().createRefresher(false);
      if (refresher != null) {
        refreshers.put(entry.getKey(), refresher);
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
              final CommittedChangeListsListener refresher = refreshers.get(wcPath.endsWith(File.separator) ? wcPath : (wcPath + File.separator));
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
            SvnBundle.message("committed.changes.action.merge.highlighting.refresh.description"), IconLoader.findIcon("/actions/sync.png"));
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

  private Consumer<List<CommittedChangeList>> createRefreshAfterAction() {
    return new Consumer<List<CommittedChangeList>>() {
      public void consume(final List<CommittedChangeList> committedChangeLists) {
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
    };
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

  private class MarkAsMerged extends AbstractIntegrateChangesAction {
    private final String myText;
    private final String myDescription;

    private MarkAsMerged() {
      super(new SelectedChangeListsChecker(null) {
        @Override
        public MergerFactory createFactory() {
          return new RecordOnlyMergerFactory(myChangeListsList, createRefreshAfterAction(), false);
        }
      }, false);
      myText = SvnBundle.message("action.mark.list.as.merged.text");
      myDescription = SvnBundle.message("action.mark.list.as.merged.description");
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setIcon(IconLoader.getIcon("/icons/MarkAsMerged.png"));
      presentation.setText(myText);
      presentation.setDescription(myDescription);
      presentation.setEnabled(presentation.isEnabled() && mergeEnabled(myChecker.getSelectedLists(), true));
    }
    
    @Nullable
    protected String getSelectedBranchUrl() {
      final SvnMergeInfoRootPanelManual data = getPanelData(myChecker.getSelectedLists());
      if (data != null) {
        return data.getBranch().getUrl();
      }
      return null;
    }

    @Nullable
    protected String getSelectedBranchLocalPath() {
      final SvnMergeInfoRootPanelManual data = getPanelData(myChecker.getSelectedLists());
      if (data != null) {
        return data.getLocalBranch();
      }
      return null;
    }

    protected String getDialogTitle() {
      return myText;
    }
  }

  private class MarkAsNotMerged extends AbstractIntegrateChangesAction {
    private final String myText;
    private final String myDescription;

    private MarkAsNotMerged() {
      super(new SelectedChangeListsChecker(null) {
        @Override
        public MergerFactory createFactory() {
          return new RecordOnlyMergerFactory(myChangeListsList, createRefreshAfterAction(), true);
        }
      }, false);
      myText = SvnBundle.message("action.mark.list.as.not.merged.title");
      myDescription = SvnBundle.message("action.mark.list.as.not.merged.descrition");
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setIcon(IconLoader.getIcon("/icons/MarkAsNotMerged.png"));
      presentation.setText(myText);
      presentation.setDescription(myDescription);
      presentation.setEnabled(presentation.isEnabled() && mergeEnabled(myChecker.getSelectedLists(), false));
    }

    @Nullable
    protected String getSelectedBranchUrl() {
      final SvnMergeInfoRootPanelManual data = getPanelData(myChecker.getSelectedLists());
      if (data != null) {
        return data.getBranch().getUrl();
      }
      return null;
    }

    @Nullable
    protected String getSelectedBranchLocalPath() {
      final SvnMergeInfoRootPanelManual data = getPanelData(myChecker.getSelectedLists());
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
    private UndoIntegrateChangeListsAction(final Consumer<List<CommittedChangeList>> afterProcessing) {
      super(afterProcessing, false);
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setIcon(IconLoader.getIcon("/icons/UndoIntegrateToBranch.png"));
      e.getPresentation().setText(SvnBundle.message("undo.integrate.to.branch"));
      e.getPresentation().setDescription(SvnBundle.message("undo.integrate.to.branch.description"));
    }

    @Override
    protected String getDialogTitle() {
      return SvnBundle.message("undo.integrate.to.branch.dialog.title");
    }
  }

  private class IntegrateChangeListsAction extends AbstractIntegrateChangesAction {
    public IntegrateChangeListsAction(final Consumer<List<CommittedChangeList>> afterProcessing) {
      this(afterProcessing, true);
    }

    protected IntegrateChangeListsAction(final Consumer<List<CommittedChangeList>> afterProcessing, final boolean direct) {
      super(new SelectedChangeListsChecker(afterProcessing) {
        @Override
        public MergerFactory createFactory() {
          return new ChangeListsMergerFactory(myChangeListsList, afterProcessing) {
            @Override
            public Merger createMerger(final SvnVcs vcs, final File target, final UpdateEventHandler handler, final SVNURL currentBranchUrl) {
              return new Merger(vcs, myChangeListsList, target, handler, currentBranchUrl, myAfterProcessing) {
                @Override
                protected SVNRevisionRange createRange() {
                  if (direct) {
                    return super.createRange();
                  } else {
                    return new SVNRevisionRange(SVNRevision.create(myLatestProcessed.getNumber()), SVNRevision.create(myLatestProcessed.getNumber() - 1)); 
                  }
                }
              };
            }
          };
        }
      }, false);
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setIcon(IconLoader.getIcon("/icons/IntegrateToBranch.png"));
    }

    protected String getSelectedBranchUrl() {
      final SvnMergeInfoRootPanelManual data = getPanelData(myChecker.getSelectedLists());
      if (data != null) {
        return data.getBranch().getUrl();
      }
      return null;
    }

    protected String getSelectedBranchLocalPath() {
      final SvnMergeInfoRootPanelManual data = getPanelData(myChecker.getSelectedLists());
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
    if (wcPath == null) {
      return null;
    }
    final MergeInfoHolder holder = getHolder(wcPath);
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
        } else if (MergeInfoHolder.ListMergeStatus.MERGED.equals(status)) {
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
      myListener.stateChanged(new ChangeEvent(this));
      myManager.repaintTree();
    }
  }
}
