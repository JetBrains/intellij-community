/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.PyUtil.guessLanguageLevel;
import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Replaces expressions like {@code "%s" % values} with likes of {@code "{0:s}".format(values)}.
 * <br/>
 * Author: Alexey.Ivanov, dcheryasov
 */
public class ConvertFormatOperatorToMethodIntention extends PyBaseIntentionAction {

  private static final Pattern FORMAT_PATTERN =
    Pattern.compile("%(?:\\((\\w+)\\))?([-#0+ ]*)((?:\\*|\\d+)?(?:\\.(?:\\*|\\d+))?)?[hlL]?([diouxXeEfFgGcrs%])");
  // groups: %:ignored,     1:key      2:mods    3:width-and---preci.sion            x:len  4: conversion-type

  private static final Pattern BRACE_PATTERN = Pattern.compile("(\\{|\\})");

  /**
   * copy source to target, doubling every brace.
   */
  private static void appendDoublingBraces(CharSequence source, StringBuilder target) {
    int index = 0;
    Matcher scanner = BRACE_PATTERN.matcher(source);
    boolean skipClosingBrace = false;
    while (scanner.find(index)) {
      if (scanner.start() > 1) {
        // handle escaping sequences PY-977
        if ("{".equals(scanner.group(0)) && "\\N".equals(source.subSequence(scanner.start()-2, scanner.start()).toString())) {
          skipClosingBrace = true;
          target.append(source.subSequence(index, scanner.end()));
          index = scanner.end();
          continue;
        }
      }
      if (skipClosingBrace && "}".equals(scanner.group(0))) {
        skipClosingBrace = false;
        target.append(source.subSequence(index, scanner.end()));
        index = scanner.end();
        continue;
      }

      target.append(source.subSequence(index, scanner.start()));
      if ("{".equals(scanner.group(0))) target.append("{{");
      else target.append("}}");
      index = scanner.end();
    }
    target.append(source.subSequence(index, source.length()));
  }

  /**
   * Converts format expressions inside a string
   * @return a pair of string builder with resulting string expression and a flag which is true if formats inside use mapping by name.
   */
  private static Pair<StringBuilder, Boolean> convertFormat(PyStringLiteralExpression stringLiteralExpression, String prefix) {
    // python string may be made of several literals, all different
    List<StringBuilder> constants = new ArrayList<>();
    boolean usesNamedFormat = false;
    final List<ASTNode> stringNodes = stringLiteralExpression.getStringNodes();
    sure(stringNodes);
    sure(stringNodes.size() > 0);
    for (ASTNode stringNode : stringNodes) {
      // preserve prefixes and quote form
      CharSequence text = stringNode.getChars();
      int openPos = 0;
      boolean hasPrefix = false;
      final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(String.valueOf(text));
      if (prefixLength != 0) hasPrefix = true;
      openPos += prefixLength;

      char quote = text.charAt(openPos);
      sure("\"'".indexOf(quote) >= 0);
      if (text.length() - openPos >= 6) {
        // triple-quoted?
        if (text.charAt(openPos+1) == quote && text.charAt(openPos+2) == quote) {
          openPos += 2;
        }
      }
      int index = openPos + 1; // from quote to first in-string char
      StringBuilder out = new StringBuilder(text.subSequence(0, openPos+1));
      if (!hasPrefix) out.insert(0, prefix);
      Matcher scanner = FORMAT_PATTERN.matcher(text);
      while (scanner.find(index)) {
        // store previous non-format part
        appendDoublingBraces(text.subSequence(index, scanner.start()), out);
        //out.append(text.subSequence(index, scanner.start()));
        // unpack format
        final String f_key = scanner.group(1);
        final String f_modifier = scanner.group(2);
        final String f_width = scanner.group(3);
        String fConversion = scanner.group(4);
        // convert to format()'s
        if ("%%".equals(scanner.group(0))) {
          // shortcut to put a literal %
          out.append("%");
        }
        else {
          sure(fConversion);
          sure(!"%".equals(fConversion)); // a padded percent literal; can't bother to autoconvert, and in 3k % is different.
          out.append("{");
          if (f_key != null) {
            out.append(f_key);
            usesNamedFormat = true;
          }
          if ("r".equals(fConversion)) out.append("!r");
          // don't convert %s -> !s, for %s is the normal way to output the default representation
          out.append(":");
          if (f_modifier != null) {
            out.append(convertFormatSpec(f_modifier, f_width, fConversion));
          }
          if (f_width != null) {
            out.append(f_width);
          }
          if ("i".equals(fConversion) || "u".equals(fConversion)) out.append("d");
          else if (!"s".equals(fConversion) && !"r".equals(fConversion)) out.append(fConversion);

          final int lastIndexOf = out.lastIndexOf(":");
          if (lastIndexOf == out.length() - 1) {
            out.deleteCharAt(lastIndexOf);
          }
          out.append("}");
        }
        index = scanner.end();
      }
      // store non-format final part
      //out.append(text.subSequence(index, text.length()-1));
      appendDoublingBraces(text.subSequence(index, text.length()), out);
      constants.add(out);
    }
    // form the entire literal filling possible gaps between constants.
    // we assume that a string literal begins with its first constant, without a gap.
    TextRange full_range = stringLiteralExpression.getTextRange();
    int full_start = full_range.getStartOffset();
    CharSequence full_text = stringLiteralExpression.getNode().getChars();
    TextRange prev_range = stringNodes.get(0).getTextRange();
    int fragment_no = 1; // look at second and further fragments
    while (fragment_no < stringNodes.size()) {
      TextRange next_range = stringNodes.get(fragment_no).getTextRange();
      int left = prev_range.getEndOffset() - full_start;
      int right = next_range.getStartOffset() - full_start;
      if (left < right) {
        constants.get(fragment_no-1).append(full_text.subSequence(left, right));
      }
      fragment_no += 1;
      prev_range = next_range;
    }
    final int left = prev_range.getEndOffset() - full_start;
    final int right = full_range.getEndOffset() - full_start;
    if (left < right) {
      // the barely possible case of last dangling "\"
      constants.get(constants.size()-1).append(full_text.subSequence(left, right));
    }

    // join everything
    StringBuilder result = new StringBuilder();
    for (StringBuilder one : constants) result.append(one);
    return new Pair<>(result, usesNamedFormat);
  }

