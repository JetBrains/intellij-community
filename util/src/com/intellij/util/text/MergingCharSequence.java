package com.intellij.util.text;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 6, 2005
 * Time: 11:32:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class MergingCharSequence implements CharSequence {
  private CharSequence s1;
  private CharSequence s2;

  public MergingCharSequence(final CharSequence s1, final CharSequence s2) {
    this.s1 = s1;
    this.s2 = s2;
  }

  public int length() {
    return s1.length() + s2.length();
  }

  public char charAt(int index) {
    if (index < s1.length()) return s1.charAt(index);
    return s2.charAt(index - s1.length());
  }

  public CharSequence subSequence(int start, int end) {
    if (start < s1.length() && end < s1.length()) return s1.subSequence(start, end);
    if (start >= s1.length() && end >= s1.length()) return s2.subSequence(start - s1.length(), end - s1.length());
    return new MergingCharSequence(s1.subSequence(start, s1.length()), s2.subSequence(0, end - s1.length()));
  }

  public String toString() {
    return s1.toString() + s2.toString();
  }
}
