package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.List;

/**
 * @author yole
 */
public interface ChangeListFilteringStrategy {
  @Nullable
  JComponent getFilterUI();
  void setFilterBase(List<CommittedChangeList> changeLists);
  void addChangeListener(ChangeListener listener);
  void removeChangeListener(ChangeListener listener);

  @NotNull
  List<CommittedChangeList> filterChangeLists(List<CommittedChangeList> changeLists);

  ChangeListFilteringStrategy NONE = new ChangeListFilteringStrategy() {
    public String toString() {
      return "None";
    }

    @Nullable
    public JComponent getFilterUI() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setFilterBase(List<CommittedChangeList> changeLists) {
    }

    public void addChangeListener(ChangeListener listener) {
    }

    public void removeChangeListener(ChangeListener listener) {
    }

    @NotNull
    public List<CommittedChangeList> filterChangeLists(List<CommittedChangeList> changeLists) {
      return changeLists;
    }
  };
}