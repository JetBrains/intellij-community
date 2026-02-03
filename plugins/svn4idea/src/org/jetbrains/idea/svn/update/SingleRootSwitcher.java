// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

public class SingleRootSwitcher extends AutoSvnUpdater {

  private final @NotNull Url myUrl;

  public SingleRootSwitcher(Project project, @NotNull FilePath root, @NotNull Url url) {
    super(project, new FilePath[]{root});

    myUrl = url;
  }

  @Override
  protected void configureUpdateRootInfo(@NotNull FilePath root, @NotNull UpdateRootInfo info) {
    super.configureUpdateRootInfo(root, info);

    info.setUrl(myUrl);
  }
}
