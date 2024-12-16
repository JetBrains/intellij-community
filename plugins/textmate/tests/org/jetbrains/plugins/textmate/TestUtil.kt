package org.jetbrains.plugins.textmate

import org.jetbrains.plugins.textmate.bundles.TextMateFileNameMatcher
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTableCore

fun TextMateSyntaxTableCore.loadBundle(bundleName: String): Map<TextMateFileNameMatcher, CharSequence> {
  val matchers = HashMap<TextMateFileNameMatcher, CharSequence>()
  val myInterner = TextMateConcurrentMapInterner()
  val grammars = TestUtil.readBundle(bundleName).readGrammars().iterator()
  while (grammars.hasNext()) {
    val grammar = grammars.next()
    addSyntax(grammar.plist.value, myInterner)?.let { rootScope ->
      grammar.fileNameMatchers.forEach { matcher ->
        matchers[matcher] = rootScope
      }
    }
  }
  return matchers
}

fun findScopeByFileName(
  matchers: Map<TextMateFileNameMatcher, CharSequence>,
  fileName: String,
): CharSequence {
  return matchers[TextMateFileNameMatcher.Name(fileName)] ?: run {
    fileNameExtensions(fileName).firstNotNullOf { extension ->
      matchers[TextMateFileNameMatcher.Extension(extension.toString())]
    }
  }
}

private fun fileNameExtensions(fileName: CharSequence): Sequence<CharSequence> {
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