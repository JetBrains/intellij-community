/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.util;

import com.intellij.ui.popup.BasePopup;
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public abstract class MnemonicsSearch {

  private BasePopup myPopup;
  private Map myChar2ValueMap = new HashMap();

  public MnemonicsSearch(BasePopup popup) {
    myPopup = popup;
    if (!myPopup.getStep().isMnemonicsNavigationEnabled()) return;

    final MnemonicNavigationFilter filter = myPopup.getStep().getMnemonicNavigationFilter();
    final Object[] values = filter.getValues();
    for (int i = 0; i < values.length; i++) {
      Object each = values[i];
      final int pos = filter.getMnemonicPos(each);
      if (pos != -1) {
        final String text = filter.getTextFor(each);
        final String charText = text.substring(pos + 1, pos + 2);
        myChar2ValueMap.put(charText.toUpperCase(), each);
        myChar2ValueMap.put(charText.toLowerCase(), each);
      }
    }
  }

  public void process(KeyEvent e) {
    if (e.isConsumed()) return;

    if (Character.isLetterOrDigit(e.getKeyChar())) {
      final String s = Character.toString(e.getKeyChar());
      final Object toSelect = myChar2ValueMap.get(s);
      if (toSelect != null) {
        select(toSelect);
      }
    }
  }

  protected abstract void select(Object value);

}
