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
  private final ArrayList myButtons = new ArrayList();
  private final MyKeymapManagerListener myKeymapManagerListener;
  private final WeakKeymapManagerListener myWeakKeymapManagerListener;
  private final MyUISettingsListener myUISettingsListener;

  Stripe(final int anchor){
    super(new GridBagLayout());
    setBackground(new Color(247, 243, 239));
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
    myButtons.add(button);
    Collections.sort(myButtons,comparator);
    rebuild();
  }

  void removeButton(final StripeButton button) {
    myButtons.remove(button);
    rebuild();
  }

  private void rebuild() {
    removeAll();
    if (myAnchor == SwingConstants.TOP || myAnchor == SwingConstants.BOTTOM) {
      add(Box.createHorizontalStrut(19));
      int idx=1;
      for (Iterator i=myButtons.iterator();i.hasNext();) {
        final StripeButton button=(StripeButton)i.next();
        add(
          button,
          new GridBagConstraints(idx,0,1,1,0,1,GridBagConstraints.WEST,GridBagConstraints.VERTICAL,new Insets(0,0,0,0),0,0)
        );
        idx++;
      }
      add(
        Box.createGlue(),
        new GridBagConstraints(idx,0,1,1,1,1,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,0,0),0,0)
      );
    }else if(myAnchor == SwingConstants.LEFT || myAnchor == SwingConstants.RIGHT) {
      for (int i = 0; i < myButtons.size(); i++) {
        final StripeButton button=(StripeButton)myButtons.get(i);
        add(
          button,
          new GridBagConstraints(0,i,1,1,1,0,GridBagConstraints.CENTER,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0)
        );
      }
      final GridBagConstraints gc = new GridBagConstraints();
      gc.gridy = myButtons.size();
      gc.weighty = 1;
      gc.anchor = GridBagConstraints.NORTH;
      add(Box.createGlue(), gc);
    }
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