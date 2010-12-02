package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class PyDunderAllReference extends PsiReferenceBase<PyStringLiteralExpression> {
  public PyDunderAllReference(@NotNull PyStringLiteralExpression element) {
    super(element);
    final List<TextRange> ranges = element.getStringValueTextRanges();
    if (ranges.size() > 0) {
      setRangeInElement(ranges.get(0));
    }
  }

  @Override
  public PsiElement resolve() {
    final PyStringLiteralExpression element = getElement();
    final String name = element.getStringValue();
    PyFile containingFile = (PyFile) element.getContainingFile();
    return containingFile.getElementNamed(name);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
