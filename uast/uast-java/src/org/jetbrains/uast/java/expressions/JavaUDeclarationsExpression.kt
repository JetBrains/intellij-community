/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.uast.java

import com.intellij.psi.PsiElement
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UElement

class JavaUDeclarationsExpression(
        override val uastParent: UElement?
) : UDeclarationsExpression {
    override lateinit var declarations: List<UDeclaration>
        internal set

    constructor(parent: UElement?, declarations: List<UDeclaration>) : this(parent) {
        this.declarations = declarations
    }

    override val annotations: List<UAnnotation>
        get() = emptyList()

    override val psi: PsiElement?
        get() = null
}