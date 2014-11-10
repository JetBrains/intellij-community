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
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

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
   * error (underline) info in format "lastOnly => Color" where "lastOnly" is to underline last letter only (not the whole line)
   * Null if nothing should be displayed.
   */
  @Nullable
  private Pair<Boolean, Color> myUnderlineInfo;
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
    if (myUnderlineInfo != null) {
      final int lineStart = (myUnderlineInfo.first ? getTextEndPosition() : getColumnWidth());
      final int lineEnd = getTextEndPosition() + (myUnderlineInfo.first ? getColumnWidth() : 0);
      g.setColor(myUnderlineInfo.second);
      final int verticalPosition = getHeight() - 5;
      g.drawLine(lineStart, verticalPosition, lineEnd, verticalPosition);
    }
  }

  /**
   * @return place (in px) where entered text ends.
   */
  int getTextEndPosition() {
    return (getText().length() + 1) * getColumnWidth();
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
   * @param lastOnly last letter only (whole line otherwise)
   */
  void underlineText(@NotNull final Color color, final boolean lastOnly) {
    myUnderlineInfo = new Pair<Boolean, Color>(lastOnly, color);
  }

  /**
   * Removes underline
   */
  void hideUnderline() {
    myUnderlineInfo = null;
  }

  /**
   * Sets appropriate width in chars
   * @param widthInChars num of chars
   */
  void setPreferredWidthInChars(final int widthInChars) {
    setColumns(widthInChars);
  }

  /**
   * Sets appropriate width in pixels
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
}
