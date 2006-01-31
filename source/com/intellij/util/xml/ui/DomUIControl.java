/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.j2ee.ui.CommittablePanel;
import com.intellij.util.xml.DomElement;

import javax.swing.*;

/**
 * @author peter
 */
public interface DomUIControl extends CommittablePanel {

  DomElement getDomElement();

  JComponent getBoundComponent();

  JComponent getFocusedComponent();

  void bind(JComponent component);
}
