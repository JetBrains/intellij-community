// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import icons.UIDesignerIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Decoration layer is over COMPONENT_LAYER (layer where all components are located).
 * It contains all necessary decorators. Decorators are:
 * - special borders to show component bounds and cell bounds inside grids
 * - special component which marks selected rectangle
 */
class PassiveDecorationLayer extends JComponent{
  @NotNull private final GuiEditor myEditor;

  PassiveDecorationLayer(@NotNull final GuiEditor editor) {
    myEditor = editor;
  }

  /**
   * Paints all necessary decoration for the specified {@code component}
   */
  protected final void paintPassiveDecoration(final RadComponent component, final Graphics g){
    // Paint component bounds and grid markers
    Painter.paintComponentDecoration(myEditor, component, g);

    final Set<RadButtonGroup> paintedGroups = new HashSet<>();
    final RadRootContainer rootContainer = myEditor.getRootContainer();
    final ComponentTree componentTree = DesignerToolWindowManager.getInstance(myEditor).getComponentTree();
    final Collection<RadButtonGroup> selectedGroups = componentTree != null
                                                      ? componentTree.getSelectedElements(RadButtonGroup.class)
                                                      : Collections.emptyList();

    // Paint selection and dragger
    FormEditingUtil.iterate(
      component,
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        @Override
        public boolean visit(final RadComponent component) {
          final Point point = SwingUtilities.convertPoint(
            component.getDelegee(),
            0,
            0,
            rootContainer.getDelegee()
          );
          RadButtonGroup group = (RadButtonGroup)FormEditingUtil.findGroupForComponent(rootContainer, component);
          if (group != null && !paintedGroups.contains(group) && (component.isSelected() || selectedGroups.contains(group))) {
            paintedGroups.add(group);
            Painter.paintButtonGroupLines(rootContainer, group, g);
          }
          g.translate(point.x, point.y);
          try{
            if (myEditor.isShowComponentTags() && FormEditingUtil.isComponentSwitchedInView(component)) {
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
    return UIDesignerIcons.Drag;
  }

  @Override
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
