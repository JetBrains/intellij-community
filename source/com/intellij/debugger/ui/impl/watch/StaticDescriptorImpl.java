/*
 * Class StaticDescriptorImpl
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.tree.StaticDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.Field;
import com.sun.jdi.ReferenceType;

import java.util.Iterator;

public class StaticDescriptorImpl extends NodeDescriptorImpl implements StaticDescriptor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.StaticDescriptorImpl");

  private final ReferenceType myType;
  private final boolean myHasStaticFields;

  public StaticDescriptorImpl(ReferenceType refType) {
    myType = refType;

    boolean hasStaticFields = false;
    for (Iterator it = myType.allFields().iterator(); it.hasNext();) {
      Field field = (Field)it.next();
      if (field.isStatic()) {
        hasStaticFields = true;
        break;
      }
    }
    myHasStaticFields = hasStaticFields;
  }

  public ReferenceType getType() {
    return myType;
  }

  public String getName() {
    return "static";
  }

  public boolean isExpandable() {
    return myHasStaticFields;
  }

  public void setContext(EvaluationContextImpl context) {
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener descriptorLabelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return getName() + " = " + myType.name();
  }
}