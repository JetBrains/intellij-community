// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.actions.StandardVcsGroup;
import org.jetbrains.idea.svn.SvnVcs;

public class SubversionGroup extends StandardVcsGroup {
  @Override
  public AbstractVcs getVcs(Project project) {
    return SvnVcs.getInstance(project);
  }

  @Override
  public String getVcsName(final Project project) {
    return SvnVcs.VCS_NAME;
  }
}
