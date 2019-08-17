package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import lombok.Builder;
import lombok.experimental.FieldNameConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Stream;

public class LombokElementFinder extends PsiElementFinder {

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final int lastDot = qualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return null;
    }
    final String parentName = qualifiedName.substring(0, lastDot);
    final String shortName = qualifiedName.substring(lastDot + 1);

    if (shortName.isEmpty() || parentName.isEmpty()) {
      return null;
    }

    final PsiClass parentClass = getPsiClassForFQN(parentName, scope);
    if (null != parentClass) {
      if (PsiAnnotationSearchUtil.isAnnotatedWith(parentClass, Builder.class, FieldNameConstants.class)) {
        return Stream.of(parentClass.getInnerClasses()).filter(c->c.getName().equals(shortName)).findFirst().orElse(null);
//        return parentClass.findInnerClassByName(shortName, false);
      } else {
//        final Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(parentClass);
//        for (PsiMethod psiMethod : psiMethods) {
//          if (PsiAnnotationSearchUtil.isAnnotatedWith(psiMethod, Builder.class)) {
//            return parentClass.findInnerClassByName(shortName, false);
//          }
//        }
      }
    }

    return null;
  }

  private PsiClass getPsiClassForFQN(@NotNull String parentName, @NotNull GlobalSearchScope scope) {
    final Project project = scope.getProject();
    if (null != project) {
      final Collection<PsiClass> classes = getJavaFullClassNameIndexInstance().get(parentName.hashCode(), project, scope);
      if (!classes.isEmpty()) {
        return classes.iterator().next();
      }
    }
    return null;
  }

  JavaFullClassNameIndex getJavaFullClassNameIndexInstance() {
    return JavaFullClassNameIndex.getInstance();
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }
}
