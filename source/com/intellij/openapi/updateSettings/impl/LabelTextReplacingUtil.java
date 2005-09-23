/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.util.IJSwingUtilities;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;

/**
 * @author nik
 */
public class LabelTextReplacingUtil {

  /**
   * replace
   *   $PRODUCT$ -> ApplicationNamesInfo.getInstance().getProductName()
   *   $FULLNAME$ -> ApplicationNamesInfo.getInstance().getFullProductName()
   * in text of component's labels
   */
  public static void replaceText(JComponent component) {
    final Iterator<Component> children = IJSwingUtilities.getChildren(component);
    while (children.hasNext()) {
      Component child = children.next();
      if (child instanceof JLabel) {
        final JLabel label = (JLabel)child;
        String oldText = label.getText();
        String newText = StringUtil.replace(oldText, "$PRODUCT$", ApplicationNamesInfo.getInstance().getProductName());
        newText = StringUtil.replace(newText, "$FULLNAME$", ApplicationNamesInfo.getInstance().getFullProductName());
        label.setText(newText);
      }
    }
  }

}
