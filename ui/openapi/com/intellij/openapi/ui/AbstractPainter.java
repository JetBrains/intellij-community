package com.intellij.openapi.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.HashSet;

public abstract class AbstractPainter implements Painter {

  private boolean myNeedsRepaint;

  private Set<Listener> myListeners = new HashSet<Listener>();


  public boolean needsRepaint() {
    return myNeedsRepaint;
  }

  public void setNeedsRepaint(final boolean needsRepaint) {
    setNeedsRepaint(needsRepaint, null);
  }

  public void setNeedsRepaint(final boolean needsRepaint, @Nullable JComponent dirtyComponent) {
    myNeedsRepaint = needsRepaint;
    if (myNeedsRepaint) {
      fireNeedsRepaint(dirtyComponent);
    }
  }

  public void addListener(final Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(final Listener listener) {
    myListeners.remove(listener);
  }

  @Nullable
  public <T> T setNeedsRepaint(T oldValue, T newValue) {
    if (!myNeedsRepaint) {
      if (oldValue != null) {
        setNeedsRepaint(!oldValue.equals(newValue));
      } else if (newValue != null) {
        setNeedsRepaint(!newValue.equals(oldValue));
      } else {
        setNeedsRepaint(false);
      }
    }

    return newValue;
  }

  @Nullable
  public <T> T setNeedsRepaint(T oldValue, T newValue, JComponent dirtyComponent) {
    if (!myNeedsRepaint) {
      if (oldValue != null) {
        setNeedsRepaint(!oldValue.equals(newValue));
      } else if (newValue != null) {
        setNeedsRepaint(!newValue.equals(oldValue));
      } else {
        setNeedsRepaint(false);
      }
    }

    return newValue;
  }

  protected void fireNeedsRepaint(JComponent dirtyComponent) {
    for (Listener each : myListeners) {
      each.onNeedsRepaint(this, dirtyComponent);
    }
  }

  public final void paint(final Component component, final Graphics2D g) {
    myNeedsRepaint = false;
    executePaint(component, g);
  }

  public abstract void executePaint(final Component component, final Graphics2D g);

}
