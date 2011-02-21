package com.jetbrains.python.codeInsight.regexp;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.*;
import org.intellij.lang.regexp.RegExpLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonRegexpInjector implements LanguageInjector {
  private static class RegexpMethodDescriptor {
    private final String methodName;
    private final int argIndex;

    private RegexpMethodDescriptor(String methodName, int argIndex) {
      this.methodName = methodName;
      this.argIndex = argIndex;
    }
  }

  private final List<RegexpMethodDescriptor> myDescriptors = new ArrayList<RegexpMethodDescriptor>();

  public PythonRegexpInjector() {
    addMethod("compile");
    addMethod("search");
    addMethod("match");
    addMethod("split");
    addMethod("findall");
    addMethod("finditer");
    addMethod("sub");
    addMethod("subn");
  }

  private void addMethod(final String name) {
    myDescriptors.add(new RegexpMethodDescriptor(name, 0));
  }

  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (host instanceof PyStringLiteralExpression && host.getParent() instanceof PyArgumentList) {
      final PyExpression[] args = ((PyArgumentList)host.getParent()).getArguments();
      int index = ArrayUtil.indexOf(args, host);
      PyCallExpression call = PsiTreeUtil.getParentOfType(host, PyCallExpression.class);
      if (call != null) {
        final PyExpression callee = call.getCallee();
        if (callee instanceof PyReferenceExpression && canBeRegexpCall(callee)) {
          final PsiElement element = ((PyReferenceExpression)callee).getReference().resolve();
          if (element != null && element.getContainingFile().getName().equals("re.py") && isRegexpMethod(element, index)) {
            List<TextRange> ranges = ((PyStringLiteralExpression)host).getStringValueTextRanges();
            if (ranges.size() == 1) {
              injectionPlacesRegistrar.addPlace(PythonRegexpLanguage.INSTANCE, ranges.get(0), null, null);
            }
          }
        }
      }
    }
  }

  private boolean isRegexpMethod(PsiElement element, int index) {
    if (!(element instanceof PyFunction)) {
      return false;
    }
    final String name = ((PyFunction)element).getName();
    for (RegexpMethodDescriptor descriptor : myDescriptors) {
      if (descriptor.methodName.equals(name) && descriptor.argIndex == index) {
        return true;
      }
    }
    return false;
  }

  private boolean canBeRegexpCall(PyExpression callee) {
    String text = callee.getText();
    for (RegexpMethodDescriptor descriptor : myDescriptors) {
      if (text.endsWith(descriptor.methodName)) {
        return true;
      }
    }
    return false;
  }
}
