/*
 * @author max
 */
package com.intellij.openapi;

public interface Forceable {
  boolean isDirty();
  void force();
}