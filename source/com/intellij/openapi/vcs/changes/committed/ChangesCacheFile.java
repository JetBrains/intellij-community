package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * @author yole
 */
public class ChangesCacheFile {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.ChangesCacheFile");
  private static final int VERSION = 2;

  private File myPath;
  private File myIndexPath;
  private RandomAccessFile myStream;
  private RandomAccessFile myIndexStream;
  boolean myStreamsOpen;
  private CachingCommittedChangesProvider myChangesProvider;
  private RepositoryLocation myLocation;
  private Date myFirstCachedDate = new Date(2020, 1, 2);
  private Date myLastCachedDate = new Date(1970, 1, 2);
  private long myFirstCachedChangelist = Long.MAX_VALUE;
  private boolean myHaveCompleteHistory = false;
  private boolean myHeaderLoaded = false;
  @NonNls private static final String INDEX_EXTENSION = ".index";
  private static final int INDEX_ENTRY_SIZE = 3*8;
  private static final int HEADER_SIZE = 30;

  public ChangesCacheFile(final File path, final CachingCommittedChangesProvider changesProvider, final RepositoryLocation location) {
    myPath = path;
    myIndexPath = new File(myPath.toString() + INDEX_EXTENSION);
    myChangesProvider = changesProvider;
    myLocation = location;
  }

  public boolean isEmpty() {
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

    return false;
  }

  public List<CommittedChangeList> writeChanges(final List<CommittedChangeList> changes) throws IOException {
    List<CommittedChangeList> result = new ArrayList<CommittedChangeList>(changes.size());
    boolean wasEmpty = isEmpty();
    openStreams();
    try {
      if (wasEmpty) {
        writeHeader();
      }
      myStream.seek(myStream.length());
      IndexEntry[] entries = readLastIndexEntries(myIndexStream, 0, 1);
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
        if (duplicate) continue;
        result.add(list);
        long position = myStream.getFilePointer();
        //noinspection unchecked
        myChangesProvider.writeChangeList(myStream, list);
        if (list.getCommitDate().getTime() > myLastCachedDate.getTime()) {
          myLastCachedDate = list.getCommitDate();
        }
        if (list.getCommitDate().getTime() < myFirstCachedDate.getTime()) {
          myFirstCachedDate = list.getCommitDate();
        }
        if (list.getNumber() < myFirstCachedChangelist) {
          myFirstCachedChangelist = list.getNumber(); 
        }
        myIndexStream.writeLong(list.getNumber());
        myIndexStream.writeLong(list.getCommitDate().getTime());
        myIndexStream.writeLong(position);
      }
      writeHeader();
      myHeaderLoaded = true;
    }
    finally {
      closeStreams();
    }
    return result;
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
    assert myStreamsOpen;
    myStream.seek(0);
    myStream.writeInt(VERSION);
    myStream.writeLong(myLastCachedDate.getTime());
    myStream.writeLong(myFirstCachedDate.getTime());
    myStream.writeLong(myFirstCachedChangelist);
    myStream.writeShort(myHaveCompleteHistory ? 1 : 0);
  }

  private IndexEntry[] readLastIndexEntries(final RandomAccessFile indexStream, int offset, int count) throws IOException {
    if (!myIndexPath.exists()) {
      return NO_ENTRIES;
    }
    long totalCount = indexStream.length() / INDEX_ENTRY_SIZE;
    if (count > totalCount - offset) {
      count = (int)totalCount - offset;
    }
    if (count == 0) {
      return NO_ENTRIES;
    }
    indexStream.seek(indexStream.length() - INDEX_ENTRY_SIZE * (count + offset));
    IndexEntry[] result = new IndexEntry[count];
    for(int i=0; i<count; i++) {
      result [i] = new IndexEntry();
      result [i].number = indexStream.readLong();
      result [i].date = indexStream.readLong();
      result [i].offset = indexStream.readLong();
    }
    return result;
  }

  public Date getLastCachedDate() {
    loadHeader();
    return myLastCachedDate;
  }

  public Date getFirstCachedDate() {
    loadHeader();
    return myFirstCachedDate;
  }

  public long getFirstCachedChangelist() {
    loadHeader();
    return myFirstCachedChangelist;
  }

  private void loadHeader() {
    if (!myHeaderLoaded) {
      try {
        RandomAccessFile stream = new RandomAccessFile(myPath, "r");
        try {
          int version = stream.readInt();
          if (version != VERSION) {
            throw new VersionMismatchException();
          }
          myLastCachedDate = new Date(stream.readLong());
          myFirstCachedDate = new Date(stream.readLong());
          myFirstCachedChangelist = stream.readLong();
          myHaveCompleteHistory = (stream.readShort() != 0);
          assert stream.getFilePointer() == HEADER_SIZE;
        }
        finally {
          stream.close();
        }
      }
      catch (IOException e) {
        LOG.error(e);
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
        IndexEntry[] entries = readLastIndexEntries(myIndexStream, 0, maxCount);
        for(IndexEntry entry: entries) {
          myStream.seek(entry.offset);
          CommittedChangeList changeList = myChangesProvider.readChangeList(myLocation, myStream);
          result.add(changeList);
        }
      }
      else {
        int offset = 0;
        while(result.size() < maxCount) {
          IndexEntry[] entries = readLastIndexEntries(myIndexStream, offset, 1);
          if (entries.length == 0) {
            break;
          }
          myStream.seek(entries [0].offset);
          CommittedChangeList changeList = myChangesProvider.readChangeList(myLocation, myStream);
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

  private static class IndexEntry {
    long number;
    long date;
    long offset;
  }

  private static final IndexEntry[] NO_ENTRIES = new IndexEntry[0];

  private static class VersionMismatchException extends RuntimeException {
  }
}
