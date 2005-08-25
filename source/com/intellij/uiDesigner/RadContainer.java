package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.lw.IContainer;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.shared.BorderType;
import com.intellij.uiDesigner.shared.XYLayoutManager;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class RadContainer extends RadComponent implements IContainer {
  /**
   * value: RadComponent[]
   */
  public static final String PROP_CHILDREN = "children";
  /**
   * Children components
   */
  private final ArrayList<RadComponent> myComponents;
  private final MyPropertyChangeListener myChangeListener;
  /**
   * Describes border's type. This member is never <code>null</code>
   */
  private BorderType myBorderType;
  /**
   * Border's title. If border doesn't have any title then
   * this member is <code>null</code>.
   */
  private StringDescriptor myBorderTitle;

  protected RadContainer(final Module module, final String id){
    this(module, JPanel.class, id);
  }

  protected RadContainer(final Module module, final Class aClass, final String id){
    super(module, aClass, id);

    myComponents = new ArrayList<RadComponent>();
    myChangeListener = new MyPropertyChangeListener();

    // By default container doesn't have any special border
    setBorderType(BorderType.NONE);

    final AbstractLayout initialLayout = createInitialLayout();
    if (initialLayout != null){
      getDelegee().setLayout(initialLayout);
    }
  }

  public Property getInplaceProperty(final int x, final int y) {
    // 1. We have to check whether user clicked inside border (if any) or not.
    // In this case we have return inplace editor for border text
    final Insets insets = getDelegee().getInsets(); // border insets
    if(
      x < insets.left  || x > getWidth() - insets.right ||
      y < 0  || y > insets.top
    ){
      return super.getInplaceProperty(x, y);
    }

    // 2. Now we are sure that user clicked inside  title area
    return new MyBorderTitleProperty();
  }

  public Rectangle getInplaceEditorBounds(final Property property, final int x, final int y) {
    if(property instanceof MyBorderTitleProperty){ // If this is our property
      final MyBorderTitleProperty _property = (MyBorderTitleProperty)property;
      final Insets insets = getDelegee().getInsets();
      return new Rectangle(
        insets.left,
        0,
        getWidth() - insets.left - insets.right,
        _property.getPreferredSize().height
      );
    }
    return super.getInplaceEditorBounds(property, x, y);
  }

  protected AbstractLayout createInitialLayout(){
    return new XYLayoutManagerImpl();
  }

  public final LayoutManager getLayout(){
    return getDelegee().getLayout();
  }

  public final void setLayout(final AbstractLayout layout) {
    getDelegee().setLayout(layout);

    for (int i=0; i < getComponentCount(); i++) {
      final RadComponent c = getComponent(i);
      layout.addLayoutComponent(c.getDelegee(), c.getConstraints());
    }
  }

  public final boolean isGrid(){
    return getLayout() instanceof GridLayoutManager;
  }

  public final boolean isXY(){
    return getLayout() instanceof XYLayoutManager;
  }

  /**
   * @param component component to be added.
   *
   * @exception java.lang.IllegalArgumentException if <code>component</code> is <code>null</code>
   * @exception java.lang.IllegalArgumentException if <code>component</code> already exist in the
   * container
   */
  public final void addComponent(final RadComponent component){
    if (component == null) {
      throw new IllegalArgumentException("component cannot be null");
    }
    if (myComponents.contains(component)) {
      throw new IllegalArgumentException("component is already added: " + component);
    }

    final RadComponent[] oldChildren = myComponents.toArray(new RadComponent[myComponents.size()]);

    // Remove from old parent
    final RadContainer oldParent=component.getParent();
    if(oldParent!=null){
      oldParent.removeComponent(component);
    }

    // Attach to new parent
    myComponents.add(component);
    component.setParent(this);
    addToDelegee(component);
    
    component.addPropertyChangeListener(myChangeListener);

    final RadComponent[] newChildren = myComponents.toArray(new RadComponent[myComponents.size()]);
    firePropertyChanged(PROP_CHILDREN, oldChildren, newChildren);
  }

  protected void addToDelegee(final RadComponent component){
    getDelegee().add(component.getDelegee(), component.getConstraints(), 0);
  }

  /**
   * Removes specified <code>component</code> from the container.
   * This method also removes component's delegee from the
   * container's delegee. Client code is responsible for revalidation
   * of invalid Swing hierarchy.
   *
   * @param component component to be removed.
   *
   * @exception java.lang.IllegalArgumentException if <code>component</code>
   * is <code>null</code>
   * @exception java.lang.IllegalArgumentException if <code>component</code>
   * doesn't exist in the container
   */
  public final void removeComponent(final RadComponent component){
    if (component == null) {
      throw new IllegalArgumentException("component cannot be null");
    }
    if(!myComponents.contains(component)){
      throw new IllegalArgumentException("component is not added: " + component);
    }

    // Remove child
    component.removePropertyChangeListener(myChangeListener);
    component.setParent(null);
    myComponents.remove(component);
    removeFromDelegee(component);
  }

  protected void removeFromDelegee(final RadComponent component){
    getDelegee().remove(component.getDelegee());
  }

  public final RadComponent getComponent(final int index) {
    return myComponents.get(index);
  }

  public final int getComponentCount() {
    return myComponents.size();
  }
  
  /**
   * @return new array with all children
   */ 
  public final RadComponent[] getComponents() {
    return myComponents.toArray(new RadComponent[myComponents.size()]);
  }

  public boolean canDrop(final int x, final int y, final int componentCount){
    if (isXY()) {
      return true;
    }
    else if (isGrid()) {
      // Do not allow to drop more then one component into grid
      if (componentCount > 1) {
        return false;
      }

      final GridLayoutManager gridLayout = (GridLayoutManager)getLayout();
      final int row = gridLayout.getRowAt(y);
      final int column = gridLayout.getColumnAt(x);

      // If target point doesn't belong to any cell and column then do not allow drop. 
      if (row == -1 || column == -1) {
        return false;
      }

      // If the target cell is not empty does not allow drop.
      for(int i=0; i<getComponentCount(); i++){
        final GridConstraints c=getComponent(i).getConstraints();
        if(
          c.getRow() <= row && row < c.getRow()+c.getRowSpan() &&
          c.getColumn() <= column && column < c.getColumn()+c.getColSpan()
        ){
          return false;
        }
      }
      
      return true;
    }
    else {
      throw new IllegalStateException("unknown layout:" + getLayout());
    }
  }

  /**
   * @param x in delegee coordinates
   * @param y in delegee coordinates
   * @param components components to be dropped; length is always > 0. Location
   * @param dx shift of component relative to x
   * @param dx shift of component relative to y
   */
  public DropInfo drop(final int x, final int y, final RadComponent[] components, final int[] dx, final int[] dy){
    if (isXY()) {
      int patchX = 0;
      int patchY = 0;

      for (int i = 0; i < components.length; i++) {
        final RadComponent c = components[i];

        final Point p = new Point(x + dx[i], y + dy[i]);
        c.setLocation(p);

        patchX = Math.min(patchX, p.x);
        patchY = Math.min(patchY, p.y);

        addComponent(c);
      }

      // shift components if necessary to make sure that no component has negative x or y
      if (patchX < 0 || patchY < 0) {
        for (int i = 0; i < components.length; i++) {
          components[i].shift(-patchX, -patchY);
        }
      }
      return new DropInfo(this, null, null);
    }
    else if (isGrid()) {

      // If target point doesn't belong to any cell and column
      // then cancel drop. If the target cell is not empty
      // then also cancel drop.

      final GridLayoutManager gridLayout = (GridLayoutManager)getLayout();
      final int row = gridLayout.getRowAt(y);
      final int column = gridLayout.getColumnAt(x);

      // Prepare component for drop.
      final RadComponent c = components[0];

      if (c instanceof RadContainer) {
        final LayoutManager layout = ((RadContainer)c).getLayout();
        if (layout instanceof XYLayoutManager) {
          ((XYLayoutManager)layout).setPreferredSize(c.getSize());
        }
      }

      final GridConstraints constraints = c.getConstraints();
      constraints.setRow(row);
      constraints.setColumn(column);
      constraints.setRowSpan(1);
      constraints.setColSpan(1);
      addComponent(c);

      // Fill DropInfo
      final RevalidateInfo info = c.revalidate();
      
      return new DropInfo(this, info.myContainer, info.myPreviousContainerSize);
    }
    else {
      throw new IllegalStateException("unknown layout: " + getLayout());
    }
  }

  /**
   * @return border's type. The method never return <code>null</code>.
   *
   * @see com.intellij.uiDesigner.shared.BorderType
   */
  public final BorderType getBorderType(){
    return myBorderType;
  }

  /**
   * @see com.intellij.uiDesigner.shared.BorderType
   *
   * @exception java.lang.IllegalArgumentException if <code>type</code>
   * is <code>null</code>
   */
  public final void setBorderType(final BorderType type){
    if(type==null){
      throw new IllegalArgumentException("type cannot be null");
    }
    if(myBorderType==type){
      return;
    }
    myBorderType=type;
    updateBorder();
  }

  /**
   * @return border's title. If the container doesn't have any title then the
   * method returns <code>null</code>.
   */
  public final StringDescriptor getBorderTitle(){
    return myBorderTitle;
  }

  /**
   * @param title new border's title. <code>null</code> means that
   * the containr doesn't have have titled border.
   */
  public final void setBorderTitle(final StringDescriptor title){
    if(Comparing.equal(title,myBorderTitle)){
      return;
    }
    myBorderTitle = title;
    updateBorder();
  }

  /**
   * Updates delegee's border
   */
  private void updateBorder(){
    final String title = ReferenceUtil.resolve(getModule(), myBorderTitle);
    getDelegee().setBorder(myBorderType.createBorder(title));
  }

  /**
   * Serializes container's border
   */
  protected final void writeBorder(final XmlWriter writer){
    writer.startElement("border");
    try{
      writer.addAttribute("type", getBorderType().getId());
      if (getBorderTitle() != null) {
        final StringDescriptor descriptor = getBorderTitle();
        if(descriptor.getValue() != null){ // direct value
          writer.addAttribute("title", descriptor.getValue());
        }
        else{ // via resource bundle
          writer.addAttribute("title-resource-bundle", descriptor.getBundleName());
          writer.addAttribute("title-key", descriptor.getKey());
        }

      }
    }finally{
      writer.endElement(); // border
    }
  }

  /**
   * Serializes container's children
   */
  protected final void writeChildren(final XmlWriter writer){
    // Children
    writer.startElement("children");
    try{
      writeChildrenImpl(writer);
    }finally{
      writer.endElement(); // children
    }
  }

  protected final void writeChildrenImpl(final XmlWriter writer){
    for (int i=0; i < getComponentCount(); i++) {
      getComponent(i).write(writer);
    }
  }

  public void write(final XmlWriter writer) {
    if (isXY()) {
      writer.startElement("xy");
    }
    else if (isGrid()) {
      writer.startElement("grid");
    }
    else {
      throw new IllegalArgumentException("unknown layout: " + getLayout());
    }
    try{
      writeId(writer);
      writeBinding(writer);

      final AbstractLayout layout = (AbstractLayout)getLayout();
      if (isGrid()) {
        final GridLayoutManager _layout = (GridLayoutManager)layout;
        writer.addAttribute("row-count", _layout.getRowCount());
        writer.addAttribute("column-count", _layout.getColumnCount());

        writer.addAttribute("same-size-horizontally", _layout.isSameSizeHorizontally());
        writer.addAttribute("same-size-vertically", _layout.isSameSizeVertically());
      }
      // It has sense to save hpap and vgap even for XY layout. The reason is
      // that XY was previously GRID with non default gaps, so when the user
      // compose XY into the grid again then he will get the same non default gaps.
      writer.addAttribute("hgap", layout.getHGap());
      writer.addAttribute("vgap", layout.getVGap());

      // Margins
      final Insets margin = layout.getMargin();
      writer.startElement("margin");
      try {
        writer.addAttribute("top", margin.top);
        writer.addAttribute("left", margin.left);
        writer.addAttribute("bottom", margin.bottom);
        writer.addAttribute("right", margin.right);
      }
      finally {
        writer.endElement(); // margin
      }

      // Constraints and properties
      writeConstraints(writer);
      writeProperties(writer);

      // Border
      writeBorder(writer);

      // Children
      writeChildren(writer);
    }finally{
      writer.endElement(); // xy/grid
    }
  }

  /**
   * Serializes child constraints into the currently opened "constraints" tag
   */
  public void writeConstraints(final XmlWriter writer, final RadComponent child){
    if (child == null) {
      throw new IllegalArgumentException("child cannot be null");
    }
    if(child.getParent() != this){
      throw new IllegalArgumentException("parent mismatch: "+child.getParent());
    }
    // Constraints of XY layout
    writer.startElement("xy");
    try{
      writer.addAttribute("x", child.getX());
      writer.addAttribute("y", child.getY());
      writer.addAttribute("width", child.getWidth());
      writer.addAttribute("height", child.getHeight());
    }finally{
      writer.endElement(); // xy
    }

    // Constraints in Grid layout
    writer.startElement("grid");
    try {
      final GridConstraints constraints = child.getConstraints();
      writer.addAttribute("row",constraints.getRow());
      writer.addAttribute("column",constraints.getColumn());
      writer.addAttribute("row-span",constraints.getRowSpan());
      writer.addAttribute("col-span",constraints.getColSpan());
      writer.addAttribute("vsize-policy",constraints.getVSizePolicy());
      writer.addAttribute("hsize-policy",constraints.getHSizePolicy());
      writer.addAttribute("anchor",constraints.getAnchor());
      writer.addAttribute("fill",constraints.getFill());

      // preferred size
      writer.writeDimension(constraints.myMinimumSize,"minimum-size");
      writer.writeDimension(constraints.myPreferredSize,"preferred-size");
      writer.writeDimension(constraints.myMaximumSize,"maximum-size");
    } finally {
      writer.endElement(); // grid
    }
  }

  private final class MyPropertyChangeListener implements PropertyChangeListener{
    public void propertyChange(final PropertyChangeEvent e){
      if (RadComponent.PROP_SELECTED.equals(e.getPropertyName())) {
        if (!((Boolean)e.getNewValue()).booleanValue()) {
          return;
        }

        if (getComponentCount() > 1 && isXY()) {
          final RadComponent component = (RadComponent)e.getSource();
          final Rectangle bounds = component.getBounds();
          // We have to change Z order of the selected component only
          // if this component intersects one of its siblings.
          for(int i = getComponentCount() - 1; i >= 0; i--){
            final RadComponent _component = getComponent(i);
            if(_component == component){
              continue;
            }
            if(bounds.intersects(_component.getBounds())){
              removeComponent(component);
              addComponent(component);
              // TODO[anton,vova] the change should be saved to document!!!
              break;
            }
          }
        }
      }
    }
  }

  private final class MyBorderTitleProperty extends Property{
    private final StringEditor myEditor;

    public MyBorderTitleProperty() {
      super(null, "Title");
      myEditor = new StringEditor();
    }

    public Dimension getPreferredSize(){
      return myEditor.getPreferredSize();
    }

    /**
     * @return {@link StringDescriptor}
     */
    public Object getValue(final RadComponent component) {
      return myBorderTitle;
    }

    protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
      setBorderTitle((StringDescriptor)value);
    }

    public Property[] getChildren() {
      return Property.EMPTY_ARRAY;
    }

    public PropertyRenderer getRenderer() {
      return null;
    }

    public PropertyEditor getEditor() {
      return myEditor;
    }
  }
}