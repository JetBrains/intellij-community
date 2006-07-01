package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class ComboBoxPropertyEditor<V> extends PropertyEditor<V> {
  protected final ComboBox myCbx;

  public ComboBoxPropertyEditor() {
    myCbx = new ComboBox(-1);
    myCbx.setBorder(null);
    myCbx.addPopupMenuListener(new MyPopupMenuListener());
  }

  public final void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCbx);
    SwingUtilities.updateComponentTreeUI((JComponent)myCbx.getRenderer());
  }

  public V getValue() throws Exception{
    if(myCbx.isEditable()){
      final Component editorComponent = myCbx.getEditor().getEditorComponent();
      //noinspection unchecked
      return (V)((JTextField)editorComponent).getText();
    }
    else{
      //noinspection unchecked
      return (V)myCbx.getSelectedItem();
    }
  }

  private final class MyPopupMenuListener implements PopupMenuListener{
    private boolean myCancelled;

    public void popupMenuWillBecomeVisible(final PopupMenuEvent e){
      myCancelled=false;
    }

    public void popupMenuWillBecomeInvisible(final PopupMenuEvent e){
      if(!myCancelled){
        fireValueCommitted(true);
      }
    }

    public void popupMenuCanceled(final PopupMenuEvent e){
      myCancelled=true;
    }
  }
}