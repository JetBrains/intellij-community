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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.inspections.PyStringFormatParser.NewStyleSubstitutionChunk;
import com.jetbrains.python.inspections.PyStringFormatParser.PercentSubstitutionChunk;
import com.jetbrains.python.inspections.PyStringFormatParser.SubstitutionChunk;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.codeInsight.intentions.ConvertFormatOperatorToMethodIntention.convertFormatSpec;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class OldStyleConvertToFStringProcessor extends BaseConvertToFStringProcessor<SubstitutionChunk> {
  public OldStyleConvertToFStringProcessor(@NotNull PyStringLiteralExpression pyString) {
    super(pyString);
  }

  @NotNull
  @Override
  protected List<SubstitutionChunk> extractAllSubstitutionChunks() {
    return PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(myPyString.getText()));
  }

  @NotNull
  @Override
  protected PySubstitutionChunkReference createReference(@NotNull SubstitutionChunk chunk) {
    return new PySubstitutionChunkReference(myPyString, chunk);
  }

  @Override
  protected boolean checkChunk(@NotNull SubstitutionChunk chunk) {
    // TODO handle dynamic width and precision in old-style/"percent" formatting
    return !("*".equals(chunk.getPrecision()) || "*".equals(chunk.getWidth()));
  }

  @Override
  protected boolean checkReferencedExpression(@NotNull List<SubstitutionChunk> chunks,
                                              @NotNull SubstitutionChunk chunk,
                                              @NotNull PsiElement valueSource,
                                              @NotNull PyExpression expression) {
    if ((chunk.getMappingKey() != null || chunks.size() > 1) && expression == valueSource) return false;
    return super.checkReferencedExpression(chunks, chunk, valueSource, expression);
  }

  @NotNull
  @Override
  public PyExpression getWholeExpressionToReplace() {
    //noinspection ConstantConditions
    return PsiTreeUtil.getParentOfType(myPyString, PyBinaryExpression.class);
  }

  @Nullable
  @Override
  protected PsiElement getValuesSource() {
    final PyBinaryExpression binaryExpression = as(myPyString.getParent(), PyBinaryExpression.class);
    assert binaryExpression != null;
    return binaryExpression.getRightExpression();
  }

  @Override
  protected boolean convertSubstitutionChunk(@NotNull SubstitutionChunk subsChunk, @NotNull StringBuilder fStringText) {
    final char conversionChar = subsChunk.getConversionType();

    String widthAndPrecision = StringUtil.notNullize(subsChunk.getWidth());
    if (StringUtil.isNotEmpty(subsChunk.getPrecision())) {
      widthAndPrecision += "." + subsChunk.getPrecision();
    }

    final String conversionFlags = subsChunk instanceof PercentSubstitutionChunk ?
                                   ((PercentSubstitutionChunk)subsChunk).getConversionFlags() :
                                   ((NewStyleSubstitutionChunk)subsChunk).getConversion();

    fStringText.append("{");
    final PySubstitutionChunkReference reference = createReference(subsChunk);
    final PyExpression resolveResult = adjustResolveResult(reference.resolve());
    assert resolveResult != null;

    final PsiElement adjusted = prepareExpressionToInject(resolveResult, subsChunk);
    if (adjusted == null) return false;

    fStringText.append(adjusted.getText());

    // TODO mostly duplicates the logic of ConvertFormatOperatorToMethodIntention
    if (conversionChar == 'r') {
      fStringText.append("!r");
    }

    if ((conversionChar != 'r' && conversionChar != 's')
        || StringUtil.isNotEmpty(conversionFlags)
        || StringUtil.isNotEmpty(widthAndPrecision)) {
      fStringText.append(":");
    }

    fStringText.append(convertFormatSpec(StringUtil.notNullize(conversionFlags), widthAndPrecision, String.valueOf(conversionChar)));

    if (StringUtil.isNotEmpty(widthAndPrecision)) {
      fStringText.append(widthAndPrecision);
    }

    if ('i' == conversionChar || 'u' == conversionChar) {
      fStringText.append("d");
    }
    else if ('s' != conversionChar && 'r' != conversionChar) {
      fStringText.append(conversionChar);
    }
    fStringText.append("}");
    return true;
  }
}
