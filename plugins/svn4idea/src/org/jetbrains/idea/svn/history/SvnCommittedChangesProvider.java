/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ConfigureBranchesAction;
import org.jetbrains.idea.svn.actions.IntegrateChangeListsAction;
import org.jetbrains.idea.svn.mergeinfo.HighlightBranchesAction;
import org.jetbrains.idea.svn.mergeinfo.SelectWcRootAction;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import java.awt.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author yole
 */
public class SvnCommittedChangesProvider implements CachingCommittedChangesProvider<SvnChangeList, ChangeBrowserSettings> {
  private final Project myProject;
  private final SvnVcs myVcs;
  private final MessageBusConnection myConnection;

  public final static int VERSION_WITH_COPY_PATHS_ADDED = 2;
  private HighlightBranchesAction myHighlightBranchesAction;

  public SvnCommittedChangesProvider(final Project project) {
    myProject = project;
    myVcs = SvnVcs.getInstance(myProject);

    myConnection = myProject.getMessageBus().connect();

    myConnection.subscribe(VcsConfigurationChangeListener.BRANCHES_CHANGED_RESPONSE, new VcsConfigurationChangeListener.DetailedNotification() {
      public void execute(final Project project, final VirtualFile vcsRoot, final List<CommittedChangeList> cachedList) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            for (CommittedChangeList committedChangeList : cachedList) {
              if ((committedChangeList instanceof SvnChangeList) &&
                  ((vcsRoot == null) || (vcsRoot.equals(((SvnChangeList)committedChangeList).getVcsRoot())))) {
                ((SvnChangeList) committedChangeList).forceReloadCachedInfo(vcsRoot == null);
              }
            }
          }
        });
      }
    });
  }

  public ChangeBrowserSettings createDefaultSettings() {
    return new ChangeBrowserSettings();
  }

  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(final boolean showDateFilter) {
    return new SvnVersionFilterComponent(showDateFilter);
  }

  @Nullable
  public RepositoryLocation getLocationFor(final FilePath root) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    String[] urls = SvnUtil.getLocationsForModule(myVcs, root.getIOFile(), progress);
    if (urls.length == 1) {
      return new SvnRepositoryLocation(root, urls [0]);
    }
    return null;
  }

  public RepositoryLocation getLocationFor(final FilePath root, final String repositoryPath) {
    if (repositoryPath == null) {
      return getLocationFor(root);
    }

    return new SvnLoadingRepositoryLocation(repositoryPath, myVcs);
  }

  public List<SvnChangeList> getCommittedChanges(ChangeBrowserSettings settings, final RepositoryLocation location, final int maxCount) throws VcsException {
    final SvnRepositoryLocation svnLocation = (SvnRepositoryLocation) location;
    final ArrayList<SvnChangeList> result = new ArrayList<SvnChangeList>();
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.setText(SvnBundle.message("progress.text.changes.collecting.changes"));
      progress.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", location));
    }
    try {
      SVNLogClient logger = myVcs.createLogClient();
      final SVNRepository repository = myVcs.createRepository(svnLocation.getURL());
      final String repositoryRoot = repository.getRepositoryRoot(true).toString();
      repository.closeSession();

      final String author = settings.getUserFilter();
      final Date dateFrom = settings.getDateAfterFilter();
      final Long changeFrom = settings.getChangeAfterFilter();
      final Date dateTo = settings.getDateBeforeFilter();
      final Long changeTo = settings.getChangeBeforeFilter();

      final SVNRevision revisionBefore;
      if (dateTo != null) {
        revisionBefore = SVNRevision.create(dateTo);
      }
      else if (changeTo != null) {
        revisionBefore = SVNRevision.create(changeTo.longValue());
      }
      else {
        revisionBefore = SVNRevision.HEAD;
      }
      final SVNRevision revisionAfter;
      if (dateFrom != null) {
        revisionAfter = SVNRevision.create(dateFrom);
      }
      else if (changeFrom != null) {
        revisionAfter = SVNRevision.create(changeFrom.longValue());
      }
      else {
        revisionAfter = SVNRevision.create(1);
      }

      logger.doLog(SVNURL.parseURIEncoded(svnLocation.getURL()), new String[]{""}, revisionBefore, revisionBefore, revisionAfter, false, true, maxCount,
                   new ISVNLogEntryHandler() {
                     public void handleLogEntry(SVNLogEntry logEntry) {
                       if (myProject.isDisposed()) throw new ProcessCanceledException();
                       if (progress != null) {
                         progress.setText2(SvnBundle.message("progress.text2.processing.revision", logEntry.getRevision()));
                         progress.checkCanceled();
                       }
                       if (author == null || author.equalsIgnoreCase(logEntry.getAuthor())) {
                         result.add(new SvnChangeList(myVcs, svnLocation, logEntry, repositoryRoot));
                       }
                     }
                   });
      settings.filterChanges(result);
      return result;
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[] {
      new ChangeListColumn.ChangeListNumberColumn(SvnBundle.message("revision.title")),
      ChangeListColumn.NAME, ChangeListColumn.DATE, ChangeListColumn.DESCRIPTION
    };
  }

  @Nullable
  public Pair<JPanel,List<AnAction>> createActionPanel(final DecoratorManager manager) {
    final JPanel panel = new JPanel(new BorderLayout());

    // show merge info action is not ready and therefore is disabled

    /*final DefaultActionGroup group = new DefaultActionGroup();
    myHighlightBranchesAction = new HighlightBranchesAction(myProject, manager);

    final SelectWcRootAction selectRootAction = new SelectWcRootAction(myProject, myHighlightBranchesAction,
                                                                       new Consumer<Boolean>() {
                                                                         public void consume(final Boolean aBoolean) {
                                                                           panel.setVisible(Boolean.TRUE.equals(aBoolean));
                                                                         }
                                                                       });
    final MyUseBranchFilderCheckboxAction checkBoxAction = new MyUseBranchFilderCheckboxAction(myHighlightBranchesAction, selectRootAction);
    myHighlightBranchesAction.enable(false);
    selectRootAction.enable(false);

    group.add(checkBoxAction);
    group.add(selectRootAction);
    group.add(myHighlightBranchesAction);
    group.add(new Refresh(myHighlightBranchesAction, selectRootAction));

    final ActionToolbar customToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    panel.add(customToolbar.getComponent(), BorderLayout.WEST);*/

    final DefaultActionGroup popup = new DefaultActionGroup(myVcs.getDisplayName(), true);
    popup.add(new IntegrateChangeListsAction());
    popup.add(new ConfigureBranchesAction());

    /*myConnection.subscribe(VcsConfigurationChangeListener.BRANCHES_CHANGED, new VcsConfigurationChangeListener.Notification() {
      public void execute(final Project project, final VirtualFile vcsRoot) {
        selectRootAction.reload();
      }
    });
    myConnection.subscribe(SvnMapDialog.WC_CONVERTED, new Runnable() {
      public void run() {
        selectRootAction.reload();
      }
    });

    ProjectLevelVcsManager.getInstance(myProject).addVcsListener(new VcsListener() {
      public void directoryMappingChanged() {
        selectRootAction.reload();
      }
    });*/

    return new Pair<JPanel, List<AnAction>>(panel, Collections.<AnAction>singletonList(popup));
  }

  private static class Refresh extends AnAction {
    private final HighlightBranchesAction myHighlightAction;
    private final SelectWcRootAction mySelectRootAction;

    private Refresh(final HighlightBranchesAction highlightAction, final SelectWcRootAction selectRootAction) {
      super(SvnBundle.message("committed.changes.action.merge.highlighting.refresh.text"),
            SvnBundle.message("committed.changes.action.merge.highlighting.refresh.description"), IconLoader.findIcon("/actions/sync.png"));
      myHighlightAction = highlightAction;
      mySelectRootAction = selectRootAction;
    }

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(myHighlightAction.enablesRefresh());
    }

    public void actionPerformed(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);

      mySelectRootAction.reload();
      myHighlightAction.refresh();
    }
  }

  private static class MyUseBranchFilderCheckboxAction extends CheckboxAction {
    private final HighlightBranchesAction myHighlightAction;
    private final SelectWcRootAction mySelectRootAction;
    private boolean mySelected;

    private MyUseBranchFilderCheckboxAction(final HighlightBranchesAction highlightAction, final SelectWcRootAction selectRootAction) {
      super(SvnBundle.message("committed.changes.action.enable.merge.highlighting"), "", null);   
      myHighlightAction = highlightAction;
      mySelectRootAction = selectRootAction;
    }

    public boolean isSelected(final AnActionEvent e) {
      return mySelected;
    }

    public void setSelected(final AnActionEvent e, final boolean state) {
      mySelected = state;
      myHighlightAction.enable(mySelected);
      mySelectRootAction.enable(mySelected);
    }
  }

  public int getUnlimitedCountValue() {
    return 0;
  }

  public int getFormatVersion() {
    return VERSION_WITH_COPY_PATHS_ADDED;
  }

  public void writeChangeList(final DataOutput dataStream, final SvnChangeList list) throws IOException {
    list.writeToStream(dataStream);
  }

  public SvnChangeList readChangeList(final RepositoryLocation location, final DataInput stream) throws IOException {
    return new SvnChangeList(myVcs, (SvnRepositoryLocation) location, stream,
                             VERSION_WITH_COPY_PATHS_ADDED >= getFormatVersion());
  }

  public boolean isMaxCountSupported() {
    return true;
  }

  public Collection<FilePath> getIncomingFiles(final RepositoryLocation location) {
    return null;
  }

  public boolean refreshCacheByNumber() {
    return true;
  }

  public String getChangelistTitle() {
    return SvnBundle.message("changes.browser.revision.term");
  }

  public boolean isChangeLocallyAvailable(FilePath filePath, @Nullable VcsRevisionNumber localRevision, VcsRevisionNumber changeRevision,
                                          final SvnChangeList changeList) {
    return localRevision != null && localRevision.compareTo(changeRevision) >= 0;
  }

  public boolean refreshIncomingWithCommitted() {
    return false;
  }

  public void deactivate() {
    myConnection.disconnect();
    if (myHighlightBranchesAction != null) {
      myHighlightBranchesAction.deactivate();
      myHighlightBranchesAction = null;
    }
  }
}