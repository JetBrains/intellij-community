package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PsiTypeUtil {

  @NotNull
  public static PsiType[] extractTypeParameters(@NotNull PsiType psiType, @NotNull PsiManager psiManager) {
    if (!(psiType instanceof PsiClassType)) {
      return PsiType.EMPTY_ARRAY;
    }

    final PsiClassType classType = (PsiClassType) psiType;
    final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
    final PsiClass psiClass = classResolveResult.getElement();
    if (psiClass == null) {
      return PsiType.EMPTY_ARRAY;
    }
    final PsiSubstitutor psiSubstitutor = classResolveResult.getSubstitutor();

    final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();

    final PsiType[] psiTypes = PsiType.createArray(typeParameters.length);
    for (int i = 0; i < typeParameters.length; i++) {
      PsiType psiSubstituteKeyType = psiSubstitutor.substitute(typeParameters[i]);
      if (null == psiSubstituteKeyType) {
        psiSubstituteKeyType = PsiType.getJavaLangObject(psiManager, GlobalSearchScope.allScope(psiManager.getProject()));
      }
      psiTypes[i] = psiSubstituteKeyType;
    }
    return psiTypes;
  }

  @NotNull
  public static PsiClassType getCollectionClassType(@NotNull PsiClassType psiType, @NotNull Project project, @NotNull String qualifiedName) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope globalsearchscope = GlobalSearchScope.allScope(project);

    PsiClass genericClass = facade.findClass(qualifiedName, globalsearchscope);
    if (null != genericClass) {
      final PsiClassType.ClassResolveResult classResolveResult = psiType.resolveGenerics();
      final PsiSubstitutor derivedSubstitutor = classResolveResult.getSubstitutor();

      final List<PsiType> typeList = new ArrayList<PsiType>(2);
      final Map<String, PsiType> nameTypeMap = new HashMap<String, PsiType>();
      for (Map.Entry<PsiTypeParameter, PsiType> entry : derivedSubstitutor.getSubstitutionMap().entrySet()) {
        nameTypeMap.put(entry.getKey().getName(), entry.getValue());
        typeList.add(entry.getValue());
      }

      PsiSubstitutor genericSubstitutor = PsiSubstitutor.EMPTY;
      final PsiTypeParameter[] typeParameters = genericClass.getTypeParameters();
      for (int i = 0; i < typeParameters.length; i++) {
        final PsiTypeParameter psiTypeParameter = typeParameters[i];
        PsiType mappedType = nameTypeMap.get(psiTypeParameter.getName());
        if (null == mappedType && typeList.size() > i) {
          mappedType = typeList.get(i);
        }
        if (null != mappedType) {
          genericSubstitutor = genericSubstitutor.put(psiTypeParameter, mappedType);
        }
      }
      return elementFactory.createType(genericClass, genericSubstitutor);
    }
    return elementFactory.createTypeByFQClassName(qualifiedName, globalsearchscope);
  }

  @Nullable
  public static String getQualifiedName(@NotNull PsiType psiType) {
    final PsiClass psiFieldClass = PsiUtil.resolveClassInType(psiType);
    return psiFieldClass != null ? psiFieldClass.getQualifiedName() : null;
  }
}
