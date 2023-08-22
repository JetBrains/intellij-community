/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.intentions

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiUtil

abstract class PackageSearchUnresolvedReferenceQuickFixProvider<T : PsiReference> : UnresolvedReferenceQuickFixProvider<T>() {

    override fun registerFixes(ref: T, registrar: QuickFixActionRegistrar) {
        if (!PsiUtil.isModuleFile(ref.element.containingFile)) {
            registrar.register(PackageSearchUnresolvedReferenceQuickFix(ref))
        }
    }
}
