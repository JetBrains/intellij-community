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
    final String tabName;
    if (component.getCustomLayoutConstraints() instanceof LwTabbedPane.Constraints) {
      tabName = ((LwTabbedPane.Constraints)component.getCustomLayoutConstraints()).myTitle;
    }
    else {
      tabName = "Untitled";
    }
    component.setCustomLayoutConstraints(null);
    tabbedPane.addTab(tabName, component.getDelegee());
  }

  protected void removeFromDelegee(final RadComponent component){
    final JTabbedPane tabbedPane = getTabbedPane();

    final JComponent delegee = component.getDelegee();
    final int i = tabbedPane.indexOfComponent(delegee);
    if (i == -1) {
      throw new IllegalArgumentException("cannot find tab for " + component);
    }
    component.setCustomLayoutConstraints(new LwTabbedPane.Constraints(tabbedPane.getTitleAt(i)));
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
    final Rectangle tabBounds = ui.getTabBounds(tabbedPane, index);
    return tabBounds;
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
        throw new IllegalArgumentException("cannot find tab for " + child);
      }

      final String title = tabbedPane.getTitleAt(i);
      writer.addAttribute("title", title != null ? title : "");
    }finally{
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
      getTabbedPane().setTitleAt(myIndex, ResourceBundleLoader.resolve(getModule(), descriptor));
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