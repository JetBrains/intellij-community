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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.dialogs.BranchConfigurationDialog;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
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
    void branchSelected(Project project, SvnBranchConfigurationNew configuration, String url, long revision);
  }

  public static void show(Project project, VirtualFile file, BranchSelectedCallback callback, final String title) {
    final SvnFileUrlMapping urlMapping = SvnVcs.getInstance(project).getSvnFileUrlMapping();
    final SVNURL svnurl = urlMapping.getUrlForFile(new File(file.getPath()));
    if (svnurl == null) {
      return;
    }
    final RootUrlInfo rootUrlInfo = urlMapping.getWcRootForUrl(svnurl.toString());
    if (rootUrlInfo == null) {
      return;
    }

    // not vcs root but wc root is ok
    showForBranchRoot(project, rootUrlInfo.getVirtualFile(), callback, title);
  }

  public static void showForBranchRoot(Project project, VirtualFile vcsRoot, BranchSelectedCallback callback, final String title) {
    showForBranchRoot(project, vcsRoot, callback, title, null);
  }

  public static void showForBranchRoot(Project project, VirtualFile vcsRoot, BranchSelectedCallback callback, final String title,
                                       final Component component) {
    final SvnBranchConfigurationNew configuration;
    try {
      configuration = SvnBranchConfigurationManager.getInstance(project).get(vcsRoot);
    }
    catch (VcsException e1) {
      Messages.showErrorDialog(project, SvnBundle.message("getting.branch.configuration.error", e1.getMessage()), title);
      return;
    }

    final List<String> items = new ArrayList<String>();
    if (configuration.getTrunkUrl() != null) {
      items.add(configuration.getTrunkUrl());
    }
    for (String url : configuration.getBranchUrls()) {
      items.add(url);
    }
    items.add(CONFIGURE_MESSAGE);

    BranchBasesPopupStep step = new BranchBasesPopupStep(project, vcsRoot, configuration, callback, items, title, component);
    final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(step);
    step.showPopupAt(listPopup);
  }

  private static class BranchBasesPopupStep extends BaseListPopupStep<String> {
    protected final Project myProject;
    private final VirtualFile myVcsRoot;
    private final SvnBranchConfigurationNew myConfiguration;
    private final boolean myTopLevel;
    private BranchSelectedCallback myCallback;
    private final Component myComponent;

    private static final String REFRESH_MESSAGE = SvnBundle.message("refresh.branches.item");

    protected BranchBasesPopupStep(final Project project,
                                   final VirtualFile vcsRoot,
                                   final SvnBranchConfigurationNew configuration,
                                   boolean topLevel,
                                   final BranchSelectedCallback callback,
                                   Component component) {
      myProject = project;
      myVcsRoot = vcsRoot;
      myConfiguration = configuration;
      myTopLevel = topLevel;
      myCallback = callback;
      myComponent = component;
    }

    public BranchBasesPopupStep(final Project project, final VirtualFile vcsRoot,
                                final SvnBranchConfigurationNew configuration,
                                final BranchSelectedCallback callback,
                                final List<String> items,
                                final String title,
                                Component component) {
      this(project, vcsRoot, configuration, true, callback, component);
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
      if (myTopLevel && ((myConfiguration.getTrunkUrl() == null) || (! value.startsWith(myConfiguration.getTrunkUrl())))) {
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
        return doFinalStep(new Runnable() {
          public void run() {
            BranchConfigurationDialog.configureBranches(myProject, myVcsRoot, true);
          }
        });
      }
      else if (!myTopLevel || selectedValue.equals(myConfiguration.getTrunkUrl())) {
        return doFinalStep(new Runnable() {
          public void run() {
            myCallback.branchSelected(myProject, myConfiguration, selectedValue, -1);
          }
        });
      }
      else {
        showBranchPopup(selectedValue, true);
      }
      return FINAL_CHOICE;
    }

    @Nullable
    private List<SvnBranchItem> loadBranches(final String selectedBranchesHolder, final boolean cached) {
      if (cached) {
        return myConfiguration.getBranches(selectedBranchesHolder);
      }

      final List<SvnBranchItem> result = new ArrayList<SvnBranchItem>();
      final ProgressManager pm = ProgressManager.getInstance();

      final boolean wasCanceled = ! pm.runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          final ProgressIndicator pi = pm.getProgressIndicator();
          final Semaphore s = new Semaphore();
          s.down();
          final Ref<Boolean> completedRef = new Ref<Boolean>();
          final SvnBranchConfigManager manager = SvnBranchConfigurationManager.getInstance(myProject).getSvnBranchConfigManager();
          manager.reloadBranches(myVcsRoot, selectedBranchesHolder, new Consumer<List<SvnBranchItem>>() {
            public void consume(final List<SvnBranchItem> svnBranchItems) {
              result.addAll(svnBranchItems);
              completedRef.set(true);
              s.up();
            }
          });
          while (true) {
            s.waitFor(500);
            if (Boolean.TRUE.equals(completedRef.get())) break;
            pi.checkCanceled();
          }
        }
      }, SvnBundle.message("compare.with.branch.progress.loading.branches"), true, myProject);


      if (wasCanceled) {
        return myConfiguration.getBranches(selectedBranchesHolder);
      } else {
        return result;
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

      final JList branchList = new JBList(items);
      branchList.setCellRenderer(new BranchRenderer());
      final JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(branchList)
        .setTitle(SVNPathUtil.tail(selectedValue))
        .setResizable(true)
        .setDimensionServiceKey("Svn.CompareWithBranchPopup")
        .setItemChoosenCallback(new Runnable() {
          public void run() {
            if (REFRESH_MESSAGE.equals(branchList.getSelectedValue())) {
              showBranchPopup(selectedValue, false);
              return;
            }
            SvnBranchItem item = (SvnBranchItem)branchList.getSelectedValue();
            if (item != null) {
              myCallback.branchSelected(myProject, myConfiguration, item.getUrl(), item.getRevision());
            }
          }
        })
        .createPopup();
      showPopupAt(popup);
    }

    public void showPopupAt(final JBPopup listPopup) {
      if (myComponent == null) {
        listPopup.showCenteredInCurrentWindow(myProject);
      } else {
        listPopup.showInCenterOf(myComponent);
      }
    }
  }

  private static class BranchRenderer extends JPanel implements ListCellRenderer {
    private final JLabel myUrlLabel = new JLabel();
    private final JLabel myDateLabel = new JLabel();

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
