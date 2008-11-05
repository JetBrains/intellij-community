package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/** should work under _external_ lock
* just logic here: do modifications to group of change lists
*/
public class ChangeListWorker implements ChangeListsWriteOperations {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListWorker");

  private final Project myProject;
  private final Map<String, LocalChangeList> myMap;
  private LocalChangeList myDefault;

  private ChangeListsIndexes myIdx;

  public ChangeListWorker(final Project project) {
    myProject = project;
    myMap = new HashMap<String, LocalChangeList>();
    myIdx = new ChangeListsIndexes();
  }

  private ChangeListWorker(final ChangeListWorker worker) {
    myProject = worker.myProject;
    myMap = new HashMap<String, LocalChangeList>();
    myIdx = new ChangeListsIndexes(worker.myIdx);
    
    LocalChangeList defaultList = null;
    for (LocalChangeList changeList : worker.myMap.values()) {
      final LocalChangeList copy = changeList.copy();
      
      final String changeListName = copy.getName();
      myMap.put(changeListName, copy);
      if (copy.isDefault()) {
        defaultList = copy;
      }
    }
    if (defaultList == null) {
      LOG.info("default list not found when copy");
      defaultList = myMap.get(worker.getDefaultListName());
    }

    if (defaultList == null) {
      LOG.info("default list not found when copy in original object too");
      if (! myMap.isEmpty()) {
        defaultList = myMap.values().iterator().next();
      } else {
        LOG.info("no changelists at all");
      }
    }
    myDefault = defaultList;
  }

  public void takeData(@NotNull final ChangeListWorker worker) {
    myMap.clear();
    myMap.putAll(worker.myMap);
    myDefault = worker.myDefault;
    myIdx = new ChangeListsIndexes(worker.myIdx);
  }

