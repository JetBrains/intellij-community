package com.intellij.util.io;

import java.io.DataOutput;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * @author max
 */
public interface WriteableMap<V> {
//Object[] keySet();
//V get(K key);
  int[] getHashCodesArray(); // Returns array of all key hash codes in the map
  V getValue( int pos );
  void writeKey( DataOutput stream, int pos ) throws IOException;
  int getKeyLength( int pos ) throws UnsupportedEncodingException;
}
