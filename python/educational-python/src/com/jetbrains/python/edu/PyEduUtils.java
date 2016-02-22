package com.jetbrains.python.edu;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PyEduUtils {
  private PyEduUtils() {
  }

  public static List<RatedResolveResult> getResolveResultFromContainingDirectory(@NotNull PyQualifiedExpression element) {
    final List<RatedResolveResult> result = new ArrayList<RatedResolveResult>();
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return result;
    final PsiDirectory directory = containingFile.getContainingDirectory();
    if (directory == null) return result;
    final String elementName = element.getName();
    final PsiFile file = directory.findFile(elementName + ".py");
    if (file != null) {
      result.add(new ImportedResolveResult(file, RatedResolveResult.RATE_NORMAL, null));
    }
    return result;
  }
}
