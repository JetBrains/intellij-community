package com.intellij.openapi.wm.impl;

import com.intellij.util.Alarm;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class TitlePanel extends JPanel {
  private final Color BND_ENABLE_COLOR = new Color(105, 128, 180);
  private final Color BND_DISABLE_COLOR = new Color(160, 160, 160);

  private final Color CNT_ENABLE_COLOR = new Color(120, 144, 192);
  private final Color CNT_DISABLE_COLOR = new Color(184, 184, 184);

  private static final int DELAY = 5; // Delay between frames
  private static final int TOTAL_FRAME_COUNT = 7; // Total number of frames in animation sequence

  private int myCurrentFrame;

  private float myBndStartRed, myBndStartGreen, myBndStartBlue; // start boundary color for animation
  private float myBndEndRed, myBndEndGreen, myBndEndBlue; // end boundary color for animation
  private Color myBndColor; // current boundary color

  private float myCntStartRed, myCntStartGreen, myCntStartBlue; // start center color for animation
  private float myCntEndRed, myCntEndGreen, myCntEndBlue; // end center color for animation
  private Color myCntColor; // current center color

  private final Alarm myFrameTicker; // Determines moments of rendering of next frame
  private final MyAnimator myAnimator; // Renders panel's color
  private boolean myActive = true;

  TitlePanel() {
    super(new BorderLayout());
    myFrameTicker = new Alarm();
    myAnimator = new MyAnimator();
    setLayout(new BorderLayout());

    // Initial boundary color

    myBndStartRed = BND_DISABLE_COLOR.getRed();
    myBndStartGreen = BND_DISABLE_COLOR.getGreen();
    myBndStartBlue = BND_DISABLE_COLOR.getBlue();

    myBndEndRed = BND_ENABLE_COLOR.getRed();
    myBndEndGreen = BND_ENABLE_COLOR.getGreen();
    myBndEndBlue = BND_ENABLE_COLOR.getBlue();

    // Initial center color

    myCntStartRed = CNT_DISABLE_COLOR.getRed();
    myCntStartGreen = CNT_DISABLE_COLOR.getGreen();
    myCntStartBlue = CNT_DISABLE_COLOR.getBlue();

    myCntEndRed = CNT_ENABLE_COLOR.getRed();
    myCntEndGreen = CNT_ENABLE_COLOR.getGreen();
    myCntEndBlue = CNT_ENABLE_COLOR.getBlue();

    myCurrentFrame = TOTAL_FRAME_COUNT;
    updateColor();
  }

  public final void setActive(final boolean active, boolean animate) {
    if (active == myActive) {
      return;
    }
    myActive = active;
    myFrameTicker.cancelAllRequests();
    if (myCurrentFrame > 0) { // reverse rendering
      myCurrentFrame = TOTAL_FRAME_COUNT - myCurrentFrame;
    }
    if (active) {

      // Boundary color

      myBndStartRed = BND_DISABLE_COLOR.getRed();
      myBndStartGreen = BND_DISABLE_COLOR.getGreen();
      myBndStartBlue = BND_DISABLE_COLOR.getBlue();

      myBndEndRed = BND_ENABLE_COLOR.getRed();
      myBndEndGreen = BND_ENABLE_COLOR.getGreen();
      myBndEndBlue = BND_ENABLE_COLOR.getBlue();

      // Center color

      myCntStartRed = CNT_DISABLE_COLOR.getRed();
      myCntStartGreen = CNT_DISABLE_COLOR.getGreen();
      myCntStartBlue = CNT_DISABLE_COLOR.getBlue();

      myCntEndRed = CNT_ENABLE_COLOR.getRed();
      myCntEndGreen = CNT_ENABLE_COLOR.getGreen();
      myCntEndBlue = CNT_ENABLE_COLOR.getBlue();
    }
    else {

      // Boundary color

      myBndStartRed = BND_ENABLE_COLOR.getRed();
      myBndStartGreen = BND_ENABLE_COLOR.getGreen();
      myBndStartBlue = BND_ENABLE_COLOR.getBlue();

      myBndEndRed = BND_DISABLE_COLOR.getRed();
      myBndEndGreen = BND_DISABLE_COLOR.getGreen();
      myBndEndBlue = BND_DISABLE_COLOR.getBlue();

      // Center color

      myCntStartRed = CNT_ENABLE_COLOR.getRed();
      myCntStartGreen = CNT_ENABLE_COLOR.getGreen();
      myCntStartBlue = CNT_ENABLE_COLOR.getBlue();

      myCntEndRed = CNT_DISABLE_COLOR.getRed();
      myCntEndGreen = CNT_DISABLE_COLOR.getGreen();
      myCntEndBlue = CNT_DISABLE_COLOR.getBlue();
    }
    if (animate) {
      myFrameTicker.addRequest(myAnimator, DELAY);
    }
  }

  private void updateColor() {

    // Update boundary color

    final int bndRed = (int) (myBndStartRed + (float) myCurrentFrame * (myBndEndRed - myBndStartRed) / TOTAL_FRAME_COUNT);
    final int bndGreen = (int) (myBndStartGreen + (float) myCurrentFrame * (myBndEndGreen - myBndStartGreen) / TOTAL_FRAME_COUNT);
    final int bndBlue = (int) (myBndStartBlue + (float) myCurrentFrame * (myBndEndBlue - myBndStartBlue) / TOTAL_FRAME_COUNT);
    myBndColor = new Color(Math.max(0, Math.min(bndRed, 255)),
                           Math.max(0, Math.min(bndGreen, 255)),
                           Math.max(0, Math.min(bndBlue, 255)));

    // Update center color

    final int cntRed = (int) (myCntStartRed + (float) myCurrentFrame * (myCntEndRed - myCntStartRed) / TOTAL_FRAME_COUNT);
    final int cntGreen = (int) (myCntStartGreen + (float) myCurrentFrame * (myCntEndGreen - myCntStartGreen) / TOTAL_FRAME_COUNT);
    final int cntBlue = (int) (myCntStartBlue + (float) myCurrentFrame * (myCntEndBlue - myCntStartBlue) / TOTAL_FRAME_COUNT);
    myCntColor = new Color(Math.max(0, Math.min(cntRed, 255)),
                           Math.max(0, Math.min(cntGreen, 255)),
                           Math.max(0, Math.min(cntBlue, 255)));
  }

  protected final void paintComponent(final Graphics g) {
    super.paintComponent(g);
    final Graphics2D g2d = (Graphics2D) g;
    g2d.setPaint(new GradientPaint(0, 0, myBndColor, 0, getHeight() / 2, myCntColor));
    g2d.fillRect(0, 0, getWidth(), getHeight() / 2);
    g2d.setPaint(new GradientPaint(0, getHeight() / 2, myCntColor, 0, getHeight() - 1, myBndColor));
    g2d.fillRect(0, getHeight() / 2, getWidth(), getHeight() - 1);
  }

  private final class MyAnimator implements Runnable {
    public void run() {
      updateColor();
      paintImmediately(0, 0, getWidth(), getHeight());
      if (myCurrentFrame <= TOTAL_FRAME_COUNT) {
        myCurrentFrame++;
        myFrameTicker.addRequest(this, DELAY);
      }
      else {
        myFrameTicker.cancelAllRequests();
      }
    }
  }
}
