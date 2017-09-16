package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class LombokElementFinder extends PsiElementFinder {

  private static final ThreadLocal<Set<String>> recursionPrevention = new ThreadLocal<Set<String>>() {
    @Override
    protected Set<String> initialValue() {
      return new HashSet<>();
    }
  };

  JavaFileManager getServiceManager(GlobalSearchScope scope) {
    final Project project = scope.getProject();
    if (null == project) {
      return null;
    }
    return ServiceManager.getService(project, JavaFileManager.class);
  }

  @Nullable
  @Override
  @SuppressWarnings("deprecation")
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

    final PsiClass parentClass = getPsiClassAndPreventRecursionCalls(parentName, scope);
    if (null != parentClass) {
      if (PsiAnnotationSearchUtil.isAnnotatedWith(parentClass, Builder.class, lombok.experimental.Builder.class)) {
        return parentClass.findInnerClassByName(shortName, false);
      } else {
        final Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(parentClass);
        for (PsiMethod psiMethod : psiMethods) {
          if (PsiAnnotationSearchUtil.isAnnotatedWith(psiMethod, Builder.class, lombok.experimental.Builder.class)) {
            return parentClass.findInnerClassByName(shortName, false);
          }
        }
      }
    }

    return null;
  }

  private PsiClass getPsiClassAndPreventRecursionCalls(@NotNull String parentName, @NotNull GlobalSearchScope scope) {
    final JavaFileManager javaFileManager = getServiceManager(scope);
    if (null == javaFileManager) {
      return null;
    }


    final PsiClass foundPsiClass;
    try {
      final boolean firstInvocation = recursionPrevention.get().add(parentName);
      if (firstInvocation) {
        foundPsiClass = javaFileManager.findClass(parentName, scope);
      } else {
        foundPsiClass = null;
      }
    } finally {
      recursionPrevention.get().remove(parentName);
    }
    return foundPsiClass;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }
}
