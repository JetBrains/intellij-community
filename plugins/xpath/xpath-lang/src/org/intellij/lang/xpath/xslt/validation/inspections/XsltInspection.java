/*
 * Copyright 2007 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.validation.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CustomSuppressableInspectionTool;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class XsltInspection extends LocalInspectionTool implements CustomSuppressableInspectionTool {
    public boolean isEnabledByDefault() {
        return true;
    }

    public SuppressIntentionAction[] getSuppressActions(@Nullable PsiElement psiElement) {
        final List<? extends IntentionAction> actions = InspectionUtil.getSuppressActions(this, false);
        return actions.toArray(new SuppressInspectionAction[actions.size()]);
    }

    public boolean isSuppressedFor(@NotNull PsiElement element) {
        return InspectionUtil.isSuppressed(this, element);
    }

    @Nls
    @NotNull
    public String getGroupDisplayName() {
        return "XSLT";
    }
}
