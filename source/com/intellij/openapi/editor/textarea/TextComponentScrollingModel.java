package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.VisibleAreaListener;

import javax.swing.text.JTextComponent;
import javax.swing.text.BadLocationException;
import java.awt.*;

/**
 * @author yole
 */
public class TextComponentScrollingModel implements ScrollingModel {
  private JTextComponent myTextComponent;

  public TextComponentScrollingModel(final JTextComponent textComponent) {
    myTextComponent = textComponent;
  }

  public Rectangle getVisibleArea() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public Rectangle getVisibleAreaOnScrollingFinished() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void scrollToCaret(final ScrollType scrollType) {
    final int position = myTextComponent.getCaretPosition();
    try {
      final Rectangle rectangle = myTextComponent.modelToView(position);
      myTextComponent.scrollRectToVisible(rectangle);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  public void scrollTo(final LogicalPosition pos, final ScrollType scrollType) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void runActionOnScrollingFinished(final Runnable action) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void disableAnimation() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void enableAnimation() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public int getVerticalScrollOffset() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public int getHorizontalScrollOffset() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void scrollVertically(final int scrollOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void scrollHorizontally(final int scrollOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void addVisibleAreaListener(final VisibleAreaListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeVisibleAreaListener(final VisibleAreaListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }
}