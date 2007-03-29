package com.intellij.openapi.ui;

import com.intellij.util.ObjectUtils;

import java.awt.*;

import org.jetbrains.annotations.Nullable;

public interface Painter {

  boolean needsRepaint();
  void paint(Component component, final Graphics2D g);

  abstract class Abstract implements Painter {

    private boolean myNeedsRepaint;

    public boolean needsRepaint() {
      return myNeedsRepaint;
    }

    public void setNeedsRepaint(final boolean needsRepaint) {
      myNeedsRepaint = needsRepaint;
    }

    @Nullable
    public <T> T setNeedsRepaint(T oldValue, T newValue) {
      if (!myNeedsRepaint) {
        if (oldValue != null) {
          myNeedsRepaint = !oldValue.equals(newValue);
        } else if (newValue != null) {
          myNeedsRepaint = !newValue.equals(oldValue);
        } else {
          myNeedsRepaint = false;
        }
      }

      return newValue;
    }

    public final void paint(final Component component, final Graphics2D g) {
      myNeedsRepaint = false;
      executePaint(component, g);
    }

    public abstract void executePaint(final Component component, final Graphics2D g);

  }

}
