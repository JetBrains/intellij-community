// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.PrintWriter
import java.io.StringWriter

fun dumpApi(classSignatures: List<ApiClass>): String {
  return reifyToString { dumpApi(classSignatures) }
}

typealias ClassName = String
typealias ClassMembers = List<String>

fun dumpApiAndGroupByClasses(classSignatures: List<ApiClass>): Map<ClassName, ClassMembers> {
  val collectingMap = mutableMapOf<ClassName, ClassMembers>()
  for (signature in classSignatures) {
    val header = reifyToString { printClassHeader(signature.className, signature.flags) }
    val list = ArrayList<String>(signature.supers.size + signature.members.size)
    signature.supers.mapTo(list) { `super` -> reifyToString { printSuper(`super`) } }
    signature.members.mapTo(list) { member -> reifyToString { printMember(member) } }
    collectingMap[header] = list
  }
  return collectingMap
}

private fun reifyToString(action: PrintWriter.() -> Unit): String {
  return StringWriter().use {
    PrintWriter(it).use { printer -> printer.action() }
    it.buffer.toString()
  }
}

fun PrintWriter.dumpApi(classSignatures: List<ApiClass>) {
  for (classData in classSignatures) {
    printClassHeader(classData.className, classData.flags)
    printNewLine()
    for (`super` in classData.supers) {
      printSuper(`super`)
      printNewLine()
    }
    for (member in classData.members) {
      printMember(member)
      printNewLine()
    }
  }
}

private fun PrintWriter.printClassHeader(className: String, flags: ApiFlags) {
  if (printFlags(flags, true)) {
    print(":")
  }
  print(className.dottedClassName())
}

private fun PrintWriter.printSuper(`super`: String) {
  print("- ")
  print(`super`.dottedClassName())
}

private fun PrintWriter.printMember(member: ApiMember) {
  print("- ")
  if (printFlags(member.flags, false)) {
    print(":")
  }
  print(member.ref.name)
  val type = Type.getType(member.ref.descriptor)
  if (type.sort == Type.METHOD) {
    print(type.argumentTypes.joinToString(prefix = "(", separator = ",", postfix = ")", transform = Type::typeString))
    print(':')
    print(type.returnType.typeString())
  }
  else {
    print(':')
    print(type.typeString())
  }
}

private fun PrintWriter.printNewLine() {
  print('\n')
}

private fun Type.typeString(): String = when (sort) {
  Type.ARRAY -> elementType.typeStringX() + "[]".repeat(dimensions)
  else -> typeStringX()
}

private fun Type.typeStringX(): String = when (sort) {
  Type.ARRAY,
  Type.METHOD -> error("unsupported $sort")
  Type.OBJECT -> className
  else -> descriptor
}

fun Appendable.printFlags(flags: ApiFlags, isClass: Boolean): Boolean {
  var hasModifier = false
  if (flags.annotationExperimental) {
    append('*')
    hasModifier = true
  }
  if (flags.access.isSet(Opcodes.ACC_SYNTHETIC)) {
    append('b') // as in 'binary visible'
    hasModifier = true
  }
  if (flags.access.isSet(Opcodes.ACC_PROTECTED)) {
    // nothing for public, it's default in dump
    append('p')
    hasModifier = true
  }
  if (flags.access.isSet(Opcodes.ACC_STATIC)) {
    append('s')
    hasModifier = true
  }
  if (isClass) {
    if (flags.access.isSet(Opcodes.ACC_ANNOTATION)) {
      append('@')
      hasModifier = true
    }
    else if (flags.access.isSet(Opcodes.ACC_INTERFACE)) {
      // this is default in dump
    }
    else if (flags.access.isSet(Opcodes.ACC_ENUM)) {
      append('e') // we don't care if ACC_ENUM class has ACC_ABSTRACT modifier
      hasModifier = true
    }
    else {
      if (flags.annotationNonExtendable) {
        append('F') // like `final`
      }
      if (flags.access.isSet(Opcodes.ACC_FINAL)) {
        append('f')
        hasModifier = true
      }
      else if (flags.access.isSet(Opcodes.ACC_ABSTRACT)) {
        append('a')
        hasModifier = true
      }
      else {
        append('c')
        hasModifier = true
      }
    }
  }
  else {
    if (flags.annotationNonExtendable) {
      append('F')
      hasModifier = true
    }
    if (flags.access.isSet(Opcodes.ACC_FINAL)) {
      append('f')
      hasModifier = true
    }
    else if (flags.access.isSet(Opcodes.ACC_ABSTRACT)) {
      append('a')
      hasModifier = true
    }
  }
  return hasModifier
}
