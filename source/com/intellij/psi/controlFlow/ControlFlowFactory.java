/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 13, 2002
 * Time: 12:07:14 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.controlFlow;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ControlFlowFactory {
  // psiElements hold weakly, controlFlows softly
  private final ConcurrentMap<PsiElement, Reference<CopyOnWriteArrayList<ControlFlowContext>>> cachedFlows = new ConcurrentWeakHashMap<PsiElement, Reference<CopyOnWriteArrayList<ControlFlowContext>>>();

  public static ControlFlowFactory getInstance(Project project) {
    return ServiceManager.getService(project, ControlFlowFactory.class);
  }

  public ControlFlowFactory(PsiManagerEx psiManager) {
    psiManager.registerRunnableToRunOnChange(new Runnable(){
      public void run() {
        clearCache();
      }
    });
  }

  public void clearCache() {
    cachedFlows.clear();
  }

  public void registerSubRange(final PsiElement codeFragment, final ControlFlowSubRange flow, final boolean evaluateConstantIfConfition,
                               final ControlFlowPolicy policy) {
    registerControlFlow(codeFragment, flow, evaluateConstantIfConfition, policy);
  }

  private static class ControlFlowContext {
    private final ControlFlowPolicy policy;
    private final boolean evaluateConstantIfCondition;
    private final long modificationCount;
    private final ControlFlow controlFlow;

    public ControlFlowContext(boolean evaluateConstantIfCondition, @NotNull ControlFlowPolicy policy, long modificationCount, ControlFlow controlFlow) {
      this.evaluateConstantIfCondition = evaluateConstantIfCondition;
      this.policy = policy;
      this.modificationCount = modificationCount;
      this.controlFlow = controlFlow;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ControlFlowContext that = (ControlFlowContext)o;

      return isFor(that);
    }

    public int hashCode() {
      int result = policy.hashCode();
      result = 31 * result + (evaluateConstantIfCondition ? 1 : 0);
      result = 31 * result + (int)(modificationCount ^ (modificationCount >>> 32));
      return result;
    }

    public boolean isFor(final ControlFlowPolicy policy, final boolean evaluateConstantIfCondition, long modificationCount) {
      if (modificationCount != this.modificationCount) return false;
      if (!policy.equals(this.policy)) return false;

      // optimization: when no constant condition were computed, both control flows are the same
      if (!controlFlow.isConstantConditionOccurred()) return true;

      if (evaluateConstantIfCondition != this.evaluateConstantIfCondition) return false;

      return true;
    }

    private boolean isFor(final ControlFlowContext that) {
      return isFor(that.policy, that.evaluateConstantIfCondition, that.modificationCount);
    }
  }

  public ControlFlow getControlFlow(@NotNull PsiElement element, @NotNull ControlFlowPolicy policy) throws AnalysisCanceledException {
    return getControlFlow(element, policy, true, true);
  }

  public ControlFlow getControlFlow(@NotNull PsiElement element, @NotNull ControlFlowPolicy policy, boolean evaluateConstantIfCondition) throws AnalysisCanceledException {
    return getControlFlow(element, policy, true, evaluateConstantIfCondition);
  }

  public ControlFlow getControlFlow(@NotNull PsiElement element,
                                           @NotNull ControlFlowPolicy policy,
                                           boolean enableShortCircuit,
                                           boolean evaluateConstantIfCondition) throws AnalysisCanceledException {
    final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
    CopyOnWriteArrayList<ControlFlowContext> cached = getOrCreateCachedFlowsForElement(element);
    for (ControlFlowContext context : cached) {
      if (context.isFor(policy, evaluateConstantIfCondition,modificationCount)) return context.controlFlow;
    }
    ControlFlow controlFlow = new ControlFlowAnalyzer(element, policy, enableShortCircuit, evaluateConstantIfCondition).buildControlFlow();
    ControlFlowContext context = createContext(evaluateConstantIfCondition, policy, controlFlow, modificationCount);
    cached.addIfAbsent(context);
    return controlFlow;
  }

  private static ControlFlowContext createContext(final boolean evaluateConstantIfCondition, final ControlFlowPolicy policy,
                                                  final ControlFlow controlFlow,
                                                  final long modificationCount) {
    return new ControlFlowContext(evaluateConstantIfCondition, policy, modificationCount,controlFlow);
  }

  private void registerControlFlow(@NotNull PsiElement element, @NotNull ControlFlow flow, boolean evaluateConstantIfCondition, @NotNull ControlFlowPolicy policy) {
    final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
    ControlFlowContext controlFlowContext = createContext(evaluateConstantIfCondition, policy, flow, modificationCount);

    CopyOnWriteArrayList<ControlFlowContext> cached = getOrCreateCachedFlowsForElement(element);
    cached.addIfAbsent(controlFlowContext);
  }

  @NotNull
  private CopyOnWriteArrayList<ControlFlowContext> getOrCreateCachedFlowsForElement(final PsiElement element) {
    Reference<CopyOnWriteArrayList<ControlFlowContext>> cachedRef = cachedFlows.get(element);
    CopyOnWriteArrayList<ControlFlowContext> cached = cachedRef == null ? null : cachedRef.get();
    if (cached == null) {
      cached = new CopyOnWriteArrayList<ControlFlowContext>();
      Reference<CopyOnWriteArrayList<ControlFlowContext>> reference = new SoftReference<CopyOnWriteArrayList<ControlFlowContext>>(cached);
      cachedRef = ConcurrencyUtil.cacheOrGet(cachedFlows, element, reference);
      CopyOnWriteArrayList<ControlFlowContext> existing = cachedRef.get();
      if (existing != null) cached = existing;
    }
    return cached;
  }
}

