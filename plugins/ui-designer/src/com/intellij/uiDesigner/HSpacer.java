// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner;

import com.intellij.ui.paint.LinePainter2D;

import java.awt.*;

public final class HSpacer extends DesignSpacer{
  public HSpacer(){
    setSize(50,getHandleHeight());
  }

  @Override
  protected void paintComponent(final Graphics g){
    final int handleHeight=getHandleHeight();
    final int handleWidth=getHandleWidth();

    // Paint left handle
    final int y=(getHeight()-handleHeight)/2;
    drawHandle(g,0,y);
    g.setColor(ourColor1);
    LinePainter2D.paint((Graphics2D)g, handleWidth, y + handleHeight / 2, handleWidth + 1, y + handleHeight / 2);

    // Paint right handle
    final int x=getWidth()-handleWidth-1;
    drawHandle(g,x,y);
    LinePainter2D.paint((Graphics2D)g, x, y + handleHeight / 2, x - 2, y + handleHeight / 2);
    g.setColor(ourColor1);

    // Draw spring
    drawSpring(g,handleWidth+1,y+handleHeight/2,getWidth()-2*handleWidth-4);
  }

  private static int getHandleWidth(){
    return HANDLE_ATOM_WIDTH;
  }

  private static int getHandleHeight(){
    return HANDLE_ATOM_HEIGHT*3 + HANDLE_ATOM_SPACE*2;
  }

  /**
   * Paints small spacer's haldle. {@code (x,y)} is a top
   * left point of a handle.
   */
  private static void drawHandle(final Graphics g,final int x,int y){
    g.setColor(ourColor1);

    g.drawRect(x,y,HANDLE_ATOM_WIDTH-1,HANDLE_ATOM_HEIGHT-1);
    LinePainter2D.paint((Graphics2D)g, x + HANDLE_ATOM_WIDTH / 2, y + HANDLE_ATOM_HEIGHT, x + HANDLE_ATOM_WIDTH / 2,
                        y + HANDLE_ATOM_HEIGHT + HANDLE_ATOM_SPACE - 1);

    y+=HANDLE_ATOM_HEIGHT+HANDLE_ATOM_SPACE;

    g.drawRect(x,y,HANDLE_ATOM_WIDTH-1,HANDLE_ATOM_HEIGHT-1);
    LinePainter2D.paint((Graphics2D)g, x + HANDLE_ATOM_WIDTH / 2, y + HANDLE_ATOM_HEIGHT, x + HANDLE_ATOM_WIDTH / 2,
                        y + HANDLE_ATOM_HEIGHT + HANDLE_ATOM_SPACE - 1);

    y+=HANDLE_ATOM_HEIGHT+HANDLE_ATOM_SPACE;

    g.drawRect(x,y,HANDLE_ATOM_WIDTH-1,HANDLE_ATOM_HEIGHT-1);
  }

  private static void drawSpring(final Graphics g,final int x,final int y,final int width){
    for(int _x=x;_x<x+width-1;_x+=SPRING_PRERIOD){
      drawSpringPeriod(g,_x,y);
    }
  }

  private static void drawSpringPeriod(final Graphics g,final int x,final int y){
    g.setColor(ourColor2);
    LinePainter2D.paint((Graphics2D)g, x, y - 1, x, y - 2);
    LinePainter2D.paint((Graphics2D)g, x + 1, y, x + 1, y);
    LinePainter2D.paint((Graphics2D)g, x + 2, y + 1, x + 2, y + 2);

    g.setColor(ourColor3);
    LinePainter2D.paint((Graphics2D)g, x + 3, y, x + 3, y);
  }

  @Override
  public Dimension getMinimumSize(){
    return new Dimension(getHandleWidth()*2+SPRING_PRERIOD,getHandleHeight());
  }
}
