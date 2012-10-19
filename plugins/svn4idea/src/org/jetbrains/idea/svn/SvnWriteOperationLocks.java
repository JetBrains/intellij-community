/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/19/12
 * Time: 12:09 PM
 */
public class SvnWriteOperationLocks {
  private final ProjectLevelVcsManager myVcsManager;
  private final Map<String, Lock> myLockMap;
  private final Object myLock;

  public SvnWriteOperationLocks(final Project project) {
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myLockMap = new HashMap<String, Lock>();
    myLock = new Object();
  }

  public void lock(final File file) throws SVNException {
    final Lock lock = getLockObject(file);
    lock.lock();
  }

  private Lock getLockObject(File file) throws SVNException {
    return getLockObject(new FilePathImpl(file, file.isDirectory()), FilePathsHelper.convertPath(file.getPath()));
  }

  private Lock getLockObject(VirtualFile file) throws SVNException {
    return getLockObject(new FilePathImpl(file), FilePathsHelper.convertPath(file.getPath()));
  }

  private Lock getLockObject(FilePathImpl file, final String path) throws SVNException {
    final boolean directory = file.isDirectory();
    final VirtualFile root = myVcsManager.getVcsRootFor(file);
    if (root == null) {
      throw new SVNException(SVNErrorMessage.create(directory ? SVNErrorCode.WC_NOT_WORKING_COPY : SVNErrorCode.WC_NOT_FILE));
    }
    Lock lock;
    synchronized (myLock) {
      lock = myLockMap.get(path);
      if (lock == null) {
        lock = new ReentrantLock();
        myLockMap.put(path, lock);
      }
    }
    return lock;
  }

  public void unlock(final File file) throws SVNException {
    final Lock lock = getLockObject(file);
    lock.unlock();
  }

  public void lock(final VirtualFile vf) throws SVNException {
    final Lock lock = getLockObject(vf);
    lock.lock();
  }

  public void unlock(final VirtualFile vf) throws SVNException {
    final Lock lock = getLockObject(vf);
    lock.unlock();
  }
}
