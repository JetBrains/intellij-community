package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.StatusBarInformer;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.idea.ActionsBundle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class InfoAndProgressPanel extends JPanel {

  StatusBarImpl myStatusBar;
  private ProcessPopup myPopup;

  private final ArrayList<ProgressIndicatorEx> myOriginals = new ArrayList<ProgressIndicatorEx>();
  private ArrayList<TaskInfo> myInfos = new ArrayList<TaskInfo>();
  private Map<InlineProgressIndicator, ProgressIndicatorEx> myInline2Original = new HashMap<InlineProgressIndicator, ProgressIndicatorEx>();
  private MultiValuesMap<ProgressIndicatorEx, InlineProgressIndicator> myOriginal2Inlines =
    new MultiValuesMap<ProgressIndicatorEx, InlineProgressIndicator>();

  private MergingUpdateQueue myUpdateQueue;
  private AsyncProcessIcon myProgressIcon;

  private boolean myShouldClosePopupAndOnProcessFinish;
  private final EmptyBorder myEmptyBorder;
  private final CompoundBorder myCompoundBorder;

  public InfoAndProgressPanel(final StatusBarImpl statusBar) {
    myStatusBar = statusBar;
    setOpaque(false);

    myEmptyBorder = new EmptyBorder(0, 3, 0, 2);
    myCompoundBorder = BorderFactory.createCompoundBorder(new StatusBarImpl.SeparatorBorder.Left(), new EmptyBorder(0, 2, 0, 2));

    myProgressIcon = new AsyncProcessIcon("Background process");
    myProgressIcon.setOpaque(true);
    myProgressIcon.setToolTipText(ActionsBundle.message("action.ShowProcessWindow.text"));
    new BaseButtonBehavior(myProgressIcon) {
      protected void execute() {
        triggerPopupShowing();
      }
    };
    myProgressIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    new StatusBarInformer(myProgressIcon, ActionsBundle.message("action.ShowProcessWindow.double.click"), myStatusBar);

    myUpdateQueue = new MergingUpdateQueue("Progress indicator", 250, true, null);
    myPopup = new ProcessPopup(this);

    restoreEmptyStatus();
  }

  public void addProgress(final ProgressIndicatorEx original, TaskInfo info) {
    synchronized (myOriginals) {
      final boolean veryFirst = myOriginals.size() == 0;

      myOriginals.add(original);
      myInfos.add(info);

      final InlineProgressIndicator expanded = createInlineDelegate(info, original, false);
      final InlineProgressIndicator compact = createInlineDelegate(info, original, true);

      myPopup.addIndicator(expanded);
      myProgressIcon.resume();

      if (veryFirst && !myPopup.isShowing()) {
        buildInInlineIndicator(compact);
      }
      else {
        buildInProcessCount();
      }
    }
  }

  public void removeProgress(InlineProgressIndicator progress) {
    synchronized (myOriginals) {
      if (!myInline2Original.containsKey(progress)) return;

      final boolean last = myOriginals.size() == 1;
      final boolean beforeLast = myOriginals.size() == 2;

      myPopup.removeIndicator(progress);

      final ProgressIndicatorEx original = removeFromMaps(progress);
      if (myOriginals.contains(original)) return;

      if (last) {
        restoreEmptyStatus();
        if (myShouldClosePopupAndOnProcessFinish) {
          hideProcessPopup();
        }
      }
      else {
        if (myPopup.isShowing() || myOriginals.size() > 1) {
          buildInProcessCount();
        }
        else if (beforeLast) {
          buildInInlineIndicator(createInlineDelegate(myInfos.get(0), myOriginals.get(0), true));
        }
        else {
          restoreEmptyStatus();
        }
      }
    }
  }

  private ProgressIndicatorEx removeFromMaps(final InlineProgressIndicator progress) {
    final ProgressIndicatorEx original = myInline2Original.get(progress);

    myInline2Original.remove(progress);

    myOriginal2Inlines.remove(original, progress);
    if (myOriginal2Inlines.get(original) == null) {
      final int originalIndex = myOriginals.indexOf(original);
      myOriginals.remove(originalIndex);
      myInfos.remove(originalIndex);
    }

    return original;
  }

  private void openProcessPopup() {
    synchronized(myOriginals) {
      if (myPopup.isShowing()) return;
      if (myOriginals.size() > 0) {
        myShouldClosePopupAndOnProcessFinish = true;
        buildInProcessCount();
      } else {
        myShouldClosePopupAndOnProcessFinish = false;
        restoreEmptyStatus();
      }
      myPopup.show();
    }
  }

  void hideProcessPopup() {
    synchronized(myOriginals) {
      if (!myPopup.isShowing()) return;

      if (myOriginals.size() == 1) {
        buildInInlineIndicator(createInlineDelegate(myInfos.get(0), myOriginals.get(0), true));
      }
      else if (myOriginals.size() == 0) {
        restoreEmptyStatus();
      }
      else {
        buildInProcessCount();
      }

      myPopup.hide();
    }
  }

  private void buildInProcessCount() {
    removeAll();
    setLayout(new BorderLayout());

    final JPanel progressCountPanel = new JPanel(new BorderLayout(0, 2));
    String processWord = myOriginals.size() == 1 ? " process" : " processes";
    final LinkLabel label = new LinkLabel(myOriginals.size() + processWord + " running...", null, new LinkListener() {
      public void linkSelected(final LinkLabel aSource, final Object aLinkData) {
        triggerPopupShowing();
      }
    });
    label.setOpaque(true);

    final Wrapper labelComp = new Wrapper(label);
    progressCountPanel.add(labelComp, BorderLayout.CENTER);

    myProgressIcon.setBorder(myCompoundBorder);
    progressCountPanel.add(myProgressIcon, BorderLayout.WEST);

    add(myStatusBar.myInfoPanel, BorderLayout.CENTER);

    progressCountPanel.setBorder(new EmptyBorder(0, 0, 0, 4));
    add(progressCountPanel, BorderLayout.EAST);

    revalidate();
    repaint();
  }

  private void buildInInlineIndicator(final InlineProgressIndicator inline) {
    removeAll();
    setLayout(new InlineLayout());
    add(myStatusBar.myInfoPanel);

    final JPanel inlinePanel = new JPanel(new BorderLayout());

    inline.getComponent().setBorder(new EmptyBorder(0, 0, 0, 2));
    inlinePanel.add(inline.getComponent(), BorderLayout.CENTER);

    myProgressIcon.setBorder(myCompoundBorder);
    inlinePanel.add(myProgressIcon, BorderLayout.WEST);

    add(inlinePanel);

    myStatusBar.myInfoPanel.revalidate();
    myStatusBar.myInfoPanel.repaint();
  }

  private static class InlineLayout extends AbstractLayoutManager {

    public Dimension preferredLayoutSize(final Container parent) {
      Dimension result = new Dimension();
      for (int i = 0; i < parent.getComponentCount(); i++) {
        final Dimension prefSize = parent.getComponent(i).getPreferredSize();
        result.width += prefSize.width;
        result.height = Math.max(prefSize.height, result.height);
      }
      return result;
    }

    public void layoutContainer(final Container parent) {
      final Dimension size = parent.getSize();
      int compWidth = size.width / parent.getComponentCount();
      int eachX = 0;
      for (int i = 0; i < parent.getComponentCount(); i++) {
        final Component each = parent.getComponent(i);
        if (i == parent.getComponentCount() - 1) {
          compWidth = size.width - eachX;
        }
        each.setBounds(eachX, 0, compWidth, size.height);
        eachX += compWidth;
      }
    }
  }


  private InlineProgressIndicator createInlineDelegate(final TaskInfo info, final ProgressIndicatorEx original, final boolean compact) {
    final Collection<InlineProgressIndicator> inlines = myOriginal2Inlines.get(original);
    if (inlines != null) {
      for (InlineProgressIndicator eachInline : inlines) {
        if (eachInline.isCompact() == compact) return eachInline;
      }
    }

    InlineProgressIndicator inline = new MyInlineProgressIndicator(compact, info, original);

    myInline2Original.put(inline, original);
    myOriginal2Inlines.put(original, inline);

    if (compact) {
      new BaseButtonBehavior(inline.getComponent()) {
        protected void execute() {
          triggerPopupShowing();
        }
      };
    }

    return inline;
  }

  private void triggerPopupShowing() {
    if (myPopup.isShowing()) {
      hideProcessPopup();
    }
    else {
      openProcessPopup();
    }
  }

  private void restoreEmptyStatus() {
    removeAll();
    setLayout(new BorderLayout());
    add(myStatusBar.myInfoPanel, BorderLayout.CENTER);
    myProgressIcon.setBorder(myCompoundBorder);
    add(myProgressIcon, BorderLayout.EAST);
    myProgressIcon.suspend();
    myStatusBar.myInfoPanel.revalidate();
    myStatusBar.myInfoPanel.repaint();
  }

  public boolean isProcessWindowOpen() {
    return myPopup.isShowing();
  }

  public void setProcessWindowOpen(final boolean open) {
    if (open) {
      openProcessPopup();
    } else {
      hideProcessPopup();
    }
  }

  private class MyInlineProgressIndicator extends InlineProgressIndicator {
    private final ProgressIndicatorEx myOriginal;

    public MyInlineProgressIndicator(final boolean compact, final TaskInfo info, final ProgressIndicatorEx original) {
      super(compact, info);
      myOriginal = original;
      original.addStateDelegate(this);
    }

    public void cancel() {
      super.cancel();
      queueRunningUpdate(new Runnable() {
        public void run() {
          removeProgress(MyInlineProgressIndicator.this);
        }
      });
    }

    protected void cancelRequest() {
      myOriginal.cancel();
    }

    public void stop() {
      super.stop();
      queueRunningUpdate(new Runnable() {
        public void run() {
          removeProgress(MyInlineProgressIndicator.this);
        }
      });
    }

    protected void queueProgressUpdate(final Runnable update) {
      myUpdateQueue.queue(new Update(MyInlineProgressIndicator.this, false, 1) {
        public void run() {
          ApplicationManager.getApplication().invokeLater(update);
        }
      });
    }

    protected void queueRunningUpdate(final Runnable update) {
      myUpdateQueue.queue(new Update(new Object(), false, 0) {
        public void run() {
          ApplicationManager.getApplication().invokeLater(update);
        }
      });
    }
  }
}
