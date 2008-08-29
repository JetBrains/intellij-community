package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompositeChangeListFilteringStrategy implements ChangeListFilteringStrategy {
  private final Map<String, ChangeListFilteringStrategy> myDelegates;

  public CompositeChangeListFilteringStrategy() {
    myDelegates = new HashMap<String, ChangeListFilteringStrategy>();
  }

  public JComponent getFilterUI() {
    return null;
  }

  public void setFilterBase(final List<CommittedChangeList> changeLists) {
    for (ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.setFilterBase(changeLists);
    }
  }

  public void addChangeListener(final ChangeListener listener) {
    // not used
    for (ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.addChangeListener(listener);
    }
  }

  public void removeChangeListener(final ChangeListener listener) {
    // not used
    for (ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.removeChangeListener(listener);
    }
  }

  @NotNull
  public List<CommittedChangeList> filterChangeLists(final List<CommittedChangeList> changeLists) {
    List<CommittedChangeList> result = new ArrayList<CommittedChangeList>(changeLists);
    for (ChangeListFilteringStrategy delegate : myDelegates.values()) {
      result = delegate.filterChangeLists(result);
    }
    return result;
  }

  public void addStrategy(final String key, final ChangeListFilteringStrategy strategy) {
    myDelegates.put(key, strategy);
  }

  public ChangeListFilteringStrategy removeStrategy(final String key) {
    return myDelegates.remove(key);
  }
}
