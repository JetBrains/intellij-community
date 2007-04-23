package com.intellij.util.io;

import java.io.DataInput;

/**
 * @author max
 */
public interface RandomAccessDataInput extends DataInput {
  void setPosition(int pos);
  int getPosition();
}
