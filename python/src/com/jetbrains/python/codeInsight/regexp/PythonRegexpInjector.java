package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.PyStringFormatParser;
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
    final PsiElement parent = element.getParent();
    return isStringLiteralPart(element) && (parent == null || !isStringLiteralPart(parent));
  }

  private static boolean isStringLiteralPart(@NotNull PsiElement element) {
    if (element instanceof PyStringLiteralExpression) {
      return true;
    }
    else if (element instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      return contained != null && isStringLiteralPart(contained);
    }
    else if (element instanceof PyBinaryExpression) {
      final PyBinaryExpression expr = (PyBinaryExpression)element;
      final PyExpression left = expr.getLeftExpression();
      final PyExpression right = expr.getRightExpression();
      return (expr.isOperator("+") && (isStringLiteralPart(left) || right != null && isStringLiteralPart(right))) ||
              expr.isOperator("%") && isStringLiteralPart(left);
    }
    else if (element instanceof PyCallExpression) {
      final PyExpression qualifier = getFormatCallQualifier((PyCallExpression)element);
      return qualifier != null && isStringLiteralPart(qualifier);
    }
    return false;
  }

  @Nullable
  private static PyExpression getFormatCallQualifier(@NotNull PyCallExpression element) {
    final PyExpression callee = element.getCallee();
    if (callee instanceof PyQualifiedExpression) {
      final PyQualifiedExpression qualifiedExpr = (PyQualifiedExpression)callee;
      final PyExpression qualifier = qualifiedExpr.getQualifier();
      if (qualifier != null && PyNames.FORMAT.equals(qualifiedExpr.getReferencedName())) {
        return qualifier;
      }
    }
    return null;
  }

  private enum Formatting {
    NONE,
    PERCENT,
    NEW_STYLE
  }

  private static void processStringLiteral(@NotNull PsiElement element, @NotNull MultiHostRegistrar registrar) {
    processStringLiteral(element, registrar, "", "", Formatting.NONE);
  }

  private static void processStringLiteral(@NotNull PsiElement element, @NotNull MultiHostRegistrar registrar, @NotNull String prefix,
                                           @NotNull String suffix, @NotNull Formatting formatting) {
    final String missingValue = "missing";
    if (element instanceof PyStringLiteralExpression) {
      final PyStringLiteralExpression expr = (PyStringLiteralExpression)element;
      final List<TextRange> ranges = expr.getStringValueTextRanges();
      final String text = expr.getText();
      for (TextRange range : ranges) {
        if (formatting != Formatting.NONE) {
          final String part = range.substring(text);
          final List<PyStringFormatParser.FormatStringChunk> chunks = formatting == Formatting.NEW_STYLE ?
                                                                      PyStringFormatParser.parseNewStyleFormat(part) :
                                                                      PyStringFormatParser.parsePercentFormat(part);
          for (int i = 0; i < chunks.size(); i++) {
            final PyStringFormatParser.FormatStringChunk chunk = chunks.get(i);
            if (chunk instanceof PyStringFormatParser.ConstantChunk) {
              final int nextIndex = i + 1;
              final String chunkPrefix = i == 1 && chunks.get(0) instanceof PyStringFormatParser.SubstitutionChunk ? missingValue : "";
              final String chunkSuffix = nextIndex < chunks.size() &&
                                         chunks.get(nextIndex) instanceof PyStringFormatParser.SubstitutionChunk ? missingValue : "";
              final TextRange chunkRange = chunk.getTextRange().shiftRight(range.getStartOffset());
              registrar.addPlace(chunkPrefix, chunkSuffix, expr, chunkRange);
            }
          }
        }
        else {
          registrar.addPlace(prefix, suffix, expr, range);
        }
      }
    }
    else if (element instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      if (contained != null) {
        processStringLiteral(contained, registrar, prefix, suffix, formatting);
      }
    }
    else if (element instanceof PyBinaryExpression) {
      final PyBinaryExpression expr = (PyBinaryExpression)element;
      final PyExpression left = expr.getLeftExpression();
      final PyExpression right = expr.getRightExpression();
      final boolean isLeftString = isStringLiteralPart(left);
      if (expr.isOperator("+")) {
        final boolean isRightString = right != null && isStringLiteralPart(right);
        if (isLeftString) {
          processStringLiteral(left, registrar, prefix, isRightString ? "" : missingValue, formatting);
        }
        if (isRightString) {
          processStringLiteral(right, registrar, isLeftString ? "" : missingValue, suffix, formatting);
        }
      }
      else if (expr.isOperator("%")) {
        processStringLiteral(left, registrar, prefix, suffix, Formatting.PERCENT);
      }
    }
    else if (element instanceof PyCallExpression) {
      final PyExpression qualifier = getFormatCallQualifier((PyCallExpression)element);
      if (qualifier != null) {
        processStringLiteral(qualifier, registrar, prefix, suffix, Formatting.NEW_STYLE);
      }
    }
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(PyStringLiteralExpression.class, PyParenthesizedExpression.class, PyBinaryExpression.class,
                         PyCallExpression.class);
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
