package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Provides completion results after "{" in ordinary non-formatted string literals as if that literal
 * was a proper f-string and adds a missing "f" prefix and a closing brace on selection of such a variant.
 * <p>
 * For instance, in the next fragment
 * <pre><code>
 * for line in f:
 *     print('# {li&lt;caret&gt;')
 * </code></pre>
 * selecting "line" from the list of completion suggestions will automatically transform it into
 * <pre><code>
 * for line in f:
 *     print(f'# {line&lt;caret&gt;}')
 * </code></pre>
 */
public final class PyFStringLikeCompletionContributor extends CompletionContributor implements DumbAware {

  private static final PsiElementPattern.Capture<PyPlainStringElement> APPLICABLE_STRING_ELEMENT =
    psiElement(PyPlainStringElement.class)
      .withParent(PyStringLiteralExpression.class)
      .andNot(psiElement().withSuperParent(2, PyLiteralPattern.class))
      .andNot(psiElement().inside(PyStringFormatCompletionContributor.FORMAT_STRING_CAPTURE));

  public PyFStringLikeCompletionContributor() {
    extend(CompletionType.BASIC, APPLICABLE_STRING_ELEMENT, new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        PyPlainStringElement stringElem = as(parameters.getPosition(), PyPlainStringElement.class);
        if (stringElem == null || stringElem.isBytes() || stringElem.isUnicode()) {
          return;
        }
        if (LanguageLevel.forElement(stringElem).isOlderThan(LanguageLevel.PYTHON36)) {
          return;
        }
        PyStringLiteralExpression stringLiteral = (PyStringLiteralExpression)stringElem.getParent();
        boolean templateStringExpected = isTemplateStringExpected(stringLiteral);
        if (templateStringExpected && LanguageLevel.forElement(stringElem).isOlderThan(LanguageLevel.PYTHON314)) {
          return;
        }
        String newPrefixChar = templateStringExpected ? "t" : "f";
        String stringElemText = stringElem.getText();
        int offset = parameters.getOffset();
        int stringElemStart = stringElem.getTextRange().getStartOffset();
        int relOffset = offset - stringElemStart;
        int braceOffset = CharArrayUtil.shiftBackwardUntil(stringElemText, relOffset - 1, "{");
        if (braceOffset < 0) {
          return;
        }
        String completionPrefix = stringElemText.substring(braceOffset + 1, relOffset);
        boolean autoPopupAfterOpeningBrace = completionPrefix.isEmpty() && parameters.isAutoPopup();
        if (autoPopupAfterOpeningBrace) {
          return;
        }
        boolean prefixCannotStartReference = !completionPrefix.isEmpty() && !PyNames.isIdentifier(completionPrefix);
        if (prefixCannotStartReference) {
          return;
        }

        String fStringText = new StringBuilder()
          .append(newPrefixChar).append(stringElemText)
          .insert(relOffset + 1 + CompletionUtilCore.DUMMY_IDENTIFIER.length(), "} ")
          .toString();
        PyExpression fString = PyUtil.createExpressionFromFragment(fStringText, stringLiteral.getParent());
        assert fString != null;
        PsiReference reference = fString.findReferenceAt(relOffset + 1);
        if (reference == null) {
          return;
        }
        List<@NotNull LookupElement> fStringVariants = ContainerUtil.mapNotNull(reference.getVariants(), v -> as(v, LookupElement.class));
        if (fStringVariants.isEmpty()) {
          return;
        }
        CompletionResultSet prefixPatchedResultSet = result.withPrefixMatcher(completionPrefix);
        for (LookupElement variant : fStringVariants) {
          prefixPatchedResultSet.addElement(new LookupElementDecorator<>(variant) {
            @Override
            public void handleInsert(@NotNull InsertionContext context) {
              super.handleInsert(context);
              Document document = context.getDocument();
              CharSequence docChars = document.getCharsSequence();
              int tailOffset = context.getTailOffset();
              if (!(tailOffset < document.getTextLength() && docChars.charAt(tailOffset) == '}')) {
                document.insertString(tailOffset, "}");
              }
              // It can happen when completion is invoked on multiple carets inside the same string
              String stringElemPrefix = PyStringLiteralCoreUtil.getPrefix(docChars, stringElemStart);
              if (!StringUtil.containsIgnoreCase(stringElemPrefix, newPrefixChar)) {
                document.insertString(stringElemStart, newPrefixChar);
              }
            }
          });
        }
      }
    });
  }

  private static boolean isTemplateStringExpected(@NotNull PyStringLiteralExpression stringLiteral) {
    TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(stringLiteral.getProject(), stringLiteral.getContainingFile());
    PyPsiFacade psiFacade = PyPsiFacade.getInstance(stringLiteral.getProject());
    PyClass templateClass = psiFacade.createClassByQName(PyNames.TEMPLATELIB_TEMPLATE, stringLiteral);
    if (templateClass == null) {
      return false;
    }
    PyClassType templateType = psiFacade.createClassType(templateClass, false);
    return isArgumentOfFunctionExpectingTemplateString(stringLiteral, templateType, typeEvalContext) ||
           isAssignedToVariableExpectingTemplateString(stringLiteral, templateType, typeEvalContext);
  }

  private static boolean isAssignedToVariableExpectingTemplateString(@NotNull PyStringLiteralExpression literal,
                                                                     @NotNull PyClassType templateType,
                                                                     @NotNull TypeEvalContext typeEvalContext) {
    PsiElement unpackedValueParent = PsiTreeUtil.skipParentsOfType(literal, PyParenthesizedExpression.class, PyTupleExpression.class);
    if (!(unpackedValueParent instanceof PyAssignmentStatement assignment)) {
      return false;
    }
    List<Pair<PyExpression, PyExpression>> mapping = assignment.getTargetsToValuesMapping();
    Pair<PyExpression, PyExpression> matchingPair = ContainerUtil.find(mapping, pair -> pair.getSecond() == literal);
    if (matchingPair == null || !(matchingPair.getFirst() instanceof PyTargetExpression target)) {
      return false;
    }
    return templateType.equals(typeEvalContext.getType(target));
  }

  private static boolean isArgumentOfFunctionExpectingTemplateString(@NotNull PyStringLiteralExpression stringLiteral,
                                                                     @NotNull PyClassType templateType,
                                                                     @NotNull TypeEvalContext typeEvalContext) {
    PsiElement callArgument = stringLiteral.getParent() instanceof PyKeywordArgument kwArg ? kwArg : stringLiteral;
    if (!(callArgument.getParent() instanceof PyArgumentList argumentList) ||
        !(argumentList.getParent() instanceof PyCallExpression call)) {
      return false;
    }
    return ContainerUtil.all(call.multiMapArguments(PyResolveContext.defaultContext(typeEvalContext)), mapping -> {
      PyCallableParameter param = mapping.getMappedParameters().get(callArgument);
      if (param == null) return false;
      PyType paramType = param.getType(typeEvalContext);
      if (param.isPositionalContainer() && paramType instanceof PyTupleType tupleType && tupleType.isHomogeneous()) {
        paramType = tupleType.getElementTypes().get(0);
      }
      else if (param.isKeywordContainer() && paramType instanceof PyCollectionType dictType &&
               PyNames.DICT.equals(dictType.getPyClass().getName())) {
        paramType = ContainerUtil.getOrElse(dictType.getElementTypes(), 1, null);
      }
      return templateType.equals(paramType);
    });
  }
}
