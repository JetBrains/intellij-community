package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.tree.StackFrameDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.util.IconLoader;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;

import javax.swing.*;

/**
 * Nodes of this type cannot be updated, because StackFrame objects become invalid as soon as VM has been resumed
 */
public class StackFrameDescriptorImpl extends NodeDescriptorImpl implements StackFrameDescriptor{
  private final StackFrameProxyImpl myFrame;
  private String myName = null;
  private Location myLocation;

  private static Icon myStackFrameIcon = IconLoader.getIcon("/debugger/stackFrame.png");;
  private static Icon myObsoleteFrameIcon = IconLoader.getIcon("/debugger/db_obsolete.png");
  private Icon myIcon = myStackFrameIcon;

  public StackFrameDescriptorImpl(StackFrameProxyImpl frame) {
    myFrame = frame;
  }

  public StackFrameProxyImpl getStackFrame() {
    return myFrame;
  }

  public String getName() {
    return myName;
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener descriptorLabelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ThreadsViewSettings settings = ThreadsViewSettings.getInstance();
    StringBuffer label = new StringBuffer();
    Location location = myFrame.location();
    Method method = location.method();
    if (method != null) {
      myName = method.name();
      label.append(myName);
      label.append("()");
    }
    if (settings.SHOW_LINE_NUMBER) {
      String lineNumber = null;
      try {
        lineNumber = Integer.toString(location.lineNumber());
      }
      catch (InternalError e) {
        lineNumber = e.toString();
      }
      if (lineNumber != null) {
        label.append(':');
        label.append(lineNumber);
      }
    }
    if (settings.SHOW_CLASS_NAME) {
      String name = null;
      try {
        ReferenceType refType = location.declaringType();
        name = refType != null ? refType.name() : null;
      }
      catch (InternalError e) {
        name = e.toString();
      }
      if (name != null) {
        label.append(", ");
        label.append(name);
      }
    }
    if (settings.SHOW_SOURCE_NAME) {
      try {
        String sourceName;
        try {
          sourceName = location.sourceName();
        }
        catch (InternalError e) {
          sourceName = e.toString();
        }
        label.append(", ");
        label.append(sourceName);
      }
      catch (AbsentInformationException exception) {
      }
    }
    return label.toString();
  }

  public final boolean stackFramesEqual(StackFrameDescriptorImpl d) {
    return getStackFrame().equals(d.getStackFrame());
  }

  public boolean isExpandable() {
    return true;
  }

  public void setContext(EvaluationContextImpl context) {
    try {
      myLocation = myFrame.location();
    }
    catch (EvaluateException e) {
      myLocation = null;
    }

    myIcon = calcIcon();
  }

  public Location getLocation() {
    return myLocation;
  }

  private Icon calcIcon() {
    try {
      if(getStackFrame().isObsolete()) {
        return myObsoleteFrameIcon;
      }
    }
    catch (EvaluateException e) {
    }
    return myStackFrameIcon;
  }

  public Icon getIcon() {
    return myIcon;
  }
}