package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.util.IconLoader;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadRootContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

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
    //noinspection ConstantConditions
    if (editor == null) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("editor cannot be null");
    }
    myEditor = editor;
  }

  /**
   * Paints all necessary decoration for the specified <code>component</code>
   */
  protected final void paintPassiveDecoration(final RadComponent component, final Graphics g){
    // Paint component bounds and grid markers
    Painter.paintComponentDecoration(myEditor, component, g);

    // Paint selection and dragger
    FormEditingUtil.iterate(
      component,
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          final Point point = SwingUtilities.convertPoint(
            component.getDelegee(),
            0,
            0,
            myEditor.getRootContainer().getDelegee()
          );
          g.translate(point.x, point.y);
          try{
            Painter.paintSelectionDecoration(component, g);
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
