package com.intellij.openapi.ui;

import java.awt.*;

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

    public final void paint(final Component component, final Graphics2D g) {
      myNeedsRepaint = false;
      executePaint(component, g);
    }

    public abstract void executePaint(final Component component, final Graphics2D g);

  }

}
