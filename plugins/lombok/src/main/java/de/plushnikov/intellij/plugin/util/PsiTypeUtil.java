package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiTypeUtil {

  @NotNull
  public static PsiType extractOneElementType(@NotNull PsiType psiType, @NotNull PsiManager psiManager) {
    return extractOneElementType(psiType, psiManager, CommonClassNames.JAVA_LANG_ITERABLE, 0);
  }

  @NotNull
  public static PsiType extractOneElementType(@NotNull PsiType psiType, @NotNull PsiManager psiManager, final String superClass, final int paramIndex) {
    PsiType oneElementType = substituteTypeParameter(psiType, superClass, paramIndex);
    if (null == oneElementType) {
      oneElementType = getJavaLangObject(psiManager);
    }
    return oneElementType;
  }

  private static PsiType substituteTypeParameter(@NotNull PsiType psiType, String superClass, int paramIndex) {
    PsiType oneElementType = PsiUtil.substituteTypeParameter(psiType, superClass, paramIndex, true);
    if (oneElementType instanceof PsiWildcardType) {
      oneElementType = ((PsiWildcardType) oneElementType).getBound();
    }
    return oneElementType;
  }

  @NotNull
  public static PsiType extractAllElementType(@NotNull PsiType psiType, @NotNull PsiManager psiManager) {
    return extractAllElementType(psiType, psiManager, CommonClassNames.JAVA_LANG_ITERABLE, 0);
  }

  @NotNull
  public static PsiType extractAllElementType(@NotNull PsiType psiType, @NotNull PsiManager psiManager, final String superClass, final int paramIndex) {
    PsiType oneElementType = substituteTypeParameter(psiType, superClass, paramIndex);

    if (null == oneElementType || Comparing.equal(getJavaLangObject(psiManager), oneElementType)) {
      return PsiWildcardType.createUnbounded(psiManager);
    } else {
      return PsiWildcardType.createExtends(psiManager, oneElementType);
    }
  }

  @NotNull
  private static PsiClassType getJavaLangObject(@NotNull PsiManager psiManager) {
    return PsiType.getJavaLangObject(psiManager, GlobalSearchScope.allScope(psiManager.getProject()));
  }

  @NotNull
  public static PsiType createCollectionType(@NotNull PsiManager psiManager, final String collectionQualifiedName, @NotNull PsiType... psiTypes) {
    final Project project = psiManager.getProject();
    final GlobalSearchScope globalsearchscope = GlobalSearchScope.allScope(project);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    final PsiClass genericClass = facade.findClass(collectionQualifiedName, globalsearchscope);
    if (null != genericClass) {
      return JavaPsiFacade.getElementFactory(project).createType(genericClass, psiTypes);
    } else {
      return getJavaLangObject(psiManager);
    }
  }

  @Nullable
  public static String getQualifiedName(@NotNull PsiType psiType) {
    final PsiClass psiFieldClass = PsiUtil.resolveClassInType(psiType);
    return psiFieldClass != null ? psiFieldClass.getQualifiedName() : null;
  }
}
