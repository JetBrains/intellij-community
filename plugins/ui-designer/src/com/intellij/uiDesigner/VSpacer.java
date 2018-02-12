/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner;

import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class VSpacer extends DesignSpacer{
  public VSpacer(){
    setSize(getHandleWidth(), 50);
  }

  private static int getHandleWidth(){
    return HANDLE_ATOM_HEIGHT*3 + HANDLE_ATOM_SPACE*2;
  }

  private static int getHandleHeight(){
    return HANDLE_ATOM_WIDTH;
  }

  protected void paintComponent(final Graphics g){
    final int handleHeight=getHandleHeight();
    final int handleWidth=getHandleWidth();

    // Paint top handle
    final int x=(getWidth()-handleWidth)/2;
    drawHandle(g,x,0);
    g.setColor(ourColor1);
    UIUtil.drawLine(g, x + handleWidth / 2, handleHeight, x + handleWidth / 2, handleHeight + 1);

    // Paint bottom handle
    final int y=getHeight()-handleHeight-1;
    drawHandle(g,x,y);
    UIUtil.drawLine(g, x + handleWidth / 2, y - 2, x + handleWidth / 2, y);
    g.setColor(ourColor1);

    // Draw spring
    drawSpring(g,x+handleWidth/2,handleHeight+1,getHeight()-2*handleHeight-4);
  }

  /**
   * Paints small spacer's haldle. {@code (x,y)} is a top
   * left point of a handle.
   */
  private static void drawHandle(final Graphics g,int x,final int y){
    g.setColor(ourColor1);

    g.drawRect(x,y,HANDLE_ATOM_HEIGHT-1,HANDLE_ATOM_WIDTH-1);
    UIUtil.drawLine(g, x + HANDLE_ATOM_HEIGHT, y + HANDLE_ATOM_WIDTH / 2, x + HANDLE_ATOM_HEIGHT + HANDLE_ATOM_SPACE - 1,
                    y + HANDLE_ATOM_WIDTH / 2);

    x+=HANDLE_ATOM_HEIGHT+HANDLE_ATOM_SPACE;

    g.drawRect(x,y,HANDLE_ATOM_HEIGHT-1,HANDLE_ATOM_WIDTH-1);
    UIUtil.drawLine(g, x + HANDLE_ATOM_HEIGHT, y + HANDLE_ATOM_WIDTH / 2, x + HANDLE_ATOM_HEIGHT + HANDLE_ATOM_SPACE - 1,
                    y + HANDLE_ATOM_WIDTH / 2);

    x+=HANDLE_ATOM_HEIGHT+HANDLE_ATOM_SPACE;

    g.drawRect(x,y,HANDLE_ATOM_HEIGHT-1,HANDLE_ATOM_WIDTH-1);
  }

  private static void drawSpring(final Graphics g,final int x,final int y,final int height){
    for(int _y=y;_y<y+height-1;_y+=SPRING_PRERIOD){
      drawSpringPeriod(g,x,_y);
    }
  }

  private static void drawSpringPeriod(final Graphics g,final int x,final int y){
    g.setColor(ourColor2);
    UIUtil.drawLine(g, x + 1, y, x + 2, y);
    UIUtil.drawLine(g, x, y + 1, x, y + 1);
    UIUtil.drawLine(g, x - 1, y + 2, x - 2, y + 2);

    g.setColor(ourColor3);
    UIUtil.drawLine(g, x, y + 3, x, y + 3);
  }

  public Dimension getMinimumSize(){
    return new Dimension(getHandleWidth(),getHandleHeight()*2+SPRING_PRERIOD);
  }
}
