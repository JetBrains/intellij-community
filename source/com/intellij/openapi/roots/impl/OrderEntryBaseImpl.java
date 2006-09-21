package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderEntry;

public abstract class OrderEntryBaseImpl extends RootModelComponentBase implements OrderEntry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.OrderEntryVeryBaseImpl");

  private int myIndex;

  //simulate System.identityHashCode()
  private static int ourInstanceCounter = 0;
  private final int myInstanceCreationIndex;

  protected OrderEntryBaseImpl(RootModelImpl rootModel) {
    super(rootModel);
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    myInstanceCreationIndex = ourInstanceCounter++;
  }

  public int hashCode() {
    return myInstanceCreationIndex;
  }

  public void setIndex(int index) { myIndex = index; }

  public int compareTo(OrderEntry orderEntry) {
    LOG.assertTrue(orderEntry.getOwnerModule() == getOwnerModule());
    return myIndex - ((OrderEntryBaseImpl)orderEntry).myIndex;
  }

  boolean sameType(OrderEntry that) {
    return getClass().equals(that.getClass());
  }
}
