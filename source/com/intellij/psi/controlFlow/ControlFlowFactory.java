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
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.util.List;

public class ControlFlowFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ControlFlowFactory");

  private static final Key<List<Reference<ControlFlowContext>>> CONTROL_FLOW_KEY = Key.create("CONTROL_FLOW_KEY");

  private ControlFlowFactory() {}

  private static class ControlFlowContext {
    private final ControlFlowPolicy policy;
    private final boolean evaluateConstantIfCondition;
    private final ControlFlow flow;
    private final long myModificationCount;

    public ControlFlowContext(boolean evaluateConstantIfCondition, ControlFlowPolicy policy, ControlFlow flow, long modificationCount) {
      this.evaluateConstantIfCondition = evaluateConstantIfCondition;
      this.policy = policy;
      this.flow = flow;
      myModificationCount = modificationCount;
    }
  }

  public static ControlFlow getControlFlow(@NotNull PsiElement element, @NotNull ControlFlowPolicy policy) throws AnalysisCanceledException {
    return getControlFlow(element, policy, true, true);
  }

  public static ControlFlow getControlFlow(@NotNull PsiElement element, @NotNull ControlFlowPolicy policy, boolean evaluateConstantIfCondition) throws AnalysisCanceledException {
    return getControlFlow(element, policy, true, evaluateConstantIfCondition);
  }

  public static ControlFlow getControlFlow(@NotNull PsiElement element,
                                           @NotNull ControlFlowPolicy policy,
                                           boolean enableShortCircuit,
                                           boolean evaluateConstantIfCondition) throws AnalysisCanceledException {
    synchronized (element) {
      List<Reference<ControlFlowContext>> refs = element.getUserData(CONTROL_FLOW_KEY);
      Pair<Integer, ControlFlow> result = findControlFlow(refs, element, policy, evaluateConstantIfCondition);
      ControlFlow flow = result == null ? null : result.getSecond();
      if (flow == null) {
        flow = new ControlFlowAnalyzer(element, policy, enableShortCircuit, evaluateConstantIfCondition).buildControlFlow();
        registerControlFlow(element, flow, evaluateConstantIfCondition, policy);
      }
      if (flow instanceof ControlFlowSubRange && LOG.isDebugEnabled()) {
        LOG.debug("CF optimization subrange works for "+element+"("+(element.getText().length() > 40 ? element.getText().substring(0,40) : element.getText())+")");
      }
      return flow;
    }
  }

  private static Pair<Integer, ControlFlow> findControlFlow(final List<Reference<ControlFlowContext>> refs,
                                                            final PsiElement element,
                                                            final ControlFlowPolicy policy,
                                                            final boolean evaluateConstantIfCondition) {
    if (refs != null) {
      final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
      for (int i = refs.size()-1; i>=0; i--) {
        Reference<ControlFlowContext> ref = refs.get(i);
        ControlFlowContext currentFlow = ref == null ? null : ref.get();
        if (currentFlow == null) {
          refs.set(i, null);
          continue;
        }
        if (modificationCount != currentFlow.myModificationCount) {
          refs.clear();
          return null;
        }
        if (currentFlow.policy != policy) {
          continue;
        }

        // optimization: when no constant condition were computed, both control flows are the same
        if (currentFlow.evaluateConstantIfCondition == evaluateConstantIfCondition ||
            currentFlow.evaluateConstantIfCondition && !currentFlow.flow.isConstantConditionOccurred()) {
          if (currentFlow.evaluateConstantIfCondition != evaluateConstantIfCondition && LOG.isDebugEnabled()) {
            LOG.debug("CF constant cond optimization works for "+element+"("+(element.getText().length() > 40 ? element.getText().substring(0,40) : element.getText())+")");
          }
          return new Pair<Integer, ControlFlow>(i, currentFlow.flow);
        }
      }
    }
    return null;
  }

  static void flushControlFlows(@NotNull PsiElement element) {
    synchronized (element) {
      element.putUserData(CONTROL_FLOW_KEY, null);
    }
  }
  static void registerControlFlow(@NotNull PsiElement element, @NotNull ControlFlow flow, boolean evaluateConstantIfCondition, @NotNull ControlFlowPolicy policy) {
    synchronized (element) {
      List<Reference<ControlFlowContext>> refs = element.getUserData(CONTROL_FLOW_KEY);
      Pair<Integer, ControlFlow> result = findControlFlow(refs, element, policy, evaluateConstantIfCondition);
      if (result != null) {
        int index = result.getFirst();
        refs.set(index, createFlowContext(element, evaluateConstantIfCondition, policy, flow));
      }
      else {
        addFlow(refs, element, evaluateConstantIfCondition, policy, flow);
      }
    }
  }

  private static void addFlow(List<Reference<ControlFlowContext>> refs,
                              final PsiElement element,
                              final boolean evaluateConstantIfCondition,
                              final ControlFlowPolicy policy,
                              final ControlFlow flow) {
    if (refs == null) {
      refs = new SmartList<Reference<ControlFlowContext>>();
      element.putUserData(CONTROL_FLOW_KEY, refs);
    }
    Reference<ControlFlowContext> ref = createFlowContext(element, evaluateConstantIfCondition, policy, flow);
    refs.add(ref);
  }

  private static Reference<ControlFlowContext> createFlowContext(final PsiElement element,
                                                                 final boolean evaluateConstantIfCondition,
                                                                 final ControlFlowPolicy policy,
                                                                 final ControlFlow flow) {
    final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
    final ControlFlowContext controlFlowContext = new ControlFlowContext(evaluateConstantIfCondition, policy, flow, modificationCount);
    return new SoftReference<ControlFlowContext>(controlFlowContext);
  }

}

