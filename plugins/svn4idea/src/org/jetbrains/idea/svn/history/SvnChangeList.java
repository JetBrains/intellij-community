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
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConstantFunction;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
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

  private final SvnVcs myVcs;
  private final SvnRepositoryLocation myLocation;
  private String myRepositoryRoot;
  private long myRevision;
  private String myAuthor;
  private Date myDate;
  private String myMessage;
  private final Set<String> myChangedPaths = new HashSet<String>();
  private final Set<String> myAddedPaths = new HashSet<String>();
  private final Set<String> myDeletedPaths = new HashSet<String>();
  private final Set<String> myReplacedPaths = new HashSet<String>();

  private ChangesListCreationHelper myListsHolder;

  private SVNURL myBranchUrl;

  private boolean myCachedInfoLoaded;

  // key: added path, value: copied-from
  private final TreeMap<String, String> myCopiedAddedPaths = new TreeMap<String, String>();
  private RootUrlInfo myWcRoot;
  private final CommonPathSearcher myCommonPathSearcher;
  private final Set<String> myKnownAsDirectories;

  public SvnChangeList(@NotNull final List<CommittedChangeList> lists, @NotNull final SvnRepositoryLocation location) {

    final SvnChangeList sample = (SvnChangeList) lists.get(0);
    myVcs = sample.myVcs;
    myLocation = location;
    myRevision = sample.myRevision;
    myAuthor = sample.myAuthor;
    myDate = sample.myDate;
    myMessage = sample.myMessage;
    myRepositoryRoot = sample.myRepositoryRoot;
    myCommonPathSearcher = new CommonPathSearcher();

    for (CommittedChangeList list : lists) {
      final SvnChangeList svnList = (SvnChangeList) list;
      myChangedPaths.addAll(svnList.myChangedPaths);
      myAddedPaths.addAll(svnList.myAddedPaths);
      myDeletedPaths.addAll(svnList.myDeletedPaths);
      myReplacedPaths.addAll(svnList.myReplacedPaths);
    }
    myKnownAsDirectories = new HashSet<String>(0);
  }

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

    myCommonPathSearcher = new CommonPathSearcher();

    myKnownAsDirectories = new HashSet<String>(0);
    for(Object o: logEntry.getChangedPaths().values()) {
      final SVNLogEntryPath entry = (SVNLogEntryPath) o;
      final String path = entry.getPath();

      if (SVNNodeKind.DIR.equals(entry.getKind())) {
        myKnownAsDirectories.add(path);
      }

      myCommonPathSearcher.next(path);
      
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
        if (entry.getType() == 'R') {
          myReplacedPaths.add(path);
        }
        myChangedPaths.add(path);
      }
    }
  }

  public SvnChangeList(SvnVcs vcs, @NotNull SvnRepositoryLocation location, DataInput stream, final boolean supportsCopyFromInfo,
                       final boolean supportsReplaced) throws IOException {
    myVcs = vcs;
    myLocation = location;
    myKnownAsDirectories = new HashSet<String>();
    readFromStream(stream, supportsCopyFromInfo, supportsReplaced);
    myCommonPathSearcher = new CommonPathSearcher();
    for (String path : myAddedPaths) {
      myCommonPathSearcher.next(path);
    }
    for (String path : myDeletedPaths) {
      myCommonPathSearcher.next(path);
    }
    for (String path : myChangedPaths) {
      myCommonPathSearcher.next(path);
    }
  }

  public Change getByPath(final String path) {
    if (myListsHolder == null) {
      createLists();
    }
    return myListsHolder.getByPath(path);
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

    correctBeforePaths();
    final List<String> copyDeleted = new ArrayList<String>(myDeletedPaths);

    for(String path: myAddedPaths) {
      final Change addedChange;
      if (myCopiedAddedPaths.containsKey(path)) {
        final String copyTarget = myCopiedAddedPaths.get(path);
        if (copyDeleted.contains(copyTarget)) {
          addedChange = new ExternallyRenamedChange(myListsHolder.createRevisionLazily(copyTarget, true),
                                                    myListsHolder.createRevisionLazily(path, false), copyTarget);
          addedChange.getMoveRelativePath(myVcs.getProject());
          ((ExternallyRenamedChange) addedChange).setCopied(false);
          copyDeleted.remove(copyTarget);
        } else {
          addedChange = new ExternallyRenamedChange(null, myListsHolder.createRevisionLazily(path, false), copyTarget);
        }
        copiedAddedChanges.put(copyTarget, (ExternallyRenamedChange) addedChange);
      } else {
        addedChange = new Change(null, myListsHolder.createRevisionLazily(path, false));
      }
      myListsHolder.add(path, addedChange);
    }
    for(String path: copyDeleted) {
      final Change deletedChange;
      if (copiedAddedChanges.containsKey(path)) {
        // seems never occurs any more
        final ExternallyRenamedChange addedChange = copiedAddedChanges.get(path);
        final FilePath source = addedChange.getAfterRevision().getFile();
        deletedChange = new ExternallyRenamedChange(myListsHolder.createDeletedItemRevision(path, true), null, path);
        ((ExternallyRenamedChange) deletedChange).setCopied(false);
        //noinspection ConstantConditions
        //addedChange.setRenamedOrMovedTarget(deletedChange.getBeforeRevision().getFile());
        //noinspection ConstantConditions
        ((ExternallyRenamedChange) deletedChange).setRenamedOrMovedTarget(source);
      } else {
        deletedChange = new Change(myListsHolder.createDeletedItemRevision(path, true), null);
      }
      myListsHolder.add(path, deletedChange);
    }
    for(String path: myChangedPaths) {
      boolean moveAndChange = false;
      final boolean replaced = myReplacedPaths.contains(path);

      // this piece: for copied-from (or moved) and further modified
      for (String addedPath : myAddedPaths) {
        String copyFromPath = myCopiedAddedPaths.get(addedPath);
        if ((copyFromPath != null) && (SVNPathUtil.isAncestor(addedPath, path))) {
          if (addedPath.length() < path.length()) {
            final String relative = SVNPathUtil.getRelativePath(addedPath, path);
            copyFromPath = SVNPathUtil.append(copyFromPath, relative);
          }
          final ExternallyRenamedChange renamedChange = new ExternallyRenamedChange(myListsHolder.createRevisionLazily(copyFromPath, true),
                                                     myListsHolder.createRevisionLazily(path, false), copyFromPath);
          moveAndChange = true;
          renamedChange.getMoveRelativePath(myVcs.getProject());
          renamedChange.setIsReplaced(replaced);

          final ExternallyRenamedChange addedChange = copiedAddedChanges.get(myCopiedAddedPaths.get(addedPath));
          if ((addedChange != null) && (addedChange.isCopied())) {
            renamedChange.setCopied(true);
          } else {
            renamedChange.setCopied(false);
          }

          myListsHolder.add(path, renamedChange);
          break;
        }
      }
      if (! moveAndChange) {
        final ExternallyRenamedChange renamedChange =
          new ExternallyRenamedChange(myListsHolder.createRevisionLazily(path, true), myListsHolder.createRevisionLazily(path, false),
                                      null);
        renamedChange.setIsReplaced(replaced);
        renamedChange.setCopied(false);
        myListsHolder.add(path, renamedChange);
      }
    }
  }

  private void correctBeforePaths() {
    processDeletedForBeforePaths(myDeletedPaths);
    processModifiedForBeforePaths(myChangedPaths);
    processModifiedForBeforePaths(myReplacedPaths);
  }

  private void processModifiedForBeforePaths(Set<String> paths) {
    final RenameHelper helper = new RenameHelper();
    for (String s : paths) {
      final String converted = helper.convertBeforePath(s, myCopiedAddedPaths);
      if (! s.equals(converted)) {
        myCopiedAddedPaths.put(s, converted);
      }
    }
  }

  private void processDeletedForBeforePaths(Set<String> paths) {
    final RenameHelper helper = new RenameHelper();
    final HashSet<String> copy = new HashSet<String>(paths);
    paths.clear();
    for (String s : copy) {
      paths.add(helper.convertBeforePath(s, myCopiedAddedPaths));
    }
  }

  @Nullable
  private FilePath getLocalPath(final String path, final NotNullFunction<File, Boolean> detector) {
    final String fullPath = myRepositoryRoot + path;
    return myLocation.getLocalPath(fullPath, detector, myVcs);
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
    private final Map<String, Change> myPathToChangeMapping;
    private List<Change> myDetailedList;
    private final List<Pair<Integer, Boolean>> myWithoutDirStatus;
    private SVNRepository myRepository;

    private ChangesListCreationHelper() {
      myList = new ArrayList<Change>();
      myWithoutDirStatus = new ArrayList<Pair<Integer, Boolean>>();
      myPathToChangeMapping = new HashMap<String, Change>();
    }

    public void add(final String path, final Change change) {
      patchChange(change, path);
      myList.add(change);
      myPathToChangeMapping.put(path, change);
    }

    public Change getByPath(final String path) {
      return myPathToChangeMapping.get(path);
    }

    private FilePath localDeletedPath(final String fullPath, final boolean isDir) {
      final SvnFileUrlMapping urlMapping = myVcs.getSvnFileUrlMapping();
      final String path = urlMapping.getLocalPath(fullPath);
      if (path != null) {
        final File file = new File(path);
        return FilePathImpl.createForDeletedFile(file, isDir || file.isDirectory());
      }

      return null;
    }

    public SvnRepositoryContentRevision createDeletedItemRevision(final String path, final boolean isBeforeRevision) {
      final boolean knownAsDirectory = myKnownAsDirectories.contains(path);
      final String fullPath = myRepositoryRoot + path;
      if (! knownAsDirectory) {
        myWithoutDirStatus.add(new Pair<Integer, Boolean>(myList.size(), isBeforeRevision));
      }
      return SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path, localDeletedPath(fullPath, knownAsDirectory),
                                                 getRevision(isBeforeRevision));
    }

    public SvnRepositoryContentRevision createRevisionLazily(final String path, final boolean isBeforeRevision) {
      final boolean knownAsDirectory = myKnownAsDirectories.contains(path);
      final FilePath localPath = getLocalPath(path, new NotNullFunction<File, Boolean>() {
        @NotNull
        public Boolean fun(final File file) {
          if (knownAsDirectory) return Boolean.TRUE;
          // list will be next
          myWithoutDirStatus.add(new Pair<Integer, Boolean>(myList.size(), isBeforeRevision));
          return Boolean.FALSE;
        }
      });
      final SvnRepositoryContentRevision contentRevision =
        SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path, localPath, getRevision(isBeforeRevision));
      if (knownAsDirectory) {
        ((FilePathImpl) contentRevision.getFile()).setIsDirectory(true);
      }
      return contentRevision;
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
          // remove duplicates
          final HashSet<Change> set = new HashSet<Change>(myDetailedList);
          myDetailedList.clear();
          myDetailedList.addAll(set);
        }
        catch (SVNException e) {
          LOG.info(e);
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
        replacingChange.setIsReplaced(sourceChange.isIsReplaced());
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

      final List<Change> preprocessed = ChangesPreprocess.preprocessChangesRemoveDeletedForDuplicateMoved(myDetailedList);

      for (Change change : preprocessed) {
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
        } else if ((change.isIsReplaced() || change.isMoved() || change.isRenamed()) && change.getAfterRevision().getFile().isDirectory()) {
          final SvnRepositoryContentRevision beforeRevision = (SvnRepositoryContentRevision) change.getBeforeRevision();
          detailsOnly.addAll(getChildrenAsChanges(beforeRevision.getPath(), true, duplicateControl));

          final SvnRepositoryContentRevision revision = (SvnRepositoryContentRevision) change.getAfterRevision();
          detailsOnly.addAll(getChildrenAsChanges(revision.getPath(), false, duplicateControl));
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
                                                 getLocalPath(path, new ConstantFunction<File, Boolean>(isDir)), getRevision(isBeforeRevision));
    }
  }

  private static class RenameHelper {
    /*private final TreeMap<String, String> myRenamesMap;

    private RenameHelper(final List<Change> svnRepositoryChanges) {
      myRenamesMap = new TreeMap<String, String>();
      for (Change change : svnRepositoryChanges) {
        if (change.isMoved() || change.isRenamed()) {
          final SvnRepositoryContentRevision before = (SvnRepositoryContentRevision) change.getBeforeRevision();
          final SvnRepositoryContentRevision after = (SvnRepositoryContentRevision) change.getAfterRevision();
          myRenamesMap.put(after.getPath(), before.getPath());
        }
      }
    } */

    public String convertBeforePath(final String path, final TreeMap<String, String> after2before) {
      String current = path;
      // backwards
      for (String key : after2before.descendingKeySet()) {
        if (SVNPathUtil.isAncestor(key, current)) {
          final String relativePath = SVNPathUtil.getRelativePath(key, current);
          current = SVNPathUtil.append(after2before.get(key), relativePath);
        }
      }
      return current;
    }
  }

  private void patchChange(Change change, final String path) {
    final SVNURL becameUrl;
    SVNURL wasUrl;
    try {
      becameUrl = SVNURL.parseURIEncoded(SVNPathUtil.append(myRepositoryRoot, path));
      wasUrl = becameUrl;

      if (change instanceof ExternallyRenamedChange && change.getBeforeRevision() != null) {
        final ExternallyRenamedChange renamedChange = (ExternallyRenamedChange)change;
        final String originUrl = renamedChange.getOriginUrl();
        if (originUrl != null) {
          // use another url for origin
          wasUrl = SVNURL.parseURIEncoded(SVNPathUtil.append(myRepositoryRoot, originUrl));
        }
      }
    }
    catch (SVNException e) {
      // nothing to do
      LOG.info(e);
      return;
    }

    final FilePath filePath = ChangesUtil.getFilePath(change);
    final Change additional = new Change(change.getBeforeRevision() == null ? null :
                                         new SvnLazyPropertyContentRevision(filePath, change.getBeforeRevision().getRevisionNumber(),
                                                                            myVcs.getProject(), wasUrl),
                                         change.getAfterRevision() == null ? null :
                                         new SvnLazyPropertyContentRevision(filePath, change.getAfterRevision().getRevisionNumber(),
                                                                            myVcs.getProject(), becameUrl));
    change.addAdditionalLayerElement(SvnChangeProvider.PROPERTY_LAYER, additional);
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

  @Override
  public String getBranch() {
    return null;
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

  @Override
  public boolean isModifiable() {
    return true;
  }

  @Override
  public void setDescription(String newMessage) {
    myMessage = newMessage;
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
    writeUTFTruncated(stream, myMessage);
    writeFiles(stream, myChangedPaths);
    writeFiles(stream, myAddedPaths);
    writeFiles(stream, myDeletedPaths);
    writeMap(stream, myCopiedAddedPaths);
    writeFiles(stream, myReplacedPaths);

    stream.writeInt(myKnownAsDirectories.size());
    for (String directory : myKnownAsDirectories) {
      stream.writeUTF(directory);
    }
  }

  // to be able to update plugin only
  public static void writeUTFTruncated(final DataOutput stream, final String text) throws IOException {
    // we should not compare number of symbols to 65635 -> it is number of bytes what should be compared
    // ? 4 bytes per symbol - rough estimation
    if (text.length() > 16383) {
      stream.writeUTF(text.substring(0, 16383));
    }
    else {
      stream.writeUTF(text);
    }
  }

  private static void writeFiles(final DataOutput stream, final Set<String> paths) throws IOException {
    stream.writeInt(paths.size());
    for(String s: paths) {
      stream.writeUTF(s);
    }
  }

  private void readFromStream(final DataInput stream, final boolean supportsCopyFromInfo, final boolean supportsReplaced) throws IOException {
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

    if (supportsReplaced) {
      readFiles(stream, myReplacedPaths);
    }

    final int size = stream.readInt();
    for (int i = 0; i < size; i++) {
      myKnownAsDirectories.add(stream.readUTF());
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

  @Nullable
  public VirtualFile getVcsRoot() {
    if (!myCachedInfoLoaded) {
      updateCachedInfo();
    }
    return (myWcRoot == null) ? null : myWcRoot.getRoot();
  }

  @Nullable
  public VirtualFile getRoot() {
    if (!myCachedInfoLoaded) {
      updateCachedInfo();
    }
    return (myWcRoot == null) ? null : myWcRoot.getVirtualFile();
  }

  public RootUrlInfo getWcRootInfo() {
    if (!myCachedInfoLoaded) {
      updateCachedInfo();
    }
    return myWcRoot;
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

    final String commonPath = myCommonPathSearcher.getCommon();
    if (commonPath != null) {
      final SvnFileUrlMapping urlMapping = myVcs.getSvnFileUrlMapping();
      if (urlMapping.isEmpty()) {
        myCachedInfoLoaded = false;
        return;
      }
      final String absoluteUrl = SVNPathUtil.append(myRepositoryRoot, commonPath);
      myWcRoot = urlMapping.getWcRootForUrl(absoluteUrl);
      if (myWcRoot != null) {
        myBranchUrl = SvnUtil.getBranchForUrl(myVcs, myWcRoot.getVirtualFile(), absoluteUrl);
      }
    }
  }

  public void forceReloadCachedInfo(final boolean reloadRoot) {
    myCachedInfoLoaded = false;
    myBranchUrl = null;

    if (reloadRoot) {
      myWcRoot = null;
    }
  }

  public Set<String> getChangedPaths() {
    return myChangedPaths;
  }

  public Set<String> getAddedPaths() {
    return myAddedPaths;
  }

  public Set<String> getDeletedPaths() {
    return myDeletedPaths;
  }

  @Nullable
  public String getWcPath() {
    final RootUrlInfo rootInfo = getWcRootInfo();
    if (rootInfo == null) {
      return null;
    }
    return rootInfo.getIoFile().getAbsolutePath();
  }

  public boolean allPathsUnder(final String path) {
    final String commonRelative = myCommonPathSearcher.getCommon();
    if (commonRelative != null) {
      return SVNPathUtil.isAncestor(path, SVNPathUtil.append(myRepositoryRoot, commonRelative));
    }
    return false;
  }
}
