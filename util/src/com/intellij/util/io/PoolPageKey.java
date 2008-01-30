/*
 * @author max
 */
package com.intellij.util.io;

class PoolPageKey implements Comparable<PoolPageKey> {
  private RandomAccessDataFile owner;
  private long offset;

  public PoolPageKey(final RandomAccessDataFile owner, final long offset) {
    this.owner = owner;
    this.offset = offset;
  }

  public int hashCode() {
    return (int)(owner.hashCode() * 31 + offset);
  }

  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  public boolean equals(final Object obj) {
    PoolPageKey k = (PoolPageKey)obj;
    return k.owner == owner && k.offset == offset;
  }

  public void setup(RandomAccessDataFile owner, long offset) {
    this.owner = owner;
    this.offset = offset;
  }

  public int compareTo(final PoolPageKey o) {
    if (owner != o.owner) {
      return owner.hashCode() - o.owner.hashCode();
    }
    return offset == o.offset ? 0 : offset - o.offset < 0 ? -1 : 1;
  }
}