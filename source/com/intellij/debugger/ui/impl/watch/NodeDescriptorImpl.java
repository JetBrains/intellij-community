package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.HashMap;
import com.sun.jdi.InconsistentDebugInfoException;
import com.sun.jdi.InvalidStackFrameException;

import java.util.ArrayList;
import java.util.List;

public abstract class NodeDescriptorImpl implements NodeDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl");

  public static final String UNKNOWN_VALUE_MESSAGE = "";
  public boolean myIsExpanded = false;
  public boolean myIsSelected = false;
  public boolean myIsVisible  = false;
  public boolean myIsSynthetic = false;

  private EvaluateException myEvaluateException;
  private String            myLabel = "";

  private HashMap<Key, Object> myUserData;

  private final List<NodeDescriptorImpl> myChildren = new ArrayList<NodeDescriptorImpl>();

  public String getName() {
    return null;
  }

  public <T> T getUserData(Key<T> key) {
    if(myUserData == null) return null;
    return (T) myUserData.get(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    if(myUserData == null) {
      myUserData = new HashMap<Key, Object>();
    }
    myUserData.put(key, value);
  }

  public void updateRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener){
    updateRepresentationNoNotify(context, labelListener);
    labelListener.labelChanged();
  }

  protected void updateRepresentationNoNotify(EvaluationContextImpl context, DescriptorLabelListener labelListener) {
    try {
      try {
        myEvaluateException = null;
        myLabel = calcRepresentation(context, labelListener);
      }
      catch (RuntimeException e) {
        LOG.debug(e);
        throw processException(e);
      }
    }
    catch (EvaluateException e) {
      setFailed(e);
    }
  }

  protected abstract String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException;

  private EvaluateException processException(Exception e) {
    if(e instanceof InconsistentDebugInfoException) {
      return new EvaluateException("Inconsistent debug information. Cannot show value. ", null);
    }

    else if(e instanceof InvalidStackFrameException) {
      return new EvaluateException("Internal exception - invalid stackframe. Cannot show value. ", null);
    }
    else {
      return EvaluateExceptionUtil.DEBUG_INFO_UNAVAILABLE;
    }
  }

  public void displayAs(NodeDescriptor descriptor) {
    if (descriptor instanceof NodeDescriptorImpl) {
      final NodeDescriptorImpl that = (NodeDescriptorImpl)descriptor;
      myIsExpanded = that.myIsExpanded;
      myIsSelected = that.myIsSelected;
      myIsVisible  = that.myIsVisible;
      myUserData = that.myUserData != null ? new HashMap<Key, Object>(that.myUserData) : null;
    }
  }

  public abstract boolean isExpandable();

  public abstract void setContext(EvaluationContextImpl context);

  public EvaluateException getEvaluateException() {
    return myEvaluateException;
  }

  public String getLabel() {
    return myLabel;
  }

  public String toString() {
    return getLabel();
  }

  protected String setFailed(EvaluateException e) {
    myEvaluateException = e;
    return e.getMessage();
  }

  protected String setLabel(String customLabel) {
    myLabel = customLabel;
    return myLabel;
  }

  //Context is set to null
  public void clear() {
    myEvaluateException = null;
    myLabel = "";
  }

  public List<NodeDescriptorImpl> getChildren() {
    return myChildren;
  }

  public void setAncestor(NodeDescriptor oldDescriptor) {
    displayAs(oldDescriptor);
  }
}
