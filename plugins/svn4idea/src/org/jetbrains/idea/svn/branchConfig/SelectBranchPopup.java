// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnFileUrlMapping;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;

public final class SelectBranchPopup {
  private SelectBranchPopup() {
  }

  public interface BranchSelectedCallback {
    void branchSelected(Project project, SvnBranchConfigurationNew configuration, @NotNull Url url, long revision);
  }

  public static void show(@NotNull Project project,
                          @NotNull VirtualFile file,
                          @NotNull BranchSelectedCallback callback,
                          @Nullable String title) {
    show(project, file, callback, title, null);
  }

  public static void show(@NotNull Project project,
                          @NotNull VirtualFile file,
                          @NotNull BranchSelectedCallback callback,
                          @Nullable String title,
                          @Nullable Component component) {
    SvnFileUrlMapping urlMapping = SvnVcs.getInstance(project).getSvnFileUrlMapping();
    Url svnurl = urlMapping.getUrlForFile(virtualToIoFile(file));

    if (svnurl != null) {
      RootUrlInfo rootUrlInfo = urlMapping.getWcRootForUrl(svnurl);

      if (rootUrlInfo != null) {
        // not vcs root but wc root is ok
        showForBranchRoot(project, rootUrlInfo.getVirtualFile(), callback, title, component);
      }
    }
  }

  public static void showForBranchRoot(@NotNull Project project,
                                       @NotNull VirtualFile vcsRoot,
                                       @NotNull BranchSelectedCallback callback,
                                       @Nullable String title) {
    showForBranchRoot(project, vcsRoot, callback, title, null);
  }

  public static void showForBranchRoot(@NotNull Project project,
                                       @NotNull VirtualFile vcsRoot,
                                       @NotNull BranchSelectedCallback callback,
                                       @Nullable String title,
                                       @Nullable Component component) {
    SvnBranchConfigurationNew configuration = SvnBranchConfigurationManager.getInstance(project).get(vcsRoot);
    List<Object> items = new ArrayList<>();

    addIfNotNull(items, configuration.getTrunk());
    items.addAll(configuration.getBranchLocations());
    items.add(getConfigureMessage());

    BranchBasesPopupStep step = new BranchBasesPopupStep(project, vcsRoot, configuration, callback, items, title, component);
    step.showPopupAt(JBPopupFactory.getInstance().createListPopup(step));
  }

  @NotNull
  private static String getBranchName(@NotNull SvnBranchItem branch) {
    return branch.getUrl().getTail();
  }

  private static class BranchBasesPopupStep extends BaseListPopupStep<Object> {
    @NotNull private final Project myProject;
    @NotNull private final VirtualFile myVcsRoot;
    @NotNull private final SvnBranchConfigurationNew myConfiguration;
    @NotNull private final BranchSelectedCallback myCallback;
    @Nullable private final Component myComponent;

    BranchBasesPopupStep(@NotNull Project project,
                                @NotNull VirtualFile vcsRoot,
                                @NotNull SvnBranchConfigurationNew configuration,
                                @NotNull BranchSelectedCallback callback,
                                @NotNull List<Object> items,
                                @Nullable String title,
                                @Nullable Component component) {
      myProject = project;
      myVcsRoot = vcsRoot;
      myConfiguration = configuration;
      myCallback = callback;
      myComponent = component;
      init(title, items, null);
    }

    @Override
    public ListSeparator getSeparatorAbove(Object value) {
      return getConfigureMessage().equals(value) ? new ListSeparator("") : null;
    }

    @NotNull
    @Override
    public String getTextFor(@NotNull Object value) {
      if (value instanceof Url) {
        Url url = (Url)value;
        String suffix = url.equals(myConfiguration.getTrunk()) ? " (trunk)" : "...";

        return url.getTail() + suffix;
      }
      return String.valueOf(value);
    }

    @Override
    public PopupStep onChosen(Object selectedValue, boolean finalChoice) {
      if (getConfigureMessage().equals(selectedValue)) {
        return doFinalStep(() -> BranchConfigurationDialog.configureBranches(myProject, myVcsRoot));
      }

      Url url = (Url)selectedValue;
      if (url.equals(myConfiguration.getTrunk())) {
        return doFinalStep(() -> myCallback.branchSelected(myProject, myConfiguration, url, -1));
      }
      else {
        return doFinalStep(() -> showBranchPopup(url));
      }
    }

    private void loadBranches(@NotNull Url branchLocation, @NotNull Runnable runnable) {
      new Task.Backgroundable(myProject, SvnBundle.message("compare.with.branch.progress.loading.branches"), true) {
        @Override
        public void onFinished() {
          runnable.run();
        }

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          NewRootBunch manager = SvnBranchConfigurationManager.getInstance(myProject).getSvnBranchConfigManager();

          manager.reloadBranches(myVcsRoot, branchLocation, InfoReliability.setByUser, false);
        }
      }.queue();
    }

    private void showBranchPopup(@NotNull Url branchLocation) {
      List<SvnBranchItem> branches = myConfiguration.getBranches(branchLocation);
      List<Object> items = new ArrayList<>(branches);
      items.add(getRefreshMessage());

      JBPopup popup =
        JBPopupFactory.getInstance().createPopupChooserBuilder(items)
                      .setTitle(branchLocation.getTail())
                      .setRenderer(new BranchRenderer())
                      .setResizable(true)
                      .setItemChosenCallback((v) -> {
                        if (getRefreshMessage().equals(v)) {
                          loadBranches(branchLocation, () -> showBranchPopup(branchLocation));
                          return;
                        }
                        SvnBranchItem item = (SvnBranchItem)v;
                        if (item != null) {
                          myCallback.branchSelected(myProject, myConfiguration, item.getUrl(), item.getRevision());
                        }
                      })
                      .setNamerForFiltering(
                        item -> item instanceof SvnBranchItem ? getBranchName((SvnBranchItem)item) : null)
                      .createPopup();
      showPopupAt(popup);
    }

    public void showPopupAt(@NotNull JBPopup listPopup) {
      if (myComponent == null) {
        listPopup.showCenteredInCurrentWindow(myProject);
      } else {
        listPopup.showInCenterOf(myComponent);
      }
    }

    private static String getRefreshMessage() {
      return SvnBundle.message("refresh.branches.item");
    }
  }

  private static class BranchRenderer extends JPanel implements ListCellRenderer<Object> {
    private final JLabel myUrlLabel = new JLabel();
    private final JLabel myDateLabel = new JLabel();

    BranchRenderer() {
      super(new BorderLayout());
      add(myUrlLabel, BorderLayout.WEST);
      add(myDateLabel, BorderLayout.EAST);
      myUrlLabel.setBorder(JBUI.Borders.empty(1));
      myDateLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      myDateLabel.setBorder(JBUI.Borders.empty(1));
      myDateLabel.setForeground(UIUtil.getInactiveTextColor());
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (isSelected || cellHasFocus) {
        setBackground(UIUtil.getListSelectionBackground(true));
        Color selectedForegroundColor = UIUtil.getListSelectionForeground();
        myUrlLabel.setForeground(selectedForegroundColor);
        myDateLabel.setForeground(selectedForegroundColor);
        setForeground(selectedForegroundColor);
      }
      else {
        setBackground(UIUtil.getListBackground());
        Color foregroundColor = UIUtil.getListForeground();
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
        long creationMillis = item.getCreationDateMillis();
        myDateLabel.setText(creationMillis > 0 ? DateFormatUtil.formatDate(creationMillis) : "");
      }
      return this;
    }
  }

  private static String getConfigureMessage() {
    return SvnBundle.message("configure.branches.item");
  }
}
