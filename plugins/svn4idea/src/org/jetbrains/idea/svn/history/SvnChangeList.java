/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 * User: yole
 * Date: 28.11.2006
 * Time: 17:20:32
 */
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.ExternallyRenamedChange;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBranchConfiguration;
import org.jetbrains.idea.svn.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class SvnChangeList implements CommittedChangeList {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history");

  private SvnVcs myVcs;
  private SvnRepositoryLocation myLocation;
  private String myRepositoryRoot;
  private long myRevision;
  private String myAuthor;
  private Date myDate;
  private String myMessage;
  private Set<String> myChangedPaths = new HashSet<String>();
  private Set<String> myAddedPaths = new HashSet<String>();
  private Set<String> myDeletedPaths = new HashSet<String>();
  private List<Change> myChanges;
  private final String myCommonPathRoot;

  private SVNURL myBranchUrl;
  private VirtualFile myVcsRoot;

  private boolean myCachedInfoLoaded;
  private SVNRepository myRepository;

  // key: added path, value: copied-from
  private Map<String, String> myCopiedAddedPaths = new HashMap<String, String>();

  public SvnChangeList(SvnVcs vcs, @NotNull final SvnRepositoryLocation location, final SVNLogEntry logEntry, String repositoryRoot) {
    myVcs = vcs;
    myLocation = location;
    myRevision = logEntry.getRevision();
    final String author = logEntry.getAuthor();
    myAuthor = author == null ? "" : author;
    myDate = logEntry.getDate();
    final String message = logEntry.getMessage();
    myMessage = message == null ? "" : message;

    myRepositoryRoot = repositoryRoot.endsWith("/") ? repositoryRoot.substring(0, repositoryRoot.length() - 1) : repositoryRoot;

    final CommonPathSearcher commonPathSearcher = new CommonPathSearcher();

    for(Object o: logEntry.getChangedPaths().values()) {
      final SVNLogEntryPath entry = (SVNLogEntryPath) o;
      final String path = entry.getPath();

      commonPathSearcher.next(path);
      
      if (entry.getType() == 'A') {
        if (entry.getCopyPath() != null) {
          myCopiedAddedPaths.put(path, entry.getCopyPath());
        }
        myAddedPaths.add(path);
      }
      else if (entry.getType() == 'D') {
        myDeletedPaths.add(path);
      }
      else {
        myChangedPaths.add(path);
      }
    }

    myCommonPathRoot = commonPathSearcher.getCommon();
  }

  public SvnChangeList(SvnVcs vcs, @NotNull SvnRepositoryLocation location, DataInput stream, final boolean supportsCopyFromInfo) throws IOException {
    myVcs = vcs;
    myLocation = location;
    readFromStream(stream, supportsCopyFromInfo);
    final CommonPathSearcher commonPathSearcher = new CommonPathSearcher();
    for (String path : myAddedPaths) {
      commonPathSearcher.next(path);
    }
    for (String path : myDeletedPaths) {
      commonPathSearcher.next(path);
    }
    for (String path : myChangedPaths) {
      commonPathSearcher.next(path);
    }
    myCommonPathRoot = commonPathSearcher.getCommon();
  }

  private void uploadDeletedRenamedChildren(final List<Change> out) {
    if (myRepository == null) {
      return;
    }

    final Set<Pair<Boolean, String>> duplicateControl = new HashSet<Pair<Boolean, String>>();
    for (Change change : out) {
      if (change.getBeforeRevision() != null) {
        duplicateControl.add(new Pair<Boolean, String>(Boolean.TRUE, ((SvnRepositoryContentRevision) change.getBeforeRevision()).getPath()));
      }
      if (change.getAfterRevision() != null) {
        duplicateControl.add(new Pair<Boolean, String>(Boolean.FALSE, ((SvnRepositoryContentRevision) change.getAfterRevision()).getPath()));
      }
    }

    for (Change change : myChanges) {
      // directory statuses are already uploaded
      if ((change.getAfterRevision() == null) && (change.getBeforeRevision().getFile().isDirectory())) {
        final SvnRepositoryContentRevision revision = (SvnRepositoryContentRevision) change.getBeforeRevision();
        out.addAll(getChildrenAsChanges(revision.getPath(), true, duplicateControl));
      } else if ((change.getBeforeRevision() == null) && (change.getAfterRevision().getFile().isDirectory())) {
        // look for renamed folders contents
        final SvnRepositoryContentRevision revision = (SvnRepositoryContentRevision) change.getAfterRevision();
        if (myCopiedAddedPaths.containsKey(revision.getPath())) {
          out.addAll(getChildrenAsChanges(revision.getPath(), false, duplicateControl));
        }
      }
    }
  }

  @NotNull
  private Collection<Change> getChildrenAsChanges(final String path, final boolean isBefore, final Set<Pair<Boolean, String>> duplicateControl) {

    try {
      final List<Change> result = new ArrayList<Change>();

      final SVNLogClient client = myVcs.createLogClient();

      final long revision = getRevision(isBefore);
      client.doList(myRepository.getLocation().appendPath(path, true), SVNRevision.create(revision), SVNRevision.create(revision),
                    true, new ISVNDirEntryHandler() {
        public void handleDirEntry(final SVNDirEntry dirEntry) throws SVNException {
          final String childPath = path + '/' + dirEntry.getRelativePath();

          if (! duplicateControl.contains(new Pair<Boolean, String>(isBefore, childPath))) {
            final ContentRevision contentRevision = createRevision(childPath, isBefore, SVNNodeKind.DIR.equals(dirEntry.getKind()));
            result.add(new Change(isBefore ? contentRevision : null, isBefore ? null : contentRevision));
          }
        }
      });

      return result;
    }
    catch (SVNException e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }

  public String getCommitterName() {
    return myAuthor;
  }

  public Date getCommitDate() {
    return myDate;
  }


  public Collection<Change> getChanges() {
    if (myChanges == null) {
      createLists();
    }
    return myChanges;
  }

  private void createLists() {
    try {
      myRepository = myVcs.createRepository(myRepositoryRoot);
    }
    catch (SVNException e) {
      LOG.error(e);
    }

    myChanges = new ArrayList<Change>();
    // key: copied-from
    final Map<String, ExternallyRenamedChange> copiedAddedChanges = new HashMap<String, ExternallyRenamedChange>();

    for(String path: myAddedPaths) {
      final ExternallyRenamedChange addedChange = new ExternallyRenamedChange(null, createRevisionLazily(path, false));
      if (myCopiedAddedPaths.containsKey(path)) {
        copiedAddedChanges.put(myCopiedAddedPaths.get(path), addedChange);
      }
      myChanges.add(addedChange);
    }
    for(String path: myDeletedPaths) {
      final ExternallyRenamedChange deletedChange = new ExternallyRenamedChange(createRevisionLazily(path, true), null);
      if (copiedAddedChanges.containsKey(path)) {
        final ExternallyRenamedChange addedChange = copiedAddedChanges.get(path);
        //noinspection ConstantConditions
        // display only 'moved to'
        //addedChange.setRenamedOrMovedTarget(deletedChange.getBeforeRevision().getFile());
        //noinspection ConstantConditions
        deletedChange.setRenamedOrMovedTarget(addedChange.getAfterRevision().getFile());
      }
      myChanges.add(deletedChange);
    }
    for(String path: myChangedPaths) {
      boolean moveAndChange = false;
      for (String addedPath : myAddedPaths) {
        final String copyFromPath = myCopiedAddedPaths.get(addedPath);
        if ((copyFromPath != null) && (SVNPathUtil.isAncestor(addedPath, path))) {
          moveAndChange = true;
          final Change renamedChange =
              new Change(createRevisionLazily(copyFromPath, true), createRevisionLazily(path, false));
          renamedChange.getMoveRelativePath(myVcs.getProject());
          myChanges.add(renamedChange);
          break;
        }
      }
      if (! moveAndChange) {
        myChanges.add(new ExternallyRenamedChange(createRevisionLazily(path, true), createRevisionLazily(path, false)));
      }
    }
  }

  @Nullable
  private FilePath getLocalPath(final String path, final NotNullFunction<File, Boolean> detector) {
    final String fullPath = myRepositoryRoot + path;
    return myLocation.getLocalPath(fullPath, detector);
  }

  private long getRevision(final boolean isBeforeRevision) {
    return isBeforeRevision ? (myRevision - 1) : myRevision;
  }

  private SvnRepositoryContentRevision createRevisionLazily(final String path, final boolean isBeforeRevision) {
    return SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path,
                                               getLocalPath(path, new NotNullFunction<File, Boolean>() {
                                                 @NotNull
                                                 public Boolean fun(final File file) {
                                                   try {
                                                     return ((myRepository != null) && SVNNodeKind.DIR.equals(myRepository.checkPath(path, getRevision(isBeforeRevision))));
                                                   }
                                                   catch (SVNException e) {
                                                     LOG.error(e);
                                                     return Boolean.FALSE;
                                                   }
                                                 }
                                               }), getRevision(isBeforeRevision));
  }

  private SvnRepositoryContentRevision createRevision(final String path, final boolean isBeforeRevision, final boolean isDir) {
    return SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path,
                                               getLocalPath(path, new NotNullFunction<File, Boolean>() {
                                                 @NotNull
                                                 public Boolean fun(final File file) {
                                                   return isDir;
                                                 }
                                               }), getRevision(isBeforeRevision));
  }

  @NotNull
  public String getName() {
    return myMessage;
  }

  public String getComment() {
    return myMessage;
  }

  public long getNumber() {
    return myRevision;
  }

  public AbstractVcs getVcs() {
    return myVcs;
  }

  public Collection<Change> getChangesWithMovedTrees() {
    final List<Change> result = new ArrayList<Change>(myChanges);
    uploadDeletedRenamedChildren(result);
    return result;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SvnChangeList that = (SvnChangeList)o;

    if (myRevision != that.myRevision) return false;
    if (myAuthor != null ? !myAuthor.equals(that.myAuthor) : that.myAuthor != null) return false;
    if (myDate != null ? !myDate.equals(that.myDate) : that.myDate != null) return false;
    if (myMessage != null ? !myMessage.equals(that.myMessage) : that.myMessage != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (int)(myRevision ^ (myRevision >>> 32));
    result = 31 * result + (myAuthor != null ? myAuthor.hashCode() : 0);
    result = 31 * result + (myDate != null ? myDate.hashCode() : 0);
    result = 31 * result + (myMessage != null ? myMessage.hashCode() : 0);
    return result;
  }

  public String toString() {
    return myMessage;
  }

  public void writeToStream(final DataOutput stream) throws IOException {
    stream.writeUTF(myRepositoryRoot);
    stream.writeLong(myRevision);
    stream.writeUTF(myAuthor);
    stream.writeLong(myDate.getTime());
    IOUtil.writeUTFTruncated(stream, myMessage);
    writeFiles(stream, myChangedPaths);
    writeFiles(stream, myAddedPaths);
    writeFiles(stream, myDeletedPaths);
    writeMap(stream, myCopiedAddedPaths);
  }

  private static void writeFiles(final DataOutput stream, final Set<String> paths) throws IOException {
    stream.writeInt(paths.size());
    for(String s: paths) {
      stream.writeUTF(s);
    }
  }

  private void readFromStream(final DataInput stream, final boolean supportsCopyFromInfo) throws IOException {
    myRepositoryRoot = stream.readUTF();
    myRevision = stream.readLong();
    myAuthor = stream.readUTF();
    myDate = new Date(stream.readLong());
    myMessage = stream.readUTF();
    readFiles(stream, myChangedPaths);
    readFiles(stream, myAddedPaths);
    readFiles(stream, myDeletedPaths);

    if (supportsCopyFromInfo) {
      readMap(stream, myCopiedAddedPaths);
    }
  }

  private static void writeMap(final DataOutput stream, final Map<String, String> map) throws IOException {
    stream.writeInt(map.size());
    for (Map.Entry<String, String> entry : map.entrySet()) {
      stream.writeUTF(entry.getKey());
      stream.writeUTF(entry.getValue());
    }
  }

  private static void readMap(final DataInput stream, final Map<String, String> map) throws IOException {
    int count = stream.readInt();
    for (int i = 0; i < count; i++) {
      map.put(stream.readUTF(), stream.readUTF());
    }
  }

  private static void readFiles(final DataInput stream, final Set<String> paths) throws IOException {
    int count = stream.readInt();
    for(int i=0; i<count; i++) {
      paths.add(stream.readUTF());
    }
  }

  public SVNURL getBranchUrl() {
    if (!myCachedInfoLoaded) {
      updateCachedInfo();
    }
    return myBranchUrl;
  }

  public VirtualFile getVcsRoot() {
    if (!myCachedInfoLoaded) {
      updateCachedInfo();
    }
    return myVcsRoot;
  }

  private static class CommonPathSearcher {
    private String myCommon;

    public void next(final String value) {
      if (value == null) {
        return;
      }
      if (myCommon == null) {
        myCommon = value;
        return;
      }

      if (value.startsWith(myCommon)) {
        return;
      }

      myCommon = SVNPathUtil.getCommonPathAncestor(myCommon, value);
    }

    public String getCommon() {
      return myCommon;
    }
  }

  private void updateCachedInfo() {
    myCachedInfoLoaded = true;

    final String absolutePath = myRepositoryRoot + (myCommonPathRoot.startsWith("/") ? myCommonPathRoot : ("/" + myCommonPathRoot));

    myVcsRoot = myVcs.getSvnFileUrlMapping().getVcRootByUrl(absolutePath);
    if (myVcsRoot == null) {
      return;
    }
    
    myBranchUrl = getBranchForUrl(myVcsRoot, absolutePath);
  }

  public void forceReloadCachedInfo(final boolean reloadRoot) {
    myCachedInfoLoaded = false;
    myBranchUrl = null;

    if (reloadRoot) {
      myVcsRoot = null;
    }
  }

  @Nullable
  private SVNURL getBranchForUrl(final VirtualFile vcsRoot, final String urlPath) {
    final SvnBranchConfiguration configuration;
    try {
      final SVNURL url = SVNURL.parseURIEncoded(urlPath);
      configuration = SvnBranchConfigurationManager.getInstance(myVcs.getProject()).get(vcsRoot);
      return configuration.getWorkingBranch(myVcs.getProject(), url);
    }
    catch (SVNException e) {
      return null;
    } catch (VcsException e1) {
      return null;
    }
  }
}