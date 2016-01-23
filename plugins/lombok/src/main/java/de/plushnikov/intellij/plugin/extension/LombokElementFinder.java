package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class LombokElementFinder extends PsiElementFinder {
  @Nullable
  @Override
  @SuppressWarnings("deprecation")
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final Project project = scope.getProject();
    if (null == project) {
      return null;
    }

    final int lastDot = qualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return null;
    }
    final String shortName = qualifiedName.substring(lastDot + 1);
    final String parentName = qualifiedName.substring(0, lastDot);

    if (shortName.isEmpty() || parentName.isEmpty()) {
      return null;
    }

    final JavaFileManager javaFileManager = ServiceManager.getService(project, JavaFileManager.class);
    if (null != javaFileManager) {
      final PsiClass parentClass = javaFileManager.findClass(parentName, scope);
      if (null != parentClass) {
        if (PsiAnnotationUtil.isAnnotatedWith(parentClass, Builder.class, lombok.experimental.Builder.class)) {
          return parentClass.findInnerClassByName(shortName, false);
        } else {
          final Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(parentClass);
          for (PsiMethod psiMethod : psiMethods) {
            if (PsiAnnotationUtil.isAnnotatedWith(psiMethod, Builder.class, lombok.experimental.Builder.class)) {
              return parentClass.findInnerClassByName(shortName, false);
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }
}
