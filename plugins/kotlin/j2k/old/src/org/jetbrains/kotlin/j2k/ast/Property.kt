// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.j2k.AccessorKind
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.getDefaultInitializer

class Property(
  val identifier: Identifier,
  annotations: Annotations,
  modifiers: Modifiers,
  val isVar: Boolean,
  val type: Type,
  private val explicitType: Boolean,
  private val initializer: DeferredElement<Expression>,
  private val needInitializer: Boolean,
  private val getter: PropertyAccessor?,
  private val setter: PropertyAccessor?,
  private val isInInterface: Boolean
) : Member(annotations, modifiers) {

    private fun presentationModifiers(): Modifiers {
        var modifiers = this.modifiers
        if (isInInterface) {
            modifiers = modifiers.without(Modifier.ABSTRACT)
        }

        if (modifiers.contains(Modifier.OVERRIDE)) {
            modifiers = modifiers.filter { it != Modifier.OPEN }
        }

        return modifiers
    }

    override fun generateCode(builder: CodeBuilder) {
        builder.append(annotations)
            .appendWithSpaceAfter(presentationModifiers())
            .append(if (isVar) "var " else "val ")
            .append(identifier)

        if (explicitType) {
            builder append ":" append type
        }

        var initializerToUse: Element = initializer
        if (initializerToUse.isEmpty && needInitializer) {
            initializerToUse = getDefaultInitializer(this) ?: Empty
        }
        if (!initializerToUse.isEmpty) {
            builder append " = " append initializerToUse
        }

        if (getter != null) {
            builder append "\n" append getter
        }

        if (setter != null) {
            builder append "\n" append setter
        }
    }
}

class PropertyAccessor(
    private val kind: AccessorKind,
    annotations: Annotations,
    modifiers: Modifiers,
    parameterList: ParameterList?,
    body: DeferredElement<Block>?
) : FunctionLike(annotations, modifiers, parameterList, body) {

    override fun generateCode(builder: CodeBuilder) {
        builder.append(annotations)

        builder.appendWithSpaceAfter(presentationModifiers())

        when (kind) {
            AccessorKind.GETTER -> builder.append("get")
            AccessorKind.SETTER -> builder.append("set")
        }

        if (parameterList != null) {
            builder.append(parameterList)
        }

        if (body != null) {
            builder.append(" ").append(body)
        }
    }
}
