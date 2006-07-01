package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntEnumEditor extends PropertyEditor<Integer> {
  private JComboBox myCbx;
  private final Pair[] myPairs;

  public IntEnumEditor(@NotNull final Pair[] pairs) {
    myPairs = pairs;
  }

  public final void updateUI() {
    SwingUtilities.updateComponentTreeUI(getCbx());
    SwingUtilities.updateComponentTreeUI((JComponent)getCbx().getRenderer());
  }

  public final Integer getValue() throws Exception {
    final Object selectedItem = getCbx().getSelectedItem();
    final Pair pair = (Pair)selectedItem;
    return pair.myValue;
  }

  public JComponent getComponent(final RadComponent ignored, final Integer value, final boolean inplace) {
    // Find pair
    if (value == null) {
      getCbx().setSelectedItem(null);
      return getCbx();
    }
    final ComboBoxModel model = getCbx().getModel();
    for (int i = model.getSize() - 1; i >= 0; i--) {
      final Pair pair = (Pair)model.getElementAt(i);
      if (pair.myValue == value.intValue()) {
        getCbx().setSelectedIndex(i);
        return getCbx();
      }
    }
    throw new IllegalArgumentException("unknown value: " + value);
  }

  private JComboBox getCbx() {
    if (myCbx == null) {
      myCbx = new JComboBox(myPairs);
      myCbx.setBorder(BorderFactory.createEmptyBorder());
      myCbx.addPopupMenuListener(new MyPopupMenuListener());
    }
    return myCbx;
  }

  private final class MyPopupMenuListener implements PopupMenuListener {
    private boolean myCancelled;

    public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
      myCancelled = false;
    }

    public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
      if (!myCancelled) {
        fireValueCommited(true);
      }
    }

    public void popupMenuCanceled(final PopupMenuEvent e) {
      myCancelled = true;
    }
  }

  public static final class Pair {
    public final int myValue;
    /**
     * Textual description of the <code>myValue</code>. This field is never <code>null</code>
     */
    public final String myText;

    public Pair(final int value, @NotNull final String text) {
      myValue = value;
      myText = text;
    }

    public String toString() {
      return myText;
    }
  }
}
