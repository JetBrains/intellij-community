/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui;

import com.intellij.util.WeakListener;

import javax.swing.*;
import java.awt.event.MouseMotionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2004
 */
public class WeakMouseMotionListener extends WeakListener<JComponent, MouseMotionListener> {
  public WeakMouseMotionListener(JComponent source, MouseMotionListener listenerImpl) {
    super(source, MouseMotionListener.class, listenerImpl);
  }
  public void addListener(JComponent source, MouseMotionListener listener) {
    source.addMouseMotionListener(listener);
  }
  public void removeListener(JComponent source, MouseMotionListener listener) {
    source.removeMouseMotionListener(listener);
  }
}
