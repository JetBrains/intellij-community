/*
 * Copyright 2006 Sascha Weinreuter
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
package org.intellij.lang.xpath;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.psi.XPathFunction;
import org.intellij.lang.xpath.psi.XPathFunctionCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPathParameterInfoHandler implements ParameterInfoHandler<XPathFunctionCall, XPathFunction> {
    public boolean couldShowInLookup() {
        return false;
    }

    public Object[] getParametersForLookup(LookupElement lookupElement, ParameterInfoContext parameterInfoContext) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public XPathFunctionCall findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        final XPathFunctionCall call = findFunctionCall(context.getFile(), context.getOffset());
        if (call != null) {
            final XPathFunction function = call.resolve();
            if (function != null && function.getDeclaration() != null) {
                context.setItemsToShow(new Object[]{ function });
            }
        }
        return call;
    }

    @Nullable
    private static XPathFunctionCall findFunctionCall(PsiFile psiFile, int offset) {
        PsiElement e = psiFile.findElementAt(offset);
        while (e != null) {
            final XPathFunctionCall call = PsiTreeUtil.getParentOfType(e, XPathFunctionCall.class);
            if (call == null) {
                break;
            }
            final ASTNode lparen = call.getNode().findChildByType(XPathTokenTypes.LPAREN);
            if (lparen != null && lparen.getTextRange().getStartOffset() < offset) {
                return call;
            }

            e = PsiTreeUtil.getParentOfType(e, XPathFunctionCall.class, true);
        }
        return null;
    }

    public void showParameterInfo(@NotNull XPathFunctionCall call, @NotNull CreateParameterInfoContext context) {
        context.showHint(call, call.getTextOffset() + 1, this);
    }

    public XPathFunctionCall findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        return findFunctionCall(context.getFile(), context.getOffset());
    }

    public void updateParameterInfo(@NotNull XPathFunctionCall call, @NotNull UpdateParameterInfoContext context) {
        int currentParameterIndex = ParameterInfoUtils.getCurrentParameterIndex(call.getNode(), context.getOffset(), XPathTokenTypes.COMMA);
        context.setCurrentParameter(currentParameterIndex);
    }

    public void updateUI(XPathFunction function, @NotNull ParameterInfoUIContext context) {
        final Function declaration = function.getDeclaration();
        if (declaration != null) {
            if (declaration.getParameters().length > 0) {
                final String signature = declaration.buildSignature();
                final int length = declaration.getName().length();
                final String hint = signature.substring(length + 1, signature.length() - 1);
                final int currentParameterIndex = context.getCurrentParameterIndex();

                if (currentParameterIndex < 0 || currentParameterIndex >= declaration.getParameters().length) {
                    context.setupUIComponentPresentation(hint, -1, -1,
                            false, false, false, context.getDefaultParameterColor());
                } else {
                    final String[] ps = hint.split(",");
                    final TextRange[] ts = new TextRange[ps.length];

                    int start = 0;
                    for (int i = 0; i < ps.length; i++) {
                        String p = ps[i];
                        ts[i] = TextRange.from(start, p.length());
                        start += p.length() + 1;
                    }
                    final TextRange range = ts[currentParameterIndex];
                    context.setupUIComponentPresentation(hint, range.getStartOffset(), range.getEndOffset(),
                            false, false, false, context.getDefaultParameterColor());
                }
            } else {
                context.setupUIComponentPresentation(noParamsMessage(), -1, -1,
                        false, false, false, context.getDefaultParameterColor());
            }
        }
    }

    @SuppressWarnings({ "UnresolvedPropertyKey" })
    private static String noParamsMessage() {
        return CodeInsightBundle.message("parameter.info.no.parameters");
    }
}
