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

package org.intellij.lang.xpath.psi.impl;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.LocalTimeCounter;
import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.jetbrains.annotations.NotNull;

public class XPathChangeUtil {
    private XPathChangeUtil() {
    }

    @NotNull
    public static XPathExpression createExpression(PsiElement context, String text) {
        final XPathFile file = createXPathFile(context, text);
        final XPathExpression child = PsiTreeUtil.getChildOfType(file, XPathExpression.class);
        assert child != null;
        return child;
    }

    @NotNull
    public static XPathVariableReference createVariableReference(PsiElement context, String name) {
        return (XPathVariableReference)createExpression(context, "$" + name);
    }

    @NotNull
    public static XPathFile createXPathFile(PsiElement context, String text) {
        final XPathFile file = createXPathFile(context.getProject(), text, context.getContainingFile().getFileType());
        ContextProvider.copy(context.getContainingFile(), file);
        return file;
    }

    @NotNull
    public static XPathFile createXPathFile(Project project, String text, FileType fileType) {
        return (XPathFile)PsiFileFactory.getInstance(project).createFileFromText("dummy." + fileType.getDefaultExtension(), fileType, text, LocalTimeCounter.currentTime(), true);
    }
}
