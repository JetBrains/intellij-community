package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.TObjectIntHashMap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class FileKeyProvider implements ByteBufferMap.KeyProvider{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.FileKeyProvider");

  private final VirtualFile[] myFileIndex;
  private final TObjectIntHashMap myFileToIndexMap;

  public FileKeyProvider(VirtualFile[] fileIndex, TObjectIntHashMap fileToIndexMap) {
    myFileIndex = fileIndex;
    myFileToIndexMap = fileToIndexMap;
  }

  public int hashCode(Object key) {
    VirtualFile file = (VirtualFile)key;
    int index = myFileToIndexMap.get(file) - 1;
    return index;
  }

  public void write(DataOutput out, Object key) throws IOException {
    VirtualFile file = (VirtualFile)key;
    int index = myFileToIndexMap.get(file) - 1;
    LOG.assertTrue(index >= 0);
    out.writeInt(index);
  }

  public int length(Object key) {
    return 4;
  }

  public Object get(DataInput in) throws IOException {
    int index = in.readInt();
    return myFileIndex[index];
  }

  public boolean equals(DataInput in, Object key) throws IOException {
    int index = in.readInt();
    return key.equals(myFileIndex[index]);
  }
}
