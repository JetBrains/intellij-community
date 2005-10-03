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
import gnu.trove.TIntArrayList;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class CutCopyPasteSupport implements CopyProvider, CutProvider, PasteProvider{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.CutCopyPasteSupport");
  private static final SAXBuilder SAX_BUILDER = new SAXBuilder();

  private static String myRecentyCopiedString;
  private static int myRecentyCopiedStringCount;

  private final GuiEditor myEditor;

  public CutCopyPasteSupport(final GuiEditor uiEditor) {
    myEditor = uiEditor;
  }

  public boolean isCopyEnabled(final DataContext dataContext) {
    return FormEditingUtil.getSelectedComponents(myEditor).size() > 0;
  }

  public void performCopy(final DataContext dataContext) {
    doCopy();
  }

  private boolean doCopy() {
    final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(myEditor);
    final MyData data = new MyData(serializeForCopy(selectedComponents));
    final MyTransferable transferable = new MyTransferable(data);
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
    return getSerializedComponents() != null;
  }

  public void performPaste(final DataContext dataContext) {
    final String serializedComponents = getSerializedComponents();
      
    if (serializedComponents.equals(myRecentyCopiedString)) {
      myRecentyCopiedStringCount++;
    }
    else {
      myRecentyCopiedString = serializedComponents;
      myRecentyCopiedStringCount = 1;
    }

    final PsiPropertiesProvider provider = new PsiPropertiesProvider(myEditor.getModule());

    final ArrayList<RadComponent> componentsToPaste = new ArrayList<RadComponent>();
    final TIntArrayList xs = new TIntArrayList();
    final TIntArrayList ys = new TIntArrayList();
      
    try {
      //noinspection HardCodedStringLiteral
      final org.jdom.Document document = SAX_BUILDER.build(new StringReader(serializedComponents), "UTF-8");

      final Element rootElement = document.getRootElement();
      //noinspection HardCodedStringLiteral
      if (!rootElement.getName().equals("serialized")) {
        return;
      }

      // we need to add component to a container in order to read them
      final LwContainer container = new LwContainer(JPanel.class.getName());
        
      final java.util.List children = rootElement.getChildren();
      for (Iterator iterator = children.iterator(); iterator.hasNext();) {
        final Element e = (Element)iterator.next();

        //noinspection HardCodedStringLiteral
        final int x = Integer.parseInt(e.getAttributeValue("x"));
        //noinspection HardCodedStringLiteral
        final int y = Integer.parseInt(e.getAttributeValue("y"));
          
        xs.add(x);
        ys.add(y);

        final Element componentElement = (Element)e.getChildren().get(0);
        final LwComponent lwComponent = LwContainer.createComponentFromTag(componentElement);

        container.addComponent(lwComponent);
          
        lwComponent.read(componentElement, provider);

        // pasted components should have no bindings
        lwComponent.setBinding(null);  
        lwComponent.setId(myEditor.generateId());
        FormEditingUtil.iterate(lwComponent, new FormEditingUtil.ComponentVisitor<LwComponent>() {
          public boolean visit(final LwComponent c) {
            c.setBinding(null);
            c.setId(myEditor.generateId());
            return true;
          }
        });

        final Module module = myEditor.getModule();
        final ClassLoader loader = LoaderFactory.getInstance(module.getProject()).getLoader(myEditor.getFile());
        final RadComponent radComponent = XmlReader.createComponent(module, lwComponent, loader);
        componentsToPaste.add(radComponent);
      }
    }
    catch (Exception e) {
    }

    final RadRootContainer rootContainer = myEditor.getRootContainer();
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
      
    myEditor.refreshAndSave(true);
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String serializeForCopy(final ArrayList<RadComponent> components) {
    final XmlWriter writer = new XmlWriter();
    
    writer.startElement("serialized", Utils.FORM_NAMESPACE);
    
    for (int i = 0; i < components.size(); i++) {
      final RadComponent component = components.get(i);

      final Point shift = SwingUtilities.convertPoint(
        component.getParent().getDelegee(), 
        component.getX(),
        component.getY(),
        myEditor.getRootContainer().getDelegee()
      );
      
      component.getX();
      
      writer.startElement("item");
      writer.addAttribute("x", shift.x); 
      writer.addAttribute("y", shift.y); 
      component.write(writer);
      
      writer.endElement();
    }
    
    writer.endElement();
    
    return writer.getText();
  }


  public static final class MyTransferable implements Transferable {
    private final MyData myDataProxy;

    public MyTransferable(final MyData data) {
      myDataProxy = data;
    }

    public Object getTransferData(final DataFlavor flavor) {
      if (!ourDataFlavor.equals(flavor)) {
        return null;
      }
      return myDataProxy;
    }

    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[]{ourDataFlavor};
    }

    public boolean isDataFlavorSupported(final DataFlavor flavor) {
      return flavor.equals(ourDataFlavor);
    }
  }
}
