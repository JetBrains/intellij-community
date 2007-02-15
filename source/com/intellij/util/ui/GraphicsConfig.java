package com.intellij.util.ui;

import java.awt.*;
import java.util.Map;

public class GraphicsConfig {

  private Graphics2D myG;
  private Map myHints;

  public GraphicsConfig(Graphics g) {
    myG = (Graphics2D)g;
    myHints = (Map)myG.getRenderingHints().clone();
  }

  public GraphicsConfig setAntialiasing(boolean on) {
    myG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, on ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
    return this;
  }

  public void restore() {
    myG.setRenderingHints(myHints);
  }
}
