/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.documentation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class DocStringReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
    final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
    if (docStringOwner != null && element == docStringOwner.getDocStringExpression()) {
      final PyStringLiteralExpression expr = (PyStringLiteralExpression)element;
      final List<TextRange> ranges = expr.getStringValueTextRanges();

      final String exprText = expr.getText();
      final TextRange textRange = PyStringLiteralExpressionImpl.getNodeTextRange(exprText);
      final String text = textRange.substring(exprText);

      if (!ranges.isEmpty()) {
        final List<PsiReference> result = new ArrayList<PsiReference>();
        final int offset = ranges.get(0).getStartOffset();
        // XXX: It does not work with multielement docstrings
        StructuredDocString docString = DocStringUtil.parse(text);
        if (docString != null) {
          result.addAll(referencesFromNames(element, offset, docString,
                                            docString.getTagArguments(StructuredDocStringBase.PARAM_TAGS),
                                            StructuredDocStringBase.PARAMETER));
          result.addAll(referencesFromNames(element, offset, docString,
                                            docString.getTagArguments(StructuredDocStringBase.PARAM_TYPE_TAGS),
                                            StructuredDocStringBase.PARAMETER_TYPE));
          result.addAll(referencesFromNames(element, offset, docString,
                                            docString.getKeywordArgumentSubstrings(), StructuredDocStringBase.KEYWORD));

          result.addAll(referencesFromNames(element, offset, docString,
                                            docString.getTagArguments(StructuredDocStringBase.VARIABLE_TAGS),
                                            StructuredDocStringBase.VARIABLE));
          result.addAll(returnTypes(element, docString, offset));
        }
        return result.toArray(new PsiReference[result.size()]);
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static List<PsiReference> returnTypes(PsiElement element,
                                                StructuredDocString docString,
                                                int offset) {
    List<PsiReference> result = new ArrayList<PsiReference>();

    final Substring rtype = docString.getReturnTypeSubstring();
    if (rtype != null) {
      result.addAll(parseTypeReferences(element, rtype, offset));
    }
    return result;
  }
  private static List<PsiReference> referencesFromNames(PsiElement element,
                                                        int offset,
                                                        StructuredDocString docString,
                                                        List<Substring> paramNames,
                                                        String refType) {
    List<PsiReference> result = new ArrayList<PsiReference>();
    for (Substring name : paramNames) {
      final String s = name.toString();
      if (PyNames.isIdentifier(s)) {
        final TextRange range = name.getTextRange().shiftRight(offset);
        result.add(new DocStringParameterReference(element, range, refType));
      }
      if (refType.equals(StructuredDocStringBase.PARAMETER_TYPE)) {
        final Substring type = docString.getParamTypeSubstring(s);
        if (type != null) {
          result.addAll(parseTypeReferences(element, type, offset));
        }
      }
    }
    return result;
  }

  private static List<PsiReference> parseTypeReferences(PsiElement anchor, Substring s, int offset) {
    final List<PsiReference> result = new ArrayList<PsiReference>();
    final PyTypeParser.ParseResult parseResult = PyTypeParser.parse(anchor, s.toString());
    final Map<TextRange, ? extends PyType> types = parseResult.getTypes();
    if (types.isEmpty()) {
      result.add(new DocStringTypeReference(anchor, s.getTextRange().shiftRight(offset), s.getTextRange().shiftRight(offset), null, null));
    }
    offset = s.getTextRange().getStartOffset() + offset;
    final Map<? extends PyType, TextRange> fullRanges = parseResult.getFullRanges();
    for (Map.Entry<TextRange, ? extends PyType> pair : types.entrySet()) {
      final PyType t = pair.getValue();
      final TextRange range = pair.getKey().shiftRight(offset);
      final TextRange fullRange = fullRanges.containsKey(t) ? fullRanges.get(t).shiftRight(offset) : range;
      final PyImportElement importElement = parseResult.getImports().get(t);
      result.add(new DocStringTypeReference(anchor, range, fullRange, t, importElement));
    }
    return result;
  }

  @Nullable
  public static TextRange findNextTag(String docString, int pos, String[] paramTags) {
    int result = Integer.MAX_VALUE;
    String foundTag = null;
    for (String paramTag : paramTags) {
      int tagPos = docString.indexOf(paramTag, pos);
      while(tagPos >= 0 && tagPos + paramTag.length() < docString.length() &&
            Character.isLetterOrDigit(docString.charAt(tagPos + paramTag.length()))) {
        tagPos = docString.indexOf(paramTag, tagPos+1);
      }
      if (tagPos >= 0 && tagPos < result) {
        foundTag = paramTag;
        result = tagPos;
      }
    }
    return foundTag == null ? null : new TextRange(result, result + foundTag.length());
  }
}
