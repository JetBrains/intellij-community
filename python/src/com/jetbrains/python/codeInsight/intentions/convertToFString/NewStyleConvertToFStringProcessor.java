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

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.inspections.PyNewStyleStringFormatParser;
import com.jetbrains.python.inspections.PyNewStyleStringFormatParser.Field;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class NewStyleConvertToFStringProcessor extends BaseConvertToFStringProcessor<Field> {
  public NewStyleConvertToFStringProcessor(@NotNull PyStringLiteralExpression pyString) {
    super(pyString);
  }

  @NotNull
  @Override
  protected List<Field> extractAllSubstitutionChunks() {
    return PyNewStyleStringFormatParser.parse(myPyString.getText()).getAllFields();
  }

  @NotNull
  @Override
  protected List<Field> extractTopLevelSubstitutionChunks() {
    return PyNewStyleStringFormatParser.parse(myPyString.getText()).getFields();
  }

  @NotNull
  @Override
  protected PySubstitutionChunkReference createReference(@NotNull Field field) {
    return new PySubstitutionChunkReference(myPyString, field);
  }

  @Override
  protected boolean checkChunk(@NotNull Field chunk) {
    return true;
  }

  @NotNull
  @Override
  public PyExpression getWholeExpressionToReplace() {
    //noinspection ConstantConditions
    return PsiTreeUtil.getParentOfType(myPyString, PyCallExpression.class);
  }

  @Nullable
  @Override
  protected PsiElement getValuesSource() {
    final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(myPyString, PyCallExpression.class);
    assert callExpression != null;
    return callExpression.getArgumentList();
  }

  @Nullable
  @Override
  protected PsiElement prepareExpressionToInject(@NotNull PyExpression expression, @NotNull Field field) {
    final PsiElement prepared = super.prepareExpressionToInject(expression, field);
    if (prepared == null) return null;

    // You cannot access attributes on numeric literals without wrapping them in parentheses
    if (!field.getAttributesAndLookups().isEmpty() && (prepared instanceof PyBinaryExpression ||
                                                       prepared instanceof PyPrefixExpression ||
                                                       prepared instanceof PyNumericLiteralExpression)) {
      return wrapExpressionInParentheses(prepared);
    }
    return prepared;
  }

  @Override
  protected boolean convertSubstitutionChunk(@NotNull Field field, @NotNull StringBuilder fStringText) {

    final String stringText = myPyString.getText();

    // Actual format field
    fStringText.append("{");
    final PySubstitutionChunkReference reference = createReference(field);
    final PyExpression resolveResult = adjustResolveResult(reference.resolve());
    if (resolveResult == null) return false;

    final PsiElement adjusted = prepareExpressionToInject(resolveResult, field);
    if (adjusted == null) return false;

    fStringText.append(adjusted.getText());
    final String quotedAttrsAndItems = quoteItemsInFragments(field);
    if (quotedAttrsAndItems == null) return false;

    fStringText.append(quotedAttrsAndItems);

    // Conversion is copied as is if it's present
    final String conversion = field.getConversion();
    if (conversion != null) {
      fStringText.append(conversion);
    }

    // Format spec is copied if present handling nested fields
    final TextRange specRange = field.getFormatSpecRange();
    if (specRange != null) {
      int specOffset = specRange.getStartOffset();
      // Do not proceed too nested fields
      if (field.getDepth() == 1) {
        for (Field nestedField : field.getNestedFields()) {
          // Copy text of the format spec between nested fragments
          fStringText.append(stringText, specOffset, nestedField.getLeftBraceOffset());
          specOffset = nestedField.getFieldEnd();

          // recursively format nested field
          if (!convertSubstitutionChunk(nestedField, fStringText)) {
            return false;
          }
        }
      }
      if (specOffset < specRange.getEndOffset()) {
        fStringText.append(stringText, specOffset, specRange.getEndOffset());
      }
    }

    fStringText.append("}");
    return true;
  }

  @Nullable
  private String quoteItemsInFragments(@NotNull Field field) {
    final List<String> escaped = new ArrayList<>();
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
        final char originalQuote = myNodeInfo.getSingleQuote();
        char targetQuote = PyStringLiteralUtil.flipQuote(originalQuote);
        // there are no escapes inside the fragment, so the lookup key cannot contain 
        // the host string quote unless it's a multiline string literal
        if (indexText.indexOf(targetQuote) >= 0) {
          if (!myNodeInfo.isTripleQuoted() || indexText.indexOf(originalQuote) >= 0) {
            return null;
          }
          targetQuote = originalQuote;
        }
        escaped.add("[" + targetQuote + indexText + targetQuote + "]");
      }
    }
    return StringUtil.join(escaped, "");
  }
}
