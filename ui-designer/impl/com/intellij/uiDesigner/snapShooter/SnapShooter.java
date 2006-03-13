/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import javax.swing.*;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.Toolkit;
import java.awt.AWTEvent;

/**
 * @author yole
 */
public class SnapShooter {
  public static void main(String[] args) throws Throwable {
    final JFrame snapShotFrame = new JFrame("SnapShooter");
    JButton takeSnapShotButton = new JButton("Take snapshot (PrintScreen)");
    takeSnapShotButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        SnapShooterDialog dlg = new SnapShooterDialog();
        dlg.showDialog(snapShotFrame);
      }
    });

    Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
      public void eventDispatched(AWTEvent event) {
        if (event instanceof KeyEvent) {
          KeyEvent keyEvent = (KeyEvent) event;
          if (keyEvent.getID() == KeyEvent.KEY_RELEASED && keyEvent.getKeyCode() == KeyEvent.VK_PRINTSCREEN) {
            SnapShooterDialog dlg = new SnapShooterDialog();
            dlg.showDialog(snapShotFrame);
          }
        }
      }
    }, AWTEvent.KEY_EVENT_MASK);


    snapShotFrame.setContentPane(takeSnapShotButton);
    snapShotFrame.pack();
    snapShotFrame.setVisible(true);

    String mainClass = args[0];
    String[] parms = new String[args.length - 1];
    for (int j = 1; j < args.length; j++) {
      parms[j - 1] = args[j];
    }
    Method m = Class.forName(mainClass).getMethod("main", new Class[]{parms.getClass()});
    try {
      ensureAccess(m);
      m.invoke(null, new Object[]{parms});
    } catch (InvocationTargetException ite) {
      throw ite.getTargetException();
    }
  }

  private static void ensureAccess(Object reflectionObject) {
    // need to call setAccessible here in order to be able to launch package-local classes
    // calling setAccessible() via reflection because the method is missing from java version 1.1.x
    final Class aClass = reflectionObject.getClass();
    try {
      final Method setAccessibleMethod = aClass.getMethod("setAccessible", new Class[] {boolean.class});
      setAccessibleMethod.invoke(reflectionObject, new Object[] {Boolean.TRUE});
    }
    catch (Exception e) {
      // the method not found
    }
  }
}
