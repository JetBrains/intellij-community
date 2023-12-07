// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public final class CutCopyPasteSupport implements CopyProvider, CutProvider, PasteProvider{
  private static final Logger LOG = Logger.getInstance(CutCopyPasteSupport.class);
  private static final SAXBuilder SAX_BUILDER = new SAXBuilder();

  private final GuiEditor myEditor;
  private static final @NonNls String ELEMENT_SERIALIZED = "serialized";
  private static final @NonNls String ATTRIBUTE_X = "x";
  private static final @NonNls String ATTRIBUTE_Y = "y";
  private static final @NonNls String ATTRIBUTE_PARENT_LAYOUT = "parent-layout";

  public CutCopyPasteSupport(final GuiEditor uiEditor) {
    myEditor = uiEditor;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isCopyEnabled(final @NotNull DataContext dataContext) {
    return FormEditingUtil.getSelectedComponents(myEditor).size() > 0 && !myEditor.getInplaceEditingLayer().isEditing();
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void performCopy(final @NotNull DataContext dataContext) {
    doCopy();
  }

  private boolean doCopy() {
    final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(myEditor);
    final SerializedComponentData data = new SerializedComponentData(serializeForCopy(myEditor, selectedComponents));
    final SimpleTransferable transferable = new SimpleTransferable<>(data, SerializedComponentData.class, ourDataFlavor);
    try {
      CopyPasteManager.getInstance().setContents(transferable);
      return true;
    }
    catch (Exception e) {
      LOG.debug(e);
      return false;
    }
  }

  @Override
  public boolean isCutEnabled(final @NotNull DataContext dataContext) {
    return isCopyEnabled(dataContext) && FormEditingUtil.canDeleteSelection(myEditor);
  }

  @Override
  public boolean isCutVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void performCut(final @NotNull DataContext dataContext) {
    if (doCopy() && myEditor.ensureEditable()) {
      CommandProcessor.getInstance().executeCommand(myEditor.getProject(), () -> FormEditingUtil.deleteSelection(myEditor), UIDesignerBundle.message("command.cut"), null);
    }
  }

  @Override
  public boolean isPastePossible(final @NotNull DataContext dataContext) {
    return isPasteEnabled(dataContext);
  }

  @Override
  public boolean isPasteEnabled(final @NotNull DataContext dataContext) {
    return getSerializedComponents() != null && !myEditor.getInplaceEditingLayer().isEditing();
  }

  @Override
  public void performPaste(final @NotNull DataContext dataContext) {
    final String serializedComponents = getSerializedComponents();
    if (serializedComponents == null) {
      return;
    }

    final ArrayList<RadComponent> componentsToPaste = new ArrayList<>();
    final IntList xs=new IntArrayList();
    final IntList ys=new IntArrayList();
    loadComponentsToPaste(myEditor, serializedComponents, xs, ys, componentsToPaste);

    myEditor.getMainProcessor().startPasteProcessor(componentsToPaste, xs, ys);
  }

  private static @Nullable ArrayList<RadComponent> deserializeComponents(final GuiEditor editor, final String serializedComponents) {
    ArrayList<RadComponent> components = new ArrayList<>();
    IntList xs=new IntArrayList();
    IntList ys=new IntArrayList();
    if (!loadComponentsToPaste(editor, serializedComponents, xs, ys, components)) {
      return null;
    }
    return components;
  }

  private static boolean loadComponentsToPaste(final GuiEditor editor, final String serializedComponents,
                                               final IntList xs,
                                               final IntList ys,
                                               final ArrayList<? super RadComponent> componentsToPaste) {
    final PsiPropertiesProvider provider = new PsiPropertiesProvider(editor.getModule());

    try {
      //noinspection HardCodedStringLiteral
      final Document document = SAX_BUILDER.build(new StringReader(serializedComponents), "UTF-8");

      final Element rootElement = document.getRootElement();
      if (!rootElement.getName().equals(ELEMENT_SERIALIZED)) {
        return false;
      }

      final List children = rootElement.getChildren();
      for (final Object aChildren : children) {
        final Element e = (Element)aChildren;

        // we need to add component to a container in order to read them
        final LwContainer container = new LwContainer(JPanel.class.getName());

        final String parentLayout = e.getAttributeValue(ATTRIBUTE_PARENT_LAYOUT);
        if (parentLayout != null) {
          container.setLayoutManager(parentLayout);
        }

        final int x = Integer.parseInt(e.getAttributeValue(ATTRIBUTE_X));
        final int y = Integer.parseInt(e.getAttributeValue(ATTRIBUTE_Y));

        xs.add(x);
        ys.add(y);

        final Element componentElement = e.getChildren().get(0);
        final LwComponent lwComponent = LwContainer.createComponentFromTag(componentElement);

        container.addComponent(lwComponent);

        lwComponent.read(componentElement, provider);

        // pasted components should have no bindings
        FormEditingUtil.iterate(lwComponent, new FormEditingUtil.ComponentVisitor<LwComponent>() {
          @Override
          public boolean visit(final LwComponent c) {
            if (c.getBinding() != null && FormEditingUtil.findComponentWithBinding(editor.getRootContainer(), c.getBinding()) != null) {
              c.setBinding(null);
            }
            c.setId(FormEditingUtil.generateId(editor.getRootContainer()));
            return true;
          }
        });

        final ClassLoader loader = LoaderFactory.getInstance(editor.getProject()).getLoader(editor.getFile());
        final RadComponent radComponent = XmlReader.createComponent(editor, lwComponent, loader, editor.getStringDescriptorLocale());
        componentsToPaste.add(radComponent);
      }
    }
    catch (Exception e) {
      return false;
    }
    return true;
  }

  private static @Nullable String getSerializedComponents() {
    try {
      final Object transferData = CopyPasteManager.getInstance().getContents(ourDataFlavor);
      if (!(transferData instanceof SerializedComponentData dataProxy)) {
        return null;
      }

      return dataProxy.getSerializedComponents();
    }
    catch (Exception e) {
      return null;
    }
  }

  private static final DataFlavor ourDataFlavor = FileCopyPasteUtil.createJvmDataFlavor(SerializedComponentData.class);

  public static @Nullable List<RadComponent> copyComponents(GuiEditor editor, List<RadComponent> components) {
    return deserializeComponents(editor, serializeForCopy(editor, components));
  }

  private static String serializeForCopy(final GuiEditor editor, final List<RadComponent> components) {
    final XmlWriter writer = new XmlWriter();

    writer.startElement(ELEMENT_SERIALIZED, Utils.FORM_NAMESPACE);

    for (final RadComponent component : components) {
      final Point shift;
      if (component.getParent() != null) {
        shift = SwingUtilities.convertPoint(
          component.getParent().getDelegee(),
          component.getX(),
          component.getY(),
          editor.getRootContainer().getDelegee()
        );
      }
      else {
        shift = new Point(0, 0);
      }

      component.getX();

      writer.startElement("item");
      writer.addAttribute(ATTRIBUTE_X, shift.x);
      writer.addAttribute(ATTRIBUTE_Y, shift.y);
      if (component.getParent() != null) {
        final String parentLayout = component.getParent().getLayoutManager().getName();
        if (parentLayout != null) {
          writer.addAttribute(ATTRIBUTE_PARENT_LAYOUT, parentLayout);
        }
      }
      component.write(writer);

      writer.endElement();
    }

    writer.endElement();

    return writer.getText();
  }


}
