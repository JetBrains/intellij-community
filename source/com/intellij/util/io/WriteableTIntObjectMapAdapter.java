package com.intellij.util.io;

import gnu.trove.TIntObjectHashMap;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Dmitry.Shtukenberg
 * Date: Apr 28, 2004
 * Time: 4:36:27 PM
 * To change this template use File | Settings | File Templates.
 */
public class WriteableTIntObjectMapAdapter implements WriteableMap<Integer,Object> {
  private TIntObjectHashMap hashmap;
  private int[] hashkeys;

  public WriteableTIntObjectMapAdapter(TIntObjectHashMap map) {
    hashmap = map;
  }

  public int[] getHashCodesArray() {
    return hashkeys = hashmap.keys();
  }

  public Object getValue( int pos ) {
    return hashmap.get( hashkeys[pos] );
  }

  public int getKeyLength( int pos ) {
    return 4;
  }

  public void writeKey( DataOutput out, int pos ) throws IOException {
    out.writeInt( hashkeys[pos] );
  }
}
