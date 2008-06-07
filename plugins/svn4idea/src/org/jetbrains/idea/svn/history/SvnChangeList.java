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
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.ExternallyRenamedChange;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
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
  private ChangesListCreationHelper myListsHolder;
  private final String myCommonPathRoot;

  private SVNURL myBranchUrl;
  private VirtualFile myVcsRoot;

  private boolean myCachedInfoLoaded;

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

  public String getCommitterName() {
    return myAuthor;
  }

  public Date getCommitDate() {
    return myDate;
  }


  public Collection<Change> getChanges() {
    if (myListsHolder == null) {
      createLists();
    }
    return myListsHolder.getList();
  }

  private void createLists() {
    myListsHolder = new ChangesListCreationHelper();
    
    // key: copied-from
    final Map<String, ExternallyRenamedChange> copiedAddedChanges = new HashMap<String, ExternallyRenamedChange>();

    for(String path: myAddedPaths) {
      final ExternallyRenamedChange addedChange = new ExternallyRenamedChange(null, myListsHolder.createRevisionLazily(path, false));
      if (myCopiedAddedPaths.containsKey(path)) {
        copiedAddedChanges.put(myCopiedAddedPaths.get(path), addedChange);
      }
      myListsHolder.add(addedChange);
    }
    for(String path: myDeletedPaths) {
      final ExternallyRenamedChange deletedChange = new ExternallyRenamedChange(myListsHolder.createRevisionLazily(path, true), null);
      if (copiedAddedChanges.containsKey(path)) {
        final ExternallyRenamedChange addedChange = copiedAddedChanges.get(path);
        //noinspection ConstantConditions
        // display only 'moved to'
        //addedChange.setRenamedOrMovedTarget(deletedChange.getBeforeRevision().getFile());
        //noinspection ConstantConditions
        deletedChange.setRenamedOrMovedTarget(addedChange.getAfterRevision().getFile());
      }
      myListsHolder.add(deletedChange);
    }
    for(String path: myChangedPaths) {
      boolean moveAndChange = false;
      for (String addedPath : myAddedPaths) {
        final String copyFromPath = myCopiedAddedPaths.get(addedPath);
        if ((copyFromPath != null) && (SVNPathUtil.isAncestor(addedPath, path))) {
          moveAndChange = true;
          final Change renamedChange =
              new Change(myListsHolder.createRevisionLazily(copyFromPath, true), myListsHolder.createRevisionLazily(path, false));
          renamedChange.getMoveRelativePath(myVcs.getProject());
          myListsHolder.add(renamedChange);
          break;
        }
      }
      if (! moveAndChange) {
        myListsHolder.add(new ExternallyRenamedChange(myListsHolder.createRevisionLazily(path, true), myListsHolder.createRevisionLazily(path, false)));
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

  public SvnRepositoryLocation getLocation() {
    return myLocation;
  }

  /**
   * needed to track in which changes non-local files live
   */
  private class ChangesListCreationHelper {
    private final List<Change> myList;
    private List<Change> myDetailedList;
    private final List<Pair<Integer, Boolean>> myWithoutDirStatus;
    private SVNRepository myRepository;

    private ChangesListCreationHelper() {
      myList = new ArrayList<Change>();
      myWithoutDirStatus = new ArrayList<Pair<Integer, Boolean>>();
    }

    public void add(final Change change) {
      myList.add(change);
    }

    public SvnRepositoryContentRevision createRevisionLazily(final String path, final boolean isBeforeRevision) {
      return SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path,
                                                 getLocalPath(path, new NotNullFunction<File, Boolean>() {
                                                   @NotNull
                                                   public Boolean fun(final File file) {
                                                     // list will be next
                                                     myWithoutDirStatus.add(new Pair<Integer, Boolean>(myList.size(), isBeforeRevision));
                                                     return Boolean.FALSE;
                                                   }
                                                 }), getRevision(isBeforeRevision));
    }

    public List<Change> getList() {
      return myList;
    }

    public List<Change> getDetailedList() {
      if (myDetailedList == null) {
        myDetailedList = new ArrayList<Change>(myList);

        try {
          myRepository = myVcs.createRepository(myRepositoryRoot);

          doRemoteDetails();
          uploadDeletedRenamedChildren();
        }
        catch (SVNException e) {
          LOG.error(e);
        } finally {
          if (myRepository != null) {
            myRepository.closeSession();
            myRepository = null;
          }
        }
      }
      return myDetailedList;
    }

    private void doRemoteDetails() throws SVNException {
      for (Pair<Integer, Boolean> idxData : myWithoutDirStatus) {
        final Change sourceChange = myDetailedList.get(idxData.first.intValue());
        final SvnRepositoryContentRevision revision = (SvnRepositoryContentRevision)
            (idxData.second.booleanValue() ? sourceChange.getBeforeRevision() : sourceChange.getAfterRevision());
        if (revision == null) {
          continue;
        }
        final boolean status = SVNNodeKind.DIR.equals(myRepository.checkPath(revision.getPath(), getRevision(idxData.second.booleanValue())));
        final Change replacingChange = new Change(createRevision((SvnRepositoryContentRevision) sourceChange.getBeforeRevision(), status),
                                                  createRevision((SvnRepositoryContentRevision) sourceChange.getAfterRevision(), status));
        myDetailedList.set(idxData.first.intValue(), replacingChange);
      }

      myWithoutDirStatus.clear();
    }

    @Nullable
    private SvnRepositoryContentRevision createRevision(final SvnRepositoryContentRevision previousRevision, final boolean isDir) {
      return previousRevision == null ? null :
             SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, previousRevision.getPath(),
             new FilePathImpl(previousRevision.getFile().getIOFile(), isDir),
             ((SvnRevisionNumber) previousRevision.getRevisionNumber()).getRevision().getNumber());
    }

    private void uploadDeletedRenamedChildren() throws SVNException {
      // cannot insert when iterate
      final List<Change> detailsOnly = new ArrayList<Change>();

      final Set<Pair<Boolean, String>> duplicateControl = new HashSet<Pair<Boolean, String>>();
      for (Change change : myDetailedList) {
        if (change.getBeforeRevision() != null) {
          duplicateControl.add(new Pair<Boolean, String>(Boolean.TRUE, ((SvnRepositoryContentRevision) change.getBeforeRevision()).getPath()));
        }
        if (change.getAfterRevision() != null) {
          duplicateControl.add(new Pair<Boolean, String>(Boolean.FALSE, ((SvnRepositoryContentRevision) change.getAfterRevision()).getPath()));
        }
      }

      for (Change change : myDetailedList) {
        // directory statuses are already uploaded
        if ((change.getAfterRevision() == null) && (change.getBeforeRevision().getFile().isDirectory())) {
          final SvnRepositoryContentRevision revision = (SvnRepositoryContentRevision) change.getBeforeRevision();
          detailsOnly.addAll(getChildrenAsChanges(revision.getPath(), true, duplicateControl));
        } else if ((change.getBeforeRevision() == null) && (change.getAfterRevision().getFile().isDirectory())) {
          // look for renamed folders contents
          final SvnRepositoryContentRevision revision = (SvnRepositoryContentRevision) change.getAfterRevision();
          if (myCopiedAddedPaths.containsKey(revision.getPath())) {
            detailsOnly.addAll(getChildrenAsChanges(revision.getPath(), false, duplicateControl));
          }
        }
      }

      myDetailedList.addAll(detailsOnly);
    }

    @NotNull
    private Collection<Change> getChildrenAsChanges(final String path, final boolean isBefore, final Set<Pair<Boolean, String>> duplicateControl)
        throws SVNException {
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

    private SvnRepositoryContentRevision createRevision(final String path, final boolean isBeforeRevision, final boolean isDir) {
      return SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path,
                                                 getLocalPath(path, new NotNullFunction<File, Boolean>() {
                                                   @NotNull
                                                   public Boolean fun(final File file) {
                                                     return isDir;
                                                   }
                                                 }), getRevision(isBeforeRevision));
    }
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
    if (myListsHolder == null) {
      createLists();
    }

    return myListsHolder.getDetailedList();
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
    
    myBranchUrl = SvnUtil.getBranchForUrl(myVcs, myVcsRoot, absolutePath);
  }

  public void forceReloadCachedInfo(final boolean reloadRoot) {
    myCachedInfoLoaded = false;
    myBranchUrl = null;

    if (reloadRoot) {
      myVcsRoot = null;
    }
  }
}