package com.intellij.uiDesigner;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GroupSelectionProcessor extends EventProcessor{
  private final GuiEditor myEditor;
  private final RadContainer myContainer;
  private Point myStartPoint;
  private final MyRectanglePainter myRectangePainter;

  /**
   * @param container group where drag is started. This group should not be selected
   * after drag is complete.
   */
  public GroupSelectionProcessor(final GuiEditor editor,final RadContainer container){
    myEditor = editor;
    myContainer = container;
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

      markRectangle(myEditor.getRootContainer(), rectangle, e.getComponent());

      final JComponent dragLayer = myEditor.getDragLayer();
      dragLayer.remove(myRectangePainter);
      dragLayer.repaint();

      myStartPoint = null;
    }
  }

  protected boolean cancelOperation(){
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
    if (!(component instanceof RadRootContainer) && !component.equals(myContainer)) {
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
      for (int i = 0; i < components.length; i++) {
        markRectangle(components[i], rectangle, coordinateOriginComponent);
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
