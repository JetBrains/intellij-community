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
package com.jetbrains.python.codeInsight.intentions;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.codeInsight.PythonFormattedStringReferenceProvider;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.inspections.PyStringFormatParser.ConstantChunk;
import com.jetbrains.python.inspections.PyStringFormatParser.FormatStringChunk;
import com.jetbrains.python.inspections.PyStringFormatParser.SubstitutionChunk;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyUtil.StringNodeInfo;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyConvertToFStringIntention extends PyBaseIntentionAction {
  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.to.fstring.literal");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile) || !LanguageLevel.forElement(file).isAtLeast(LanguageLevel.PYTHON36)) return false;

    final Pair<PyStringLiteralExpression, Boolean> pair = findTargetStringUnderCaret(editor, file);
    if (pair == null) return false;

    final PyStringLiteralExpression pyString = pair.getFirst();
    final boolean percentOperator = pair.getSecond();

    // TODO handle "glued" literals
    if (pyString != null && pyString.getStringNodes().size() == 1) {
      final String stringText = pyString.getText();
      final String prefix = PyStringLiteralUtil.getPrefix(stringText);
      if (PyStringLiteralUtil.isBytesPrefix(prefix) || PyStringLiteralUtil.isFormattedPrefix(prefix)) {
        return false;
      }

      final List<FormatStringChunk> chunks = percentOperator ? PyStringFormatParser.parsePercentFormat(stringText)
                                                             : PyStringFormatParser.parseNewStyleFormat(stringText);
      final List<SubstitutionChunk> substitutions = PyStringFormatParser.filterSubstitutions(chunks);

      // TODO handle dynamic format spec in both formatting styles
      final boolean hasDynamicFormatting;
      if (percentOperator) {
        hasDynamicFormatting = substitutions.stream().anyMatch(s -> "*".equals(s.getWidth()) || "*".equals(s.getPrecision()));
      }
      else {
        hasDynamicFormatting = false;
      }
      if (hasDynamicFormatting) return false;

      final PySubstitutionChunkReference[] references =
        PythonFormattedStringReferenceProvider.getReferencesFromChunks(pyString, substitutions, percentOperator);

      final PsiElement valuesSource;
      if (percentOperator) {
        final PyBinaryExpression binaryExpression = as(pyString.getParent(), PyBinaryExpression.class);
        assert binaryExpression != null;
        valuesSource = binaryExpression.getRightExpression();
      }
      else {
        final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(pyString, PyCallExpression.class);
        assert callExpression != null;
        valuesSource = callExpression.getArgumentList();
      }

      return Arrays.stream(references)
        .map(PyConvertToFStringIntention::getActualReplacementExpression)
        .allMatch(element -> element != null &&
                             !(element instanceof PyStarExpression) &&
                             !(element instanceof PyStarArgument) &&
                             PsiTreeUtil.isAncestor(valuesSource, element, false) &&
                             expressionCanBeInlined(pyString, element));
    }
    return false;
  }

  private static boolean expressionCanBeInlined(@NotNull PyStringLiteralExpression host, @NotNull PyExpression target) {
    // Cannot inline multi-line expressions or expressions that contains backslashes (yet)
    if (target.textContains('\\') || target.textContains('\n')) return false;
    return adjustQuotesInside((PyExpression)target.copy(), host) != null;
  }

  @Nullable
  private static PsiElement adjustQuotesInside(@NotNull PyExpression element, @NotNull PyStringLiteralExpression host) {
    final StringNodeInfo hostInfo = new StringNodeInfo(host.getStringNodes().get(0));
    final char hostQuote = hostInfo.getSingleQuote();
    final PyElementGenerator generator = PyElementGenerator.getInstance(host.getProject());

    final Collection<PyStringLiteralExpression> innerStrings = PsiTreeUtil.collectElementsOfType(element, PyStringLiteralExpression.class);
    for (PyStringLiteralExpression literal : innerStrings) {
      final List<ASTNode> nodes = literal.getStringNodes();
      // TODO figure out what to do with those
      if (nodes.size() > 1) {
        return null;
      }
      final StringNodeInfo info = new StringNodeInfo(nodes.get(0));
      // Nest string contain the same type of quote as host string inside, and we cannot escape inside f-string -- retreat
      final String content = info.getContent();
      final char targetSingleQuote = invertQuote(hostQuote);
      if (content.indexOf(hostQuote) >= 0 || content.indexOf(targetSingleQuote) >= 0) {
        return null;
      }
      if (!info.isTerminated()) {
        return null;
      }
      if (info.getSingleQuote() == hostQuote) {
        final String targetQuote = info.getQuote().replace(hostQuote, targetSingleQuote);
        final String stringWithSwappedQuotes = info.getPrefix() + targetQuote + content + targetQuote;
        final PsiElement replaced = literal.replace(generator.createStringLiteralAlreadyEscaped(stringWithSwappedQuotes));
        if (literal == element) {
          return replaced;
        }
      }
    }
    return element;
  }

  @Nullable
  private static Pair<PyStringLiteralExpression, Boolean> findTargetStringUnderCaret(@NotNull Editor editor, @NotNull PsiFile file) {
    final PsiElement anchor = file.findElementAt(editor.getCaretModel().getOffset());
    if (anchor == null) return null;

    final PyBinaryExpression binaryExpr = PsiTreeUtil.getParentOfType(anchor, PyBinaryExpression.class);
    if (binaryExpr != null && binaryExpr.getOperator() == PyTokenTypes.PERC) {
      final PyStringLiteralExpression pyString = as(binaryExpr.getLeftExpression(), PyStringLiteralExpression.class);
      if (pyString != null) {
        return Pair.create(pyString, true);
      }
    }
    final PyCallExpression callExpr = PsiTreeUtil.getParentOfType(anchor, PyCallExpression.class);
    if (callExpr != null) {
      final PyReferenceExpression callee = as(callExpr.getCallee(), PyReferenceExpression.class);
      if (callee != null) {
        final PyStringLiteralExpression pyString = as(callee.getQualifier(), PyStringLiteralExpression.class);
        if (pyString != null) {
          return Pair.create(pyString, false);
        }
      }
    }
    return null;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final Pair<PyStringLiteralExpression, Boolean> pair = findTargetStringUnderCaret(editor, file);
    assert pair != null;
    final Boolean percentOperator = pair.getSecond();
    if (percentOperator) {
      convertPercentOperatorFormatting(pair.getFirst());
    }
    else {
      convertFormatMethodFormatting(pair.getFirst());
    }
  }

  private static void convertPercentOperatorFormatting(@NotNull PyStringLiteralExpression pyString) {
    final String stringText = pyString.getText();
    final Pair<String, String> quotes = PyStringLiteralUtil.getQuotes(stringText);
    assert quotes != null;
    final StringBuilder result = new StringBuilder();
    result.append("f");
    result.append(quotes.getFirst().replaceAll("[uU]", ""));
    final List<FormatStringChunk> chunks = PyStringFormatParser.parsePercentFormat(stringText);
    final TextRange contentRange = PyStringLiteralExpressionImpl.getNodeTextRange(stringText);
    int subsChunkPosition = 0;
    for (FormatStringChunk chunk : chunks) {
      if (chunk instanceof ConstantChunk) {
        final TextRange rangeWithoutQuotes = chunk.getTextRange().intersection(contentRange);
        assert rangeWithoutQuotes != null;
        result.append(rangeWithoutQuotes.substring(stringText));
      }
      else {
        final SubstitutionChunk subsChunk = (SubstitutionChunk)chunk;
        final char conversionChar = subsChunk.getConversionType();
        final String widthAndPrecision;
        if (StringUtil.isNotEmpty(subsChunk.getWidth()) || StringUtil.isNotEmpty(subsChunk.getPrecision())) {
          widthAndPrecision = StringUtil.notNullize(subsChunk.getWidth()) + "." + StringUtil.notNullize(subsChunk.getPrecision());
        }
        else {
          widthAndPrecision = "";
        }
        final String conversionFlags = subsChunk.getConversionFlags();

        result.append("{");
        final PySubstitutionChunkReference reference = new PySubstitutionChunkReference(pyString, subsChunk, subsChunkPosition, true);
        final PyExpression resolveResult = getActualReplacementExpression(reference);
        assert resolveResult != null;

        final PsiElement adjusted = adjustQuotesInside(resolveResult, pyString);
        if (adjusted == null) return;

        result.append(adjusted.getText());

        // TODO mostly duplicates the logic of ConvertFormatOperatorToMethodIntention
        if (conversionChar == 'r') {
          result.append("!r");
        }

        if ((conversionChar != 'r' && conversionChar != 's')
            || StringUtil.isNotEmpty(conversionFlags)
            || StringUtil.isNotEmpty(widthAndPrecision)) {
          result.append(":");
        }

        if (StringUtil.isNotEmpty(conversionFlags)) {
          final String conversionStr = String.valueOf(conversionChar);
          result.append(ConvertFormatOperatorToMethodIntention.convertFormatSpec(conversionFlags, widthAndPrecision, conversionStr));
        }

        if (StringUtil.isNotEmpty(widthAndPrecision)) {
          result.append(widthAndPrecision);
        }

        if ('i' == conversionChar || 'u' == conversionChar) {
          result.append("d");
        }
        else if ('s' != conversionChar && 'r' != conversionChar) {
          result.append(conversionChar);
        }
        result.append("}");
        subsChunkPosition++;
      }
    }
    result.append(quotes.getSecond());

    final PyBinaryExpression expressionToReplace = PsiTreeUtil.getParentOfType(pyString, PyBinaryExpression.class);
    assert expressionToReplace != null;

    final PyElementGenerator generator = PyElementGenerator.getInstance(pyString.getProject());
    final PyExpression fString = generator.createExpressionFromText(LanguageLevel.PYTHON36, result.toString());
    expressionToReplace.replace(fString);
  }

  @Nullable
  private static PyExpression getActualReplacementExpression(@NotNull PySubstitutionChunkReference reference) {
    final PsiElement resolveResult = reference.resolve();
    if (resolveResult == null) {
      return null;
    }
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

  private static void convertFormatMethodFormatting(@NotNull PyStringLiteralExpression pyString) {
    // TODO get rid of duplication with #convertPercentOperatorFormatting
    final String stringText = pyString.getText();
    final StringNodeInfo stringInfo = new StringNodeInfo(pyString.getStringNodes().get(0));
    final StringBuilder result = new StringBuilder();
    result.append("f");
    result.append(stringInfo.getPrefix().replaceAll("[uU]", ""));
    result.append(stringInfo.getQuote());
    final List<FormatStringChunk> chunks = PyStringFormatParser.parseNewStyleFormat(stringText);
    final TextRange contentRange = stringInfo.getContentRange();
    int subsChunkPosition = 0;
    for (FormatStringChunk chunk : chunks) {
      if (chunk instanceof ConstantChunk) {
        final TextRange rangeWithoutQuotes = chunk.getTextRange().intersection(contentRange);
        assert rangeWithoutQuotes != null;
        result.append(rangeWithoutQuotes.substring(stringText));
      }
      else {
        final SubstitutionChunk subsChunk = (SubstitutionChunk)chunk;

        result.append("{");
        final PySubstitutionChunkReference reference = new PySubstitutionChunkReference(pyString, subsChunk, subsChunkPosition, false);
        final PyExpression resolveResult = getActualReplacementExpression(reference);
        assert resolveResult != null;

        final PsiElement adjusted = adjustQuotesInside(resolveResult, pyString);
        if (adjusted == null) return;

        // Replaces name
        result.append(adjusted.getText());

        final String wholeFragment = subsChunk.getTextRange().substring(stringText);

        final String escapedItemOrAttr = quoteItemsInFragments(wholeFragment, stringInfo.getSingleQuote());
        if (escapedItemOrAttr == null) return;
        result.append(escapedItemOrAttr);

        final int formatOrConversionCharStart = StringUtil.indexOfAny(wholeFragment, "!:");
        if (formatOrConversionCharStart >= 0) {
          final String formatAndConversionChar = wholeFragment.substring(formatOrConversionCharStart, wholeFragment.length() - 1);
          result.append(formatAndConversionChar);
        }

        result.append("}");
        subsChunkPosition++;
      }
    }
    result.append(stringInfo.getQuote());

    final PyCallExpression expressionToReplace = PsiTreeUtil.getParentOfType(pyString, PyCallExpression.class);
    assert expressionToReplace != null;

    final PyElementGenerator generator = PyElementGenerator.getInstance(pyString.getProject());
    final PyExpression fString = generator.createExpressionFromText(LanguageLevel.PYTHON36, result.toString());
    expressionToReplace.replace(fString);
  }

  @Nullable
  private static String quoteItemsInFragments(@NotNull String reference, char hostStringQuote) {
    List<String> escaped = new ArrayList<>();
    for (String part : extractItemsAndAttributes(reference)) {
      if (part.startsWith(".")) {
        escaped.add(part);
      }
      else if (part.startsWith("[")) {
        final String indexText = part.substring(1, part.length() - 1);
        if (indexText.matches("\\d+")) {
          escaped.add(part);
          continue;
        }
        final char quote = invertQuote(hostStringQuote);
        if (indexText.indexOf('\'') >= 0 && indexText.indexOf(quote) >= 0) {
          return null;
        }
        escaped.add("[" + quote + indexText + quote + "]");
      }
    }
    return StringUtil.join(escaped, "");
  }

  private static char invertQuote(char quote) {
    return quote == '"' ? '\'' : '"';
  }

  // TODO move this into PyStringFormatParser
  // For e.g. {0[foo].bar[!:]!r:something} returns: [foo], .bar, [!:]
  @VisibleForTesting
  @NotNull
  public static List<String> extractItemsAndAttributes(@NotNull String chunkText) {
    List<String> result = new ArrayList<>();

    boolean insideItem = false;
    boolean insideAttribute = false;
    int fragmentStart = 0;

    int offset = 1;
    while (offset < chunkText.length() - 1) {
      final char c = chunkText.charAt(offset);
      if (insideItem) {
        if (c == ']') {
          insideItem = false;
          result.add(chunkText.substring(fragmentStart, offset + 1));
        }
      }
      else if (c == '!' || c == ':' || c == '}') {
        break;
      }
      else if (c == '.' || c == '[') {
        if (insideAttribute) {
          result.add(chunkText.substring(fragmentStart, offset));
        }
        insideAttribute = c == '.';
        insideItem = c == '[';
        fragmentStart = offset;
      }
      offset++;
    }
    if (insideAttribute) {
      result.add(chunkText.substring(fragmentStart, offset));
    }
    return result;
  }
}
