package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.StringDescriptorManager;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.designSurface.EventProcessor;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.properties.ClientPropertiesProperty;
import com.intellij.uiDesigner.propertyInspector.properties.ClientPropertyProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class RadComponent implements IComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.radComponents.RadComponent");

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
  @NonNls public static final String PROP_SELECTED="selected";

  /**
   * Change notification for this property is fired when the constraints of a component
   * change.
   */
  @NonNls public static final String PROP_CONSTRAINTS = "constraints";

  /**
   * Component id is unique per RadRootContainer.
   */
  @NotNull private final String myId;
  /**
   * @see #getBinding()
   */
  private String myBinding;
  private boolean myCustomCreate = false;
  private boolean myLoadingProperties = false;

  @NotNull private final Module myModule;
  @NotNull private final Class myClass;
  /**
   * Delegee is the JComponent which really represents the
   * component in UI.
   */
  @NotNull private final JComponent myDelegee;
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

  @NotNull private final GridConstraints myConstraints;

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
   * Creates new <code>RadComponent</code> with the specified
   * class of delegee and specified ID.
   *
   * @param aClass class of the compoent's delegee
   * @param id id of the compoent inside the form. <code>id</code>
   * should be a unique atring inside the form.
   */
  public RadComponent(final Module module, @NotNull final Class aClass, @NotNull final String id) {
    myModule = module;
    myClass = aClass;
    myId = id;

    myChangeSupport = new PropertyChangeSupport(this);
    myConstraints = new GridConstraints();
    myModifiedPropertyNames = new HashSet<String>();

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
      myDelegee = (JComponent)constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    myDelegee.putClientProperty(CLIENT_PROP_RAD_COMPONENT, this);
  }

  public RadComponent(final Module module, @NotNull final Class aClass, @NotNull final String id, final Palette palette) {
    this(module, aClass, id);
    myPalette = palette;
  }

  /**
   * @return module for the component.
   */
  public final Module getModule() {
    return myModule;
  }

  public boolean isLoadingProperties() {
    return myLoadingProperties;
  }

  @NotNull public final Project getProject() {
    return myModule.getProject();
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
  public void init(final GuiEditor editor, @NotNull final ComponentItem item) {
    initDefaultProperties(item);
  }

  public void initDefaultProperties(@NotNull final ComponentItem item) {
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
  @NotNull
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
   * @return Swing delegee component. The <code>RadComponent</code> has the same
   * delegee during all its life.
   */
  @NotNull
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
  @Nullable
  public Property getInplaceProperty(final int x, final int y){
    return getDefaultInplaceProperty();
  }

  @Nullable
  public Property getDefaultInplaceProperty() {
    return getPalette().getInplaceProperty(this);
  }

  @Nullable
  public Rectangle getDefaultInplaceEditorBounds() {
    return null;
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
  @Nullable
  public Rectangle getInplaceEditorBounds(@NotNull final Property property, final int x, final int y){
    return null;
  }

  @NotNull
  public final Class getComponentClass(){
    return myClass;
  }

  @NotNull
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
    getDelegee().setVisible(!myDragging);
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

  public final void addPropertyChangeListener(final PropertyChangeListener l){
    final PropertyChangeListener[] propertyChangeListeners = myChangeSupport.getPropertyChangeListeners();
    for(PropertyChangeListener listener: propertyChangeListeners) {
      assert listener != l;
    }
    myChangeSupport.addPropertyChangeListener(l);
  }

  public final void removePropertyChangeListener(final PropertyChangeListener l){
    myChangeSupport.removePropertyChangeListener(l);
  }

  protected final void firePropertyChanged(
    @NotNull final String propertyName,
    final Object oldValue,
    final Object newValue
  ){
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /**
   * @return component's constarints.
   */
  @NotNull
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
      firePropertyChanged(PROP_SELECTED, !mySelected, mySelected);
      GuiEditor.repaintLayeredPane(this);
    }
  }

  /**
   * @see JComponent#getClientProperty(Object)
   */
  public final Object getClientProperty(@NotNull final Object key){
    return myDelegee.getClientProperty(key);
  }

  /**
   * @see JComponent#putClientProperty(Object, Object)
   */
  public final void putClientProperty(@NotNull final Object key, final Object value){
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
    return Util.getMinimumSize(myDelegee, myConstraints, false);
  }

  public final Dimension getPreferredSize(){
    return Util.getPreferredSize(myDelegee, myConstraints, false);
  }

  public final void revalidate() {
    RadContainer theContainer = null;

    for (RadContainer container = this instanceof RadContainer ? (RadContainer)this : getParent(); container != null; container = container.getParent()) {
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

  public void processMouseEvent(final MouseEvent event) {}

  @Nullable
  public EventProcessor getEventProcessor(final MouseEvent event) {
    return null;
  }

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
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_BINDING, getBinding());
    }
    if (isCustomCreate()) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_CUSTOM_CREATE, Boolean.TRUE.toString());
    }
  }

  protected void writeConstraints(final XmlWriter writer){
    writer.startElement("constraints");
    try {
      if (getParent() != null) {
        getParent().getLayoutManager().writeChildConstraints(writer, this);
      }
    } finally {
      writer.endElement(); // constraints
    }
  }

  protected final void writeProperties(final XmlWriter writer){
    writer.startElement("properties");
    try{
      final IntrospectedProperty[] introspectedProperties =
        getPalette().getIntrospectedProperties(this);
      for(final IntrospectedProperty property : introspectedProperties) {
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
    }finally{
      writer.endElement(); // properties
    }
    writeClientProperties(writer);
  }

  private void writeClientProperties(final XmlWriter writer) {
    if (myModule == null) return;
    boolean haveClientProperties = false;
    try {
      ClientPropertiesProperty cpp = ClientPropertiesProperty.getInstance(getProject());
      for(Property prop: cpp.getChildren(this)) {
        ClientPropertyProperty clientProp = (ClientPropertyProperty) prop;
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

  public IProperty[] getModifiedProperties() {
    IntrospectedProperty[] props = getPalette().getIntrospectedProperties(this);
    ArrayList<IProperty> result = new ArrayList<IProperty>();
    for(IntrospectedProperty prop: props) {
      if (isMarkedAsModified(prop)) {
        result.add(prop);
      }
    }
    return result.toArray(new IProperty[result.size()]);
  }

  public IContainer getParentContainer() {
    return myParent;
  }

  public boolean hasIntrospectedProperties() {
    return true;
  }

  public boolean accept(ComponentVisitor visitor) {
    return visitor.visit(this);
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

  @Nullable
  public static RadComponent createSnapshotComponent(final SnapshotContext context, final JComponent component) {
    String id = context.newId();
    RadComponent result;

    Class componentClass = component.getClass();
    if (componentClass.isAnonymousClass()) {
      componentClass = componentClass.getSuperclass();
    }
    if (component instanceof JPanel) {
      RadContainer container = new RadContainer(componentClass, id, context.getPalette());
      final RadLayoutManager manager = LayoutManagerRegistry.createFromLayout(component.getLayout());
      if (manager == null) {
        return null;
      }
      container.setLayoutManager(manager);
      result = container;
    }
    else {
      final Class<? extends RadComponent> radClass = InsertComponentProcessor.getRadComponentClass(componentClass.getName());
      if (radClass == null) {
        result = new RadAtomicComponent(componentClass, id, context.getPalette());
      }
      else {
        try {
          result = radClass.getConstructor(String.class, Palette.class).newInstance(id, context.getPalette());
        }
        catch(Exception ex) {
          throw new RuntimeException(ex);
        }
      }
    }

    context.registerComponent(component, result);
    result.importSnapshotComponent(context, component);

    final IntrospectedProperty[] properties = context.getPalette().getIntrospectedProperties(component.getClass(),
                                                                                             result.getDelegee().getClass());
    for(IntrospectedProperty prop: properties) {
      prop.importSnapshotValue(context, component, result);
    }

    if (component instanceof AbstractButton) {
      AbstractButton btn = (AbstractButton) component;
      if (btn.getModel() instanceof DefaultButtonModel) {
        DefaultButtonModel model = (DefaultButtonModel) btn.getModel();
        if (model.getGroup() != null) {
          context.registerButtonGroup(model.getGroup());
        }
      }
    }

    return result;
  }

  protected void importSnapshotComponent(final SnapshotContext context, final JComponent component) {
  }

  @Nullable
  public String getComponentTitle() {
    Palette palette = Palette.getInstance(getModule().getProject());
    IntrospectedProperty[] props = palette.getIntrospectedProperties(this);
    for(IntrospectedProperty prop: props) {
      if (prop.getName().equals(SwingProperties.TEXT) && prop instanceof IntroStringProperty) {
        StringDescriptor value = (StringDescriptor) prop.getValue(this);
        if (value != null) {
          return "\"" + value.getResolvedValue() + "\"";
        }
      }
    }

    if (this instanceof RadContainer) {
      RadContainer container = (RadContainer)this;
      StringDescriptor descriptor = container.getBorderTitle();
      if (descriptor != null) {
        if (descriptor.getResolvedValue() == null) {
          descriptor.setResolvedValue(StringDescriptorManager.getInstance(getModule()).resolve(this, descriptor));
        }
        return "\"" + descriptor.getResolvedValue() + "\"";
      }
    }

    if (getParent() instanceof RadTabbedPane) {
      RadTabbedPane parentTabbedPane = (RadTabbedPane) getParent();
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
