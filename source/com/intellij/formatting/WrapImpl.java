package com.intellij.formatting;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class WrapImpl extends Wrap {
  private LeafBlockWrapper myFirstEntry = null;
  private int myFirstPosition = -1;
  private boolean myIsActive = false;
  private final boolean myWrapFirstElement;
  private final long myId;
  private static long ourId = 0;

  private final Set<WrapImpl> myParents = new HashSet<WrapImpl>();
  private boolean myIgnoreParentWraps = false;
  private Collection<WrapImpl> myIgnoredWraps;

  public boolean isChildOf(final WrapImpl wrap) {
    if (myIgnoreParentWraps) return false;
    if (myIgnoredWraps != null && myIgnoredWraps.contains(wrap)) return false;
    for (WrapImpl parent : myParents) {
      if (parent == wrap) return true;
      if (parent.isChildOf(wrap)) return true;
    }
    return false;
  }

  void registerParent(WrapImpl parent) {
    if (parent == this) return;
    if (parent == null) return;
    if (parent.isChildOf(this)) return;
    myParents.add(parent);  }

  public void reset() {
    myFirstEntry = null;
    myFirstPosition = -1;
    myIsActive = false;
  }

  public boolean getIgnoreParentWraps() {
    return myIgnoreParentWraps;
  }

  public void ignoreParentWrap(final WrapImpl wrap) {
    if (myIgnoredWraps == null) {
      myIgnoredWraps = new HashSet<WrapImpl>();
    }
    myIgnoredWraps.add(wrap);
  }

  static class Type{
    private final String myName;

    private Type(final String name) {
      myName = name;
    }

    public static final Type DO_NOT_WRAP = new Type("NONE");
    public static final Type WRAP_AS_NEEDED = new Type("NORMAL");
    public static final Type CHOP_IF_NEEDED = new Type("CHOP");
    public static final Type WRAP_ALWAYS = new Type("ALWAYS");

    public String toString() {
      return myName;
    }
  }

  LeafBlockWrapper getFirstEntry() {
    return myFirstEntry;
  }

  void markAsUsed() {
    myFirstEntry = null;
    myIsActive = true;
  }

  void processNextEntry(final int startOffset) {
    if (myFirstPosition < 0) {
      myFirstPosition = startOffset;
    }
  }

  int getFirstPosition() {
    return myFirstPosition;
  }

  private final Type myType;

  public WrapImpl(WrapType type, boolean wrapFirstElement) {
    switch(type) {
        case NORMAL: myType = Type.WRAP_AS_NEEDED;break;
        case NONE: myType= Type.DO_NOT_WRAP;break;
        case ALWAYS: myType = Type.WRAP_ALWAYS; break;
        default: myType = Type.CHOP_IF_NEEDED;
    }

    myWrapFirstElement = wrapFirstElement;
    myId = ourId++;

  }

  Type getType() {
    return myType;
  }

  boolean isWrapFirstElement() {
    return myWrapFirstElement;
  }

  void saveFirstEntry(LeafBlockWrapper current) {
    if (myFirstEntry  == null) {
      myFirstEntry = current;
    }
  }

  boolean isIsActive() {
    return myIsActive;
  }

  public String toString() {
    return myType.toString();
  }

  public String getId() {
    return String.valueOf(myId);
  }

  public void ignoreParentWraps() {
    myIgnoreParentWraps = true;
  }
}
