
package com.intellij.psi.statistics.impl;

import com.intellij.util.containers.StringInterner;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import gnu.trove.TObjectProcedure;

import java.io.*;
import java.util.HashSet;

class StatisticsUnit {
  private static final int FORMAT_VERSION_NUMBER = 4;

  private static final class MyDataKey {
    public final String key1;
    public final String key2;

    public MyDataKey(String key1, String key2) {
      this.key1 = key1;
      this.key2 = key2;
    }

    public boolean equals(Object o) {
      if (!(o instanceof MyDataKey)) return false;
      MyDataKey key = (MyDataKey)o;
      if (!key1.equals(key.key1)) return false;
      if (!key2.equals(key.key2)) return false;
      return true;
    }

    public int hashCode() {
      return key1.hashCode() + key2.hashCode();
    }
  }

  private final int myNumber;
  private final StringInterner myKeys;

  private TObjectIntHashMap<MyDataKey> myDataMap = new TObjectIntHashMap<MyDataKey>();

  public StatisticsUnit(int number, StringInterner keys) {
    myNumber = number;
    myKeys = keys;
  }

  public int getData(String key1, String key2) {
    return myDataMap.get(createKey(key1, key2));
  }

  private MyDataKey createKey(final String key1, final String key2) {
    return new MyDataKey(myKeys.intern(key1), myKeys.intern(key2));
  }

  public void putData(String key1, String key2, int data) {
    myDataMap.put(createKey(key1, key2), data);
  }

  public String[] getKeys2(final String key1){
    final HashSet<String> keys = new HashSet<String>();
    myDataMap.forEachKey(
      new TObjectProcedure<MyDataKey>() {
        public boolean execute(MyDataKey object) {
          if (object.key1.equals(key1)){
            keys.add(object.key2);
          }
          return true;
        }
      }
    );
    return keys.toArray(new String[keys.size()]);
  }

  public int getNumber() {
    return myNumber;
  }

  public void write(OutputStream out) throws IOException{
    final DataOutputStream dataOut = new DataOutputStream(out);
    dataOut.writeInt(FORMAT_VERSION_NUMBER);

    dataOut.writeInt(myDataMap.size());

    final IOException[] ex = new IOException[1];
    myDataMap.forEachEntry(
      new TObjectIntProcedure<MyDataKey>() {
        public boolean execute(MyDataKey key, int value) {
          try{
            dataOut.writeUTF(key.key1);
            dataOut.writeUTF(key.key2);
            dataOut.writeInt(value);
            return true;
          }
          catch(IOException e){
            ex[0] = e;
            return false;
          }
        }
      }
    );

    if (ex[0] != null){
      throw ex[0];
    }
  }

  public void read(InputStream in) throws IOException, WrongFormatException {
    DataInputStream dataIn = new DataInputStream(in);
    int formatVersion = dataIn.readInt();
    if (formatVersion != FORMAT_VERSION_NUMBER){
      throw new WrongFormatException();
    }

    myDataMap.clear();
    int size = dataIn.readInt();
    for(int i = 0; i < size; i++){
      String key1 = dataIn.readUTF();
      String key2 = dataIn.readUTF();
      int value = dataIn.readInt();
      myDataMap.put(createKey(key1, key2), value);
    }
  }
}