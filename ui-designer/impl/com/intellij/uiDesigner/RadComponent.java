package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Constructor;
import java.util.HashSet;

import org.jetbrains.annotations.NonNls;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class RadComponent implements IComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.RadComponent");

  /**
   * Shared instance of empty array of RadComponenets
   */
  public static final RadComponent[] EMPTY_ARRAY = new RadComponent[]{};
  /**
   * Using this constant as client property of the Swing component
   * you can find corresponding <code>RadComponent</code>
   */
  @NonNls
  public static final String CLIENT_PROP_RAD_COMPONENT = "radComponent";
  /**
   * Whether the component selected or not. Value is java.lang.Boolean
   */
  @NonNls
  public static final String PROP_SELECTED="selected";

  /**
   * Component id is unique per RadRootContainer.
   */
  private final String myId;
  /**
   * @see #getBinding()
   */
  private String myBinding;

  private final Module myModule;
  private final Class myClass;
  /**
   * Delegee is the JComponent which really represents the
   * component in UI.
   */
  private final JComponent myDelegee;
  /**
   * Parent RadContainer. This field is always not <code>null</code>
   * is the component is in hierarchy. But the root of hierarchy
   * has <code>null</code> parent indeed.
   */
  private RadContainer myParent;
  /**
   * Defines whether the component selected or not.
   */
  private boolean mySelected;
  /**
   * never <code>null</code>
   */
  private final GridConstraints myConstraints;

  private Object myCustomLayoutConstraints;

  private final PropertyChangeSupport myChangeSupport;

  private final HashSet myModifiedPropertyNames;

  private boolean myHasDragger;

  /**
   * Creates new <code>RadComponent</code> with the specified
   * class of delegee and specified ID.
   *
   * @param aClass class of the compoent's delegee
   * @param id id of the compoent inside the form. <code>id</code>
   * should be a unique atring inside the form.
   */
  public RadComponent(final Module module, final Class aClass, final String id){
    LOG.assertTrue(module != null);
    LOG.assertTrue(aClass != null);
    LOG.assertTrue(id != null);

    myModule = module;
    myClass = aClass;
    myId = id;

    myChangeSupport=new PropertyChangeSupport(this);
    myConstraints = new GridConstraints();
    myModifiedPropertyNames = new HashSet();

    try {
      final Constructor constructor = myClass.getConstructor(new Class[0]);
      constructor.setAccessible(true);
      myDelegee = (JComponent)constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    myDelegee.putClientProperty(CLIENT_PROP_RAD_COMPONENT, this);
  }

  /**
   * @return module for the component. Never returns <code>null</code>.
   */
  public final Module getModule() {
    return myModule;
  }

  /**
   * Initializes introspected properties into default values and
   * sets default component's constraints.
   */
  public final void init(final ComponentItem item) {
    LOG.assertTrue(item != null);

    final IntrospectedProperty[] properties = Palette.getInstance(myModule.getProject()).getIntrospectedProperties(myClass);
    for (int i = 0; i < properties.length; i++) {
      final IntrospectedProperty property = properties[i];
      final Object initialValue = item.getInitialValue(property);
      if (initialValue != null) {
        try {
          property.setValue(this, initialValue);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    myConstraints.restore(item.getDefaultConstraints());
  }

  /**
   * @return the component's id. It is unique within the form. Never null.
   */
  public final String getId(){
    return myId;
  }

  public final String getBinding(){
    return myBinding;
  }

  public final void setBinding(final String binding){
    //TODO[anton,vova]: check that binding is a valid java identifier!!!
    myBinding = binding;
  }

  /**
   * @return Swing delegee component. The <code>RadComponent</code> has the same
   * delegee during all its life. The method never returns <code>null</code>.
   */
  public final JComponent getDelegee(){
    return myDelegee;
  }

  /**
   * Sometime bounds of the inplace editor depends on the point where
   * user invoked inplace editor.
   *
   * @param x x in delegee coordinate system
   * @param y y in delegee coordinate system
   *
   * @return inplace property for the <code>RadComponent</code> if any.
   * The method returns <code>null</code> if the component doesn't have
   * any inplace property. Please not the method can return different
   * instances of the property for each invokation.
   */
  public Property getInplaceProperty(final int x, final int y){
    return Palette.getInstance(myModule.getProject()).getInplaceProperty(getComponentClass());
  }

  /**
   * Sometime bounds of the inplace editor depends on the point where
   * user invoked inplace editor.
   *
   * @param x x in delegee coordinate system
   * @param y y in delegee coordinate system
   *
   * @return area where editor component is located. This is the hint to the
   * designer.  Designer can use or not this rectangle.
   */
  public Rectangle getInplaceEditorBounds(final Property property, final int x, final int y){
    LOG.assertTrue(property != null);
    return null;
  }

  /**
   * @return never <code>null</code>.
   */
  public final Class getComponentClass(){
    return myClass;
  }

  /**
   * @return never <code>null</code>.
   */
  public String getComponentClassName() {
    return myClass.getName();
  }

  public final Object getCustomLayoutConstraints(){
    return myCustomLayoutConstraints;
  }

  public final void setCustomLayoutConstraints(final Object customConstraints){
    myCustomLayoutConstraints = customConstraints;
  }

  public final boolean hasDragger(){
    return myHasDragger;
  }

  public final void setDragger(final boolean hasDragger){
    myHasDragger = hasDragger;
  }

  public final void addPropertyChangeListener(final PropertyChangeListener l){
    myChangeSupport.addPropertyChangeListener(l);
  }

  public final void removePropertyChangeListener(final PropertyChangeListener l){
    myChangeSupport.removePropertyChangeListener(l);
  }

  protected final void firePropertyChanged(
    final String propertyName,
    final Object oldValue,
    final Object newValue
  ){
    LOG.assertTrue(propertyName != null);
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /**
   * @return component's constarints. The method never returns <code>null</code>.
   */
  public final GridConstraints getConstraints(){
    return myConstraints;
  }

  public final RadContainer getParent(){
    return myParent;
  }

  public final void setParent(final RadContainer parent){
    myParent = parent;
  }

  public boolean isSelected(){
    return mySelected;
  }

  public void setSelected(final boolean selected){
    if (mySelected != selected) {
      mySelected = selected;
      firePropertyChanged(PROP_SELECTED,Boolean.valueOf(!mySelected),Boolean.valueOf(mySelected));
      GuiEditor.repaintLayeredPane(this);
    }
  }

  /**
   * @see JComponent#getClientProperty(Object)
   */
  public final Object getClientProperty(final Object key){
    LOG.assertTrue(key != null);
    return myDelegee.getClientProperty(key);
  }

  /**
   * @see JComponent#putClientProperty(Object, Object)
   */
  public final void putClientProperty(final Object key, final Object value){
    LOG.assertTrue(key != null);
    myDelegee.putClientProperty(key, value);
  }

  public final int getX() {
    return myDelegee.getX();
  }

  public final int getY() {
    return myDelegee.getY();
  }

  public final void setLocation(final Point location){
    myDelegee.setLocation(location);
  }

  public final void shift(final int dx, final int dy){
    myDelegee.setLocation(myDelegee.getX() + dx, myDelegee.getY() + dy);
  }

  public final int getWidth(){
    return myDelegee.getWidth();
  }

  public final int getHeight(){
    return myDelegee.getHeight();
  }

  public final Dimension getSize() {
    return myDelegee.getSize();
  }

  public final void setSize(final Dimension size) {
    myDelegee.setSize(size);
  }

  /**
   * @return bounds of the delegee in the parent container
   */
  public final Rectangle getBounds() {
    return myDelegee.getBounds();
  }

  public final void setBounds(final Rectangle bounds) {
    myDelegee.setBounds(bounds);
  }

  public final Dimension getMinimumSize(){
    return Util.getMinimumSize(myDelegee, myConstraints);
  }

  public final Dimension getPreferredSize(){
    return Util.getPreferredSize(myDelegee, myConstraints);
  }

  /**
   * todo[anton] get rid of
   * @return
   */
  public final RevalidateInfo revalidate() {
    final RevalidateInfo info = new RevalidateInfo();

    for (RadContainer container = this instanceof RadContainer ? (RadContainer)this : getParent(); container != null; container = container.getParent()) {
      final RadContainer parent = container.getParent();
      if (parent != null && parent.isXY()) {
        final Dimension size = container.getSize();
        final Dimension minimumSize = container.getMinimumSize();
        if (size.width < minimumSize.width || size.height < minimumSize.height) {
          info.myContainer = container;
          info.myPreviousContainerSize = size;
        }
      }
    }

    if (info.myContainer != null) {
      final Dimension minimumSize = info.myContainer.getMinimumSize();

      minimumSize.width = Math.max(minimumSize.width, info.myContainer.getWidth());
      minimumSize.height = Math.max(minimumSize.height, info.myContainer.getHeight());

      info.myContainer.getDelegee().setSize(minimumSize);
    }

    myDelegee.revalidate();

    return info;
  }

  public final boolean isMarkedAsModified(final Property property) {
    return myModifiedPropertyNames.contains(property.getName());
  }

  public final void markPropertyAsModified(final Property property) {
    myModifiedPropertyNames.add(property.getName());
  }

  /**
   * @param x in delegee coordinates
   * @param y in delegee coordinates
   * @param componentCount number of components to be dropped; always > 0
   */
  public abstract boolean canDrop(int x, int y, int componentCount);

  public void processMouseEvent(final MouseEvent event) {}

  /**
   * Serializes component into the passed <code>writer</code>
   */
  public abstract void write(XmlWriter writer);

  /**
   * Serializes component's ID
   */
  protected final void writeId(final XmlWriter writer){
    writer.addAttribute("id", getId());
  }

  /**
   * Serializes component's class
   */
  protected final void writeClass(final XmlWriter writer){
    writer.addAttribute("class", getComponentClass().getName());
  }

  protected final void writeBinding(final XmlWriter writer){
    // Binding
    if (getBinding() != null){
      writer.addAttribute("binding", getBinding());
    }
  }

  protected final void writeConstraints(final XmlWriter writer){
    writer.startElement("constraints");
    try {
      getParent().writeConstraints(writer, this);
    } finally {
      writer.endElement(); // constraints
    }
  }

  protected final void writeProperties(final XmlWriter writer){
    writer.startElement("properties");
    try{
      final IntrospectedProperty[] introspectedProperties =
        Palette.getInstance(myModule.getProject()).getIntrospectedProperties(getComponentClass());
      for (int i = 0; i < introspectedProperties.length; i++) {
        final IntrospectedProperty property = introspectedProperties[i];
        if (isMarkedAsModified(property)) {
          final Object value = property.getValue(this);
          if (value != null) {
            writer.startElement(property.getName());
            try{
              property.write(value, writer);
            }finally{
              writer.endElement();
            }
          }
        }
      }
    }finally{
      writer.endElement(); // properties
    }
  }
}