/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
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
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class RadContainer extends RadComponent implements IContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.radComponents.RadContainer");

  public static class Factory extends RadComponentFactory {
    public RadComponent newInstance(ModuleProvider module, Class aClass, String id) {
      return new RadContainer(module, aClass, id);
    }

    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      return new RadContainer(componentClass, id, palette);
    }
  }

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

  public RadContainer(@NotNull final Class aClass, @NotNull final String id, final Palette palette) {
    this(null, aClass, id);
    setPalette(palette);
  }

  @Nullable
  protected RadLayoutManager createInitialLayoutManager() {
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
  @Nullable
  public Property getDefaultInplaceProperty() {
    return new MyBorderTitleProperty();
  }

  @Override
  @Nullable
  public Rectangle getDefaultInplaceEditorBounds() {
    return getBorderInPlaceEditorBounds(new MyBorderTitleProperty());
  }

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

    if (layout instanceof AbstractLayout) {
      AbstractLayout aLayout = (AbstractLayout)layout;
      for (int i = 0; i < getComponentCount(); i++) {
        final RadComponent c = getComponent(i);
        aLayout.addLayoutComponent(c.getDelegee(), c.getConstraints());
      }
    }
  }

  public final boolean isXY() {
    return getLayout() instanceof XYLayoutManager;
  }

  /**
   * @param component component to be added.
   * @throws java.lang.IllegalArgumentException
   *          if <code>component</code> is <code>null</code>
   * @throws java.lang.IllegalArgumentException
   *          if <code>component</code> already exist in the
   *          container
   */
  public final void addComponent(@NotNull final RadComponent component, int index) {
    if (myComponents.contains(component)) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("component is already added: " + component);
    }

    final RadComponent[] oldChildren = myComponents.toArray(new RadComponent[myComponents.size()]);

    // Remove from old parent
    final RadContainer oldParent = component.getParent();
    if (oldParent != null) {
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
   * @throws java.lang.IllegalArgumentException
   *          if <code>component</code>
   *          is <code>null</code>
   * @throws java.lang.IllegalArgumentException
   *          if <code>component</code>
   *          doesn't exist in the container
   */
  public final void removeComponent(@NotNull final RadComponent component) {
    if (!myComponents.contains(component)) {
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

  public int indexOfComponent(IComponent component) {
    return myComponents.indexOf(component);
  }

  /**
   * @return new array with all children
   */
  public final RadComponent[] getComponents() {
    return myComponents.toArray(new RadComponent[myComponents.size()]);
  }

  @NotNull
  public ComponentDropLocation getDropLocation(@Nullable Point location) {
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

  @Nullable
  public RadComponent getComponentAtGrid(boolean rowFirst, int coord1, int coord2) {
    return rowFirst ? getComponentAtGrid(coord1, coord2) : getComponentAtGrid(coord2, coord1);
  }

  @Nullable
  public RadComponent getComponentAtGrid(int row, int col) {
    return getGridLayoutManager().getComponentAtGrid(this, row, col);
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
   * @see com.intellij.uiDesigner.shared.BorderType
   */
  @NotNull
  public final BorderType getBorderType() {
    return myBorderType;
  }

  /**
   * @throws java.lang.IllegalArgumentException
   *          if <code>type</code>
   *          is <code>null</code>
   * @see com.intellij.uiDesigner.shared.BorderType
   */
  public final void setBorderType(@NotNull final BorderType type) {
    if (myBorderType == type) {
      return;
    }
    myBorderType = type;
    updateBorder();
  }

  /**
   * @return border's title. If the container doesn't have any title then the
   *         method returns <code>null</code>.
   */
  @Nullable
  public final StringDescriptor getBorderTitle() {
    return myBorderTitle;
  }

  /**
   * @param title new border's title. <code>null</code> means that
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
    String title = null;
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
    return myBorderTitle != null && !Comparing.equal(oldTitle, myBorderTitle.getResolvedValue());
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

  @Override
  protected void importSnapshotComponent(final SnapshotContext context, final JComponent component) {
    getLayoutManager().createSnapshotLayout(context, component, this, component.getLayout());
    importSnapshotBorder(component);
    for (Component child : component.getComponents()) {
      if (child instanceof JComponent) {
        RadComponent childComponent = createSnapshotComponent(context, (JComponent)child);
        if (childComponent != null) {
          getLayoutManager().addSnapshotComponent(component, (JComponent)child, this, childComponent);
        }
      }
    }
  }

  private void importSnapshotBorder(final JComponent component) {
    Border border = component.getBorder();
    if (border != null) {
      if (border instanceof TitledBorder) {
        TitledBorder titledBorder = (TitledBorder)border;
        setBorderTitle(StringDescriptor.create(titledBorder.getTitle()));
        setBorderTitleJustification(titledBorder.getTitleJustification());
        setBorderTitlePosition(titledBorder.getTitlePosition());
        final Font titleFont = titledBorder.getTitleFont();
        if (titleFont != null) {
          setBorderTitleFont(new FontDescriptor(titleFont.getName(), titleFont.getStyle(), titleFont.getSize()));
        }
        setBorderTitleColor(new ColorDescriptor(titledBorder.getTitleColor()));
        border = titledBorder.getBorder();
      }

      if (border instanceof EtchedBorder) {
        setBorderType(BorderType.ETCHED);
      }
      else if (border instanceof BevelBorder) {
        BevelBorder bevelBorder = (BevelBorder)border;
        setBorderType(bevelBorder.getBevelType() == BevelBorder.RAISED ? BorderType.BEVEL_RAISED : BorderType.BEVEL_LOWERED);
      }
      else if (border instanceof EmptyBorder) {
        EmptyBorder emptyBorder = (EmptyBorder)border;
        setBorderType(BorderType.EMPTY);
        setBorderSize(emptyBorder.getBorderInsets());
      }
      else if (border instanceof LineBorder) {
        LineBorder lineBorder = (LineBorder)border;
        setBorderType(BorderType.LINE);
        setBorderColor(new ColorDescriptor(lineBorder.getLineColor()));
      }
    }
  }

  public RadAbstractGridLayoutManager getGridLayoutManager() {
    if (!(myLayoutManager instanceof RadAbstractGridLayoutManager)) {
      throw new RuntimeException("Not a grid container: " + myLayoutManager);
    }
    return (RadAbstractGridLayoutManager)myLayoutManager;
  }

  @Nullable
  public RadComponent findComponentWithConstraints(final Object constraints) {
    for (RadComponent component : getComponents()) {
      if (constraints.equals(component.getCustomLayoutConstraints())) {
        return component;
      }
    }
    return null;
  }

  private final class MyBorderTitleProperty extends Property<RadContainer, StringDescriptor> {
    private final StringEditor myEditor;

    public MyBorderTitleProperty() {
      super(null, "Title");
      myEditor = new StringEditor(getProject());
    }

    public Dimension getPreferredSize() {
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