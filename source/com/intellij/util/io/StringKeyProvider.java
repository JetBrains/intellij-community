package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class StringKeyProvider implements ByteBufferMap.KeyProvider{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.StringKeyProvider");

  public static final StringKeyProvider INSTANCE = new StringKeyProvider();

  private StringKeyProvider() {
  }

  public int hashCode(Object key) {
    return key.hashCode();
  }

  public void write(DataOutput out, Object key) throws IOException {
    String keyString = (String)key;
    byte[] keyBytes = keyString.getBytes("UTF-8");
    out.writeInt(keyBytes.length);
    out.write(keyBytes);
  }

  public int length(Object key) {
    try{
      String keyString = (String)key;
      byte[] keyBytes = keyString.getBytes("UTF-8");
      return 4 + keyBytes.length;
    }
    catch(UnsupportedEncodingException e){
      LOG.error(e);
      return 0;
    }
  }

  public Object get(DataInput in) throws IOException {
    int length = in.readInt();
    byte[] bytes = new byte[length];
    in.readFully(bytes);
    try {
      return new String(bytes, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
      return null;
    }
  }

  public boolean equals(DataInput in, Object key) throws IOException {
    try {
      String keyString = (String)key;
      byte[] keyBytes = keyString.getBytes("UTF-8");

      int length = in.readInt();
      byte[] inputBytes = new byte[length];
      in.readFully(inputBytes);
      if (length != keyBytes.length) return false;
      for (int i = 0; i < length; i++) {
        if (keyBytes[i] != inputBytes[i]) return false;
      }

      return true;
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
      return false;
    }
  }
}
