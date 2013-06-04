/* -*-mode:java; c-basic-offset:2; -*- */
/* JCTerm
 * Copyright (C) 2002-2004 ymnk, JCraft,Inc.
 *  
 * Written by: 2002 ymnk<ymnk@jcaft.com>
 *   
 *   
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 * 
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.jetbrains.plugins.terminal;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.ui.GraphicsUtil;
import com.jediterm.emulator.TextStyle;
import com.jediterm.emulator.display.BackBuffer;
import com.jediterm.emulator.display.LinesBuffer;
import com.jediterm.emulator.display.StyleState;
import com.jediterm.emulator.ui.SwingTerminalPanel;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class JBTerminalPanel extends SwingTerminalPanel {
  private final EditorColorsScheme myColorScheme;

  public JBTerminalPanel(BackBuffer backBuffer, LinesBuffer scrollBuffer, StyleState styleState, EditorColorsScheme scheme) {
    super(backBuffer, scrollBuffer, styleState);
    myColorScheme = scheme;

    styleState.setDefaultStyle(new TextStyle(myColorScheme.getDefaultForeground(), myColorScheme.getDefaultBackground()));
  }

  protected Font createFont() {
    Font normalFont = Font.decode(myColorScheme.getConsoleFontName());
    if (normalFont == null) {
      return super.createFont();
    }
    return normalFont;
  }

  protected void setupAntialiasing(Graphics graphics, boolean antialiasing) {
    GraphicsUtil.setupAntialiasing(graphics, antialiasing, false);
  }

  @Override
  public Color getBackground() {
    if (myColorScheme != null) {
      return myColorScheme.getDefaultBackground();
    }
    return super.getBackground();
  }

  @Override
  public Color getForeground() {
    if (myColorScheme != null) {
      return myColorScheme.getDefaultForeground();
    }
    return super.getBackground();
  }

  @Override
  protected void setCopyContents(StringSelection selection) {
    CopyPasteManager.getInstance().setContents(selection);
  }


  @Override
  protected String getClipboardContent() throws IOException, UnsupportedFlavorException {
    Transferable contents = CopyPasteManager.getInstance().getContents();
    if (contents == null) {
      return null;
    }
    return (String)contents.getTransferData(DataFlavor.stringFlavor);
  }
}
