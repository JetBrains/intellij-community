/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 07.06.2002
 * Time: 18:16:19
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiModifier;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.EventListener;

public class VisibilityPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.ui.VisibilityPanel");

  private JRadioButton myRbAsIs;
  private JRadioButton myRbPrivate;
  private JRadioButton myRbProtected;
  private JRadioButton myRbPackageLocal;
  private JRadioButton myRbPublic;

  public VisibilityPanel(boolean hasAsIs) {
    setBorder(IdeBorderFactory.createTitledBorder("Visibility"));
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    ButtonGroup bg = new ButtonGroup();

    ItemListener listener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if(e.getStateChange() == ItemEvent.SELECTED) {
          fireStateChanged();
        }
      };
    };

    if(hasAsIs) {
      myRbAsIs = new JRadioButton("As is");
      myRbAsIs.setMnemonic('a');
      myRbAsIs.addItemListener(listener);
      add(myRbAsIs);
      bg.add(myRbAsIs);
    }



    myRbPrivate = new JRadioButton("Private");
    myRbPrivate.setMnemonic('v');
    myRbPrivate.addItemListener(listener);
    myRbPrivate.setFocusable(false);
    add(myRbPrivate);
    bg.add(myRbPrivate);

    myRbPackageLocal = new JRadioButton("Package local");
    myRbPackageLocal.setMnemonic('k');
    myRbPackageLocal.addItemListener(listener);
    myRbPackageLocal.setFocusable(false);
    add(myRbPackageLocal);
    bg.add(myRbPackageLocal);

    myRbProtected = new JRadioButton("Protected");
    myRbProtected.setMnemonic('o');
    myRbProtected.addItemListener(listener);
    myRbProtected.setFocusable(false);
    add(myRbProtected);
    bg.add(myRbProtected);

    myRbPublic = new JRadioButton("Public");
    myRbPublic.setMnemonic('b');
    myRbPublic.addItemListener(listener);
    myRbPublic.setFocusable(false);
    add(myRbPublic);
    bg.add(myRbPublic);
  }


  public String getVisibility() {
    if (myRbPublic.isSelected()) {
      return PsiModifier.PUBLIC;
    }
    if (myRbPackageLocal.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (myRbProtected.isSelected()) {
      return PsiModifier.PROTECTED;
    }
    if (myRbPrivate.isSelected()) {
      return PsiModifier.PRIVATE;
    }

    return null;
  }

  public void setVisibilityEnabled(String visibility, boolean value) {
    if(PsiModifier.PRIVATE.equals(visibility)) myRbPrivate.setEnabled(value);
    else if(PsiModifier.PROTECTED.equals(visibility)) myRbProtected.setEnabled(value);
    else if(PsiModifier.PACKAGE_LOCAL.equals(visibility)) myRbPackageLocal.setEnabled(value);
    else if(PsiModifier.PUBLIC.equals(visibility)) myRbPublic.setEnabled(value);
  }

  public void setVisibility(String visibility) {
    if (PsiModifier.PUBLIC.equals(visibility)) {
      myRbPublic.setSelected(true);
    }
    else if (PsiModifier.PROTECTED.equals(visibility)) {
      myRbProtected.setSelected(true);
    }
    else if (PsiModifier.PACKAGE_LOCAL.equals(visibility)) {
      myRbPackageLocal.setSelected(true);
    }
    else if (PsiModifier.PRIVATE.equals(visibility)) {
      myRbPrivate.setSelected(true);
    }
    else {
      LOG.assertTrue(myRbAsIs != null);
      myRbAsIs.setSelected(true);
    }
  }

  public static interface StateChanged extends EventListener {
    void visibilityChanged(String newVisibility);
  }

  public void addStateChangedListener(StateChanged l) {
    listenerList.add(StateChanged.class, l);
  }
  public void fireStateChanged() {
    Object[] list = listenerList.getListenerList();

    String visibility = getVisibility();
    for (int i = 0; i < list.length; i++) {
      if(list[i] instanceof StateChanged) {
        ((StateChanged) list[i]).visibilityChanged(visibility);
      }
    }
  }
}
