// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.bindToElement

import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement

abstract class AbstractK2BindToFqnTest : AbstractK2BindToTest() {
    override fun bindElement(refElement: KtElement) {
        val nameToBind = InTextDirectivesUtils.findStringWithPrefixes(file.text, BIND_TO) ?: return
        val mainReference = refElement.mainReference ?: error("Ref element doesn't have a main reference")
        myFixture.project.executeWriteCommand("bindToElement") {
            if (mainReference !is KtSimpleNameReference) error("Main reference should be simple name reference")
            mainReference.bindToFqName(FqName(nameToBind))
        }
        myFixture.checkResultByFile("${dataFile().name}.after")
    }
}