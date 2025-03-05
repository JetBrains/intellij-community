/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.intentions.convertToFString;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyStringFormatParser;
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyUtil.StringNodeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public abstract class BaseConvertToFStringProcessor<T extends PyStringFormatParser.SubstitutionChunk> {
  protected final PyStringLiteralExpression myPyString;
  protected final StringNodeInfo myNodeInfo;

  protected BaseConvertToFStringProcessor(@NotNull PyStringLiteralExpression pyString) {
    myPyString = pyString;
    myNodeInfo = new StringNodeInfo(pyString.getStringNodes().get(0));
  }

  public final boolean isRefactoringAvailable() {
    // TODO support glued/concatenated string literal with multiple nodes
    if (myPyString.getStringNodes().size() > 1 || myNodeInfo.isBytes() || myNodeInfo.isFormatted()) return false;

    final PsiElement valuesSource = getValuesSource();
    if (valuesSource == null) return false;
    final List<T> chunks = extractAllSubstitutionChunks();
    for (T chunk : chunks) {
      if (!checkChunk(chunk)) return false;
      final PySubstitutionChunkReference reference = createReference(chunk);
      final PyExpression referencedExpr = adjustResolveResult(reference.resolve());
      if (referencedExpr == null) return false;
      if (!PsiTreeUtil.isAncestor(valuesSource, referencedExpr, false)) return false;
      if (referencedExpr instanceof PyStarExpression || referencedExpr instanceof PyStarArgument) return false;
      if (!checkReferencedExpression(chunks, chunk, valuesSource, referencedExpr)) return false;
    }
    return true;
  }

  protected boolean checkReferencedExpression(@NotNull List<T> chunks,
                                              @NotNull T chunk,
                                              @NotNull PsiElement valueSource,
                                              @NotNull PyExpression expression) {
    if (expression.textContains('\\') || expression.textContains('\n')) return false;
    return adjustQuotesInsideInjectedExpression(expression) != null;
  }

  public final void doRefactoring() {
    final String stringText = myPyString.getText();
    final StringBuilder fStringText = new StringBuilder();
    fStringText.append("f");
    fStringText.append(StringUtil.replaceIgnoreCase(myNodeInfo.getPrefix(), "u", ""));
    fStringText.append(myNodeInfo.getQuote());

    final TextRange contentRange = myNodeInfo.getContentRange();
    int offset = contentRange.getStartOffset();

    for (final T chunk : extractTopLevelSubstitutionChunks()) {
      processLiteralChunk(stringText.substring(offset, chunk.getStartIndex()), fStringText);
      if (!processSubstitutionChunk(chunk, fStringText)) {
        return;
      }
      offset = chunk.getEndIndex();
    }

    if (offset < contentRange.getEndOffset()) {
      processLiteralChunk(stringText.substring(offset, contentRange.getEndOffset()), fStringText);
    }

    fStringText.append(myNodeInfo.getQuote());

    final PyExpression expressionToReplace = getWholeExpressionToReplace();

    final PyElementGenerator generator = PyElementGenerator.getInstance(myPyString.getProject());
    final PyExpression fString = generator.createExpressionFromText(LanguageLevel.forElement(myPyString), fStringText.toString());
    expressionToReplace.replace(fString);
  }

  protected abstract @NotNull List<T> extractAllSubstitutionChunks();

  protected @NotNull List<T> extractTopLevelSubstitutionChunks() {
    return extractAllSubstitutionChunks();
  }

  protected abstract @NotNull PySubstitutionChunkReference createReference(@NotNull T chunk);

  protected abstract boolean checkChunk(@NotNull T chunk);

  protected abstract boolean processSubstitutionChunk(@NotNull T chunk, @NotNull StringBuilder fStringText);

  protected abstract void processLiteralChunk(@NotNull String chunk, @NotNull StringBuilder fStringText);

  protected @Nullable PsiElement adjustQuotesInsideInjectedExpression(@NotNull PsiElement expression) {
    final PsiElement copied = expression.copy();

    final char hostQuote = myNodeInfo.getSingleQuote();
    final PyElementGenerator generator = PyElementGenerator.getInstance(myPyString.getProject());

    final Collection<PyStringLiteralExpression> innerStrings = PsiTreeUtil.collectElementsOfType(copied, PyStringLiteralExpression.class);
    for (PyStringLiteralExpression literal : innerStrings) {
      final List<ASTNode> nodes = literal.getStringNodes();
      // TODO figure out what to do with those
      if (nodes.size() > 1) {
        return copied;
      }
      final StringNodeInfo info = new StringNodeInfo(nodes.get(0));
      // Nest string contain the same type of quote as host string inside, and we cannot escape inside f-string -- retreat
      final String content = info.getContent();
      if (content.indexOf(hostQuote) >= 0) {
        return null;
      }
      if (!info.isTerminated()) {
        return null;
      }
      if (info.getQuote().startsWith(myNodeInfo.getQuote())) {
        final char targetSingleQuote = PyStringLiteralUtil.flipQuote(hostQuote);
        if (content.indexOf(targetSingleQuote) >= 0) {
          return null;
        }

        final String targetQuote = info.getQuote().replace(hostQuote, targetSingleQuote);
        final String stringWithSwappedQuotes = info.getPrefix() + targetQuote + content + targetQuote;
        final PsiElement replaced = literal.replace(generator.createStringLiteralAlreadyEscaped(stringWithSwappedQuotes));
        if (literal == copied) {
          return replaced;
        }
      }
    }
    return copied;
  }

  protected abstract @NotNull PyExpression getWholeExpressionToReplace();

  protected abstract @Nullable PsiElement getValuesSource();

  protected @Nullable PsiElement prepareExpressionToInject(@NotNull PyExpression expression, @NotNull T chunk) {
    final PsiElement quoted = adjustQuotesInsideInjectedExpression(expression);
    if (quoted == null) return null;

    if (quoted instanceof PyLambdaExpression) {
      return wrapExpressionInParentheses(quoted);
    }
    return quoted;
  }

  protected final @Nullable PsiElement wrapExpressionInParentheses(@NotNull PsiElement expression) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(myPyString.getProject());
    return generator.createExpressionFromText(LanguageLevel.forElement(myPyString), "(" + expression.getText() + ")");
  }

  protected static @Nullable PyExpression adjustResolveResult(@Nullable PsiElement resolveResult) {
    if (resolveResult == null) return null;
    final PyKeywordArgument argument = as(resolveResult, PyKeywordArgument.class);
    if (argument != null) {
      return argument.getValueExpression();
    }
    final PyKeyValueExpression parent = as(resolveResult.getParent(), PyKeyValueExpression.class);
    if (parent != null && parent.getKey() == resolveResult) {
      return parent.getValue();
    }
    return as(resolveResult, PyExpression.class);
  }
}
