// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.svn.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.VcsRevisionNumberAware;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConstantFunction;
import com.intellij.util.NotNullFunction;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.jetbrains.idea.svn.SvnUtil.*;

public class SvnChangeList implements CommittedChangeList, VcsRevisionNumberAware {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history");

  private final SvnVcs myVcs;
  private final SvnRepositoryLocation myLocation;
  private String myRepositoryRoot;
  private long myRevision;
  private VcsRevisionNumber myRevisionNumber;
  private String myAuthor;
  private Date myDate;
  private String myMessage;
  private final Set<String> myChangedPaths = new HashSet<>();
  private final Set<String> myAddedPaths = new HashSet<>();
  private final Set<String> myDeletedPaths = new HashSet<>();
  private final Set<String> myReplacedPaths = new HashSet<>();

  private ChangesListCreationHelper myListsHolder;

  private Url myBranchUrl;

  private boolean myCachedInfoLoaded;

  // key: added path, value: copied-from
  private final TreeMap<String, String> myCopiedAddedPaths = new TreeMap<>();
  private RootUrlInfo myWcRoot;
  private final CommonPathSearcher myCommonPathSearcher;
  private final Set<String> myKnownAsDirectories;

  public SvnChangeList(@NotNull final List<CommittedChangeList> lists, @NotNull final SvnRepositoryLocation location) {

    final SvnChangeList sample = (SvnChangeList) lists.get(0);
    myVcs = sample.myVcs;
    myLocation = location;
    setRevision(sample.myRevision);
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
    myKnownAsDirectories = new HashSet<>(0);
  }

  public SvnChangeList(@NotNull SvnVcs vcs,
                       @NotNull SvnRepositoryLocation location,
                       @NotNull LogEntry logEntry,
                       @NotNull Url repositoryRoot) {
    this(vcs, location, logEntry, repositoryRoot.toDecodedString());
  }

