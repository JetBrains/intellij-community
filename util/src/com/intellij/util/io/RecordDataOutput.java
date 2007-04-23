package com.intellij.util.io;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author max
 */ 
public interface RecordDataOutput extends DataOutput {
  int getRecordId();
  void close() throws IOException;
}
