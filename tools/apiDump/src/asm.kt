// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Files
import java.nio.file.Path

internal fun readClass(path: Path): ClassNode {
  val classNode = ClassNode()
  Files.newInputStream(path).use { stream ->
    ClassReader(stream).accept(classNode, ClassReader.SKIP_CODE)
  }
  return classNode
}

internal fun Int.isSet(modifier: Int): Boolean = this and modifier != 0

fun String.packageName(): String {
  val endIndex = lastIndexOf('/')
  return if (endIndex < 0) {
    ""
  }
  else {
    substring(0, endIndex)
  }
}

fun String.dottedClassName(): String {
  return replace('/', '.')
}

internal fun List<AnnotationNode>?.hasAnnotation(desc: String): Boolean {
  return findAnnotation(desc) != null
}

internal fun List<AnnotationNode>?.findAnnotation(desc: String): AnnotationNode? {
  return this?.firstOrNull {
    it.desc == desc
  }
}

/**
 * @return a list of slash-separated FQNs, which are referenced from [descriptor]
 */
fun referencedFqns(descriptor: String): List<String> {
  val type = Type.getType(descriptor)
  return when (type.sort) {
    Type.METHOD -> {
      buildList {
        type.returnType.referencedType()?.let(::add)
        for (argumentType in type.argumentTypes) {
          argumentType.referencedType()?.let(::add)
        }
      }
    }
    else -> {
      listOfNotNull(type.referencedType())
    }
  }
}

private fun Type.referencedType(): String? {
  return when (sort) {
    Type.METHOD -> error("Unsupported")
    Type.OBJECT -> internalName
    Type.ARRAY -> elementType.referencedType()
    else -> null
  }
}
