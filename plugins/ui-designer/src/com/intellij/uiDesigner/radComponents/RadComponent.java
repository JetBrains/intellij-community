// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.designSurface.EventProcessor;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.properties.ClientPropertiesProperty;
import com.intellij.uiDesigner.propertyInspector.properties.ClientPropertyProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;

public abstract class RadComponent implements IComponent {
  private static final Logger LOG = Logger.getInstance(RadComponent.class);

  /**
   * Shared instance of empty array of RadComponenets
   */
  public static final RadComponent[] EMPTY_ARRAY = new RadComponent[]{};
  /**
   * Using this constant as client property of the Swing component
   * you can find corresponding {@code RadComponent}
   */
  public static final @NonNls String CLIENT_PROP_RAD_COMPONENT = "radComponent";

  public static final @NonNls String CLIENT_PROP_LOAD_TIME_LOCALE = "LoadTimeLocaleKey";

  /**
   * Whether the component selected or not. Value is java.lang.Boolean
   */
  public static final @NonNls String PROP_SELECTED = "selected";

  /**
   * Change notification for this property is fired when the constraints of a component
   * change.
   */
  public static final @NonNls String PROP_CONSTRAINTS = "constraints";

  /**
   * Component id is unique per RadRootContainer.
   */
  private final @NotNull String myId;
  /**
   * @see #getBinding()
   */
  private String myBinding;
  private boolean myCustomCreate = false;
  private boolean myLoadingProperties = false;

  private final ModuleProvider myModule;

  private final @NotNull Class myClass;
  /**
   * Delegee is the JComponent which really represents the
   * component in UI.
   */
  private final @NotNull JComponent myDelegee;
  /**
   * Parent RadContainer. This field is always not {@code null}
   * is the component is in hierarchy. But the root of hierarchy
   * has {@code null} parent indeed.
   */
  private RadContainer myParent;
  /**
   * Defines whether the component selected or not.
   */
  private boolean mySelected;

  private final @NotNull GridConstraints myConstraints;

  private Object myCustomLayoutConstraints;

  private final PropertyChangeSupport myChangeSupport;

  private final HashSet<String> myModifiedPropertyNames;

  private Palette myPalette;

  private boolean myHasDragger;
  private boolean myResizing;
  private boolean myDragging;
  private boolean myDragBorder;
  private boolean myDefaultBinding;

