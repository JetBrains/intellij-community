/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 13, 2002
 * Time: 12:07:14 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;

import java.lang.ref.SoftReference;

public class ControlFlowFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ControlFlowFactory");

  private static final Key<SoftReference<ControlFlowContext>> CONTROL_FLOW_KEY = Key.create("CONTROL_FLOW_KEY");

  private static class ControlFlowContext {
    private final ControlFlowPolicy policy;
    private final boolean evaluateConstantIfCondition;
    private final ControlFlow flow;
    private ControlFlowContext next;
    private final long myModificationCount;

    public ControlFlowContext(boolean evaluateConstantIfCondition, ControlFlowPolicy policy, ControlFlow flow, ControlFlowContext next, long modificationCount) {
      this.evaluateConstantIfCondition = evaluateConstantIfCondition;
      this.policy = policy;
      this.flow = flow;
      this.next = next;
      myModificationCount = modificationCount;
    }
  }

  public static ControlFlow getControlFlow(PsiElement element, ControlFlowPolicy policy) throws ControlFlowAnalyzer.AnalysisCanceledException {
    return getControlFlow(element, policy, true);
  }

  public static ControlFlow getControlFlow(PsiElement element, ControlFlowPolicy policy, boolean evaluateConstantIfCondition) throws ControlFlowAnalyzer.AnalysisCanceledException {
    SoftReference<ControlFlowContext> ref = element.getUserData(CONTROL_FLOW_KEY);
    ControlFlowContext flows = ref == null ? null : ref.get();
    ControlFlowContext currentFlow = flows;
    final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
    ControlFlow flow = null;
    while (currentFlow != null) {
      if (currentFlow.policy == policy && isValid(currentFlow, modificationCount)) {
        // optimization: when no constant condition were computed, both control flows are the same
        if (currentFlow.evaluateConstantIfCondition == evaluateConstantIfCondition || !currentFlow.flow.isConstantConditionOccurred()) {
          if (currentFlow.evaluateConstantIfCondition != evaluateConstantIfCondition) {
            LOG.debug("CF constant cond optimization works for "+element+"("+(element.getText().length() > 40 ? element.getText().substring(0,40) : element.getText())+")");
          }
          flow = currentFlow.flow;
          break;
        }
      }
      currentFlow = currentFlow.next;
    }
    if (flow == null) {
      flow = new ControlFlowAnalyzer(element, policy, true, evaluateConstantIfCondition).buildControlFlow();
      registerControlFlow(element, flow, evaluateConstantIfCondition, policy);
    }
    if (flow instanceof ControlFlowSubRange) {
      LOG.debug("CF optimization subrange works for "+element+"("+(element.getText().length() > 40 ? element.getText().substring(0,40) : element.getText())+")");
    }
    return flow;
  }

  static void registerControlFlow(PsiElement element, ControlFlow flow, boolean evaluateConstantIfCondition, ControlFlowPolicy policy) {
    SoftReference<ControlFlowContext> ref = element.getUserData(CONTROL_FLOW_KEY);
    ControlFlowContext flows = ref == null ? null : (ControlFlowContext) ref.get();
    final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
    final ControlFlowContext controlFlowContext = new ControlFlowContext(evaluateConstantIfCondition, policy, flow, flows, modificationCount);
    element.putUserData(CONTROL_FLOW_KEY, new SoftReference<ControlFlowContext>(controlFlowContext));
  }

  private static boolean isValid(ControlFlowContext currentFlow, long modificationCount) {
    return modificationCount == currentFlow.myModificationCount;
  }

}

