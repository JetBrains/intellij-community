/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"NestedSynchronizedStatement"})
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

  private final Object nonIOlock = new Object(); // locks are ordered. iolock must never be taken with nonIOlock in hands
  private final Object iolock = new Object();

  private final PoolKey keyInstance = new PoolKey(null, -1);
  private final Map<PoolKey, Page> myPages = new HashMap<PoolKey, Page>();

  @NotNull
  public Page alloc(RandomAccessDataFile owner, long offset) {
    Page page = hitCache(owner, offset);
    if (page != null) return page;

    synchronized (iolock) {
      page = hitCache(owner, offset);
      if (page != null) return page; // Double checking locking (in regards to iolock)

      dropLeastUsed();

      offset -= offset % Page.PAGE_SIZE;
      page = new Page(owner, offset);

      synchronized (nonIOlock) {
        myPages.put(new PoolKey(owner, offset), page);
        page.lock();
      }

      return page;
    }
  }

  @Nullable
  private Page hitCache(RandomAccessDataFile owner, long offset) {
    synchronized (nonIOlock) {
      offset -= offset % Page.PAGE_SIZE;
      keyInstance.setup(owner, offset);

      final Page page = myPages.get(keyInstance);
      if (page != null) {
        if (page.isLocked()) return null;

        page.lock();
      }

      return page;
    }
  }

  private void dropLeastUsed() {
    while (true) {
      Page minPage = null;
      synchronized (nonIOlock) {
        if (myPages.size() < PAGES_COUNT) return;
        long minUsageCount = Long.MAX_VALUE;

        for (Page page : myPages.values()) {
          if (!page.isLocked() && page.getPageAccessCount() < minUsageCount) {
            minUsageCount = page.getPageAccessCount();
            minPage = page;
          }
        }
      }

      if (minPage != null) {
        dropPage(minPage);
      }
    }
  }

  private void dropPage(final Page page) {
    synchronized (iolock) {
      synchronized (nonIOlock) {
        if (page.isLocked()) return;
        page.lock();
      }

      page.flush();

      synchronized (nonIOlock) {
        keyInstance.setup(page.getOwner(), page.getOffset());
        myPages.remove(keyInstance);
        page.unlock();
      }
    }
  }

  public void flushPagesInRange(RandomAccessDataFile owner, long start, int length) {
    List<Page> pagesToDrop = new ArrayList<Page>();
    synchronized (nonIOlock) {
      for (Page page : myPages.values()) {
          if (page.intersects(owner, start, length)) {
            pagesToDrop.add(page);
          }
        }
    }

    synchronized (iolock) {
      for (Page page : pagesToDrop) {
        dropPage(page);
      }
    }
  }

  public void flushPages(final RandomAccessDataFile owner) {
    List<Page> pagesToDrop = new ArrayList<Page>();
    synchronized (nonIOlock) {
      for (Page page : myPages.values()) {
        if (page.getOwner() == owner) {
            pagesToDrop.add(page);
          }
        }
    }

    synchronized (iolock) {
      for (Page page : pagesToDrop) {
        dropPage(page);
      }
    }
  }

  public void reclaim(final Page page) {
    synchronized (nonIOlock) {
      page.unlock();
    }
  }
}