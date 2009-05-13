package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.ComplexPackagingElementNode;
import com.intellij.packaging.elements.ComplexPackagingElement;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.HashSet;

/**
 * @author nik
 */
public class ComplexElementSubstitutionParameters {
  private boolean mySubstituteAll;
  private Set<ComplexPackagingElement> mySubstituted = new HashSet<ComplexPackagingElement>();

  public void setSubstituteAll() {
    mySubstituteAll = true;
  }

  public void setSubstituteNone() {
    mySubstituted.clear();
    mySubstituteAll = false;
  }

  public boolean shouldSubstitute(@NotNull ComplexPackagingElement element) {
    return mySubstituteAll || mySubstituted.contains(element);
  }

  public void substitute(ComplexPackagingElementNode complexNode) {
    mySubstituted.addAll(complexNode.getPackagingElements());
  }

  public void dontSubstitute(ComplexPackagingElement<?> element) {
    mySubstituted.remove(element);
  }
}
