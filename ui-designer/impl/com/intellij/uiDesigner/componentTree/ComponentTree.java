package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.radComponents.*;
import com.intellij.uiDesigner.actions.StartInplaceEditingAction;
import com.intellij.uiDesigner.designSurface.DraggedComponentList;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private HashMap<SimpleTextAttributes, SimpleTextAttributes> myAttr2WarningAttr; // exists only for performance reason

  private GuiEditor myEditor;
  private final QuickFixManager myQuickFixManager;
  private RadComponent myDropTargetComponent = null;
  private StartInplaceEditingAction myStartInplaceEditingAction;

  public ComponentTree() {
    super(new DefaultTreeModel(new DefaultMutableTreeNode()));

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
    myQuickFixManager = new QuickFixManagerImpl(null, this);

    // Popup menu
    PopupHandler.installPopupHandler(
      this,
      (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_GUI_DESIGNER_COMPONENT_TREE_POPUP),
      ActionPlaces.GUI_DESIGNER_COMPONENT_TREE_POPUP, ActionManager.getInstance());

    // F2 should start inplace editing
    myStartInplaceEditingAction = new StartInplaceEditingAction(null);
    myStartInplaceEditingAction.registerCustomShortcutSet(
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

  public void setEditor(final GuiEditor editor) {
    myEditor = editor;
    myQuickFixManager.setEditor(editor);
    myStartInplaceEditingAction.setEditor(editor);
  }

  public void refreshIntentionHint() {
    myQuickFixManager.refreshIntentionHint();
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
  private static RadComponent getComponentFromPath(TreePath path) {
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
   * @return currently selected components.
   */
  @NotNull public RadComponent[] getSelectedComponents() {
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
    if (GuiEditor.class.getName().equals(dataId)) {
      return myEditor;
    }

    if (
      DataConstantsEx.COPY_PROVIDER.equals(dataId) ||
      DataConstantsEx.CUT_PROVIDER.equals(dataId) ||
      DataConstantsEx.PASTE_PROVIDER.equals(dataId) ||
      DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)
    ) {
      return myEditor == null ? null : myEditor.getData(dataId);
    }

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

  private SimpleTextAttributes getAttribute(@NotNull final SimpleTextAttributes attrs,
                                            @Nullable HighlightDisplayLevel level) {
    if (level == null) {
      return attrs;
    }
    if (level == HighlightDisplayLevel.WARNING) {
      SimpleTextAttributes result = myAttr2WarningAttr.get(attrs);
      if (result == null) {
        result = new SimpleTextAttributes(attrs.getStyle() | SimpleTextAttributes.STYLE_WAVED,
                                          attrs.getFgColor(),
                                          Color.ORANGE);
        myAttr2WarningAttr.put(attrs, result);
      }
      return result;
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
    myAttr2WarningAttr = new HashMap<SimpleTextAttributes, SimpleTextAttributes>();

    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes attributes = globalScheme.getAttributes(HighlighterColors.JAVA_STRING);

    myBindingAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getTreeForeground());
    myClassAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getTreeForeground());
    myPackageAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY);
    myTitleAttributes =new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, attributes.getForegroundColor());
    myUnknownAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, Color.RED);
  }

  @Nullable
  public static String getComponentTitle(final RadComponent component) {
    Palette palette = Palette.getInstance(component.getModule().getProject());
    IntrospectedProperty[] props = palette.getIntrospectedProperties(component.getComponentClass());
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
          descriptor.setResolvedValue(ReferenceUtil.resolve(component, descriptor));
        }
        return "\"" + descriptor.getResolvedValue() + "\"";
      }
    }

    if (component.getParent() instanceof RadTabbedPane) {
      RadTabbedPane parentTabbedPane = (RadTabbedPane) component.getParent();
      final StringDescriptor descriptor = parentTabbedPane.getChildTitle(component);
      if (descriptor != null) {
        if (descriptor.getResolvedValue() == null) {
          descriptor.setResolvedValue(ReferenceUtil.resolve(component, descriptor));
        }
        return "\"" + descriptor.getResolvedValue() + "\"";
      }
      else {
        parentTabbedPane.getChildTitle(component);
      }
    }
    return null;
  }

  public static Icon getComponentIcon(final RadComponent component) {
    if (!(component instanceof RadErrorComponent)) {
      final Palette palette = Palette.getInstance(component.getModule().getProject());
      final ComponentItem item = palette.getItem(component.getComponentClassName());
      final Icon icon;
      if (item != null) {
        icon = item.getSmallIcon();
      }
      else {
        icon = IconLoader.getIcon("/com/intellij/uiDesigner/icons/unknown-small.png");
      }
      return icon;
    }
    else {
      return IconLoader.getIcon("/com/intellij/uiDesigner/icons/error-small.png");
    }
  }

  public void setDropTargetComponent(final RadComponent dropTargetComponent) {
    if (dropTargetComponent != myDropTargetComponent) {
      myDropTargetComponent = dropTargetComponent;
      repaint();
    }
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

        final HighlightDisplayLevel level = ErrorAnalyzer.getHighlightDisplayLevel(component);

        // Text
        boolean hasText = false;
        final String binding = component.getBinding();
        if (binding != null) {
          append(binding, getAttribute(myBindingAttributes, level));
          append(" : ", getAttribute(myClassAttributes, level));
          hasText = true;
        }
        else {
          String componentTitle = getComponentTitle(component);
          if (componentTitle != null) {
            append(componentTitle, getAttribute(myTitleAttributes, level));
            append(" : ", getAttribute(myClassAttributes, level));
            hasText = true;
          }
        }


        final String componentClassName = component.getComponentClassName();

        if (component instanceof RadVSpacer) {
          append(UIDesignerBundle.message("component.vertical.spacer"), getAttribute(myClassAttributes, level));
        }
        else if (component instanceof RadHSpacer) {
          append(UIDesignerBundle.message("component.horizontal.spacer"), getAttribute(myClassAttributes, level));
        }
        else if (component instanceof RadErrorComponent) {
          final RadErrorComponent c = (RadErrorComponent)component;
          append(c.getErrorDescription(), getAttribute(myUnknownAttributes, level));
        }
        else if (component instanceof RadRootContainer) {
          append(UIDesignerBundle.message("component.form"), getAttribute(myClassAttributes, level));
          append("(", getAttribute(myPackageAttributes, level));
          final String classToBind = ((RadRootContainer)component).getClassToBind();
          if (classToBind != null) {
            append(classToBind, getAttribute(myPackageAttributes, level));
          }
          else {
            append(UIDesignerBundle.message("component.no.binding"), getAttribute(myPackageAttributes, level));
          }
          append(")", getAttribute(myPackageAttributes, level));
        }
        else {
          String packageName = null;
          int pos = componentClassName.lastIndexOf('.');
          if (pos >= 0) {
            packageName = componentClassName.substring(0, pos);
          }

          SimpleTextAttributes classAttributes = hasText ? myPackageAttributes : myClassAttributes;

          if (packageName != null) {
            append(componentClassName.substring(packageName.length() + 1).replace('$', '.'),
                   getAttribute(classAttributes, level));
            if (!packageName.equals(SWING_PACKAGE)) {
              append(" (", getAttribute(myPackageAttributes, level));
              append(packageName, getAttribute(myPackageAttributes, level));
              append(")", getAttribute(myPackageAttributes, level));
            }
          }
          else {
            append(componentClassName.replace('$', '.'), getAttribute(classAttributes, level));
          }
        }

        // Icon
        setIcon(getComponentIcon(component));

        if (component == myDropTargetComponent) {
          setBorder(BorderFactory.createLineBorder(Color.BLUE, 2));
        }
      }
    }
  }

  private final class MyDropTargetListener extends DropTargetAdapter {
    public void dragOver(DropTargetDragEvent dtde) {
      RadComponent dropTargetComponent = null;
      final DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
      ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
      if (dcl != null || componentItem != null) {
        final TreePath path = getPathForLocation((int) dtde.getLocation().getX(),
                                                 (int) dtde.getLocation().getY());
        final RadComponent targetComponent = getComponentFromPath(path);
        int dropCount = (dcl != null) ? dcl.getComponents().size() : 1;
        if (path != null && targetComponent != null && targetComponent.canDrop(null, dropCount)) {
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
      setDropTargetComponent(dropTargetComponent);
    }

    public void dragExit(DropTargetEvent dte) {
      setDropTargetComponent(null);
    }

    public void drop(DropTargetDropEvent dtde) {
      final DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
      ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
      if (dcl != null || componentItem != null) {
        final TreePath path = getPathForLocation((int) dtde.getLocation().getX(),
                                                 (int) dtde.getLocation().getY());
        final RadComponent targetComponent = getComponentFromPath(path);
        if (targetComponent instanceof RadContainer) {
          RadContainer container = (RadContainer)targetComponent;
          if (dcl != null) {
            RadComponent[] components = dcl.getComponents().toArray(new RadComponent [dcl.getComponents().size()]);
            container.drop(null, components, null, null);
          }
          else {
            new InsertComponentProcessor(myEditor).processComponentInsert(null, container, componentItem);
          }
        }
      }
      setDropTargetComponent(null);
    }
  }
}
