/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.NullableFunction;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnFileUrlMapping;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

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
    show(project, file, callback, title, null);
  }

  public static void show(Project project, VirtualFile file, BranchSelectedCallback callback, final String title, final Component component) {
    final SvnFileUrlMapping urlMapping = SvnVcs.getInstance(project).getSvnFileUrlMapping();
    final SVNURL svnurl = urlMapping.getUrlForFile(virtualToIoFile(file));
    if (svnurl == null) {
      return;
    }
    final RootUrlInfo rootUrlInfo = urlMapping.getWcRootForUrl(svnurl.toString());
    if (rootUrlInfo == null) {
      return;
    }

    // not vcs root but wc root is ok
    showForBranchRoot(project, rootUrlInfo.getVirtualFile(), callback, title, component);
  }

  public static void showForBranchRoot(Project project, VirtualFile vcsRoot, BranchSelectedCallback callback, final String title) {
    showForBranchRoot(project, vcsRoot, callback, title, null);
  }

  public static void showForBranchRoot(Project project, VirtualFile vcsRoot, BranchSelectedCallback callback, final String title,
                                       final Component component) {
    final SvnBranchConfigurationNew configuration = SvnBranchConfigurationManager.getInstance(project).get(vcsRoot);
    final List<String> items = new ArrayList<>();
    if (! StringUtil.isEmptyOrSpaces(configuration.getTrunkUrl())) {
      items.add(getTrunkString(configuration));
    }
    for (String url : configuration.getBranchUrls()) {
      items.add(url);
    }
    items.add(CONFIGURE_MESSAGE);

    BranchBasesPopupStep step = new BranchBasesPopupStep(project, vcsRoot, configuration, callback, items, title, component);
    final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(step);
    step.showPopupAt(listPopup);
  }
  
  private static String getTrunkString(final SvnBranchConfigurationNew configuration) {
    return configuration.getTrunkUrl() + " (trunk)";
  }

  @NotNull
  private static String getBranchName(@NotNull SvnBranchItem branch) {
    return SVNPathUtil.tail(branch.getUrl());
  }

  private static class BranchBasesPopupStep extends BaseListPopupStep<String> {
    protected final Project myProject;
    private final VirtualFile myVcsRoot;
    private final SvnBranchConfigurationNew myConfiguration;
    private final boolean myTopLevel;
    private BranchSelectedCallback myCallback;
    private final Component myComponent;

    private static final String REFRESH_MESSAGE = SvnBundle.message("refresh.branches.item");
    private String myTrunkString;

    protected BranchBasesPopupStep(final Project project,
                                   final VirtualFile vcsRoot,
                                   final SvnBranchConfigurationNew configuration,
                                   boolean topLevel,
                                   final BranchSelectedCallback callback,
                                   Component component) {
      myProject = project;
      myVcsRoot = vcsRoot;
      myConfiguration = configuration;
      myTrunkString = getTrunkString(configuration);
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
        return doFinalStep(() -> BranchConfigurationDialog.configureBranches(myProject, myVcsRoot));
      }
      else if (myTrunkString.equals(selectedValue)) {
        return doFinalStep(() -> myCallback.branchSelected(myProject, myConfiguration, myConfiguration.getTrunkUrl(), -1));
      }
      else if (!myTopLevel || selectedValue.equals(myConfiguration.getTrunkUrl())) {
        return doFinalStep(() -> myCallback.branchSelected(myProject, myConfiguration, selectedValue, -1));
      }
      else {
        showBranchPopup(selectedValue);
      }
      return FINAL_CHOICE;
    }

    @Nullable
    private void loadBranches(final String selectedBranchesHolder, final Runnable runnable) {
      new Task.Backgroundable(myProject, SvnBundle.message("compare.with.branch.progress.loading.branches"), true) {
        @Override
        public void onFinished() {
          runnable.run();
        }

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final NewRootBunch manager = SvnBranchConfigurationManager.getInstance(myProject).getSvnBranchConfigManager();

          manager.reloadBranches(myVcsRoot, selectedBranchesHolder, InfoReliability.setByUser, false);
        }
      }.queue();
    }

    private void showBranchPopup(final String selectedValue) {
      List<SvnBranchItem> branches = myConfiguration.getBranches(selectedValue);
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
        //.setDimensionServiceKey("Svn.CompareWithBranchPopup")
        .setItemChoosenCallback(() -> {
          if (REFRESH_MESSAGE.equals(branchList.getSelectedValue())) {
            SwingUtilities.invokeLater(() -> loadBranches(selectedValue, () -> showBranchPopup(selectedValue)));
            return;
          }
          SvnBranchItem item = (SvnBranchItem)branchList.getSelectedValue();
          if (item != null) {
            myCallback.branchSelected(myProject, myConfiguration, item.getUrl(), item.getRevision());
          }
        })
        .setFilteringEnabled(
          (NullableFunction<Object, String>)item -> item instanceof SvnBranchItem ? getBranchName((SvnBranchItem)item) : null)
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
      myUrlLabel.setBorder(JBUI.Borders.empty(1));
      myDateLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      myDateLabel.setBorder(JBUI.Borders.empty(1));
      myDateLabel.setForeground(UIUtil.getInactiveTextColor());
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
        myDateLabel.setForeground(UIUtil.getInactiveTextColor());
        setForeground(foregroundColor);
      }
      if (value instanceof String) {
        myUrlLabel.setText((String) value);
        myDateLabel.setText("");
      } else {
        SvnBranchItem item = (SvnBranchItem) value;
        myUrlLabel.setText(getBranchName(item));
        final long creationMillis = item.getCreationDateMillis();
        myDateLabel.setText((creationMillis > 0) ? DateFormatUtil.formatDate(creationMillis) : "");
      }
      return this;
    }
  }
}
