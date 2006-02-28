package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.lw.ComponentVisitor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IContainer;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.shared.BorderType;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class RadContainer extends RadComponent implements IContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.radComponents.RadContainer");

  /**
   * value: RadComponent[]
   */
  @NonNls
  public static final String PROP_CHILDREN = "children";
  /**
   * Children components
   */
  private final ArrayList<RadComponent> myComponents;
  /**
   * Describes border's type.
   */
  @NotNull private BorderType myBorderType;
  /**
   * Border's title. If border doesn't have any title then
   * this member is <code>null</code>.
   */
  @Nullable private StringDescriptor myBorderTitle;

  protected RadLayoutManager myLayoutManager;

  public RadContainer(final Module module, final String id){
    this(module, JPanel.class, id);
  }

  public RadContainer(final Module module, final Class aClass, final String id){
    super(module, aClass, id);

    myComponents = new ArrayList<RadComponent>();

    // By default container doesn't have any special border
    setBorderType(BorderType.NONE);

    myLayoutManager = createInitialLayoutManager();
    if (myLayoutManager != null) {
      setLayout(myLayoutManager.createLayout());
    }
    else {
      //noinspection OverriddenMethodCallInConstructor
      final AbstractLayout initialLayout = createInitialLayout();
      if (initialLayout != null){
        getDelegee().setLayout(initialLayout);
      }
    }
  }

  @Nullable protected RadLayoutManager createInitialLayoutManager() {
    return null;
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

  @Override @Nullable
  public Property getDefaultInplaceProperty() {
    return new MyBorderTitleProperty();
  }

  @Override @Nullable
  public Rectangle getDefaultInplaceEditorBounds() {
    return getBorderInPlaceEditorBounds(new MyBorderTitleProperty());
  }

  public Rectangle getInplaceEditorBounds(final Property property, final int x, final int y) {
    if(property instanceof MyBorderTitleProperty){ // If this is our property
      return getBorderInPlaceEditorBounds(property);
    }
    return super.getInplaceEditorBounds(property, x, y);
  }

  private Rectangle getBorderInPlaceEditorBounds(final Property property) {
    final MyBorderTitleProperty _property = (MyBorderTitleProperty)property;
    final Insets insets = getDelegee().getInsets();
    return new Rectangle(
      insets.left,
      0,
      getWidth() - insets.left - insets.right,
      _property.getPreferredSize().height
    );
  }

  @Nullable
  protected AbstractLayout createInitialLayout() {
    if (GuiDesignerConfiguration.getInstance(getModule().getProject()).IRIDA_LAYOUT_MODE) {
      return new XYLayoutManagerImpl();
    }
    return new GridLayoutManager(1, 1);
  }

  public final LayoutManager getLayout(){
    return getDelegee().getLayout();
  }

  public final void setLayout(final LayoutManager layout) {
    getDelegee().setLayout(layout);

    if (layout instanceof AbstractLayout) {
      AbstractLayout aLayout = (AbstractLayout) layout;
      for (int i=0; i < getComponentCount(); i++) {
        final RadComponent c = getComponent(i);
        aLayout.addLayoutComponent(c.getDelegee(), c.getConstraints());
      }
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
  public final void addComponent(@NotNull final RadComponent component, int index) {
    if (myComponents.contains(component)) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("component is already added: " + component);
    }

    final RadComponent[] oldChildren = myComponents.toArray(new RadComponent[myComponents.size()]);

    // Remove from old parent
    final RadContainer oldParent=component.getParent();
    if(oldParent!=null) {
      oldParent.removeComponent(component);
    }

    // Attach to new parent
    myComponents.add(index, component);
    component.setParent(this);
    addToDelegee(index, component);

    final RadComponent[] newChildren = myComponents.toArray(new RadComponent[myComponents.size()]);
    firePropertyChanged(PROP_CHILDREN, oldChildren, newChildren);
  }

  public final void addComponent(@NotNull final RadComponent component) {
    addComponent(component, myComponents.size());
  }

  protected void addToDelegee(final int index, final RadComponent component){
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
  public final void removeComponent(@NotNull final RadComponent component){
    if(!myComponents.contains(component)){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("component is not added: " + component);
    }

    final RadComponent[] oldChildren = myComponents.toArray(new RadComponent[myComponents.size()]);

    // Remove child
    component.setParent(null);
    myComponents.remove(component);
    removeFromDelegee(component);

    final RadComponent[] newChildren = myComponents.toArray(new RadComponent[myComponents.size()]);
    firePropertyChanged(PROP_CHILDREN, oldChildren, newChildren);
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

  public int indexOfComponent(RadComponent component) {
    return myComponents.indexOf(component);
  }

  /**
   * @return new array with all children
   */
  public final RadComponent[] getComponents() {
    return myComponents.toArray(new RadComponent[myComponents.size()]);
  }

  @Override public boolean canDrop(@Nullable Point location, final ComponentDragObject dragObject) {
    if (location == null) {
      if (isXY() || dragObject.getComponentCount() > 1) {
        return false;
      }
      return getComponentAtGrid(0, 0) == null;
    }

    if (isXY()) {
      return true;
    }
    else if (isGrid()) {
      final GridLayoutManager gridLayout = (GridLayoutManager)getLayout();
      final int row = gridLayout.getRowAt(location.y);
      final int column = gridLayout.getColumnAt(location.x);

      // If target point doesn't belong to any cell and column then do not allow drop. 
      if (row == -1 || column == -1) {
        LOG.debug("RadContainer.canDrop=false because no cell at mouse position");
        return false;
      }

      for(int i=0; i<dragObject.getComponentCount(); i++) {
        int relativeCol = dragObject.getRelativeCol(i);
        int relativeRow = dragObject.getRelativeRow(i);
        int colSpan = dragObject.getColSpan(i);
        int rowSpan = dragObject.getRowSpan(i);

        if (row + relativeRow < 0 ||
            column + relativeCol < 0 ||
            relativeRow + rowSpan > gridLayout.getRowCount() ||
            relativeCol + colSpan > gridLayout.getColumnCount()) {
          LOG.debug("RadContainer.canDrop=false because range is outside grid: row=" + (row+relativeRow) +
            ", col=" + (column+relativeCol) + ", colSpan=" + colSpan + ", rowSpan=" + rowSpan);
          return false;
        }

        final RadComponent componentInRect = findComponentInRect(row + relativeRow, column + relativeCol, rowSpan, colSpan);
        if (componentInRect != null) {
          LOG.debug("RadContainer.canDrop=false because found component " + componentInRect.getId() +
                    " in rect (row=" + (row+relativeRow) + ", col=" + (column+relativeCol) +
                    ", rowSpan=" + rowSpan + ", colSpan=" + colSpan + ")");
          return false;
        }
      }

      return true;
    }
    else {
      //noinspection HardCodedStringLiteral
      throw new IllegalStateException("unknown layout:" + getLayout());
    }
  }

  private RadComponent findComponentInRect(final int startRow, final int startCol, final int rowSpan, final int colSpan) {
    for(int r=startRow; r < startRow + rowSpan; r++) {
      for(int c=startCol; c < startCol + colSpan; c++) {
        final RadComponent result = getComponentAtGrid(r, c);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  @Nullable
  public RadComponent getComponentAtGrid(boolean rowFirst, int coord1, int coord2) {
    return rowFirst ? getComponentAtGrid(coord1, coord2) : getComponentAtGrid(coord2, coord1);
  }

  @Nullable
  public RadComponent getComponentAtGrid(final int row, final int column) {
    // If the target cell is not empty does not allow drop.
    for(int i=0; i<getComponentCount(); i++){
      final RadComponent component = getComponent(i);
      if (component.isDragging()) {
        continue;
      }
      final GridConstraints constraints=component.getConstraints();
      if(
        constraints.getRow() <= row && row < constraints.getRow()+constraints.getRowSpan() &&
        constraints.getColumn() <= column && column < constraints.getColumn()+constraints.getColSpan()
      ){
        return component;
      }
    }
    return null;
  }

  /**
   * @param location in delegee coordinates
   * @param components components to be dropped; length is always > 0. Location
   */
  public void drop(@Nullable Point location, RadComponent[] components, ComponentDragObject dragObject) {
    if (location == null) {
      assert isGrid() && components.length == 1;
      assert getComponentAtGrid(0, 0) == null;
      dropIntoGrid(components, 0, 0, dragObject);
      return;
    }

    if (isXY()) {
      int patchX = 0;
      int patchY = 0;

      for (int i = 0; i < components.length; i++) {
        final RadComponent c = components[i];

        final Point p = new Point(location);
        Point delta = dragObject.getDelta(i);
        if (delta != null) {
          p.translate(delta.x, delta.y);
        }
        c.setLocation(p);

        patchX = Math.min(patchX, p.x);
        patchY = Math.min(patchY, p.y);

        addComponent(c);
      }

      // shift components if necessary to make sure that no component has negative x or y
      if (patchX < 0 || patchY < 0) {
        for(RadComponent component : components) {
          component.shift(-patchX, -patchY);
        }
      }
    }
    else if (isGrid()) {

      // If target point doesn't belong to any cell and column
      // then cancel drop. If the target cell is not empty
      // then also cancel drop.

      final GridLayoutManager gridLayout = (GridLayoutManager)getLayout();
      final int row = gridLayout.getRowAt(location.y);
      final int column = gridLayout.getColumnAt(location.x);

      dropIntoGrid(components, row, column, dragObject);
    }
    else {
      //noinspection HardCodedStringLiteral
      throw new IllegalStateException("unknown layout: " + getLayout());
    }
  }

  public final void dropIntoGrid(final RadComponent[] components, int row, int column, final ComponentDragObject dragObject) {
    final GridLayoutManager gridLayout = (GridLayoutManager)getLayout();
    assert components.length > 0;

    for(int i=0; i<components.length; i++) {
      RadComponent c = components [i];
      if (c instanceof RadContainer) {
        final LayoutManager layout = ((RadContainer)c).getLayout();
        if (layout instanceof XYLayoutManager) {
          ((XYLayoutManager)layout).setPreferredSize(c.getSize());
        }
      }

      int relativeCol = dragObject.getRelativeCol(i);
      int relativeRow = dragObject.getRelativeRow(i);
      LOG.debug("dropIntoGrid: relativeRow=" + relativeRow + ", relativeCol=" + relativeCol);
      int colSpan = dragObject.getColSpan(i);
      int rowSpan = dragObject.getRowSpan(i);

      assert row + relativeRow >= 0;
      assert column + relativeCol >= 0;
      assert relativeRow + rowSpan <= gridLayout.getRowCount();
      assert relativeCol + colSpan <= gridLayout.getColumnCount();

      RadComponent old = findComponentInRect(row + relativeRow, column + relativeCol, rowSpan, colSpan);
      if (old != null) {
        LOG.assertTrue(false,
                       "Drop rectangle not empty: (" + (row + relativeRow) + ", " + (column + relativeCol)
                       + ", " + rowSpan + ", " + colSpan + "), component ID=" + old.getId());
      }

      final GridConstraints constraints = c.getConstraints();
      constraints.setRow(row + relativeRow);
      constraints.setColumn(column + relativeCol);
      constraints.setRowSpan(colSpan);
      constraints.setColSpan(rowSpan);
      addComponent(c);

      // Fill DropInfo
      c.revalidate();
    }

    revalidate();
  }

  /**
   * @return border's type.
   *
   * @see com.intellij.uiDesigner.shared.BorderType
   */
  @NotNull
  public final BorderType getBorderType(){
    return myBorderType;
  }

  /**
   * @see com.intellij.uiDesigner.shared.BorderType
   *
   * @exception java.lang.IllegalArgumentException if <code>type</code>
   * is <code>null</code>
   */
  public final void setBorderType(@NotNull final BorderType type){
    //noinspection ConstantConditions
    if(type==null){
      //noinspection HardCodedStringLiteral
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
  @Nullable
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
  public void updateBorder(){
    final String title = ReferenceUtil.resolve(this, myBorderTitle);
    getDelegee().setBorder(myBorderType.createBorder(title));
  }

  public RadLayoutManager getLayoutManager() {
    RadContainer parent = this;
    while(parent != null) {
      if (parent.myLayoutManager != null) {
        return parent.myLayoutManager;
      }
      parent = parent.getParent();
    }
    return null;
  }

  public void setLayoutManager(final RadLayoutManager layoutManager) {
    myLayoutManager = layoutManager;
    setLayout(myLayoutManager.createLayout());
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
        writer.writeStringDescriptor(descriptor, UIFormXmlConstants.ATTRIBUTE_TITLE,
                                     UIFormXmlConstants.ATTRIBUTE_TITLE_RESOURCE_BUNDLE,
                                     UIFormXmlConstants.ATTRIBUTE_TITLE_KEY);
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
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("unknown layout: " + getLayout());
    }
    try{
      writeId(writer);
      writeBinding(writer);

      if (myLayoutManager != null) {
        writer.addAttribute("layout-manager", myLayoutManager.getName());
      }

      getLayoutManager().writeLayout(writer, this);

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
  public void writeConstraints(final XmlWriter writer, @NotNull final RadComponent child){
    if(child.getParent() != this){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("parent mismatch: "+child.getParent());
    }
    getLayoutManager().writeChildConstraints(writer, child);
  }

  public boolean accept(ComponentVisitor visitor) {
    if (!super.accept(visitor)) {
      return false;
    }

    for (int i = 0; i < getComponentCount(); i++) {
      final IComponent c = getComponent(i);
      if (!c.accept(visitor)) {
        return false;
      }
    }

    return true;
  }

  protected void writeNoLayout(final XmlWriter writer) {
    writeId(writer);
    writeBinding(writer);

    // Constraints and properties
    writeConstraints(writer);
    writeProperties(writer);

    // Margin and border
    writeBorder(writer);
    writeChildren(writer);
  }

  private final class MyBorderTitleProperty extends Property{
    private final StringEditor myEditor;

    public MyBorderTitleProperty() {
      super(null, "Title");
      myEditor = new StringEditor(getModule().getProject());
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

    @NotNull
    public PropertyRenderer getRenderer() {
      return null;
    }

    public PropertyEditor getEditor() {
      return myEditor;
    }
  }
}