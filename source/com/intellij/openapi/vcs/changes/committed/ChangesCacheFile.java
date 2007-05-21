package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author yole
 */
public class ChangesCacheFile {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.ChangesCacheFile");
  private static final int VERSION = 6;

  private File myPath;
  private File myIndexPath;
  private RandomAccessFile myStream;
  private RandomAccessFile myIndexStream;
  private boolean myStreamsOpen;
  private Project myProject;
  private AbstractVcs myVcs;
  private CachingCommittedChangesProvider myChangesProvider;
  private FilePath myRootPath;
  private RepositoryLocation myLocation;
  private Date myFirstCachedDate;
  private Date myLastCachedDate;
  private long myFirstCachedChangelist = Long.MAX_VALUE;
  private long myLastCachedChangelist = -1;
  private int myIncomingCount = 0;
  private boolean myHaveCompleteHistory = false;
  private boolean myHeaderLoaded = false;
  @NonNls private static final String INDEX_EXTENSION = ".index";
  private static final int INDEX_ENTRY_SIZE = 3*8+2;
  private static final int HEADER_SIZE = 46;

  public ChangesCacheFile(Project project, File path, AbstractVcs vcs, VirtualFile root, RepositoryLocation location) {
    final Calendar date = Calendar.getInstance();
    date.set(2020, 1, 2);
    myFirstCachedDate = date.getTime();
    date.set(1970, 1, 2);
    myLastCachedDate = date.getTime();
    myProject = project;
    myPath = path;
    myIndexPath = new File(myPath.toString() + INDEX_EXTENSION);
    myVcs = vcs;
    myChangesProvider = (CachingCommittedChangesProvider) vcs.getCommittedChangesProvider();
    myRootPath = new FilePathImpl(root);
    myLocation = location;
  }

  public RepositoryLocation getLocation() {
    return myLocation;
  }

  public CachingCommittedChangesProvider getProvider() {
    return myChangesProvider;
  }

  public boolean isEmpty() throws IOException {
    if (!myPath.exists()) {
      return true;
    }
    try {
      loadHeader();
    }
    catch(VersionMismatchException ex) {
      myPath.delete();
      myIndexPath.delete();
      return true;
    }
    catch(EOFException ex) {
      myPath.delete();
      myIndexPath.delete();
      return true;
    }

    return false;
  }

