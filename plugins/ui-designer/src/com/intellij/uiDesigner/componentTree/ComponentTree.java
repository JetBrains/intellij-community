// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.componentTree;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.actions.StartInplaceEditingAction;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.lw.LwInspectionSuppression;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;
import com.intellij.uiDesigner.radComponents.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import icons.UIDesignerIcons;
import org.jetbrains.annotations.ApiStatus;
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
import java.util.List;
import java.util.*;

public final class ComponentTree extends Tree implements UiDataProvider {
  private static final Logger LOG = Logger.getInstance(ComponentTree.class);

  public static final DataKey<LwInspectionSuppression[]> LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY =
    DataKey.create(LwInspectionSuppression.class.getName());

  private SimpleTextAttributes myBindingAttributes; // exists only for performance reason
  private SimpleTextAttributes myClassAttributes; // exists only for performance reason
  private SimpleTextAttributes myPackageAttributes; // exists only for performance reason
  private SimpleTextAttributes myUnknownAttributes; // exists only for performance reason
  private SimpleTextAttributes myTitleAttributes; // exists only for performance reason

  private Map<HighlightSeverity, Map<SimpleTextAttributes, SimpleTextAttributes>> myHighlightAttributes;

  private GuiEditor myEditor;
  private UIFormEditor myFormEditor;
  private QuickFixManager myQuickFixManager;
  private RadComponent myDropTargetComponent = null;
  private final StartInplaceEditingAction myStartInplaceEditingAction;
  private final MyDeleteProvider myDeleteProvider = new MyDeleteProvider();

  private static final @NonNls String ourHelpID = "guiDesigner.uiTour.compsTree";
  private final Project myProject;

