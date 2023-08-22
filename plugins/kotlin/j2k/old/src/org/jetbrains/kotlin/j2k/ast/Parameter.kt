// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.j2k.CodeBuilder

abstract class Parameter(val identifier: Identifier, val type: Type?) : Element()

class FunctionParameter(
  identifier: Identifier,
  type: Type?,
  private val varVal: VarValModifier,
  val annotations: Annotations,
  val modifiers: Modifiers,
  val defaultValue: DeferredElement<Expression>? = null
) : Parameter(identifier, type) {
    enum class VarValModifier {
        None,
        Val,
        Var
    }

    override fun generateCode(builder: CodeBuilder) {
        builder.append(annotations).appendWithSpaceAfter(modifiers)

        if (type is VarArgType) {
            builder.append("vararg ")
        }

        when (varVal) {
            VarValModifier.Var -> builder.append("var ")
            VarValModifier.Val -> builder.append("val ")
            VarValModifier.None -> {
            }
        }

        builder.append(identifier)

        if (type != null) {
            builder append ":" append type
        }

        if (defaultValue != null) {
            builder append " = " append defaultValue
        }
    }
}

class LambdaParameter(identifier: Identifier, type: Type?) : Parameter(identifier, type) {
    override fun generateCode(builder: CodeBuilder) {
        builder append identifier

        if (type != null) {
            builder append ":" append type
        }
    }
}