  @NotNull
  public static String convertFormatSpec(@NotNull String modifier,
                                         @Nullable String widthAndPrecision,
                                         @Nullable String conversionChar) {
    final StringBuilder result = new StringBuilder();
    // in strict order
    if (has(modifier, '-')) {
      result.append("<"); // left align
    }
    else if ("s".equals(conversionChar) && !StringUtil.isEmptyOrSpaces(widthAndPrecision)) {
      // "%20s" aligns right, "{0:20s}" aligns left; to preserve align, make it explicit
      result.append(">");
    }
    if (has(modifier, '+')) {
      result.append("+"); // signed
    }
    else if (has(modifier, ' ')) result.append(" "); // default-signed
    if (has(modifier, '#')) result.append("#"); // alt numbers
    if (has(modifier, '0')) result.append("0"); // padding
    // anything else can't be here
    return result.toString();
  }

  private static boolean has(String where, char what) {
    return where.indexOf(what) >= 0;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.format.operator.to.method");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PyBinaryExpression binaryExpression  =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);
    if (binaryExpression == null) {
      return false;
    }
    if (binaryExpression.getLeftExpression() instanceof PyStringLiteralExpression && binaryExpression.getOperator() == PyTokenTypes.PERC) {
      final PyStringLiteralExpression str = (PyStringLiteralExpression)binaryExpression.getLeftExpression();
      if ((str.getText().length() > 0 && Character.toUpperCase(str.getText().charAt(0)) == 'B')) {
        return false;
      }

      final List<PyStringFormatParser.SubstitutionChunk> chunks =
        PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(binaryExpression.getLeftExpression().getText()));

      for (PyStringFormatParser.SubstitutionChunk chunk : chunks) {
        if ("*".equals(chunk.getWidth()) || "*".equals(chunk.getPrecision())) {
          return false;
        }
      }
      
      setText(PyBundle.message("INTN.replace.with.method"));
      return true;
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
    final PyBinaryExpression element = PsiTreeUtil.getParentOfType(elementAt, PyBinaryExpression.class, false);
    if (element == null) return;
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final PyExpression rightExpression = sure(element).getRightExpression();
    if (rightExpression == null) {
      return;
    }
    final PyExpression rhs = PyPsiUtils.flattenParens(rightExpression);
    if (rhs == null) return;
    final String paramText = sure(rhs).getText();
    final TypeEvalContext context = TypeEvalContext.userInitiated(file.getProject(), file);
    final PyType rhsType = context.getType(rhs);
    String prefix = "";
    final LanguageLevel languageLevel = guessLanguageLevel(project);
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(rhs);
    if (languageLevel.isPython2() && PyTypeChecker.match(builtinCache.getUnicodeType(languageLevel), rhsType, context) &&
        !PyTypeChecker.match(builtinCache.getBytesType(languageLevel), rhsType, context)) {
      prefix = "u";
    }
    final PyStringLiteralExpression leftExpression = (PyStringLiteralExpression)element.getLeftExpression();
    final Pair<StringBuilder, Boolean> converted = convertFormat(leftExpression, prefix);
    final StringBuilder target = converted.getFirst();
    final String separator = getSeparator(leftExpression);
    target.append(separator).append(".format");

    if (rhs instanceof PyReferenceExpression && rhsType instanceof PyTupleType) {
      target.append("(*").append(paramText).append(")");
    }
    else if (rhs instanceof PyCallExpression) { // potential dict(foo=1) -> format(foo=1)
      final PyCallExpression callExpression = (PyCallExpression)rhs;
      final PyExpression callee = callExpression.getCallee();
      final PyClassType classType = PyUtil.as(rhsType, PyClassType.class);
      if (classType != null && callee != null && isDictCall(callee, classType)) {
        target.append(sure(sure(callExpression.getArgumentList()).getNode()).getChars());
      }
      else { // just a call, reuse
        target.append("(");
        if (converted.getSecond()) target.append("**"); // map-by-name formatting was detected
        target.append(paramText).append(")");
      }
    }
    else if (rhsType instanceof PyCollectionType && "dict".equals(rhsType.getName())) {
      target.append("(**").append(paramText).append(")");
    }
    else target.append("(").append(paramText).append(")"); // tuple is ok as is
    // Correctly handle multiline implicitly concatenated string literals (PY-9176)
    target.insert(0, '(').append(')');
    final PyExpression parenthesized = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), target.toString());
    element.replace(sure(((PyParenthesizedExpression)parenthesized).getContainedExpression()));
  }
  
  private static boolean isDictCall(@NotNull PyExpression callee,
                                    @NotNull PyClassType classType) {
    final PyClassType dictType = PyBuiltinCache.getInstance(callee.getContainingFile()).getDictType();
    if (dictType != null && classType.getPyClass() == dictType.getPyClass()) {
      if (callee instanceof PyReferenceExpression) {
        PsiElement maybeDict = ((PyReferenceExpression)callee).getReference().resolve();
        final PyFunction dictInit = PyUtil.as(maybeDict, PyFunction.class);
        if (dictInit != null) {
          if (PyNames.INIT.equals(dictInit.getName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static String getSeparator(PyStringLiteralExpression leftExpression) {
    String separator = ""; // detect nontrivial whitespace around the "%"
    Pair<String, PsiElement> crop = collectWhitespace(leftExpression);
    String maybeSeparator = crop.getFirst();
    if (maybeSeparator != null && !maybeSeparator.isEmpty() && !" ".equals(maybeSeparator))
      separator = maybeSeparator;
    else { // after "%"
      crop = collectWhitespace(crop.getSecond());
      maybeSeparator = crop.getFirst();
      if (maybeSeparator != null && !maybeSeparator.isEmpty() && !" ".equals(maybeSeparator))
        separator = maybeSeparator;
    }
    return separator;
  }

  private static Pair<String, PsiElement> collectWhitespace(PsiElement start) {
    StringBuilder sb = new StringBuilder();
    PsiElement seeker = start;
    while (seeker != null) {
      seeker = seeker.getNextSibling();
      if (seeker instanceof PsiWhiteSpace) sb.append(seeker.getText());
      else break;
    }
    return Pair.create(sb.toString(), seeker);
  }
}
