package com.intellij.uiDesigner.propertyInspector;

import com.intellij.uiDesigner.RadComponent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class Property {
  protected static final Property[] EMPTY_ARRAY=new Property[]{};

  /**
   * Parent property
   */
  private final Property myParent;
  /**
   * Propertie's name
   */
  private final String myName;

  /**
   * @exception java.lang.IllegalArgumentException if <code>name</code> is <code>null</code>
   */
  public Property(final Property parent, final String name){
    if(name == null){
      throw new IllegalArgumentException("name cannot be null");
    }
    myParent = parent;
    myName = name;
  }

  /**
   * @return property's name. The method always returns not <code>null</code> string.
   */
  public final String getName(){
    return myName;
  }

  /**
   * This method can extract value of the property from the
   * instance of the RadComponent. This value is passed to the
   * PropertyRenderer and PropertyEditor.
   */
  public abstract Object getValue(RadComponent component);

  /**
   * Do not invoke this method outside Property class, bacuse
   * <code>setValue(Component,Object)</code> does some additional work.
   * This method exists only for convenience.
   *
   * @see #setValue(RadComponent,Object)
   */
  protected abstract void setValueImpl(RadComponent component, Object value) throws Exception;


  /**
   * Sets the <code>value</code> of the property. This method is invoked
   * after editing is complete.
   *
   * @param component component which property should be set
   * @param value new propertie's value
   *
   * @exception java.lang.Exception if passed <code>value</code> cannot
   * be applied to the <code>component</code>. Note, the exception's
   * message will be shown to the user.
   */
  public final void setValue(final RadComponent component, final Object value) throws Exception{
    setValueImpl(component, value);
    Property topmostParent = this;
    while (topmostParent.getParent() != null) {
      topmostParent = topmostParent.getParent();
    }
    component.markPropertyAsModified(topmostParent);
    component.getDelegee().invalidate();
  }

  /**
   * @return property which is the parent for this property.
   * The method can return <code>null</code> if the property
   * doesn't have parent.
   */
  public final Property getParent(){
    return myParent;
  }

  /**
   * @return child properties. The method never returns <code>null</code>.
   */
  public abstract Property[] getChildren();

  /**
   * @return property's renderer. This method should never return <code>null</code>.
   */
  public abstract PropertyRenderer getRenderer();

  /**
   * @return property's editor. The method allows to return <code>null</code>.
   * In this case property is not editable.
   */
  public abstract PropertyEditor getEditor();
}
