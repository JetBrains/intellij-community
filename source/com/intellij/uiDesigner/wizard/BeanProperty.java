package com.intellij.uiDesigner.wizard;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BeanProperty implements Comparable<BeanProperty>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.wizard.BeanProperty");

  /**
   * Property name. Cannot be <code>null</code>.
   */
  public final String myName;
  /**
   * Property type. Cannot be <code>null</code>.
   * There are two possible types:
   * <ul>
   *  <li>java.lang.String</li>
   *  <li>boolean</li>
   * </ul>
   */
  public final String myType;

  public BeanProperty(final String name, final String type) {
    LOG.assertTrue(name != null);
    LOG.assertTrue(type != null);

    //noinspection HardCodedStringLiteral
    if(!"java.lang.String".equals(type) && !"boolean".equals(type)){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("unknown type: " + type);
    }

    myName = name;
    myType = type;
  }

  public int compareTo(final BeanProperty property) {
    if(property == null){
      return 1;
    }
    else{
      return myName.compareTo(property.myName);
    }
  }

  /**
   * This method is used by ComboBox editor of {@link BindToExistingBeanStep.MyTableCellEditor}
   */
  public String toString() {
    return myName;
  }

  public boolean equals(final Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof BeanProperty)) return false;
    return myName.equals(((BeanProperty)obj).myName);
  }

  public int hashCode() {
    return myName.hashCode();
  }
}
