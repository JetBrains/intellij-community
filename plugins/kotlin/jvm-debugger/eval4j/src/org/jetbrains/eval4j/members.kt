// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.eval4j

import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.FieldInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode

open class MemberDescription protected constructor(
    val ownerInternalName: String,
    val name: String,
    val desc: String,
    val isStatic: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return (other is MemberDescription
                && ownerInternalName == other.ownerInternalName
                && name == other.name
                && desc == other.desc
                && isStatic == other.isStatic
                )
    }

    override fun hashCode(): Int {
        var result = 13
        result = result * 23 + ownerInternalName.hashCode()
        result = result * 23 + name.hashCode()
        result = result * 23 + desc.hashCode()
        result = result * 23 + isStatic.hashCode()
        return result
    }

    override fun toString() = "MemberDescription(ownerInternalName = $ownerInternalName, name = $name, desc = $desc, isStatic = $isStatic)"
}

val MemberDescription.ownerType: Type
    get() = Type.getObjectType(ownerInternalName)

class MethodDescription(
    ownerInternalName: String,
    name: String,
    desc: String,
    isStatic: Boolean
) : MemberDescription(ownerInternalName, name, desc, isStatic) {
    constructor(insn: MethodInsnNode) : this(insn.owner, insn.name, insn.desc, insn.opcode == INVOKESTATIC)
}

val MethodDescription.returnType: Type
    get() = Type.getReturnType(desc)

val MethodDescription.parameterTypes: List<Type>
    get() = Type.getArgumentTypes(desc).toList()


class FieldDescription(
    ownerInternalName: String,
    name: String,
    desc: String,
    isStatic: Boolean
) : MemberDescription(ownerInternalName, name, desc, isStatic) {
    constructor(insn: FieldInsnNode) : this(insn.owner, insn.name, insn.desc, insn.opcode in setOf(GETSTATIC, PUTSTATIC))
}

val FieldDescription.fieldType: Type
    get() = Type.getType(desc)
