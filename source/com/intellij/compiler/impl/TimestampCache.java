/*
 * @author: Eugene Zhuravlev
 * Date: Apr 1, 2003
 * Time: 1:53:00 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class TimestampCache extends StateCache <Long> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.TimestampCache");
  public TimestampCache(String storeDirectory, String idPrefix) {
    super(storeDirectory + File.separator + idPrefix + "_timestamp.dat");
  }

  public void update(String url, Long state) {
    LOG.assertTrue(state != null);
    super.update(url, state);
  }

  public Long read(DataInputStream stream) throws IOException {
    return new Long(stream.readLong());
  }

  public void write(Long aLong, DataOutputStream stream) throws IOException {
    stream.writeLong(aLong.longValue());
  }
}
