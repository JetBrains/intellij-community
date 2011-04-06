/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.validation.inspections.quickfix;

import com.intellij.psi.PsiElement;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.psi.impl.XPathChangeUtil;

import com.intellij.util.IncorrectOperationException;

public abstract class ReplaceElementFix<TYPE extends XPathElement> extends XPathQuickFixFactory.Fix<TYPE> {

    protected ReplaceElementFix(TYPE element) {
        super(element);
    }

    protected void replace(String expressionText) throws IncorrectOperationException {
      PsiElement myElement = getStartElement();
      myElement.replace(XPathChangeUtil.createExpression(myElement, expressionText));
    }
}
