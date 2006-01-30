package com.intellij.uiDesigner;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import gnu.trove.TIntArrayList;
import org.jdom.Element;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class CutCopyPasteSupport implements CopyProvider, CutProvider, PasteProvider{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.CutCopyPasteSupport");
  private static final SAXBuilder SAX_BUILDER = new SAXBuilder();

  private static String myRecentyCopiedString;
  private static int myRecentyCopiedStringCount;

  private final GuiEditor myEditor;
  @NonNls private static final String ELEMENT_SERIALIZED = "serialized";
  @NonNls private static final String ATTRIBUTE_X = "x";
  @NonNls private static final String ATTRIBUTE_Y = "y";

  public CutCopyPasteSupport(final GuiEditor uiEditor) {
    myEditor = uiEditor;
  }

  public boolean isCopyEnabled(final DataContext dataContext) {
    return FormEditingUtil.getSelectedComponents(myEditor).size() > 0 && !myEditor.getInplaceEditingLayer().isEditing();
  }

  public void performCopy(final DataContext dataContext) {
    doCopy();
  }

  private boolean doCopy() {
    final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(myEditor);
    final MyData data = new MyData(serializeForCopy(myEditor, selectedComponents));
    final SimpleTransferable transferable = new SimpleTransferable<MyData>(data, MyData.class);
    try {
      final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents(transferable, new MyClipboardOwner());
      return true;
    } catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      return false;
    }
  }

  public boolean isCutEnabled(final DataContext dataContext) {
    return isCopyEnabled(dataContext);
  }

  public void performCut(final DataContext dataContext) {
    if (doCopy()) {
      FormEditingUtil.deleteSelection(myEditor);
    }
  }

  public boolean isPastePossible(final DataContext dataContext) {
    return isPasteEnabled(dataContext);
  }

  public boolean isPasteEnabled(final DataContext dataContext) {
    return getSerializedComponents() != null && !myEditor.getInplaceEditingLayer().isEditing();
  }

  public void performPaste(final DataContext dataContext) {
    final String serializedComponents = getSerializedComponents();
    if (serializedComponents == null) {
      return;
    }

    if (serializedComponents.equals(myRecentyCopiedString)) {
      myRecentyCopiedStringCount++;
    }
    else {
      myRecentyCopiedString = serializedComponents;
      myRecentyCopiedStringCount = 1;
    }

    final ArrayList<RadComponent> componentsToPaste = new ArrayList<RadComponent>();
    final TIntArrayList xs = new TIntArrayList();
    final TIntArrayList ys = new TIntArrayList();
    loadComponentsToPaste(myEditor, serializedComponents, xs, ys, componentsToPaste);

    final RadRootContainer rootContainer = myEditor.getRootContainer();
    final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(myEditor);

    if (selectedComponents.size() == 1 && selectedComponents.get(0).canDrop(selectedComponents.size())) {
      RadComponent component = selectedComponents.get(0);
      if (component instanceof RadContainer) {
        RadContainer container = (RadContainer) component;
        container.drop(componentsToPaste.toArray(new RadComponent[componentsToPaste.size()]));
      }
    }
    else {
      FormEditingUtil.clearSelection(rootContainer);
      for (int i = 0; i < componentsToPaste.size(); i++) {
        final RadComponent component = componentsToPaste.get(i);
        final int delta = myRecentyCopiedStringCount * 10;
        component.setLocation(new Point(xs.get(i) + delta, ys.get(i) + delta));
        rootContainer.addComponent(component);

        FormEditingUtil.iterate(component, new FormEditingUtil.ComponentVisitor<RadComponent>() {
          public boolean visit(final RadComponent c) {
            c.setSelected(true);
            return true;
          }
        });
      }
    }

    myEditor.refreshAndSave(true);
  }

  @Nullable
  public static ArrayList<RadComponent> deserializeComponents(final GuiEditor editor, final String serializedComponents) {
    ArrayList<RadComponent> components = new ArrayList<RadComponent>();
    TIntArrayList xs = new TIntArrayList();
    TIntArrayList ys = new TIntArrayList();
    if (!loadComponentsToPaste(editor, serializedComponents, xs, ys, components)) {
      return null;
    }
    return components;
  }

  public static boolean loadComponentsToPaste(final GuiEditor editor, final String serializedComponents,
                                              final TIntArrayList xs,
                                              final TIntArrayList ys,
                                              final ArrayList<RadComponent> componentsToPaste) {
    final PsiPropertiesProvider provider = new PsiPropertiesProvider(editor.getModule());

    try {
      //noinspection HardCodedStringLiteral
      final Document document = SAX_BUILDER.build(new StringReader(serializedComponents), "UTF-8");

      final Element rootElement = document.getRootElement();
      if (!rootElement.getName().equals(ELEMENT_SERIALIZED)) {
        return false;
      }

      // we need to add component to a container in order to read them
      final LwContainer container = new LwContainer(JPanel.class.getName());

      final java.util.List children = rootElement.getChildren();
      for (final Object aChildren : children) {
        final Element e = (Element)aChildren;

        final int x = Integer.parseInt(e.getAttributeValue(ATTRIBUTE_X));
        final int y = Integer.parseInt(e.getAttributeValue(ATTRIBUTE_Y));

        xs.add(x);
        ys.add(y);

        final Element componentElement = (Element)e.getChildren().get(0);
        final LwComponent lwComponent = LwContainer.createComponentFromTag(componentElement);

        container.addComponent(lwComponent);

        lwComponent.read(componentElement, provider);

        // pasted components should have no bindings
        lwComponent.setBinding(null);
        lwComponent.setId(editor.generateId());
        FormEditingUtil.iterate(lwComponent, new FormEditingUtil.ComponentVisitor<LwComponent>() {
          public boolean visit(final LwComponent c) {
            c.setBinding(null);
            c.setId(editor.generateId());
            return true;
          }
        });

        final Module module = editor.getModule();
        final ClassLoader loader = LoaderFactory.getInstance(module.getProject()).getLoader(editor.getFile());
        final RadComponent radComponent = XmlReader.createComponent(module, lwComponent, loader);
        componentsToPaste.add(radComponent);
      }
    }
    catch (Exception e) {
      return false;
    }
    return true;
  }

  private static final class MyData {
    private final String mySerializedComponents;

    public MyData(final String components) {
      mySerializedComponents = components;
    }

    public String getSerializedComponents() {
      return mySerializedComponents;
    }
  }

  @Nullable
  private String getSerializedComponents() {
    try {
      final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      final Transferable content = clipboard.getContents(this);
      final Object transferData;
      try {
        transferData = content.getTransferData(ourDataFlavor);
      } catch (UnsupportedFlavorException e) {
        return null;
      } catch (IOException e) {
        return null;
      }

      if (!(transferData instanceof MyData)) {
        return null;
      }
      final MyData dataProxy = (MyData) transferData;
      return dataProxy.getSerializedComponents();
    } catch (Exception e) {
      return null;
    }
  }

  private static final DataFlavor ourDataFlavor;
  static {
    try {
      //noinspection HardCodedStringLiteral
      ourDataFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + MyData.class.getName());
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class MyClipboardOwner implements ClipboardOwner {
    public void lostOwnership(final Clipboard clipboard, final Transferable contents) {
    }
  }

  public static String serializeForCopy(final GuiEditor editor, final ArrayList<RadComponent> components) {
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
      component.write(writer);

      writer.endElement();
    }

    writer.endElement();

    return writer.getText();
  }


}
