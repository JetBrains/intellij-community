package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.ITabbedPane;
import com.intellij.uiDesigner.lw.LwTabbedPane;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadTabbedPane extends RadContainer implements ITabbedPane {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.RadTabbedPane");
  /**
   * value: HashMap<String, StringDescriptor>
   */
  @NonNls
  private static final String CLIENT_PROP_ID_2_DESCRIPTOR = "index2descriptor";

  public RadTabbedPane(final Module module, final String id){
    super(module, JTabbedPane.class, id);
  }

  protected AbstractLayout createInitialLayout(){
    return null;
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

  @Override public DropLocation getDropLocation(@Nullable Point location) {
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
    return new AddTabDropLocation();
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

  @Override protected void addToDelegee(final int index, final RadComponent component) {
    final JTabbedPane tabbedPane = getTabbedPane();
    final StringDescriptor titleDescriptor;
    if (component.getCustomLayoutConstraints() instanceof LwTabbedPane.Constraints) {
      titleDescriptor = ((LwTabbedPane.Constraints)component.getCustomLayoutConstraints()).myTitle;
    }
    else {
      titleDescriptor = null;
    }
    component.setCustomLayoutConstraints(null);
    final HashMap<String, StringDescriptor> id2Descriptor = getId2Descriptor(this);
    tabbedPane.insertTab(calcTabName(titleDescriptor), null, component.getDelegee(), null, index);
    id2Descriptor.put(component.getId(), titleDescriptor);
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

  protected void removeFromDelegee(final RadComponent component){
    final JTabbedPane tabbedPane = getTabbedPane();

    final JComponent delegee = component.getDelegee();
    final int i = tabbedPane.indexOfComponent(delegee);
    if (i == -1) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("cannot find tab for " + component);
    }
    final HashMap<String, StringDescriptor> id2Descriptor = getId2Descriptor(this);
    StringDescriptor titleDescriptor = id2Descriptor.get(component.getId());
    if (titleDescriptor == null) {
      titleDescriptor = StringDescriptor.create(tabbedPane.getTitleAt(i));
    }
    component.setCustomLayoutConstraints(new LwTabbedPane.Constraints(titleDescriptor));
    id2Descriptor.remove(component.getId());
    tabbedPane.removeTabAt(i);
  }

  /**
   * @return inplace property for editing of the title of the clicked tab
   */
  public Property getInplaceProperty(final int x, final int y) {
    final JTabbedPane tabbedPane = getTabbedPane();
    final TabbedPaneUI ui = tabbedPane.getUI();
    LOG.assertTrue(ui != null);
    final int index = ui.tabForCoordinate(tabbedPane, x, y);
    return index != -1 ? new MyTitleProperty(index) : null;
  }

  @Override @Nullable
  public Property getDefaultInplaceProperty() {
    final int index = getTabbedPane().getSelectedIndex();
    if (index >= 0) {
      return new MyTitleProperty(index);
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
      new MyTitleProperty(index).setValue(this, title);
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

  public void writeConstraints(final XmlWriter writer, final RadComponent child) {
    writer.startElement("tabbedpane");
    try{
      final JComponent delegee = child.getDelegee();
      final JTabbedPane tabbedPane = getTabbedPane();
      final int i = tabbedPane.indexOfComponent(delegee);
      if (i == -1) {
        //noinspection HardCodedStringLiteral
        throw new IllegalArgumentException("cannot find tab for " + child);
      }

      final HashMap<String, StringDescriptor> id2Descriptor = getId2Descriptor(this);
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

  @NotNull
  private static HashMap<String, StringDescriptor> getId2Descriptor(final RadComponent component){
    HashMap<String, StringDescriptor> id2Descriptor = (HashMap<String, StringDescriptor>)component.getClientProperty(CLIENT_PROP_ID_2_DESCRIPTOR);
    if(id2Descriptor == null){
      id2Descriptor = new HashMap<String,StringDescriptor>();
      component.putClientProperty(CLIENT_PROP_ID_2_DESCRIPTOR, id2Descriptor);
    }
    return id2Descriptor;
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

  private final class MyTitleProperty extends Property{
    /**
     * Index of tab which title should be edited
     */
    private final int myIndex;
    private final StringEditor myEditor;

    public MyTitleProperty(final int index) {
      super(null, "Title");
      myIndex = index;
      myEditor = new StringEditor(getModule().getProject());
    }

    public Object getValue(final RadComponent component) {
      // 1. resource bundle
      final StringDescriptor descriptor = getId2Descriptor(component).get(component.getId());
      if(descriptor != null){
        return descriptor;
      }

      // 2. plain value
      return StringDescriptor.create(getTabbedPane().getTitleAt(myIndex));
    }

    protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
      // 1. Put value into map
      final StringDescriptor descriptor = (StringDescriptor)value;
      final HashMap<String, StringDescriptor> id2Descriptor = getId2Descriptor(component);
      if(descriptor == null){
        id2Descriptor.remove(component.getId());
      }
      else{
        id2Descriptor.put(component.getId(), descriptor);
      }

      // 2. Apply real string value to JComponent peer
      getTabbedPane().setTitleAt(myIndex, ReferenceUtil.resolve(RadTabbedPane.this, descriptor));
    }

    @NotNull
    public PropertyRenderer getRenderer() {
      throw new UnsupportedOperationException();
    }

    public PropertyEditor getEditor() {
      return myEditor;
    }
  }

  private final class InsertTabDropLocation implements DropLocation {
    private int myInsertIndex;
    private String myInsertBeforeId;
    private Rectangle myFeedbackRect;

    public InsertTabDropLocation(final int insertIndex, final Rectangle feedbackRect) {
      myInsertIndex = insertIndex;
      myInsertBeforeId = getRadComponent(myInsertIndex).getId();
      myFeedbackRect = feedbackRect;
    }

    public RadContainer getContainer() {
      return RadTabbedPane.this;
    }

    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1;
    }

    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      feedbackLayer.putFeedback(getDelegee(), myFeedbackRect, VertInsertFeedbackPainter.INSTANCE);
    }

    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      for(int i=0; i<getTabbedPane().getTabCount(); i++) {
        if (getRadComponent(i).getId().equals(myInsertBeforeId)) {
          myInsertIndex = i;
          break;
        }
      }
      addComponent(components [0], myInsertIndex);
      getTabbedPane().setSelectedIndex(myInsertIndex);
    }
  }

  private final class AddTabDropLocation implements DropLocation {

    public RadContainer getContainer() {
      return RadTabbedPane.this;
    }

    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1;
    }

    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
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

    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      addComponent(components [0]);
      getTabbedPane().setSelectedIndex(getTabbedPane().getTabCount()-1);
    }
  }
}