  public ComponentTree(final @NotNull Project project) {
    super(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myProject = project;

    setCellRenderer(new MyTreeCellRenderer());
    setRootVisible(false);
    setShowsRootHandles(true);

    // Enable tooltips
    ToolTipManager.sharedInstance().registerComponent(this);

    // Install convenient keyboard navigation
    TreeUtil.installActions(this);

    // Popup menu
    PopupHandler.installPopupMenu(
      this,
      IdeActions.GROUP_GUI_DESIGNER_COMPONENT_TREE_POPUP,
      ActionPlaces.GUI_DESIGNER_COMPONENT_TREE_POPUP);

    // F2 should start inplace editing
    myStartInplaceEditingAction = new StartInplaceEditingAction(null);
    myStartInplaceEditingAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)),
      this
    );

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      setDragEnabled(true);
      setTransferHandler(new TransferHandler() {
        @Override
        public int getSourceActions(JComponent c) {
          return DnDConstants.ACTION_COPY_OR_MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
          return DraggedComponentList.pickupSelection(myEditor, null);
        }
      });
      setDropTarget(new DropTarget(this, new MyDropTargetListener()));
    }
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public void initQuickFixManager(JViewport viewPort) {
    myQuickFixManager = new QuickFixManagerImpl(null, this, viewPort);
  }

  public void setEditor(final GuiEditor editor) {
    myEditor = editor;
    myDeleteProvider.setEditor(editor);
    myQuickFixManager.setEditor(editor);
    myStartInplaceEditingAction.setEditor(editor);
  }

  public GuiEditor getEditor() {
    return myEditor;
  }

  public void refreshIntentionHint() {
    myQuickFixManager.refreshIntentionHint();
  }

  @Override
  public @Nullable String getToolTipText(final MouseEvent e) {
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

  private static @Nullable RadComponent getComponentFromPath(TreePath path) {
    if (path != null) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      LOG.assertTrue(node != null);
      final Object userObject = node.getUserObject();
      if (userObject instanceof ComponentPtrDescriptor descriptor) {
        final ComponentPtr ptr = descriptor.getElement();
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
   * @return first selected component. The method returns {@code null}
   *         if there is no selection in the tree.
   */
  public @Nullable RadComponent getSelectedComponent() {
    return ArrayUtil.getFirstElement(getSelectedComponents());
  }

  /**
   * TODO[vova] should return pair <RadComponent, TreePath>
   *
   * @return currently selected components.
   */
  public RadComponent @NotNull [] getSelectedComponents() {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) {
      return RadComponent.EMPTY_ARRAY;
    }
    final ArrayList<RadComponent> result = new ArrayList<>(paths.length);
    for (TreePath path : paths) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node != null && node.getUserObject() instanceof ComponentPtrDescriptor descriptor) {
        final ComponentPtr ptr = descriptor.getElement();
        if (ptr != null && ptr.isValid()) {
          result.add(ptr.getComponent());
        }
      }
    }
    return result.toArray(RadComponent.EMPTY_ARRAY);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(GuiEditor.DATA_KEY, myEditor);

    sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteProvider);

    if (myEditor != null) {
      sink.set(PlatformDataKeys.COPY_PROVIDER, myEditor.getCutCopyPasteDelegator());
      sink.set(PlatformDataKeys.CUT_PROVIDER, myEditor.getCutCopyPasteDelegator());
      sink.set(PlatformDataKeys.PASTE_PROVIDER, myEditor.getCutCopyPasteDelegator());
    }

    Collection<LwInspectionSuppression> elements = getSelectedElements(LwInspectionSuppression.class);
    sink.set(LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY,
             elements.isEmpty() ? null : elements.toArray(LwInspectionSuppression.EMPTY_ARRAY));

    sink.set(PlatformCoreDataKeys.HELP_ID, ourHelpID);
    sink.set(PlatformCoreDataKeys.FILE_EDITOR, myFormEditor);

    RadComponent selectedComponent = getSelectedComponent();
    if (selectedComponent == null) return;
    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> {
      return getPsiFile(selectedComponent);
    });
  }

  @ApiStatus.Internal
  public @Nullable Navigatable getPsiFile(@NotNull RadComponent selectedComponent) {
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

  public <T> List<T> getSelectedElements(Class<? extends T> elementClass) {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) {
      return Collections.emptyList();
    }
    final ArrayList<T> result = new ArrayList<>(paths.length);
    for (TreePath path : paths) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor && elementClass.isInstance(((NodeDescriptor<?>) userObject).getElement())) {
        //noinspection unchecked
        result.add((T)((NodeDescriptor<?>) node.getUserObject()).getElement());
      }
    }
    return result;
  }

  private SimpleTextAttributes getAttribute(final @NotNull SimpleTextAttributes attrs,
                                            @Nullable HighlightDisplayLevel level) {
    if (level == null) {
      return attrs;
    }

    Map<SimpleTextAttributes, SimpleTextAttributes> highlightMap = myHighlightAttributes.get(level.getSeverity());
    if (highlightMap == null) {
      highlightMap = new HashMap<>();
      myHighlightAttributes.put(level.getSeverity(), highlightMap);
    }

    SimpleTextAttributes result = highlightMap.get(attrs);
    if (result == null) {
      final TextAttributesKey attrKey = SeverityRegistrar.getSeverityRegistrar(myProject).getHighlightInfoTypeBySeverity(level.getSeverity()).getAttributesKey();
      TextAttributes textAttrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attrKey);
      textAttrs = TextAttributes.merge(attrs.toTextAttributes(), textAttrs);
      result = SimpleTextAttributes.fromTextAttributes(textAttrs);
      highlightMap.put(attrs, result);
    }

    return result;
  }

  @Override
  public void setUI(final TreeUI ui) {
    super.setUI(ui);

    // [vova] we cannot create this hash in constructor and just clear it here. The
    // problem is that setUI is invoked by constructor of superclass.
    myHighlightAttributes = new HashMap<>();

    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes attributes = globalScheme.getAttributes(JavaHighlightingColors.STRING);

    myBindingAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getTreeForeground());
    myClassAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getTreeForeground());
    myPackageAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY);
    myTitleAttributes =new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, attributes.getForegroundColor());
    myUnknownAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, JBColor.RED);
  }

  public static Icon getComponentIcon(final RadComponent component) {
    if (!(component instanceof RadErrorComponent)) {
      final Palette palette = Palette.getInstance(component.getProject());
      final ComponentItem item = palette.getItem(component.getComponentClassName());
      final Icon icon;
      if (item != null) {
        icon = item.getSmallIcon();
      }
      else {
        icon = UIDesignerIcons.Unknown;
      }
      return icon;
    }
    else {
      return AllIcons.General.Error;
    }
  }

  public void setDropTargetComponent(final @Nullable RadComponent dropTargetComponent) {
    if (dropTargetComponent != myDropTargetComponent) {
      myDropTargetComponent = dropTargetComponent;
      repaint();
    }
  }

  public void setFormEditor(final UIFormEditor formEditor) {
    myFormEditor = formEditor;
  }

  private final class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    private static final @NonNls String SWING_PACKAGE = "javax.swing";

    @Override
    public void customizeCellRenderer(
      final @NotNull JTree tree,
      final Object value,
      final boolean selected,
      final boolean expanded,
      final boolean leaf,
      final int row,
      final boolean hasFocus
    ) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      if (node.getUserObject() instanceof ComponentPtrDescriptor descriptor) {
        final ComponentPtr ptr = descriptor.getElement();
        if (ptr == null) return;
        final RadComponent component = ptr.getComponent();
        if (component == null) return;

        final HighlightDisplayLevel level = ErrorAnalyzer.getHighlightDisplayLevel(myProject, component);

        // Text
        boolean hasText = false;
        final String binding = component.getBinding();
        if (binding != null) {
          append(binding, getAttribute(myBindingAttributes, level));
          append(" : ", getAttribute(myClassAttributes, level));
          hasText = true;
        }
        else {
          String componentTitle = component.getComponentTitle();
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
        else if (component instanceof RadErrorComponent c) {
          append(c.getErrorDescription(), getAttribute(myUnknownAttributes, level));
        }
        else if (component instanceof RadRootContainer) {
          append(UIDesignerBundle.message("component.form"), getAttribute(myClassAttributes, level));
          append(" (", getAttribute(myPackageAttributes, level));
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
          setBorder(BorderFactory.createLineBorder(PlatformColors.BLUE, 1));
        } else {
          setBorder(BorderFactory.createEmptyBorder(1,1,1,1));
        }
      }
      else if (node.getUserObject() != null) {
        final @NlsSafe String fragment = node.getUserObject().toString();
        if (fragment != null) {
          append(fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        if (node.getUserObject() instanceof SuppressionDescriptor) {
          setIcon(UIDesignerIcons.InspectionSuppression);
        }
        else if (node.getUserObject() instanceof ButtonGroupDescriptor) {
          setIcon(UIDesignerIcons.ButtonGroup);
        }
      }
    }
  }

  private final class MyDropTargetListener extends DropTargetAdapter {
    @Override
    public void dragOver(DropTargetDragEvent dtde) {
      try {
        RadComponent dropTargetComponent = null;
        ComponentDragObject dragObject = null;

        final DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
        if (dcl != null) {
          dragObject = dcl;
        }
        else {
          ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
          if (componentItem != null) {
            dragObject = new ComponentItemDragObject(componentItem);
          }
        }

        boolean canDrop = false;
        if (dragObject != null) {
          final TreePath path = getPathForLocation((int) dtde.getLocation().getX(),
                                                   (int) dtde.getLocation().getY());
          final RadComponent targetComponent = getComponentFromPath(path);
          if (path != null && targetComponent instanceof RadContainer) {
            final ComponentDropLocation dropLocation = ((RadContainer)targetComponent).getDropLocation(null);
            canDrop = dropLocation.canDrop(dragObject);
            if (dcl != null && FormEditingUtil.isDropOnChild(dcl, dropLocation)) {
              canDrop = false;
            }
            if (canDrop) {
              dropTargetComponent = targetComponent;
              dtde.acceptDrag(dtde.getDropAction());
            }
          }
        }
        if (!canDrop) {
          dtde.rejectDrag();
        }
        setDropTargetComponent(dropTargetComponent);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
      setDropTargetComponent(null);
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
      try {
        final DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
        ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
        if (dcl != null || componentItem != null) {
          final TreePath path = getPathForLocation((int) dtde.getLocation().getX(),
                                                   (int) dtde.getLocation().getY());
          final RadComponent targetComponent = getComponentFromPath(path);
          if (!myEditor.ensureEditable()) return;
          if (targetComponent instanceof RadContainer) {
            final ComponentDropLocation dropLocation = ((RadContainer)targetComponent).getDropLocation(null);
            if (dcl != null) {
              if (!FormEditingUtil.isDropOnChild(dcl, dropLocation)) {
                RadComponent[] components = dcl.getComponents().toArray(RadComponent.EMPTY_ARRAY);
                RadContainer[] originalParents = dcl.getOriginalParents();
                final GridConstraints[] originalConstraints = dcl.getOriginalConstraints();
                for(int i=0; i<components.length; i++) {
                  originalParents [i].removeComponent(components [i]);
                }
                dropLocation.processDrop(myEditor, components, null, dcl);
                for (int i = 0; i < originalConstraints.length; i++) {
                  if (originalParents[i].getLayoutManager().isGrid()) {
                    FormEditingUtil.deleteEmptyGridCells(originalParents[i], originalConstraints[i]);
                  }
                }
              }
            }
            else {
              new InsertComponentProcessor(myEditor).processComponentInsert(componentItem, dropLocation);
            }
          }
          myEditor.refreshAndSave(true);
        }
        setDropTargetComponent(null);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private static class MyDeleteProvider implements DeleteProvider {
    private GuiEditor myEditor;

    public void setEditor(final GuiEditor editor) {
      myEditor = editor;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      DeleteProvider baseProvider = myEditor == null ? null : myEditor.getDeleteProvider();
      return baseProvider == null ? ActionUpdateThread.BGT : baseProvider.getActionUpdateThread();
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      if (myEditor == null) return;
      LwInspectionSuppression[] suppressions = LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY.getData(dataContext);
      if (suppressions != null) {
        if (!myEditor.ensureEditable()) return;
        for(LwInspectionSuppression suppression: suppressions) {
          myEditor.getRootContainer().removeInspectionSuppression(suppression);
        }
        myEditor.refreshAndSave(true);
      }
      else {
        DeleteProvider baseProvider = myEditor.getDeleteProvider();
        baseProvider.deleteElement(dataContext);
      }
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      if (myEditor != null) {
        LwInspectionSuppression[] suppressions = LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY.getData(dataContext);
        if (suppressions != null) {
          return true;
        }
        DeleteProvider baseProvider = myEditor.getDeleteProvider();
        return baseProvider.canDeleteElement(dataContext);
      }
      return false;
    }
  }
}