/*
 * @author: Eugene Zhuravlev
 * Date: Jul 23, 2002
 * Time: 11:10:11 AM
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.request.StepRequest;

class RequestHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.RequestHint");
  private final int myDepth;
  private SourcePosition myPosition;
  private int myFrameCount;
  private VirtualMachineProxyImpl myVirtualMachineProxy;

  private boolean myIgnoreFilters = false;
  private boolean myRestoreBreakpoints = false;
  private boolean mySkipThisMethod = false;

  public RequestHint(final SuspendContextImpl suspendContext, int depth) {
    final DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
    myDepth = depth;
    myVirtualMachineProxy = debugProcess.getVirtualMachineProxy();

    try {
      final ThreadReferenceProxyImpl thread = suspendContext.getThread();
      myFrameCount = thread.frameCount();

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          myPosition = ContextUtil.getSourcePosition(suspendContext);
        }
      });
    }
    catch (Exception e) {
      myPosition = null;
    }
  }

  public void setIgnoreFilters(boolean ignoreFilters) {
    myIgnoreFilters = ignoreFilters;
  }

  public void setRestoreBreakpoints(boolean restoreBreakpoints) {
    myRestoreBreakpoints = restoreBreakpoints;
  }

  public boolean isRestoreBreakpoints() {
    return myRestoreBreakpoints;
  }

  public boolean isIgnoreFilters() {
    return myIgnoreFilters;
  }

  public int getDepth() {
    return mySkipThisMethod ? StepRequest.STEP_OUT : myDepth;
  }

  public boolean shouldSkipFrame(final SuspendContextImpl context) {
    try {
      if(mySkipThisMethod) {
        mySkipThisMethod = false;
        return true;
      }

      if (myPosition != null) {
        final SourcePosition locationPosition = ApplicationManager.getApplication().runReadAction(new Computable<SourcePosition>() {
          public SourcePosition compute() {
            return ContextUtil.getSourcePosition(context);          
          }
        });

        if(locationPosition == null) return true;

        int frameCount = -1;
        if (context.getFrameProxy() != null) {
          try {
            frameCount = context.getFrameProxy().threadProxy().frameCount();
          }
          catch (EvaluateException e) {
          }
        }
        if (myPosition.getFile().equals(locationPosition.getFile()) && myPosition.getLine() == locationPosition.getLine() && myFrameCount == frameCount) {
          return true;
        }
      }
      DebuggerSettings settings = DebuggerSettings.getInstance();
      if (settings.SKIP_SYNTHETIC_METHODS) {
        Location location = context.getFrameProxy().location();
        Method method = location.method();
        if (method != null) {
          if (myVirtualMachineProxy.canGetSyntheticAttribute()? method.isSynthetic() : method.name().indexOf('$') >= 0) {
            return true;
          }
        }
      }
      if (!myIgnoreFilters) {
        if(settings.SKIP_GETTERS) {
          boolean isGetter = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>(){
            public Boolean compute() {
              PsiMethod psiMethod = PsiTreeUtil.getParentOfType(PositionUtil.getContextElement(context), PsiMethod.class);
              if(psiMethod == null) return Boolean.FALSE;

              return new Boolean(PropertyUtil.isSimplePropertyGetter(psiMethod));
            }
          }).booleanValue();

          if(isGetter) {
            mySkipThisMethod = isGetter;
            return true;
          }
        }

        if (settings.SKIP_CONSTRUCTORS) {
          Location location = context.getFrameProxy().location();
          Method method = location.method();
          if (method != null && method.isConstructor()) {
            mySkipThisMethod = true;
            return true;
          }
        }
      }
      return false;
    }
    catch (VMDisconnectedException e) {
      return false;
    }
    catch (EvaluateException e) {
      LOG.error(e);
      return false;
    }
  }

}
