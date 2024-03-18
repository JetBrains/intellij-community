// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.palette.Palette;
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
import java.util.Objects;

public class RadContainer extends RadComponent implements IContainer {
  private static final Logger LOG = Logger.getInstance(RadContainer.class);

  public static class Factory extends RadComponentFactory {
    @Override
    public RadComponent newInstance(ModuleProvider module, Class aClass, String id) {
      return new RadContainer(module, aClass, id);
    }

    @Override
    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      return new RadContainer(componentClass, id, palette);
    }
  }

  /**
   * value: RadComponent[]
   */
  public static final @NonNls String PROP_CHILDREN = "children";
  /**
   * Children components
   */
  private final ArrayList<RadComponent> myComponents;
  /**
   * Describes border's type.
   */
  private @NotNull BorderType myBorderType;
  /**
   * Border's title. If border doesn't have any title then
   * this member is {@code null}.
   */
  private @Nullable StringDescriptor myBorderTitle;
  private int myBorderTitleJustification;
  private int myBorderTitlePosition;
  private FontDescriptor myBorderTitleFont;
  private ColorDescriptor myBorderTitleColor;
  private Insets myBorderSize;
  private ColorDescriptor myBorderColor;

  protected RadLayoutManager myLayoutManager;
  private LayoutManager myDelegeeLayout;

  public RadContainer(final ModuleProvider module, final String id) {
    this(module, JPanel.class, id);
  }

  public RadContainer(final ModuleProvider module, final Class aClass, final String id) {
    super(module, aClass, id);

    myComponents = new ArrayList<>();

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

  public RadContainer(final @NotNull Class aClass, final @NotNull String id, final Palette palette) {
    this(null, aClass, id);
    setPalette(palette);
  }

  protected @Nullable RadLayoutManager createInitialLayoutManager() {
    String defaultLayoutManager = UIFormXmlConstants.LAYOUT_INTELLIJ;
    if (getModule() != null) {
      final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(getProject());
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

  @Override
  public Property getInplaceProperty(final int x, final int y) {
    // 1. We have to check whether user clicked inside border (if any) or not.
    // In this case we have return inplace editor for border text
    final Insets insets = getDelegee().getInsets(); // border insets
    if (
      x < insets.left || x > getWidth() - insets.right ||
      y < 0 || y > insets.top
      ) {
      return super.getInplaceProperty(x, y);
    }

    // 2. Now we are sure that user clicked inside  title area
    return new MyBorderTitleProperty();
  }

  @Override
  public @Nullable Property getDefaultInplaceProperty() {
    return new MyBorderTitleProperty();
  }

  @Override
  public @Nullable Rectangle getDefaultInplaceEditorBounds() {
    return getBorderInPlaceEditorBounds(new MyBorderTitleProperty());
  }

  @Override
  public Rectangle getInplaceEditorBounds(final Property property, final int x, final int y) {
    if (property instanceof MyBorderTitleProperty) { // If this is our property
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

  public final LayoutManager getLayout() {
    if (myDelegeeLayout != null) {
      return myDelegeeLayout;
    }
    return getDelegee().getLayout();
  }

  public final void setLayout(final LayoutManager layout) {
    // some components (for example, JXCollapsiblePanel from SwingX) have asymmetrical getLayout/setLayout - a different
    // layout is returned compared to what was passed to setLayout(). to avoid crashes, we store the layout we passed to
    // the component.
    myDelegeeLayout = layout;
    getDelegee().setLayout(layout);

    if (layout instanceof AbstractLayout aLayout) {
      for (int i = 0; i < getComponentCount(); i++) {
        final RadComponent c = getComponent(i);
        aLayout.addLayoutComponent(c.getDelegee(), c.getConstraints());
      }
    }
  }

  @Override
  public final boolean isXY() {
    return getLayout() instanceof XYLayoutManager;
  }

  /**
   * @param component component to be added.
   * @throws IllegalArgumentException
   *          if {@code component} is {@code null}
   * @throws IllegalArgumentException
   *          if {@code component} already exist in the
   *          container
   */
  public final void addComponent(final @NotNull RadComponent component, int index) {
    if (myComponents.contains(component)) {
      throw new IllegalArgumentException("component is already added: " + component);
    }

    final RadComponent[] oldChildren = myComponents.toArray(RadComponent.EMPTY_ARRAY);

    // Remove from old parent
    final RadContainer oldParent = component.getParent();
    if (oldParent != null) {
      oldParent.removeComponent(component);
    }

    // Attach to new parent
    myComponents.add(index, component);
    component.setParent(this);
    myLayoutManager.addComponentToContainer(this, component, index);

    final RadComponent[] newChildren = myComponents.toArray(RadComponent.EMPTY_ARRAY);
    firePropertyChanged(PROP_CHILDREN, oldChildren, newChildren);
  }

  public final void addComponent(final @NotNull RadComponent component) {
    addComponent(component, myComponents.size());
  }

  /**
   * Removes specified {@code component} from the container.
   * This method also removes component's delegee from the
   * container's delegee. Client code is responsible for revalidation
   * of invalid Swing hierarchy.
   *
   * @param component component to be removed.
   * @throws IllegalArgumentException
   *          if {@code component}
   *          is {@code null}
   * @throws IllegalArgumentException
   *          if {@code component}
   *          doesn't exist in the container
   */
  public final void removeComponent(final @NotNull RadComponent component) {
    if (!myComponents.contains(component)) {
      throw new IllegalArgumentException("component is not added: " + component);
    }

    final RadComponent[] oldChildren = myComponents.toArray(RadComponent.EMPTY_ARRAY);

    // Remove child
    component.setParent(null);
    myComponents.remove(component);
    myLayoutManager.removeComponentFromContainer(this, component);

    final RadComponent[] newChildren = myComponents.toArray(RadComponent.EMPTY_ARRAY);
    firePropertyChanged(PROP_CHILDREN, oldChildren, newChildren);
  }

  @Override
  public final RadComponent getComponent(final int index) {
    return myComponents.get(index);
  }

  @Override
  public final int getComponentCount() {
    return myComponents.size();
  }

  @Override
  public int indexOfComponent(IComponent component) {
    return myComponents.indexOf(component);
  }

  /**
   * @return new array with all children
   */
  public final RadComponent[] getComponents() {
    return myComponents.toArray(RadComponent.EMPTY_ARRAY);
  }

  public @NotNull ComponentDropLocation getDropLocation(@Nullable Point location) {
    return getLayoutManager().getDropLocation(this, location);
  }

  public RadComponent findComponentInRect(final int startRow, final int startCol, final int rowSpan, final int colSpan) {
    for (int r = startRow; r < startRow + rowSpan; r++) {
      for (int c = startCol; c < startCol + colSpan; c++) {
        final RadComponent result = getComponentAtGrid(r, c);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  public @Nullable RadComponent getComponentAtGrid(boolean rowFirst, int coord1, int coord2) {
    return rowFirst ? getComponentAtGrid(coord1, coord2) : getComponentAtGrid(coord2, coord1);
  }

  public @Nullable RadComponent getComponentAtGrid(int row, int col) {
    return RadAbstractGridLayoutManager.getComponentAtGrid(this, row, col);
  }

  public int getGridRowCount() {
    return getGridLayoutManager().getGridRowCount(this);
  }

  public int getGridColumnCount() {
    return getGridLayoutManager().getGridColumnCount(this);
  }

  public int getGridCellCount(boolean isRow) {
    return isRow ? getGridRowCount() : getGridColumnCount();
  }

  public int getGridRowAt(int y) {
    return getGridLayoutManager().getGridRowAt(this, y);
  }

  public int getGridColumnAt(int x) {
    return getGridLayoutManager().getGridColumnAt(this, x);
  }

  /**
   * @return border's type.
   * @see BorderType
   */
  @Override
  public final @NotNull BorderType getBorderType() {
    return myBorderType;
  }

  /**
   * @throws IllegalArgumentException
   *          if {@code type}
   *          is {@code null}
   * @see BorderType
   */
  public final void setBorderType(final @NotNull BorderType type) {
    if (myBorderType == type) {
      return;
    }
    myBorderType = type;
    updateBorder();
  }

  /**
   * @return border's title. If the container doesn't have any title then the
   *         method returns {@code null}.
   */
  @Override
  public final @Nullable StringDescriptor getBorderTitle() {
    return myBorderTitle;
  }

  /**
   * @param title new border's title. {@code null} means that
   *              the containr doesn't have have titled border.
   */
  public final void setBorderTitle(final StringDescriptor title) {
    if (Comparing.equal(title, myBorderTitle)) {
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

  public Insets getBorderSize() {
    return myBorderSize;
  }

  public void setBorderSize(final Insets borderSize) {
    if (!Comparing.equal(myBorderSize, borderSize)) {
      myBorderSize = borderSize;
      updateBorder();
    }
  }

  public ColorDescriptor getBorderColor() {
    return myBorderColor;
  }

  public void setBorderColor(final ColorDescriptor borderColor) {
    if (!Comparing.equal(myBorderColor, borderColor)) {
      myBorderColor = borderColor;
      updateBorder();
    }
  }

  /**
   * Updates delegee's border
   */
  public boolean updateBorder() {
    @NlsSafe String title = null;
    String oldTitle = null;
    if (myBorderTitle != null) {
      oldTitle = myBorderTitle.getResolvedValue();
      myBorderTitle.setResolvedValue(null);
      // NOTE: the explicit getValue() check is required for SnapShooter operation
      if (myBorderTitle.getValue() != null) {
        title = myBorderTitle.getValue();
      }
      else {
        title = StringDescriptorManager.getInstance(getModule()).resolve(this, myBorderTitle);
      }
    }
    Font font = (myBorderTitleFont != null) ? myBorderTitleFont.getResolvedFont(getDelegee().getFont()) : null;
    Color titleColor = (myBorderTitleColor != null) ? myBorderTitleColor.getResolvedColor() : null;
    Color borderColor = (myBorderColor != null) ? myBorderColor.getResolvedColor() : null;
    getDelegee().setBorder(myBorderType.createBorder(title, myBorderTitleJustification, myBorderTitlePosition,
                                                     font, titleColor, myBorderSize, borderColor));
    return myBorderTitle != null && !Objects.equals(oldTitle, myBorderTitle.getResolvedValue());
  }

  public RadLayoutManager getLayoutManager() {
    RadContainer parent = this;
    while (parent != null) {
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

  public RadComponent getActionTargetComponent(RadComponent child) {
    return child;
  }

  @Override
  public boolean areChildrenExclusive() {
    return myLayoutManager.areChildrenExclusive();
  }

  @Override
  public void refresh() {
    for (int i = 0; i < getComponentCount(); i++) {
      getComponent(i).refresh();
    }
    myLayoutManager.refresh(this);
  }

  /**
   * Serializes container's border
   */
  protected final void writeBorder(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_BORDER);
    try {
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
        writer.startElement(UIFormXmlConstants.ELEMENT_TITLE_COLOR);
        writer.writeColorDescriptor(myBorderTitleColor);
        writer.endElement();
      }
      if (myBorderSize != null) {
        writer.startElement(UIFormXmlConstants.ELEMENT_SIZE);
        writer.writeInsets(myBorderSize);
        writer.endElement();
      }
      if (myBorderColor != null) {
        writer.startElement(UIFormXmlConstants.ELEMENT_COLOR);
        writer.writeColorDescriptor(myBorderColor);
        writer.endElement();
      }
    }
    finally {
      writer.endElement(); // border
    }
  }

  /**
   * Serializes container's children
   */
  protected final void writeChildren(final XmlWriter writer) {
    // Children
    writer.startElement("children");
    try {
      writeChildrenImpl(writer);
    }
    finally {
      writer.endElement(); // children
    }
  }

  protected final void writeChildrenImpl(final XmlWriter writer) {
    for (int i = 0; i < getComponentCount(); i++) {
      getComponent(i).write(writer);
    }
  }

  @Override
  public void write(final XmlWriter writer) {
    if (isXY()) {
      writer.startElement("xy");
    }
    else {
      writer.startElement("grid");
    }
    try {
      writeId(writer);
      writeClassIfDifferent(writer, JPanel.class.getName());
      writeBinding(writer);

      if (myLayoutManager != null) {
        writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_LAYOUT_MANAGER, myLayoutManager.getName());
      }

      getLayoutManager().writeLayout(writer, this);

      // Constraints and properties
      writeConstraints(writer);
      writeProperties(writer);

      // Border
      writeBorder(writer);

      // Children
      writeChildren(writer);
    }
    finally {
      writer.endElement(); // xy/grid
    }
  }

  @Override
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

  protected void writeNoLayout(final XmlWriter writer, final String defaultClassName) {
    writeId(writer);
    writeClassIfDifferent(writer, defaultClassName);
    writeBinding(writer);

    // Constraints and properties
    writeConstraints(writer);
    writeProperties(writer);

    // Margin and border
    writeBorder(writer);
    writeChildren(writer);
  }

  public RadAbstractGridLayoutManager getGridLayoutManager() {
    if (!(myLayoutManager instanceof RadAbstractGridLayoutManager)) {
      throw new RuntimeException("Not a grid container: " + myLayoutManager);
    }
    return (RadAbstractGridLayoutManager)myLayoutManager;
  }

  public @Nullable RadComponent findComponentWithConstraints(final Object constraints) {
    for (RadComponent component : getComponents()) {
      if (constraints.equals(component.getCustomLayoutConstraints())) {
        return component;
      }
    }
    return null;
  }

  private final class MyBorderTitleProperty extends Property<RadContainer, StringDescriptor> {
    private final StringEditor myEditor;

    MyBorderTitleProperty() {
      super(null, "Title");
      myEditor = new StringEditor(getProject());
    }

    public Dimension getPreferredSize() {
      return myEditor.getPreferredSize();
    }

    @Override
    public StringDescriptor getValue(final RadContainer component) {
      return myBorderTitle;
    }

    @Override
    protected void setValueImpl(final RadContainer container, final StringDescriptor value) throws Exception {
      setBorderTitle(value);
    }

    @Override
    public @NotNull PropertyRenderer<StringDescriptor> getRenderer() {
      return null;
    }

    @Override
    public PropertyEditor<StringDescriptor> getEditor() {
      return myEditor;
    }
  }
}