  public ChangeListWorker copy() {
    return new ChangeListWorker(this);
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
      correctChangeListEditHandler(myDefault);
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

  public LocalChangeList addChangeList(@NotNull final String name, @Nullable final String description) {
    return addChangeList(name, description, false);
  }

  LocalChangeList addChangeList(@NotNull final String name, @Nullable final String description, final boolean inUpdate) {
    final boolean contains = myMap.containsKey(name);
    LOG.assertTrue(! contains, "Attempt to create duplicate changelist " + name);
    if (! contains) {
      final LocalChangeListImpl newList = (LocalChangeListImpl) LocalChangeList.createEmptyChangeList(myProject, name);

      if (description != null) {
        newList.setCommentImpl(description);
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
      myIdx.changeAdded(change);
      correctChangeListEditHandler(changeList);
    }
    return changeList != null;
  }

  public void addChangeToCorrespondingList(final Change change) {
    assert myDefault != null;
    for (LocalChangeList list : myMap.values()) {
      if (list.isDefault()) continue;
      if (((LocalChangeListImpl) list).processChange(change)) {
        myIdx.changeAdded(change);
        correctChangeListEditHandler(list);
        return;
      }
    }
    ((LocalChangeListImpl) myDefault).processChange(change);
    myIdx.changeAdded(change);
    correctChangeListEditHandler(myDefault);
  }

  public boolean removeChangeList(@NotNull String name) {
    final LocalChangeList list = myMap.get(name);
    if (list == null) {
      return false;
    }
    if (list.isDefault()) {
      throw new RuntimeException(new IncorrectOperationException("Cannot remove default changelist"));
    }
    final String listName = list.getName();

    for (Change change : list.getChanges()) {
      ((LocalChangeListImpl) myDefault).addChange(change);
    }

    myMap.remove(listName);
    return true;
  }

  void initialized() {
    for (LocalChangeList list : myMap.values()) {
      correctChangeListEditHandler(list);
    }
  }

  // since currently it is only for P4, "composite" edit handler is not created - it does not make sence
  private void correctChangeListEditHandler(final LocalChangeList list) {
  }

  @Nullable
  public MultiMap<LocalChangeList, Change> moveChangesTo(final String name, final Change[] changes) {
    final LocalChangeListImpl changeList = (LocalChangeListImpl) myMap.get(name);
    if (changeList != null) {
      final MultiMap<LocalChangeList, Change> result = new MultiMap<LocalChangeList, Change>();
      for (LocalChangeList list : myMap.values()) {
        if (list.equals(changeList)) continue;
        for (Change change : changes) {
          final Change removedChange = ((LocalChangeListImpl)list).removeChange(change);
          if (removedChange != null) {
            correctChangeListEditHandler(list);

            changeList.addChange(removedChange);
            result.putValue(list, removedChange);
          }
        }
      }
      correctChangeListEditHandler(changeList);
      return result;
    }
    return null;
  }

  public boolean editName(@NotNull final String fromName, @NotNull final String toName) {
    if (fromName.equals(toName)) return false;
    final LocalChangeList list = myMap.get(fromName);
    final boolean canEdit = list != null && (!list.isReadOnly());
    if (canEdit) {
      final LocalChangeListImpl listImpl = (LocalChangeListImpl) list;
      listImpl.setNameImpl(toName);
      myMap.remove(fromName);
      myMap.put(toName, list);
      final ChangeListEditHandler editHandler = listImpl.getEditHandler();
      if (editHandler != null) {
        listImpl.setCommentImpl(editHandler.changeCommentOnChangeName(toName, listImpl.getComment()));
      }
    }
    return canEdit;
  }

  @Nullable
  public String editComment(@NotNull final String fromName, final String newComment) {
    final LocalChangeList list = myMap.get(fromName);
    if (list != null && (! list.isReadOnly())) {
      final String oldComment = list.getComment();
      if (! Comparing.equal(oldComment, newComment)) {
        final LocalChangeListImpl listImpl = (LocalChangeListImpl) list;
        listImpl.setCommentImpl(newComment);
        final ChangeListEditHandler editHandler = listImpl.getEditHandler();
        if (editHandler != null) {
          listImpl.setNameImpl(editHandler.changeNameOnChangeComment(listImpl.getName(), listImpl.getComment()));
          if (! fromName.equals(listImpl.getName())) {
            myMap.remove(fromName);
            myMap.put(listImpl.getName(), list);
          }
        }
      }
      return oldComment;
    }
    return null;
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
    final Collection<Change> oldChanges = new ArrayList<Change>();
    for (LocalChangeList list : myMap.values()) {
      oldChanges.addAll(((LocalChangeListImpl) list).startProcessingChanges(myProject, scope));
    }
    for (Change change : oldChanges) {
      myIdx.changeRemoved(change);
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

  public List<File> getAffectedPaths() {
    return myIdx.getAffectedPaths();
  }

  @NotNull
  public List<VirtualFile> getAffectedFiles() {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (LocalChangeList list : myMap.values()) {
      for (Change change : list.getChanges()) {
        final ContentRevision before = change.getBeforeRevision();
        final ContentRevision after = change.getAfterRevision();
        if (before != null) {
          final VirtualFile file = before.getFile().getVirtualFile();
          if (file != null) {
            result.add(file);
          }
        }
        if (after != null) {
          final VirtualFile file = after.getFile().getVirtualFile();
          if (file != null) {
            result.add(file);
          }
        }
      }
    }
    return result;
  }

  public LocalChangeList getListCopy(@NotNull final VirtualFile file) {
    for (LocalChangeList list : myMap.values()) {
      for (Change change : list.getChanges()) {
        if (change.getAfterRevision() != null &&
            Comparing.equal(change.getAfterRevision().getFile().getVirtualFile(), file)) {
          return list.copy();
        }
        if (change.getBeforeRevision() != null &&
            Comparing.equal(change.getBeforeRevision().getFile().getVirtualFile(), file)) {
          return list.copy();
        }
      }
    }
    return null;
  }

  @Nullable
  public Change getChangeForPath(final FilePath file) {
    for (LocalChangeList list : myMap.values()) {
      for (Change change : list.getChanges()) {
        final ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null && afterRevision.getFile().equals(file)) {
          return change;
        }
        final ContentRevision beforeRevision = change.getBeforeRevision();
        if (beforeRevision != null && beforeRevision.getFile().equals(file)) {
          return change;
        }
      }
    }
    return null;
  }

  public FileStatus getStatus(final VirtualFile file) {
    return myIdx.getStatus(file);
  }

  @Nullable
  public LocalChangeList listForChange(final Change change) {
    for (LocalChangeList list : myMap.values()) {
      if (list.getChanges().contains(change)) return list.copy();
    }
    return null;
  }

  @Nullable
  public String listNameIfOnlyOne(final @Nullable Change[] changes) {
    if (changes == null || changes.length == 0) {
      return null;
    }

    final Change first = changes[0];

    for (LocalChangeList list : myMap.values()) {
      final Collection<Change> listChanges = list.getChanges();
      if (listChanges.contains(first)) {
        // must contain all other
        for (int i = 1; i < changes.length; i++) {
          final Change change = changes[i];
          if (! listChanges.contains(change)) {
            return null;
          }
        }
        return list.getName();
      }
    }
    return null;
  }

  @NotNull
  public Collection<Change> getChangesIn(final FilePath dirPath) {
    List<Change> changes = new ArrayList<Change>();
    for (ChangeList list : myMap.values()) {
      for (Change change : list.getChanges()) {
        final ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null && afterRevision.getFile().isUnder(dirPath, false)) {
          changes.add(change);
          continue;
        }

        final ContentRevision beforeRevision = change.getBeforeRevision();
        if (beforeRevision != null && beforeRevision.getFile().isUnder(dirPath, false)) {
          changes.add(change);
        }
      }
    }
    return changes;
  }

  ChangeListManagerGate createSelfGate() {
    return new MyGate(this);
  }

  private static class MyGate implements ChangeListManagerGate {
    private final ChangeListWorker myWorker;

    private MyGate(final ChangeListWorker worker) {
      myWorker = worker;
    }

    @Nullable
    public LocalChangeList findChangeList(final String name) {
      return myWorker.getCopyByName(name);
    }

    public LocalChangeList addChangeList(final String name, final String comment) {
      return myWorker.addChangeList(name, comment, true);
    }

    public LocalChangeList findOrCreateList(final String name, final String comment) {
      LocalChangeList list = myWorker.getCopyByName(name);
      if (list == null) {
        list = addChangeList(name, comment);
      }
      return list;
    }

    public void editComment(final String name, final String comment) {
      myWorker.editComment(name, comment);
    }
  }
}
