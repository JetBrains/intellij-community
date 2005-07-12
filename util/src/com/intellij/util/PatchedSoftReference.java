/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.reference.SoftReference;

import java.lang.ref.ReferenceQueue;

public class PatchedSoftReference<T> extends SoftReference<T> {
  public PatchedSoftReference(final T referent) {
    super(referent);
  }

  public PatchedSoftReference(final T referent, final ReferenceQueue<? super T> q) {
    super(referent, q);
  }
}
