// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

/**
 * @author Konstantin Kolosovsky.
 */
public class SingleRootSwitcher extends AutoSvnUpdater {

  @NotNull private final Url myUrl;

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
