package com.intellij.util.text;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Dec 16, 2006
 * Time: 7:31:01 PM
 * To change this template use File | Settings | File Templates.
 */
public interface CharSequenceBackedByArray extends CharSequence {
  char[] getChars();
  void getChars(char[] dst, int dstOffset);
}
