package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.util.Alarm;

import javax.swing.*;

public abstract class TestsProgressAnimator implements Runnable {
  private static final int FRAMES_COUNT = 8;
  private static final int MOVIE_TIME = 800;
  private static final int FRAME_TIME = MOVIE_TIME / FRAMES_COUNT;

  public static final Icon PAUSED_ICON = TestsUIUtil.loadIcon("testPaused");
  public static final Icon[] FRAMES = new Icon[FRAMES_COUNT];

  private long myLastInvocationTime = -1;

  private Alarm myAlarm;
  private AbstractTestProxy myCurrentTestCase;
  private AbstractTestTreeBuilder myTreeBuilder;



  static {
    for (int i = 0; i < FRAMES_COUNT; i++)
      FRAMES[i] = TestsUIUtil.loadIcon("testInProgress" + (i + 1));
  }

  public static Icon getCurrentFrame() {
    final int frameIndex = (int) ((System.currentTimeMillis() % MOVIE_TIME) / FRAME_TIME);
    return FRAMES[frameIndex];
  }

  /**
   * Initializes animator: creates alarm and sets tree builder
   * @param treeBuilder tree builder
   */
  protected void init(final AbstractTestTreeBuilder treeBuilder) {
    myAlarm = new Alarm();
    myTreeBuilder = treeBuilder;
  }

  public AbstractTestProxy getCurrentTestCase() {
    return myCurrentTestCase;
  }

  public void run() {
    if (myCurrentTestCase != null) {
      final long time = System.currentTimeMillis();
      // optimization:
      // we shouldn't repaint if this frame was painted in current interval
      if (time - myLastInvocationTime >= FRAME_TIME) {
        repaintSubTree();
        myLastInvocationTime = time;
      }
    }
    scheduleRepaint();
  }

  public void setCurrentTestCase(final AbstractTestProxy currentTestCase) {
    myCurrentTestCase = currentTestCase;
    scheduleRepaint();
  }

  public void stopMovie() {
    if (myCurrentTestCase != null)
      repaintSubTree();
    setCurrentTestCase(null);
    cancelAlarm();
  }


  public void dispose() {
    myTreeBuilder = null;
    myCurrentTestCase = null;
    cancelAlarm();
  }

  private void cancelAlarm() {
    if (myAlarm != null) {
      myAlarm.cancelAllRequests();
      myAlarm = null;
    }
  }

  private void repaintSubTree() {
    myTreeBuilder.repaintWithParents(myCurrentTestCase);
  }

  private void scheduleRepaint() {
    if (myAlarm == null) {
      return;
    }
    myAlarm.cancelAllRequests();
    if (myCurrentTestCase != null) {
      myAlarm.addRequest(this, FRAME_TIME);
    }
  }

}
