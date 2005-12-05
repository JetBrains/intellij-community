package com.intellij.psi.impl.source;

import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class HierarchicalMethodSignatureImpl extends HierarchicalMethodSignature {
  List<HierarchicalMethodSignature> mySupers;

  public HierarchicalMethodSignatureImpl(final MethodSignatureBackedByPsiMethod signature) {
    super(signature);
  }

  public void addSuperSignature(HierarchicalMethodSignature superSignatureHierarchical) {
    if (mySupers == null) mySupers = new ArrayList<HierarchicalMethodSignature>(2);
    mySupers.add(superSignatureHierarchical);
  }

  @NotNull
  public List<HierarchicalMethodSignature> getSuperSignatures() {
    return Collections.unmodifiableList(mySupers == null ?
                                        Collections.<HierarchicalMethodSignature>emptyList() :
                                        mySupers);
  }

}
