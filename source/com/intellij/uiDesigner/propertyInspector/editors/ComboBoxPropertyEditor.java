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
public abstract class ComboBoxPropertyEditor extends PropertyEditor{
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

  public Object getValue() throws Exception{
    if(myCbx.isEditable()){
      final Component editorComponent = myCbx.getEditor().getEditorComponent();
      return ((JTextField)editorComponent).getText();
    }
    else{
      return myCbx.getSelectedItem();
    }
  }

  private final class MyPopupMenuListener implements PopupMenuListener{
    private boolean myCancelled;

    public void popupMenuWillBecomeVisible(final PopupMenuEvent e){
      myCancelled=false;
    }

    public void popupMenuWillBecomeInvisible(final PopupMenuEvent e){
      if(!myCancelled){
        fireValueCommited();
      }
    }

    public void popupMenuCanceled(final PopupMenuEvent e){
      myCancelled=true;
    }
  }
}