/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

public abstract class MappedBufferWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.MappedBufferWrapper");

  @NonNls public static final String BBU_TEMP_FILE_NAME = "BBU";
  @NonNls public static final String CLEANER_METHOD = "cleaner";
  @NonNls public static final String CLEAN_METHOD = "clean";
  @NonNls public static final String VIEWED_BUFFER_FIELD = "viewedBuffer";
  @NonNls public static final String FINALIZE_METHOD = "finalize";

  protected File myFile;
  protected long myPosition;
  protected long myLength;
  private SoftReference<ByteBuffer> myBufferRef;

  public MappedBufferWrapper(final File file, final long pos, final long length) {
    myFile = file;
    myPosition = pos;
    myLength = length;
  }

  protected abstract MappedByteBuffer map();

  public final void unmap() {
    if (!unmapMappedByteBuffer142b19(this)) {
      unmapMappedByteBuffer141(this);
    }
  }

  public ByteBuffer buf() {
    ByteBuffer buffer = _buf();
    if (buffer == null) {
      buffer = map();
      myBufferRef = new SoftReference<ByteBuffer>(buffer);
    }

    return buffer;
  }

  @Nullable
  private ByteBuffer _buf() {
    return myBufferRef != null ? myBufferRef.get() : null;
  }


  private static void unmapMappedByteBuffer141(MappedBufferWrapper holder) {
    ByteBuffer buffer = holder._buf();

    unmapBuffer(buffer);
    holder.myBufferRef = null;

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
    if (clean(holder._buf())) {
      holder.myBufferRef = null;
      return true;
    }

    return false;
  }

  public static boolean clean(final Object buffer) {
    return AccessController.doPrivileged(new PrivilegedAction<Object>() {
      public Object run() {
        try {
          Method getCleanerMethod = buffer.getClass().getMethod(CLEANER_METHOD, ArrayUtil.EMPTY_CLASS_ARRAY);
          getCleanerMethod.setAccessible(true);
          Object cleaner = getCleanerMethod.invoke(buffer, ArrayUtil.EMPTY_OBJECT_ARRAY); // cleaner is actually of sun.misc.Cleaner
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

  public boolean isMapped() {
    return _buf() != null;
  }

  public void flush() {
    final ByteBuffer buffer = _buf();
    if (buffer != null) {
      if (buffer instanceof MappedByteBuffer) {
        final MappedByteBuffer mappedByteBuffer = (MappedByteBuffer)buffer;
        mappedByteBuffer.force();
      }
    }
  }
}