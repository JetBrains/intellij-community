/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimedComputable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

public abstract class MappedBufferWrapper extends TimedComputable<ByteBuffer> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.MappedBufferWrapper");

  @NonNls public static final String BBU_TEMP_FILE_NAME = "BBU";
  @NonNls public static final String CLEANER_METHOD = "cleaner";
  @NonNls public static final String CLEAN_METHOD = "clean";
  @NonNls public static final String VIEWED_BUFFER_FIELD = "viewedBuffer";
  @NonNls public static final String FINALIZE_METHOD = "finalize";

  protected File myFile;
  protected long myPosition;
  protected long myLength;

  private static int totalSize = 0;

  public MappedBufferWrapper(final File file, final long pos, final long length) {
    super(null);

    myFile = file;
    myPosition = pos;
    myLength = length;
  }

  protected abstract MappedByteBuffer map();

  public final void unmap() {
    totalSize -= myLength;

    /* TODO: not sure we need this. Everything seem to be forced, when native cleaner winishes its work.
    final ByteBuffer buffer = getIfCached();
    if (buffer instanceof MappedByteBuffer) {
      ((MappedByteBuffer)buffer).force();
    }
    */

    if (!unmapMappedByteBuffer142b19(this)) {
      unmapMappedByteBuffer141(this);
    }
  }

  /*
   * An assumption made here that any retreiver of the buffer will not use it for time longer than 60 seconds.
   */
  public ByteBuffer buf() {
    final ByteBuffer buf = acquire(); // hack, makes buffer live for 120sec without disposing. TODO: make disposing explicit
    acquire();
    release();
    release();
    return buf;
  }

  @NotNull
  protected ByteBuffer calc() {
    totalSize += myLength;
    /*
    System.out.println("mapped total: " + StringUtil.formatFileSize(totalSize));
    */
    return map();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MappedBufferWrapper that = (MappedBufferWrapper)o;

    /*
    if (myLength != that.myLength) return false;
    if (myPosition != that.myPosition) return false;
    */
    if (!myFile.equals(that.myFile)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myFile.hashCode();
    result = 31 * result + (int)(myPosition ^ (myPosition >>> 32));
    result = 31 * result + (int)(myLength ^ (myLength >>> 32));
    return result;
  }

  private static void unmapMappedByteBuffer141(MappedBufferWrapper holder) {
    ByteBuffer buffer = holder.getIfCached();

    unmapBuffer(buffer);

    boolean needGC = SystemInfo.JAVA_VERSION.startsWith("1.4.0");

    if (!needGC) {
      try {
        File newFile = File.createTempFile(BBU_TEMP_FILE_NAME, "", holder.myFile.getParentFile());
        newFile.delete();
        if (!holder.myFile.renameTo(newFile)) {
          needGC = true;
        }
        else {
          newFile.renameTo(holder.myFile);
        }
      }
      catch (IOException e) {
        needGC = true;
      }
    }

    if (needGC) {
      System.gc();
      System.runFinalization();
    }
  }

  private static boolean unmapMappedByteBuffer142b19(MappedBufferWrapper holder) {
    if (clean(holder.getIfCached())) {
      return true;
    }

    return false;
  }

  public static boolean clean(final Object buffer) {
    if (buffer == null) return true;
    return AccessController.doPrivileged(new PrivilegedAction<Object>() {
      public Object run() {
        try {
          Method getCleanerMethod = buffer.getClass().getMethod(CLEANER_METHOD, ArrayUtil.EMPTY_CLASS_ARRAY);
          getCleanerMethod.setAccessible(true);
          Object cleaner = getCleanerMethod.invoke(buffer, ArrayUtil.EMPTY_OBJECT_ARRAY); // cleaner is actually of sun.misc.Cleaner
          if (cleaner == null) return null; // Already cleaned
          
          Class cleanerClass = Class.forName("sun.misc.Cleaner");
          Method cleanMethod = cleanerClass.getMethod(CLEAN_METHOD, ArrayUtil.EMPTY_CLASS_ARRAY);
          cleanMethod.invoke(cleaner, ArrayUtil.EMPTY_OBJECT_ARRAY);
        }
        catch (Exception e) {
          return buffer;
        }
        return null;
      }
    }) == null;
  }

  private static void unmapBuffer(ByteBuffer buffer) {
    try {
      Field field = Class.forName("java.nio.DirectByteBuffer").getDeclaredField(VIEWED_BUFFER_FIELD);
      field.setAccessible(true);
      if (field.get(buffer) instanceof MappedByteBuffer) {
        unmapBuffer((MappedByteBuffer)field.get(buffer));
        return;
      }


      Method finalizeMethod = Object.class.getDeclaredMethod(FINALIZE_METHOD, ArrayUtil.EMPTY_CLASS_ARRAY);
      finalizeMethod.setAccessible(true);
      finalizeMethod.invoke(buffer, ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public synchronized boolean isMapped() {
    return getIfCached() != null;
  }

  public synchronized void flush() {
    final ByteBuffer buffer = getIfCached();
    if (buffer != null) {
      if (buffer instanceof MappedByteBuffer) {
        final MappedByteBuffer mappedByteBuffer = (MappedByteBuffer)buffer;
        mappedByteBuffer.force();
      }
    }
  }

  public synchronized void dispose() {
    unmap();
    super.dispose();
  }
}
