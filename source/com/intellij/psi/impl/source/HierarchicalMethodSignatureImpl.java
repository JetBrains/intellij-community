package com.intellij.psi.impl.source;

import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

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

  public List<HierarchicalMethodSignature> getSuperSignatures() {
    return Collections.unmodifiableList(mySupers == null ?
                                        Collections.<HierarchicalMethodSignature>emptyList() :
                                        mySupers);
  }

}
