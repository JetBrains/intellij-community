package org.jetbrains.debugger.memory.view;

import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.component.CreationPositionTracker;
import org.jetbrains.debugger.memory.utils.StackFrameDescriptor;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

class InstancesWithStackFrameView {
  private static final float DEFAULT_SPLITTER_PROPORTION = 0.7f;
  private static final List<StackFrameDescriptor> EMPTY_FRAME = Collections.emptyList();

  private float myHidedProportion;

  private final JBSplitter mySplitter = new JBSplitter(false, DEFAULT_SPLITTER_PROPORTION);
  private boolean myIsHided = false;

  InstancesWithStackFrameView(@NotNull XDebugSession debugSession, @NotNull InstancesTree tree,
                              @NotNull StackFrameList list) {
    mySplitter.setFirstComponent(new JBScrollPane(tree));
    mySplitter.setSecondComponent(new JBScrollPane(list));
    mySplitter.setHonorComponentsMinimumSize(false);
    myHidedProportion = DEFAULT_SPLITTER_PROPORTION;

    hideStackFrame();

    CreationPositionTracker tracker = CreationPositionTracker.getInstance(debugSession.getProject());
    tree.addTreeSelectionListener(e -> {
      ObjectReference ref = tree.getSelectedReference();
      if (ref != null && tracker != null) {
        List<StackFrameDescriptor> stack = tracker.getStack(debugSession, ref);
        if (stack != null) {
          list.setFrame(stack);
          showStackFrame();
          return;
        }
      }

      list.setFrame(EMPTY_FRAME);
      hideStackFrame();
    });
  }

  JComponent getComponent() {
    return mySplitter;
  }

  private void hideStackFrame() {
    if (!myIsHided) {
      myHidedProportion = mySplitter.getProportion();
      mySplitter.getSecondComponent().setVisible(false);
      mySplitter.setProportion(1.f);
      myIsHided = true;
    }
  }

  private void showStackFrame() {
    if (myIsHided) {
      mySplitter.getSecondComponent().setVisible(true);
      mySplitter.setProportion(myHidedProportion);
      myIsHided = false;
    }
  }
}
