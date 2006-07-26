package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

class ByteBufferMapWriteHandler<V> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.ByteBufferMapWriteHandler");

  private final ByteBufferMap.ValueProvider<V> myValueProvider;
  private final WriteableMap<V> myMap;

  private final int[] myKeyHashes;
  private final int myMod;
  private DataOutput myOut;

  public ByteBufferMapWriteHandler(DataOutput stream, /*ByteBufferMap.KeyProvider keyProvider, */ByteBufferMap.ValueProvider<V> valueProvider, WriteableMap<V> map, double searchFactor) {
    myValueProvider = valueProvider;
    myMap = map;

    myKeyHashes = myMap.getHashCodesArray();
    int mod = (int)(myKeyHashes.length / searchFactor);
    myMod = mod != 0 ? mod : 1;
    myOut = stream;
  }

  public void execute() throws IOException {

    executeImpl( true );
  }

  public int calcLength() throws IOException {
    return executeImpl( false );
  }

  public int executeImpl( boolean write ) throws IOException {
    if( write ) myOut.writeInt(myMod);
    int offset = 4;

    int[] overflowList = new int[myKeyHashes.length];
    int[] firstOverflowElem = new int[myMod];
    int[] occurs = new int[myMod];
    Arrays.fill(firstOverflowElem, -1);

    // Creating hash table and overflow lists
    for( int i = myKeyHashes.length-1; i >= 0; i-- ) {
      int hashhash = hash(myKeyHashes[i]);
      overflowList[i] = firstOverflowElem[hashhash];
      firstOverflowElem[hashhash] = i;
      occurs[hashhash]++;
    }

    offset += 4 * myMod; // hash table size
    // writing hash table
    for( int i = 0; i < myMod; i++ ) {
      if( write ) myOut.writeInt( occurs[i] != 0 ? offset : -1 );
      if( occurs[i] != 0 ) offset += 4; // key group size, if key group present
      int occurs_i = 0;
      for( int j = firstOverflowElem[i]; j != -1; j = overflowList[j] ) {
        offset += myMap.getKeyLength( j ) + 4 /* value offset */;
        occurs_i++;
      }
      LOG.assertTrue( occurs_i == occurs[i] );
    }

    // writing key table
    for( int i = 0; i < myMod; i++ ) {
      if( occurs[i] == 0 ) continue;

      if( write ) myOut.writeInt( occurs[i] );
      for( int j = firstOverflowElem[i]; j != -1; j = overflowList[j] ) {
        if( write ) {
          myMap.writeKey( myOut, j );
          myOut.writeInt( offset );
        }
        V value = myMap.getValue(j);
        offset += myValueProvider.length(value);
      }
    }

    // writing value table
    for( int i = 0; i < myMod; i++ ) {
      for( int j = firstOverflowElem[i]; j != -1; j = overflowList[j] ) {
        V value = myMap.getValue(j);
        if( write ) myValueProvider.write( myOut, value );
      }
    }

    return offset; // total resulting length
  }

  private int hash(int hashCode){
    return Math.abs(hashCode) % myMod;
  }
}
