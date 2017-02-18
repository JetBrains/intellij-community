package org.intellij.plugins.xsltDebugger.impl;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import org.intellij.plugins.xsltDebugger.VMPausedException;
import org.intellij.plugins.xsltDebugger.XsltDebuggerSession;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XsltExecutionStack extends XExecutionStack {
  private final XsltStackFrame myTopFrame;
  private final XsltDebuggerSession myDebuggerSession;

  public XsltExecutionStack(String name, Debugger.Frame topFrame, XsltDebuggerSession debuggerSession) {
    super(name);
    myDebuggerSession = debuggerSession;
    myTopFrame = new XsltStackFrame(topFrame, myDebuggerSession);
  }

  @Override
  public XStackFrame getTopFrame() {
    return myTopFrame;
  }

  @Override
  public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
    try {
      if (myDebuggerSession.getCurrentState() == Debugger.State.SUSPENDED) {
        Debugger.Frame frame = myTopFrame.getFrame();
        final List<XStackFrame> frames = new ArrayList<>();
        frames.add(myTopFrame);
        while (frame != null) {
          frame = frame.getPrevious();
          if (frame != null) {
            frames.add(new XsltStackFrame(frame, myDebuggerSession));
          }
        }
        if (firstFrameIndex <= frames.size()) {
          container.addStackFrames(frames.subList(firstFrameIndex, frames.size()), true);
        } else {
          container.addStackFrames(Collections.<XStackFrame>emptyList(), true);
        }
      }
    } catch (VMPausedException e) {
      container.errorOccurred(VMPausedException.MESSAGE);
    }
  }
}
