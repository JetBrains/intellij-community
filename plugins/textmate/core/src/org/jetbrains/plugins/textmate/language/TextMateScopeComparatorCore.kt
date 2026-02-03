package org.jetbrains.plugins.textmate.language

import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh

class TextMateScopeComparatorCore<T>(
  private val weigher: TextMateSelectorWeigher,
  private val scope: TextMateScope,
  private val scopeProvider: (T) -> CharSequence,
) : Comparator<T> {
  override fun compare(o1: T, o2: T): Int {
    return weigher.weigh(scopeProvider(o1), scope)
      .compareTo(weigher.weigh(scopeProvider(o2), scope))
  }

  fun sortAndFilter(objects: Collection<T>): List<T> {
    return objects.asSequence()
      .filter { weigher.weigh(scopeProvider(it), scope).weigh > 0 }
      .sortedWith(this.reversed())
      .toList()
  }

  fun max(objects: Collection<T>): T? {
    var max = TextMateWeigh.ZERO
    var result: T? = null
    for (o in objects) {
      val weigh: TextMateWeigh = weigher.weigh(scopeProvider(o), scope)
      if (weigh.weigh > 0 && weigh > max) {
        max = weigh
        result = o
      }
    }
    return result
  }
}