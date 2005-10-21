package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 */
public class OneSideRoundedLineBorder extends LineBorder {
  private final int myArcSize;
  private final int myRadius;
  private boolean myIsTopRounded;
  private boolean myIsBottomRounded;
  private boolean myDrawDottedAngledSide;
  private static final float[] DASH = new float[]{0, 2, 0, 2};


  public OneSideRoundedLineBorder(Color color, int arcSize) {
    this(color, arcSize, 1, true, false, false);
  }

  public OneSideRoundedLineBorder(Color color, int arcSize, int thickness, boolean isTopRounded, boolean isBottomRounded, boolean drawDottedAngledSide) {
    super(color, thickness);
    myArcSize = arcSize;
    myIsTopRounded = isTopRounded;
    myIsBottomRounded = isBottomRounded;
    myRadius = myArcSize / 2;
    myDrawDottedAngledSide = drawDottedAngledSide;
  }


  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    final Graphics2D g2 = (Graphics2D) g;

    final Object oldAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    final Color oldColor = g2.getColor();
    g2.setColor(lineColor);

    for (int idx = 0; idx < thickness; idx++) {
      final int correctedHeight = height - idx - idx - 1;
      final int correctedWidth = width - idx - idx - 1;
      final int startX = x + idx;
      final int startY = y + idx;

      if (myIsTopRounded && myIsBottomRounded) {
        g2.drawRoundRect(startX, startY, correctedWidth, correctedHeight, myArcSize, myArcSize);
      }
      else if (myIsTopRounded){
        UIUtil.drawLine(g2, startX + myRadius, startY, startX + correctedWidth - myRadius, startY); // top
        if (myDrawDottedAngledSide) {
          drawDottedLine(g2, startX, startY + correctedHeight, startX + correctedWidth, startY + correctedHeight); // bottom
        }
        else {
          UIUtil.drawLine(g2, startX, startY + correctedHeight, startX + correctedWidth, startY + correctedHeight); // bottom
        }
        g2.drawArc(startX, startY, myArcSize, myArcSize, 90, 90);
        g2.drawArc(startX + correctedWidth - myArcSize, startY, myArcSize, myArcSize, 0, 90);
        UIUtil.drawLine(g2, startX, startY + myRadius + 1, startX, startY + correctedHeight);  // left
        UIUtil.drawLine(g2, startX + correctedWidth, startY + myRadius + 1, startX + correctedWidth, startY + correctedHeight);  // right
      }
      else if (myIsBottomRounded) {
        // todo
      }
      else {
        g2.drawRect(startX, startY, correctedWidth, correctedHeight);
      }
    }

    g2.setColor(oldColor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
  }

  private void drawDottedLine(Graphics2D g, int x1, int y1, int x2, int y2) {
    final Stroke saved = g.getStroke();
    g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, DASH, y1 % 2));

    UIUtil.drawLine(g, x1, y1, x2, y2);

    g.setStroke(saved);
  }
}