  public List<CommittedChangeList> writeChanges(final List<CommittedChangeList> changes) throws IOException {
    List<CommittedChangeList> result = new ArrayList<CommittedChangeList>(changes.size());
    boolean wasEmpty = isEmpty();
    openStreams();
    try {
      if (wasEmpty) {
        myHeaderLoaded = true;
        writeHeader();
      }
      myStream.seek(myStream.length());
      IndexEntry[] entries = readLastIndexEntries(0, changes.size());
      // the list and index are sorted in direct chronological order
      Collections.sort(changes, new Comparator<CommittedChangeList>() {
        public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
          return o1.getCommitDate().compareTo(o2.getCommitDate());
        }
      });
      for(CommittedChangeList list: changes) {
        boolean duplicate = false;
        for(IndexEntry entry: entries) {
          if (list.getCommitDate().getTime() == entry.date && list.getNumber() == entry.number) {
            duplicate = true;
            break;
          }
        }
        if (duplicate) {
          LOG.info("Skipping duplicate changelist " + list.getNumber());
          continue;
        }
        LOG.info("Writing incoming changelist " + list.getNumber());
        result.add(list);
        long position = myStream.getFilePointer();
        //noinspection unchecked
        myChangesProvider.writeChangeList(myStream, list);
        updateCachedRange(list);
        writeIndexEntry(list.getNumber(), list.getCommitDate().getTime(), position, false);
        myIncomingCount++;
      }
      writeHeader();
      myHeaderLoaded = true;
    }
    finally {
      closeStreams();
    }
    return result;
  }

  private void updateCachedRange(final CommittedChangeList list) {
    if (list.getCommitDate().getTime() > myLastCachedDate.getTime()) {
      myLastCachedDate = list.getCommitDate();
    }
    if (list.getCommitDate().getTime() < myFirstCachedDate.getTime()) {
      myFirstCachedDate = list.getCommitDate();
    }
    if (list.getNumber() < myFirstCachedChangelist) {
      myFirstCachedChangelist = list.getNumber();
    }
    if (list.getNumber() > myLastCachedChangelist) {
      myLastCachedChangelist = list.getNumber();
    }
  }

  private void writeIndexEntry(long number, long date, long offset, boolean completelyDownloaded) throws IOException {
    myIndexStream.writeLong(number);
    myIndexStream.writeLong(date);
    myIndexStream.writeLong(offset);
    myIndexStream.writeShort(completelyDownloaded ? 1 : 0);
  }

  private void openStreams() throws FileNotFoundException {
    myStream = new RandomAccessFile(myPath, "rw");
    myIndexStream = new RandomAccessFile(myIndexPath, "rw");
    myStreamsOpen = true;
  }

  private void closeStreams() throws IOException {
    myStreamsOpen = false;
    try {
      myStream.close();
    }
    finally {
      myIndexStream.close();
    }
  }

  private void writeHeader() throws IOException {
    assert myStreamsOpen && myHeaderLoaded;
    myStream.seek(0);
    myStream.writeInt(VERSION);
    myStream.writeInt(myChangesProvider.getFormatVersion());
    myStream.writeLong(myLastCachedDate.getTime());
    myStream.writeLong(myFirstCachedDate.getTime());
    myStream.writeLong(myFirstCachedChangelist);
    myStream.writeLong(myLastCachedChangelist);
    myStream.writeShort(myHaveCompleteHistory ? 1 : 0);
    myStream.writeInt(myIncomingCount);
    LOG.info("Saved header for cache of " + myLocation + ": last cached date=" + myLastCachedDate +
             ", last cached number=" + myLastCachedChangelist + ", incoming count=" + myIncomingCount);
  }

  private IndexEntry[] readLastIndexEntries(int offset, int count) throws IOException {
    if (!myIndexPath.exists()) {
      return NO_ENTRIES;
    }
    long totalCount = myIndexStream.length() / INDEX_ENTRY_SIZE;
    if (count > totalCount - offset) {
      count = (int)totalCount - offset;
    }
    if (count == 0) {
      return NO_ENTRIES;
    }
    myIndexStream.seek(myIndexStream.length() - INDEX_ENTRY_SIZE * (count + offset));
    IndexEntry[] result = new IndexEntry[count];
    for(int i=0; i<count; i++) {
      result [i] = new IndexEntry();
      readIndexEntry(result [i]);
    }
    return result;
  }

  private void readIndexEntry(final IndexEntry result) throws IOException {
    result.number = myIndexStream.readLong();
    result.date = myIndexStream.readLong();
    result.offset = myIndexStream.readLong();
    result.completelyDownloaded = (myIndexStream.readShort() != 0);
  }

  public Date getLastCachedDate() throws IOException {
    loadHeader();
    return myLastCachedDate;
  }

  public Date getFirstCachedDate() throws IOException {
    loadHeader();
    return myFirstCachedDate;
  }

  public long getFirstCachedChangelist() throws IOException {
    loadHeader();
    return myFirstCachedChangelist;
  }

  public long getLastCachedChangelist() throws IOException {
    loadHeader();
    return myLastCachedChangelist;
  }

  private void loadHeader() throws IOException {
    if (!myHeaderLoaded) {
      RandomAccessFile stream = new RandomAccessFile(myPath, "r");
      try {
        int version = stream.readInt();
        if (version != VERSION) {
          throw new VersionMismatchException();
        }
        int providerVersion = stream.readInt();
        if (providerVersion != myChangesProvider.getFormatVersion()) {
          throw new VersionMismatchException();
        }
        myLastCachedDate = new Date(stream.readLong());
        myFirstCachedDate = new Date(stream.readLong());
        myFirstCachedChangelist = stream.readLong();
        myLastCachedChangelist = stream.readLong();
        myHaveCompleteHistory = (stream.readShort() != 0);
        myIncomingCount = stream.readInt();
        assert stream.getFilePointer() == HEADER_SIZE;
      }
      finally {
        stream.close();
      }
      myHeaderLoaded = true;
    }
  }

  public List<CommittedChangeList> readChanges(final ChangeBrowserSettings settings, final int maxCount) throws IOException {
    final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
    final ChangeBrowserSettings.Filter filter = settings.createFilter();
    openStreams();
    try {
      if (maxCount == 0) {
        myStream.seek(HEADER_SIZE);  // skip header
        while(myStream.getFilePointer() < myStream.length()) {
          CommittedChangeList changeList = myChangesProvider.readChangeList(myLocation, myStream);
          if (filter.accepts(changeList)) {
            result.add(changeList);
          }
        }
      }
      else if (!settings.isAnyFilterSpecified()) {
        IndexEntry[] entries = readLastIndexEntries(0, maxCount);
        for(IndexEntry entry: entries) {
          myStream.seek(entry.offset);
          result.add(myChangesProvider.readChangeList(myLocation, myStream));
        }
      }
      else {
        int offset = 0;
        while(result.size() < maxCount) {
          IndexEntry[] entries = readLastIndexEntries(offset, 1);
          if (entries.length == 0) {
            break;
          }
          CommittedChangeList changeList = loadChangeListAt(entries [0].offset);
          if (filter.accepts(changeList)) {
            result.add(0, changeList);
          }
          offset++;
        }
      }
      return result;
    }
    finally {
      closeStreams();
    }
  }

  public boolean hasCompleteHistory() {
    return myHaveCompleteHistory;
  }

  public void setHaveCompleteHistory(final boolean haveCompleteHistory) {
    if (myHaveCompleteHistory != haveCompleteHistory) {
      myHaveCompleteHistory = haveCompleteHistory;
      try {
        openStreams();
        try {
          writeHeader();
        }
        finally {
          closeStreams();
        }
      }
      catch(IOException ex) {
        LOG.error(ex);
      }
    }
  }

  public Collection<? extends CommittedChangeList> loadIncomingChanges() throws IOException {
    final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
    int offset = 0;
    openStreams();
    try {
      while(true) {
        IndexEntry[] entries = readLastIndexEntries(offset, 1);
        if (entries.length == 0) {
          break;
        }
        if (!entries [0].completelyDownloaded) {
          result.add(loadChangeListAt(entries[0].offset));
          if (result.size() == myIncomingCount) break;
        }
        offset++;
      }
      LOG.info("Loaded " + result.size() + " incoming changelists");
      return result;
    }
    finally {
      closeStreams();
    }
  }

  private CommittedChangeList loadChangeListAt(final long clOffset) throws IOException {
    myStream.seek(clOffset);
    return myChangesProvider.readChangeList(myLocation, myStream);
  }

  public boolean processUpdatedFiles(UpdatedFiles updatedFiles, List<CommittedChangeList> receivedChanges) throws IOException {
    boolean haveUnaccountedUpdatedFiles = false;
    openStreams();
    loadHeader();
    ReceivedChangeListTracker tracker = new ReceivedChangeListTracker();
    try {
      final List<IncomingChangeListData> incomingData = loadIncomingChangeListData();
      for(FileGroup group: updatedFiles.getTopLevelGroups()) {
        haveUnaccountedUpdatedFiles |= processGroup(group, incomingData, tracker);
      }
      if (!haveUnaccountedUpdatedFiles) {
        for(IncomingChangeListData data: incomingData) {
          saveIncoming(data);
        }
        writeHeader();
      }
    }
    finally {
      closeStreams();
    }
    receivedChanges.addAll(tracker.getChangeLists());
    return haveUnaccountedUpdatedFiles;
  }

  private void saveIncoming(final IncomingChangeListData data) throws IOException {
    writePartial(data);
    if (data.accountedChanges.size() == data.changeList.getChanges().size()) {
      LOG.info("Removing changelist " + data.changeList.getNumber() + " from incoming changelists");
      myIndexStream.seek(data.indexOffset);
      writeIndexEntry(data.indexEntry.number, data.indexEntry.date, data.indexEntry.offset, true);
      myIncomingCount--;
    }
  }

  private boolean processGroup(final FileGroup group, final List<IncomingChangeListData> incomingData,
                               final ReceivedChangeListTracker tracker) {
    boolean haveUnaccountedUpdatedFiles = false;
    final List<Pair<String,VcsRevisionNumber>> list = group.getFilesAndRevisions(myProject);
    for(Pair<String, VcsRevisionNumber> pair: list) {
      final String file = pair.first;
      FilePath path = new FilePathImpl(new File(file), false);
      if (!path.isUnder(myRootPath, false) || pair.second == null) {
        continue;
      }
      if (group.getId().equals(FileGroup.REMOVED_FROM_REPOSITORY_ID)) {
        haveUnaccountedUpdatedFiles |= processDeletedFile(path, incomingData, tracker);
      }
      else {
        haveUnaccountedUpdatedFiles |= processFile(path, pair.second, incomingData, tracker);
      }
    }
    for(FileGroup childGroup: group.getChildren()) {
      haveUnaccountedUpdatedFiles |= processGroup(childGroup, incomingData, tracker);
    }
    return haveUnaccountedUpdatedFiles;
  }

  private static boolean processFile(final FilePath path,
                                     final VcsRevisionNumber number,
                                     final List<IncomingChangeListData> incomingData,
                                     final ReceivedChangeListTracker tracker) {
    boolean foundRevision = false;
    LOG.info("Processing updated file " + path + ", revision " + number);
    for(IncomingChangeListData data: incomingData) {
      for(Change change: data.changeList.getChanges()) {
        ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null && afterRevision.getFile().equals(path)) {
          int rc = number.compareTo(afterRevision.getRevisionNumber());
          if (rc == 0) {
            foundRevision = true;
          }
          if (rc >= 0) {
            tracker.addChange(data.changeList, change);
            data.accountedChanges.add(change);
          }
        }
      }
    }
    LOG.info(foundRevision ? "All changes for file found" : "Some of changes for file not found");
    return !foundRevision;
  }

  private static boolean processDeletedFile(final FilePath path,
                                            final List<IncomingChangeListData> incomingData,
                                            final ReceivedChangeListTracker tracker) {
    boolean foundRevision = false;
    for(IncomingChangeListData data: incomingData) {
      for(Change change: data.changeList.getChanges()) {
        ContentRevision beforeRevision = change.getBeforeRevision();
        if (beforeRevision != null && beforeRevision.getFile().equals(path)) {
          tracker.addChange(data.changeList, change);
          data.accountedChanges.add(change);
          if (change.getAfterRevision() == null) {
            foundRevision = true;
          }
        }
      }
    }
    return !foundRevision;
  }

  private List<IncomingChangeListData> loadIncomingChangeListData() throws IOException {
    final long length = myIndexStream.length();
    long totalCount = length / INDEX_ENTRY_SIZE;
    List<IncomingChangeListData> incomingData = new ArrayList<IncomingChangeListData>();
    for(int i=0; i<totalCount; i++) {
      final long indexOffset = length - (i + 1) * INDEX_ENTRY_SIZE;
      myIndexStream.seek(indexOffset);
      IndexEntry e = new IndexEntry();
      readIndexEntry(e);
      if (!e.completelyDownloaded) {
        IncomingChangeListData data = new IncomingChangeListData();
        data.indexOffset = indexOffset;
        data.indexEntry = e;
        data.changeList = loadChangeListAt(e.offset);
        readPartial(data);
        incomingData.add(data);
        if (incomingData.size() == myIncomingCount) {
          break;
        }
      }
    }
    LOG.info("Loaded " + incomingData.size() + " incoming changelist pointers");
    return incomingData;
  }

  private void writePartial(final IncomingChangeListData data) throws IOException {
    File partialFile = getPartialPath(data.indexEntry.offset);
    final int accounted = data.accountedChanges.size();
    if (accounted == data.changeList.getChanges().size()) {
      partialFile.delete();
    }
    else if (accounted > 0) {
      RandomAccessFile file = new RandomAccessFile(partialFile, "rw");
      try {
        file.writeInt(accounted);
        for(Change c: data.accountedChanges) {
          boolean isAfterRevision = true;
          ContentRevision revision = c.getAfterRevision();
          if (revision == null) {
            isAfterRevision = false;
            revision = c.getBeforeRevision();
            assert revision != null;
          }
          file.writeByte(isAfterRevision ? 1 : 0);
          file.writeUTF(revision.getFile().getIOFile().toString());
        }
      }
      finally {
        file.close();
      }
    }
  }

  private void readPartial(IncomingChangeListData data) {
    HashSet<Change> result = new HashSet<Change>();
    try {
      File partialFile = getPartialPath(data.indexEntry.offset);
      if (partialFile.exists()) {
        RandomAccessFile file = new RandomAccessFile(partialFile, "r");
        try {
          int count = file.readInt();
          for(int i=0; i<count; i++) {
            boolean isAfterRevision = (file.readByte() != 0);
            String path = file.readUTF();
            for(Change c: data.changeList.getChanges()) {
              final ContentRevision afterRevision = isAfterRevision ? c.getAfterRevision() : c.getBeforeRevision();
              if (afterRevision != null && afterRevision.getFile().getIOFile().toString().equals(path)) {
                result.add(c);
              }
            }
          }
        }
        finally {
          file.close();
        }
      }
    }
    catch(IOException ex) {
      LOG.error(ex);
    }
    data.accountedChanges = result;
  }

  @NonNls
  private File getPartialPath(final long offset) {
    return new File(myPath + "." + offset + ".partial");
  }

  public boolean refreshIncomingChanges() throws IOException {
    final DiffProvider diffProvider = myVcs.getDiffProvider();
    if (diffProvider == null) return false;
    final Collection<FilePath> incomingFiles = myChangesProvider.getIncomingFiles(myLocation);
    boolean anyChanges = false;
    openStreams();
    loadHeader();
    FactoryMap<VirtualFile, VcsRevisionNumber> currentRevisions = new FactoryMap<VirtualFile, VcsRevisionNumber>() {
      protected VcsRevisionNumber create(final VirtualFile key) {
        return diffProvider.getCurrentRevision(key);
      }
    };
    try {
      final List<IncomingChangeListData> list = loadIncomingChangeListData();
      // the incoming changelist pointers are actually sorted in reverse chronological order,
      // so we process file delete changes before changes made to deleted files before they were deleted
      final Set<FilePath> deletedFiles = new HashSet<FilePath>();
      final Set<FilePath> createdFiles = new HashSet<FilePath>();
      for(IncomingChangeListData data: list) {
        LOG.info("Checking incoming changelist " + data.changeList.getNumber());
        boolean updated = false;
        for(Change change: data.changeList.getChanges()) {
          if (data.accountedChanges.contains(change)) continue;
          final boolean changeFound = processIncomingChange(change, currentRevisions, incomingFiles, deletedFiles, createdFiles);
          if (changeFound) {
            data.accountedChanges.add(change);
          }
          updated |= changeFound;
        }
        if (updated) {
          anyChanges = true;
          saveIncoming(data);
        }
      }
      if (anyChanges) {
        writeHeader();
      }
    }
    finally {
      closeStreams();
    }
    return anyChanges;
  }

  private boolean processIncomingChange(final Change change,
                                        final FactoryMap<VirtualFile, VcsRevisionNumber> currentRevisions,
                                        @Nullable final Collection<FilePath> incomingFiles,
                                        final Set<FilePath> deletedFiles,
                                        final Set<FilePath> createdFiles) {
    ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision != null) {
      if (afterRevision.getFile().isNonLocal()) {
        // don't bother to search for nonlocal paths on local disk
        return true;
      }
      if (change.getBeforeRevision() == null) {
        final FilePath path = afterRevision.getFile();
        LOG.info("Marking created file " + path);
        createdFiles.add(path);
      }
      if (incomingFiles != null && !incomingFiles.contains(afterRevision.getFile())) {
        LOG.info("Skipping new/changed file outside of incoming files: " + afterRevision.getFile());
        return true;
      }
      afterRevision.getFile().refresh();
      LOG.info("Checking file " + afterRevision.getFile().getPath());
      VirtualFile file = afterRevision.getFile().getVirtualFile();
      if (file != null) {
        VcsRevisionNumber revision = currentRevisions.get(file);
        if (revision != null) {
          LOG.info("Current revision is " + revision + ", changelist revision is " + afterRevision.getRevisionNumber());
          if (myChangesProvider.isChangeLocallyAvailable(afterRevision.getFile(), revision, afterRevision.getRevisionNumber())) {
            return true;
          }
        }
        else {
          LOG.info("Failed to fetch revision");
        }
      }
      else if (isDeletedFile(deletedFiles, afterRevision)) {
        LOG.info("Found deleted file");
        return true;
      }
      else {
        LOG.info("Could not find local file for change " + afterRevision.getFile().getPath());
      }
    }
    else {
      ContentRevision beforeRevision = change.getBeforeRevision();
      assert beforeRevision != null;
      LOG.info("Checking deleted file " + beforeRevision.getFile());
      deletedFiles.add(beforeRevision.getFile());
      if (incomingFiles != null && !incomingFiles.contains(beforeRevision.getFile())) {
        LOG.info("Skipping deleted file outside of incoming files: " + beforeRevision.getFile());
        return true;
      }
      beforeRevision.getFile().refresh();
      if (beforeRevision.getFile().getVirtualFile() == null || createdFiles.contains(beforeRevision.getFile())) {
        // file has already been deleted
        return true;
      }
      else {
        LOG.info("File exists locally and no 'create' change found for it");
      }
    }
    return false;
  }

  private static boolean isDeletedFile(final Set<FilePath> deletedFiles, final ContentRevision afterRevision) {
    FilePath file = afterRevision.getFile();
    while(file != null) {
      if (deletedFiles.contains(file)) {
        return true;
      }
      file = file.getParentPath();
    }
    return false;
  }

  private static class IndexEntry {
    long number;
    long date;
    long offset;
    boolean completelyDownloaded;
  }

  private static class IncomingChangeListData {
    public long indexOffset;
    public IndexEntry indexEntry;
    public CommittedChangeList changeList;
    public Set<Change> accountedChanges;
  }

  private static final IndexEntry[] NO_ENTRIES = new IndexEntry[0];

  private static class VersionMismatchException extends RuntimeException {
  }

  private static class ReceivedChangeListTracker {
    private Map<CommittedChangeList, ReceivedChangeList> myMap = new HashMap<CommittedChangeList, ReceivedChangeList>();

    public void addChange(CommittedChangeList changeList, Change change) {
      ReceivedChangeList list = myMap.get(changeList);
      if (list == null) {
        list = new ReceivedChangeList(changeList);
        myMap.put(changeList, list);
      }
      list.addChange(change);
    }

    public Collection<? extends CommittedChangeList> getChangeLists() {
      return myMap.values();
    }
  }
}
