/*
 * @author: Eugene Zhuravlev
 * Date: Mar 3, 2003
 * Time: 12:34:44 PM
 */
package com.intellij.compiler;

import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class SymbolTable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.SymbolTable");
  private TIntObjectHashMap myIdToSymbolMap = new TIntObjectHashMap(10, 0.9f);
  private TObjectIntHashMap mySymbolToIdMap = new TObjectIntHashMap(10, 0.9f);
  private int myNextAvailableId = 0;
  private long myTimeStamp;
  private int myVersion;
  private boolean myIsDirty = false;

  public SymbolTable() {
    updateTimeStamp();
  }

  public SymbolTable(DataInput input) throws IOException {
    myTimeStamp = input.readLong();
    myVersion = input.readInt();
    myNextAvailableId = input.readInt();
    int size = input.readInt();
    myIdToSymbolMap = new TIntObjectHashMap(size, 0.9f);
    mySymbolToIdMap = new TObjectIntHashMap(size, 0.9f);

    while (size-- > 0) {
      final String symbol = CompilerIOUtil.readString(input);
      final int id = input.readInt();
      myIdToSymbolMap.put(id, symbol);
      mySymbolToIdMap.put(symbol, id);
    }
  }

  public int getVersion() {
    return myVersion;
  }

  public boolean isFull() {
    return (Integer.MAX_VALUE - myNextAvailableId) <= 10000;
  }

  public synchronized boolean isDirty() {
    return myIsDirty;
  }

  public synchronized void save(final DataOutput output) throws IOException {
    output.writeLong(myTimeStamp);
    output.writeInt(CompilerConfiguration.DEPENDENCY_FORMAT_VERSION);
    output.writeInt(myNextAvailableId);
    final int size = mySymbolToIdMap.size();
    output.writeInt(size);
    final SymbolToIdWriteProcedure symbolToIdWriteProcedure = new SymbolToIdWriteProcedure(output);
    mySymbolToIdMap.forEachEntry(symbolToIdWriteProcedure);
    final IOException ex = symbolToIdWriteProcedure.getException();
    if (ex != null) {
      throw ex;
    }
    myIsDirty = false;
  }

  public synchronized int getId(String symbol) {
    if ("".equals(symbol)) {
      return -1;
    }
    LOG.assertTrue(symbol != null);
    final int id;
    if (mySymbolToIdMap.containsKey(symbol)) {
      id = mySymbolToIdMap.get(symbol);
    }
    else {
      id = myNextAvailableId++;
      mySymbolToIdMap.put(symbol, id);
      myIdToSymbolMap.put(id, symbol);
      myIsDirty = true;
    }
    return id;
  }

  public synchronized String getSymbol(int id) {
    if (id == -1) {
      return "";
    }
    if (myIdToSymbolMap.containsKey(id)) {
      return (String)myIdToSymbolMap.get(id);
    }
    return null;
  }

  /*
  public void removeUnusedIds(final TIntHashSet idsToKeep) {
    LOG.info("BEGIN Compacting compiler cache symbol table");
    final int[] keys = myIdToSymbolMap.keys();
    for (int idx = 0; idx < keys.length; idx++) {
      int key = keys[idx];
      if (idsToKeep.contains(key)) {
        continue;
      }
      final Object value = myIdToSymbolMap.get(key);
      mySymbolToIdMap.remove(value);
      myIdToSymbolMap.remove(key);
      myIsDirty = true;
      LOG.info("Removed entry [" + key + "<->" + value + "]");
    }
    myIdToSymbolMap.compact();
    mySymbolToIdMap.compact();
    final int[] maxValue = new int[] {0};
    idsToKeep.forEach(new TIntProcedure() {
      public boolean execute(int value) {
        if (maxValue[0] < value) {
          maxValue[0] = value;
        }
        return true;
      }
    });
    myNextAvailableId = maxValue[0] + 1;
    LOG.info("Next available ID = "+myNextAvailableId);
    LOG.info("END Compacting compiler cache symbol table");
  }
  */

// --Recycle Bin START (7/2/03 2:47 PM):
//  public long getTimeStamp() {
//    return myTimeStamp;
//  }
// --Recycle Bin STOP (7/2/03 2:47 PM)

  public synchronized final void updateTimeStamp() {
    myTimeStamp = System.currentTimeMillis();
  }

  private static class SymbolToIdWriteProcedure implements TObjectIntProcedure {
    private final DataOutput myOutput;
    private IOException myException = null;

    public SymbolToIdWriteProcedure(DataOutput output) {
      myOutput = output;
    }

    public boolean execute(Object a, int b) {
      try {
        CompilerIOUtil.writeString((String)a, myOutput);
        myOutput.writeInt(b);
        return true;
      }
      catch (IOException e) {
        myException = e;
        return false;
      }
    }

    public IOException getException() {
      return myException;
    }
  }
}
