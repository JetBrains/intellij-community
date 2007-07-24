/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.list;

import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.ui.popup.PopupIcons;

import javax.swing.*;

public class PopupListElementRenderer extends GroupedItemsListRenderer {

  private ListPopupImpl myPopup;

  public PopupListElementRenderer(final ListPopupImpl aPopup) {
    super(new ListItemDescriptor() {
      public String getTextFor(Object value) {
        return aPopup.getListStep().getTextFor(value);
      }

      public String getTooltipFor(Object value) {
        return null;
      }

      public Icon getIconFor(Object value) {
        return aPopup.getListStep().getIconFor(value);
      }

      public boolean hasSeparatorAboveOf(Object value) {
        return aPopup.getListModel().isSeparatorAboveOf(value);
      }

      public String getCaptionAboveOf(Object value) {
        return aPopup.getListModel().getCaptionAboveOf(value);
      }
    });
    myPopup = aPopup;
  }

  protected void prepareItemComponent(JList list, Object value, boolean isSelected) {
    ListPopupStep<Object> step = myPopup.getListStep();
    boolean isSelectable = step.isSelectable(value);
    myTextLabel.setEnabled(isSelectable);

    super.prepareItemComponent(list, value, isSelected);

    if (step.isMnemonicsNavigationEnabled()) {
      final int pos = step.getMnemonicNavigationFilter().getMnemonicPos(value);
      if (pos != -1) {
        String text = myTextLabel.getText();
        text = text.substring(0, pos) + text.substring(pos + 1);
        myTextLabel.setText(text);
        myTextLabel.setDisplayedMnemonicIndex(pos);
      }
    }
    else {
      myTextLabel.setDisplayedMnemonicIndex(-1);
    }

    if (myPopup.getStep().hasSubstep(value) && isSelectable) {
      myNextStepLabel.setVisible(true);
      myNextStepLabel.setIcon(isSelected ? PopupIcons.HAS_NEXT_ICON : PopupIcons.HAS_NEXT_ICON_GRAYED);
    }
    else {
      myNextStepLabel.setVisible(false);
      //myNextStepLabel.setIcon(PopupIcons.EMPTY_ICON);
    }
  }


}
