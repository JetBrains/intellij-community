package com.intellij.tokenindex;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class TokenIndexKey {
  private final String myLanguageId;
  private final int myBlockId;

  public TokenIndexKey(@NotNull String languageId, int blockId) {
    myLanguageId = languageId;
    myBlockId = blockId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TokenIndexKey that = (TokenIndexKey)o;

    if (myBlockId != that.myBlockId) return false;
    if (!myLanguageId.equals(that.myLanguageId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myLanguageId.hashCode();
    result = 31 * result + myBlockId;
    return result;
  }

  @Override
  public String toString() {
    return myLanguageId + ": " + myBlockId;
  }

  public String getLanguageId() {
    return myLanguageId;
  }

  public int getBlockId() {
    return myBlockId;
  }
}
