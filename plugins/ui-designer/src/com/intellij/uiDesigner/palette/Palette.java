// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.palette;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.uiDesigner.Properties;
import com.intellij.uiDesigner.SwingProperties;
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
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.List;
import java.util.*;

@State(name = "Palette2",defaultStateAsResource = true, storages = @Storage("uiDesigner.xml"))
public final class Palette implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(Palette.class);

  private final Map<Class<?>, IntrospectedProperty<?>[]> myClass2Properties;
  private final Map<String, ComponentItem> myClassName2Item;
  /*All groups in the palette*/
  private final List<GroupItem> myGroups;
  /*Listeners, etc*/
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Project myProject;
  private final GroupItem mySpecialGroup = new GroupItem(true);

  /**
   * Predefined item for javax.swing.JPanel
   */
  private ComponentItem myPanelItem;
  private static final @NonNls String ATTRIBUTE_VSIZE_POLICY = "vsize-policy";
  private static final @NonNls String ATTRIBUTE_HSIZE_POLICY = "hsize-policy";
  private static final @NonNls String ATTRIBUTE_ANCHOR = "anchor";
  private static final @NonNls String ATTRIBUTE_FILL = "fill";
  private static final @NonNls String ELEMENT_MINIMUM_SIZE = "minimum-size";
  private static final @NonNls String ATTRIBUTE_WIDTH = "width";
  private static final @NonNls String ATTRIBUTE_HEIGHT = "height";
  private static final @NonNls String ELEMENT_PREFERRED_SIZE = "preferred-size";
  private static final @NonNls String ELEMENT_MAXIMUM_SIZE = "maximum-size";
  private static final @NonNls String ATTRIBUTE_CLASS = "class";
  private static final @NonNls String ATTRIBUTE_ICON = "icon";
  private static final @NonNls String ATTRIBUTE_TOOLTIP_TEXT = "tooltip-text";
  private static final @NonNls String ELEMENT_DEFAULT_CONSTRAINTS = "default-constraints";
  private static final @NonNls String ELEMENT_INITIAL_VALUES = "initial-values";
  private static final @NonNls String ELEMENT_PROPERTY = "property";
  private static final @NonNls String ATTRIBUTE_NAME = "name";
  private static final @NonNls String ATTRIBUTE_VALUE = "value";
  private static final @NonNls String ATTRIBUTE_REMOVABLE = "removable";
  private static final @NonNls String ELEMENT_ITEM = "item";
  private static final @NonNls String ELEMENT_GROUP = "group";
  private static final @NonNls String ATTRIBUTE_VERSION = "version";
  private static final @NonNls String ATTRIBUTE_SINCE_VERSION = "since-version";
  private static final @NonNls String ATTRIBUTE_AUTO_CREATE_BINDING = "auto-create-binding";
  private static final @NonNls String ATTRIBUTE_CAN_ATTACH_LABEL = "can-attach-label";
  private static final @NonNls String ATTRIBUTE_IS_CONTAINER = "is-container";

  public static Palette getInstance(@NotNull Project project) {
    return project.getService(Palette.class);
  }

  /**
   * Invoked by reflection
   */
  public Palette(@Nullable Project project) {
    myProject = project;
    myClass2Properties = new HashMap<>();
    myClassName2Item = new HashMap<>();
    myGroups = new ArrayList<>();

    if (project != null) {
      mySpecialGroup.setReadOnly(true);
      mySpecialGroup.addItem(ComponentItem.createAnyComponentItem(project));

      ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(LafManagerListener.TOPIC, new MyLafManagerListener());
    }
  }

  @Override
  public void dispose() {
  }

  @Override
  public Element getState() {
    Element state = new Element("state");
    writeGroups(state);
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
    myClass2Properties.clear();
    myClassName2Item.clear();
    myGroups.clear();

    processGroups(state.getChildren(ELEMENT_GROUP));

    // Ensure that all predefined items are loaded
    LOG.assertTrue(myPanelItem != null);

    if (!state.getAttributeValue(ATTRIBUTE_VERSION, "1").equals("2")) {
      upgradePalette();
    }
  }

  @Override
  public void noStateLoaded() {
    try {
      loadState(Objects.requireNonNull(loadDefaultPalette()));
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  /**
   * Adds specified listener.
   */
  public void addListener(final @NotNull Listener l) {
    LOG.assertTrue(!myListeners.contains(l));
    myListeners.add(l);
  }

  void fireGroupsChanged() {
    for (Listener listener : myListeners) {
      listener.groupsChanged(this);
    }
  }

  private void upgradePalette() {
    // load new components from the predefined Palette2.xml
    try {
      Element rootElement = loadDefaultPalette();
      for (Element groupElement : Objects.requireNonNull(rootElement).getChildren(ELEMENT_GROUP)) {
        for (GroupItem group : myGroups) {
          if (group.getName().equals(groupElement.getAttributeValue(ATTRIBUTE_NAME))) {
            upgradeGroup(group, groupElement);
            break;
          }
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private @Nullable Element loadDefaultPalette() throws Exception {
    return JDOMUtil.load(getClass().getClassLoader().getResourceAsStream("Palette2.xml"));
  }

  private void upgradeGroup(final GroupItem group, final Element groupElement) {
    for (Element itemElement : groupElement.getChildren(ELEMENT_ITEM)) {
      if (itemElement.getAttributeValue(ATTRIBUTE_SINCE_VERSION, "").equals("2")) {
        processItemElement(itemElement, group, true);
      }
      final String className = LwXmlReader.getRequiredString(itemElement, ATTRIBUTE_CLASS);
      final ComponentItem item = getItem(className);
      if (item != null) {
        if (LwXmlReader.getOptionalBoolean(itemElement, ATTRIBUTE_AUTO_CREATE_BINDING, false)) {
          item.setAutoCreateBinding(true);
        }
        if (LwXmlReader.getOptionalBoolean(itemElement, ATTRIBUTE_CAN_ATTACH_LABEL, false)) {
          item.setCanAttachLabel(true);
        }
      }
    }
  }

  /**
   * @return a predefined palette item which corresponds to the JPanel.
   */
  public @NotNull ComponentItem getPanelItem() {
    return myPanelItem;
  }

  /**
   * @return {@code ComponentItem} for the UI bean with the specified {@code componentClassName}.
   * The method returns {@code null} if palette has no information about the specified
   * class.
   */
  public @Nullable ComponentItem getItem(final @NotNull String componentClassName) {
    return myClassName2Item.get(componentClassName);
  }

  /**
   * @return read-only list of all groups in the palette.
   * <em>DO NOT MODIFY OR CACHE THIS LIST</em>.
   */
  public List<GroupItem> getGroups() {
    return myGroups;
  }

  public GroupItem[] getToolWindowGroups() {
    GroupItem[] groups = new GroupItem[myGroups.size() + 1];
    for (int i = 0; i < myGroups.size(); i++) {
      groups[i] = myGroups.get(i);
    }
    groups[myGroups.size()] = mySpecialGroup;
    return groups;
  }

  /**
   * @param groups list of new groups.
   */
  public void setGroups(final @NotNull ArrayList<GroupItem> groups) {
    myGroups.clear();
    myGroups.addAll(groups);

    fireGroupsChanged();
  }

  /**
   * Adds specified {@code item} to the palette.
   *
   * @param item item to be added
   * @throws IllegalArgumentException if an item for the same class
   *                                            is already exists in the palette
   */
  public void addItem(final @NotNull GroupItem group, final @NotNull ComponentItem item) {
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
    if ("javax.swing.JPanel".equals(item.getClassName())) {
      myPanelItem = item;
    }
  }

  public void replaceItem(GroupItem group, ComponentItem oldItem, ComponentItem newItem) {
    group.replaceItem(oldItem, newItem);
    myClassName2Item.put(oldItem.getClassName(), newItem);
  }

  public void removeItem(final GroupItem group, final ComponentItem selectedItem) {
    group.removeItem(selectedItem);
    myClassName2Item.remove(selectedItem.getClassName());
  }

  public GroupItem findGroup(final ComponentItem componentItem) {
    for (GroupItem group : myGroups) {
      if (group.contains(componentItem)) {
        return group;
      }
    }
    return null;
  }

  /**
   * Helper method.
   */
  private static GridConstraints processDefaultConstraintsElement(final @NotNull Element element) {
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
    if (prefSizeElement != null) {
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

  private void processItemElement(final @NotNull Element itemElement, final @NotNull GroupItem group, final boolean skipExisting) {
    // Class name. It's OK if class does not exist.
    final String className = LwXmlReader.getRequiredString(itemElement, ATTRIBUTE_CLASS);
    if (skipExisting && getItem(className) != null) {
      return;
    }

    // Icon (optional)
    final String iconPath = LwXmlReader.getString(itemElement, ATTRIBUTE_ICON);

    // Tooltip text (optional)
    final String toolTipText = LwXmlReader.getString(itemElement, ATTRIBUTE_TOOLTIP_TEXT); // can be null

    boolean autoCreateBinding = LwXmlReader.getOptionalBoolean(itemElement, ATTRIBUTE_AUTO_CREATE_BINDING, false);
    boolean canAttachLabel = LwXmlReader.getOptionalBoolean(itemElement, ATTRIBUTE_CAN_ATTACH_LABEL, false);
    boolean isContainer = LwXmlReader.getOptionalBoolean(itemElement, ATTRIBUTE_IS_CONTAINER, false);

    // Default constraint
    final GridConstraints constraints;
    final Element defaultConstraints = itemElement.getChild(ELEMENT_DEFAULT_CONSTRAINTS);
    if (defaultConstraints != null) {
      constraints = processDefaultConstraintsElement(defaultConstraints);
    }
    else {
      constraints = new GridConstraints();
    }

    final HashMap<String, StringDescriptor> propertyName2initialValue = new HashMap<>();
    {
      final Element initialValues = itemElement.getChild(ELEMENT_INITIAL_VALUES);
      if (initialValues != null) {
        for (final Element e : initialValues.getChildren(ELEMENT_PROPERTY)) {
          final String name = LwXmlReader.getRequiredString(e, ATTRIBUTE_NAME);
          // TODO[all] currently all initial values are strings
          final StringDescriptor value = StringDescriptor.create(LwXmlReader.getRequiredString(e, ATTRIBUTE_VALUE));
          propertyName2initialValue.put(name, value);
        }
      }
    }

    final boolean removable = LwXmlReader.getOptionalBoolean(itemElement, ATTRIBUTE_REMOVABLE, true);

    final ComponentItem item = new ComponentItem(
      myProject,
      className,
      iconPath,
      toolTipText,
      constraints,
      propertyName2initialValue,
      removable,
      autoCreateBinding,
      canAttachLabel
    );
    item.setIsContainer(isContainer);
    addItem(group, item);
  }

  /**
   * Reads PaletteElements from
   */
  private void processGroups(@NotNull List<Element> groupElements) {
    for (Element groupElement : groupElements) {
      GroupItem group = new GroupItem(LwXmlReader.getRequiredString(groupElement, ATTRIBUTE_NAME));
      myGroups.add(group);
      for (Element itemElement : groupElement.getChildren(ELEMENT_ITEM)) {
        try {
          processItemElement(itemElement, group, false);
        }
        catch (Exception ex) {
          LOG.error(ex);
        }
      }
    }
  }

  /**
   * Helper method
   */
  private static void writeDefaultConstraintsElement(final @NotNull Element itemElement, final @NotNull GridConstraints c) {
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

  /**
   * Helper method
   */
  private static void writeInitialValuesElement(
    final @NotNull Element itemElement,
    final @NotNull HashMap<String, StringDescriptor> name2value
  ) {
    LOG.assertTrue(ELEMENT_ITEM.equals(itemElement.getName()));

    if (name2value.isEmpty()) { // do not append 'initial-values' subtag
      return;
    }

    final Element initialValuesElement = new Element(ELEMENT_INITIAL_VALUES);
    itemElement.addContent(initialValuesElement);

    for (final Map.Entry<String, StringDescriptor> entry : name2value.entrySet()) {
      final Element propertyElement = new Element(ELEMENT_PROPERTY);
      initialValuesElement.addContent(propertyElement);
      propertyElement.setAttribute(ATTRIBUTE_NAME, entry.getKey());
      propertyElement.setAttribute(ATTRIBUTE_VALUE, entry.getValue().getValue()/*descriptor is always trivial*/);
    }
  }

  /**
   * Helper method
   */
  private static void writeComponentItem(final @NotNull Element groupElement, final @NotNull ComponentItem item) {
    LOG.assertTrue(ELEMENT_GROUP.equals(groupElement.getName()));

    final Element itemElement = new Element(ELEMENT_ITEM);
    groupElement.addContent(itemElement);

    // Class
    itemElement.setAttribute(ATTRIBUTE_CLASS, item.getClassName());

    // Tooltip text (if any)
    if (item.myToolTipText != null) {
      itemElement.setAttribute(ATTRIBUTE_TOOLTIP_TEXT, item.myToolTipText);
    }

    // Icon (if any)
    final String iconPath = item.getIconPath();
    if (iconPath != null) {
      itemElement.setAttribute(ATTRIBUTE_ICON, iconPath);
    }

    // Removable
    itemElement.setAttribute(ATTRIBUTE_REMOVABLE, Boolean.toString(item.isRemovable()));
    itemElement.setAttribute(ATTRIBUTE_AUTO_CREATE_BINDING, Boolean.toString(item.isAutoCreateBinding()));
    itemElement.setAttribute(ATTRIBUTE_CAN_ATTACH_LABEL, Boolean.toString(item.isCanAttachLabel()));
    if (item.isContainer()) {
      itemElement.setAttribute(ATTRIBUTE_IS_CONTAINER, Boolean.toString(true));
    }

    // Default constraints
    writeDefaultConstraintsElement(itemElement, item.getDefaultConstraints());

    // Initial values (if any)
    writeInitialValuesElement(itemElement, item.getInitialValues());
  }

  private void writeGroups(@NotNull Element parentElement) {
    for (GroupItem group : myGroups) {
      Element groupElement = new Element(ELEMENT_GROUP);
      parentElement.addContent(groupElement);
      groupElement.setAttribute(ATTRIBUTE_NAME, group.getName());

      for (ComponentItem aItemList : group.getItems()) {
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
  ) {
    return new IntroIntProperty(
      name,
      readMethod,
      writeMethod,
      new IntEnumRenderer(pairs),
      new IntEnumEditor(pairs), false);
  }

  public IntrospectedProperty<?> @NotNull [] getIntrospectedProperties(final @NotNull RadComponent component) {
    return getIntrospectedProperties(component.getComponentClass(), component.getDelegee().getClass());
  }

  /**
   * @return arrays of all properties that can be introspected from the
   * specified class. Only properties with getter and setter methods are
   * returned.
   */
  public IntrospectedProperty<?> @NotNull [] getIntrospectedProperties(@NotNull Class<?> aClass, @NotNull Class<?> delegeeClass) {
    // Try the cache first
    // TODO[vova, anton] update cache after class reloading (its properties could be hanged).
    if (myClass2Properties.containsKey(aClass)) {
      return myClass2Properties.get(aClass);
    }

    List<IntrospectedProperty<?>> result = new ArrayList<>();
    try {
      final BeanInfo beanInfo = Introspector.getBeanInfo(aClass);
      final PropertyDescriptor[] descriptors = beanInfo.getPropertyDescriptors();
      for (final PropertyDescriptor descriptor : descriptors) {
        Method readMethod = descriptor.getReadMethod();
        Method writeMethod = descriptor.getWriteMethod();
        Class<?> propertyType = descriptor.getPropertyType();
        if (writeMethod == null || readMethod == null || propertyType == null) {
          continue;
        }

        boolean storeAsClient = false;
        try {
          delegeeClass.getMethod(readMethod.getName(), readMethod.getParameterTypes());
          delegeeClass.getMethod(writeMethod.getName(), writeMethod.getParameterTypes());
        }
        catch (NoSuchMethodException e) {
          storeAsClient = true;
        }

        final @NonNls String name = descriptor.getName();

        IntrospectedProperty<?> property;
        Properties properties = (myProject == null) ? new Properties() : Properties.getInstance();
        if (int.class.equals(propertyType)) { // int
          IntEnumEditor.Pair[] enumPairs = properties.getEnumPairs(aClass, name);
          if (enumPairs != null) {
            property = createIntEnumProperty(name, readMethod, writeMethod, enumPairs);
          }
          else if (JLabel.class.isAssignableFrom(aClass)) { // special handling for javax.swing.JLabel
            if (JLabel.class.isAssignableFrom(aClass) &&
                ("displayedMnemonic".equals(name) || "displayedMnemonicIndex".equals(name))) { // skip JLabel#displayedMnemonic and JLabel#displayedMnemonicIndex
              continue;
            }
            else {
              property = new IntroIntProperty(name, readMethod, writeMethod, storeAsClient);
            }
          }
          else if (AbstractButton.class.isAssignableFrom(aClass)) {  // special handling AbstractButton subclasses
            if ("mnemonic".equals(name) || "displayedMnemonicIndex".equals(name)) { // AbstractButton#mnemonic
              continue;
            }
            else {
              property = new IntroIntProperty(name, readMethod, writeMethod, storeAsClient);
            }
          }
          else if (JTabbedPane.class.isAssignableFrom(aClass)) {
            if (SwingProperties.SELECTED_INDEX.equals(name)) {
              continue;
            }
            property = new IntroIntProperty(name, readMethod, writeMethod, storeAsClient);
          }
          else {
            property = new IntroIntProperty(name, readMethod, writeMethod, storeAsClient);
          }
        }
        else if (boolean.class.equals(propertyType)) { // boolean
          property = new IntroBooleanProperty(name, readMethod, writeMethod, storeAsClient);
        }
        else if (double.class.equals(propertyType)) {
          property = new IntroPrimitiveTypeProperty<>(name, readMethod, writeMethod, storeAsClient, Double.class);
        }
        else if (float.class.equals(propertyType)) {
          property = new IntroPrimitiveTypeProperty<>(name, readMethod, writeMethod, storeAsClient, Float.class);
        }
        else if (long.class.equals(propertyType)) {
          property = new IntroPrimitiveTypeProperty<>(name, readMethod, writeMethod, storeAsClient, Long.class);
        }
        else if (byte.class.equals(propertyType)) {
          property = new IntroPrimitiveTypeProperty<>(name, readMethod, writeMethod, storeAsClient, Byte.class);
        }
        else if (short.class.equals(propertyType)) {
          property = new IntroPrimitiveTypeProperty<>(name, readMethod, writeMethod, storeAsClient, Short.class);
        }
        else if (char.class.equals(propertyType)) { // java.lang.String
          property = new IntroCharProperty(name, readMethod, writeMethod, storeAsClient);
        }
        else if (String.class.equals(propertyType)) { // java.lang.String
          property = new IntroStringProperty(name, readMethod, writeMethod, myProject, storeAsClient);
        }
        else if (Insets.class.equals(propertyType)) { // java.awt.Insets
          property = new IntroInsetsProperty(name, readMethod, writeMethod, storeAsClient);
        }
        else if (Dimension.class.equals(propertyType)) { // java.awt.Dimension
          property = new IntroDimensionProperty(name, readMethod, writeMethod, storeAsClient);
        }
        else if (Rectangle.class.equals(propertyType)) { // java.awt.Rectangle
          property = new IntroRectangleProperty(name, readMethod, writeMethod, storeAsClient);
        }
        else if (Component.class.isAssignableFrom(propertyType)) {
          if (JSplitPane.class.isAssignableFrom(aClass) && (name.equals("leftComponent") || name.equals("rightComponent") ||
                                                            name.equals("topComponent") || name.equals("bottomComponent"))) {
            // these properties are set through layout
            continue;
          }
          if (JTabbedPane.class.isAssignableFrom(aClass) && name.equals(SwingProperties.SELECTED_COMPONENT)) {
            // can't set selectedComponent because of set property / add child sequence
            continue;
          }
          if (JMenuBar.class.isAssignableFrom(propertyType) || JPopupMenu.class.isAssignableFrom(propertyType)) {
            // no menu editing yet
            continue;
          }
          Condition<RadComponent> filter = null;
          if (name.equals(SwingProperties.LABEL_FOR)) {
            filter = t -> {
              ComponentItem item = getItem(t.getComponentClassName());
              return item != null && item.isCanAttachLabel();
            };
          }
          property = new IntroComponentProperty(name, readMethod, writeMethod, propertyType, filter, storeAsClient);
        }
        else if (Color.class.equals(propertyType)) {
          property = new IntroColorProperty(name, readMethod, writeMethod, storeAsClient);
        }
        else if (Font.class.equals(propertyType)) {
          property = new IntroFontProperty(name, readMethod, writeMethod, storeAsClient);
        }
        else if (Icon.class.equals(propertyType)) {
          property = new IntroIconProperty(name, readMethod, writeMethod, storeAsClient);
        }
        else if (ListModel.class.isAssignableFrom(propertyType)) {
          property = new IntroListModelProperty(name, readMethod, writeMethod, storeAsClient);
        }
        else if (Enum.class.isAssignableFrom(propertyType)) {
          property = new IntroEnumProperty(name, readMethod, writeMethod, storeAsClient, propertyType);
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

    IntrospectedProperty<?>[] properties = result.toArray(new IntrospectedProperty[0]);
    myClass2Properties.put(aClass, properties);
    return properties;
  }

  /**
   * @return introspected property with the given {@code name} of the
   * specified {@code class}. The method returns {@code null} if there is no
   * property with the such name.
   */
  public @Nullable IntrospectedProperty<?> getIntrospectedProperty(final @NotNull RadComponent component, final @NotNull String name) {
    IntrospectedProperty<?>[] properties = getIntrospectedProperties(component);
    for (IntrospectedProperty<?> property : properties) {
      if (name.equals(property.getName())) {
        return property;
      }
    }
    return null;
  }

  /**
   * @return "inplace" property for the component with the specified class.
   * <b>DO NOT USE THIS METHOD DIRECTLY</b>. Use {@link RadComponent#getInplaceProperty(int, int) }
   * instead.
   */
  public @Nullable IntrospectedProperty<?> getInplaceProperty(final @NotNull RadComponent component) {
    final String inplaceProperty = Properties.getInstance().getInplaceProperty(component.getComponentClass());
    final IntrospectedProperty<?>[] properties = getIntrospectedProperties(component);
    for (int i = properties.length - 1; i >= 0; i--) {
      final IntrospectedProperty<?> property = properties[i];
      if (property.getName().equals(inplaceProperty)) {
        return property;
      }
    }
    return null;
  }

  public static boolean isRemovable(final @NotNull GroupItem group) {
    final ComponentItem[] items = group.getItems();
    for (int i = items.length - 1; i >= 0; i--) {
      if (!items[i].isRemovable()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Updates UI of editors and renderers of all introspected properties
   */
  private final class MyLafManagerListener implements LafManagerListener {
    private static void updateUI(Property<?, ?> property) {
      PropertyRenderer<?> renderer = property.getRenderer();
      renderer.updateUI();
      PropertyEditor<?> editor = property.getEditor();
      if (editor != null) {
        editor.updateUI();
      }
      Property<?, ?>[] children = property.getChildren(null);
      for (int i = children.length - 1; i >= 0; i--) {
        updateUI(children[i]);
      }
    }

    @Override
    public void lookAndFeelChanged(final @NotNull LafManager source) {
      for (final IntrospectedProperty<?>[] properties : myClass2Properties.values()) {
        LOG.assertTrue(properties != null);
        for (int j = properties.length - 1; j >= 0; j--) {
          updateUI(properties[j]);
        }
      }
    }
  }

  interface Listener {
    void groupsChanged(@NotNull Palette palette);
  }
}
