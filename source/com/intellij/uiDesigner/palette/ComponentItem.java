package com.intellij.uiDesigner.palette;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;

import javax.swing.*;
import java.util.HashMap;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */ 
public final class ComponentItem implements Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.palette.ComponentItem");

  private String myClassName;
  private final GridConstraints myDefaultConstraints;
  /**
   * Do not use this member directly. Use {@link #getIcon()} instead.
   */
  private Icon myIcon;
  /**
   * Do not use this member directly. Use {@link #getSmallIcon()} instead.
   */
  private Icon mySmallIcon;
  /**
   * @see #getIconPath()
   * @see #setIconPath(java.lang.String)
   */
  private String myIconPath;
  /**
   * Do not access this field directly. Use {@link #getToolTipText()} instead.
   */
  final String myToolTipText;
  private final HashMap<String, StringDescriptor> myPropertyName2intitialValue;
  /** Whether item is removable or not */
  private final boolean myRemovable;

  /**
   * @param iconPath can be <code>null</code>.
   * @param toolTipText if null, will be automatically calculated
   * @param propertyName2intitialValue cannot be <code>null</code>
   */
  public ComponentItem(
    final String className,
    final String iconPath,
    final String toolTipText,
    final GridConstraints defaultConstraints,
    final HashMap<String, StringDescriptor> propertyName2intitialValue,
    final boolean removable
  ){
    LOG.assertTrue(defaultConstraints != null);
    LOG.assertTrue(propertyName2intitialValue != null);

    setClassName(className);
    setIconPath(iconPath);

    myToolTipText = toolTipText;
    myDefaultConstraints = defaultConstraints;
    myPropertyName2intitialValue = propertyName2intitialValue;

    myRemovable = removable;
  }

  /**
   * @return whether the item is removable from palette or not.
   */
  public boolean isRemovable() {
    return myRemovable;
  }

  private static String calcToolTipText(final String className) {
    LOG.assertTrue(className != null);

    final int lastDotIndex = className.lastIndexOf('.');
    if (lastDotIndex != -1 && lastDotIndex != className.length() - 1/*not the last char in class name*/) {
      return className.substring(lastDotIndex + 1) + " (" + className.substring(0, lastDotIndex) + ")";
    }
    else{
      return className;
    }
  }

  /** Creates deep copy of the object. You can edit any properties of the returned object. */
  public ComponentItem clone(){
    final ComponentItem result = new ComponentItem(
      myClassName,
      myIconPath,
      myToolTipText,
      (GridConstraints)myDefaultConstraints.clone(),
      (HashMap<String, StringDescriptor>)myPropertyName2intitialValue.clone(),
      myRemovable
    );
    return result;
  }

  /**
   * @return string that represents path in the JAR file system that was used to load
   * icon returned by {@link #getIcon()} method. This method can returns <code>null</code>.
   * It means that palette item has some "unknown" item.
   */
  String getIconPath(){
    return myIconPath;
  }

  /**
   * @param iconPath new path inside JAR file system. <code>null</code> means that
   * <code>iconPath</code> is not specified and some "unknown" icon should be used
   * to represent the {@link ComponentItem} in UI.
   */
  void setIconPath(final String iconPath){
    myIcon = null; // reset cached icon
    mySmallIcon = null; // reset cached icon

    myIconPath = iconPath;
  }

  /**
   * @return item's icon. This icon is used to represent item at the toolbar.
   * Note, that the method never returns <code>null</code>. It returns some
   * default "unknown" icon for the items that has no specified icon in the XML.
   */
  public Icon getIcon(){
    // Check cached value first
    if(myIcon != null){
      return myIcon;
    }

    // Create new icon
    if(myIconPath != null){
      myIcon = IconLoader.getIcon(myIconPath);
    }
    if(myIcon == null){
      myIcon = IconLoader.getIcon("/com/intellij/uiDesigner/icons/unknown.png");
    }
    LOG.assertTrue(myIcon != null);
    return myIcon;
  }

  /**
   * @return small item's icon. This icon represents component in the
   * component tree. The method never returns <code>null</code>. It returns some
   * default "unknown" icon for the items that has no specified icon in the XML.
   */
  public Icon getSmallIcon(){
    // Check cached value first
    if(mySmallIcon != null){
      return myIcon;
    }

    // [vova] It's safe to cast to ImageIcon here because all icons loaded by IconLoader
    // are ImageIcon(s).
    final ImageIcon icon = (ImageIcon)getIcon();
    mySmallIcon = new MySmallIcon(icon.getImage());
    return mySmallIcon;
  }

  /**
   * @return name of component's class which is represented by the item.
   * This method never returns <code>null</code>.
   */
  public String getClassName() {
    return myClassName;
  }

  /**
   * @param className name of the class that will be instanteated when user drop
   * item on the form. Cannot be <code>null</code>. If the class does not exist or
   * could not be instanteated (for example, class has no default constructor,
   * it's not a subclass of JComponent, etc) then placeholder component will be
   * added to the form.
   */
  public void setClassName(final String className){
    LOG.assertTrue(className != null);
    myClassName = className;
  }

  public String getToolTipText() {
    return myToolTipText != null ? myToolTipText : calcToolTipText(myClassName);
  }

  /**
   * @return never <code>null</code>.
   */
  public GridConstraints getDefaultConstraints() {
    return myDefaultConstraints;
  }

  /**
   * The method returns initial value of the property. Term
   * "initial" means that just after creation of RadComponent
   * all its properties are set into initial values.
   * The method returns <code>null</code> if the
   * initial property is not defined. Unfortunately we cannot
   * put this method into the constuctor of <code>RadComponent</code>.
   * The problem is that <code>RadComponent</code> is used in the
   * code genaration and code generation doesn't depend on any
   * <code>ComponentItem</code>, so we need to initialize <code>RadComponent</code>
   * in all places where it's needed explicitly.
   */
  public Object getInitialValue(final IntrospectedProperty property){
    return myPropertyName2intitialValue.get(property.getName());
  }

  /**
   * Internal method. It should be used only to externalize initial item's values.
   * This method never returns <code>null</code>.
   */
  HashMap<String, StringDescriptor> getInitialValues(){
    return myPropertyName2intitialValue;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ComponentItem)) return false;

    final ComponentItem componentItem = (ComponentItem)o;

    if (myClassName != null ? !myClassName.equals(componentItem.myClassName) : componentItem.myClassName != null) return false;
    if (myDefaultConstraints != null
        ? !myDefaultConstraints.equals(componentItem.myDefaultConstraints)
        : componentItem.myDefaultConstraints != null) {
      return false;
    }
    if (myIconPath != null ? !myIconPath.equals(componentItem.myIconPath) : componentItem.myIconPath != null) return false;
    if (myPropertyName2intitialValue != null
        ? !myPropertyName2intitialValue.equals(componentItem.myPropertyName2intitialValue)
        : componentItem.myPropertyName2intitialValue != null) {
      return false;
    }
    if (myToolTipText != null ? !myToolTipText.equals(componentItem.myToolTipText) : componentItem.myToolTipText != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myClassName != null ? myClassName.hashCode() : 0);
    result = 29 * result + (myDefaultConstraints != null ? myDefaultConstraints.hashCode() : 0);
    result = 29 * result + (myIconPath != null ? myIconPath.hashCode() : 0);
    result = 29 * result + (myToolTipText != null ? myToolTipText.hashCode() : 0);
    result = 29 * result + (myPropertyName2intitialValue != null ? myPropertyName2intitialValue.hashCode() : 0);
    return result;
  }

  private static final class MySmallIcon implements Icon{
    private final Image myImage;

    public MySmallIcon(final Image delegate) {
      LOG.assertTrue(delegate != null);
      myImage = delegate;
    }

    public int getIconHeight() {
      return 18;
    }

    public int getIconWidth() {
      return 18;
    }

    public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
      g.drawImage(myImage, 2, 2, 14, 14, c);
    }
  }
}