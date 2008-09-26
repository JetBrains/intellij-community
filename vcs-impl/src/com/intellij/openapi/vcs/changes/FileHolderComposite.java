package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class FileHolderComposite implements FileHolder {
  private final Map<HolderType, FileHolder> myHolders;

  public FileHolderComposite(final Project project) {
    myHolders = new HashMap<HolderType, FileHolder>();
    myHolders.put(FileHolder.HolderType.DELETED, new DeletedFilesHolder());
    myHolders.put(FileHolder.HolderType.UNVERSIONED, new VirtualFileHolder(project, FileHolder.HolderType.UNVERSIONED));
    myHolders.put(FileHolder.HolderType.SWITCHED, new SwitchedFileHolder(project));
    myHolders.put(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING, new VirtualFileHolder(project, FileHolder.HolderType.MODIFIED_WITHOUT_EDITING));
    myHolders.put(FileHolder.HolderType.IGNORED, new VirtualFileHolder(project, FileHolder.HolderType.IGNORED));
    myHolders.put(FileHolder.HolderType.LOCKED, new VirtualFileHolder(project, FileHolder.HolderType.LOCKED));
  }

  public FileHolderComposite(final FileHolderComposite holder) {
    myHolders = new HashMap<HolderType, FileHolder>();
    for (FileHolder fileHolder : holder.myHolders.values()) {
      myHolders.put(fileHolder.getType(), fileHolder.copy());
    }
  }

  public FileHolder add(@NotNull final FileHolder fileHolder, final boolean copy) {
    final FileHolder added = copy ? fileHolder.copy() : fileHolder;
    myHolders.put(fileHolder.getType(), added);
    return added;
  }

  public void cleanAll() {
    for (FileHolder holder : myHolders.values()) {
      holder.cleanAll();
    }
  }

  public void cleanScope(final VcsDirtyScope scope) {
    for (FileHolder holder : myHolders.values()) {
      holder.cleanScope(scope);
    }
  }

  public FileHolder copy() {
    return new FileHolderComposite(this);
  }

  public FileHolder get(final HolderType type) {
    return myHolders.get(type);
  }

  public VirtualFileHolder getVFHolder(final HolderType type) {
    return (VirtualFileHolder) myHolders.get(type);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FileHolderComposite another = (FileHolderComposite) o;
    if (another.myHolders.size() != myHolders.size()) {
      return false;
    }

    for (Map.Entry<HolderType, FileHolder> entry : myHolders.entrySet()) {
      if (! entry.getValue().equals(another.myHolders.get(entry.getKey()))) {
        return false;
      }
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myHolders != null ? myHolders.hashCode() : 0;
  }

  public HolderType getType() {
    throw new UnsupportedOperationException();
  }

  public DeletedFilesHolder getDeletedFilesHolder() {
    return (DeletedFilesHolder) myHolders.get(HolderType.DELETED);
  }

  public SwitchedFileHolder getSwitchedFileHolder() {
    return (SwitchedFileHolder) myHolders.get(HolderType.SWITCHED);
  }
}
