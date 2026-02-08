package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyLiteralPattern;
import com.jetbrains.python.psi.PyPlainStringElement;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyExpectedTypeJudgement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
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
    PyType expectedType = PyExpectedTypeJudgement.getExpectedType(stringLiteral, typeEvalContext);
    return templateType.equals(expectedType);
  }
}
