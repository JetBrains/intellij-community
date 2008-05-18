package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;

import java.util.List;

public interface VcsConfigurationChangeListener {
  Topic<Notification> BRANCHES_CHANGED = new Topic<Notification>("branch mapping changed", Notification.class);
  Topic<DetailedNotification> BRANCHES_CHANGED_RESPONSE = new Topic<DetailedNotification>("branch mapping changed (detailed)", DetailedNotification.class);

  interface Notification {
    void execute(final Project project, final VirtualFile vcsRoot);
  }

  interface DetailedNotification {
    void execute(final Project project, final VirtualFile vcsRoot, final List<CommittedChangeList> cachedList);
  }
}
