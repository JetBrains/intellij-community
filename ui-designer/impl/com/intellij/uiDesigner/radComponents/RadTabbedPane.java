package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.StringRenderer;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 * @author yole
 */
public final class RadTabbedPane extends RadContainer implements ITabbedPane {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.radComponents.RadTabbedPane");
  /**
   * value: HashMap<String, StringDescriptor>
   */
  @NonNls
  private static final String CLIENT_PROP_ID_2_DESCRIPTOR = "index2descriptor";

  private int mySelectedIndex = -1;
  private IntrospectedProperty mySelectedIndexProperty = null;

  public RadTabbedPane(final Module module, final String id){
    super(module, JTabbedPane.class, id);
  }

  public RadTabbedPane(@NotNull final String id, final Palette palette) {
    super(JTabbedPane.class, id, palette);
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
    if (component instanceof JComponent) {
      JComponent jc = (JComponent) component;
      c = (RadComponent) jc.getClientProperty(RadComponent.CLIENT_PROP_RAD_COMPONENT);
    }
    return c;
  }

  @Override public void init(final GuiEditor editor, @NotNull final ComponentItem item) {
    super.init(editor, item);
    // add one tab by default
    Palette palette = Palette.getInstance(editor.getProject());
    RadComponent panel = InsertComponentProcessor.createInsertedComponent(editor, palette.getPanelItem());
    addComponent(panel);
  }

  @NotNull
  private JTabbedPane getTabbedPane(){
    return (JTabbedPane)getDelegee();
  }

  private String calcTabName(final StringDescriptor titleDescriptor) {
    if (titleDescriptor == null) {
      return UIDesignerBundle.message("tab.untitled");
    }
    final String value = titleDescriptor.getValue();
    if (value == null) { // from res bundle
      return ReferenceUtil.resolve(this, titleDescriptor);
    }
    return value;
  }

  /**
   * @return inplace property for editing of the title of the clicked tab
   */
  public Property getInplaceProperty(final int x, final int y) {
    final JTabbedPane tabbedPane = getTabbedPane();
    final TabbedPaneUI ui = tabbedPane.getUI();
    LOG.assertTrue(ui != null);
    final int index = ui.tabForCoordinate(tabbedPane, x, y);
    return index != -1 ? new MyTitleProperty(true, index) : null;
  }

