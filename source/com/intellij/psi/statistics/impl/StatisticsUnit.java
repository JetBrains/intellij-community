
package com.intellij.psi.statistics.impl;

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

  private TObjectIntHashMap myDataMap = new TObjectIntHashMap();

  public StatisticsUnit(int number) {
    myNumber = number;
  }

  public int getData(String key1, String key2) {
    return myDataMap.get(new MyDataKey(key1, key2));
  }

  public void putData(String key1, String key2, int data) {
    MyDataKey key = new MyDataKey(key1, key2);
    myDataMap.put(key, data);
  }

  public String[] getKeys2(final String key1){
    final HashSet keys = new HashSet();
    myDataMap.forEachKey(
      new TObjectProcedure() {
        public boolean execute(Object object) {
          MyDataKey dataKey = (MyDataKey)object;
          if (dataKey.key1.equals(key1)){
            keys.add(dataKey.key2);
          }
          return true;
        }
      }
    );
    return (String[])keys.toArray(new String[keys.size()]);
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
      new TObjectIntProcedure() {
        public boolean execute(Object key, int value) {
          try{
            dataOut.writeUTF(((MyDataKey)key).key1);
            dataOut.writeUTF(((MyDataKey)key).key2);
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
      myDataMap.put(new MyDataKey(key1, key2), value);
    }
  }
}