// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.LwContainer;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.AbstractTextFieldEditor;
import com.intellij.uiDesigner.propertyInspector.editors.ComponentEditor;
import com.intellij.uiDesigner.propertyInspector.properties.HGapProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VGapProperty;
import com.intellij.uiDesigner.propertyInspector.renderers.ComponentRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;


public class RadCardLayoutManager extends RadLayoutManager {
  @Override
  public @Nullable String getName() {
    return UIFormXmlConstants.LAYOUT_CARD;
  }

  @Override
  public @Nullable LayoutManager createLayout() {
    return new CardLayout();
  }

  @Override
  public void readLayout(LwContainer lwContainer, RadContainer radContainer) throws Exception {
    String defaultCard = (String)lwContainer.getClientProperty(UIFormXmlConstants.LAYOUT_CARD);
    DefaultCardProperty.INSTANCE.setValue(radContainer, defaultCard);
  }

  @Override
  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    writer.startElement(UIFormXmlConstants.ELEMENT_CARD);
    try {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_NAME, (String) child.getCustomLayoutConstraints());
    }
    finally {
      writer.endElement();
    }
  }

  @Override
  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    CardLayout layout = (CardLayout) radContainer.getLayout();

    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_HGAP, layout.getHgap());
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VGAP, layout.getVgap());

    String defaultCard = DefaultCardProperty.INSTANCE.getValue(radContainer);
    if (!StringUtil.isEmpty(defaultCard)) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_SHOW, defaultCard);
    }
  }

  @Override
  public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
    container.getDelegee().add(component.getDelegee(), component.getCustomLayoutConstraints());
  }

  @Override
  public void removeComponentFromContainer(RadContainer container, RadComponent component) {
    if (component.getId().equals(DefaultCardProperty.INSTANCE.getValue(container))) {
      DefaultCardProperty.INSTANCE.setValueEx(container, null);
    }
    super.removeComponentFromContainer(container, component);
  }

  @Override public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    if (container.getComponentCount() != 0) {
      throw new IncorrectOperationException("Only empty containers can be changed to CardLayout");
    }
    super.changeContainerLayout(container);
  }

  @Override
  public @NotNull ComponentDropLocation getDropLocation(RadContainer container, final @Nullable Point location) {
    return new CardDropLocation(container);
  }

  @Override
  public Property[] getContainerProperties(final Project project) {
    return new Property[]{
      HGapProperty.getInstance(project),
      VGapProperty.getInstance(project),
      DefaultCardProperty.INSTANCE };
  }

  @Override
  public Property[] getComponentProperties(final Project project, final RadComponent component) {
    return new Property[] { CardNameProperty.INSTANCE };
  }

  @Override
  public boolean isSwitchedToChild(RadContainer container, RadComponent child) {
    return child.getDelegee().isVisible();
  }

  @Override
  public boolean switchContainerToChild(RadContainer container, RadComponent child) {
    CardLayout cardLayout = (CardLayout) container.getLayout();
    String card = (String) child.getCustomLayoutConstraints();
    cardLayout.show(container.getDelegee(), card);
    return true;
  }

  @Override
  public boolean areChildrenExclusive() {
    return true;
  }


  private static class CardDropLocation implements ComponentDropLocation {
    private final RadContainer myContainer;
    private static final @NonNls String CARD_NAME_PREFIX = "Card";

    CardDropLocation(final RadContainer container) {
      myContainer = container;
    }

    @Override
    public RadContainer getContainer() {
      return myContainer;
    }

    @Override
    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1;
    }

    @Override
    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      Rectangle rc = myContainer.getBounds();
      feedbackLayer.putFeedback(myContainer.getParent().getDelegee(), rc, null);
    }

    @Override
    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      int cardIndex = 1;
      while(myContainer.findComponentWithConstraints(CARD_NAME_PREFIX + cardIndex) != null) {
        cardIndex++;
      }
      components [0].setCustomLayoutConstraints(CARD_NAME_PREFIX + cardIndex);
      myContainer.addComponent(components [0]);
    }

    @Override
    public @Nullable ComponentDropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }

  private static final class CardNameProperty extends Property<RadComponent, String> {
    private final LabelPropertyRenderer<String> myRenderer = new LabelPropertyRenderer<>();

    private final AbstractTextFieldEditor<String> myEditor = new AbstractTextFieldEditor<>() {
      @Override
      protected void setValueFromComponent(RadComponent component, String value) {
        myTf.setText((String)component.getCustomLayoutConstraints());
      }

      @Override
      public String getValue() throws Exception {
        return myTf.getText();
      }
    };

    static CardNameProperty INSTANCE = new CardNameProperty();

    private CardNameProperty() {
      super(null, "Card Name");
    }

    @Override
    public String getValue(final RadComponent component) {
      return (String) component.getCustomLayoutConstraints();
    }

    @Override
    protected void setValueImpl(final RadComponent component, final String value) throws Exception {
      if (!value.equals(component.getCustomLayoutConstraints())) {
        if (component.getParent().findComponentWithConstraints(value) != null) {
          throw new Exception(UIDesignerBundle.message("error.card.already.exists", value));
        }
        component.changeCustomLayoutConstraints(value);
        final JComponent parent = component.getParent().getDelegee();
        CardLayout layout = (CardLayout) parent.getLayout();
        layout.show(parent, value);
      }
    }

    @Override
    public @NotNull PropertyRenderer<String> getRenderer() {
      return myRenderer;
    }

    @Override
    public PropertyEditor<String> getEditor() {
      return myEditor;
    }

    @Override
    public boolean appliesToSelection(final List<RadComponent> selection) {
      return selection.size() == 1;
    }
  }

  private static class DefaultCardProperty extends Property<RadContainer, String> {
    private static final @NonNls String NAME = "Default Card";

    private final ComponentRenderer myRenderer = new ComponentRenderer();
    private ComponentEditor myEditor;

    static DefaultCardProperty INSTANCE = new DefaultCardProperty();

    DefaultCardProperty() {
      super(null, NAME);
    }

    @Override
    public @NotNull PropertyRenderer<String> getRenderer() {
      return myRenderer;
    }

    @Override
    public PropertyEditor<String> getEditor() {
      if (myEditor == null) {
        myEditor = new ComponentEditor(null, null) {
          @Override
          protected RadComponent[] collectFilteredComponents(RadComponent component) {
            RadContainer container = (RadContainer)component;
            RadComponent[] result = new RadComponent[container.getComponentCount() + 1];
            for (int i = 1; i < result.length; i++) {
              result[i] = container.getComponent(i - 1);
            }
            return result;
          }
        };
      }
      return myEditor;
    }

    @Override
    public String getValue(RadContainer component) {
      return (String)component.getDelegee().getClientProperty(NAME);
    }

    @Override
    protected void setValueImpl(RadContainer component, String value) throws Exception {
      component.getDelegee().putClientProperty(NAME, StringUtil.isEmpty(value) ? null : value);
    }

    @Override
    public boolean appliesToSelection(List<RadComponent> selection) {
      return selection.size() == 1;
    }
  }
}