  @Override @Nullable
  public Property getDefaultInplaceProperty() {
    final int index = getTabbedPane().getSelectedIndex();
    if (index >= 0) {
      return new MyTitleProperty(true, index);
    }
    return null;
  }

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
    final HashMap<String, StringDescriptor> id2Descriptor = getId2Descriptor(this);
    return id2Descriptor.get(component.getId());
  }

  public void setChildTitle(RadComponent component, StringDescriptor title) throws Exception {
    final JComponent delegee = component.getDelegee();
    final JTabbedPane tabbedPane = getTabbedPane();
    int index = tabbedPane.indexOfComponent(delegee);
    if (index >= 0) {
      new MyTitleProperty(true, index).setValue(this, title);
    }
  }

  /**
   * This allows user to select and scroll tabs via the mouse
   */
  public void processMouseEvent(final MouseEvent event){
    event.getComponent().dispatchEvent(event);
  }

  public void write(final XmlWriter writer) {
    writer.startElement("tabbedpane");
    try{
      writeId(writer);
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
  private static HashMap<String, StringDescriptor> getId2Descriptor(final RadComponent component){
    HashMap<String, StringDescriptor> id2Descriptor = (HashMap<String, StringDescriptor>)component.getClientProperty(CLIENT_PROP_ID_2_DESCRIPTOR);
    if(id2Descriptor == null){
      id2Descriptor = new HashMap<String,StringDescriptor>();
      component.putClientProperty(CLIENT_PROP_ID_2_DESCRIPTOR, id2Descriptor);
    }
    return id2Descriptor;
  }

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

  public StringDescriptor getTabTitle(IComponent component) {
    return getChildTitle((RadComponent) component);
  }

  public void refreshChildTitle(final RadComponent radComponent) {
    StringDescriptor childTitle = getChildTitle(radComponent);
    childTitle.setResolvedValue(null);
    try {
      setChildTitle(radComponent, childTitle);
    }
    catch (Exception e) {
      LOG.error(e);
    }
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

  @Override
  protected void importSnapshotComponent(final SnapshotContext context, final JComponent component) {
    JTabbedPane tabbedPane = (JTabbedPane) component;
    for(int i=0; i<tabbedPane.getTabCount(); i++) {
      String title = tabbedPane.getTitleAt(i);
      Component child = tabbedPane.getComponentAt(i);
      if (child instanceof JComponent) {
        RadComponent childComponent = createSnapshotComponent(context, (JComponent) child);
        if (childComponent != null) {
          childComponent.setCustomLayoutConstraints(new LwTabbedPane.Constraints(StringDescriptor.create(title)));
          addComponent(childComponent);
        }
      }
    }
  }

  private final class MyTitleProperty extends Property<RadComponent, StringDescriptor> {
    /**
     * Index of tab which title should be edited
     */
    private final boolean myInPlace;
    private final int myIndex;
    private final StringEditor myEditor;
    private final StringRenderer myRenderer;

    public MyTitleProperty(final boolean inPlace, final int index) {
      super(null, "Tab Title");
      myInPlace = inPlace;
      myIndex = index;
      myEditor = new StringEditor(getModule().getProject());
      myRenderer = new StringRenderer();
    }

    public StringDescriptor getValue(final RadComponent component) {
      final RadComponent tabComponent = myInPlace ? component : getRadComponent(myIndex);
      // 1. resource bundle
      final StringDescriptor descriptor = getId2Descriptor(RadTabbedPane.this).get(tabComponent.getId());
      LOG.debug("MyTitleProperty: getting value " + (descriptor == null ? "<null>" : descriptor.toString()) +
                " for component ID=" + tabComponent.getId());
      if(descriptor != null){
        return descriptor;
      }

      // 2. plain value
      return StringDescriptor.create(getTabbedPane().getTitleAt(myIndex));
    }

    protected void setValueImpl(final RadComponent component, final StringDescriptor value) throws Exception {
      final RadComponent tabComponent = myInPlace ? component : getRadComponent(myIndex);
      // 1. Put value into map
      LOG.debug("MyTitleProperty: setting value " + (value == null ? "<null>" : value.toString()) +
                " for component ID=" + tabComponent.getId());
      final HashMap<String, StringDescriptor> id2Descriptor = getId2Descriptor(RadTabbedPane.this);
      if(value == null){
        id2Descriptor.remove(tabComponent.getId());
      }
      else{
        id2Descriptor.put(tabComponent.getId(), value);
      }

      // 2. Apply real string value to JComponent peer
      getTabbedPane().setTitleAt(myIndex, ReferenceUtil.resolve(RadTabbedPane.this, value));
    }

    @NotNull
    public PropertyRenderer getRenderer() {
      return myRenderer;
    }

    public PropertyEditor getEditor() {
      return myEditor;
    }
  }

  private class RadTabbedPaneLayoutManager extends RadLayoutManager {

    @Nullable public String getName() {
      return null;
    }

    @Override @NotNull
    public DropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
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

    public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
      writer.startElement("tabbedpane");
      try{
        final JComponent delegee = child.getDelegee();
        final JTabbedPane tabbedPane = getTabbedPane();
        final int i = tabbedPane.indexOfComponent(delegee);
        if (i == -1) {
          throw new IllegalArgumentException("cannot find tab for " + child);
        }

        final HashMap<String, StringDescriptor> id2Descriptor = getId2Descriptor(RadTabbedPane.this);
        final StringDescriptor tabTitleDescriptor = id2Descriptor.get(child.getId());
        if (tabTitleDescriptor != null) {
          writer.writeStringDescriptor(tabTitleDescriptor,
                                       UIFormXmlConstants.ATTRIBUTE_TITLE,
                                       UIFormXmlConstants.ATTRIBUTE_TITLE_RESOURCE_BUNDLE,
                                       UIFormXmlConstants.ATTRIBUTE_TITLE_KEY);
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

    public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
      final JTabbedPane tabbedPane = getTabbedPane();
      final StringDescriptor titleDescriptor;
      if (component.getCustomLayoutConstraints() instanceof LwTabbedPane.Constraints) {
        titleDescriptor = ((LwTabbedPane.Constraints)component.getCustomLayoutConstraints()).myTitle;
      }
      else {
        titleDescriptor = null;
      }
      component.setCustomLayoutConstraints(null);
      final HashMap<String, StringDescriptor> id2Descriptor = getId2Descriptor(RadTabbedPane.this);
      LOG.debug("Added component with ID " + component.getId() + ", title " + (titleDescriptor == null ? "<null>" : titleDescriptor.toString()));
      id2Descriptor.put(component.getId(), titleDescriptor);
      tabbedPane.insertTab(calcTabName(titleDescriptor), null, component.getDelegee(), null, index);
    }


    @Override public void removeComponentFromContainer(final RadContainer container, final RadComponent component) {
      LOG.debug("Removing component with ID " + component.getId());
      final JTabbedPane tabbedPane = getTabbedPane();

      final JComponent delegee = component.getDelegee();
      final int i = tabbedPane.indexOfComponent(delegee);
      if (i == -1) {
        throw new IllegalArgumentException("cannot find tab for " + component);
      }
      final HashMap<String, StringDescriptor> id2Descriptor = getId2Descriptor(RadTabbedPane.this);
      StringDescriptor titleDescriptor = id2Descriptor.get(component.getId());
      if (titleDescriptor == null) {
        LOG.debug("title of removed component is null");
        titleDescriptor = StringDescriptor.create(tabbedPane.getTitleAt(i));
      }
      else {
        LOG.debug("title of removed component is " + titleDescriptor.toString());
      }
      component.setCustomLayoutConstraints(new LwTabbedPane.Constraints(titleDescriptor));
      id2Descriptor.remove(component.getId());
      tabbedPane.removeTabAt(i);
    }

    @Override public Property[] getComponentProperties(final Project project, final RadComponent component) {
      final JComponent delegee = component.getDelegee();
      final JTabbedPane tabbedPane = getTabbedPane();
      int index = tabbedPane.indexOfComponent(delegee);
      if (index >= 0) {
        return new Property[] { new MyTitleProperty(false, index) };
      }
      return Property.EMPTY_ARRAY;
    }

    @Override
    public boolean switchContainerToChild(RadContainer container, RadComponent child) {
      RadTabbedPane.this.selectTab(child);
      return true;
    }
  }

  private final class InsertTabDropLocation implements DropLocation {
    private int myInsertIndex;
    private String myInsertBeforeId;
    private Rectangle myFeedbackRect;

    public InsertTabDropLocation(final int insertIndex, final Rectangle feedbackRect) {
      myInsertIndex = insertIndex;
      if (myInsertIndex < getTabbedPane().getTabCount()) {
        myInsertBeforeId = getRadComponent(myInsertIndex).getId();
      }
      myFeedbackRect = feedbackRect;
    }

    public RadContainer getContainer() {
      return RadTabbedPane.this;
    }

    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1;
    }

    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      if (myInsertIndex < getTabbedPane().getTabCount()) {
        feedbackLayer.putFeedback(getDelegee(), myFeedbackRect, VertInsertFeedbackPainter.INSTANCE);
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
        feedbackLayer.putFeedback(getDelegee(), rcFeedback);
      }
    }

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
        RadContainer panel = (RadContainer) InsertComponentProcessor.createInsertedComponent(editor, palette.getPanelItem());
        assert panel != null;
        addComponent(panel);
        panel.getDropLocation(null).processDrop(editor, new RadComponent[] { componentToAdd }, null, palette.getPanelItem());
      }
      getTabbedPane().setSelectedIndex(myInsertIndex);
    }

    @Nullable
    public DropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }
}
