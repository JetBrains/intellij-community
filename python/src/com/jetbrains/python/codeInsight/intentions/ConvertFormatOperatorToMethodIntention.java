package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Replaces expressions like <code>"%s" % values</code> with likes of <code>"{0:s}".format(values)</code>.
 * <br/>
 * Author: Alexey.Ivanov, dcheryasov
 */
public class ConvertFormatOperatorToMethodIntention extends BaseIntentionAction {

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
    while (scanner.find(index)) {
      target.append(source.subSequence(index, scanner.start()));
      if ("{".equals(scanner.group(0))) target.append("{{");
      else target.append("}}");
      index = scanner.end();
    }
    target.append(source.subSequence(index, source.length()));
  }

  /**
   * Converts format expressions inside a string
   * @param stringLiteralExpression
   * @return a pair of string builder with resulting string expression and a flag which is true if formats inside use mapping by name.
   */
  private static Pair<StringBuilder, Boolean> convertFormat(PyStringLiteralExpression stringLiteralExpression) {
    // python string may be made of several literals, all different
    List<StringBuilder> constants = new ArrayList<StringBuilder>();
    boolean uses_named_format = false;
    final List<ASTNode> text_nodes = stringLiteralExpression.getStringNodes();
    sure(text_nodes);
    sure(text_nodes.size() > 0);
    for (ASTNode text_node : text_nodes) {
      // preserve prefixes and quote form
      CharSequence text = text_node.getChars();
      int open_pos = 0;
      if ("uUbB".indexOf(text.charAt(open_pos)) >= 0)  open_pos += 1;  // unicode modifier
      if ("rR".indexOf(text.charAt(open_pos)) >= 0) open_pos += 1; // raw modifier
      char quote = text.charAt(open_pos);
      sure("\"'".indexOf(quote) >= 0);
      if (text.length() - open_pos >= 6) {
        // triple-quoted?
        if (text.charAt(open_pos+1) == quote && text.charAt(open_pos+2) == quote) {
          open_pos += 2;
        }
      }
      int index = open_pos + 1; // from quote to first in-string char
      StringBuilder out = new StringBuilder(text.subSequence(0, open_pos+1));
      int position_count = 0;
      Matcher scanner = FORMAT_PATTERN.matcher(text);
      while (scanner.find(index)) {
        // store previous non-format part
        appendDoublingBraces(text.subSequence(index, scanner.start()), out);
        //out.append(text.subSequence(index, scanner.start()));
        // unpack format
        final String f_key = scanner.group(1);
        final String f_modifier = scanner.group(2);
        final String f_width = scanner.group(3);
        String f_conversion = scanner.group(4);
        // convert to format()'s
        if ("%%".equals(scanner.group(0))) {
          // shortcut to put a literal %
          out.append("%");
        }
        else {
          sure(f_conversion);
          sure(!"%".equals(f_conversion)); // a padded percent literal; can't bother to autoconvert, and in 3k % is different.
          out.append("{");
          if (f_key != null) {
            out.append(f_key);
            uses_named_format = true;
          }
          else {
            out.append(position_count);
            position_count += 1;
          }
          if ("r".equals(f_conversion)) out.append("!r");
          // don't convert %s -> !s, for %s is the normal way to output the default representation
          out.append(":");
          if (f_modifier != null) {
            // in strict order
            if (has(f_modifier, '-')) out.append("<"); // left align
            else if ("s".equals(f_conversion) && f_width != null) {
              // "%20s" aligns right, "{0:20s}" aligns left; to preserve align, make it explicit
              out.append(">");
            }
            if (has(f_modifier, '+')) out.append("+"); // signed
            else if (has(f_modifier, ' ')) out.append(" "); // default-signed
            if (has(f_modifier, '#')) out.append("#"); // alt numbers
            if (has(f_modifier, '0')) out.append("0"); // padding
            // anything else can't be here
          }
          if (f_width != null) {
            out.append(f_width);
          }
          if ("i".equals(f_conversion) || "u".equals(f_conversion)) out.append("d");
          else if ("r".equals(f_conversion)) out.append("s"); // we want our raw string as a string
          else out.append(f_conversion);
          //
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
    TextRange prev_range = text_nodes.get(0).getTextRange();
    int fragment_no = 1; // look at second and further fragments
    while (fragment_no < text_nodes.size()) {
      TextRange next_range = text_nodes.get(fragment_no).getTextRange();
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
    return new Pair<StringBuilder, Boolean>(result, uses_named_format);
  }

  private static boolean has(String where, char what) {
    return where.indexOf(what) >= 0;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.format.operator.to.method");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyBinaryExpression binaryExpression  =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);
    if (binaryExpression == null) {
      return false;
    }
    final VirtualFile virtualFile = binaryExpression.getContainingFile().getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    final LanguageLevel languageLevel = LanguageLevel.forFile(virtualFile);
    if (languageLevel.isOlderThan(LanguageLevel.PYTHON26)) {
      return false;
    }
    if (binaryExpression.getLeftExpression() instanceof PyStringLiteralExpression && binaryExpression.getOperator() == PyTokenTypes.PERC) {
      setText(PyBundle.message("INTN.replace.with.method"));
      return true;
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyBinaryExpression element =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final PyExpression rightExpression = sure(element).getRightExpression();
    if (rightExpression == null) {
      return;
    }
    PyExpression rhs = PyUtil.flattenParens(rightExpression);
    String param_text = sure(rhs).getText();
    final PyStringLiteralExpression leftExpression = (PyStringLiteralExpression)element.getLeftExpression();
    Pair<StringBuilder, Boolean> converted = convertFormat(leftExpression);
    StringBuilder target = converted.getFirst();
    String separator = ""; // detect nontrivial whitespace around the "%"
    Pair<String, PsiElement> crop = collectWhitespace(leftExpression);
    String maybe_separator = crop.getFirst();
    if (!"".equals(maybe_separator) && !" ".equals(maybe_separator)) separator = maybe_separator;
    else { // after "%"
      crop = collectWhitespace(crop.getSecond());
      maybe_separator = crop.getFirst();
      if (!"".equals(maybe_separator) && !" ".equals(maybe_separator)) separator = maybe_separator;
    }
    target.append(separator).append(".format");
    if (rhs instanceof PyDictLiteralExpression) target.append("(**").append(param_text).append(")");
    else if (rhs instanceof PyCallExpression) { // potential dict(foo=1) -> format(foo=1)
      final PyCallExpression call_expression = (PyCallExpression)rhs;
      final PyExpression callee = call_expression.getCallee();
      if (callee instanceof PyReferenceExpression) {
        PsiElement maybe_dict = ((PyReferenceExpression)callee).getReference().resolve();
        if (maybe_dict instanceof PyFunction) {
          PyFunction dict_init = (PyFunction)maybe_dict;
          if (PyNames.INIT.equals(dict_init.getName())) {
            final PyClassType dict_type = PyBuiltinCache.getInstance(file).getDictType();
            if (dict_type != null && dict_type.getPyClass() == dict_init.getContainingClass()) {
              target.append(sure(sure(call_expression.getArgumentList()).getNode()).getChars());
            }
          }
          else { // just a call, reuse
            target.append("(");
            if (converted.getSecond()) target.append("**"); // map-by-name formatting was detected
            target.append(param_text).append(")");
          }
        }
      }
    }
    else target.append("(").append(param_text).append(")"); // tuple is ok as is
    element.replace(elementGenerator.createExpressionFromText(target.toString()));
  }

  private static Pair<String, PsiElement> collectWhitespace(PsiElement start) {
    StringBuilder sb = new StringBuilder();
    PsiElement seeker = start;
    while (seeker != null) {
      seeker = seeker.getNextSibling();
      if (seeker != null && seeker instanceof PsiWhiteSpace) sb.append(seeker.getText());
      else break;
    }
    return new Pair<String, PsiElement>(sb.toString(), seeker);
  }
}
