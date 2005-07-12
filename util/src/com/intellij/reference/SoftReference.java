package com.intellij.reference;

import java.lang.ref.ReferenceQueue;

/**
 * The class is necessary to debug memory allocations via soft references. All IDEA classes should use this SoftReference
 * instead of original from java.lang.ref. Whenever we suspect soft memory allocation overhead this easily becomes a hard
 * reference so we can see allocations and memory consumption in memory profiler.
 * @author max
 */
public class SoftReference<T> extends java.lang.ref.SoftReference<T> {
//  T myReferent;
  public SoftReference(final T referent) {
    super(referent);
//    myReferent = referent;
  }

  public SoftReference(final T referent, final ReferenceQueue<? super T> q) {
    super(referent, q);
//    myReferent = referent;
  }

  /*
  @Override
  public T get() {
    return myReferent;
  }
  */
}
