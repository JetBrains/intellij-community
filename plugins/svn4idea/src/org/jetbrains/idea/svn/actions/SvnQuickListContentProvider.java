// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.vcs.actions.VcsQuickListContentProviderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman.Chernyatchik
 */
public class SvnQuickListContentProvider extends VcsQuickListContentProviderBase {
  @NotNull
  @Override
  protected String getVcsName() {
    return SvnVcs.VCS_NAME;
  }

  @Override
  protected List<AnAction> collectVcsSpecificActions(@NotNull ActionManager manager) {
    final List<AnAction> actions = new ArrayList<>();
    add("ChangesView.Move", manager, actions);
    add("Subversion.Copy", manager, actions);
    add("Subversion.Clenaup", manager, actions);
    return actions;
  }
}
