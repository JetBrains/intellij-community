/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Alexander.Kitaev
 * Date: 20.07.2006
 * Time: 19:09:29
 */
package org.jetbrains.idea.svn.diff;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

import java.io.File;
import java.io.OutputStream;
import java.util.Map;

public class SvnDiffEditor implements ISVNEditor {
  private File mySourceRoot;
  private SVNRepository mySource;
  private final SVNRepository myTarget;
  private final long myTargetRevision;
  private final boolean myReverse;

  private final Map<String, Change> myChanges;

  public SvnDiffEditor(@NotNull SVNRepository source, SVNRepository target, long targetRevision, boolean reverse) {
    mySource = source;
    myTarget = target;
    myTargetRevision = targetRevision;
    myChanges = new HashMap<>();
    myReverse = reverse;
  }

  public SvnDiffEditor(@NotNull final File sourceRoot, final SVNRepository target, long targetRevision,
                       boolean reverse) {
    mySourceRoot = sourceRoot;
    myTarget = target;
    myTargetRevision = targetRevision;
    myChanges = new HashMap<>();
    myReverse = reverse;
  }

  public Map<String, Change> getChangesMap() {
    return myChanges;
  }

  private ContentRevision createBeforeRevision(final String path) {
    if (mySource != null) {
      return new DiffContentRevision(path, mySource, -1);
    }
    // 'path' includes the first component of the root local path
    File f = new File(mySourceRoot, path);
    FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(f);
    return CurrentContentRevision.create(filePath);
  }

  private DiffContentRevision createAfterRevision(final String path) {
    if (mySourceRoot != null) {
      File f = new File(mySourceRoot, path);
      FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(f);
      return new DiffContentRevision(path, myTarget, myTargetRevision, filePath);
    }
    return new DiffContentRevision(path, myTarget, myTargetRevision);
  }

  public void targetRevision(long revision) throws SVNException {
  }
  public void openRoot(long revision) throws SVNException {
  }

  public void deleteEntry(String path, long revision) throws SVNException {
    // deleted - null for target, existing for source.
    Change change = createChange(path, FileStatus.DELETED);
    myChanges.put(path, change);
  }

  public void absentDir(String path) throws SVNException {
  }
  public void absentFile(String path) throws SVNException {
  }
  public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    FileStatus status = FileStatus.ADDED;
    if (myChanges.containsKey(path) && myChanges.get(path).getFileStatus() == FileStatus.DELETED) {
      // replaced file
      myChanges.remove(path);
      status = FileStatus.MODIFIED;
    }
    Change change = createChange(path, status);
    myChanges.put(path, change);
  }

  private Change createChange(final String path, final FileStatus status) {
    final ContentRevision beforeRevision = createBeforeRevision(path);
    final DiffContentRevision afterRevision = createAfterRevision(path);
    if (myReverse) {
      if (status == FileStatus.ADDED) {
        return new Change(afterRevision, null);
      }
      if (status == FileStatus.DELETED) {
        return new Change(null, beforeRevision);
      }
      return new Change(afterRevision, beforeRevision, status);
    }
    return new Change(status == FileStatus.ADDED ? null : beforeRevision,
                      status == FileStatus.DELETED ? null : afterRevision,
                      status);
  }

  public void openDir(String path, long revision) throws SVNException {
  }
  public void changeDirProperty(final String name, final SVNPropertyValue value) throws SVNException {
  }
  public void closeDir() throws SVNException {
  }

  public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    FileStatus status = FileStatus.ADDED;
    if (myChanges.containsKey(path) && myChanges.get(path).getFileStatus() == FileStatus.DELETED) {
      // replaced file
      myChanges.remove(path);
      status = FileStatus.MODIFIED;
    }
    Change change = createChange(path, status);
    myChanges.put(path, change);
  }

  public void openFile(String path, long revision) throws SVNException {
    Change change = createChange(path, FileStatus.MODIFIED);
    myChanges.put(path, change);
  }

  public void changeFileProperty(final String path, final String propertyName, final SVNPropertyValue propertyValue) throws SVNException {
  }
  public void closeFile(String path, String textChecksum) throws SVNException {
  }

  public void abortEdit() throws SVNException {
  }

  public void applyTextDelta(String path, String baseChecksum) throws SVNException {
  }

  public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
    return null;
  }

  public void textDeltaEnd(String path) throws SVNException {
  }

  public SVNCommitInfo closeEdit() throws SVNException {
    return null;
  }

}
