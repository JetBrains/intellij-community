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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.codeInsight.PythonFormattedStringReferenceProvider;
import com.jetbrains.python.inspections.PyNewStyleStringFormatParser;
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

import static com.jetbrains.python.codeInsight.intentions.ConvertFormatOperatorToMethodIntention.convertFormatSpec;
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
      
      if (percentOperator) {
        for (int i = 0; i < substitutions.size(); i++) {
          final SubstitutionChunk chunk = substitutions.get(i);
          if ((chunk.getMappingKey() != null || substitutions.size() > 1) && references[i].resolve() == valuesSource) {
            return false;
          }
        }
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
      final char targetSingleQuote = flipQuote(hostQuote);
      if (content.indexOf(hostQuote) >= 0) {
        return null;
      }
      if (!info.isTerminated()) {
        return null;
      }
      if (info.getQuote().startsWith(hostInfo.getQuote())) {
        if (content.indexOf(targetSingleQuote) >= 0) {
          return null;
        }
        
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
      if (callee != null && PyNames.FORMAT.equals(callee.getName())) {
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
        
        String widthAndPrecision = StringUtil.notNullize(subsChunk.getWidth());
        if (StringUtil.isNotEmpty(subsChunk.getPrecision())) {
          widthAndPrecision += "." + subsChunk.getPrecision();
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

        result.append(convertFormatSpec(StringUtil.notNullize(conversionFlags), widthAndPrecision, String.valueOf(conversionChar)));

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
    final StringBuilder newStringText = new StringBuilder();
    newStringText.append("f");
    newStringText.append(stringInfo.getPrefix().replaceAll("[uU]", ""));
    newStringText.append(stringInfo.getQuote());

    final PyNewStyleStringFormatParser.ParseResult parseResult = PyNewStyleStringFormatParser.parse(stringText);
    
    final TextRange contentRange = stringInfo.getContentRange();
    int offset = contentRange.getStartOffset();
    for (PyNewStyleStringFormatParser.Field field : parseResult.getFields()) {
      // Preceding literal text
      newStringText.append(stringText.substring(offset, field.getLeftBraceOffset()));
      offset = field.getFieldEnd();
      
      if (!processField(field, pyString, newStringText, true)) {
        return;
      }
    }
    if (offset < contentRange.getEndOffset()) {
      newStringText.append(stringText.substring(offset, contentRange.getEndOffset()));
    }

    newStringText.append(stringInfo.getQuote());

    final PyCallExpression expressionToReplace = PsiTreeUtil.getParentOfType(pyString, PyCallExpression.class);
    assert expressionToReplace != null;

    final PyElementGenerator generator = PyElementGenerator.getInstance(pyString.getProject());
    final PyExpression fString = generator.createExpressionFromText(LanguageLevel.PYTHON36, newStringText.toString());
    expressionToReplace.replace(fString);
  }

  private static boolean processField(@NotNull PyNewStyleStringFormatParser.Field field,
                                      @NotNull PyStringLiteralExpression pyString,
                                      @NotNull StringBuilder newStringText, 
                                      boolean withNestedFields) {
    
    String stringText = pyString.getText(); 
    StringNodeInfo stringInfo = new StringNodeInfo(pyString.getStringNodes().get(0));
    
    // Actual format field
    newStringText.append("{");
    // Isn't supposed to be used by PySubstitutionChunkReference if explicit name or index is given
    final int autoNumber = field.getAutoPosition() == null ? 0 : field.getAutoPosition();
    final PySubstitutionChunkReference reference = new PySubstitutionChunkReference(pyString, field, autoNumber, false);
    final PyExpression resolveResult = getActualReplacementExpression(reference);
    if (resolveResult == null) return false;

    final PsiElement adjusted = adjustQuotesInside(resolveResult, pyString);
    if (adjusted == null) return false;

    newStringText.append(adjusted.getText());
    final String quotedAttrsAndItems = quoteItemsInFragments(field, stringInfo);
    if (quotedAttrsAndItems == null) return false;

    newStringText.append(quotedAttrsAndItems);

    // Conversion is copied as is if it's present
    final String conversion = field.getConversion();
    if (conversion != null) {
      newStringText.append(conversion);
    }

    // Format spec is copied if present handling nested fields
    final TextRange specRange = field.getFormatSpecRange();
    if (specRange != null) {
      if (withNestedFields) {
        int specOffset = specRange.getStartOffset();
        for (PyNewStyleStringFormatParser.Field nestedField : field.getNestedFields()) {
          // Copy text of the format spec between nested fragments
          newStringText.append(stringText.substring(specOffset, nestedField.getLeftBraceOffset()));
          specOffset = nestedField.getFieldEnd();
          
          // recursively format nested field
          if (!processField(nestedField, pyString, newStringText, false)) {
            return false;
          }
        }
        if (specOffset < specRange.getEndOffset()) {
          newStringText.append(stringText.substring(specOffset, specRange.getEndOffset()));
        }
      }
      else {
        // Fields nested deeper that twice append as is
        newStringText.append(field.getFormatSpec());
      }
    }

    newStringText.append("}");
    return true;
  }

  @Nullable
  private static String quoteItemsInFragments(@NotNull PyNewStyleStringFormatParser.Field field, @NotNull StringNodeInfo hostStringInfo) {
    List<String> escaped = new ArrayList<>();
    for (String part : field.getAttributesAndLookups()) {
      if (part.startsWith(".")) {
        escaped.add(part);
      }
      else if (part.startsWith("[")) {
        if (part.contains("\\")) {
          return null;
        }
        final String indexText = part.substring(1, part.length() - 1);
        if (indexText.matches("\\d+")) {
          escaped.add(part);
          continue;
        }
        final char originalQuote = hostStringInfo.getSingleQuote();
        char targetQuote = flipQuote(originalQuote);
        // there are no escapes inside the fragment, so the lookup key cannot contain 
        // the host string quote unless it's a multiline string literal
        if (indexText.indexOf(targetQuote) >= 0) {
          if (!hostStringInfo.isTripleQuoted() || indexText.indexOf(originalQuote) >= 0) {
            return null;
          }
          targetQuote = originalQuote;
        }
        escaped.add("[" + targetQuote + indexText + targetQuote + "]");
      }
    }
    return StringUtil.join(escaped, "");
  }

  private static char flipQuote(char quote) {
    return quote == '"' ? '\'' : '"';
  }
}
