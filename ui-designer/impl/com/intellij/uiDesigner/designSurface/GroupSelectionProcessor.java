package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.core.GridConstraints;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GroupSelectionProcessor extends EventProcessor {
  private final GuiEditor myEditor;
  private final RadComponent myComponent;
  private Point myStartPoint;
  private final MyRectanglePainter myRectangePainter;

  /**
   * @param container group where drag is started. This group should not be selected
   * after drag is complete.
   */
  public GroupSelectionProcessor(final GuiEditor editor, final RadComponent component) {
    myEditor = editor;
    myComponent = component;
    myRectangePainter=new MyRectanglePainter();
  }

  protected void processKeyEvent(final KeyEvent e){
  }

  protected void processMouseEvent(final MouseEvent e){
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      myStartPoint = e.getPoint();
      myEditor.getDragLayer().add(myRectangePainter);
    }
    else if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
      final Rectangle rectangle = getRectangle(e);
      myRectangePainter.setBounds(rectangle);
      myEditor.getDragLayer().repaint();
    }
    else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      final Rectangle rectangle = getRectangle(e);
      if (e.isShiftDown() && rectangle.width <= 3 && rectangle.height <= 3) {
        RadComponent component = FormEditingUtil.getRadComponentAt(myEditor.getRootContainer(), e.getX(), e.getY());
        if (component != null) {
          RadComponent anchor = myEditor.getSelectionAnchor();
          if (anchor == null || anchor.getParent() != component.getParent() || anchor.getParent() == null || !anchor.getParent().getLayoutManager().isGrid()) {
            component.setSelected(!component.isSelected());
          }
          else {
            selectComponentsInRange(component, anchor);
          }
        }
      }
      markRectangle(myEditor.getRootContainer(), rectangle, e.getComponent());
      final JComponent dragLayer = myEditor.getDragLayer();
      dragLayer.remove(myRectangePainter);
      dragLayer.repaint();
      myStartPoint = null;
    }
  }

  private static void selectComponentsInRange(final RadComponent component, final RadComponent anchor) {
    final GridConstraints c1 = component.getConstraints();
    final GridConstraints c2 = anchor.getConstraints();
    int startRow = Math.min(c1.getRow(), c2.getRow());
    int startCol = Math.min(c1.getColumn(), c2.getColumn());
    int endRow = Math.max(c1.getRow() + c1.getRowSpan(), c2.getRow() + c2.getRowSpan());
    int endCol = Math.max(c1.getColumn() + c1.getColSpan(), c2.getColumn() + c2.getColSpan());
    for(int row=startRow; row<endRow; row++) {
      for(int col=startCol; col<endCol; col++) {
        RadComponent c = anchor.getParent().getComponentAtGrid(row, col);
        if (c != null) {
          c.setSelected(true);
        }
      }
    }
  }

  protected boolean cancelOperation() {
    final JComponent dragLayer = myEditor.getDragLayer();
    dragLayer.remove(myRectangePainter);
    dragLayer.repaint();
    return true;
  }

  private Rectangle getRectangle(final MouseEvent e){
    final int x = Math.min(myStartPoint.x, e.getX());
    final int y = Math.min(myStartPoint.y, e.getY());

    final int width = Math.abs(myStartPoint.x - e.getX());
    final int height = Math.abs(myStartPoint.y - e.getY());

    return new Rectangle(x, y, width, height);
  }

  private void markRectangle(
    final RadComponent component,
    final Rectangle rectangle,
    final Component coordinateOriginComponent
  ){
    if (!(component instanceof RadRootContainer) && !component.equals(myComponent)) {
      final Rectangle bounds = component.getBounds();
      final Point point = SwingUtilities.convertPoint(component.getDelegee().getParent(), bounds.x, bounds.y, coordinateOriginComponent);
      bounds.setLocation(point);

      if(rectangle.intersects(bounds)){
        component.setSelected(true);
        return;
      }
    }

    if (component instanceof RadContainer){
      final RadContainer container = (RadContainer)component;
      // [anton] it is very important to iterate through a STORED array because setSelected can
      // change order of components so iteration via getComponent(i) is incorrect 
      final RadComponent[] components = container.getComponents();
      for (RadComponent component1 : components) {
        markRectangle(component1, rectangle, coordinateOriginComponent);
      }
    }
  }

  private static final class MyRectanglePainter extends JComponent{
    private final AlphaComposite myComposite1;
    private final AlphaComposite myComposite2;
    private final Color myColor;


    public MyRectanglePainter() {
      myComposite1 = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.3f);
      myComposite2 = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.6f);
      myColor = new Color(47, 67, 96);
    }

    protected void paintComponent(final Graphics g){
      final Graphics2D g2d = (Graphics2D)g;
      super.paintComponent(g);
      final Composite oldComposite = g2d.getComposite();
      final Color oldColor = g2d.getColor();
      g2d.setColor(myColor);

      g2d.setComposite(myComposite1);
      g2d.fillRect(0, 0, getWidth(), getHeight());

      g2d.setComposite(myComposite2);
      g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

      g2d.setColor(oldColor);
      g2d.setComposite(oldComposite);
    }
  }
}
