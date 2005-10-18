package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.lw.LwTabbedPane;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadTabbedPane extends RadContainer{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.RadTabbedPane");
  /**
   * value: HashMap<Integer, StringDescriptor>
   */
  @NonNls
  private static final String CLIENT_PROP_INDEX_2_DESCRIPTOR = "index2descriptor";

  public RadTabbedPane(final Module module, final String id){
    super(module, JTabbedPane.class, id);
  }

  protected AbstractLayout createInitialLayout(){
    return null;
  }

  public boolean canDrop(final int x, final int y, final int componentCount){
    return componentCount == 1;
  }

  public DropInfo drop(final int x, final int y, final RadComponent[] components, final int[] dx, final int[] dy){
    addComponent(components[0]);
    return new DropInfo(this, null, null);
  }

  /**
   * @return never returns <code>null</code>.
   */
  private JTabbedPane getTabbedPane(){
    return (JTabbedPane)getDelegee();
  }

  protected void addToDelegee(final RadComponent component){
    final JTabbedPane tabbedPane = getTabbedPane();
    final StringDescriptor titleDescriptor;
    if (component.getCustomLayoutConstraints() instanceof LwTabbedPane.Constraints) {
      titleDescriptor = ((LwTabbedPane.Constraints)component.getCustomLayoutConstraints()).myTitle;
    }
    else {
      titleDescriptor = null;
    }
    component.setCustomLayoutConstraints(null);
    tabbedPane.addTab(calcTabName(titleDescriptor), component.getDelegee());
    if (titleDescriptor != null) {
      getIndex2Descriptor(this).put(new Integer(tabbedPane.getTabCount() - 1), titleDescriptor);
    }
  }

  private String calcTabName(final StringDescriptor titleDescriptor) {
    if (titleDescriptor == null) {
      return UIDesignerBundle.message("tab.untitled");
    }
    final String value = titleDescriptor.getValue();
    if (value == null) { // from res bundle
      return ReferenceUtil.resolve(getModule(), titleDescriptor);
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
    StringDescriptor titleDescriptor = getIndex2Descriptor(this).get(new Integer(i));
    if (titleDescriptor == null) {
      titleDescriptor = StringDescriptor.create(tabbedPane.getTitleAt(i));
    }
    component.setCustomLayoutConstraints(new LwTabbedPane.Constraints(titleDescriptor));
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

  public Rectangle getInplaceEditorBounds(final Property property, final int x, final int y) {
    final JTabbedPane tabbedPane = getTabbedPane();
    final TabbedPaneUI ui = tabbedPane.getUI();
    LOG.assertTrue(ui != null);
    final int index = ui.tabForCoordinate(tabbedPane, x, y);
    LOG.assertTrue(index != -1);
    return ui.getTabBounds(tabbedPane, index);
  }

  @Nullable
  public StringDescriptor getChildTitle(RadComponent component) {
    final JComponent delegee = component.getDelegee();
    final JTabbedPane tabbedPane = getTabbedPane();
    int index = tabbedPane.indexOfComponent(delegee);
    return getIndex2Descriptor(this).get(index);
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

      final HashMap<Integer, StringDescriptor> index2Descriptor = getIndex2Descriptor(this);
      final StringDescriptor tabTitleDescriptor = index2Descriptor.get(new Integer(i));
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

  /**
   * @return never returns <code>null</code>.
   */
  private HashMap<Integer, StringDescriptor> getIndex2Descriptor(final RadComponent component){
    HashMap<Integer, StringDescriptor> index2Descriptor = (HashMap<Integer, StringDescriptor>)component.getClientProperty(CLIENT_PROP_INDEX_2_DESCRIPTOR);
    if(index2Descriptor == null){
      index2Descriptor = new HashMap<Integer,StringDescriptor>();
      component.putClientProperty(CLIENT_PROP_INDEX_2_DESCRIPTOR, index2Descriptor);
    }
    return index2Descriptor;
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
      myEditor = new StringEditor();
    }

    public Object getValue(final RadComponent component) {
      // 1. resource bundle
      final StringDescriptor descriptor = getIndex2Descriptor(component).get(new Integer(myIndex));
      if(descriptor != null){
        return descriptor;
      }

      // 2. plain value
      return StringDescriptor.create(getTabbedPane().getTitleAt(myIndex));
    }

    protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
      // 1. Put value into map
      final StringDescriptor descriptor = (StringDescriptor)value;
      final HashMap<Integer, StringDescriptor> index2Descriptor = getIndex2Descriptor(component);
      if(descriptor == null || descriptor.getBundleName() == null){
        index2Descriptor.remove(new Integer(myIndex));
      }
      else{
        index2Descriptor.put(new Integer(myIndex), descriptor);
      }

      // 2. Apply real string value to JComponent peer
      getTabbedPane().setTitleAt(myIndex, ReferenceUtil.resolve(getModule(), descriptor));
    }

    public Property[] getChildren() {
      return Property.EMPTY_ARRAY;
    }

    public PropertyRenderer getRenderer() {
      throw new UnsupportedOperationException();
    }

    public PropertyEditor getEditor() {
      return myEditor;
    }
  }
}