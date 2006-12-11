/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 13, 2002
 * Time: 12:07:14 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.controlFlow;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.ref.Reference;

public class ControlFlowFactory extends AbstractProjectComponent {
  private final ConcurrentHashMap<ControlFlowContext, ControlFlow> flows = new ConcurrentHashMap<ControlFlowContext, ControlFlow>();

  public static ControlFlowFactory getInstance(Project project) {
    return project.getComponent(ControlFlowFactory.class);
  }

  public ControlFlowFactory(Project project) {
    super(project);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ControlFlowFactory";
  }

  public void registerSubRange(final PsiElement codeFragment, final ControlFlowSubRange flow, final boolean evaluateConstantIfConfition,
                               final ControlFlowPolicy policy) {
    registerControlFlow(codeFragment, flow, evaluateConstantIfConfition, policy);
  }

  private static class ControlFlowContext {
    private final ControlFlowPolicy policy;
    private final boolean evaluateConstantIfCondition;
    private final Reference<PsiElement> element;
    private final long modificationCount;

    public ControlFlowContext(boolean evaluateConstantIfCondition, @NotNull ControlFlowPolicy policy, @NotNull PsiElement element, long modificationCount) {
      this.evaluateConstantIfCondition = evaluateConstantIfCondition;
      this.policy = policy;
      this.element = new SoftReference<PsiElement>(element);
      this.modificationCount = modificationCount;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ControlFlowContext that = (ControlFlowContext)o;

      if (evaluateConstantIfCondition != that.evaluateConstantIfCondition) return false;
      if (modificationCount != that.modificationCount) return false;
      if (!Comparing.equal(element.get(), that.element.get())) return false;
      if (!policy.equals(that.policy)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = policy.hashCode();
      result = 31 * result + (evaluateConstantIfCondition ? 1 : 0);
      PsiElement psiElement = element.get();
      result = 31 * result + (psiElement != null ? psiElement.hashCode() : 0);
      result = 31 * result + (int)(modificationCount ^ (modificationCount >>> 32));
      return result;
    }
  }

  public ControlFlow getControlFlow(@NotNull PsiElement element, @NotNull ControlFlowPolicy policy) throws AnalysisCanceledException {
    return getControlFlow(element, policy, true, true);
  }

  public ControlFlow getControlFlow(@NotNull PsiElement element, @NotNull ControlFlowPolicy policy, boolean evaluateConstantIfCondition) throws AnalysisCanceledException {
    return getControlFlow(element, policy, true, evaluateConstantIfCondition);
  }

  private final ReentrantLock myLock = new ReentrantLock();
  public ControlFlow getControlFlow(@NotNull PsiElement element,
                                           @NotNull ControlFlowPolicy policy,
                                           boolean enableShortCircuit,
                                           boolean evaluateConstantIfCondition) throws AnalysisCanceledException {
    ControlFlowContext context = createContext(element, evaluateConstantIfCondition, policy);
    ControlFlow flow = flows.get(context);
    if (flow == null) {
      myLock.lock();
      try {
        flow = new ControlFlowAnalyzer(element, policy, enableShortCircuit, evaluateConstantIfCondition).buildControlFlow();
        flows.put(context, flow);
      }
      finally {
        myLock.unlock();
      }
    }
    return flow;
  }

  private static ControlFlowContext createContext(final PsiElement element, final boolean evaluateConstantIfCondition, final ControlFlowPolicy policy) {
    final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
    return new ControlFlowContext(evaluateConstantIfCondition, policy, element, modificationCount);
  }

  public void flushInvalidControlFlows() {
    long modificationCount = PsiManager.getInstance(myProject).getModificationTracker().getModificationCount();
    for (ControlFlowContext context : flows.keySet()) {
      PsiElement element = context.element.get();
      if (context.modificationCount != modificationCount
        || element == null
        || !element.isValid()
        || flows.get(context) instanceof ControlFlowSubRange
        ) {
        flows.remove(context);
      }
    }
  }


  private void registerControlFlow(@NotNull PsiElement element, @NotNull ControlFlow flow, boolean evaluateConstantIfCondition, @NotNull ControlFlowPolicy policy) {
    ControlFlowContext controlFlowContext = createContext(element, evaluateConstantIfCondition, policy);
    flows.putIfAbsent(controlFlowContext, flow);
    if (evaluateConstantIfCondition && !flow.isConstantConditionOccurred()) {
      // optimization: when no constant condition were computed, both control flows are the same
      ControlFlowContext otherContext = createContext(element, false, policy);
      flows.putIfAbsent(otherContext, flow);
    }
  }
}

