package com.intellij.uiDesigner.propertyInspector;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface PropertyRenderer {
  /**
   * @return <code>JComponent</code> to represent the <code>value</code>
   * somewhere in UI (for example in the JList of in the JTree). To be
   * consistent with other UI additional parameter abount selection and
   * focus are also passed.
   */
  public JComponent getComponent(Object value, boolean selected, boolean hasFocus);

  /**
   * Renderer should update UI of all its internal components to fit current
   * IDEA Look And Feel. We cannot directly update UI of the component
   * that is returned by {@link #getComponent(Object,boolean,boolean) } method
   * because hidden component that are not in the Swing tree can exist.
   */
  public abstract void updateUI();
}
