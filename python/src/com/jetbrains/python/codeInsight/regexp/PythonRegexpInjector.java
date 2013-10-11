package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class PythonRegexpInjector implements MultiHostInjector {
  private static class RegexpMethodDescriptor {
    @NotNull private final String methodName;
    private final int argIndex;

    private RegexpMethodDescriptor(@NotNull String methodName, int argIndex) {
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

  private void addMethod(@NotNull String name) {
    myDescriptors.add(new RegexpMethodDescriptor(name, 0));
  }

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final PsiElement contextParent = context.getParent();
    if (isStringLiteral(context) && contextParent instanceof PyArgumentList) {
      final PyExpression[] args = ((PyArgumentList)contextParent).getArguments();
      int index = ArrayUtil.indexOf(args, context);
      PyCallExpression call = PsiTreeUtil.getParentOfType(context, PyCallExpression.class);
      if (call != null) {
        final PyExpression callee = call.getCallee();
        if (callee instanceof PyReferenceExpression && canBeRegexpCall(callee)) {
          final PsiPolyVariantReference ref = ((PyReferenceExpression)callee).getReference(PyResolveContext.noImplicits());
          if (ref != null) {
            final PsiElement element = ref.resolve();
            if (element != null && element.getContainingFile().getName().equals("re.py") && isRegexpMethod(element, index)) {
              final Language language = isVerbose(call) ? PythonVerboseRegexpLanguage.INSTANCE : PythonRegexpLanguage.INSTANCE;
              registrar.startInjecting(language);
              processStringLiteral(context, registrar);
              registrar.doneInjecting();
            }
          }
        }
      }
    }

  }

  private static boolean isStringLiteral(@NotNull PsiElement element) {
    if (element instanceof PyStringLiteralExpression) {
      return true;
    }
    else if (element instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      return contained != null && isStringLiteral(contained);
    }
    else if (element instanceof PyBinaryExpression) {
      final PyBinaryExpression expr = (PyBinaryExpression)element;
      final PyExpression left = expr.getLeftExpression();
      final PyExpression right = expr.getRightExpression();
      return expr.isOperator("+") && (isStringLiteral(left) || right != null && isStringLiteral(right));
    }
    return false;
  }

  private static void processStringLiteral(@NotNull PsiElement element, @NotNull MultiHostRegistrar registrar) {
    processStringLiteral(element, registrar, "", "");
  }

  private static void processStringLiteral(@NotNull PsiElement element, @NotNull MultiHostRegistrar registrar, @NotNull String prefix,
                                           @NotNull String suffix) {
    final String missingValue = "missing";
    if (element instanceof PyStringLiteralExpression) {
      final PyStringLiteralExpression expr = (PyStringLiteralExpression)element;
      final List<TextRange> ranges = expr.getStringValueTextRanges();
      for (TextRange range : ranges) {
        registrar.addPlace(prefix, suffix, expr, range);
      }
    }
    else if (element instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      if (contained != null) {
        processStringLiteral(contained, registrar, prefix, suffix);
      }
    }
    else if (element instanceof PyBinaryExpression) {
      final PyBinaryExpression expr = (PyBinaryExpression)element;
      if (expr.isOperator("+")) {
        final PyExpression left = expr.getLeftExpression();
        final PyExpression right = expr.getRightExpression();
        final boolean isLeftString = isStringLiteral(left);
        final boolean isRightString = right != null && isStringLiteral(right);
        if (isLeftString) {
          processStringLiteral(left, registrar, prefix, isRightString ? "" : missingValue);
        }
        if (isRightString) {
          processStringLiteral(right, registrar, isLeftString ? "" : missingValue, suffix);
        }
      }
    }
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(PyStringLiteralExpression.class, PyParenthesizedExpression.class, PyBinaryExpression.class);
  }

  private static boolean isVerbose(@NotNull PyCallExpression call) {
    PyExpression[] arguments = call.getArguments();
    if (arguments.length <= 1) {
      return false;
    }
    return isVerbose(arguments[arguments.length-1]);
  }

  private static boolean isVerbose(@Nullable PyExpression expr) {
    if (expr instanceof PyKeywordArgument) {
      PyKeywordArgument keywordArgument = (PyKeywordArgument)expr;
      if (!"flags".equals(keywordArgument.getName())) {
        return false;
      }
      return isVerbose(keywordArgument.getValueExpression());
    }
    if (expr instanceof PyReferenceExpression) {
      return "VERBOSE".equals(((PyReferenceExpression)expr).getReferencedName());
    }
    if (expr instanceof PyBinaryExpression) {
      return isVerbose(((PyBinaryExpression)expr).getLeftExpression()) || isVerbose(((PyBinaryExpression)expr).getRightExpression());
    }
    return false;
  }

  private boolean isRegexpMethod(@NotNull PsiElement element, int index) {
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

  private boolean canBeRegexpCall(@NotNull PyExpression callee) {
    String text = callee.getText();
    for (RegexpMethodDescriptor descriptor : myDescriptors) {
      if (text.endsWith(descriptor.methodName)) {
        return true;
      }
    }
    return false;
  }
}