  /**
   * Creates new {@code RadComponent} with the specified
   * class of delegee and specified ID.
   *
   * @param aClass class of the compoent's delegee
   * @param id     id of the compoent inside the form. {@code id}
   *               should be a unique atring inside the form.
   */
  public RadComponent(final ModuleProvider module, final @NotNull Class aClass, final @NotNull String id) {
    myModule = module;
    myClass = aClass;
    myId = id;

    myChangeSupport = new PropertyChangeSupport(this);
    myConstraints = new GridConstraints();
    myModifiedPropertyNames = new HashSet<>();

    Constructor constructor;
    try {
      constructor = myClass.getConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
    }
    catch (NoSuchMethodException e) {
      try {
        constructor = Utils.suggestReplacementClass(myClass).getConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
      }
      catch (NoSuchMethodException e1) {
        throw new RuntimeException(e1);
      }
      setCustomCreate(true);
    }

    constructor.setAccessible(true);
    try {
      myDelegee = (JComponent)constructor.newInstance(ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    myDelegee.putClientProperty(CLIENT_PROP_RAD_COMPONENT, this);
  }

  public RadComponent(final ModuleProvider module, final @NotNull Class aClass, final @NotNull String id, final Palette palette) {
    this(module, aClass, id);
    myPalette = palette;
  }

  /**
   * @return module for the component.
   */
  public final Module getModule() {
    return myModule == null ? null : myModule.getModule();
  }

  public final Project getProject() {
    return myModule == null ? null : myModule.getProject();
  }

  public boolean isLoadingProperties() {
    return myLoadingProperties;
  }

  public Palette getPalette() {
    if (myPalette == null) {
      return Palette.getInstance(getProject());
    }
    return myPalette;
  }

  public void setPalette(final Palette palette) {
    myPalette = palette;
  }

  /**
   * Initializes introspected properties into default values and
   * sets default component's constraints.
   */
  public void init(final GuiEditor editor, final @NotNull ComponentItem item) {
    initDefaultProperties(item);
  }

  public void initDefaultProperties(final @NotNull ComponentItem item) {
    final IntrospectedProperty[] properties = getPalette().getIntrospectedProperties(this);
    for (final IntrospectedProperty property : properties) {
      final Object initialValue = item.getInitialValue(property);
      if (initialValue != null) {
        try {
          //noinspection unchecked
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
   * @return the component's id. It is unique within the form.
   */
  @Override
  public final @NotNull String getId() {
    return myId;
  }

  @Override
  public final @NlsSafe String getBinding() {
    return myBinding;
  }

  public final void setBinding(final String binding) {
    //TODO[anton,vova]: check that binding is a valid java identifier!!!
    myBinding = binding;
  }

  @Override
  public boolean isCustomCreate() {
    return myCustomCreate;
  }

  public void setCustomCreate(final boolean customCreate) {
    myCustomCreate = customCreate;
  }

  public boolean isCustomCreateRequired() {
    return !getDelegee().getClass().equals(getComponentClass());
  }

  /**
   * @return Swing delegee component. The {@code RadComponent} has the same
   *         delegee during all its life.
   */
  public final @NotNull JComponent getDelegee() {
    return myDelegee;
  }

  /**
   * Sometime bounds of the inplace editor depends on the point where
   * user invoked inplace editor.
   *
   * @param x x in delegee coordinate system
   * @param y y in delegee coordinate system
   * @return inplace property for the {@code RadComponent} if any.
   *         The method returns {@code null} if the component doesn't have
   *         any inplace property. Please not the method can return different
   *         instances of the property for each invokation.
   */
  public @Nullable Property getInplaceProperty(final int x, final int y) {
    return getDefaultInplaceProperty();
  }

  public @Nullable Property getDefaultInplaceProperty() {
    return getPalette().getInplaceProperty(this);
  }

  public @Nullable Rectangle getDefaultInplaceEditorBounds() {
    return null;
  }

  /**
   * Sometime bounds of the inplace editor depends on the point where
   * user invoked inplace editor.
   *
   * @param x x in delegee coordinate system
   * @param y y in delegee coordinate system
   * @return area where editor component is located. This is the hint to the
   *         designer.  Designer can use or not this rectangle.
   */
  public @Nullable Rectangle getInplaceEditorBounds(final @NotNull Property property, final int x, final int y) {
    return null;
  }

  public final @NotNull Class getComponentClass() {
    return myClass;
  }

  @Override
  public @NotNull
  @NlsSafe String getComponentClassName() {
    return myClass.getName();
  }

  @Override
  public final Object getCustomLayoutConstraints() {
    return myCustomLayoutConstraints;
  }

  public final void setCustomLayoutConstraints(final Object customConstraints) {
    myCustomLayoutConstraints = customConstraints;
  }

  public void changeCustomLayoutConstraints(final Object constraints) {
    setCustomLayoutConstraints(constraints);
    // update constraints in CardLayout
    final JComponent parent = getParent().getDelegee();
    for (int i = 0; i < parent.getComponentCount(); i++) {
      if (parent.getComponent(i) == getDelegee()) {
        parent.remove(i);
        parent.add(getDelegee(), constraints, i);
        break;
      }
    }
  }

  public final boolean hasDragger() {
    return myHasDragger;
  }

  public final void setDragger(final boolean hasDragger) {
    myHasDragger = hasDragger;
  }

  public boolean isResizing() {
    return myResizing;
  }

  public void setResizing(final boolean resizing) {
    myResizing = resizing;
  }

  public boolean isDragging() {
    return myDragging;
  }

  public void setDragging(final boolean dragging) {
    myDragging = dragging;
    RadContainer parent = getParent();
    if (parent != null) {
      parent.getLayoutManager().setChildDragging(this, dragging);
    }
  }

  public void setDragBorder(final boolean dragging) {
    myDragging = dragging;
    myDragBorder = dragging;
  }

  public boolean isDragBorder() {
    return myDragBorder;
  }

  public boolean isDefaultBinding() {
    return myDefaultBinding;
  }

  public void setDefaultBinding(final boolean defaultBinding) {
    myDefaultBinding = defaultBinding;
  }

  public final void addPropertyChangeListener(final PropertyChangeListener l) {
    final PropertyChangeListener[] propertyChangeListeners = myChangeSupport.getPropertyChangeListeners();
    for (PropertyChangeListener listener : propertyChangeListeners) {
      assert listener != l;
    }
    myChangeSupport.addPropertyChangeListener(l);
  }

  public final void removePropertyChangeListener(final PropertyChangeListener l) {
    myChangeSupport.removePropertyChangeListener(l);
  }

  protected final void firePropertyChanged(
    final @NotNull String propertyName,
    final Object oldValue,
    final Object newValue
  ) {
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /**
   * @return component's constarints.
   */
  @Override
  public final @NotNull GridConstraints getConstraints() {
    return myConstraints;
  }

  public final RadContainer getParent() {
    return myParent;
  }

  public final void setParent(final RadContainer parent) {
    myParent = parent;
  }

  public boolean isSelected() {
    return mySelected;
  }

  public void setSelected(final boolean selected) {
    if (mySelected != selected) {
      mySelected = selected;
      firePropertyChanged(PROP_SELECTED, !mySelected, mySelected);
      GuiEditor.repaintLayeredPane(this);
    }
  }

  /**
   * @see JComponent#getClientProperty(Object)
   */
  @Override
  public final Object getClientProperty(final @NotNull Object key) {
    return myDelegee.getClientProperty(key);
  }

  /**
   * @see JComponent#putClientProperty(Object, Object)
   */
  @Override
  public final void putClientProperty(final @NotNull Object key, final Object value) {
    myDelegee.putClientProperty(key, value);
  }

  public final int getX() {
    return myDelegee.getX();
  }

  public final int getY() {
    return myDelegee.getY();
  }

  public final void setLocation(final Point location) {
    myDelegee.setLocation(location);
  }

  public final void shift(final int dx, final int dy) {
    myDelegee.setLocation(myDelegee.getX() + dx, myDelegee.getY() + dy);
  }

  public final int getWidth() {
    return myDelegee.getWidth();
  }

  public final int getHeight() {
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

  public final Dimension getMinimumSize() {
    return Util.getMinimumSize(myDelegee, myConstraints, false);
  }

  public final Dimension getPreferredSize() {
    return Util.getPreferredSize(myDelegee, myConstraints, false);
  }

  public void refresh() {
  }

  public final void revalidate() {
    RadContainer theContainer = null;

    for (RadContainer container = this instanceof RadContainer ? (RadContainer)this : getParent();
         container != null;
         container = container.getParent()) {
      final RadContainer parent = container.getParent();
      if (parent != null && parent.isXY()) {
        final Dimension size = container.getSize();
        final Dimension minimumSize = container.getMinimumSize();
        if (size.width < minimumSize.width || size.height < minimumSize.height) {
          theContainer = container;
        }
      }
    }

    if (theContainer != null) {
      final Dimension minimumSize = theContainer.getMinimumSize();

      minimumSize.width = Math.max(minimumSize.width, theContainer.getWidth());
      minimumSize.height = Math.max(minimumSize.height, theContainer.getHeight());

      theContainer.getDelegee().setSize(minimumSize);
    }

    myDelegee.revalidate();
  }

  public final boolean isMarkedAsModified(final Property property) {
    return myModifiedPropertyNames.contains(property.getName());
  }

  public final void markPropertyAsModified(final Property property) {
    myModifiedPropertyNames.add(property.getName());
  }

  public final void removeModifiedProperty(final Property property) {
    myModifiedPropertyNames.remove(property.getName());
  }

  public RadComponent getComponentToDrag(final Point pnt) {
    return this;
  }

  public void processMouseEvent(final MouseEvent event) {
  }

  public @Nullable EventProcessor getEventProcessor(final MouseEvent event) {
    return null;
  }

  /**
   * Serializes component into the passed {@code writer}
   */
  public abstract void write(XmlWriter writer);

  /**
   * Serializes component's ID
   */
  protected final void writeId(final XmlWriter writer) {
    writer.addAttribute("id", getId());
  }

  /**
   * Serializes component's class
   */
  protected final void writeClass(final XmlWriter writer) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_CLASS, getComponentClass().getName());
  }

  protected final void writeClassIfDifferent(final XmlWriter writer, String defaultClassName) {
    if (!getComponentClassName().equals(defaultClassName)) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_CLASS, getComponentClass().getName());
    }
  }

  protected final void writeBinding(final XmlWriter writer) {
    // Binding
    if (getBinding() != null) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_BINDING, getBinding());
    }
    if (isCustomCreate()) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_CUSTOM_CREATE, Boolean.TRUE.toString());
    }
    if (isDefaultBinding()) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_DEFAULT_BINDING, Boolean.TRUE.toString());
    }
  }

  protected void writeConstraints(final XmlWriter writer) {
    writer.startElement("constraints");
    try {
      if (getParent() != null) {
        getParent().getLayoutManager().writeChildConstraints(writer, this);
      }
    }
    finally {
      writer.endElement(); // constraints
    }
  }

  protected final void writeProperties(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_PROPERTIES);
    try {
      final IntrospectedProperty[] introspectedProperties =
        getPalette().getIntrospectedProperties(this);
      for (final IntrospectedProperty property : introspectedProperties) {
        if (isMarkedAsModified(property)) {
          final Object value = property.getValue(this);
          if (value != null) {
            writer.startElement(property.getName());
            try {
              //noinspection unchecked
              property.write(value, writer);
            }
            finally {
              writer.endElement();
            }
          }
        }
      }
    }
    finally {
      writer.endElement(); // properties
    }
    writeClientProperties(writer);
  }

  private void writeClientProperties(final XmlWriter writer) {
    if (myModule == null) {
      return;
    }
    boolean haveClientProperties = false;
    try {
      ClientPropertiesProperty cpp = ClientPropertiesProperty.getInstance(getProject());
      for (Property prop : cpp.getChildren(this)) {
        ClientPropertyProperty clientProp = (ClientPropertyProperty)prop;
        final Object value = getDelegee().getClientProperty(clientProp.getName());
        if (value != null) {
          if (!haveClientProperties) {
            writer.startElement(UIFormXmlConstants.ELEMENT_CLIENT_PROPERTIES);
            haveClientProperties = true;
          }
          writer.startElement(clientProp.getName());
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_CLASS, value.getClass().getName());
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, value.toString());
          writer.endElement();
        }
      }
    }
    finally {
      if (haveClientProperties) {
        writer.endElement();
      }
    }
  }

  public void fireConstraintsChanged(GridConstraints oldConstraints) {
    firePropertyChanged(PROP_CONSTRAINTS, oldConstraints, myConstraints);
  }

  @Override
  public IProperty[] getModifiedProperties() {
    IntrospectedProperty[] props = getPalette().getIntrospectedProperties(this);
    ArrayList<IProperty> result = new ArrayList<>();
    for (IntrospectedProperty prop : props) {
      if (isMarkedAsModified(prop)) {
        result.add(prop);
      }
    }
    return result.toArray(new IProperty[0]);
  }

  @Override
  public IContainer getParentContainer() {
    return myParent;
  }

  public boolean hasIntrospectedProperties() {
    return true;
  }

  @Override
  public boolean accept(ComponentVisitor visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean areChildrenExclusive() {
    return false;
  }

  public void loadLwProperty(final LwComponent lwComponent,
                             final LwIntrospectedProperty lwProperty,
                             final IntrospectedProperty property) {
    myLoadingProperties = true;
    try {
      try {
        final Object value = lwComponent.getPropertyValue(lwProperty);
        //noinspection unchecked
        property.setValue(this, value);
      }
      catch (Exception e) {
        LOG.error(e);
        //TODO[anton,vova]: show error and continue to load form
      }
    }
    finally {
      myLoadingProperties = false;
    }
  }

  public void doneLoadingFromLw() {
  }

  public @Nullable
  @NlsSafe String getComponentTitle() {
    Palette palette = Palette.getInstance(getProject());
    IntrospectedProperty[] props = palette.getIntrospectedProperties(this);
    for (IntrospectedProperty prop : props) {
      if (prop.getName().equals(SwingProperties.TEXT) && prop instanceof IntroStringProperty) {
        StringDescriptor value = (StringDescriptor)prop.getValue(this);
        if (value != null) {
          return "\"" + value.getResolvedValue() + "\"";
        }
      }
    }

    if (this instanceof RadContainer container) {
      StringDescriptor descriptor = container.getBorderTitle();
      if (descriptor != null) {
        if (descriptor.getResolvedValue() == null) {
          descriptor.setResolvedValue(StringDescriptorManager.getInstance(getModule()).resolve(this, descriptor));
        }
        return "\"" + descriptor.getResolvedValue() + "\"";
      }
    }

    if (getParent() instanceof RadTabbedPane parentTabbedPane) {
      final StringDescriptor descriptor = parentTabbedPane.getChildTitle(this);
      if (descriptor != null) {
        if (descriptor.getResolvedValue() == null) {
          descriptor.setResolvedValue(StringDescriptorManager.getInstance(getModule()).resolve(this, descriptor));
        }
        return "\"" + descriptor.getResolvedValue() + "\"";
      }
      else {
        parentTabbedPane.getChildTitle(this);
      }
    }
    return null;
  }

  public String getDisplayName() {
    StringBuilder titleBuilder = new StringBuilder();
    if (getBinding() != null) {
      titleBuilder.append(getBinding());
    }
    else {
      final String className = getComponentClassName();
      int pos = className.lastIndexOf('.');
      if (pos < 0) {
        titleBuilder.append(className);
      }
      else {
        titleBuilder.append(className.substring(pos + 1).replace('$', '.'));
      }
      final String title = getComponentTitle();
      if (title != null) {
        titleBuilder.append(" ").append(title);
      }
    }
    return titleBuilder.toString();
  }
}
