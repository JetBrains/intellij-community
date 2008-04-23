package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.List;

public interface SelectedCommittedStuffChecker {
  void execute(final AnActionEvent event);

  boolean isValid();

  List<CommittedChangeList> getChangeListsList();

  MergerFactory createFactory();
}
