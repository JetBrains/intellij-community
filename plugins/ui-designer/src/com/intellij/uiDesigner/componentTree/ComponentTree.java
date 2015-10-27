/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import java.util.*;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ComponentTree extends Tree implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.componentTree.ComponentTree");

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

  @NonNls private static final String ourHelpID = "guiDesigner.uiTour.compsTree";
  private final Project myProject;

  public ComponentTree(@NotNull final Project project) {
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

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      setDragEnabled(true);
      setTransferHandler(new TransferHandler() {
        public int getSourceActions(JComponent c) {
          return DnDConstants.ACTION_COPY_OR_MOVE;
        }

        protected Transferable createTransferable(JComponent c) {
          return DraggedComponentList.pickupSelection(myEditor, null);
        }
      });
      setDropTarget(new DropTarget(this, new MyDropTargetListener()));
    }
  }

  @NotNull
  public Project getProject() {
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
        final ComponentPtrDescriptor descriptor = (ComponentPtrDescriptor)userObject;
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
   * @return first selected component. The method returns <code>null</code>
   *         if there is no selection in the tree.
   */
  @Nullable
  public RadComponent getSelectedComponent() {
    return ArrayUtil.getFirstElement(getSelectedComponents());
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
      if (node != null && node.getUserObject() instanceof ComponentPtrDescriptor) {
        final ComponentPtrDescriptor descriptor = (ComponentPtrDescriptor)node.getUserObject();
        final ComponentPtr ptr = descriptor.getElement();
        if (ptr != null && ptr.isValid()) {
          result.add(ptr.getComponent());
        }
      }
    }
    return result.toArray(new RadComponent[result.size()]);
  }

  /**
   * Provides {@link PlatformDataKeys#NAVIGATABLE} to navigate to
   * binding of currently selected component (if any)
   */
  public Object getData(final String dataId) {
    if (GuiEditor.DATA_KEY.is(dataId)) {
      return myEditor;
    }

    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return myDeleteProvider;
    }

    if (PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return myEditor == null ? null : myEditor.getData(dataId);
    }

    if (LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY.is(dataId)) {
      Collection<LwInspectionSuppression> elements = getSelectedElements(LwInspectionSuppression.class);
      return elements.size() == 0 ? null : elements.toArray(new LwInspectionSuppression[elements.size()]);
    }

    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return ourHelpID;
    }

    if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
      return myFormEditor;
    }

    if (!CommonDataKeys.NAVIGATABLE.is(dataId)) {
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

  public <T> List<T> getSelectedElements(Class<? extends T> elementClass) {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) {
      return Collections.emptyList();
    }
    final ArrayList<T> result = new ArrayList<T>(paths.length);
    for (TreePath path : paths) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor && elementClass.isInstance(((NodeDescriptor) userObject).getElement())) {
        //noinspection unchecked
        result.add((T)((NodeDescriptor) node.getUserObject()).getElement());
      }
    }
    return result;
  }

  private SimpleTextAttributes getAttribute(@NotNull final SimpleTextAttributes attrs,
                                            @Nullable HighlightDisplayLevel level) {
    if (level == null) {
      return attrs;
    }

    Map<SimpleTextAttributes, SimpleTextAttributes> highlightMap = myHighlightAttributes.get(level.getSeverity());
    if (highlightMap == null) {
      highlightMap = new HashMap<SimpleTextAttributes, SimpleTextAttributes>();
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

  public void setUI(final TreeUI ui) {
    super.setUI(ui);

    // [vova] we cannot create this hash in constructor and just clear it here. The
    // problem is that setUI is invoked by constructor of superclass.
    myHighlightAttributes = new HashMap<HighlightSeverity, Map<SimpleTextAttributes, SimpleTextAttributes>>();

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
        icon = UIDesignerIcons.Unknown_small;
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
        else if (component instanceof RadErrorComponent) {
          final RadErrorComponent c = (RadErrorComponent)component;
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
        final String fragment = node.getUserObject().toString();
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

    public void dragExit(DropTargetEvent dte) {
      setDropTargetComponent(null);
    }

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
                RadComponent[] components = dcl.getComponents().toArray(new RadComponent [dcl.getComponents().size()]);
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

    public void deleteElement(@NotNull DataContext dataContext) {
      if (myEditor != null) {
        LwInspectionSuppression[] suppressions = LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY.getData(dataContext);
        if (suppressions != null) {
          if (!myEditor.ensureEditable()) return;
          for(LwInspectionSuppression suppression: suppressions) {
            myEditor.getRootContainer().removeInspectionSuppression(suppression);
          }
          myEditor.refreshAndSave(true);
        }
        else {
          DeleteProvider baseProvider = (DeleteProvider) myEditor.getData(PlatformDataKeys.DELETE_ELEMENT_PROVIDER.getName());
          if (baseProvider != null) {
            baseProvider.deleteElement(dataContext);
          }
        }
      }
    }

    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      if (myEditor != null) {
        LwInspectionSuppression[] suppressions = LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY.getData(dataContext);
        if (suppressions != null) {
          return true;
        }
        DeleteProvider baseProvider = (DeleteProvider) myEditor.getData(PlatformDataKeys.DELETE_ELEMENT_PROVIDER.getName());
        if (baseProvider != null) {
          return baseProvider.canDeleteElement(dataContext);
        }
      }
      return false;
    }
  }
}
