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
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.SVNURL;

import java.util.List;
import java.util.Map;

public interface SvnBranchConfigManager {
  void updateForRoot(@NotNull VirtualFile root, @NotNull InfoStorage<SvnBranchConfigurationNew> config,
                     @Nullable final PairConsumer<SvnBranchConfigurationNew, SvnBranchConfigurationNew> callbackOnUpdate);

  void updateBranches(@NotNull VirtualFile root, @NotNull String branchesParent,
                             @NotNull InfoStorage<List<SvnBranchItem>> items);

  @NotNull
  SvnBranchConfigurationNew getConfig(@NotNull VirtualFile root);

  void reloadBranches(@NotNull VirtualFile root, @NotNull String branchParentUrl,
                             Consumer<List<SvnBranchItem>> callback);
  @Nullable
  @CalledInBackground
  SVNURL getWorkingBranchWithReload(final SVNURL svnurl, final VirtualFile root);
  
  Map<VirtualFile, SvnBranchConfigurationNew> getMapCopy();
}
