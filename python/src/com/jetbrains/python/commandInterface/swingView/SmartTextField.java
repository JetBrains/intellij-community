/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.commandInterface.swingView;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.util.Range;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Text field that has width to be changed and accepts error underline
 *
 * @author Ilya.Kazakevich
 */
@SuppressWarnings({"NonSerializableFieldInSerializableClass", "SerializableHasSerializationMethods"}) // Will never serialize it
public class SmartTextField extends JTextField {
  /**
   * Placeholder for this textbox
   */
  private StatusText myPlaceHolder;
  /**
   * Error (underline) info to display. Check {@link UnderlineInfo} class for more info
   */
  @NotNull
  private final Collection<UnderlineInfo> myUnderlineInfo = new ArrayList<UnderlineInfo>();
  private int myPreferredWidth;

  public SmartTextField() {
    setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.CONSOLE_PLAIN));
  }

  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    if (myPlaceHolder != null) {
      myPlaceHolder.paint(this, g);
    }
    synchronized (myUnderlineInfo) {
      for (final UnderlineInfo underlineInfo : myUnderlineInfo) {
        g.setColor(underlineInfo.myColor);
        // To prevent too long underlines: last char should really be last
        underline(g, underlineInfo.getFrom(), underlineInfo.getTo());
      }
    }
  }

  /**
   * Underlines certain place
   *
   * @param g    canvas
   * @param from from where (int px)
   * @param to   to where (int px)
   */
  private void underline(@NotNull final Graphics g, final int from, final int to) {
    final int verticalPosition = getHeight() - 5;
    g.drawLine(from + getColumnWidth(), verticalPosition, to + getColumnWidth(), verticalPosition);
  }


  /**
   * @return place (in px) where caret.
   */
  int getTextCaretPositionInPx() {
    return (getCaretPosition() + 1) * getColumnWidth();
  }

  void setWaterMarkPlaceHolderText(@NotNull final String watermark) {
    myPlaceHolder = new MyStatusText(this);
    myPlaceHolder.setText(watermark);
  }


  @Override
  public Dimension getPreferredSize() {
    final Dimension dimension = super.getPreferredSize();
    final int placeHolderTextLength = ((myPlaceHolder != null) ? myPlaceHolder.getText().length() : 0);
    final int columns = Math.max(Math.max(getText().length(), placeHolderTextLength), getColumns());
    final int desiredSize = columns * getColumnWidth();
    return new Dimension(Math.max(Math.max(myPreferredWidth, dimension.width), desiredSize), dimension.height);
  }

  /**
   * Display underline
   *
   * @param color color to underline
   * @param from  from (in chars)
   * @param to    (in chars)
   */
  final void underlineText(@NotNull final Color color, final int from, final int to) {
    final int columnWidth = getColumnWidth();
    synchronized (myUnderlineInfo) {
      myUnderlineInfo.add(new UnderlineInfo(from * columnWidth, to * columnWidth, color));
    }
  }

  /**
   * Removes underline
   */
  void hideUnderline() {
    synchronized (myUnderlineInfo) {
      myUnderlineInfo.clear();
    }
  }

  /**
   * Sets appropriate width in pixels
   *
   * @param width width in px
   */
  void setPreferredWidthInPx(final int width) {
    myPreferredWidth = width;
  }


  /**
   * Wrapper to display placeholder
   */
  private class MyStatusText extends StatusText {
    MyStatusText(final JComponent owner) {
      super(owner);
    }

    @Override
    protected boolean isStatusVisible() {
      return SmartTextField.this.getText().isEmpty();
    }
  }

  /**
   * Information about underline
   *
   * @author Ilya.Kazakevich
   */
  private static final class UnderlineInfo extends Range<Integer> {
    /**
     * Color to use to underline
     */
    @NotNull
    private final Color myColor;

    /**
     * @param from  underline from where (in px)
     * @param to    underline to where (in px)
     * @param color color to use to underline
     */
    UnderlineInfo(final int from, final int to, @NotNull final Color color) {
      super(from, to);
      myColor = color;
    }
  }
}
