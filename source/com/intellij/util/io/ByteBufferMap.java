package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;

public class ByteBufferMap {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.ByteBufferMap");

  private final RandomAccessDataInput myBuffer;
  private final int myStartOffset;
  private final KeyProvider myKeyProvider;
  private final ValueProvider myValueProvider;
  private int myMod;
  private final int myEndOffset;

  public static interface KeyProvider {
    int hashCode(Object key);

    void write(DataOutput out, Object key) throws IOException;

    int length(Object key);

    Object get(DataInput in) throws IOException;

    /**
     * Should move the buffer pointer to the key end.
     */
    boolean equals(DataInput in, Object key) throws IOException;
  }

  public static interface ValueProvider {
    void write(DataOutput out, Object value) throws IOException;

    int length(Object value);

    Object get(DataInput in) throws IOException;
  }

  public static void writeMap(DataOutput stream,
                              ValueProvider valueProvider,
                              WriteableMap map,
                              double searchFactor) throws IOException {
    new ByteBufferMapWriteHandler(stream, valueProvider, map, searchFactor).execute();
  }

  public static int calcMapLength(ValueProvider valueProvider,
                                  WriteableMap map,
                                  double searchFactor) throws IOException {
    return new ByteBufferMapWriteHandler(null, valueProvider, map, searchFactor).calcLength();
  }

  public ByteBufferMap(RandomAccessDataInput buffer,
                       int startOffset,
                       int endOffset,
                       KeyProvider keyProvider,
                       ValueProvider valueProvider) {
    LOG.assertTrue(keyProvider != null);
    LOG.assertTrue(valueProvider != null);
    LOG.assertTrue(startOffset < endOffset);

    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myKeyProvider = keyProvider;
    myValueProvider = valueProvider;

    buffer.setPosition(startOffset);
    try {
      myMod = buffer.readInt();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public Object get(Object key) {
    int hash = hash(myKeyProvider.hashCode(key));
    int keyGroupOffset = readKeyGroupOffset(hash);
    if (keyGroupOffset == -1) return null;
    if (!(myStartOffset < keyGroupOffset && keyGroupOffset < myEndOffset)){
      LOG.error("keyGroupOffset = " + keyGroupOffset + " myStartOffset = " + myStartOffset + " myEndOffset = " + myEndOffset);
    }

    try {
      myBuffer.setPosition(keyGroupOffset);
      int keyGroupSize = myBuffer.readInt();
      LOG.assertTrue(keyGroupSize > 0);
      for (int i = 0; i < keyGroupSize; i++) {
        if (myKeyProvider.equals(myBuffer, key)) {
          int valueOffset = myBuffer.readInt();
          LOG.assertTrue(valueOffset > 0);

          myBuffer.setPosition(myStartOffset + valueOffset);
          return myValueProvider.get(myBuffer);
        }
        else {
          myBuffer.readInt(); //read offset;
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    return null;
  }

  public Object[] getKeys(Class keyClass) {
    ArrayList result = new ArrayList();
    getKeys(keyClass, result);
    return result.toArray((Object[])Array.newInstance(keyClass, result.size()));
  }

  public void getKeys(Class keyClass, Collection dst) {
    try {
      myBuffer.setPosition(myStartOffset + 4 /* mod */);

      int firstKeyGroupOffset = -1;
      int lastKeyGroupOffset = -1;
      for (int i = 0; i < myMod; i++) {
        int value = myBuffer.readInt();
        if (value != -1) {
          int offset = value + myStartOffset;
          if (firstKeyGroupOffset == -1) firstKeyGroupOffset = offset;
          lastKeyGroupOffset = offset;
        }
      }
      if (firstKeyGroupOffset == -1) {
        return;
      }
      LOG.assertTrue(firstKeyGroupOffset > myStartOffset);
      LOG.assertTrue(lastKeyGroupOffset > myStartOffset);
      LOG.assertTrue(lastKeyGroupOffset >= firstKeyGroupOffset);

      int firstValueOffset = -1;

      myBuffer.setPosition(firstKeyGroupOffset);
      while (myBuffer.getPosition() <= lastKeyGroupOffset) {
        int groupSize = myBuffer.readInt();
        for (int i = 0; i < groupSize; i++) {
          dst.add(myKeyProvider.get(myBuffer));

          int valueOffset = myBuffer.readInt(); /* value offset */
          if( firstValueOffset == -1 ) firstValueOffset = valueOffset + myStartOffset;
        }
      }
      LOG.assertTrue( myBuffer.getPosition() == firstValueOffset );
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private int readKeyGroupOffset(int hash) {
    myBuffer.setPosition(myStartOffset + 4 /* mod */ + 4 * hash);
    int offset = -1;
    try {
      offset = myBuffer.readInt();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    if (offset == -1) return -1;
    return offset + myStartOffset;
  }

  private int hash(int hashCode) {
    return Math.abs(hashCode) % myMod;
  }
}
