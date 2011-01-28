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
package org.intellij.plugins.xpathView.support.jaxen.extensions;

import org.intellij.lang.xpath.context.functions.Parameter;
import org.intellij.lang.xpath.psi.XPathType;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import org.jaxen.Context;
import org.jaxen.Function;
import org.jaxen.FunctionCallException;

import java.util.List;

public abstract class BasicFileInfoFunction extends FunctionImplementation implements Function {
    public BasicFileInfoFunction(String name, XPathType returnType, Parameter... parameters) {
        super(name, returnType, parameters);
    }

    public Function getImplementation() {
        return this;
    }

    @Nullable
    @SuppressWarnings({ "RawUseOfParameterizedType" })
    public Object call(Context context, List list) throws FunctionCallException {
        final Object arg;
        if (list.size() == 0) {
            arg = context.getNodeSet().get(0);
        } else {
            final Object o = list.get(0);
            arg = o instanceof List ? ((List)o).get(0) : o;
        }
        if (!(arg instanceof PsiElement)) {
            throw new FunctionCallException("NodeSet expected");
        }
        final PsiFile psiFile = ((PsiElement)arg).getContainingFile();

        assert psiFile != null;
        return extractInfo(psiFile);
    }

    @Nullable
    protected abstract Object extractInfo(PsiFile psiFile);
}
