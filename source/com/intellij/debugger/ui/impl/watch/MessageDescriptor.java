package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.diagnostic.Logger;

public class MessageDescriptor extends NodeDescriptorImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.MessageDescriptor");

  public static final int ERROR = 0;
  public static final int WARNING = 1;
  public static final int INFORMATION = 2;
  public static final int SPECIAL = 3;
  private int myKind;
  private String myMessage;

  public static MessageDescriptor DEBUG_INFO_UNAVAILABLE = new MessageDescriptor("Debug info unavailable");
  public static MessageDescriptor LOCAL_VARIABLES_INFO_UNAVAILABLE = new MessageDescriptor("Local variables debug info unavailable");
  public static MessageDescriptor ALL_ELEMENTS_IN_VISIBLE_RANGE_ARE_NULL = new MessageDescriptor("All elements in visible range are null");
  public static MessageDescriptor ALL_ELEMENTS_IN_RANGE_ARE_NULL = new MessageDescriptor("All elements are null");
  public static MessageDescriptor ARRAY_IS_EMPTY = new MessageDescriptor("Empty");
  public static MessageDescriptor CLASS_HAS_NO_FIELDS = new MessageDescriptor("Class has no fields");
  public static MessageDescriptor OBJECT_COLLECTED = new MessageDescriptor("Object was garbage collected during method invokation");
  public static MessageDescriptor EVALUATING = new MessageDescriptor(NodeDescriptorImpl.EVALUATING_MESSAGE);
  public static MessageDescriptor THREAD_IS_RUNNING = new MessageDescriptor("Thread is running");
  public static MessageDescriptor THREAD_IS_EMPTY = new MessageDescriptor("Thread has no frames");

  public MessageDescriptor(String message) {
    this(message, INFORMATION);
  }

  public MessageDescriptor(String message, int kind) {
    myKind = kind;
    myMessage = message;
  }

  public int getKind() {
    return myKind;
  }

  public String getLabel() {
    return myMessage;
  }

  public boolean isExpandable() {
    return false;
  }

  public void setContext(EvaluationContextImpl context) {
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myMessage;
  }
}