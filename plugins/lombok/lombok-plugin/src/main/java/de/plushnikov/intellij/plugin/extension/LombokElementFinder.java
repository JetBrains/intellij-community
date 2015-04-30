package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LombokElementFinder extends PsiElementFinder {
  @Nullable
  @Override
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

    if (shortName.length() == 0 || parentName.length() == 0) {
      return null;
    }

    List<PsiClass> result = new SmartList<PsiClass>();

    final JavaFileManager javaFileManager = ServiceManager.getService(project, JavaFileManager.class);
    PsiClass parentClass = javaFileManager.findClass(parentName, scope);
    if (null != parentClass) {
      if (PsiAnnotationUtil.isAnnotatedWith(parentClass, Builder.class, lombok.experimental.Builder.class)) {
        ContainerUtil.addIfNotNull(result, parentClass.findInnerClassByName(shortName, false));
      }
    }
    //PsiShortNamesCache.getInstance(project).getClassesByName(name, scope)
    return result.isEmpty() ? null : result.get(0);
//    return result.isEmpty() ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }
}