  @Deprecated // Required for compatibility with external plugins.
  public SvnChangeList(@NotNull SvnVcs vcs,
                       @NotNull SvnRepositoryLocation location,
                       @NotNull LogEntry logEntry,
                       @NotNull String repositoryRoot) {
    myVcs = vcs;
    myLocation = location;
    setRevision(logEntry.getRevision());
    myAuthor = StringUtil.notNullize(logEntry.getAuthor());
    myDate = logEntry.getDate();
    myMessage = StringUtil.notNullize(logEntry.getMessage());
    myRepositoryRoot = UriUtil.trimTrailingSlashes(repositoryRoot);

    myCommonPathSearcher = new CommonPathSearcher();

    myKnownAsDirectories = new HashSet<>(0);
    for(LogEntryPath entry : logEntry.getChangedPaths().values()) {
      final String path = entry.getPath();

      if (entry.isDirectory()) {
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

  public SvnChangeList(SvnVcs vcs, @NotNull SvnRepositoryLocation location, @NotNull DataInput stream, final boolean supportsCopyFromInfo,
                       final boolean supportsReplaced) throws IOException {
    myVcs = vcs;
    myLocation = location;
    myKnownAsDirectories = new HashSet<>();
    readFromStream(stream, supportsCopyFromInfo, supportsReplaced);
    myCommonPathSearcher = new CommonPathSearcher();
    myCommonPathSearcher.next(myAddedPaths);
    myCommonPathSearcher.next(myDeletedPaths);
    myCommonPathSearcher.next(myChangedPaths);
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

  @Nullable
  public Date getCommitDate() {
    return myDate;
  }

  @Nullable
  @Override
  public VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  private void setRevision(long revision) {
    myRevision = revision;
    myRevisionNumber = new SvnRevisionNumber(Revision.of(revision));
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
    final Map<String, ExternallyRenamedChange> copiedAddedChanges = new HashMap<>();

    correctBeforePaths();
    final List<String> copyDeleted = new ArrayList<>(myDeletedPaths);

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
        if ((copyFromPath != null) && (Url.isAncestor(addedPath, path))) {
          if (addedPath.length() < path.length()) {
            String relative = Url.getRelative(addedPath, path);
            copyFromPath = Url.append(copyFromPath, relative);
          }
          final ExternallyRenamedChange renamedChange = new ExternallyRenamedChange(myListsHolder.createRevisionLazily(copyFromPath, true),
                                                     myListsHolder.createRevisionLazily(path, false), copyFromPath);
          moveAndChange = true;
          renamedChange.getMoveRelativePath(myVcs.getProject());
          renamedChange.setIsReplaced(replaced);

          final ExternallyRenamedChange addedChange = copiedAddedChanges.get(myCopiedAddedPaths.get(addedPath));
          renamedChange.setCopied(addedChange != null && addedChange.isCopied());

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
    final HashSet<String> copy = new HashSet<>(paths);
    paths.clear();
    for (String s : copy) {
      paths.add(helper.convertBeforePath(s, myCopiedAddedPaths));
    }
  }

  @Nullable
  private FilePath getLocalPath(@NotNull String path, final NotNullFunction<File, Boolean> detector) {
    if (myVcs.getProject().isDefault()) return null;

    Url absoluteUrl = parseUrl(myRepositoryRoot + path, false);
    final RootUrlInfo rootForUrl = myVcs.getSvnFileUrlMapping().getWcRootForUrl(absoluteUrl);
    FilePath result = null;

    if (rootForUrl != null) {
      String relativePath = getRelativeUrl(rootForUrl.getUrl(), absoluteUrl);
      File file = new File(rootForUrl.getPath(), relativePath);
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      result = virtualFile != null ? VcsUtil.getFilePath(virtualFile) : VcsUtil.getFilePath(file, detector.fun(file).booleanValue());
    }

    return result;
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

    private ChangesListCreationHelper() {
      myList = new ArrayList<>();
      myWithoutDirStatus = new ArrayList<>();
      myPathToChangeMapping = new HashMap<>();
    }

    public void add(final String path, final Change change) {
      patchChange(change, path);
      myList.add(change);
      myPathToChangeMapping.put(path, change);
    }

    public Change getByPath(final String path) {
      return myPathToChangeMapping.get(path);
    }

    @Nullable
    private FilePath localDeletedPath(@NotNull Url url, final boolean isDir) {
      final SvnFileUrlMapping urlMapping = myVcs.getSvnFileUrlMapping();
      final File file = urlMapping.getLocalPath(url);
      if (file != null) {
        return VcsUtil.getFilePath(file.getAbsolutePath(), isDir || file.isDirectory());
      }

      return null;
    }

    public SvnRepositoryContentRevision createDeletedItemRevision(@NotNull String path, final boolean isBeforeRevision) {
      final boolean knownAsDirectory = myKnownAsDirectories.contains(path);
      Url url = parseUrl(myRepositoryRoot + path, false);
      if (! knownAsDirectory) {
        myWithoutDirStatus.add(Pair.create(myList.size(), isBeforeRevision));
      }
      return SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path, localDeletedPath(url, knownAsDirectory),
                                                 getRevision(isBeforeRevision));
    }

    public SvnRepositoryContentRevision createRevisionLazily(final String path, final boolean isBeforeRevision) {
      final boolean knownAsDirectory = myKnownAsDirectories.contains(path);
      final FilePath localPath = getLocalPath(path, file -> {
        if (knownAsDirectory) return Boolean.TRUE;
        // list will be next
        myWithoutDirStatus.add(new Pair<>(myList.size(), isBeforeRevision));
        return Boolean.FALSE;
      });
      long revision = getRevision(isBeforeRevision);
      return localPath == null
             ? SvnRepositoryContentRevision.createForRemotePath(myVcs, myRepositoryRoot, path, knownAsDirectory, revision)
             : SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path, localPath, revision);
    }

    public List<Change> getList() {
      return myList;
    }

    public List<Change> getDetailedList() {
      if (myDetailedList == null) {
        myDetailedList = new ArrayList<>(myList);

        try {
          doRemoteDetails();
          uploadDeletedRenamedChildren();
          ContainerUtil.removeDuplicates(myDetailedList);
        }
        catch (VcsException e) {
          LOG.info(e);
        }
      }
      return myDetailedList;
    }

    private void doRemoteDetails() throws SvnBindException {
      for (Pair<Integer, Boolean> idxData : myWithoutDirStatus) {
        final Change sourceChange = myDetailedList.get(idxData.first.intValue());
        final SvnRepositoryContentRevision revision = (SvnRepositoryContentRevision)
            (idxData.second.booleanValue() ? sourceChange.getBeforeRevision() : sourceChange.getAfterRevision());
        if (revision == null) {
          continue;
        }
        // TODO: Logic with detecting "isDirectory" status is not clear enough. Why we can't just collect this info from logEntry and
        // TODO: if loading from disk - use cached values? Not to invoke separate call here.
        Revision beforeRevision = Revision.of(getRevision(idxData.second.booleanValue()));
        Info info = myVcs.getInfo(createUrl(revision.getFullPath()), beforeRevision, beforeRevision);
        boolean isDirectory = info != null && info.isDirectory();
        Change replacingChange = new Change(createRevision((SvnRepositoryContentRevision)sourceChange.getBeforeRevision(), isDirectory),
                                            createRevision((SvnRepositoryContentRevision)sourceChange.getAfterRevision(), isDirectory));
        replacingChange.setIsReplaced(sourceChange.isIsReplaced());
        myDetailedList.set(idxData.first.intValue(), replacingChange);
      }

      myWithoutDirStatus.clear();
    }

    @Nullable
    private SvnRepositoryContentRevision createRevision(final SvnRepositoryContentRevision previousRevision, final boolean isDir) {
      return previousRevision == null ? null :
             SvnRepositoryContentRevision.create(myVcs, previousRevision.getFullPath(),
                                                 VcsUtil.getFilePath(previousRevision.getFile().getPath(), isDir),
                                                 previousRevision.getRevisionNumber().getRevision().getNumber());
    }

    private void uploadDeletedRenamedChildren() throws VcsException {
      Set<Pair<Boolean, String>> duplicates = collectDuplicates();
      List<Change> preprocessed = ChangesPreprocess.preprocessChangesRemoveDeletedForDuplicateMoved(myDetailedList);

      myDetailedList.addAll(collectDetails(preprocessed, duplicates));
    }

    private List<Change> collectDetails(@NotNull List<Change> changes, @NotNull Set<Pair<Boolean, String>> duplicates)
      throws VcsException {
      List<Change> result = ContainerUtil.newArrayList();

      for (Change change : changes) {
        // directory statuses are already uploaded
        if ((change.getAfterRevision() == null) && (change.getBeforeRevision().getFile().isDirectory())) {
          result.addAll(getChildrenAsChanges(change.getBeforeRevision(), true, duplicates));
        } else if ((change.getBeforeRevision() == null) && (change.getAfterRevision().getFile().isDirectory())) {
          // look for renamed folders contents
          if (myCopiedAddedPaths.containsKey(getRelativePath(change.getAfterRevision()))) {
            result.addAll(getChildrenAsChanges(change.getAfterRevision(), false, duplicates));
          }
        } else if ((change.isIsReplaced() || change.isMoved() || change.isRenamed()) && change.getAfterRevision().getFile().isDirectory()) {
          result.addAll(getChildrenAsChanges(change.getBeforeRevision(), true, duplicates));
          result.addAll(getChildrenAsChanges(change.getAfterRevision(), false, duplicates));
        }
      }

      return result;
    }

    private Set<Pair<Boolean, String>> collectDuplicates() {
      Set<Pair<Boolean, String>> result = ContainerUtil.newHashSet();

      for (Change change : myDetailedList) {
        addDuplicate(result, true, change.getBeforeRevision());
        addDuplicate(result, false, change.getAfterRevision());
      }

      return result;
    }

    private void addDuplicate(@NotNull Set<Pair<Boolean, String>> duplicates,
                              boolean isBefore,
                              @Nullable ContentRevision revision) {
      if (revision != null) {
        duplicates.add(Pair.create(isBefore, getRelativePath(revision)));
      }
    }

    @NotNull
    private String getRelativePath(@NotNull ContentRevision revision) {
      return ((SvnRepositoryContentRevision)revision).getRelativePath(myRepositoryRoot);
    }

    @NotNull
    private Collection<Change> getChildrenAsChanges(@NotNull ContentRevision contentRevision,
                                                    final boolean isBefore,
                                                    @NotNull final Set<Pair<Boolean, String>> duplicates)
      throws VcsException {
      final List<Change> result = new ArrayList<>();

      final String path = getRelativePath(contentRevision);
      Url fullPath = createUrl(((SvnRepositoryContentRevision)contentRevision).getFullPath());
      Revision revisionNumber = Revision.of(getRevision(isBefore));
      Target target = Target.on(fullPath, revisionNumber);

      myVcs.getFactory(target).createBrowseClient().list(target, revisionNumber, Depth.INFINITY, entry -> {
        final String childPath = path + '/' + entry.getRelativePath();

        if (!duplicates.contains(Pair.create(isBefore, childPath))) {
          final ContentRevision contentRevision1 = createRevision(childPath, isBefore, entry.isDirectory());
          result.add(new Change(isBefore ? contentRevision1 : null, isBefore ? null : contentRevision1));
        }
      });

      return result;
    }

    private SvnRepositoryContentRevision createRevision(final String path, final boolean isBeforeRevision, final boolean isDir) {
      return SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path,
                                                 getLocalPath(path, new ConstantFunction<>(isDir)), getRevision(isBeforeRevision));
    }
  }

  private static class RenameHelper {

    public String convertBeforePath(final String path, final TreeMap<String, String> after2before) {
      String current = path;
      // backwards
      for (String key : after2before.descendingKeySet()) {
        if (Url.isAncestor(key, current)) {
          final String relativePath = Url.getRelative(key, current);
          current = Url.append(after2before.get(key), relativePath);
        }
      }
      return current;
    }
  }

  private void patchChange(Change change, final String path) {
    final Url becameUrl;
    Url wasUrl;
    try {
      becameUrl = createUrl(Url.append(myRepositoryRoot, path));
      wasUrl = becameUrl;

      if (change instanceof ExternallyRenamedChange && change.getBeforeRevision() != null) {
        String originUrl = ((ExternallyRenamedChange)change).getOriginUrl();

        if (originUrl != null) {
          // use another url for origin
          wasUrl = createUrl(Url.append(myRepositoryRoot, originUrl));
        }
      }
    }
    catch (SvnBindException e) {
      LOG.info(e);
      return;
    }

    final FilePath filePath = ChangesUtil.getFilePath(change);
    final Change additional = new Change(createPropertyRevision(filePath, change.getBeforeRevision(), wasUrl),
                                         createPropertyRevision(filePath, change.getAfterRevision(), becameUrl));
    change.addAdditionalLayerElement(SvnChangeProvider.PROPERTY_LAYER, additional);
  }

  @Nullable
  private SvnLazyPropertyContentRevision createPropertyRevision(@NotNull FilePath filePath,
                                                                @Nullable ContentRevision revision,
                                                                @NotNull Url url) {
    return revision == null ? null : new SvnLazyPropertyContentRevision(myVcs, filePath, revision.getRevisionNumber(), url);
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

  public void writeToStream(@NotNull DataOutput stream) throws IOException {
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

  private void readFromStream(@NotNull DataInput stream, final boolean supportsCopyFromInfo, final boolean supportsReplaced)
    throws IOException {
    myRepositoryRoot = stream.readUTF();
    setRevision(stream.readLong());
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

  public Url getBranchUrl() {
    ensureCacheUpdated();

    return myBranchUrl;
  }

  @Nullable
  public VirtualFile getVcsRoot() {
    ensureCacheUpdated();

    return myWcRoot == null ? null : myWcRoot.getRoot();
  }

  @Nullable
  public VirtualFile getRoot() {
    ensureCacheUpdated();

    return myWcRoot == null ? null : myWcRoot.getVirtualFile();
  }

  public RootUrlInfo getWcRootInfo() {
    ensureCacheUpdated();

    return myWcRoot;
  }

  private void ensureCacheUpdated() {
    if (!myCachedInfoLoaded) {
      updateCachedInfo();
    }
  }

  private static class CommonPathSearcher {
    private String myCommon;

    public void next(Iterable<String> values) {
      for (String value : values) {
        next(value);
      }
    }

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

      myCommon = Url.getCommonAncestor(myCommon, value);
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
      Url absoluteUrl = parseUrl(Url.append(myRepositoryRoot, commonPath), false);
      myWcRoot = urlMapping.getWcRootForUrl(absoluteUrl);
      if (myWcRoot != null) {
        myBranchUrl = SvnUtil.getBranchForUrl(myVcs, myWcRoot.getVirtualFile(), absoluteUrl);
      }
    }
  }

  public void forceReloadCachedInfo() {
    myCachedInfoLoaded = false;
    myBranchUrl = null;
    myWcRoot = null;
  }

  @NotNull
  public Set<String> getAffectedPaths() {
    return ContainerUtil.newHashSet(ContainerUtil.concat(myAddedPaths, myDeletedPaths, myChangedPaths));
  }

  @Nullable
  public String getWcPath() {
    final RootUrlInfo rootInfo = getWcRootInfo();

    return rootInfo == null ? null : rootInfo.getIoFile().getAbsolutePath();
  }

  public boolean allPathsUnder(final String path) {
    final String commonRelative = myCommonPathSearcher.getCommon();

    return commonRelative != null && Url.isAncestor(path, Url.append(myRepositoryRoot, commonRelative));
  }
}
