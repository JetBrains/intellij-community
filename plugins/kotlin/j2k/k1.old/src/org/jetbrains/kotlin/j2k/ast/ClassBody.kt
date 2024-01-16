// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.j2k.ClassKind
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append

abstract class Member(var annotations: Annotations, val modifiers: Modifiers) : Element()

class ClassBody(
        val primaryConstructor: PrimaryConstructor?,
        val primaryConstructorSignature: PrimaryConstructorSignature?,
        val baseClassParams: List<DeferredElement<Expression>>?,
        val members: List<Member>,
        val companionObjectMembers: List<Member>,
        val lBrace: LBrace,
        val rBrace: RBrace,
        val classKind: ClassKind
) {
    fun appendTo(builder: CodeBuilder) {
        val membersFiltered = members.filter { !it.isEmpty }
        if (classKind != ClassKind.ANONYMOUS_OBJECT && membersFiltered.isEmpty() && companionObjectMembers.isEmpty()) return

        builder append " " append lBrace append "\n"

        if (!classKind.isEnum()) {
            builder.append(membersFiltered.sortedByDescending { it is Property }, "\n")
        }
        else {
            val (constants, otherMembers) = membersFiltered.partition { it is EnumConstant }

            builder.append(constants, ", ")

            if (otherMembers.isNotEmpty() || companionObjectMembers.isNotEmpty()) {
                builder.append(";\n")
            }

            builder.append(otherMembers.sortedByDescending { it is Property }, "\n")
        }

        appendCompanionObject(builder, membersFiltered.isNotEmpty())

        builder append "\n" append rBrace
    }

    private fun appendCompanionObject(builder: CodeBuilder, blankLineBefore: Boolean) {
        if (companionObjectMembers.isEmpty()) return
        if (blankLineBefore) builder.append("\n\n")
        builder.append(companionObjectMembers, "\n", "companion object {\n", "\n}")
    }
}