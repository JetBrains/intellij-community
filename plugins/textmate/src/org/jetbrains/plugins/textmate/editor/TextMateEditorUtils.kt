package org.jetbrains.plugins.textmate.editor

fun fileNameExtensions(fileName: CharSequence): Sequence<CharSequence> {
  return generateSequence(fileNameExtension(fileName)) { s ->
    fileNameExtension(s)
  }
}

private fun fileNameExtension(fileName: CharSequence): CharSequence? {
  return when(val i = fileName.indexOf('.')) {
    -1 -> null
    else -> fileName.subSequence(i + 1, fileName.length).ifEmpty { null }
  }
}