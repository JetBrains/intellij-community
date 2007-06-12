/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.util.containers.Stack;

import java.util.*;

public class RefreshWorker {
  private boolean myIsRecursive;
  private Stack<VirtualFile> myRefreshQueue = new Stack<VirtualFile>();

  private List<VFileEvent> myEvents = new ArrayList<VFileEvent>();

  public RefreshWorker(final VirtualFile refreshRoot, final boolean isRecursive) {
    myIsRecursive = isRecursive;
    myRefreshQueue.push(refreshRoot);
  }

  public void scan() {
    final NewVirtualFile root = (NewVirtualFile)myRefreshQueue.peek();
    final NewVirtualFileSystem delegate = root.getFileSystem();
    if (root.isDirty() && !delegate.exists(root)) {
      scheduleDeletion(root);
      root.markClean();
    }
    else {
      final PersistentFS persistence = (PersistentFS)ManagingFS.getInstance();

      while (!myRefreshQueue.isEmpty()) {
        final VirtualFileSystemEntry file = (VirtualFileSystemEntry)myRefreshQueue.pop();
        if (!file.isDirty()) continue;

        if (file.isDirectory()) {
          VirtualDirectoryImpl dir = (VirtualDirectoryImpl)file;
          final boolean fullSync = dir.allChildrenLoaded();
          if (fullSync) {
            Set<String> currentNames = new HashSet<String>(Arrays.asList(persistence.list(file)));
            Set<String> uptodateNames = new HashSet<String>(Arrays.asList(delegate.list(file)));

            Set<String> newNames = new HashSet<String>(uptodateNames);
            newNames.removeAll(currentNames);

            Set<String> deletedNames = new HashSet<String>(currentNames);
            deletedNames.removeAll(uptodateNames);

            for (String name : deletedNames) {
              scheduleDeletion(file.findChild(name));
            }

            for (String name : newNames) {
              boolean isDirectory = delegate.isDirectory(new FakeVirtualFile(name, file));
              scheduleCreation(file, name, isDirectory);
            }

            for (VirtualFile child : file.getChildren()) {
              if (!deletedNames.contains(child.getName())) {
                scheduleChildRefresh(file, child, delegate);
              }
            }
          }
          else {
            for (VirtualFile child : file.getCachedChildren()) {
              if (delegate.exists(child)) {
                scheduleChildRefresh(file, child, delegate);
              }
              else {
                scheduleDeletion(child);
              }
            }

            final List<String> names = dir.getSuspicousNames();
            for (String name : names) {
              final VirtualFile fake = new FakeVirtualFile(name, file);
              if (delegate.exists(fake)) {
                scheduleCreation(file, name, delegate.isDirectory(fake));
              }
            }
          }
        }
        else {
          long currentTimestamp = persistence.getTimeStamp(file);
          long updtodateTimestamp = delegate.getTimeStamp(file);

          if (currentTimestamp != updtodateTimestamp) {
            scheduleUpdateContent(file);
          }

          boolean currentWritable = persistence.isWritable(file);
          boolean uptodateWritable = delegate.isWritable(file);

          if (currentWritable != uptodateWritable) {
            scheduleWritableAttributeChange(file, currentWritable, uptodateWritable);
          }
        }

        file.markClean();
      }
    }
  }

  private void scheduleChildRefresh(final VirtualFileSystemEntry file, final VirtualFile child, final NewVirtualFileSystem delegate) {
    final boolean currentIsDirectory = child.isDirectory();
    final boolean uptodateisDirectory = delegate.isDirectory(child);
    if (currentIsDirectory != uptodateisDirectory) {
      scheduleDeletion(child);
      scheduleCreation(file, child.getName(), uptodateisDirectory);
    }
    else if ((myIsRecursive || !currentIsDirectory)) {
      myRefreshQueue.push(child);
    }
  }

  private void scheduleWritableAttributeChange(final VirtualFileSystemEntry file, final boolean currentWritable, final boolean uptodateWritable) {
    myEvents.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_WRITABLE, currentWritable, uptodateWritable, true));
  }

  private void scheduleUpdateContent(final VirtualFileSystemEntry file) {
    myEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, true));
  }

  private void scheduleCreation(final VirtualFileSystemEntry parent, final String childName, final boolean isDirectory) {
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true));
  }

  private void scheduleDeletion(final VirtualFile file) {
    myEvents.add(new VFileDeleteEvent(null, file, true));
  }

  public List<VFileEvent> getEvents() {
    return myEvents;
  }
}
