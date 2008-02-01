/*
 * @author max
 */
package com.intellij.util.containers;

public interface ShareableKey {
  ShareableKey getStableCopy();
}