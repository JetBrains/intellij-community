/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.text;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class CharArrayUtil {

  public static void getChars(CharSequence src, char[] dst, int dstOffset) {
    if (src instanceof CharArrayCharSequence) {
      ((CharArrayCharSequence)src).getChars(dst, dstOffset);
    }
    else if (src instanceof StringBuffer) {
      ((StringBuffer)src).getChars(0, src.length(), dst, dstOffset);
    }
    else if (src instanceof String) {
      ((String)src).getChars(0, src.length(), dst, dstOffset);
    }
    else {
      for (int i = 0; i < src.length(); i++) {
        dst[i + dstOffset] = src.charAt(i);
      }
    }
  }

  public static char[] fromSequence(CharSequence seq) {
    if (seq instanceof CharArrayCharSequence) {
      return ((CharArrayCharSequence)seq).getChars();
    }
    if (seq instanceof StringBuffer) {
      char[] chars = new char[seq.length()];
      ((StringBuffer)seq).getChars(0, seq.length(), chars, 0);
      return chars;
    }
    if (seq instanceof String) {
      char[] chars = new char[seq.length()];
      ((String)seq).getChars(0, seq.length(), chars, 0);
      return chars;
    }
    return seq.toString().toCharArray();
  }

  public static int shiftForward(CharSequence buffer, int offset, String chars) {
    while(true){
      if (offset >= buffer.length()) break;
      char c = buffer.charAt(offset);
      int i;
      for(i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i == chars.length()) break;
      offset++;
    }
    return offset;
  }

  public static int shiftForward(char[] buffer, int offset, String chars){
    return shiftForward(new CharArrayCharSequence(buffer), offset, chars);
  }

  public static int shiftBackward(CharSequence buffer, int offset, String chars) {
    while(true){
      if (offset < 0) break;
      char c = buffer.charAt(offset);
      int i;
      for(i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i == chars.length()) break;
      offset--;
    }
    return offset;
  }

  public static int shiftBackward(char[] buffer, int offset, String chars){
    return shiftBackward(new CharArrayCharSequence(buffer), offset, chars);
  }

  public static int shiftForwardUntil(char[] buffer, int offset, String chars){
    return shiftForwardUntil(new CharArrayCharSequence(buffer), offset, chars);
  }

  public static int shiftForwardUntil(CharSequence buffer, int offset, String chars){
    while(true){
      if (offset >= buffer.length()) break;
      char c = buffer.charAt(offset);
      int i;
      for(i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i < chars.length()) break;
      offset++;
    }
    return offset;
  }

  public static int shiftBackwardUntil(char[] buffer, int offset, String chars){
    return shiftBackwardUntil(new CharArrayCharSequence(buffer), offset, chars);
  }

  public static int shiftBackwardUntil(CharSequence buffer, int offset, String chars){
    while(true){
      if (offset < 0) break;
      char c = buffer.charAt(offset);
      int i;
      for(i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i < chars.length()) break;
      offset--;
    }
    return offset;
  }

  public static boolean regionMatches(CharSequence buffer, int offset, String s) {
    if (offset + s.length() > buffer.length()) return false;
    if (offset < 0) return false;
    for(int i = 0; i < s.length(); i++){
      if (buffer.charAt(offset + i) != s.charAt(i)) return false;
    }
    return true;
  }

  public static int indexOf(char[] buffer, String pattern, int fromIndex) {
    char[] chars = pattern.toCharArray();
    int limit = buffer.length - chars.length;
    if (fromIndex < 0){
      fromIndex = 0;
    }
  SearchLoop:
    for(int i = fromIndex; i < limit; i++){
      for(int j = 0; j < chars.length; j++){
        if (chars[j] != buffer[i + j]) continue SearchLoop;
      }
      return i;
    }
    return -1;
  }

  public static int lastIndexOf(CharSequence buffer, String pattern, int fromIndex) {
    char[] chars = pattern.toCharArray();
    int end = buffer.length() - chars.length;
    if (fromIndex > end){
      fromIndex = end;
    }
  SearchLoop:
    for(int i = fromIndex; i >= 0; i--){
      for(int j = 0; j < chars.length; j++){
        if (chars[j] != buffer.charAt(i + j)) continue SearchLoop;
      }
      return i;
    }
    return -1;
  }

  public static int lastIndexOf(char[] buffer, String pattern, int fromIndex) {
    return lastIndexOf(new CharArrayCharSequence(buffer), pattern, fromIndex);
  }

  public static byte[] toByteArray(char[] chars) throws IOException {
    return toByteArray(chars, chars.length);
  }

  public static byte[] toByteArray(char[] chars, int size) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    OutputStreamWriter writer = new OutputStreamWriter(out);
    writer.write(chars, 0, size);
    writer.close();
    return out.toByteArray();
  }
}