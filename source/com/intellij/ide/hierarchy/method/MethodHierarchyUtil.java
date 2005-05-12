package com.intellij.ide.hierarchy.method;

import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;

import java.util.Map;

final class MethodHierarchyUtil {
  public static PsiMethod findBaseMethodInClass(final PsiMethod derivedMethod, final PsiClass aClass, final boolean checkBases) {
    if (derivedMethod == null) return null; // base method is invalid

    final EjbClassRole role = J2EERolesUtil.getEjbRole(aClass);

    if (role == null || role.isDeclarationRole()) {
      final Map<PsiClass, PsiSubstitutor> substitutors = new HashMap<PsiClass, PsiSubstitutor>();
      final PsiClass derivedClass = derivedMethod.getContainingClass();
      final PsiMethod[] methods = aClass.findMethodsByName(derivedMethod.getName(), checkBases);
      for (int i = 0; i < methods.length; i++) {
        final PsiMethod baseMethod = methods[i];
        if (baseMethod.getParameterList().getParameters().length == derivedMethod.getParameterList().getParameters().length) {
          PsiSubstitutor substitutor = substitutors.get(baseMethod.getContainingClass());
          if (substitutor == null) {
            substitutor = TypeConversionUtil.getClassSubstitutor(baseMethod.getContainingClass(), derivedMethod.getContainingClass(), PsiSubstitutor.EMPTY);
            substitutors.put(baseMethod.getContainingClass(), substitutor);
          }
          if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
          if (MethodSignatureUtil.findMethodBySuperSignature(derivedClass, baseMethod.getSignature(substitutor)) == derivedMethod) return baseMethod;
        }
      }

      return null;
    }
    else {
      final PsiMethod[] methods = aClass.getMethods();
      for (int j = 0; j < methods.length; j++) {
        final PsiMethod ejbMethod = methods[j];
        final EjbMethodRole methodRole = J2EERolesUtil.getEjbRole(ejbMethod);
        if (methodRole == null) continue;

        final PsiMethod[] declarations = EjbUtil.findEjbDeclarations(ejbMethod);
        for (int k = 0; k < declarations.length; k++) {
          final PsiMethod declaration = declarations[k];
          if (declaration.equals(derivedMethod)) return ejbMethod;

          final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(declaration);
          for (int i = 0; i < superMethods.length; i++) {
            final PsiMethod superMethod = superMethods[i];
            if (declaration.equals(superMethod)) return ejbMethod;
          }
        }
      }
    }

    return aClass.findMethodBySignature(derivedMethod, checkBases);
  }
}
