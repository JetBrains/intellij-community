/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @author Konstantin Kolosovsky.
 */
public class SingleRootSwitcher extends AutoSvnUpdater {

  @NotNull private final SVNURL myUrl;

  public SingleRootSwitcher(Project project, @NotNull FilePath root, @NotNull SVNURL url) {
    super(project, new FilePath[]{root});

    myUrl = url;
  }

  @Override
  protected void configureUpdateRootInfo(@NotNull FilePath root, @NotNull UpdateRootInfo info) {
    super.configureUpdateRootInfo(root, info);

    info.setUrl(myUrl);
  }
}
