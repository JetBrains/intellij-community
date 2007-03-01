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

package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.FileContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBranchConfiguration;
import org.jetbrains.idea.svn.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.BranchConfigurationDialog;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * @author yole
 */
public class CompareWithBranchAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.actions.CompareWithBranchAction");

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(DataKeys.VIRTUAL_FILE);
    final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(virtualFile);
    final SvnBranchConfiguration configuration;
    try {
      configuration = SvnBranchConfigurationManager.getInstance(project).get(vcsRoot);
    }
    catch (VcsException e1) {
      Messages.showErrorDialog(project, SvnBundle.message("compare.with.branch.configuration.error", e1.getMessage()),
                               SvnBundle.message("compare.with.branch.error.title"));
      return;
    }

    BranchBasesPopupStep step = new BranchBasesPopupStep(project, virtualFile, vcsRoot, configuration);
    JBPopupFactory.getInstance().createListPopup(step).showCenteredInCurrentWindow(project);
  }

  @Override
  public void update(final AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(DataKeys.VIRTUAL_FILE);
    e.getPresentation().setEnabled(isEnabled(project, virtualFile));
  }

  private static boolean isEnabled(final Project project, final VirtualFile virtualFile) {
    if (project == null || virtualFile == null || virtualFile.isDirectory()) {
      return false;
    }
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED) {
      return false;
    }
    return true;
  }

  private class BranchBasesPopupStep extends BaseListPopupStep<String> {
    protected final Project myProject;
    private final VirtualFile myVirtualFile;
    private final VirtualFile myVcsRoot;
    private final SvnBranchConfiguration myConfiguration;
    private final boolean myTopLevel;

    protected BranchBasesPopupStep(final Project project, final VirtualFile virtualFile, final VirtualFile vcsRoot,
                                final SvnBranchConfiguration configuration, boolean topLevel) {
      myProject = project;
      myVirtualFile = virtualFile;
      myVcsRoot = vcsRoot;
      myConfiguration = configuration;
      myTopLevel = topLevel;
    }

    public BranchBasesPopupStep(final Project project, final VirtualFile virtualFile, final VirtualFile vcsRoot,
                                final SvnBranchConfiguration configuration) {
      this(project, virtualFile, vcsRoot, configuration, true);
      List<String> items = new ArrayList<String>();
      items.add(myConfiguration.getTrunkUrl());
      items.addAll(myConfiguration.getBranchUrls());
      items.add(SvnBundle.message("configure.branches.item"));
      init(SvnBundle.message("compare.with.branch.popup.title"), items, null);
    }

    @Override
    public ListSeparator getSeparatorAbove(final String value) {
      return value.equals(SvnBundle.message("configure.branches.item")) ? new ListSeparator("") : null;
    }

    @NotNull
    @Override
    public String getTextFor(final String value) {
      int pos = value.lastIndexOf('/');
      if (pos < 0) {
        return value;
      }
      if (myTopLevel && !value.startsWith(myConfiguration.getTrunkUrl())) {
        return value.substring(pos+1) + "...";
      }
      return value.substring(pos+1);
    }

    @Override
    public boolean hasSubstep(final String selectedValue) {
      return false;
    }

    @Override
    public PopupStep onChosen(final String selectedValue, final boolean finalChoice) {
      if (selectedValue.equals(SvnBundle.message("configure.branches.item"))) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            BranchConfigurationDialog.configureBranches(myProject, myVcsRoot);
          }
        });
      }
      else if (!myTopLevel || selectedValue.equals(myConfiguration.getTrunkUrl())) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            compareWithBranch(selectedValue, -1);
          }
        });
      }
      else {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            showBranchPopup(selectedValue);
          }
        });
      }
      return null;
    }

    private void showBranchPopup(final String selectedValue) {
      List<SvnBranchItem> branches;
      try {
        branches = getBranches(selectedValue);
      }
      catch (SVNException e) {
        Messages.showErrorDialog(myProject, e.getMessage(), SvnBundle.message("compare.with.branch.list.error"));
        return;
      }
      final JList branchList = new JList(branches.toArray());
      branchList.setCellRenderer(new BranchRenderer());
      JBPopupFactory.getInstance().createListPopupBuilder(branchList)
              .setTitle(SVNPathUtil.tail(selectedValue))
              .setResizable(true)
              .setDimensionServiceKey("Svn.CompareWithBranchPopup")
              .setItemChoosenCallback(new Runnable() {
                public void run() {
                  SvnBranchItem item = (SvnBranchItem) branchList.getSelectedValue();
                  if (item != null) {
                    compareWithBranch(item.myUrl, item.getRevision());                  
                  }
                }
              })
              .createPopup().showCenteredInCurrentWindow(myProject);
    }

    private List<SvnBranchItem> getBranches(final String url) throws SVNException {
      final ArrayList<SvnBranchItem> result = new ArrayList<SvnBranchItem>();
      final Ref<SVNException> ex = new Ref<SVNException>();
      boolean rc = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          if (indicator != null) {
            indicator.setIndeterminate(true);
          }
          final SVNLogClient logClient;
          try {
            logClient = SvnVcs.getInstance(myProject).createLogClient();
            logClient.doList(SVNURL.parseURIEncoded(url), SVNRevision.UNDEFINED, SVNRevision.HEAD, false, new ISVNDirEntryHandler() {
              public void handleDirEntry(final SVNDirEntry dirEntry) throws SVNException {
                ProgressManager.getInstance().checkCanceled();
                final String url = dirEntry.getURL().toString();
                result.add(new SvnBranchItem(url, dirEntry.getDate(), dirEntry.getRevision()));
              }
            });
            Collections.sort(result);
          }
          catch (SVNException e) {
            ex.set(e);
          }
        }
      }, SvnBundle.message("compare.with.branch.progress.loading.branches"), true, myProject);
      if (!rc) {
        return Collections.emptyList();
      }
      if (!ex.isNull()) {
        throw ex.get();
      }
      return result;
    }

    private void compareWithBranch(final String baseUrl, final long revision) {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final StringBuilder remoteTitleBuilder = new StringBuilder();
      final Ref<Boolean> success = new Ref<Boolean>();
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          try {
            final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
            if (indicator != null) {
              indicator.setIndeterminate(true);
            }
            SVNRevision infoRevision = SVNRevision.WORKING;
            if (revision != -1) {
              infoRevision = SVNRevision.create(revision);
            }
            SVNWCClient client = SvnVcs.getInstance(myProject).createWCClient();
            SVNInfo info;
            try {
              info = client.doInfo(VfsUtil.virtualToIoFile(myVirtualFile), infoRevision);
            }
            catch (SVNException ex) {
              reportException(ex, baseUrl);
              return;
            }
            if (info == null) {
              reportNotFound(baseUrl);
              return;
            }
            String fileUrl = myConfiguration.getRelativeUrl(info.getURL().toString());
            final SVNURL svnurl = SVNURL.parseURIEncoded(baseUrl).appendPath(fileUrl, true);
            remoteTitleBuilder.append(svnurl.toString());
            client.doGetFileContents(svnurl, SVNRevision.UNDEFINED, SVNRevision.HEAD, true, baos);
            success.set(true);
          }
          catch (SVNException ex) {
            reportException(ex, baseUrl);
          }
        }
      }, SvnBundle.message("compare.with.branch.progress.loading.content"), true, myProject);
      if (success.isNull()) {
        return;
      }
      ByteBuffer contents = ByteBuffer.wrap(baos.toByteArray());
      SimpleDiffRequest req = new SimpleDiffRequest(myProject, SvnBundle.message("compare.with.branch.diff.title"));
      req.setContents(new SimpleContent(myVirtualFile.getCharset().decode(contents).toString()),
                      new FileContent(myProject, myVirtualFile));
      req.setContentTitles(remoteTitleBuilder.toString(), myVirtualFile.getPresentableUrl());
      DiffManager.getInstance().getDiffTool().show(req);
    }

    private void reportException(final SVNException ex, final String baseUrl) {
      if (ex.getErrorMessage().getErrorCode().equals(SVNErrorCode.RA_ILLEGAL_URL) ||
          ex.getErrorMessage().getErrorCode().equals(SVNErrorCode.CLIENT_UNRELATED_RESOURCES) ||
          ex.getErrorMessage().getErrorCode().equals(SVNErrorCode.RA_DAV_PATH_NOT_FOUND)) {
        reportNotFound(baseUrl);
      }
      else {
        LOG.error(ex);
      }
    }

    private void reportNotFound(final String baseUrl) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showMessageDialog(myProject,
                                     SvnBundle.message("compare.with.branch.location.error", myVirtualFile.getPresentableUrl(), baseUrl),
                                     SvnBundle.message("compare.with.branch.error.title"), Messages.getErrorIcon());
        }
      });
    }
  }

  private static class SvnBranchItem implements Comparable<SvnBranchItem> {
    private String myUrl;
    private Date myCreationDate;
    private long myRevision;

    public SvnBranchItem(final String url, final Date creationDate, final long revision) {
      myUrl = url;
      myCreationDate = creationDate;
      myRevision = revision;
    }

    public String getUrl() {
      return myUrl;
    }

    public Date getCreationDate() {
      return myCreationDate;
    }

    public long getRevision() {
      return myRevision;
    }

    public int compareTo(SvnBranchItem o) {
      return -myCreationDate.compareTo(o.getCreationDate());
    }
  }

  private static class BranchRenderer extends JPanel implements ListCellRenderer {
    private JLabel myUrlLabel = new JLabel();
    private JLabel myDateLabel = new JLabel();

    public BranchRenderer() {
      super(new BorderLayout());
      add(myUrlLabel, BorderLayout.WEST);
      add(myDateLabel, BorderLayout.EAST);
      myUrlLabel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
      myDateLabel.setHorizontalAlignment(JLabel.RIGHT);
      myDateLabel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
      myDateLabel.setForeground(UIUtil.getTextInactiveTextColor());
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (isSelected || cellHasFocus) {
        setBackground(UIUtil.getListSelectionBackground());
      }
      else {
        setBackground(UIUtil.getListBackground());
      }
      SvnBranchItem item = (SvnBranchItem) value;
      myUrlLabel.setText(SVNPathUtil.tail(item.getUrl()));
      myDateLabel.setText(SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(item.getCreationDate()));
      return this;
    }
  }
}
