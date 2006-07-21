/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 10, 2002
 * Time: 10:14:59 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScrollingModelImpl implements ScrollingModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.ScrollingModelImpl");

  private EditorImpl myEditor;
  private CopyOnWriteArrayList<VisibleAreaListener> myVisibleAreaListeners = new CopyOnWriteArrayList<VisibleAreaListener>();

  private AnimatedScrollingRunnable myCurrentAnimatedRunnable = null;
  private final Object myAnimatedLock = new Object();
  private boolean myAnimationDisabled = false;
  private DocumentAdapter myDocumentListener;
  @NonNls private static final String ANIMATED_SCROLLING_THREAD_NAME = "AnimatedScrollingThread";

  public ScrollingModelImpl(EditorImpl editor) {
    myEditor = editor;

    myEditor.getScrollPane().getViewport().addChangeListener(new ChangeListener() {
      private Rectangle myLastViewRect;

      public void stateChanged(ChangeEvent event) {
        Rectangle viewRect = getVisibleArea();
        VisibleAreaEvent visibleAreaEvent = new VisibleAreaEvent(myEditor, myLastViewRect, viewRect);
        myLastViewRect = viewRect;
        for (VisibleAreaListener listener : myVisibleAreaListeners) {
          listener.visibleAreaChanged(visibleAreaEvent);
        }
      }
    });

    myDocumentListener = new DocumentAdapter() {
      public void beforeDocumentChange(DocumentEvent e) {
        cancelAnimatedScrolling(true);
      }
    };
    myEditor.getDocument().addDocumentListener(myDocumentListener);
  }

  public Rectangle getVisibleArea() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) {
      return new Rectangle(0, 0, 0, 0);
    }
    return myEditor.getScrollPane().getViewport().getViewRect();
  }

  public Rectangle getVisibleAreaOnScrollingFinished() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myCurrentAnimatedRunnable != null) {
      return myCurrentAnimatedRunnable.getTargetVisibleArea();
    }
    else {
      return getVisibleArea();
    }
  }

  public void scrollToCaret(ScrollType scrollType) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();
    myEditor.validateSize();
    scrollTo(caretPosition, scrollType);
  }

  public void scrollTo(LogicalPosition pos, ScrollType scrollType) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) return;

    AnimatedScrollingRunnable canceledThread = cancelAnimatedScrolling(false);
    Rectangle viewRect = canceledThread != null ? canceledThread.getTargetVisibleArea() : getVisibleArea();

    Point p = calcOffsetsToScroll(pos, scrollType, viewRect);
    scrollToOffsets(p.x, p.y);
  }

  public void runActionOnScrollingFinished(Runnable action) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    synchronized (myAnimatedLock) {
      if (myCurrentAnimatedRunnable != null) {
        myCurrentAnimatedRunnable.addPostRunnable(action);
        return;
      }
    }

    action.run();
  }

  public void disableAnimation() {
    myAnimationDisabled = true;
  }

  public void enableAnimation() {
    myAnimationDisabled = false;
  }

  private Point calcOffsetsToScroll(LogicalPosition pos, ScrollType scrollType, Rectangle viewRect) {
    Point targetLocation = myEditor.logicalPositionToXY(pos);

    if (myEditor.getSettings().isRefrainFromScrolling() && viewRect.contains(targetLocation)) {
      if (scrollType == ScrollType.CENTER ||
          scrollType == ScrollType.CENTER_DOWN ||
          scrollType == ScrollType.CENTER_UP) {
        scrollType = ScrollType.RELATIVE;
      }
    }

    int spaceWidth = myEditor.getSpaceWidth(Font.PLAIN);
    int xInsets = myEditor.getSettings().getAdditionalColumnsCount() * spaceWidth;

    int hOffset = scrollType == ScrollType.CENTER ||
                  scrollType == ScrollType.CENTER_DOWN ||
                  scrollType == ScrollType.CENTER_UP ? 0 : viewRect.x;
    if (targetLocation.x < hOffset) {
      hOffset = targetLocation.x - 4 * spaceWidth;
      hOffset = hOffset > 0 ? hOffset : 0;
    }
    else if (targetLocation.x > viewRect.x + viewRect.width - xInsets) {
      hOffset = targetLocation.x - viewRect.width + xInsets;
    }

    int scrollUpBy = viewRect.y + myEditor.getLineHeight() - targetLocation.y;
    int scrollDownBy = targetLocation.y - (viewRect.y + viewRect.height - 2 * myEditor.getLineHeight());
    int centerPosition = targetLocation.y - viewRect.height / 3;

    int vOffset = viewRect.y;
    if (scrollType == ScrollType.CENTER) {
      vOffset = centerPosition;
    }
    else if (scrollType == ScrollType.CENTER_UP) {
      if (scrollUpBy > 0 || scrollDownBy > 0 || vOffset > centerPosition) {
        vOffset = centerPosition;
      }
    }
    else if (scrollType == ScrollType.CENTER_DOWN) {
      if (scrollUpBy > 0 || scrollDownBy > 0 || vOffset < centerPosition) {
        vOffset = centerPosition;
      }
    }
    else if (scrollType == ScrollType.RELATIVE) {
      if (scrollUpBy > 0) {
        vOffset = viewRect.y - scrollUpBy;
      }
      else if (scrollDownBy > 0) {
        vOffset = viewRect.y + scrollDownBy;
      }
    }
    else if (scrollType == ScrollType.MAKE_VISIBLE) {
      if (scrollUpBy > 0 || scrollDownBy > 0) {
        vOffset = centerPosition;
      }
    }

    JScrollPane scrollPane = myEditor.getScrollPane();
    hOffset = Math.max(0, hOffset);
    vOffset = Math.max(0, vOffset);
    hOffset = Math.min(scrollPane.getHorizontalScrollBar().getMaximum(), hOffset);
    vOffset = Math.min(scrollPane.getVerticalScrollBar().getMaximum(), vOffset);

    return new Point(hOffset, vOffset);
  }

  public int getVerticalScrollOffset() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) return 0;

    JScrollBar scrollbar = myEditor.getScrollPane().getVerticalScrollBar();
    return scrollbar.getValue();
  }

  public int getHorizontalScrollOffset() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) return 0;

    JScrollBar scrollbar = myEditor.getScrollPane().getHorizontalScrollBar();
    return scrollbar.getValue();
  }

  public void scrollVertically(int scrollOffset) {
    scrollToOffsets(getHorizontalScrollOffset(), scrollOffset);
  }

  private void _scrollVertically(int scrollOffset) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) return;

    myEditor.validateSize();
    JScrollBar scrollbar = myEditor.getScrollPane().getVerticalScrollBar();

    if (scrollbar.getVisibleAmount() < Math.abs(scrollOffset - scrollbar.getValue()) + 50) {
      myEditor.stopOptimizedScrolling();
    }

    scrollbar.setValue(scrollOffset);

    //System.out.println("scrolled vertically to: " + scrollOffset);
  }

  public void scrollHorizontally(int scrollOffset) {
    scrollToOffsets(scrollOffset, getVerticalScrollOffset());
  }

  private void _scrollHorizontally(int scrollOffset) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myEditor.getScrollPane() == null) return;

    myEditor.validateSize();
    JScrollBar scrollbar = myEditor.getScrollPane().getHorizontalScrollBar();
    scrollbar.setValue(scrollOffset);
  }

  private void scrollToOffsets(int hOffset, int vOffset) {
    cancelAnimatedScrolling(false);

    VisibleEditorsTracker editorsTracker = VisibleEditorsTracker.getInstance();
    boolean useAnimation;
    //System.out.println("myCurrentCommandStart - myLastCommandFinish = " + (myCurrentCommandStart - myLastCommandFinish));
    if (!myEditor.getSettings().isAnimatedScrolling() || myAnimationDisabled) {
      useAnimation = false;
    }
    else if (CommandProcessor.getInstance().getCurrentCommand() == null) {
      useAnimation = myEditor.getComponent().isShowing();
    }
    else if (editorsTracker.getCurrentCommandStart() - editorsTracker.getLastCommandFinish() <
             AnimatedScrollingRunnable.SCROLL_DURATION) {
      useAnimation = false;
    }
    else {
      useAnimation = editorsTracker.wasEditorVisibleOnCommandStart(myEditor);
    }

    if (useAnimation) {
      //System.out.println("scrollToAnimated: " + endVOffset);

      int startHOffset = getHorizontalScrollOffset();
      int startVOffset = getVerticalScrollOffset();

      if (startHOffset == hOffset && startVOffset == vOffset) {
        return;
      }

      //System.out.println("startVOffset = " + startVOffset);

      try {
        myCurrentAnimatedRunnable = new AnimatedScrollingRunnable(startHOffset, startVOffset, hOffset, vOffset);
        new Thread(myCurrentAnimatedRunnable, ANIMATED_SCROLLING_THREAD_NAME).start();
      }
      catch (NoAnimationRequiredException e) {
        _scrollHorizontally(hOffset);
        _scrollVertically(vOffset);
      }
    }
    else {
      _scrollHorizontally(hOffset);
      _scrollVertically(vOffset);
    }
  }

  public void addVisibleAreaListener(VisibleAreaListener listener) {
    myVisibleAreaListeners.add(listener);
  }

  public void removeVisibleAreaListener(VisibleAreaListener listener) {
    boolean success = myVisibleAreaListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public void commandStarted() {
    cancelAnimatedScrolling(true);
  }

  private AnimatedScrollingRunnable cancelAnimatedScrolling(boolean scrollToTarget) {
    synchronized (myAnimatedLock) {
      AnimatedScrollingRunnable thread = myCurrentAnimatedRunnable;
      myCurrentAnimatedRunnable = null;
      if (thread != null) {
        thread.cancel(scrollToTarget);
      }
      return thread;
    }
  }

  public void dispose() {
    myEditor.getDocument().removeDocumentListener(myDocumentListener);
  }

  public void beforeModalityStateChanged() {
    cancelAnimatedScrolling(true);
  }

  private class AnimatedScrollingRunnable implements Runnable {
    private static final int SCROLL_DURATION = 150;
    private static final int SCROLL_INTERVAL = 10;

    private final int myStartHOffset;
    private final int myStartVOffset;
    private final int myEndHOffset;
    private final int myEndVOffset;
    private final int myAnimationDuration;

    private ArrayList<Runnable> myPostRunnables = new ArrayList<Runnable>();

    private final Runnable myStartCommand;
    private final int myHDist;
    private final int myVDist;
    private final int myMaxDistToScroll;
    private final double myTotalDist;
    private final double myScrollDist;

    private boolean myCanceled = false;
    private final int myStepCount;
    private final double myPow;
    private final ModalityState myModalityState;

    public AnimatedScrollingRunnable(int startHOffset,
                                     int startVOffset,
                                     int endHOffset,
                                     int endVOffset) throws NoAnimationRequiredException {
      myStartHOffset = startHOffset;
      myStartVOffset = startVOffset;
      myEndHOffset = endHOffset;
      myEndVOffset = endVOffset;

      myHDist = Math.abs(myEndHOffset - myStartHOffset);
      myVDist = Math.abs(myEndVOffset - myStartVOffset);

      myMaxDistToScroll = myEditor.getLineHeight() * 50;
      myTotalDist = Math.sqrt((double)myHDist * myHDist + (double)myVDist * myVDist);
      myScrollDist = Math.min(myTotalDist, myMaxDistToScroll);
      myAnimationDuration = calcAnimationDuration();
      if (myAnimationDuration < SCROLL_INTERVAL * 2) {
        throw new NoAnimationRequiredException();
      }
      myStepCount = myAnimationDuration / SCROLL_INTERVAL - 1;
      double firstStepTime = 1.0 / myStepCount;
      double firstScrollDist = 5.0;
      if (myTotalDist > myScrollDist) {
        firstScrollDist *= myTotalDist / myScrollDist;
        firstScrollDist = Math.min(firstScrollDist, myEditor.getLineHeight() * 5);
      }
      myPow = myScrollDist > 0 ? setupPow(firstStepTime, firstScrollDist / myScrollDist) : 1;

      myStartCommand = CommandProcessor.getInstance().getCurrentCommand();
      myModalityState = ModalityState.current();
    }

    public Rectangle getTargetVisibleArea() {
      Rectangle viewRect = getVisibleArea();
      return new Rectangle(myEndHOffset, myEndVOffset, viewRect.width, viewRect.height);
    }

    public Runnable getStartCommand() {
      return myStartCommand;
    }

    public void run() {
      long startTime = System.currentTimeMillis();
      ScrollLoop:
        for (int i = 0; i < myStepCount; i++) {
          synchronized (myAnimatedLock) {
            if (myCanceled) return;

            double time = (i + 1) / (double)myStepCount;
            double fraction = timeToFraction(time);
            final int hOffset = (int)(myStartHOffset + (myEndHOffset - myStartHOffset) * fraction + 0.5);
            final int vOffset = (int)(myStartVOffset + (myEndVOffset - myStartVOffset) * fraction + 0.5);

            ApplicationManager.getApplication().invokeLater(new Runnable() {
                      public void run() {
                        if (myCanceled) return;
                        _scrollHorizontally(hOffset);
                        _scrollVertically(vOffset);
                      }
                    }, myModalityState);
          }

          long delay;
          while (true) {
            long nextStopTime = startTime + i * SCROLL_INTERVAL;
            long currentTime = System.currentTimeMillis();
            delay = nextStopTime - currentTime;
            //System.out.println("delay = " + delay);
            if (delay >= 0) break;
            if (++i == myStepCount) break ScrollLoop;
          }

          try {
            Thread.sleep(delay);
          }
          catch (InterruptedException e) {
          }
        }

      synchronized (myAnimatedLock) {
        if (myCanceled) return;
        finish(true, true);
      }
    }

    public void cancel(boolean scrollToTarget) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      synchronized (myAnimatedLock) {
        myCanceled = true;
        finish(false, scrollToTarget);
      }
    }

    public void addPostRunnable(Runnable runnable) {
      myPostRunnables.add(runnable);
    }

    private void finish(boolean needInvokeLater, boolean scrollToTarget) {
      if (scrollToTarget || myPostRunnables.size() > 0) {
        Runnable runnable = new Runnable() {
          public void run() {
            _scrollHorizontally(myEndHOffset);
            _scrollVertically(myEndVOffset);

            for (int i = 0; i < myPostRunnables.size(); i++) {
              Runnable runnable = myPostRunnables.get(i);
              runnable.run();
            }
          }
        };

        if (needInvokeLater) {
          ApplicationManager.getApplication().invokeLater(runnable, myModalityState);
        }
        else {
          runnable.run();
        }
      }


      if (myCurrentAnimatedRunnable == this) {
        myCurrentAnimatedRunnable = null;
      }
    }

    private double timeToFraction(double time) {
      if (time > 0.5) {
        return 1 - timeToFraction(1 - time);
      }

      double fraction = Math.pow(time * 2, myPow) / 2;

      if (myTotalDist > myMaxDistToScroll) {
        fraction *= (double)myMaxDistToScroll / myTotalDist;
      }

      return fraction;
    }

    private double setupPow(double inTime, double moveBy) {
      double pow = Math.log(2 * moveBy) / Math.log(2 * inTime);
      if (pow < 1) pow = 1;
      return pow;
    }

    private int calcAnimationDuration() {
      int lineHeight = myEditor.getLineHeight();
      double lineDist = myTotalDist / lineHeight;
      double part = (lineDist - 1) / 10;
      if (part > 1) part = 1;
      int duration = (int)(part * SCROLL_DURATION);
      //System.out.println("duration = " + duration);
      return duration;
    }
  }

  private static class NoAnimationRequiredException extends Exception {
  }
}
