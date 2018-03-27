// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.api.Url;

import java.util.List;

public interface SelectedCommittedStuffChecker {
  void execute(final AnActionEvent event);

  boolean isValid();

  Url getSameBranch();

  VirtualFile getRoot();

  List<CommittedChangeList> getSelectedLists();

}
