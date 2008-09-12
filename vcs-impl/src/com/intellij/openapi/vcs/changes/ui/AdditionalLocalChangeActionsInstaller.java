package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AdditionalLocalChangeActionsInstaller {
  @Nullable
  public static List<AnAction> calculateActions(final Project project, final Collection<Change> changes) {
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    final Map<String, AbstractVcs> map = new HashMap<String, AbstractVcs>();
    for (Change change : changes) {
      if (change.getAfterRevision() != null) {
        final AbstractVcs vcs = plVcsManager.getVcsFor(change.getAfterRevision().getFile());
        if ((vcs != null) && (! map.containsKey(vcs.getName()))) {
          map.put(vcs.getName(), vcs);
        }
      }
    }
    if (map.isEmpty()) {
      return null;
    }
    final List<AnAction> result = new ArrayList<AnAction>(1);
    for (AbstractVcs vcs : map.values()) {
      result.addAll(vcs.getAdditionalActionsForLocalChange());
    }
    return result;
  }
}
