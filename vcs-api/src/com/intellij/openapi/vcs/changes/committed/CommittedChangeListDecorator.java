package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface CommittedChangeListDecorator {
  @Nullable
  List<Pair<String,SimpleTextAttributes>> decorate(final CommittedChangeList list);
}
