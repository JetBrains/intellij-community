package com.intellij.uiDesigner;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import gnu.trove.TIntArrayList;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class CutCopyPasteSupport implements CopyProvider, CutProvider, PasteProvider{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.CutCopyPasteSupport");
  private static final SAXBuilder SAX_BUILDER = new SAXBuilder();

  private final GuiEditor myEditor;
  @NonNls private static final String ELEMENT_SERIALIZED = "serialized";
  @NonNls private static final String ATTRIBUTE_X = "x";
  @NonNls private static final String ATTRIBUTE_Y = "y";
  @NonNls private static final String ATTRIBUTE_PARENT_LAYOUT = "parent-layout";

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
    final SimpleTransferable transferable = new SimpleTransferable<MyData>(data, MyData.class, ourDataFlavor);
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
    return isCopyEnabled(dataContext) && FormEditingUtil.canDeleteSelection(myEditor);
  }

  public void performCut(final DataContext dataContext) {
    if (doCopy() && myEditor.ensureEditable()) {
      CommandProcessor.getInstance().executeCommand(myEditor.getProject(), new Runnable() {
        public void run() {
          FormEditingUtil.deleteSelection(myEditor);
        }
      }, UIDesignerBundle.message("command.cut"), null);
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

    final ArrayList<RadComponent> componentsToPaste = new ArrayList<RadComponent>();
    final TIntArrayList xs = new TIntArrayList();
    final TIntArrayList ys = new TIntArrayList();
    loadComponentsToPaste(myEditor, serializedComponents, xs, ys, componentsToPaste);

    myEditor.getMainProcessor().startPasteProcessor(componentsToPaste, xs, ys);
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

        final Element componentElement = (Element)e.getChildren().get(0);
        final LwComponent lwComponent = LwContainer.createComponentFromTag(componentElement);

        container.addComponent(lwComponent);

        lwComponent.read(componentElement, provider);

        // pasted components should have no bindings
        FormEditingUtil.iterate(lwComponent, new FormEditingUtil.ComponentVisitor<LwComponent>() {
          public boolean visit(final LwComponent c) {
            if (c.getBinding() != null && FormEditingUtil.findComponentWithBinding(editor.getRootContainer(), c.getBinding()) != null) {
              c.setBinding(null);
            }
            c.setId(FormEditingUtil.generateId(editor.getRootContainer()));
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
      if (!clipboard.isDataFlavorAvailable(ourDataFlavor)) {
        return null;
      }
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

  @Nullable
  public static List<RadComponent> copyComponents(GuiEditor editor, List<RadComponent> components) {
    return deserializeComponents(editor, serializeForCopy(editor, components));
  }

  public static String serializeForCopy(final GuiEditor editor, final List<RadComponent> components) {
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
