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

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.terminal.ui.SystemSettingsProvider;
import com.jediterm.terminal.ui.TerminalPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public class JBTerminalPanel extends TerminalPanel {
  private final EditorColorsScheme myColorScheme;

  public JBTerminalPanel(@NotNull SystemSettingsProvider settingsProvider,
                         @NotNull BackBuffer backBuffer,
                         @NotNull StyleState styleState,
                         @NotNull EditorColorsScheme scheme) {
    super(settingsProvider, backBuffer, styleState);
    myColorScheme = scheme;
    

    styleState.setDefaultStyle(new TextStyle(myColorScheme.getDefaultForeground(), myColorScheme.getDefaultBackground()));

    setSelectionColor(new TextStyle(myColorScheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR),
                                    myColorScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)));
    
    setLineSpace(myColorScheme.getConsoleLineSpacing());
  }

  protected Font createFont() {

    Font normalFont = Font.decode(getFontName());
    
    if (normalFont == null) {
      normalFont = super.createFont();
    }

    normalFont = normalFont.deriveFont((float)myColorScheme.getConsoleFontSize());

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

  @Override
  protected BufferedImage createBufferedImage(int width, int height) {
    return UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
  }

  public String getFontName() {
    List<String> fonts = myColorScheme.getConsoleFontPreferences().getEffectiveFontFamilies();

    for (String font : fonts) {
      if (isApplicable(font)) {
        return font;
      }
    }
    return "Monospaced-14";
  }

  private static boolean isApplicable(String font) {
    if ("Source Code Pro".equals(font)) {
      return false;
    }
    return true;
  }
}

