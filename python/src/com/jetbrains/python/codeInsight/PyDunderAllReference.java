package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.LightNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
    final List<LookupElement> result = new ArrayList<LookupElement>();
    PyFile containingFile = (PyFile) getElement().getContainingFile().getOriginalFile();
    final List<String> dunderAll = containingFile.getDunderAll();
    containingFile.processDeclarations(new PsiScopeProcessor() {
      @Override
      public boolean execute(PsiElement element, ResolveState state) {
        if (element instanceof PsiNamedElement && !(element instanceof LightNamedElement)) {
          final String name = ((PsiNamedElement)element).getName();
          if (name != null && PyUtil.getInitialUnderscores(name) == 0 && (dunderAll == null || !dunderAll.contains(name))) {
            result.add(LookupElementBuilder.create((PsiNamedElement) element).setIcon(element.getIcon(Iconable.ICON_FLAG_CLOSED)));
          }
        }
        else if (element instanceof PyImportElement) {
          final String visibleName = ((PyImportElement)element).getVisibleName();
          if (visibleName != null && (dunderAll == null || !dunderAll.contains(visibleName))) {
            result.add(LookupElementBuilder.create(element, visibleName));
          }
        }
        return true;
      }

      @Override
      public <T> T getHint(Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(Event event, @Nullable Object associated) {
      }
    }, ResolveState.initial(), null, containingFile);
    return ArrayUtil.toObjectArray(result);
  }
}
