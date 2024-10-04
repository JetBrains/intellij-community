// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.PrintWriter
import java.io.StringWriter

fun dumpApi(classSignatures: List<ApiClass>): String {
  return StringWriter().use {
    PrintWriter(it).use { printer ->
      printer.dumpApi(classSignatures)
    }
    it.buffer.toString()
  }
}

fun PrintWriter.dumpApi(classSignatures: List<ApiClass>) {
  for (classData in classSignatures) {
    printClassHeader(classData.className, classData.flags)
    printSupers(classData.supers)
    printMembers(classData.members)
  }
}

private fun PrintWriter.printClassHeader(className: String, flags: ApiFlags) {
  if (printFlags(flags, true)) {
    print(":")
  }
  print(className.dottedClassName())
  println()
}

private fun PrintWriter.printSupers(supers: List<String>) {
  for (`super` in supers) {
    print("- ")
    print(`super`.dottedClassName())
    println()
  }
}

private fun PrintWriter.printMembers(members: List<ApiMember>) {
  for (member in members) {
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
    println()
  }
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
