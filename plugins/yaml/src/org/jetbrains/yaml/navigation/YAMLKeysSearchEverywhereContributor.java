// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.navigation;

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager;
import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.Plow;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;

import javax.swing.*;

import static org.jetbrains.yaml.navigation.YamlSearchEverywhereResultsCollectorKt.searchForKey;

public class YAMLKeysSearchEverywhereContributor implements SearchEverywhereContributor<YAMLKeyNavigationItem> {
  private final Project myProject;

  public YAMLKeysSearchEverywhereContributor(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull String getSearchProviderId() {
    return getClass().getSimpleName();
  }

  @Override
  public @NotNull String getGroupName() {
    return YAMLBundle.message("YAMLKeysSearchEverywhereContributor.group.name");
  }

  @Override
  public int getSortWeight() {
    // lets show yaml keys in the last order
    // all other implementations have weight from 50 to 300
    return 1000;
  }

  @Override
  public boolean showInFindResults() {
    return true;
  }

  @Override
  public void fetchElements(@NotNull String pattern,
                            @NotNull ProgressIndicator progressIndicator,
                            @NotNull Processor<? super YAMLKeyNavigationItem> consumer) {
    if (myProject == null || pattern.isEmpty()) {
      return;
    }

    Runnable task = () -> findKeys(consumer, pattern);
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      application.runReadAction(task);
    } else {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
      ProgressIndicatorUtils.yieldToPendingWriteActions();
      ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(task, progressIndicator);
    }
  }

  @Override
  public boolean processSelectedItem(@NotNull YAMLKeyNavigationItem selected, int modifiers, @NotNull String searchText) {
    ((Navigatable)selected).navigate(true);
    return true;
  }

  @Override
  public @NotNull ListCellRenderer<? super Object> getElementsRenderer() {
    return new NavigationItemListCellRenderer();
  }

  private void findKeys(@NotNull Processor<? super YAMLKeyNavigationItem> consumer, @NotNull String pattern) {
    if (ActionUtil.isDumbMode(myProject)) {
      return;
    }
    assert myProject != null;

    GlobalSearchScope filter = SearchEverywhereManager.getInstance(myProject).isEverywhere()
                               ? ProjectScope.getAllScope(myProject)
                               : ProjectScope.getProjectScope(myProject);
    Plow.ofSequence(searchForKey(pattern, filter, myProject))
      .cancellable()
      .mapNotNull(keyData -> new YAMLKeyNavigationItem(myProject,
                                                       keyData.getKey(),
                                                       keyData.getFile(),
                                                       keyData.getOffset(),
                                                       computePrettyLocation(keyData.getFile())))
      .processWith(consumer);
  }

  private @NotNull @NlsSafe String computePrettyLocation(VirtualFile file) {
    var root = ProjectFileIndex.getInstance(myProject).getContentRootForFile(file);
    if (root == null) {
      return file.getName();
    }
    String relativePath = VfsUtilCore.getRelativePath(file, root);
    return relativePath == null ? file.getName() : relativePath;
  }

  public static class Factory implements SearchEverywhereContributorFactory<YAMLKeyNavigationItem> {
    @Override
    public @NotNull SearchEverywhereContributor<YAMLKeyNavigationItem> createContributor(@NotNull AnActionEvent initEvent) {
      return new YAMLKeysSearchEverywhereContributor(initEvent.getProject());
    }
  }
}
