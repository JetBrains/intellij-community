package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;

public interface CheckinChangeListSpecificComponent extends RefreshableOnComponent {
  void onChangeListSelected(final LocalChangeList list);
  Object getDataForCommit();
}
