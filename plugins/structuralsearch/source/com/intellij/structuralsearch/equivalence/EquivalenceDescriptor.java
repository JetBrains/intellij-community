package com.intellij.structuralsearch.equivalence;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface EquivalenceDescriptor {
  List<PsiElement[]> getCodeBlocks();

  List<SingleChildDescriptor> getSingleChildDescriptors();

  List<MultiChildDescriptor> getMultiChildDescriptors();

  List<Object> getConstants();
}
