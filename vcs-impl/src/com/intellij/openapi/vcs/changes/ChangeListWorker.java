package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** should work under _external_ lock
* just logic here: do modifications to group of change lists
*/
public class ChangeListWorker implements ChangeListsWriteOperations {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListWorker");

  private final Project myProject;
  private final Map<String, LocalChangeList> myMap;
  private LocalChangeList myDefault;

  public ChangeListWorker(final Project project) {
    myProject = project;
    myMap = new HashMap<String, LocalChangeList>();
  }

  private ChangeListWorker(final Project project, @NotNull final Collection<LocalChangeList> lists, @NotNull final String defaultName) {
    myProject = project;
    myMap = new HashMap<String, LocalChangeList>();
    LocalChangeList defaultList = null;
    for (LocalChangeList changeList : lists) {
      myMap.put(changeList.getName(), changeList);
      if (defaultName.equals(changeList.getName())) {
        defaultList = changeList;
      }
    }
    assert defaultList != null;
    myDefault = defaultList;
  }

  public void takeData(@NotNull final ChangeListWorker worker) {
    myMap.clear();
    myMap.putAll(worker.myMap);
    myDefault = worker.myDefault;
  }

  public ChangeListWorker copy() {
    List<LocalChangeList> copy = new ArrayList<LocalChangeList>(myMap.size());
    for (LocalChangeList list : myMap.values()) {
      copy.add(list.copy());
    }
    return new ChangeListWorker(myProject, copy, myDefault.getName());
  }

  public boolean findListByName(@NotNull final String name) {
    return myMap.containsKey(name);
  }

  @Nullable
  public LocalChangeList getCopyByName(final String name) {
    return myMap.get(name);
  }

  /**
   * @return if list with name exists, return previous default list name or null of there wasn't previous
   */
  @Nullable
  public String setDefault(final String name) {
    final LocalChangeList newDefault = myMap.get(name);
    if (newDefault == null) {
      return null;
    }
    String previousName = null;
    if (myDefault != null) {
      ((LocalChangeListImpl) myDefault).setDefault(false);
      previousName = myDefault.getName();
    }

    ((LocalChangeListImpl) newDefault).setDefault(true);
    myDefault = newDefault;

    return previousName;
  }

  public boolean setReadOnly(final String name, final boolean value) {
    final LocalChangeList list = myMap.get(name);
    if (list != null) {
      list.setReadOnly(value);
    }
    return list != null;
  }

  public void startProcessingChanges(@NotNull final String name, final VcsDirtyScope scope) {
    final LocalChangeList changeList = myMap.get(name);
    if (changeList != null) {
      ((LocalChangeListImpl) changeList).startProcessingChanges(myProject, scope);
    }
  }

  public LocalChangeList addChangeList(@NotNull final String name, @Nullable final String description) {
    return addChangeList(name, description, false);
  }

  public LocalChangeList addChangeList(@NotNull final String name, @Nullable final String description, final boolean inUpdate) {
    final boolean contains = myMap.containsKey(name);
    LOG.assertTrue(! contains, "Attempt to create duplicate changelist " + name);
    if (! contains) {
      final LocalChangeListImpl newList = (LocalChangeListImpl) LocalChangeList.createEmptyChangeList(myProject, name);
      if (description != null) {
        newList.setComment(description);
      }
      myMap.put(name, newList);
      if (inUpdate) {
        // scope is not important: nothing had been added jet, nothing to move to "old state" members
        newList.startProcessingChanges(myProject, null);
      }
      return newList.copy();
    }
    return null;
  }

  public boolean addChangeToList(@NotNull final String name, final Change change) {
    final LocalChangeList changeList = myMap.get(name);
    if (changeList != null) {
      ((LocalChangeListImpl) changeList).addChange(change);
    }
    return changeList != null;
  }

  public void addChangeToCorrespondingList(final Change change) {
    assert myDefault != null;
    for (LocalChangeList list : myMap.values()) {
      if (list.isDefault()) continue;
      if (((LocalChangeListImpl) list).processChange(change)) return;
    }
    ((LocalChangeListImpl) myDefault).processChange(change);
  }

  public boolean removeChangeList(@NotNull String name) {
    final LocalChangeList list = myMap.get(name);
    if (list == null) {
      return false;
    }
    if (list.isDefault()) {
      throw new RuntimeException(new IncorrectOperationException("Cannot remove default changelist"));
    }

    for (Change change : list.getChanges()) {
      ((LocalChangeListImpl) myDefault).addChange(change);
    }

    myMap.remove(list.getName());
    return true;
  }

  @Nullable
  public MultiMap<LocalChangeList, Change> moveChangesTo(final String name, final Change[] changes) {
    final LocalChangeListImpl changeList = (LocalChangeListImpl) myMap.get(name);
    if (changeList != null) {
      final MultiMap<LocalChangeList, Change> result = new MultiMap<LocalChangeList, Change>();
      for (LocalChangeList list : myMap.values()) {
        for (Change change : changes) {
          final Change removedChange = ((LocalChangeListImpl)list).removeChange(change);
          if (removedChange != null) {
            changeList.addChange(removedChange);
            result.putValue(list, removedChange);
          }
        }
      }
      return result;
    }
    return null;
  }

  public boolean editName(@NotNull final String fromName, @NotNull final String toName) {
    if (fromName.equals(toName)) return false;
    final LocalChangeList list = myMap.get(fromName);
    if (list != null) {
      ((LocalChangeListImpl) list).setNameImpl(toName);
      myMap.remove(fromName);
      myMap.put(toName, list);
    }
    return list != null;
  }

  @Nullable
  public String editComment(@NotNull final String fromName, final String newComment) {
    final LocalChangeList list = myMap.get(fromName);
    if (list != null) {
      final String oldComment = list.getComment();
      if (! Comparing.equal(oldComment, newComment)) {
        ((LocalChangeListImpl) list).setCommentImpl(newComment);
      }
      return oldComment;
    }
    return null;
  }

  public boolean editChangeList(@NotNull final String fromName, @NotNull final String toName, @Nullable final String comment) {
    final LocalChangeList list = myMap.get(fromName);
    if (list != null) {
      list.setName(toName);
      list.setComment(comment);
      myMap.remove(fromName);
      myMap.put(toName, list);
    }
    return list != null;
  }

  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Nullable
  public LocalChangeList getDefaultListCopy() {
    return myDefault == null ? null : myDefault.copy();
  }

  public Project getProject() {
    return myProject;
  }

  public void notifyStartProcessingChanges(final VcsDirtyScope scope) {
    for (LocalChangeList list : myMap.values()) {
      ((LocalChangeListImpl) list).startProcessingChanges(myProject, scope);
    }
  }

  public void notifyDoneProcessingChanges(final EventDispatcher<ChangeListListener> dispatcher) {
    List<ChangeList> changedLists = new ArrayList<ChangeList>();
      for (LocalChangeList list : myMap.values()) {
        if (((LocalChangeListImpl) list).doneProcessingChanges()) {
          changedLists.add(list);
        }
      }
    for(ChangeList changeList: changedLists) {
      dispatcher.getMulticaster().changeListChanged(changeList);
    }
  }

  public List<LocalChangeList> getListsCopy() {
    final List<LocalChangeList> result = new ArrayList<LocalChangeList>();
    for (LocalChangeList list : myMap.values()) {
      result.add(list.copy());
    }
    return result;
  }

  public String getDefaultListName() {
    return myDefault == null ? null : myDefault.getName();
  }
}
