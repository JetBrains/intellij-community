package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LombokElementFinder extends PsiElementFinder {

  private final JavaFileManager myFileManager;
  private final Project myProject;

  public LombokElementFinder(Project project) {
    myFileManager = JavaFileManager.getInstance(project);
    myProject = project;
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (!LombokLibraryUtil.hasLombokLibrary(myProject)) {
      return null;
    }

    final int lastDot = qualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return null;
    }

    final String parentName = qualifiedName.substring(0, lastDot);
    final String shortName = qualifiedName.substring(lastDot + 1);

    if (shortName.isEmpty() || parentName.isEmpty()) {
      return null;
    }

    final PsiClass parentClass = myFileManager.findClass(parentName, scope);
    if (null != parentClass) {
      return parentClass.findInnerClassByName(shortName, false);
    }

    return null;
  }

  @Override
  public PsiClass @NotNull [] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }
}
