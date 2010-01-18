/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex 1.4.3                                                             *
 * Copyright (C) 1998-2009  Gerwin Klein <lsf@jflex.de>                    *
 * All rights reserved.                                                    *
 *                                                                         *
 * This program is free software; you can redistribute it and/or modify    *
 * it under the terms of the GNU General Public License. See the file      *
 * COPYRIGHT for more information.                                         *
 *                                                                         *
 * This program is distributed in the hope that it will be useful,         *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of          *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the           *
 * GNU General Public License for more details.                            *
 *                                                                         *
 * You should have received a copy of the GNU General Public License along *
 * with this program; if not, write to the Free Software Foundation, Inc., *
 * 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA                 *
 *                                                                         *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

package JFlex.gui;

import java.awt.*;
import java.util.*;

/**
 * Grid layout manager like GridLayout but with predefinable
 * grid size.
 *
 * @author Gerwin Klein
 * @version $Revision: 1.4.3 $, $Date: 2009/12/21 15:58:48 $
 */
public class GridPanel extends Panel implements Handles {

  private int cols;
  private int rows;

  private int hgap;
  private int vgap;
 
  private Vector constraints = new Vector();
  private Insets insets = new Insets(0,0,0,0);

  public GridPanel(int cols, int rows) {
    this(cols, rows, 0, 0);
  }

  public GridPanel(int cols, int rows, int hgap, int vgap) {
    this.cols = cols;
    this.rows = rows;
    this.hgap = hgap;
    this.vgap = vgap;
  }

  public void doLayout() {
    Dimension size = getSize();
    size.height -= insets.top+insets.bottom;
    size.width  -= insets.left+insets.right;

    float cellWidth  = size.width/cols;
    float cellHeight = size.height/rows;

    for (int i = 0; i < constraints.size(); i++) {
      GridPanelConstraint c = (GridPanelConstraint) constraints.elementAt(i);

      float x = cellWidth * c.x + insets.left + hgap/2;
      float y = cellHeight * c.y + insets.right + vgap/2;

      float width, height;

      if (c.handle == FILL) {
        width  = (cellWidth-hgap) * c.width;
        height = (cellHeight-vgap) * c.height;
      }
      else {
        Dimension d = c.component.getPreferredSize();
        width  = d.width;
        height = d.height;
      }

      switch (c.handle) {
      case TOP_CENTER: 
        x+= (cellWidth+width)/2; 
        break;
      case TOP_RIGHT:
        x+= cellWidth-width;
        break;
      case CENTER_LEFT:
        y+= (cellHeight+height)/2;
        break;
      case CENTER:
        x+= (cellWidth+width)/2; 
        y+= (cellHeight+height)/2;
        break;
      case CENTER_RIGHT:
        y+= (cellHeight+height)/2;
        x+= cellWidth-width;
        break;
      case BOTTOM:
        y+= cellHeight-height;
        break;
      case BOTTOM_CENTER:
        x+= (cellWidth+width)/2; 
        y+= cellHeight-height;
        break;
      case BOTTOM_RIGHT:        
        y+= cellHeight-height;
        x+= cellWidth-width;
        break;
      }

      c.component.setBounds(new Rectangle((int)x, (int)y, (int)width, (int)height));
    }
  }

  public Dimension getPreferredSize() {
    float dy = 0;
    float dx = 0;
   
    for (int i = 0; i < constraints.size(); i++) {
      GridPanelConstraint c = (GridPanelConstraint) constraints.elementAt(i);

      Dimension d = c.component.getPreferredSize();

      dx = Math.max(dx, d.width/c.width);
      dy = Math.max(dy, d.height/c.height);
    }

    dx+= hgap;
    dy+= vgap;

    dx*= cols;
    dy*= rows;

    dx+= insets.left+insets.right;
    dy+= insets.top+insets.bottom;

    return new Dimension((int)dx,(int)dy);
  }

  public void setInsets(Insets insets) {
    this.insets = insets;
  }

  public void add(int x, int y, Component c) {
    add(x,y,1,1,FILL,c);
  }

  public void add(int x, int y, int handle, Component c) {
    add(x,y,1,1,handle,c);
  }

  public void add(int x, int y, int dx, int dy, Component c) {
    add(x,y,dx,dy,FILL,c);
  }

  public void add(int x, int y, int dx, int dy, int handle, Component c) {
    super.add(c);
    constraints.addElement(new GridPanelConstraint(x,y,dx,dy,handle,c));
  }
}
