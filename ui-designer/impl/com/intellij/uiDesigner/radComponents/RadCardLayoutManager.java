/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.AbstractTextFieldEditor;
import com.intellij.uiDesigner.propertyInspector.properties.HGapProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VGapProperty;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.CardLayout;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.Vector;
import java.util.List;

/**
 * @author yole
 */
public class RadCardLayoutManager extends RadLayoutManager {
  @Nullable
  public String getName() {
    return UIFormXmlConstants.LAYOUT_CARD;
  }

  @Override @Nullable
  public LayoutManager createLayout() {
    return new CardLayout();
  }

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
  }

  public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
    container.getDelegee().add(component.getDelegee(), component.getCustomLayoutConstraints());
  }

  @Override public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    if (container.getComponentCount() != 0) {
      throw new IncorrectOperationException("Only empty containers can be changed to CardLayout");
    }
    super.changeContainerLayout(container);
  }

  @Override @NotNull
  public DropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
    return new CardDropLocation(container);
  }

  @Override
  public Property[] getContainerProperties(final Project project) {
    return new Property[] {
      HGapProperty.getInstance(project),
      VGapProperty.getInstance(project) };
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

  @Override public void createSnapshotLayout(final SnapshotContext context,
                                             final JComponent parent,
                                             final RadContainer container,
                                             final LayoutManager layout) {
    CardLayout cardLayout = (CardLayout) layout;
    container.setLayout(new CardLayout(cardLayout.getHgap(), cardLayout.getVgap()));
  }


  @SuppressWarnings({"UseOfObsoleteCollectionType", "HardCodedStringLiteral"})
  @Override
  public void addSnapshotComponent(final JComponent parent,
                                   final JComponent child,
                                   final RadContainer container,
                                   final RadComponent component) {
    // unfortunately card can be extracted only through reflection
    String cardName = null;
    try {
      final LayoutManager layout = parent.getLayout();
      Field vectorField = layout.getClass().getDeclaredField("vector");
      vectorField.setAccessible(true);
      Vector vector = (Vector) vectorField.get(parent.getLayout());
      for (Object card : vector) {
        Field nameField = card.getClass().getDeclaredField("name");
        nameField.setAccessible(true);
        Field compField = card.getClass().getDeclaredField("comp");
        compField.setAccessible(true);
        if (compField.get(card) == child) {
          cardName = (String) nameField.get(card);
          break;
        }
      }
    }
    catch (Exception e) {
      // ignore
    }

    if (cardName != null) {
      component.setCustomLayoutConstraints(cardName);
      container.addComponent(component);
    }
  }

  private static boolean cardExists(final RadContainer container, final String s) {
    for(RadComponent component: container.getComponents()) {
      if (s.equals(component.getCustomLayoutConstraints())) {
        return true;
      }
    }
    return false;
  }

  private static class CardDropLocation implements DropLocation {
    private RadContainer myContainer;
    @NonNls private static final String CARD_NAME_PREFIX = "Card";

    public CardDropLocation(final RadContainer container) {
      myContainer = container;
    }

    public RadContainer getContainer() {
      return myContainer;
    }

    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1;
    }

    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      Rectangle rc = myContainer.getBounds();
      feedbackLayer.putFeedback(myContainer.getParent().getDelegee(), rc);
    }

    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      int cardIndex = 1;
      while(cardExists(myContainer, CARD_NAME_PREFIX + cardIndex)) {
        cardIndex++;
      }
      components [0].setCustomLayoutConstraints(CARD_NAME_PREFIX + cardIndex);
      myContainer.addComponent(components [0]);
    }

    @Nullable
    public DropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }

  private static class CardNameProperty extends Property<RadComponent, String> {
    private LabelPropertyRenderer<String> myRenderer = new LabelPropertyRenderer<String>();

    private AbstractTextFieldEditor<String> myEditor = new AbstractTextFieldEditor<String>() {
      protected void setValueFromComponent(RadComponent component, String value) {
        myTf.setText((String) component.getCustomLayoutConstraints());
      }

      public String getValue() throws Exception {
        return myTf.getText();
      }
    };

    static CardNameProperty INSTANCE = new CardNameProperty();

    private CardNameProperty() {
      super(null, "Card Name");
    }

    public String getValue(final RadComponent component) {
      return (String) component.getCustomLayoutConstraints();
    }

    protected void setValueImpl(final RadComponent component, final String value) throws Exception {
      if (!value.equals(component.getCustomLayoutConstraints())) {
        if (cardExists(component.getParent(), value)) {
          throw new Exception(UIDesignerBundle.message("error.card.already.exists", value));
        }
        component.setCustomLayoutConstraints(value);
      }
    }

    @NotNull
    public PropertyRenderer<String> getRenderer() {
      return myRenderer;
    }

    public PropertyEditor<String> getEditor() {
      return myEditor;
    }

    @Override
    public boolean appliesToSelection(final List<RadComponent> selection) {
      return selection.size() == 1;
    }
  }
}
