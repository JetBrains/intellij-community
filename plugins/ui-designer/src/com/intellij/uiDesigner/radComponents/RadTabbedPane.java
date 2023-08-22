// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.*;
import com.intellij.uiDesigner.propertyInspector.editors.IconEditor;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.propertyInspector.properties.AbstractBooleanProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroIconProperty;
import com.intellij.uiDesigner.propertyInspector.renderers.IconRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.StringRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Objects;

public final class RadTabbedPane extends RadContainer implements ITabbedPane {

  public static class Factory extends RadComponentFactory {
    @Override
    public RadComponent newInstance(ModuleProvider module, Class aClass, String id) {
      return new RadTabbedPane(module, aClass, id);
    }

    @Override
    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      return new RadTabbedPane(componentClass, id, palette);
    }
  }

  private static final Logger LOG = Logger.getInstance(RadTabbedPane.class);
  /**
   * value: HashMap<String, LwTabbedPane.Constraints>
   */
  @NonNls
  private static final String CLIENT_PROP_ID_2_CONSTRAINTS = "index2descriptor";

  private int mySelectedIndex = -1;
  private IntrospectedProperty mySelectedIndexProperty = null;

  public RadTabbedPane(final ModuleProvider module, Class componentClass, final String id){
    super(module, componentClass, id);
  }

  public RadTabbedPane(Class componentClass, @NotNull final String id, final Palette palette) {
    super(componentClass, id, palette);
  }

  @Override protected RadLayoutManager createInitialLayoutManager() {
    return new RadTabbedPaneLayoutManager();
  }

  @Override public RadComponent getComponentToDrag(final Point pnt) {
    final int i = getTabbedPane().getUI().tabForCoordinate(getTabbedPane(), pnt.x, pnt.y);
    if (i >= 0) {
      RadComponent c = getRadComponent(i);
      if (c != null) {
        return c;
      }
    }
    return this;
  }

  private RadComponent getRadComponent(final int i) {
    RadComponent c = null;
    final Component component = getTabbedPane().getComponentAt(i);
    if (component instanceof JComponent jc) {
      c = (RadComponent) jc.getClientProperty(RadComponent.CLIENT_PROP_RAD_COMPONENT);
    }
    return c;
  }

  @Override public void init(final GuiEditor editor, @NotNull final ComponentItem item) {
    super.init(editor, item);
    // add one tab by default
    addComponent(InsertComponentProcessor.createPanelComponent(editor));
  }

  @NotNull
  private JTabbedPane getTabbedPane(){
    return (JTabbedPane)getDelegee();
  }

  private String calcTabName(final StringDescriptor titleDescriptor) {
    if (titleDescriptor == null) {
      return UIDesignerBundle.message("tab.untitled");
    }
    return getDescriptorText(titleDescriptor);
  }

  @Nullable
  private String getDescriptorText(@Nullable final StringDescriptor titleDescriptor) {
    if (titleDescriptor == null) return null;
    final String value = titleDescriptor.getValue();
    if (value == null) { // from res bundle
      final String resolvedValue = StringDescriptorManager.getInstance(getModule()).resolve(this, titleDescriptor);
      titleDescriptor.setResolvedValue(resolvedValue);
      return resolvedValue;
    }
    return value;
  }

  /**
   * @return inplace property for editing of the title of the clicked tab
   */
  @Override
  public Property getInplaceProperty(final int x, final int y) {
    final JTabbedPane tabbedPane = getTabbedPane();
    final TabbedPaneUI ui = tabbedPane.getUI();
    LOG.assertTrue(ui != null);
    final int index = ui.tabForCoordinate(tabbedPane, x, y);
    return index != -1 ? new MyTitleProperty(null, index) : null;
  }

  @Override @Nullable
  public Property getDefaultInplaceProperty() {
    final int index = getTabbedPane().getSelectedIndex();
    if (index >= 0) {
      return new MyTitleProperty(null, index);
    }
    return null;
  }

  @Override
  public Rectangle getInplaceEditorBounds(final Property property, final int x, final int y) {
    final JTabbedPane tabbedPane = getTabbedPane();
    final TabbedPaneUI ui = tabbedPane.getUI();
    LOG.assertTrue(ui != null);
    final int index = ui.tabForCoordinate(tabbedPane, x, y);
    LOG.assertTrue(index != -1);
    return ui.getTabBounds(tabbedPane, index);
  }

  @Override @Nullable
  public Rectangle getDefaultInplaceEditorBounds() {
    final JTabbedPane tabbedPane = getTabbedPane();
    final int index = tabbedPane.getSelectedIndex();
    if (index >= 0) {
      return tabbedPane.getUI().getTabBounds(tabbedPane, index);
    }
    return null;
  }

  @Nullable
  public StringDescriptor getChildTitle(RadComponent component) {
    final HashMap<String, LwTabbedPane.Constraints> id2Constraints = getId2Constraints(this);
    final LwTabbedPane.Constraints constraints = id2Constraints.get(component.getId());
    return constraints == null ? null : constraints.myTitle;
  }

  public void setTabProperty(RadComponent component, final String propName, StringDescriptor title) throws Exception {
    final JComponent delegee = component.getDelegee();
    final JTabbedPane tabbedPane = getTabbedPane();
    int index = tabbedPane.indexOfComponent(delegee);
    if (index >= 0) {
      if (propName.equals(ITabbedPane.TAB_TITLE_PROPERTY)) {
        new MyTitleProperty(null, index).setValue(component, title);
      }
      else if (propName.equals(ITabbedPane.TAB_TOOLTIP_PROPERTY)) {
        new MyToolTipProperty(null, index).setValue(component, title);
      }
      else {
        throw new IllegalArgumentException("Invalid property name " + propName);
      }
    }
  }

  /**
   * This allows user to select and scroll tabs via the mouse
   */
  @Override
  public void processMouseEvent(final MouseEvent event){
    event.getComponent().dispatchEvent(event);
  }

  @Override
  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_TABBEDPANE);
    try{
      writeId(writer);
      writeClassIfDifferent(writer, JTabbedPane.class.getName());
      writeBinding(writer);

      // Constraints and properties
      writeConstraints(writer);
      writeProperties(writer);

      writeBorder(writer);
      writeChildren(writer);
    }finally{
      writer.endElement();
    }
  }

  @NotNull
  private static HashMap<String, LwTabbedPane.Constraints> getId2Constraints(final RadComponent component){
    //noinspection unchecked
    HashMap<String, LwTabbedPane.Constraints> id2Constraints = (HashMap<String, LwTabbedPane.Constraints>)component.getClientProperty(CLIENT_PROP_ID_2_CONSTRAINTS);
    if(id2Constraints == null){
      id2Constraints = new HashMap<>();
      component.putClientProperty(CLIENT_PROP_ID_2_CONSTRAINTS, id2Constraints);
    }
    return id2Constraints;
  }

  @Nullable
  public RadComponent getSelectedTab() {
    int index = getTabbedPane().getSelectedIndex();
    return index < 0 ? null : getComponent(index);
  }

  public void selectTab(final RadComponent component) {
    final JTabbedPane tabbedPane = getTabbedPane();
    int index = tabbedPane.indexOfComponent(component.getDelegee());
    if (index >= 0) {
      tabbedPane.setSelectedIndex(index);
    }
  }

  @Override
  public StringDescriptor getTabProperty(IComponent component, final String propName) {
    final HashMap<String, LwTabbedPane.Constraints> id2Constraints = getId2Constraints(this);
    final LwTabbedPane.Constraints constraints = id2Constraints.get(component.getId());
    return constraints == null ? null : constraints.getProperty(propName);
  }

  public boolean refreshChildTitle(final RadComponent radComponent) {
    StringDescriptor childTitle = getChildTitle(radComponent);
    if (childTitle == null) return false;
    String oldTitle = childTitle.getResolvedValue();
    childTitle.setResolvedValue(null);
    try {
      setTabProperty(radComponent, ITabbedPane.TAB_TITLE_PROPERTY, childTitle);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return !Objects.equals(oldTitle, childTitle.getResolvedValue());
  }

  @Override public void loadLwProperty(final LwComponent lwComponent, final LwIntrospectedProperty lwProperty, final IntrospectedProperty property) {
    if (lwProperty.getName().equals(SwingProperties.SELECTED_INDEX)) {
      mySelectedIndexProperty = property;
      mySelectedIndex = ((Integer)lwProperty.getPropertyValue(lwComponent)).intValue();
    }
    else {
      super.loadLwProperty(lwComponent, lwProperty, property);
    }
  }

  @Override public void doneLoadingFromLw() {
    if (mySelectedIndex >= 0) {
      getTabbedPane().setSelectedIndex(mySelectedIndex);
      markPropertyAsModified(mySelectedIndexProperty);
    }
  }

  @NotNull
  private LwTabbedPane.Constraints getConstraintsForComponent(final RadComponent tabComponent) {
    final HashMap<String, LwTabbedPane.Constraints> id2Constraints = getId2Constraints(this);
    LwTabbedPane.Constraints constraints = id2Constraints.get(tabComponent.getId());
    if (constraints == null) {
      int index = indexOfComponent(tabComponent);
      constraints = new LwTabbedPane.Constraints(StringDescriptor.create(getTabbedPane().getTitleAt(index)));
      id2Constraints.put(tabComponent.getId(), constraints);
    }
    return constraints;
  }

  private final class MyTabGroupProperty extends ReadOnlyProperty {
    private final int myIndex;
    private final LabelPropertyRenderer myRenderer = new LabelPropertyRenderer("");

    MyTabGroupProperty(final int index) {
      super(null, "Tab");
      myIndex = index;
    }

    @Override
    @NotNull
    public PropertyRenderer getRenderer() {
      return myRenderer;
    }


    @Override
    public Property @NotNull [] getChildren(final RadComponent component) {
      return new Property[] {
        new MyTitleProperty(this, myIndex),
        new MyToolTipProperty(this, myIndex),
        new MyIconProperty(this, myIndex, false),
        new MyIconProperty(this, myIndex, true),
        new MyEnabledProperty(this, myIndex)
      };
    }
  }

  private class MyTitleProperty extends Property<RadComponent, StringDescriptor> {
    protected final int myIndex;
    private final StringEditor myEditor = new StringEditor(getProject());
    private final StringRenderer myRenderer = new StringRenderer();

    MyTitleProperty(final Property parent, final int index) {
      super(parent, TAB_TITLE_PROPERTY);
      myIndex = index;
    }

    protected MyTitleProperty(final Property parent, @NonNls final String name, final int index) {
      super(parent, name);
      myIndex = index;
    }

    @Override
    public StringDescriptor getValue(final RadComponent component) {
      final RadComponent tabComponent = getRadComponent(myIndex);
      // 1. resource bundle
      final LwTabbedPane.Constraints constraints = getId2Constraints(RadTabbedPane.this).get(tabComponent.getId());
      final StringDescriptor descriptor = constraints == null ? null : getValueFromConstraints(constraints);
      if(descriptor != null){
        return descriptor;
      }

      // 2. plain value
      return StringDescriptor.create(getValueFromTabbedPane());
    }

    @Override
    protected void setValueImpl(final RadComponent component, StringDescriptor value) throws Exception {
      final RadComponent tabComponent = getRadComponent(myIndex);
      // 1. Put value into map
      if (value == null) value = StringDescriptor.create("");
      LwTabbedPane.Constraints constraints = getConstraintsForComponent(tabComponent);
      putValueToConstraints(value, constraints);

      // 2. Apply real string value to JComponent peer
      final String text = StringDescriptorManager.getInstance(getModule()).resolve(RadTabbedPane.this, value);
      if (value.getValue() == null) {
        value.setResolvedValue(text);
      }
      putValueToTabbedPane(text);
    }

    protected String getValueFromTabbedPane() {
      return getTabbedPane().getTitleAt(myIndex);
    }

    protected StringDescriptor getValueFromConstraints(final LwTabbedPane.Constraints constraints) {
      return constraints.myTitle;
    }

    protected void putValueToTabbedPane(final @NlsSafe String text) {
      getTabbedPane().setTitleAt(myIndex, text);
    }

    protected void putValueToConstraints(final StringDescriptor value, final LwTabbedPane.Constraints constraints) {
      constraints.myTitle = value;
    }

    @Override
    @NotNull
    public PropertyRenderer<StringDescriptor> getRenderer() {
      return myRenderer;
    }

    @Override
    public PropertyEditor<StringDescriptor> getEditor() {
      return myEditor;
    }

    @Override public boolean isModified(final RadComponent component) {
      return !getTabbedPane().getTitleAt(myIndex).equals(UIDesignerBundle.message("tab.untitled"));
    }

    @Override public void resetValue(final RadComponent component) throws Exception {
      setValue(component, StringDescriptor.create(UIDesignerBundle.message("tab.untitled")));
    }
  }

  private class MyToolTipProperty extends MyTitleProperty {
    protected MyToolTipProperty(final Property parent, final int index) {
      super(parent, TAB_TOOLTIP_PROPERTY, index);
    }

    @Override protected String getValueFromTabbedPane() {
      return getTabbedPane().getToolTipTextAt(myIndex);
    }

    @Override protected StringDescriptor getValueFromConstraints(final LwTabbedPane.Constraints constraints) {
      return constraints.myToolTip;
    }

    @Override protected void putValueToTabbedPane(final @NlsSafe String text) {
      getTabbedPane().setToolTipTextAt(myIndex, text);
    }

    @Override protected void putValueToConstraints(final StringDescriptor value, final LwTabbedPane.Constraints constraints) {
      constraints.myToolTip = value;
    }

    @Override public boolean isModified(final RadComponent component) {
      String toolTipText = getTabbedPane().getToolTipTextAt(myIndex);
      return !StringUtil.isEmpty(toolTipText);
    }

    @Override public void resetValue(final RadComponent component) throws Exception {
      setValue(component, StringDescriptor.create(""));
    }
  }

  private class MyIconProperty extends Property<RadComponent, IconDescriptor> {
    private final int myIndex;
    private final boolean myDisabledIcon;
    private final IconRenderer myRenderer = new IconRenderer();
    private final IconEditor myEditor = new IconEditor();

    MyIconProperty(final Property parent, final int index, final boolean disabledIcon) {
      super(parent, disabledIcon ? "Tab Disabled Icon" : "Tab Icon");
      myIndex = index;
      myDisabledIcon = disabledIcon;
    }

    @Override
    public IconDescriptor getValue(final RadComponent component) {
      LwTabbedPane.Constraints constraints = getConstraintsForComponent(component);
      return myDisabledIcon ? constraints.myDisabledIcon : constraints.myIcon;
    }

    @Override
    protected void setValueImpl(final RadComponent component, final IconDescriptor value) throws Exception {
      Icon icon = (value != null) ? value.getIcon() : null;
      LwTabbedPane.Constraints constraints = getConstraintsForComponent(component);
      if (myDisabledIcon) {
        constraints.myDisabledIcon = value;
        getTabbedPane().setDisabledIconAt(myIndex, icon);
      }
      else
      {
        constraints.myIcon = value;
        getTabbedPane().setIconAt(myIndex, icon);
      }
    }

    @Override
    @NotNull
    public PropertyRenderer<IconDescriptor> getRenderer() {
      return myRenderer;
    }

    @Override
    public PropertyEditor<IconDescriptor> getEditor() {
      return myEditor;
    }

    @Override public boolean isModified(final RadComponent radComponent) {
      return getValue(radComponent) != null;
    }

    @Override public void resetValue(final RadComponent radComponent) throws Exception {
      setValue(radComponent, null);
    }
  }

  private class MyEnabledProperty extends AbstractBooleanProperty<RadComponent> {
    private final int myIndex;

    MyEnabledProperty(final Property parent, final int index) {
      super(parent, "Tab Enabled", true);
      myIndex = index;
    }

    @Override
    public Boolean getValue(final RadComponent component) {
      LwTabbedPane.Constraints constraints = getConstraintsForComponent(component);
      return constraints.myEnabled;
    }

    @Override
    protected void setValueImpl(final RadComponent component, final Boolean value) throws Exception {
      LwTabbedPane.Constraints constraints = getConstraintsForComponent(component);
      constraints.myEnabled = value.booleanValue();
      getTabbedPane().setEnabledAt(myIndex, value.booleanValue());
    }
  }

  private class RadTabbedPaneLayoutManager extends RadLayoutManager {

    @Override
    @Nullable public String getName() {
      return null;
    }

    @Override @NotNull
    public ComponentDropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
      final JTabbedPane tabbedPane = getTabbedPane();
      final TabbedPaneUI ui = tabbedPane.getUI();
      if (location != null && tabbedPane.getTabCount() > 0) {
        for(int i=0; i<tabbedPane.getTabCount(); i++) {
          Rectangle rc = ui.getTabBounds(tabbedPane, i);
          if (location.x < rc.getCenterX()) {
            return new InsertTabDropLocation(i, new Rectangle(rc.x-4, rc.y, 8, rc.height));
          }
        }
      }
      return new InsertTabDropLocation(tabbedPane.getTabCount(), null);
    }

    @Override
    public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
      writer.startElement(UIFormXmlConstants.ELEMENT_TABBEDPANE);
      try{
        final JComponent delegee = child.getDelegee();
        final JTabbedPane tabbedPane = getTabbedPane();
        final int i = tabbedPane.indexOfComponent(delegee);
        if (i == -1) {
          throw new IllegalArgumentException("cannot find tab for " + child);
        }

        final HashMap<String, LwTabbedPane.Constraints> id2Constraints = getId2Constraints(RadTabbedPane.this);
        final LwTabbedPane.Constraints tabTitleConstraints = id2Constraints.get(child.getId());
        if (tabTitleConstraints != null) {
          writer.writeStringDescriptor(tabTitleConstraints.myTitle,
                                       UIFormXmlConstants.ATTRIBUTE_TITLE,
                                       UIFormXmlConstants.ATTRIBUTE_TITLE_RESOURCE_BUNDLE,
                                       UIFormXmlConstants.ATTRIBUTE_TITLE_KEY);

          if (tabTitleConstraints.myIcon != null) {
            writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_ICON, tabTitleConstraints.myIcon.getIconPath());
          }
          if (tabTitleConstraints.myDisabledIcon != null) {
            writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_DISABLED_ICON, tabTitleConstraints.myDisabledIcon.getIconPath());
          }
          if (!tabTitleConstraints.myEnabled) {
            writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_ENABLED, false);
          }
          if (tabTitleConstraints.myToolTip != null) {
            writer.startElement(UIFormXmlConstants.ELEMENT_TOOLTIP);
            writer.writeStringDescriptor(tabTitleConstraints.myToolTip,
                                         UIFormXmlConstants.ATTRIBUTE_VALUE,
                                         UIFormXmlConstants.ATTRIBUTE_RESOURCE_BUNDLE,
                                         UIFormXmlConstants.ATTRIBUTE_KEY);
            writer.endElement();
          }
        }
        else {
          final String title = tabbedPane.getTitleAt(i);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_TITLE, title != null ? title : "");
        }
      }
      finally{
        writer.endElement();
      }
    }

    @Override
    public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
      final JTabbedPane tabbedPane = getTabbedPane();
      LwTabbedPane.Constraints constraints = null;
      if (component.getCustomLayoutConstraints() instanceof LwTabbedPane.Constraints) {
        constraints = ((LwTabbedPane.Constraints)component.getCustomLayoutConstraints());
      }
      component.setCustomLayoutConstraints(null);
      final HashMap<String, LwTabbedPane.Constraints> id2Constraints = getId2Constraints(RadTabbedPane.this);
      id2Constraints.put(component.getId(), constraints);
      final @NlsSafe String tabName = calcTabName(constraints == null ? null : constraints.myTitle);
      @NlsSafe String toolTip = null;
      Icon icon = null;
      if (constraints != null) {
        toolTip = getDescriptorText(constraints.myToolTip);
        if (constraints.myIcon != null) {
          IntroIconProperty.ensureIconLoaded(getModule(), constraints.myIcon);
          icon = constraints.myIcon.getIcon();
        }
      }
      tabbedPane.insertTab(tabName, icon, component.getDelegee(), toolTip, index);
      if (constraints != null) {
        if (constraints.myDisabledIcon != null) {
          IntroIconProperty.ensureIconLoaded(getModule(), constraints.myDisabledIcon);
          tabbedPane.setDisabledIconAt(index, constraints.myDisabledIcon.getIcon());
        }
        tabbedPane.setEnabledAt(index, constraints.myEnabled);
      }
    }

    @Override public void removeComponentFromContainer(final RadContainer container, final RadComponent component) {
      LOG.debug("Removing component with ID " + component.getId());
      final JTabbedPane tabbedPane = getTabbedPane();

      final JComponent delegee = component.getDelegee();
      final int i = tabbedPane.indexOfComponent(delegee);
      if (i == -1) {
        throw new IllegalArgumentException("cannot find tab for " + component);
      }
      final HashMap<String, LwTabbedPane.Constraints> id2Constraints = getId2Constraints(RadTabbedPane.this);
      LwTabbedPane.Constraints constraints = id2Constraints.get(component.getId());
      if (constraints == null) {
        LOG.debug("title of removed component is null");
        constraints = new LwTabbedPane.Constraints(StringDescriptor.create(tabbedPane.getTitleAt(i)));
      }
      else {
        LOG.debug("title of removed component is " + constraints.myTitle.toString());
      }
      component.setCustomLayoutConstraints(constraints);
      id2Constraints.remove(component.getId());
      tabbedPane.removeTabAt(i);
    }

    @Override public Property[] getComponentProperties(final Project project, final RadComponent component) {
      final JComponent delegee = component.getDelegee();
      final JTabbedPane tabbedPane = getTabbedPane();
      int index = tabbedPane.indexOfComponent(delegee);
      if (index >= 0) {
        return new Property[] { new MyTabGroupProperty(index) };
      }
      return Property.EMPTY_ARRAY;
    }

    @Override
    public boolean isSwitchedToChild(RadContainer container, RadComponent child) {
      return child == getSelectedTab();
    }

    @Override
    public boolean switchContainerToChild(RadContainer container, RadComponent child) {
      RadTabbedPane.this.selectTab(child);
      return true;
    }

    @Override
    public boolean areChildrenExclusive() {
      return true;
    }
  }

  private final class InsertTabDropLocation implements ComponentDropLocation {
    private int myInsertIndex;
    private String myInsertBeforeId;
    private final Rectangle myFeedbackRect;

    InsertTabDropLocation(final int insertIndex, final Rectangle feedbackRect) {
      myInsertIndex = insertIndex;
      if (myInsertIndex < getTabbedPane().getTabCount()) {
        myInsertBeforeId = getRadComponent(myInsertIndex).getId();
      }
      myFeedbackRect = feedbackRect;
    }

    @Override
    public RadContainer getContainer() {
      return RadTabbedPane.this;
    }

    @Override
    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1;
    }

    @Override
    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      final String tooltipText = UIDesignerBundle.message("insert.feedback.add.tab", getDisplayName(), myInsertIndex);
      if (myInsertIndex < getTabbedPane().getTabCount()) {
        feedbackLayer.putFeedback(getDelegee(), myFeedbackRect, VertInsertFeedbackPainter.INSTANCE, tooltipText);
      }
      else {
        Rectangle rcFeedback;
        final JTabbedPane tabbedPane = getTabbedPane();
        final TabbedPaneUI ui = tabbedPane.getUI();
        if (tabbedPane.getTabCount() > 0) {
          Rectangle rc = ui.getTabBounds(tabbedPane, tabbedPane.getTabCount()-1);
          rcFeedback = new Rectangle(rc.x+rc.width, rc.y, 50, rc.height);
        }
        else {
          // approximate
          rcFeedback = new Rectangle(0, 0, 50, tabbedPane.getFontMetrics(tabbedPane.getFont()).getHeight() + 8);
        }
        feedbackLayer.putFeedback(getDelegee(), rcFeedback, tooltipText);
      }
    }

    @Override
    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      if (myInsertBeforeId != null) {
        for(int i=0; i<getTabbedPane().getTabCount(); i++) {
          if (getRadComponent(i).getId().equals(myInsertBeforeId)) {
            myInsertIndex = i;
            break;
          }
        }
      }
      if (myInsertIndex > getTabbedPane().getTabCount()) {
        myInsertIndex = getTabbedPane().getTabCount();
      }
      RadComponent componentToAdd = components [0];
      if (componentToAdd instanceof RadContainer) {
        addComponent(componentToAdd, myInsertIndex);
      }
      else {
        Palette palette = Palette.getInstance(editor.getProject());
        RadContainer panel = InsertComponentProcessor.createPanelComponent(editor);
        addComponent(panel);
        panel.getDropLocation(null).processDrop(editor, new RadComponent[] { componentToAdd }, null,
                                                new ComponentItemDragObject(palette.getPanelItem()));
      }
      getTabbedPane().setSelectedIndex(myInsertIndex);
    }

    @Override
    @Nullable
    public ComponentDropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }
}
