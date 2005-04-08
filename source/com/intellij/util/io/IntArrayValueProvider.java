package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class IntArrayValueProvider implements ByteBufferMap.ValueProvider<int[]> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.IntArrayValueProvider");
  public static final IntArrayValueProvider INSTANCE = new IntArrayValueProvider(-1);

  private final int myArraySize;

  public IntArrayValueProvider(int arraySize) {
    myArraySize = arraySize;
  }

  public void write(DataOutput out, int[] value) throws IOException {
    //if (value instanceof IntArrayList) {
    //  IntArrayList list = (IntArrayList) value;
    //  LOG.assertTrue(myArraySize == -1 || list.size() == myArraySize);
    //  if (myArraySize == -1) out.writeInt(list.size());
    //  for (int i = 0; i < list.size(); i++) {
    //    out.writeInt(list.get(i));
    //  }
    //} else if (value instanceof TIntArrayList) {
    //  TIntArrayList list = (TIntArrayList) value;
    //  LOG.assertTrue(myArraySize == -1 || list.size() == myArraySize);
    //  if (myArraySize == -1) out.writeInt(list.size());
    //  for (int i = 0; i < list.size(); i++) {
    //    out.writeInt(list.get(i));
    //  }
    //} else {
      int[] array = (int[])value;
      LOG.assertTrue(myArraySize == -1 || array.length == myArraySize);
      if (myArraySize == -1) out.writeInt(array.length);
      for(int i = 0; i < array.length; i++){
        out.writeInt(array[i]);
      }
    //}
  }

  public int length(int[] value) {
    //if (value instanceof IntArrayList) {
    //  IntArrayList list = (IntArrayList) value;
    //  LOG.assertTrue(myArraySize == -1 || list.size() == myArraySize);
    //
    //  if (myArraySize == -1) return 4 * (list.size() + 1);
    //
    //  return 4 * myArraySize;
    //} else if (value instanceof TIntArrayList) {
    //  TIntArrayList list = (TIntArrayList) value;
    //  LOG.assertTrue(myArraySize == -1 || list.size() == myArraySize);
    //
    //  if (myArraySize == -1) return 4 * (list.size() + 1);
    //
    //  return 4 * myArraySize;
    //} else {
      int[] array = (int[])value;
      LOG.assertTrue(myArraySize == -1 || array.length == myArraySize);

      if (myArraySize == -1) return 4 * (array.length + 1);

      return 4 * myArraySize;
    //}
  }

  public int[] get(DataInput in) throws IOException {
    final int[] result;

    if (myArraySize >= 0) {
      result = new int[myArraySize];
    } else {
      result = new int[in.readInt()];
    }

    for(int i = 0; i < result.length; i++){
      result[i] = in.readInt();
    }
    return result;
  }
}
