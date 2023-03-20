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

package com.jetbrains.packagesearch.intellij.plugin.kotlin

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.jetbrains.packagesearch.intellij.plugin.intentions.PackageSearchUnresolvedReferenceQuickFixProvider

class KotlinPackageSearchUnresolvedReferenceQuickFixProvider : PackageSearchUnresolvedReferenceQuickFixProvider<PsiReference>() {

    @Suppress("UNCHECKED_CAST") // We need to return a raw PsiReference as it's the common supertype
    override fun getReferenceClass(): Class<PsiReference> = try {
        Class.forName("org.jetbrains.kotlin.idea.references.KtSimpleNameReference") as Class<PsiReference>
    } catch (e: ClassNotFoundException) {
        // If for whatever reason we can't find the KtSimpleNameReference class, which is on the Kotlin plugin classpath
        DummyPsiReference::class.java as Class<PsiReference>
    }

    private class DummyPsiReference : PsiReference {

        override fun getElement(): PsiElement {
            TODO("This is a fakeReference")
        }

        override fun getRangeInElement(): TextRange {
            TODO("This is a fakeReference")
        }

        override fun resolve(): PsiElement? {
            TODO("This is a fakeReference")
        }

        override fun getCanonicalText(): String {
            TODO("This is a fakeReference")
        }

        override fun handleElementRename(newElementName: String): PsiElement {
            TODO("This is a fakeReference")
        }

        override fun bindToElement(element: PsiElement): PsiElement {
            TODO("This is a fakeReference")
        }

        override fun isReferenceTo(element: PsiElement): Boolean {
            TODO("This is a fakeReference")
        }

        override fun isSoft(): Boolean {
            TODO("This is a fakeReference")
        }
    }
}
