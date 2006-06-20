package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.util.IconLoader;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.Collection;

/**
 * Decoration layer is over COMPONENT_LAYER (layer where all components are located).
 * It contains all necessary decorators. Decorators are:
 * - special borders to show component bounds and cell bounds inside grids
 * - special component which marks selected rectangle
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
class PassiveDecorationLayer extends JComponent{
  @NotNull private final GuiEditor myEditor;
  private static Icon ourDragIcon;

  public PassiveDecorationLayer(@NotNull final GuiEditor editor) {
    myEditor = editor;
  }

  /**
   * Paints all necessary decoration for the specified <code>component</code>
   */
  protected final void paintPassiveDecoration(final RadComponent component, final Graphics g){
    // Paint component bounds and grid markers
    Painter.paintComponentDecoration(myEditor, component, g);

    final Set<RadButtonGroup> paintedGroups = new HashSet<RadButtonGroup>();
    final RadRootContainer rootContainer = myEditor.getRootContainer();
    final ComponentTree componentTree = UIDesignerToolWindowManager.getInstance(component.getProject()).getComponentTree();
    final Collection<RadButtonGroup> selectedGroups = componentTree.getSelectedElements(RadButtonGroup.class);

    // Paint selection and dragger
    FormEditingUtil.iterate(
      component,
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          final Point point = SwingUtilities.convertPoint(
            component.getDelegee(),
            0,
            0,
            rootContainer.getDelegee()
          );
          RadButtonGroup group = rootContainer.findGroupForComponent(component);
          if (group != null && !paintedGroups.contains(group) && (component.isSelected() || selectedGroups.contains(group))) {
            paintedGroups.add(group);
            Painter.paintButtonGroupLines(rootContainer, group, g);
          }
          g.translate(point.x, point.y);
          try{
            if (myEditor.isShowComponentTags()) {
              Painter.paintComponentTag(component, g);
            }
            Painter.paintSelectionDecoration(component, g,myEditor.getGlassLayer().isFocusOwner());
            // Over selection we have to paint dragger
            if (component.hasDragger()){
              final Icon icon = getDragIcon();
              icon.paintIcon(PassiveDecorationLayer.this, g, - icon.getIconWidth(), - icon.getIconHeight());
            }
          }finally{
            g.translate(-point.x, -point.y);
          }
          return true;
        }
      }
    );
  }

  private static Icon getDragIcon() {
    if (ourDragIcon == null) {
      ourDragIcon = IconLoader.getIcon("/com/intellij/uiDesigner/icons/drag.png");
    }
    return ourDragIcon;
  }

  public void paint(final Graphics g){
    // Passive decoration
    final RadRootContainer root = myEditor.getRootContainer();
    for(int i = root.getComponentCount() - 1; i >= 0; i--){
      final RadComponent component = root.getComponent(i);
      paintPassiveDecoration(component, g);
    }

    // Paint active decorators
    paintChildren(g);
  }
}
