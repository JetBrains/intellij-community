package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.svn.SvnBranchConfiguration;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.dialogs.BranchConfigurationDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

/**
 * @author yole
 */
public class SelectBranchPopup {
  private SelectBranchPopup() {
  }

  public interface BranchSelectedCallback {
    void branchSelected(Project project, VirtualFile virtualFile, SvnBranchConfiguration configuration,
                        String url, long revision);
  }

  public static void show(Project project, VirtualFile virtualFile, BranchSelectedCallback callback) {
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

    BranchBasesPopupStep step = new BranchBasesPopupStep(project, virtualFile, vcsRoot, configuration, callback);
    JBPopupFactory.getInstance().createListPopup(step).showCenteredInCurrentWindow(project);

  }

  private static class BranchBasesPopupStep extends BaseListPopupStep<String> {
    protected final Project myProject;
    private final VirtualFile myVirtualFile;
    private final VirtualFile myVcsRoot;
    private final SvnBranchConfiguration myConfiguration;
    private final boolean myTopLevel;
    private BranchSelectedCallback myCallback;

    protected BranchBasesPopupStep(final Project project, final VirtualFile virtualFile, final VirtualFile vcsRoot,
                                final SvnBranchConfiguration configuration, boolean topLevel, final BranchSelectedCallback callback) {
      myProject = project;
      myVirtualFile = virtualFile;
      myVcsRoot = vcsRoot;
      myConfiguration = configuration;
      myTopLevel = topLevel;
      myCallback = callback;
    }

    public BranchBasesPopupStep(final Project project, final VirtualFile virtualFile, final VirtualFile vcsRoot,
                                final SvnBranchConfiguration configuration, final BranchSelectedCallback callback) {
      this(project, virtualFile, vcsRoot, configuration, true, callback);
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
            myCallback.branchSelected(myProject, myVirtualFile, myConfiguration, selectedValue, -1);
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
                    myCallback.branchSelected(myProject, myVirtualFile, myConfiguration, item.myUrl, item.getRevision());
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

    @Nullable
    public Date getCreationDate() {
      return myCreationDate;
    }

    public long getRevision() {
      return myRevision;
    }

    public int compareTo(SvnBranchItem o) {
      return -Comparing.compare(myCreationDate, o.getCreationDate());
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
      final Date creationDate = item.getCreationDate();
      myDateLabel.setText(creationDate != null ? SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(creationDate) : "");
      return this;
    }
  }
}