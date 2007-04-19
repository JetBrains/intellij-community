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
import java.util.*;

/**
 * @author yole
 */
public class ChangesCacheFile {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.ChangesCacheFile");
  private static final int VERSION = 1;

  private File myPath;
  private File myIndexPath;
  private CachingCommittedChangesProvider myChangesProvider;
  private RepositoryLocation myLocation;
  private Date myLastCachedDate = new Date(1970, 1, 2);
  private boolean myHeaderLoaded = false;
  @NonNls private static final String INDEX_EXTENSION = ".index";
  private static final int INDEX_ENTRY_SIZE = 3*8;
  private static final int HEADER_SIZE = 12;

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
    return false;
  }

  public List<CommittedChangeList> writeChanges(final List<CommittedChangeList> changes) throws IOException {
    List<CommittedChangeList> result = new ArrayList<CommittedChangeList>(changes.size());
    boolean wasEmpty = isEmpty();
    RandomAccessFile stream = new RandomAccessFile(myPath, "rw");
    RandomAccessFile indexStream = new RandomAccessFile(myIndexPath, "rw");
    try {
      if (wasEmpty) {
        stream.writeInt(VERSION);
        stream.writeLong(0L);
      }
      stream.seek(stream.length());
      IndexEntry[] entries = readLastIndexEntries(indexStream, 1);
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
        long position = stream.getFilePointer();
        //noinspection unchecked
        myChangesProvider.writeChangeList(stream, list);
        if (list.getCommitDate().getTime() > myLastCachedDate.getTime()) {
          myLastCachedDate = new Date(list.getCommitDate().getTime());
        }
        indexStream.writeLong(list.getNumber());
        indexStream.writeLong(list.getCommitDate().getTime());
        indexStream.writeLong(position);
      }
      stream.seek(4);
      stream.writeLong(myLastCachedDate.getTime());
      myHeaderLoaded = true;
    }
    finally {
      stream.close();
      indexStream.close();
    }
    return result;
  }

  private IndexEntry[] readLastIndexEntries(final RandomAccessFile indexStream, int count) throws IOException {
    if (!myIndexPath.exists()) {
      return NO_ENTRIES;
    }
    long totalCount = indexStream.length() / INDEX_ENTRY_SIZE;
    if (count > totalCount) {
      count = (int)totalCount;
    }
    if (count == 0) {
      return NO_ENTRIES;
    }
    indexStream.seek(indexStream.length() - INDEX_ENTRY_SIZE * count);
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
    if (!myHeaderLoaded) {
      try {
        RandomAccessFile stream = new RandomAccessFile(myPath, "r");
        try {
          stream.seek(4);
          myLastCachedDate = new Date(stream.readLong());
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
    return myLastCachedDate;
  }

  public List<CommittedChangeList> readChanges(final ChangeBrowserSettings settings) throws IOException {
    final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
    final ChangeBrowserSettings.Filter filter = settings.createFilter();
    RandomAccessFile stream = new RandomAccessFile(myPath, "r");
    try {
      stream.seek(HEADER_SIZE);  // skip header
      while(stream.getFilePointer() < stream.length()) {
        CommittedChangeList changeList = myChangesProvider.readChangeList(myLocation, stream);
        if (filter.accepts(changeList)) {
          result.add(changeList);
        }
      }
      return result;
    }
    finally {
      stream.close();
    }
  }

  private static class IndexEntry {
    long number;
    long date;
    long offset;
  }

  private static final IndexEntry[] NO_ENTRIES = new IndexEntry[0];
}
