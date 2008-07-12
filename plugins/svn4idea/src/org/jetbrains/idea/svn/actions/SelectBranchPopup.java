package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.dialogs.BranchConfigurationDialog;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author yole
 */
public class SelectBranchPopup {
  private final static String CONFIGURE_MESSAGE = SvnBundle.message("configure.branches.item");

  private SelectBranchPopup() {
  }

  public interface BranchSelectedCallback {
    void branchSelected(Project project, SvnBranchConfiguration configuration, String url, long revision);
  }

  public static void show(Project project, VirtualFile file, BranchSelectedCallback callback, final String title) {
    final SvnFileUrlMapping urlMapping = SvnVcs.getInstance(project).getSvnFileUrlMapping();
    final SVNURL svnurl = urlMapping.getUrlForFile(new File(file.getPath()));
    if (svnurl == null) {
      return;
    }
    final RootMixedInfo rootInfo = urlMapping.getWcRootForUrl(svnurl.toString());
    if (rootInfo == null) {
      return;
    }

    showForBranchRoot(project, rootInfo.getFile(), callback, title);
  }

  public static void showForBranchRoot(Project project, VirtualFile vcsRoot, BranchSelectedCallback callback, final String title) {
    final SvnBranchConfiguration configuration;
    try {
      configuration = SvnBranchConfigurationManager.getInstance(project).get(vcsRoot);
    }
    catch (VcsException e1) {
      Messages.showErrorDialog(project, SvnBundle.message("getting.branch.configuration.error", e1.getMessage()), title);
      return;
    }

    final List<String> items = new ArrayList<String>();
    items.add(configuration.getTrunkUrl());
    for (String url : configuration.getBranchUrls()) {
      items.add(url);
    }
    items.add(CONFIGURE_MESSAGE);

    BranchBasesPopupStep step = new BranchBasesPopupStep(project, vcsRoot, configuration, callback, items, title);
    JBPopupFactory.getInstance().createListPopup(step).showCenteredInCurrentWindow(project);
  }

  private static class BranchBasesPopupStep extends BaseListPopupStep<String> {
    protected final Project myProject;
    private final VirtualFile myVcsRoot;
    private final SvnBranchConfiguration myConfiguration;
    private final boolean myTopLevel;
    private BranchSelectedCallback myCallback;

    private static final String REFRESH_MESSAGE = SvnBundle.message("refresh.branches.item");

    protected BranchBasesPopupStep(final Project project, final VirtualFile vcsRoot, final SvnBranchConfiguration configuration, boolean topLevel,
                                   final BranchSelectedCallback callback) {
      myProject = project;
      myVcsRoot = vcsRoot;
      myConfiguration = configuration;
      myTopLevel = topLevel;
      myCallback = callback;
    }

    public BranchBasesPopupStep(final Project project, final VirtualFile vcsRoot,
                                final SvnBranchConfiguration configuration, final BranchSelectedCallback callback, final List<String> items,
                                final String title) {
      this(project, vcsRoot, configuration, true, callback);
      init(title, items, null);
    }

    @Override
    public ListSeparator getSeparatorAbove(final String value) {
      return (CONFIGURE_MESSAGE.equals(value)) ||
             (REFRESH_MESSAGE.equals(value)) ? new ListSeparator("") : null;
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
      if (CONFIGURE_MESSAGE.equals(selectedValue)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            BranchConfigurationDialog.configureBranches(myProject, myVcsRoot, true);
          }
        });
      }
      else if (!myTopLevel || selectedValue.equals(myConfiguration.getTrunkUrl())) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            myCallback.branchSelected(myProject, myConfiguration, selectedValue, -1);
          }
        });
      }
      else {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            showBranchPopup(selectedValue, true);
          }
        });
      }
      return null;
    }

    @Nullable
    private List<SvnBranchItem> loadBranches(final String selectedBranchesHolder, final boolean cached) {
      try {
        return cached ? myConfiguration.getBranches(selectedBranchesHolder, myProject, true) :
            myConfiguration.reloadBranches(selectedBranchesHolder, myProject, myVcsRoot);
      }
      catch (SVNException e) {
        Messages.showErrorDialog(myProject, e.getMessage(), SvnBundle.message("compare.with.branch.list.error"));
        return null;
      }
    }

    private void showBranchPopup(final String selectedValue, final boolean cached) {
      List<SvnBranchItem> branches = loadBranches(selectedValue, cached);
      if (branches == null) {
        return;
      }

      final Object[] items = new Object[branches.size() + 1];
      System.arraycopy(branches.toArray(), 0, items, 0, branches.size());
      items[items.length - 1] = REFRESH_MESSAGE;

      final JList branchList = new JList(items);
      branchList.setCellRenderer(new BranchRenderer());
      JBPopupFactory.getInstance().createListPopupBuilder(branchList)
              .setTitle(SVNPathUtil.tail(selectedValue))
              .setResizable(true)
              .setDimensionServiceKey("Svn.CompareWithBranchPopup")
              .setItemChoosenCallback(new Runnable() {
                public void run() {
                  if (REFRESH_MESSAGE.equals(branchList.getSelectedValue())) {
                    showBranchPopup(selectedValue, false);
                    return;
                  }
                  SvnBranchItem item = (SvnBranchItem) branchList.getSelectedValue();
                  if (item != null) {
                    myCallback.branchSelected(myProject, myConfiguration, item.getUrl(), item.getRevision());
                  }
                }
              })
              .createPopup().showCenteredInCurrentWindow(myProject);
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
        final Color selectedForegroundColor = UIUtil.getListSelectionForeground();
        myUrlLabel.setForeground(selectedForegroundColor);
        myDateLabel.setForeground(selectedForegroundColor);
        setForeground(selectedForegroundColor);
      }
      else {
        setBackground(UIUtil.getListBackground());
        final Color foregroundColor = UIUtil.getListForeground();
        myUrlLabel.setForeground(foregroundColor);
        myDateLabel.setForeground(UIUtil.getTextInactiveTextColor());
        setForeground(foregroundColor);
      }
      if (value instanceof String) {
        myUrlLabel.setText((String) value);
        myDateLabel.setText("");
      } else {
        SvnBranchItem item = (SvnBranchItem) value;
        myUrlLabel.setText(SVNPathUtil.tail(item.getUrl()));
        final long creationMillis = item.getCreationDateMillis();
        myDateLabel.setText((creationMillis > 0) ? SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(new Date(creationMillis)) : "");
      }
      return this;
    }
  }
}