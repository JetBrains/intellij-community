package com.intellij.refactoring.psi;

import com.intellij.psi.*;

import java.util.Set;

@SuppressWarnings({"AssignmentToCollectionOrArrayFieldFromParameter"})
public class TypeParametersVisitor extends JavaRecursiveElementVisitor {
   private final Set<PsiTypeParameter> params;

   public TypeParametersVisitor(Set<PsiTypeParameter> params) {
       super();
       this.params = params;
   }

   public void visitTypeElement(PsiTypeElement typeElement) {
       super.visitTypeElement(typeElement);
       final PsiType type = typeElement.getType();
       if (type instanceof PsiClassType) {
           final PsiClass referent = ((PsiClassType) type).resolve();
           if (referent instanceof PsiTypeParameter) {
               params.add((PsiTypeParameter) referent);
           }
       }
   }

}
