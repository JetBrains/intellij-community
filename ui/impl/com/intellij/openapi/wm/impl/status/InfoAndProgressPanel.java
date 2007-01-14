package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.wm.ex.ProcessInfo;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class InfoAndProgressPanel extends JPanel {

  StatusBarImpl myStatusBar;
  private ProcessPopup myPopup;

  private ArrayList<ProgressIndicatorEx> myOriginals = new ArrayList<ProgressIndicatorEx>();
  private ArrayList<ProcessInfo> myInfos = new ArrayList<ProcessInfo>();
  private Map<InlineProgressIndicator, ProgressIndicatorEx> myInline2Original = new HashMap<InlineProgressIndicator, ProgressIndicatorEx>();
  private MultiValuesMap<ProgressIndicatorEx, InlineProgressIndicator> myOriginal2Inlines =
    new MultiValuesMap<ProgressIndicatorEx, InlineProgressIndicator>();

  private MergingUpdateQueue myUpdateQueue;
  private AsyncProcessIcon myProgressIcon;

  public InfoAndProgressPanel(final StatusBarImpl statusBar) {
    myStatusBar = statusBar;
    setOpaque(false);

    myProgressIcon = new AsyncProcessIcon("Background process");
    myProgressIcon.setOpaque(true);
    myProgressIcon.setToolTipText("View " + ProcessPopup.BACKGROUND_PROCESSES);
    new BaseButtonBehavior(myProgressIcon) {
      protected void execute() {
        triggerPopupShowing();
      }
    };
    myProgressIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    myUpdateQueue = new MergingUpdateQueue("Progress indicator", 250, true, null);
    myPopup = new ProcessPopup(this);

    restoreEmptyStatus();
  }

  private Border createSeparatorBorder() {
    return BorderFactory.createCompoundBorder(new StatusBarImpl.SeparatorBorder.Left(), new EmptyBorder(0, 2, 0, 2));
  }

  private Border createInsetBorder() {
    return new EmptyBorder(0, 3, 0, 2);
  }

  public void addProgress(final ProgressIndicatorEx original, ProcessInfo info) {
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

  public void removeProgress(InlineProgressIndicator progress) {
    if (!myInline2Original.containsKey(progress)) return;

    final boolean last = myOriginals.size() == 1;
    final boolean beforeLast = myOriginals.size() == 2;

    removeFromMaps(progress);

    myPopup.removeIndicator(progress);

    if (last) {
      restoreEmptyStatus();
    }
    else {
      if (myPopup.isShowing()) {
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

  private void removeFromMaps(final InlineProgressIndicator progress) {
    final ProgressIndicatorEx original = myInline2Original.get(progress);

    myInline2Original.remove(progress);

    myOriginal2Inlines.remove(original, progress);
    if (myOriginal2Inlines.get(original) == null) {
      final int originalIndex = myOriginals.indexOf(original);
      myOriginals.remove(originalIndex);
      myInfos.remove(originalIndex);
    }
  }

  private void openProcessPopup() {
    if (myPopup.isShowing()) return;
    if (myOriginals.size() > 0) {
      buildInProcessCount();
    } else {
      restoreEmptyStatus();
    }
    myPopup.show();
  }

  void hideProcessPopup() {
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
    labelComp.setBorder(BorderFactory.createCompoundBorder(createSeparatorBorder(), new EmptyBorder(0, 3, 0, 0)));

    progressCountPanel.add(labelComp, BorderLayout.CENTER);

    myProgressIcon.setBorder(createInsetBorder());
    progressCountPanel.add(myProgressIcon, BorderLayout.EAST);

    add(myStatusBar.myInfoPanel, BorderLayout.CENTER);
    add(progressCountPanel, BorderLayout.EAST);

    revalidate();
    repaint();
  }

  private void buildInInlineIndicator(final InlineProgressIndicator inline) {
    removeAll();
    setLayout(new GridLayout(1, 2));
    add(myStatusBar.myInfoPanel);

    final JPanel inlinePanel = new JPanel(new BorderLayout());

    inline.getComponent().setBorder(createSeparatorBorder());
    inlinePanel.add(inline.getComponent(), BorderLayout.CENTER);

    myProgressIcon.setBorder(createSeparatorBorder());
    inlinePanel.add(myProgressIcon, BorderLayout.EAST);

    add(inlinePanel);

    myStatusBar.myInfoPanel.revalidate();
    myStatusBar.myInfoPanel.repaint();
  }

  private InlineProgressIndicator createInlineDelegate(final ProcessInfo info, final ProgressIndicatorEx original, final boolean compact) {
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
    myProgressIcon.setBorder(createSeparatorBorder());
    add(myProgressIcon, BorderLayout.EAST);
    myProgressIcon.suspend();
    myStatusBar.myInfoPanel.revalidate();
    myStatusBar.myInfoPanel.repaint();
  }

  private class MyInlineProgressIndicator extends InlineProgressIndicator {
    private final ProgressIndicatorEx myOriginal;

    public MyInlineProgressIndicator(final boolean compact, final ProcessInfo info, final ProgressIndicatorEx original) {
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
      removeProgress(this);
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
