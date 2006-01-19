package com.intellij.uiDesigner.palette;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.LwXmlReader;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEnumEditor;
import com.intellij.uiDesigner.propertyInspector.properties.*;
import com.intellij.uiDesigner.propertyInspector.renderers.IntEnumRenderer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class Palette implements ProjectComponent, JDOMExternalizable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.palette.Palette");

  private final MyLafManagerListener myLafManagerListener;
  private final HashMap<Class, IntrospectedProperty[]> myClass2Properties;
  private final HashMap<String, ComponentItem> myClassName2Item;
  /*All groups in the palette*/
  private final ArrayList<GroupItem> myGroups;
  /*Listeners, etc*/
  private final ArrayList<Listener> myListeners;
  private Project myProject;

  /**
   * Predefined item for javax.swing.JPanel
   */
  private ComponentItem myPanelItem;
  @NonNls private static final String ATTRIBUTE_VSIZE_POLICY = "vsize-policy";
  @NonNls private static final String ATTRIBUTE_HSIZE_POLICY = "hsize-policy";
  @NonNls private static final String ATTRIBUTE_ANCHOR = "anchor";
  @NonNls private static final String ATTRIBUTE_FILL = "fill";
  @NonNls private static final String ELEMENT_MINIMUM_SIZE = "minimum-size";
  @NonNls private static final String ATTRIBUTE_WIDTH = "width";
  @NonNls private static final String ATTRIBUTE_HEIGHT = "height";
  @NonNls private static final String ELEMENT_PREFERRED_SIZE = "preferred-size";
  @NonNls private static final String ELEMENT_MAXIMUM_SIZE = "maximum-size";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";
  @NonNls private static final String ATTRIBUTE_ICON = "icon";
  @NonNls private static final String ATTRIBUTE_TOOLTIP_TEXT = "tooltip-text";
  @NonNls private static final String ELEMENT_DEFAULT_CONSTRAINTS = "default-constraints";
  @NonNls private static final String ELEMENT_INITIAL_VALUES = "initial-values";
  @NonNls private static final String ELEMENT_PROPERTY = "property";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";
  @NonNls private static final String ATTRIBUTE_REMOVABLE = "removable";
  @NonNls private static final String ELEMENT_ITEM = "item";
  @NonNls private static final String ELEMENT_GROUP = "group";

  public static Palette getInstance(@NotNull final Project project) {
    return project.getComponent(Palette.class);
  }

  /** Invoked by reflection */
  private Palette(Project project) {
    myProject = project;
    myLafManagerListener = new MyLafManagerListener();
    myClass2Properties = new HashMap<Class, IntrospectedProperty[]>();
    myClassName2Item = new HashMap<String, ComponentItem>();
    myGroups = new ArrayList<GroupItem>();
    myListeners = new ArrayList<Listener>();
  }

  /**Adds specified listener.*/
  public void addListener(@NotNull final Listener l){
    LOG.assertTrue(!myListeners.contains(l));
    myListeners.add(l);
  }

  /**Removes specified listener.*/
  public void removeListener(@NotNull final Listener l){
    LOG.assertTrue(myListeners.contains(l));
    myListeners.remove(l);
  }

  void fireGroupsChanged() {
    final Listener[] listeners = myListeners.toArray(new Listener[myListeners.size()]);
    for(Listener listener : listeners) {
      listener.groupsChanged(this);
    }
  }

  public String getComponentName(){
    return "Palette2";
  }

  public void projectOpened() {
    LafManager.getInstance().addLafManagerListener(myLafManagerListener);
  }

  public void projectClosed() {
    LafManager.getInstance().removeLafManagerListener(myLafManagerListener);
  }

  public void readExternal(@NotNull final Element element) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    // It seems that IDEA inokes readExternal twice: first time for node in defaults XML
    // the second time for node in project file. Stupidity... :(
    myClass2Properties.clear();
    myClassName2Item.clear();
    myGroups.clear();

    // Parse XML
    //noinspection HardCodedStringLiteral
    final List groupElements = element.getChildren(ELEMENT_GROUP);
    processGroups(groupElements);

    // Ensure that all predefined items are loaded
    LOG.assertTrue(myPanelItem != null);
  }

  public void writeExternal(@NotNull final Element element) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    writeGroups(element);
  }

  public void initComponent() {}

  public void disposeComponent() {}

  /**
   * @return a predefined palette item which corresponds to the JPanel.
   */
  @NotNull
  public ComponentItem getPanelItem(){
    return myPanelItem;
  }

  /**
   * @return <code>ComponentItem</code> for the UI bean with the specified <code>componentClassName</code>.
   * The method returns <code>null</code> if palette has no information about the specified
   * class.
   */
  @Nullable
  public ComponentItem getItem(@NotNull final String componentClassName) {
    return myClassName2Item.get(componentClassName);
  }

  /**
   * @return read-only list of all groups in the palette.
   * <em>DO NOT MODIFY OR CACHE THIS LIST</em>.
   */
  public ArrayList<GroupItem> getGroups(){
    return myGroups;
  }

  /**
   * @param groups list of new groups.
   */
  public void setGroups(@NotNull final ArrayList<GroupItem> groups){
    myGroups.clear();
    myGroups.addAll(groups);

    fireGroupsChanged();
  }

  /**
   * Adds specified <code>item</code> to the palette.
   * @param item item to be added
   * @exception java.lang.IllegalArgumentException  if an item for the same class
   * is already exists in the palette
   */
  private void addItem(@NotNull final GroupItem group, @NotNull final ComponentItem item) {
    // class -> item
    final String componentClassName = item.getClassName();
    if (getItem(componentClassName) != null) {
      Messages.showMessageDialog(
        UIDesignerBundle.message("error.item.already.added", componentClassName),
        ApplicationNamesInfo.getInstance().getFullProductName(),
        Messages.getErrorIcon()
      );
      return;
    }
    myClassName2Item.put(componentClassName, item);

    // group -> items
    group.addItem(item);

    // Process special predefined item for JPanel
    if("javax.swing.JPanel".equals(item.getClassName())){
      myPanelItem = item;
    }
  }

  /**
   * Helper method.
   */
  private static GridConstraints processDefaultConstraintsElement(@NotNull final Element element){
    final GridConstraints constraints = new GridConstraints();

    // grid related attributes
    constraints.setVSizePolicy(LwXmlReader.getRequiredInt(element, ATTRIBUTE_VSIZE_POLICY));
    constraints.setHSizePolicy(LwXmlReader.getRequiredInt(element, ATTRIBUTE_HSIZE_POLICY));
    constraints.setAnchor(LwXmlReader.getRequiredInt(element, ATTRIBUTE_ANCHOR));
    constraints.setFill(LwXmlReader.getRequiredInt(element, ATTRIBUTE_FILL));

    // minimum size
    final Element minSizeElement = element.getChild(ELEMENT_MINIMUM_SIZE);
    if (minSizeElement != null) {
      constraints.myMinimumSize.width = LwXmlReader.getRequiredInt(minSizeElement, ATTRIBUTE_WIDTH);
      constraints.myMinimumSize.height = LwXmlReader.getRequiredInt(minSizeElement, ATTRIBUTE_HEIGHT);
    }

    // preferred size
    final Element prefSizeElement = element.getChild(ELEMENT_PREFERRED_SIZE);
    if (prefSizeElement != null){
      constraints.myPreferredSize.width = LwXmlReader.getRequiredInt(prefSizeElement, ATTRIBUTE_WIDTH);
      constraints.myPreferredSize.height = LwXmlReader.getRequiredInt(prefSizeElement, ATTRIBUTE_HEIGHT);
    }

    // maximum size
    final Element maxSizeElement = element.getChild(ELEMENT_MAXIMUM_SIZE);
    if (maxSizeElement != null) {
      constraints.myMaximumSize.width = LwXmlReader.getRequiredInt(maxSizeElement, ATTRIBUTE_WIDTH);
      constraints.myMaximumSize.height = LwXmlReader.getRequiredInt(maxSizeElement, ATTRIBUTE_HEIGHT);
    }

    return constraints;
  }

  private void processItemElement(@NotNull final Element itemElement, @NotNull final GroupItem group){
    // Class name. It's OK if class does not exist.
    final String className = LwXmlReader.getRequiredString(itemElement, ATTRIBUTE_CLASS);

    // Icon (optional)
    final String iconPath = LwXmlReader.getString(itemElement, ATTRIBUTE_ICON);

    // Tooltip text (optional)
    final String toolTipText = LwXmlReader.getString(itemElement, ATTRIBUTE_TOOLTIP_TEXT); // can be null

    // Default constraint
    final GridConstraints constraints;
    final Element defaultConstraints = itemElement.getChild(ELEMENT_DEFAULT_CONSTRAINTS);
    if (defaultConstraints != null) {
      constraints = processDefaultConstraintsElement(defaultConstraints);
    }
    else {
      constraints = new GridConstraints();
    }

    final HashMap<String, StringDescriptor> propertyName2intitialValue = new HashMap<String, StringDescriptor>();
    {
      final Element initialValues = itemElement.getChild(ELEMENT_INITIAL_VALUES);
      if (initialValues != null){
        for(final Object o : initialValues.getChildren(ELEMENT_PROPERTY)) {
          final Element e = (Element)o;
          final String name = LwXmlReader.getRequiredString(e, ATTRIBUTE_NAME);
          // TODO[all] currently all initial values are strings
          final StringDescriptor value = StringDescriptor.create(LwXmlReader.getRequiredString(e, ATTRIBUTE_VALUE));
          propertyName2intitialValue.put(name, value);
        }
      }
    }

    final boolean removable = LwXmlReader.getOptionalBoolean(itemElement, ATTRIBUTE_REMOVABLE, true);

    final ComponentItem item = new ComponentItem(
      className,
      iconPath,
      toolTipText,
      constraints,
      propertyName2intitialValue,
      removable
    );
    addItem(group, item);
  }

  /**
   * Reads PaletteElements from
   */
  private void processGroups(final List groupElements){
    for(final Object groupElement1 : groupElements) {
      final Element groupElement = (Element)groupElement1;
      final String groupName = LwXmlReader.getRequiredString(groupElement, ATTRIBUTE_NAME);
      final GroupItem group = new GroupItem(groupName);
      myGroups.add(group);
      for (final Object o : groupElement.getChildren(ELEMENT_ITEM)) {
        final Element itemElement = (Element)o;
        try {
          processItemElement(itemElement, group);
        }
        catch (Exception ex) {
          LOG.error(ex);
        }
      }
    }
  }

  /** Helper method */
  private static void writeDefaultConstraintsElement(@NotNull final Element itemElement, @NotNull final GridConstraints c){
    LOG.assertTrue(ELEMENT_ITEM.equals(itemElement.getName()));

    final Element element = new Element(ELEMENT_DEFAULT_CONSTRAINTS);
    itemElement.addContent(element);

    // grid related attributes
    {
      element.setAttribute(ATTRIBUTE_VSIZE_POLICY, Integer.toString(c.getVSizePolicy()));
      element.setAttribute(ATTRIBUTE_HSIZE_POLICY, Integer.toString(c.getHSizePolicy()));
      element.setAttribute(ATTRIBUTE_ANCHOR, Integer.toString(c.getAnchor()));
      element.setAttribute(ATTRIBUTE_FILL, Integer.toString(c.getFill()));
    }

    // minimum size
    {
      if (c.myMinimumSize.width != -1 || c.myMinimumSize.height != -1) {
        final Element _element = new Element(ELEMENT_MINIMUM_SIZE);
        element.addContent(_element);
        _element.setAttribute(ATTRIBUTE_WIDTH, Integer.toString(c.myMinimumSize.width));
        _element.setAttribute(ATTRIBUTE_HEIGHT, Integer.toString(c.myMinimumSize.height));
      }
    }

    // preferred size
    {
      if (c.myPreferredSize.width != -1 || c.myPreferredSize.height != -1) {
        final Element _element = new Element(ELEMENT_PREFERRED_SIZE);
        element.addContent(_element);
        _element.setAttribute(ATTRIBUTE_WIDTH, Integer.toString(c.myPreferredSize.width));
        _element.setAttribute(ATTRIBUTE_HEIGHT, Integer.toString(c.myPreferredSize.height));
      }
    }

    // maximum size
    {
      if (c.myMaximumSize.width != -1 || c.myMaximumSize.height != -1) {
        final Element _element = new Element(ELEMENT_MAXIMUM_SIZE);
        element.addContent(_element);
        _element.setAttribute(ATTRIBUTE_WIDTH, Integer.toString(c.myMaximumSize.width));
        _element.setAttribute(ATTRIBUTE_HEIGHT, Integer.toString(c.myMaximumSize.height));
      }
    }
  }

  /** Helper method */
  private static void writeInitialValuesElement(
    final Element itemElement,
    final HashMap<String, StringDescriptor> name2value
  ){
    LOG.assertTrue(itemElement != null);
    LOG.assertTrue(ELEMENT_ITEM.equals(itemElement.getName()));
    LOG.assertTrue(name2value != null);

    if(name2value.size() == 0){ // do not append 'initial-values' subtag
      return;
    }

    final Element initialValuesElement = new Element(ELEMENT_INITIAL_VALUES);
    itemElement.addContent(initialValuesElement);

    for(final Iterator<Map.Entry<String,StringDescriptor>> i = name2value.entrySet().iterator(); i.hasNext();){
      final Map.Entry<String,StringDescriptor> entry = i.next();
      final Element propertyElement = new Element(ELEMENT_PROPERTY);
      initialValuesElement.addContent(propertyElement);
      propertyElement.setAttribute(ATTRIBUTE_NAME, entry.getKey());
      propertyElement.setAttribute(ATTRIBUTE_VALUE, entry.getValue().getValue()/*descriptor is always trivial*/);
    }
  }

  /** Helper method */
  private static void writeComponentItem(@NotNull final Element groupElement, @NotNull final ComponentItem item){
    LOG.assertTrue(ELEMENT_GROUP.equals(groupElement.getName()));

    final Element itemElement = new Element(ELEMENT_ITEM);
    groupElement.addContent(itemElement);

    // Class
    itemElement.setAttribute(ATTRIBUTE_CLASS, item.getClassName());

    // Tooltip text (if any)
    if(item.myToolTipText != null){
      itemElement.setAttribute(ATTRIBUTE_TOOLTIP_TEXT, item.myToolTipText);
    }

    // Icon (if any)
    final String iconPath = item.getIconPath();
    if(iconPath != null){
      itemElement.setAttribute(ATTRIBUTE_ICON, iconPath);
    }

    // Removable
    itemElement.setAttribute(ATTRIBUTE_REMOVABLE, Boolean.toString(item.isRemovable()));

    // Default constraints
    writeDefaultConstraintsElement(itemElement, item.getDefaultConstraints());

    // Initial values (if any)
    writeInitialValuesElement(itemElement, item.getInitialValues());
  }

  /**
   * @param parentElement element to which all "group" elements will be appended
   */
  private void writeGroups(@NotNull final Element parentElement){
    for (final GroupItem group : myGroups) {
      final Element groupElement = new Element(ELEMENT_GROUP);
      parentElement.addContent(groupElement);
      groupElement.setAttribute(ATTRIBUTE_NAME, group.getName());

      final ArrayList<ComponentItem> itemList = group.getItems();
      for (ComponentItem aItemList : itemList) {
        writeComponentItem(groupElement, aItemList);
      }
    }
  }

  /**
   * Helper method
   */
  private static IntroIntProperty createIntEnumProperty(
    final String name,
    final Method readMethod,
    final Method writeMethod,
    final IntEnumEditor.Pair[] pairs
  ){
    return new IntroIntProperty(
      name,
      readMethod,
      writeMethod,
      new IntEnumRenderer(pairs),
      new IntEnumEditor(pairs)
    );
  }

  /**
   * @return arrys of all properties that can be introspected from the
   * specified class. Only properties with getter and setter methods are
   * returned.
   */
  @NotNull
  public IntrospectedProperty[] getIntrospectedProperties(@NotNull final Class aClass){
    // Try the cache first
    // TODO[vova, anton] update cache after class reloading (its properties caould be hanged).
    if (myClass2Properties.containsKey(aClass)) {
      return myClass2Properties.get(aClass);
    }

    final ArrayList<IntrospectedProperty> result = new ArrayList<IntrospectedProperty>();
    try {
      final BeanInfo beanInfo = Introspector.getBeanInfo(aClass);
      final PropertyDescriptor[] descriptors = beanInfo.getPropertyDescriptors();
      for (int i = 0; i < descriptors.length; i++) {
        final PropertyDescriptor descriptor = descriptors[i];

        final Method readMethod = descriptor.getReadMethod();
        final Method writeMethod = descriptor.getWriteMethod();
        if (writeMethod == null || readMethod == null) {
          continue;
        }

        final String name = descriptor.getName();

        //noinspection HardCodedStringLiteral
        if (
          name.equals("preferredSize") ||
          name.equals("minimumSize") ||
          name.equals("maximumSize")
        ){
          // our own properties must be used instead
          continue;
        }

        final IntrospectedProperty property;

        final Class propertyType = descriptor.getPropertyType();
        if (int.class.equals(propertyType)) { // int
          //noinspection HardCodedStringLiteral
          if(
            JSplitPane.class.isAssignableFrom(aClass) &&
            "orientation".equals(name)
          ){ // JSplitPane#orientation
            final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
              new IntEnumEditor.Pair(JSplitPane.HORIZONTAL_SPLIT, UIDesignerBundle.message("property.horizontal")),
              new IntEnumEditor.Pair(JSplitPane.VERTICAL_SPLIT, UIDesignerBundle.message("property.vertical"))
            };
            property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
          }
          else if (JScrollPane.class.isAssignableFrom(aClass)) {
            //noinspection HardCodedStringLiteral
            if("horizontalScrollBarPolicy".equals(name)){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS, UIDesignerBundle.message("property.always")),
                new IntEnumEditor.Pair(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, UIDesignerBundle.message("property.as.needed")),
                new IntEnumEditor.Pair(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, UIDesignerBundle.message("property.never"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else
            //noinspection HardCodedStringLiteral
            if("verticalScrollBarPolicy".equals(name)){
                final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                  new IntEnumEditor.Pair(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, UIDesignerBundle.message("property.always")),
                  new IntEnumEditor.Pair(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, UIDesignerBundle.message("property.as.needed")),
                  new IntEnumEditor.Pair(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, UIDesignerBundle.message("property.never"))
                };
                property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
              }
              else{
                property = new IntroIntProperty(name, readMethod, writeMethod);
              }
          }
          else if(JTabbedPane.class.isAssignableFrom(aClass)){ // special handling for javax.swing.JTabbedPane
            //noinspection HardCodedStringLiteral
            if("tabLayoutPolicy".equals(name)){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(JTabbedPane.WRAP_TAB_LAYOUT, UIDesignerBundle.message("property.wrap")),
                new IntEnumEditor.Pair(JTabbedPane.SCROLL_TAB_LAYOUT, UIDesignerBundle.message("property.scroll"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else
            // noinspection HardCodedStringLiteral
            if("tabPlacement".equals(name)){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(JTabbedPane.TOP, UIDesignerBundle.message("property.top")),
                new IntEnumEditor.Pair(JTabbedPane.LEFT, UIDesignerBundle.message("property.left")),
                new IntEnumEditor.Pair(JTabbedPane.BOTTOM, UIDesignerBundle.message("property.bottom")),
                new IntEnumEditor.Pair(JTabbedPane.RIGHT, UIDesignerBundle.message("property.right"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else{
              property = new IntroIntProperty(name, readMethod, writeMethod);
            }
          }
          else if(JLabel.class.isAssignableFrom(aClass)){ // special handling for javax.swing.JLabel
            //noinspection HardCodedStringLiteral
            if(
              JLabel.class.isAssignableFrom(aClass) &&
              ("displayedMnemonic".equals(name) || "displayedMnemonicIndex".equals(name))
            ){ // skip JLabel#displayedMnemonic and JLabel#displayedMnemonicIndex
              continue;
            }
            else
            //noinspection HardCodedStringLiteral
            if("horizontalAlignment".equals(name)){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(JLabel.LEFT, UIDesignerBundle.message("property.left")),
                new IntEnumEditor.Pair(JLabel.CENTER, UIDesignerBundle.message("property.center")),
                new IntEnumEditor.Pair(JLabel.RIGHT, UIDesignerBundle.message("property.right")),
                new IntEnumEditor.Pair(JLabel.LEADING, UIDesignerBundle.message("property.leading")),
                new IntEnumEditor.Pair(JLabel.TRAILING, UIDesignerBundle.message("property.trailing"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else
            //noinspection HardCodedStringLiteral
            if("horizontalTextPosition".equals(name)){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(JLabel.LEFT, UIDesignerBundle.message("property.left")),
                new IntEnumEditor.Pair(JLabel.CENTER, UIDesignerBundle.message("property.center")),
                new IntEnumEditor.Pair(JLabel.RIGHT, UIDesignerBundle.message("property.right")),
                new IntEnumEditor.Pair(JLabel.LEADING, UIDesignerBundle.message("property.leading")),
                new IntEnumEditor.Pair(JLabel.TRAILING, UIDesignerBundle.message("property.trailing"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else
            //noinspection HardCodedStringLiteral
            if("verticalAlignment".equals(name)){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(JLabel.TOP, UIDesignerBundle.message("property.top")),
                new IntEnumEditor.Pair(JLabel.CENTER, UIDesignerBundle.message("property.center")),
                new IntEnumEditor.Pair(JLabel.BOTTOM, UIDesignerBundle.message("property.bottom"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else
            //noinspection HardCodedStringLiteral
            if("verticalTextPosition".equals(name)){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(JLabel.TOP, UIDesignerBundle.message("property.top")),
                new IntEnumEditor.Pair(JLabel.CENTER, UIDesignerBundle.message("property.center")),
                new IntEnumEditor.Pair(JLabel.BOTTOM, UIDesignerBundle.message("property.bottom"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else{
              property = new IntroIntProperty(name, readMethod, writeMethod);
            }
          }
          else if(AbstractButton.class.isAssignableFrom(aClass)) {  // special handling AbstractButton subclasses
              //noinspection HardCodedStringLiteral
              if (
              "mnemonic".equals(name) || "displayedMnemonicIndex".equals(name)
              ) { // AbstractButton#mnemonic
              continue;
            }
            else
              //noinspection HardCodedStringLiteral
              if ("horizontalAlignment".equals(name)) {
                final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                  new IntEnumEditor.Pair(SwingConstants.LEFT, UIDesignerBundle.message("property.left")),
                  new IntEnumEditor.Pair(SwingConstants.CENTER, UIDesignerBundle.message("property.center")),
                  new IntEnumEditor.Pair(SwingConstants.RIGHT, UIDesignerBundle.message("property.right")),
                  new IntEnumEditor.Pair(SwingConstants.LEADING, UIDesignerBundle.message("property.leading")),
                  new IntEnumEditor.Pair(SwingConstants.TRAILING, UIDesignerBundle.message("property.trailing"))
                };
                property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
              }
              else
                //noinspection HardCodedStringLiteral
                if ("horizontalTextPosition".equals(name)) {
                  final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                    new IntEnumEditor.Pair(SwingConstants.LEFT, UIDesignerBundle.message("property.left")),
                    new IntEnumEditor.Pair(SwingConstants.CENTER, UIDesignerBundle.message("property.center")),
                    new IntEnumEditor.Pair(SwingConstants.RIGHT, UIDesignerBundle.message("property.right")),
                    new IntEnumEditor.Pair(SwingConstants.LEADING, UIDesignerBundle.message("property.leading")),
                    new IntEnumEditor.Pair(SwingConstants.TRAILING, UIDesignerBundle.message("property.trailing"))
                  };
                  property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
                }
                else
                  //noinspection HardCodedStringLiteral
                  if ("verticalAlignment".equals(name)) {
                    final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                      new IntEnumEditor.Pair(SwingConstants.TOP, UIDesignerBundle.message("property.top")),
                      new IntEnumEditor.Pair(SwingConstants.CENTER, UIDesignerBundle.message("property.center")),
                      new IntEnumEditor.Pair(SwingConstants.BOTTOM, UIDesignerBundle.message("property.bottom"))
                    };
                    property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
                  }
                  else
                    //noinspection HardCodedStringLiteral
                    if ("verticalTextPosition".equals(name)) {
                      final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                        new IntEnumEditor.Pair(SwingConstants.TOP, UIDesignerBundle.message("property.top")),
                        new IntEnumEditor.Pair(SwingConstants.CENTER, UIDesignerBundle.message("property.center")),
                        new IntEnumEditor.Pair(SwingConstants.BOTTOM, UIDesignerBundle.message("property.bottom"))
                      };
                      property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
                    }
                    else {
                      property = new IntroIntProperty(name, readMethod, writeMethod);
                    }
            }
            else
            //noinspection HardCodedStringLiteral
            if(
              JTextField.class.isAssignableFrom(aClass) &&
              "horizontalAlignment".equals(name)
            ){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(SwingConstants.LEFT, UIDesignerBundle.message("property.left")),
                new IntEnumEditor.Pair(SwingConstants.CENTER, UIDesignerBundle.message("property.center")),
                new IntEnumEditor.Pair(SwingConstants.RIGHT, UIDesignerBundle.message("property.right")),
                new IntEnumEditor.Pair(SwingConstants.LEADING, UIDesignerBundle.message("property.leading")),
                new IntEnumEditor.Pair(SwingConstants.TRAILING, UIDesignerBundle.message("property.trailing"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else if(
              JList.class.isAssignableFrom(aClass)
            ){
              //noinspection HardCodedStringLiteral
              if ("layoutOrientation".equals(name)) {
                final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                  new IntEnumEditor.Pair(JList.VERTICAL, UIDesignerBundle.message("property.vertical")),
                  new IntEnumEditor.Pair(JList.HORIZONTAL_WRAP, UIDesignerBundle.message("property.horizontal.wrap")),
                  new IntEnumEditor.Pair(JList.VERTICAL_WRAP, UIDesignerBundle.message("property.vertical.wrap"))
                };
                property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
              }
              else
              //noinspection HardCodedStringLiteral
              if ("selectionMode".equals(name)) {
                final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                  new IntEnumEditor.Pair(ListSelectionModel.SINGLE_SELECTION, UIDesignerBundle.message("property.selection.single")),
                  new IntEnumEditor.Pair(ListSelectionModel.SINGLE_INTERVAL_SELECTION, UIDesignerBundle.message("property.selection.single.interval")),
                  new IntEnumEditor.Pair(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, UIDesignerBundle.message("property.selection.multiple.interval"))
                };
                property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
              }
              else {
                property = new IntroIntProperty(name, readMethod, writeMethod);
              }
            }
            else
            //noinspection HardCodedStringLiteral
            if(
              JTable.class.isAssignableFrom(aClass) &&
              "autoResizeMode".equals(name)
            ){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(JTable.AUTO_RESIZE_OFF, UIDesignerBundle.message("property.resize.off")),
                new IntEnumEditor.Pair(JTable.AUTO_RESIZE_NEXT_COLUMN, UIDesignerBundle.message("property.resize.next.column")),
                new IntEnumEditor.Pair(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS, UIDesignerBundle.message("property.resize.subsequent.columns")),
                new IntEnumEditor.Pair(JTable.AUTO_RESIZE_LAST_COLUMN, UIDesignerBundle.message("property.resize.last.column")),
                new IntEnumEditor.Pair(JTable.AUTO_RESIZE_ALL_COLUMNS, UIDesignerBundle.message("property.resize.all.columns"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else
            //noinspection HardCodedStringLiteral
            if(
              JSlider.class.isAssignableFrom(aClass) &&
              "orientation".equals(name)
            ){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(JSlider.HORIZONTAL, UIDesignerBundle.message("property.horizontal")),
                new IntEnumEditor.Pair(JSlider.VERTICAL, UIDesignerBundle.message("property.vertical"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else
            //noinspection HardCodedStringLiteral
            if(
              JFormattedTextField.class.isAssignableFrom(aClass) &&
              "focusLostBehavior".equals(name)
            ){
              final IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[]{
                new IntEnumEditor.Pair(JFormattedTextField.COMMIT, UIDesignerBundle.message("property.focuslost.commit")),
                new IntEnumEditor.Pair(JFormattedTextField.COMMIT_OR_REVERT, UIDesignerBundle.message("property.focuslost.commit.or.revert")),
                new IntEnumEditor.Pair(JFormattedTextField.PERSIST, UIDesignerBundle.message("property.focuslost.persist")),
                new IntEnumEditor.Pair(JFormattedTextField.REVERT, UIDesignerBundle.message("property.focuslost.revert"))
              };
              property = createIntEnumProperty(name, readMethod, writeMethod, pairs);
            }
            else{
              property = new IntroIntProperty(name, readMethod, writeMethod);
            }
        }
        else if (boolean.class.equals(propertyType)) { // boolean
          property = new IntroBooleanProperty(name, readMethod, writeMethod);
        }
        else if(double.class.equals(propertyType)){ // double
          property = new IntroDoubleProperty(name, readMethod, writeMethod);
        }
        else if (String.class.equals(propertyType)){ // java.lang.String
          property = new IntroStringProperty(name, readMethod, writeMethod);
        }
        else if (Insets.class.equals(propertyType)) { // java.awt.Insets
          property = new IntroInsetsProperty(name, readMethod, writeMethod);
        }
        else if (Dimension.class.equals(propertyType)) { // java.awt.Dimension
          property = new IntroDimensionProperty(name, readMethod, writeMethod);
        }
        else if(Rectangle.class.equals(propertyType)){ // java.awt.Rectangle
          property = new IntroRectangleProperty(name, readMethod, writeMethod);
        }
        else if (propertyType.isAssignableFrom(Component.class)) {
          property = new IntroComponentProperty(name, readMethod, writeMethod);
        }
        else if (Color.class.equals(propertyType)) {
          property = new IntroColorProperty(name, readMethod, writeMethod);
        }
        else if (Font.class.equals(propertyType)) {
          property = new IntroFontProperty(name, readMethod, writeMethod);
        }
        else if (Icon.class.equals(propertyType)) {
          property = new IntroIconProperty(name, readMethod, writeMethod);
        }
        else {
          // other types are not supported (yet?)
          continue;
        }

        result.add(property);
      }
    }
    catch (IntrospectionException e) {
      throw new RuntimeException(e);
    }

    final IntrospectedProperty[] properties = result.toArray(new IntrospectedProperty[result.size()]);
    myClass2Properties.put(aClass, properties);
    return properties;
  }

  /**
   * @return introspected property with the given <code>name</code> of the
   * specified <code>class</code>. The method returns <code>null</code> if there is no
   * property with the such name.
   */
  @Nullable
  public IntrospectedProperty getIntrospectedProperty(@NotNull final Class aClass, @NotNull final String name){
    final IntrospectedProperty[] properties = getIntrospectedProperties(aClass);
    for (final IntrospectedProperty property: properties) {
      if (name.equals(property.getName())) {
        return property;
      }
    }
    return null;
  }

  /**
   * @return "inplace" property for the component with the specified class.
   * <b>DO NOT USE THIS METHOD DIRECTLY</b>. Use {@link com.intellij.uiDesigner.RadComponent#getInplaceProperty(int, int) }
   * instead.
   */
  @Nullable
  public IntrospectedProperty getInplaceProperty(@NotNull final Class aClass){
    final String inplaceProperty = com.intellij.uiDesigner.Properties.getInstance().getInplaceProperty(aClass);
    final IntrospectedProperty[] properties = getIntrospectedProperties(aClass);
    for (int i = properties.length - 1; i >= 0; i--) {
      final IntrospectedProperty property = properties[i];
      if(property.getName().equals(inplaceProperty)){
        return property;
      }
    }
    return null;
  }

  public static boolean isRemovable(@NotNull final GroupItem group){
    final ArrayList<ComponentItem> items = group.getItems();
    for(int i = items.size() - 1; i >=0; i--){
      if(!items.get(i).isRemovable()){
        return false;
      }
    }
    return true;
  }

  /**
   * Updates UI of editors and renderers of all introspected properties
   */
  private final class MyLafManagerListener implements LafManagerListener{
    private void updateUI(final Property property){
      final PropertyRenderer renderer = property.getRenderer();
      renderer.updateUI();
      final PropertyEditor editor = property.getEditor();
      if(editor != null){
        editor.updateUI();
      }
      final Property[] children = property.getChildren();
      for (int i = children.length - 1; i >= 0; i--) {
        updateUI(children[i]);
      }
    }

    public void lookAndFeelChanged(final LafManager source) {
      for (final IntrospectedProperty[] properties : myClass2Properties.values()) {
        LOG.assertTrue(properties != null);
        for (int j = properties.length - 1; j >= 0; j--) {
          updateUI(properties[j]);
        }
      }
    }
  }

  static interface Listener{
    void groupsChanged(Palette palette);
  }
}