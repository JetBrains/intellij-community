package com.intellij.psi.impl.source;

import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class HierarchicalMethodSignatureImpl extends HierarchicalMethodSignature {
  private List<HierarchicalMethodSignature> mySupers;

  public HierarchicalMethodSignatureImpl(@NotNull MethodSignatureBackedByPsiMethod signature) {
    super(signature);
  }

  public void addSuperSignature(@NotNull HierarchicalMethodSignature superSignatureHierarchical) {
    if (mySupers == null) mySupers = new SmartList<HierarchicalMethodSignature>();
    mySupers.add(superSignatureHierarchical);
  }

  @NotNull
  public List<HierarchicalMethodSignature> getSuperSignatures() {
    return mySupers == null ? Collections.<HierarchicalMethodSignature>emptyList() : mySupers;
  }
}
