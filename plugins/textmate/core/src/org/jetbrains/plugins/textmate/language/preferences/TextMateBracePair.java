package org.jetbrains.plugins.textmate.language.preferences;

public class TextMateBracePair {
  public final char leftChar;
  public final char rightChar;

  public TextMateBracePair(char leftChar, char rightChar) {
    this.leftChar = leftChar;
    this.rightChar = rightChar;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TextMateBracePair pair = (TextMateBracePair)o;

    if (leftChar != pair.leftChar) return false;
    if (rightChar != pair.rightChar) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return 31 * (int)leftChar + (int)rightChar;
  }

  @Override
  public String toString() {
    return "TextMateBracePair{" +
           "startComment=" + leftChar +
           ", endComment=" + rightChar +
           '}';
  }
}
