// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Holds what working copies we have for URLs
 */
@State(name = "SvnBranchMapperManager", storages = @Storage(StoragePathMacros.NON_ROAMABLE_FILE))
public final class SvnBranchMapperManager implements PersistentStateComponent<SvnBranchMapperManager.SvnBranchMapperHolder> {
  private SvnBranchMapperHolder myStateHolder;

  public static SvnBranchMapperManager getInstance() {
    return ApplicationManager.getApplication().getService(SvnBranchMapperManager.class);
  }

  public SvnBranchMapperManager() {
    myStateHolder = new SvnBranchMapperHolder();
  }

  @Override
  public SvnBranchMapperHolder getState() {
    return myStateHolder;
  }

  @Override
  public void loadState(@NotNull SvnBranchMapperHolder state) {
    myStateHolder = state;
  }

  public void put(@NotNull Url url, @NotNull File file) {
    myStateHolder.put(url.toDecodedString(), file.getAbsolutePath());
  }

  public void remove(@NotNull Url url, @NotNull File value) {
    Set<String> set = myStateHolder.get(url.toDecodedString());
    if (set != null) {
      set.remove(value.getAbsolutePath());
    }
  }

  public void notifyBranchesChanged(final Project project, final VirtualFile vcsRoot, final SvnBranchConfigurationNew configuration) {
    for (Map.Entry<Url, File> entry : configuration.getUrl2FileMappings(project, vcsRoot).entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  public Set<String> get(@NotNull Url url) {
    return myStateHolder.get(url.toDecodedString());
  }

  public static final class SvnBranchMapperHolder {
    public final Map<String, Set<String>> myMapping = new HashMap<>();

    public void put(final String key, final String value) {
      Set<String> files = myMapping.get(key);
      if (files == null) {
        files = new HashSet<>();
        myMapping.put(key, files);
      }
      files.add(value);
    }

    public Set<String> get(String key) {
      return myMapping.get(key);
    }
  }
}
