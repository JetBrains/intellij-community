package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.shared.BorderType;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
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
  private int myBorderTitleJustification;
  private int myBorderTitlePosition;
  private FontDescriptor myBorderTitleFont;
  private ColorDescriptor myBorderTitleColor;

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
      final LayoutManager layoutManager = myLayoutManager.createLayout();
      if (layoutManager != null) {
        setLayout(layoutManager);
      }
    }
  }

  public RadContainer(@NotNull final Class aClass, @NotNull final String id, final Palette palette) {
    this(null, aClass, id);
    setPalette(palette);
  }

  @Nullable protected RadLayoutManager createInitialLayoutManager() {
    String defaultLayoutManager = UIFormXmlConstants.LAYOUT_INTELLIJ;
    if (getModule() != null) {
      final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(getModule().getProject());
      if (configuration.IRIDA_LAYOUT_MODE) {
        return new RadXYLayoutManager();
      }
      defaultLayoutManager = configuration.DEFAULT_LAYOUT_MANAGER;
    }

    try {
      return LayoutManagerRegistry.createLayoutManager(defaultLayoutManager);
    }
    catch (Exception e) {
      LOG.error(e);
      return new RadGridLayoutManager();
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
    myLayoutManager.addComponentToContainer(this, component, index);

    final RadComponent[] newChildren = myComponents.toArray(new RadComponent[myComponents.size()]);
    firePropertyChanged(PROP_CHILDREN, oldChildren, newChildren);
  }

  public final void addComponent(@NotNull final RadComponent component) {
    addComponent(component, myComponents.size());
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
    myLayoutManager.removeComponentFromContainer(this, component);

    final RadComponent[] newChildren = myComponents.toArray(new RadComponent[myComponents.size()]);
    firePropertyChanged(PROP_CHILDREN, oldChildren, newChildren);
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

  @NotNull
  public DropLocation getDropLocation(@Nullable Point location) {
    return getLayoutManager().getDropLocation(this, location);
  }

  public RadComponent findComponentInRect(final int startRow, final int startCol, final int rowSpan, final int colSpan) {
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
  public RadComponent getComponentAtGrid(int row, int col) {
    return getLayoutManager().getComponentAtGrid(this, row, col);
  }

  public int getGridRowCount() {
    return myLayoutManager.getGridRowCount(this);
  }

  public int getGridColumnCount() {
    return myLayoutManager.getGridColumnCount(this);
  }

  public int getGridRowAt(int y) {
    return myLayoutManager.getGridRowAt(this, y);
  }

  public int getGridColumnAt(int x) {
    return myLayoutManager.getGridColumnAt(this, x);
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

  public int getBorderTitleJustification() {
    return myBorderTitleJustification;
  }

  public void setBorderTitleJustification(final int borderTitleJustification) {
    if (myBorderTitleJustification != borderTitleJustification) {
      myBorderTitleJustification = borderTitleJustification;
      updateBorder();
    }
  }

  public int getBorderTitlePosition() {
    return myBorderTitlePosition;
  }

  public void setBorderTitlePosition(final int borderTitlePosition) {
    if (myBorderTitlePosition != borderTitlePosition) {
      myBorderTitlePosition = borderTitlePosition;
      updateBorder();
    }
  }

  public FontDescriptor getBorderTitleFont() {
    return myBorderTitleFont;
  }

  public void setBorderTitleFont(final FontDescriptor borderTitleFont) {
    if (!Comparing.equal(myBorderTitleFont, borderTitleFont)) {
      myBorderTitleFont = borderTitleFont;
      updateBorder();
    }
  }

  public ColorDescriptor getBorderTitleColor() {
    return myBorderTitleColor;
  }

  public void setBorderTitleColor(final ColorDescriptor borderTitleColor) {
    if (!Comparing.equal(myBorderTitleColor, borderTitleColor)) {
      myBorderTitleColor = borderTitleColor;
      updateBorder();
    }
  }

  /**
   * Updates delegee's border
   */
  public void updateBorder() {
    String title = null;
    if (myBorderTitle != null) {
      title = StringDescriptorManager.getInstance(getModule()).resolve(this, myBorderTitle);
    }
    Font font = (myBorderTitleFont != null) ? myBorderTitleFont.getResolvedFont() : null;
    Color color = (myBorderTitleColor != null) ? myBorderTitleColor.getResolvedColor() : null;
    getDelegee().setBorder(myBorderType.createBorder(title, myBorderTitleJustification, myBorderTitlePosition,
                                                     font, color));
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

  public void setLayoutManager(RadLayoutManager layoutManager, LayoutManager layout) {
    myLayoutManager = layoutManager;
    setLayout(layout);
  }

  public RadComponent getComponentToResize(RadComponent child) {
    return child;
  }

  /**
   * Serializes container's border
   */
  protected final void writeBorder(final XmlWriter writer){
    writer.startElement(UIFormXmlConstants.ELEMENT_BORDER);
    try{
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_TYPE, getBorderType().getId());
      if (getBorderTitle() != null) {
        final StringDescriptor descriptor = getBorderTitle();
        writer.writeStringDescriptor(descriptor, UIFormXmlConstants.ATTRIBUTE_TITLE,
                                     UIFormXmlConstants.ATTRIBUTE_TITLE_RESOURCE_BUNDLE,
                                     UIFormXmlConstants.ATTRIBUTE_TITLE_KEY);
      }
      if (myBorderTitleJustification != 0) {
        writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_TITLE_JUSTIFICATION, myBorderTitleJustification);
      }
      if (myBorderTitlePosition != 0) {
        writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_TITLE_POSITION, myBorderTitlePosition);
      }
      if (myBorderTitleFont != null) {
        writer.startElement(UIFormXmlConstants.ELEMENT_FONT);
        writer.writeFontDescriptor(myBorderTitleFont);
        writer.endElement();
      }
      if (myBorderTitleColor != null) {
        writer.startElement(UIFormXmlConstants.ELEMENT_COLOR);
        writer.writeColorDescriptor(myBorderTitleColor);
        writer.endElement();
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
    else {
      writer.startElement("grid");
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

  @Override
  protected void importSnapshotComponent(final SnapshotContext context, final JComponent component) {
    getLayoutManager().createSnapshotLayout(context, component, this, component.getLayout());
    importSnapshotBorder(component);
    for(Component child: component.getComponents()) {
      if (child instanceof JComponent) {
        RadComponent childComponent = createSnapshotComponent(context, (JComponent) child);
        if (childComponent != null) {
          getLayoutManager().addSnapshotComponent(component, (JComponent) child, this, childComponent);
        }
      }
    }
  }

  private void importSnapshotBorder(final JComponent component) {
    Border border = component.getBorder();
    if (border != null) {
      if (border instanceof TitledBorder) {
        TitledBorder titledBorder = (TitledBorder) border;
        setBorderTitle(StringDescriptor.create(titledBorder.getTitle()));
        setBorderTitleJustification(titledBorder.getTitleJustification());
        setBorderTitlePosition(titledBorder.getTitlePosition());
        setBorderTitleFont(new FontDescriptor(titledBorder.getTitleFont()));
        setBorderTitleColor(new ColorDescriptor(titledBorder.getTitleColor()));
        border = titledBorder.getBorder();
      }

      if (border instanceof EtchedBorder) {
        setBorderType(BorderType.ETCHED);
      }
      else if (border instanceof BevelBorder) {
        BevelBorder bevelBorder = (BevelBorder) border;
        setBorderType(bevelBorder.getBevelType() == BevelBorder.RAISED ? BorderType.BEVEL_RAISED : BorderType.BEVEL_LOWERED);
      }
    }
  }

  private final class MyBorderTitleProperty extends Property<RadContainer, StringDescriptor> {
    private final StringEditor myEditor;

    public MyBorderTitleProperty() {
      super(null, "Title");
      myEditor = new StringEditor(getModule().getProject());
    }

    public Dimension getPreferredSize(){
      return myEditor.getPreferredSize();
    }

    public StringDescriptor getValue(final RadContainer component) {
      return myBorderTitle;
    }

    protected void setValueImpl(final RadContainer container, final StringDescriptor value) throws Exception {
      setBorderTitle(value);
    }

    @NotNull
    public PropertyRenderer<StringDescriptor> getRenderer() {
      return null;
    }

    public PropertyEditor<StringDescriptor> getEditor() {
      return myEditor;
    }
  }
}