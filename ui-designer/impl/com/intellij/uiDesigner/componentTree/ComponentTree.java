package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.actions.StartInplaceEditingAction;
import com.intellij.uiDesigner.designSurface.DraggedComponentList;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ComponentTree extends Tree implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.componentTree.ComponentTree");

  private SimpleTextAttributes myBindingAttributes; // exists only for performance reason
  private SimpleTextAttributes myClassAttributes; // exists only for performance reason
  private SimpleTextAttributes myPackageAttributes; // exists only for performance reason
  private SimpleTextAttributes myUnknownAttributes; // exists only for performance reason
  private SimpleTextAttributes myTitleAttributes; // exists only for performance reason

  private HashMap<SimpleTextAttributes, SimpleTextAttributes> myAttr2errAttr; // exists only for performance reason

  private final GuiEditor myEditor;
  private final Palette myPalette;
  private final QuickFixManager myQuickFixManager;
  private RadComponent myDropTargetComponent = null;

  public ComponentTree(@NotNull final GuiEditor editor) {
    super(new DefaultTreeModel(new DefaultMutableTreeNode()));
    //noinspection ConstantConditions
    if (editor == null) {
      throw new IllegalArgumentException("editor cannot be null");
    }
    myEditor = editor;
    myPalette = Palette.getInstance(editor.getProject());

    setCellRenderer(new MyTreeCellRenderer());
    setRootVisible(false);
    setShowsRootHandles(false);

    // Enable tooltips
    ToolTipManager.sharedInstance().registerComponent(this);

    // Install convenient keyboard navigation
    TreeUtil.installActions(this);

    // Install advanced tooltips
    TreeToolTipHandler.install(this);

    // Install light bulb
    myQuickFixManager = new QuickFixManagerImpl(editor, this);

    // Popup menu
    PopupHandler.installPopupHandler(
      this,
      (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_GUI_DESIGNER_COMPONENT_TREE_POPUP),
      ActionPlaces.GUI_DESIGNER_COMPONENT_TREE_POPUP, ActionManager.getInstance());

    // F2 should start inplace editing
    final StartInplaceEditingAction startInplaceEditingAction = new StartInplaceEditingAction(editor);
    startInplaceEditingAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)),
      this
    );

    setDragEnabled(true);
    setTransferHandler(new TransferHandler() {
      public int getSourceActions(JComponent c) {
        return DnDConstants.ACTION_COPY_OR_MOVE;
      }

      protected Transferable createTransferable(JComponent c) {
        return DraggedComponentList.pickupSelection(myEditor);
      }
    });
    setDropTarget(new DropTarget(this, new MyDropTargetListener()));
  }

  public void updateIntentionHintVisibility() {
    myQuickFixManager.updateIntentionHintVisibility();
  }

  /**
   * Hides intention hint (if any)
   */
  public void hideIntentionHint() {
    myQuickFixManager.hideIntentionHint();
  }

  @Nullable
  public String getToolTipText(final MouseEvent e) {
    final TreePath path = getPathForLocation(e.getX(), e.getY());
    final RadComponent component = getComponentFromPath(path);
    if (component != null) {
      final ErrorInfo errorInfo = ErrorAnalyzer.getErrorForComponent(component);
      if (errorInfo != null) {
        return errorInfo.myDescription;
      }
    }
    return null;
  }

  @Nullable
  private RadComponent getComponentFromPath(TreePath path) {
    if (path != null) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      LOG.assertTrue(node != null);
      final Object userObject = node.getUserObject();
      if (userObject instanceof ComponentPtrDescriptor) {
        final NodeDescriptor descriptor = (NodeDescriptor)userObject;
        final ComponentPtr ptr = (ComponentPtr)descriptor.getElement();
        if (ptr != null && ptr.isValid()) {
          final RadComponent component = ptr.getComponent();
          LOG.assertTrue(component != null);
          return component;
        }
      }
    }
    return null;
  }

  /**
   * TODO[vova] should return pair <RadComponent, TreePath>
   *
   * @return first selected component. The method returns <code>null</code>
   *         if there is no selection in the tree.
   */
  @Nullable
  public RadComponent getSelectedComponent() {
    final RadComponent[] selectedComponents = getSelectedComponents();
    return selectedComponents.length > 0 ? selectedComponents[0] : null;
  }

  /**
   * TODO[vova] should return pair <RadComponent, TreePath>
   *
   * @return currently selected components. This method never returns <code>null</code>.
   */
  public RadComponent[] getSelectedComponents() {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) {
      return RadComponent.EMPTY_ARRAY;
    }
    final ArrayList<RadComponent> result = new ArrayList<RadComponent>(paths.length);
    for (TreePath path : paths) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      LOG.assertTrue(node != null);
      if (node.getUserObject() instanceof ComponentPtrDescriptor) {
        final ComponentPtrDescriptor descriptor = (ComponentPtrDescriptor)node.getUserObject();
        final ComponentPtr ptr = (ComponentPtr)descriptor.getElement();
        LOG.assertTrue(ptr != null && ptr.isValid());
        result.add(ptr.getComponent());
      }
    }
    return result.toArray(new RadComponent[result.size()]);
  }

  /**
   * Provides {@link DataConstants#NAVIGATABLE} to navigate to
   * binding of currently selected component (if any)
   */
  public Object getData(final String dataId) {
    if (!DataConstants.NAVIGATABLE.equals(dataId)) {
      return null;
    }

    final RadComponent selectedComponent = getSelectedComponent();
    if (selectedComponent == null) {
      return null;
    }

    final String classToBind = myEditor.getRootContainer().getClassToBind();
    if (classToBind == null) {
      return null;
    }

    final PsiClass aClass = FormEditingUtil.findClassToBind(myEditor.getModule(), classToBind);
    if (aClass == null) {
      return null;
    }

    if (selectedComponent instanceof RadRootContainer) {
      return EditSourceUtil.getDescriptor(aClass);
    }

    final String binding = selectedComponent.getBinding();
    if (binding == null) {
      return null;
    }

    final PsiField[] fields = aClass.getFields();

    for (final PsiField field : fields) {
      if (binding.equals(field.getName())) {
        return EditSourceUtil.getDescriptor(field);
      }
    }

    return null;
  }

  private SimpleTextAttributes getAttribute(@NotNull final SimpleTextAttributes attrs, final boolean error) {
    //noinspection ConstantConditions
    if (attrs == null) {
      throw new IllegalArgumentException("attrs cannot be null");
    }
    if (!error) {
      return attrs;
    }
    SimpleTextAttributes result = myAttr2errAttr.get(attrs);
    if (result == null) {
      result = new SimpleTextAttributes(attrs.getStyle() | SimpleTextAttributes.STYLE_WAVED,
                                        attrs.getFgColor(),
                                        Color.RED);
      myAttr2errAttr.put(attrs, result);
    }
    return result;
  }

  public void setUI(final TreeUI ui) {
    super.setUI(ui);

    // [vova] we cannot create this hash in constructor and just clear it here. The
    // problem is that setUI is invoked by constructor of superclass.
    myAttr2errAttr = new HashMap<SimpleTextAttributes, SimpleTextAttributes>();

    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes attributes = globalScheme.getAttributes(HighlighterColors.JAVA_STRING);

    myBindingAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getTreeForeground());
    myClassAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getTreeForeground());
    myPackageAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY);
    myTitleAttributes =new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, attributes.getForegroundColor());
    myUnknownAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, Color.RED);
  }

  private final class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    @NonNls private static final String SWING_PACKAGE = "javax.swing";

    public void customizeCellRenderer(
      final JTree tree,
      final Object value,
      final boolean selected,
      final boolean expanded,
      final boolean leaf,
      final int row,
      final boolean hasFocus
    ) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      if (node.getUserObject() instanceof ComponentPtrDescriptor) {
        final ComponentPtrDescriptor descriptor = (ComponentPtrDescriptor)node.getUserObject();
        final ComponentPtr ptr = (ComponentPtr)descriptor.getElement();
        LOG.assertTrue(ptr != null);
        final RadComponent component = ptr.getComponent();
        LOG.assertTrue(component != null);

        final boolean error = ErrorAnalyzer.getErrorForComponent(component) != null;

        // Text
        boolean hasText = false;
        final String binding = component.getBinding();
        if (binding != null) {
          append(binding, getAttribute(myBindingAttributes, error));
          append(" : ", getAttribute(myClassAttributes, error));
          hasText = true;
        }
        else {
          String componentTitle = getComponentTitle(component);
          if (componentTitle != null) {
            append(componentTitle, getAttribute(myTitleAttributes, error));
            append(" : ", getAttribute(myClassAttributes, error));
            hasText = true;
          }
        }


        final Class componentClass = component.getComponentClass();
        final String componentClassName = componentClass.getName();

        if (component instanceof RadVSpacer) {
          append(UIDesignerBundle.message("component.vertical.spacer"), getAttribute(myClassAttributes, error));
        }
        else if (component instanceof RadHSpacer) {
          append(UIDesignerBundle.message("component.horizontal.spacer"), getAttribute(myClassAttributes, error));
        }
        else if (component instanceof RadErrorComponent) {
          final RadErrorComponent c = (RadErrorComponent)component;
          append(c.getErrorDescription(), getAttribute(myUnknownAttributes, error));
        }
        else if (component instanceof RadRootContainer) {
          append(UIDesignerBundle.message("component.form"), getAttribute(myClassAttributes, error));
          append("(", getAttribute(myPackageAttributes, error));
          final String classToBind = ((RadRootContainer)component).getClassToBind();
          if (classToBind != null) {
            append(classToBind, getAttribute(myPackageAttributes, error));
          }
          else {
            append(UIDesignerBundle.message("component.no.binding"), getAttribute(myPackageAttributes, error));
          }
          append(")", getAttribute(myPackageAttributes, error));
        }
        else {
          final Package aPackage = componentClass.getPackage();
          String packageName = null;
          if (aPackage != null/*null for classes in default package*/) {
            packageName = aPackage.getName();
          }

          SimpleTextAttributes classAttributes = hasText ? myPackageAttributes : myClassAttributes;

          if (packageName != null) {
            append(componentClassName.substring(packageName.length() + 1), getAttribute(classAttributes, error));
            if (!packageName.equals(SWING_PACKAGE)) {
              append(" (", getAttribute(myPackageAttributes, error));
              append(packageName, getAttribute(myPackageAttributes, error));
              append(")", getAttribute(myPackageAttributes, error));
            }
          }
          else {
            append(componentClassName, getAttribute(classAttributes, error));
          }
        }

        // Icon
        if (!(component instanceof RadErrorComponent)) {
          final ComponentItem item = Palette.getInstance(myEditor.getProject()).getItem(componentClassName);
          final Icon icon;
          if (item != null) {
            icon = item.getSmallIcon();
          }
          else {
            icon = IconLoader.getIcon("/com/intellij/uiDesigner/icons/unknown-small.png");
          }
          setIcon(icon);
        }
        else {
          setIcon(IconLoader.getIcon("/com/intellij/uiDesigner/icons/error-small.png"));
        }

        if (component == myDropTargetComponent) {
          setBorder(BorderFactory.createLineBorder(Color.BLUE, 2));
        }
      }
    }

    private String getComponentTitle(final RadComponent component) {
      IntrospectedProperty[] props = myPalette.getIntrospectedProperties(component.getComponentClass());
      for(IntrospectedProperty prop: props) {
        if (prop.getName().equals(SwingProperties.TEXT) && prop instanceof IntroStringProperty) {
          StringDescriptor value = (StringDescriptor) prop.getValue(component);
          if (value != null) {
            return "\"" + value.getResolvedValue() + "\"";
          }
        }
      }

      if (component instanceof RadContainer) {
        RadContainer container = (RadContainer) component;
        StringDescriptor descriptor = container.getBorderTitle();
        if (descriptor != null) {
          if (descriptor.getResolvedValue() == null) {
            descriptor.setResolvedValue(ReferenceUtil.resolve(component.getModule(), descriptor));
          }
          return "\"" + descriptor.getResolvedValue() + "\"";
        }
      }

      if (component.getParent() instanceof RadTabbedPane) {
        RadTabbedPane parentTabbedPane = (RadTabbedPane) component.getParent();
        final StringDescriptor descriptor = parentTabbedPane.getChildTitle(component);
        if (descriptor != null) {
          if (descriptor.getResolvedValue() == null) {
            descriptor.setResolvedValue(ReferenceUtil.resolve(component.getModule(), descriptor));
          }
          return "\"" + descriptor.getResolvedValue() + "\"";
        }
      }
      return null;
    }
  }

  private final class MyDropTargetListener extends DropTargetAdapter {
    public void dragOver(DropTargetDragEvent dtde) {
      RadComponent dropTargetComponent = null;
      final DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
      if (dcl != null) {
        final TreePath path = getPathForLocation((int) dtde.getLocation().getX(),
                                                 (int) dtde.getLocation().getY());
        final RadComponent targetComponent = getComponentFromPath(path);
        if (path != null && targetComponent != null && targetComponent.canDrop(dcl.getComponents().size())) {
          dropTargetComponent = targetComponent;
          dtde.acceptDrag(dtde.getDropAction());
        }
        else {
          dtde.rejectDrag();
        }
      }
      else {
        dtde.rejectDrag();
      }
      if (dropTargetComponent != myDropTargetComponent) {
        myDropTargetComponent = dropTargetComponent;
        repaint();
      }
    }

    public void dragExit(DropTargetEvent dte) {
      myDropTargetComponent = null;
      repaint();
    }

    public void drop(DropTargetDropEvent dtde) {
      final DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
      if (dcl != null) {
        final TreePath path = getPathForLocation((int) dtde.getLocation().getX(),
                                                 (int) dtde.getLocation().getY());
        final RadComponent targetComponent = getComponentFromPath(path);
        if (targetComponent != null && targetComponent instanceof RadContainer) {
          RadContainer container = (RadContainer) targetComponent;
          RadComponent[] components = dcl.getComponents().toArray(new RadComponent [dcl.getComponents().size()]);
          container.drop(components);
        }
      }
      myDropTargetComponent = null;
      repaint();
    }
  }
}
