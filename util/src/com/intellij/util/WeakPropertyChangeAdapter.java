/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;

/**
 *
 * @version 1.0
 */
public class WeakPropertyChangeAdapter
  implements PropertyChangeListener
{
  private WeakReference myRef;

  public WeakPropertyChangeAdapter(PropertyChangeListener l) {
    myRef = new WeakReference(l);
  }

  public void propertyChange(PropertyChangeEvent e) {
    PropertyChangeListener l = (PropertyChangeListener)myRef.get();
    if (l != null) {
      l.propertyChange(e);
    }
  }
}
