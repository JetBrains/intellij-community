/*
 * @author max
 */
package com.intellij.util.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PagePool {
  private final static int PAGES_COUNT = 50;

  private static class PoolKey {
    private RandomAccessDataFile owner;
    private long offset;

    private PoolKey(final RandomAccessDataFile owner, final long offset) {
      this.owner = owner;
      this.offset = offset;
    }

    public int hashCode() {
      return (int)(owner.hashCode() * 31 + offset);
    }

    @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
    public boolean equals(final Object obj) {
      PoolKey k = (PoolKey)obj;
      return k.owner == owner && k.offset == offset;
    }

    public void setup(RandomAccessDataFile owner, long offset) {
      this.owner = owner;
      this.offset = offset;
    }
  }

  private final Object lock = new Object();
  private final PoolKey keyInstance = new PoolKey(null, -1);
  private final Map<PoolKey, Page> myPages = new HashMap<PoolKey, Page>();
  public Page alloc(RandomAccessDataFile owner, long offset) {
    synchronized (lock) {
      offset -= offset % Page.PAGE_SIZE;
      keyInstance.setup(owner, offset);
      Page page = myPages.get(keyInstance);
      if (page == null) {
        page = new Page(owner, offset);
        if (myPages.size() + 1 > PAGES_COUNT) {
          dropLeastUsed();
        }

        myPages.put(new PoolKey(owner, offset), page);
      }
      page.lock();

      return page;
    }
  }

  private void dropLeastUsed() {
    Page minPage = null;
    long minUsageCount = Long.MAX_VALUE;

    for (Page page : myPages.values()) {
      if (!page.isLocked() && page.getPageAccessCount() < minUsageCount) {
        minUsageCount = page.getPageAccessCount();
        minPage = page;
      }
    }

    if (minPage != null) {
      dropPage(minPage);
    }
  }

  private void dropPage(final Page page) {
    page.flush();
    myPages.remove(new PoolKey(page.getOwner(), page.getOffset()));
  }

  public void flushPagesInRange(RandomAccessDataFile owner, long start, int length) {
    synchronized (lock) {
      final Collection<Page> pages = new ArrayList<Page>(myPages.values());
      for (Page page : pages) {
        if (page.intersects(owner, start, length)) {
          dropPage(page);
        }
      }
    }
  }

  public void flushPages(final RandomAccessDataFile owner) {
    synchronized (lock) {
      final Collection<Page> pages = new ArrayList<Page>(myPages.values());
      for (Page page : pages) {
        if (page.getOwner() == owner) {
          dropPage(page);
        }
      }
    }
  }

  public void reclaim(final Page page) {
    synchronized (lock) {
      page.unlock();
    }
  }
}