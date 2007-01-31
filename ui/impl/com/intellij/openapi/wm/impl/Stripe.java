package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.ex.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.WeakKeymapManagerListener;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/**
 * @author Eugene Belyaev
 */
final class Stripe extends JPanel{
  private final int myAnchor;
  private final ArrayList<StripeButton> myButtons = new ArrayList<StripeButton>();
  private final MyKeymapManagerListener myKeymapManagerListener;
  private final WeakKeymapManagerListener myWeakKeymapManagerListener;
  private final MyUISettingsListener myUISettingsListener;

  private Dimension myPrefSize;

  Stripe(final int anchor){
    super(new GridBagLayout());
    //setBackground(new Color(247, 243, 239));
    myAnchor = anchor;
    myKeymapManagerListener=new MyKeymapManagerListener();
    myWeakKeymapManagerListener=new WeakKeymapManagerListener(KeymapManagerEx.getInstanceEx(),myKeymapManagerListener);
    myUISettingsListener=new MyUISettingsListener();
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public void addNotify(){
    super.addNotify();
    updateText();
    updateState();
    KeymapManagerEx.getInstanceEx().addKeymapManagerListener(myWeakKeymapManagerListener);
    UISettings.getInstance().addUISettingsListener(myUISettingsListener);
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  public void removeNotify(){
    KeymapManagerEx.getInstanceEx().removeKeymapManagerListener(myWeakKeymapManagerListener);
    UISettings.getInstance().removeUISettingsListener(myUISettingsListener);
    super.removeNotify();
  }

  void addButton(final StripeButton button,final Comparator comparator){
    myPrefSize = null;
    myButtons.add(button);
    Collections.sort(myButtons,comparator);
    add(button);
    revalidate();
  }

  void removeButton(final StripeButton button) {
    myPrefSize = null;
    myButtons.remove(button);
    remove(button);
    revalidate();
  }


  public void doLayout() {
    recomputeBounds(true);
  }

  private Dimension recomputeBounds(boolean setBounds) {
    final boolean horizontal = isHorizontal();

    int eachX = -1;
    int eachY = 0;
    Dimension size = new Dimension();
    final int gap = 1;

    Dimension max = new Dimension();
    for (StripeButton eachButton : myButtons) {
      final Dimension eachSize = eachButton.getPreferredSize();
      max.width = Math.max(eachSize.width, max.width);
      max.height = Math.max(eachSize.height, max.height);
    }

    for (StripeButton eachButton : myButtons) {
      if (!eachButton.isVisible()) continue;

      final Dimension eachSize = eachButton.getPreferredSize();
      if (eachX == -1) {
        if (horizontal) {
          final Insets insets = eachButton.getInsets();
          eachX = eachSize.height - (insets == null ? 0 : insets.top);
        } else {
          eachX = 0;
        }
      }

      if (setBounds) {
        final int width = horizontal ? eachSize.width : max.width;
        final int height = horizontal ? max.height : eachSize.height;
        eachButton.setBounds(eachX, eachY, width, height);
      }
      if (horizontal) {
        final int deltaX = eachSize.width + gap;
        eachX += deltaX;
        size.width += deltaX;
        size.height = eachSize.height;
      } else {
        final int deltaY = eachSize.height + gap;
        eachY += deltaY;
        size.width = eachSize.width;
        size.height += deltaY;
      }
    }

    return size;
  }

  private boolean isHorizontal() {
    return myAnchor == SwingConstants.TOP || myAnchor == SwingConstants.BOTTOM;
  }

  public Dimension getPreferredSize() {
    if (myPrefSize == null) {
      myPrefSize = recomputeBounds(false);
    }
    return myPrefSize;
  }


  private void updateText(){
    final int size=myButtons.size();
    for(int i=0;i<size;i++){
      ((StripeButton)myButtons.get(i)).updateText();
    }
  }

  private void updateState(){
    final int size=myButtons.size();
    for(int i=0;i<size;i++){
      ((StripeButton)myButtons.get(i)).updateState();
    }
  }

  public boolean containsScreen(final Point screenPoint) {
    Point point = new Point(screenPoint);
    SwingUtilities.convertPointFromScreen(point, this);
    return contains(point);
  }

  private final class MyKeymapManagerListener implements KeymapManagerListener{
    public void activeKeymapChanged(final Keymap keymap){
      updateText();
    }
  }

  private final class MyUISettingsListener implements UISettingsListener{
    public void uiSettingsChanged(final UISettings source){
      updateText();
      updateState();
    }
  }
}