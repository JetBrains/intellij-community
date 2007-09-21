/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenChannelsCache { // TODO: Will it make sense to have a background thread, that flushes the cache by timeout?
  private final int myCacheSizeLimit;
  private final String myAccessMode;
  private final Map<File, ChannelDescriptor> myCache;

  public OpenChannelsCache(final int cacheSizeLimit, @NonNls String accessMode) {
    myCacheSizeLimit = cacheSizeLimit;
    myAccessMode = accessMode;
    myCache = new LinkedHashMap<File, ChannelDescriptor>(cacheSizeLimit, 0.5f, true);
  }

  public synchronized FileChannel getChannel(File ioFile) throws FileNotFoundException {
    ChannelDescriptor descriptor = myCache.get(ioFile);
    if (descriptor == null) {
      dropOvercache();
      descriptor = new ChannelDescriptor(ioFile, myAccessMode);
      myCache.put(ioFile, descriptor);
    }
    descriptor.lock();
    return descriptor.getChannel();
  }

  private void dropOvercache() {
    int dropCount = myCache.size() - myCacheSizeLimit;

    if (dropCount >= 0) {
      List<File> keysToDrop = new ArrayList<File>();
      for (Map.Entry<File, ChannelDescriptor> entry : myCache.entrySet()) {
        if (dropCount < 0) break;
        if (!entry.getValue().isLocked()) {
          dropCount--;
          keysToDrop.add(entry.getKey());
        }
      }

      for (File file : keysToDrop) {
        closeChannel(file);
      }
    }
  }

  public synchronized void releaseChannel(File ioFile) {
    ChannelDescriptor descriptor = myCache.get(ioFile);
    assert descriptor != null;

    descriptor.unlock();
  }

  public synchronized void closeChannel(File ioFile) {
    final ChannelDescriptor descriptor = myCache.remove(ioFile);

    if (descriptor != null && !descriptor.isLocked()) {
      AntivirusDetector.getInstance().execute(new Runnable() {
        public void run() {
          try {
            descriptor.getChannel().close();
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
  }

  private static class ChannelDescriptor {
    private int lockCount = 0;
    private final FileChannel myChannel;

    @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
    public ChannelDescriptor(File file, String accessMode) throws FileNotFoundException {
      RandomAccessFile raf = new RandomAccessFile(file, accessMode);
      myChannel = raf.getChannel();
    }

    public void lock() {
      lockCount++;
    }

    public void unlock() {
      lockCount--;
    }

    public boolean isLocked() {
      return lockCount != 0;
    }

    public FileChannel getChannel() {
      return myChannel;
    }
  }
}