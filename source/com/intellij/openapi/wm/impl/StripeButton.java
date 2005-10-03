package com.intellij.openapi.wm.impl;

import com.intellij.ide.actions.ActivateToolWindowAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public final class StripeButton extends JToggleButton implements ActionListener{
  private final Color ourBackgroundColor=new Color(247, 243, 239);

  /**
   * This is analog of Swing mnemomic. We cannot use the standard ones
   * because it causes typing of "funny" characters into the editor.
   */
  private int myMnemonic;
  private final InternalDecorator myDecorator;
  private final MyPropertyChangeListener myToolWindowHandler;
  private boolean myPressedWhenSelected;

  StripeButton(final InternalDecorator decorator){
    myDecorator=decorator;
    myToolWindowHandler=new MyPropertyChangeListener();

    init();
  }

  /**
   * We are using the trick here: the method does all things that super method does
   * excepting firing of the MNEMONIC_CHANGED_PROPERTY event. After that mnemonic
   * doesn't work via standard Swing rules (processing of Alt keystrokes).
   */
  public void setMnemonic(final int mnemonic){
    throw new UnsupportedOperationException("use setMnemonic2(int)");
  }

  private void setMnemonic2(final int mnemonic){
    myMnemonic=mnemonic;
    revalidate();
    repaint();
  }

  public int getMnemonic2(){
    return myMnemonic;
  }

  WindowInfo getWindowInfo(){
    return myDecorator.getWindowInfo();
  }

  private void init(){
    setFocusable(false);
    setBackground(ourBackgroundColor);
    final Border border = BorderFactory.createEmptyBorder(5, 5, 0, 5);
    setBorder(border);
    updateText();
    updateState();
    apply(myDecorator.getWindowInfo());
    myDecorator.getToolWindow().addPropertyChangeListener(myToolWindowHandler);
    addActionListener(this);
    addMouseListener(new MyPopupHandler());
    setRolloverEnabled(true);
    setOpaque(false);

    enableEvents(MouseEvent.MOUSE_EVENT_MASK);
  }

  protected void processMouseEvent(final MouseEvent e){
    if(MouseEvent.MOUSE_PRESSED==e.getID()){
      myPressedWhenSelected=isSelected();
    }
    super.processMouseEvent(e);
  }

  public void actionPerformed(final ActionEvent e){
    if(myPressedWhenSelected){
      myDecorator.fireHidden();
    }else{
      myDecorator.fireActivated();
    }
    myPressedWhenSelected=false;
  }

  public void apply(final WindowInfo info){
    setSelected(info.isVisible()||info.isActive());
  }

  void dispose(){
    myDecorator.getToolWindow().removePropertyChangeListener(myToolWindowHandler);
  }

  private void showPopup(final Component component,final int x,final int y){
    final ActionGroup group=myDecorator.createPopupGroup();
    final ActionPopupMenu popupMenu=ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN,group);
    popupMenu.getComponent().show(component,x,y);
  }

  public void updateUI(){
    setUI(StripeButtonUI.createUI(this));
    Font font= UIUtil.getButtonFont();
    if(font.getSize()%2==1){ // that's a trick. Size of antialiased font isn't properly calculated for fonts with odd size
      font=font.deriveFont(font.getStyle(),font.getSize()-1);
    }
    setFont(font);
  }

  /**
   * Updates button's text. It composes text as combination of tool window <code>id</code>
   * and short cut registered in the key map.
   */
  void updateText(){
    final String toolWindowId = getWindowInfo().getId();
    String text=toolWindowId;
    if (UISettings.getInstance().SHOW_WINDOW_SHORTCUTS) {
      final int mnemonic=ActivateToolWindowAction.getMnemonicForToolWindow(toolWindowId);
      if(mnemonic!=-1){
        text = ((char)mnemonic) + ": " + text;
        setMnemonic2(mnemonic);
      }else{
        setMnemonic2(0);
      }
    }
    setText(text);
  }

  void updateState(){
    final boolean available=myDecorator.getToolWindow().isAvailable();
    if(UISettings.getInstance().ALWAYS_SHOW_WINDOW_BUTTONS){
      setVisible(true);
    }else{
      setVisible(available);
    }
    setEnabled(available);
  }

  private final class MyPopupHandler extends PopupHandler{
    public void invokePopup(final Component component,final int x,final int y) {
      showPopup(component,x,y);
    }
  }

  private final class MyPropertyChangeListener implements PropertyChangeListener{
    public void propertyChange(final PropertyChangeEvent e){
      final String name=e.getPropertyName();
      if(ToolWindowEx.PROP_AVAILABLE.equals(name)){
        updateState();
      }else if(ToolWindowEx.PROP_TITLE.equals(name)){
        updateText();
      }else if(ToolWindowEx.PROP_ICON.equals(name)){
        final Icon icon=(Icon)e.getNewValue();
        final Icon disabledIcon=IconLoader.getDisabledIcon(icon);
        setIcon(icon);
        setDisabledIcon(disabledIcon);
      }
    }
  }
}