package org.jetbrains.plugins.textmate.editor

fun fileNameExtensions(fileName: CharSequence): Sequence<CharSequence> {
  return generateSequence(fileName) { s ->
    val i = s.indexOf('.')
    val extension = if (i == -1) "" else s.subSequence(i + 1, s.length)
    extension.ifEmpty { null }
  }
}