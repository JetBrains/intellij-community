// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.common

import java.nio.CharBuffer
import kotlin.math.max


/* ------------------------------------------------------------------------------------------- */
//region Map2, Map3 definitions

typealias Map2<K1, K2, V> = Map<K1, Map<K2, V>>
typealias Map3<K1, K2, K3, V> = Map2<K1, K2, Map<K3, V>>

typealias MutableMap2<K1, K2, V> = MutableMap<K1, MutableMap<K2, V>>
typealias MutableMap3<K1, K2, K3, V> = MutableMap2<K1, K2, MutableMap<K3, V>>

//endregion
/* ------------------------------------------------------------------------------------------- */


/* ------------------------------------------------------------------------------------------- */
//region MapK extensions

internal inline fun <K, V, R> buildLinkedHashMapOf(pairs: Iterable<Pair<K, V>>, transformValue: (V) -> R) =
  LinkedHashMap<K, R>().apply {
    for (pair in pairs)
      put(pair.first, transformValue(pair.second))
  }

fun <K1, K2, V> mutableMap2Of(pairs: Iterable<Pair<K1, Map<K2, V>>>): MutableMap2<K1, K2, V> =
  buildLinkedHashMapOf(pairs) { it.toMutableMap() }

fun <K1, K2, V> mutableMap2Of(vararg pairs: Pair<K1, Map<K2, V>>): MutableMap2<K1, K2, V> =
  buildLinkedHashMapOf(pairs.asIterable()) { it.toMutableMap() }

fun <K1, K2, V> Map2<K1, K2, V>.toMutableMap2(): MutableMap2<K1, K2, V> =
  buildLinkedHashMapOf(toList()) { it.toMutableMap() }

fun <K1, K2, K3, V> mutableMap3Of(pairs: Iterable<Pair<K1, Map2<K2, K3, V>>>): MutableMap3<K1, K2, K3, V> =
  buildLinkedHashMapOf(pairs) { it.toMutableMap2() }

fun <K1, K2, K3, V> mutableMap3Of(vararg pairs: Pair<K1, Map2<K2, K3, V>>): MutableMap3<K1, K2, K3, V> =
  buildLinkedHashMapOf(pairs.asIterable()) { it.toMutableMap2() }

fun <K1, K2, K3, V> Map3<K1, K2, K3, V>.toMutableMap3(): MutableMap3<K1, K2, K3, V> =
  buildLinkedHashMapOf(toList()) { it.toMutableMap2() }

fun <KE, K : Iterable<KE>, V> Map<K, V>.foldKeysByLevel(
  level: Int,
  keyBuilder: (List<KE>) -> K,
  keyFolder: (List<KE>) -> Iterable<KE>?
): Map<K, V> {
  val result = mutableMapOf<K, V>()
  val folded = mutableMapOf<K, V>()
  for ((key, value) in entries) {
    val unfoldedPart = key.take(level)
    when (val foldedPart = keyFolder(unfoldedPart)) {
      null -> result[key] = value // do nothing, copy as is
      else -> folded.merge(keyBuilder(unfoldedPart + foldedPart), value) { old, new ->
        if (old == new) value
        else error("Loss of values for key: $key")
      }
    }
  }
  for (it in folded) {
    result.merge(it.key, it.value) { _, _ ->
      error("Folded context should be different from unfolded one: both are ${it.key}")
    }
  }
  return result
}

inline fun <K, V, KR, T, VR> Map<K, V>.transform(
  crossinline transformKey: (K) -> KR,
  crossinline transformValue: (V) -> T,
  crossinline mergeValues: (VR?, T) -> VR?
): MutableMap<KR, VR> =
  mutableMapOf<KR, VR>().also { result ->
    for ((key, value) in entries) {
      result.compute(transformKey(key)) { _, old ->
        mergeValues(old, transformValue(value))
      }
    }
  }

inline fun <K1, K2, V, T, K1R, K2R, VR : T> Map2<K1, K2, V>.transformMap2(
  crossinline transformKey1: (K1) -> K1R,
  crossinline transformKey2: (K2) -> K2R,
  crossinline transformValue: (V) -> T,
  crossinline mergeValues: (VR?, T) -> VR?
): MutableMap2<K1R, K2R, VR> =
  transform(
    transformKey1,
    { it.transform(transformKey2, transformValue, mergeValues) },
    { prev: MutableMap<K2R, VR>?, new: MutableMap<K2R, VR> ->
      (prev ?: mutableMapOf()).apply { mergeMap(new) { a, (_, b) -> mergeValues(a, b) } }
    }
  )

inline fun <K1, K2, K3, V, T, K1R, K2R, K3R, VR : T> Map3<K1, K2, K3, V>.transformMap3(
  crossinline transformKey1: (K1) -> K1R,
  crossinline transformKey2: (K2) -> K2R,
  crossinline transformKey3: (K3) -> K3R,
  crossinline transformValue: (V) -> T,
  crossinline mergeValues: (VR?, T) -> VR?
): MutableMap3<K1R, K2R, K3R, VR> =
  transform(
    transformKey1,
    { it.transformMap2(transformKey2, transformKey3, transformValue, mergeValues) },
    { prev: MutableMap2<K2R, K3R, VR>?, new: MutableMap2<K2R, K3R, VR> ->
      (prev ?: mutableMapOf()).apply { mergeMap2(new) { a, (_, b) -> mergeValues(a, b) } }
    }
  )

inline fun <K1, K2, V> Map2<K1, K2, V>.filterInner(
  predicate: (K1, K2, V) -> Boolean
): Map2<K1, K2, V> =
  mapValues { (key1, innerMap) ->
    innerMap.filter { (key2, value) -> predicate(key1, key2, value) }
  }


inline fun <K, V, VThat> MutableMap<K, V>.mergeMap(
  that: Map<K, VThat>,
  crossinline mergeValues: (V?, Map.Entry<K, VThat>) -> V?
) {
  for (entry in that.entries) {
    // see KT-41174
    //compute(entry.key) { _, prev -> mergeValues(prev, entry) }
    when (val t = mergeValues(get(entry.key), entry)) {
      null -> remove(entry.key)
      else -> put(entry.key, t)
    }
  }
}

inline fun <K1, K2, V, VThat> MutableMap2<K1, K2, V>.mergeMap2(
  that: Map2<K1, K2, VThat>,
  crossinline mergeValues: (V?, Map.Entry<K2, VThat>) -> V?
) {
  mergeMap(that) { prev, (_, innerMap) ->
    (prev ?: mutableMapOf()).apply { mergeMap(innerMap, mergeValues) }
  }
}

inline fun <K1, K2, K3, V, VThat> MutableMap3<K1, K2, K3, V>.mergeMap3(
  that: Map3<K1, K2, K3, VThat>,
  crossinline mergeValues: (V?, Map.Entry<K3, VThat>) -> V?
) {
  mergeMap(that) { prev, (_, innerMap) ->
    (prev ?: mutableMapOf()).apply { mergeMap2(innerMap, mergeValues) }
  }
}

//endregion
/* ------------------------------------------------------------------------------------------- */


/* ------------------------------------------------------------------------------------------- */
//region Map pretty printing

inline fun <A : Appendable, K, V> A.printMapAsAlignedLists(
  map: Map<K, V>,
  keyComparator: Comparator<K>,
  prefix: String = "[",
  separator: String = ", ",
  postfix: String = "]",
  alignByRight: Boolean = false,
  keyToList: (K) -> List<String>,
  valuePrinter: A.(K, V) -> Unit = { _, value -> append(value.toString()) }
): A {
  val stringifiedKeys = map.mapValues { keyToList(it.key) }

  val maxContextLength = stringifiedKeys.values.maxBy { it.size }?.size ?: 10
  val contextElementWidths = stringifiedKeys.values
    .fold(MutableList(maxContextLength) { 1 }) { acc, stringifiedContext ->
      stringifiedContext.forEachIndexed { i, s ->
        val index = if (alignByRight) maxContextLength - stringifiedContext.size + i else i
        acc[index] = max(acc[index], s.length)
      }
      acc
    }

  return printMap(map, keyComparator = keyComparator) { key, value ->
    val sContext = stringifiedKeys.getValue(key)
    contextElementWidths
      .mapIndexed { i, width ->
        "%-${width}s".format(
          sContext.getOrNull(if (alignByRight) i + sContext.size - maxContextLength else i) ?: "")
      }
      .joinTo(this, prefix = prefix, separator = separator, postfix = postfix)

    valuePrinter(key, value)
  }
}

fun <A : Appendable, K, V> A.printMapAsAlignedTrees(
  map: Map<K, V>,
  margin: CharSequence,
  leafKey: String = ".",
  keyToList: (K) -> List<String>,
  keyPrinter: A.(String, Int) -> Unit = { key, _ -> append(key.toString()) },
  valuePrinter: A.(K, V) -> Unit = { _, value -> append(value.toString()) }
): A {
  val stringifiedKeys = map.mapValues { keyToList(it.key) }

  val tree = map.toList()
    .map { (key, value) -> Pair(stringifiedKeys.getValue(key), Pair(key, value)) }
    .toMap()
    .uncurry(leafKey)

  // for pretty alignment
  val valuePadding = max(1, stringifiedKeys.values.asSequence().map { it.lastOrNull()?.length ?: 0 }.max()!!)
  val treeDepth = stringifiedKeys.values.asSequence().map { it.size }.max()!!

  prettyPrintTree(
    tree,
    margin,
    keyComparator = Comparator.naturalOrder(),
    keyPrinter = { key, t ->
      val children = (t as Node).children
      when (val child = children.getValue(key)) {
        is Node -> keyPrinter(key, 1)
        is Leaf -> {
          val missedMarginCompensation =
            margin.length * (treeDepth - (stringifiedKeys[child.value.first]?.size ?: 0))

          keyPrinter(key, missedMarginCompensation + valuePadding)
        }
      }
    },
    valPrinter = { (context, targets), _ -> valuePrinter(context, targets) })

  return this
}

inline fun <A : Appendable, K, V> A.printMap(
  map: Map<K, V>,
  separator: CharSequence = "\n",
  keyComparator: Comparator<K> = Comparator { _, _ -> 0 },
  keyValuePrinter: A.(K, V) -> Unit = { key, value -> append(key.toString()); append(value.toString()) }
): A {
  map
    .toList()
    .sortedWith(Comparator { o1, o2 -> keyComparator.compare(o1.first, o2.first) })
    .forEachIndexed { index, (key, value) ->
      if (index > 0) append(separator)
      keyValuePrinter(key, value)
    }
  return this
}

private fun <A : Appendable, K, V> A.prettyPrintTree(
  tree: Tree<K, V>,
  margin: CharSequence,
  keyComparator: Comparator<K> = Comparator { _, _ -> 0 },
  keyPrinter: Appendable.(K, Tree<K, V>) -> Unit = { key, _ -> append(key.toString()) },
  valPrinter: Appendable.(V, Tree<K, V>) -> Unit = { value, _ -> append(value.toString()) }
): Unit {
  when (tree) {
    is Node ->
      printMap(tree.children, keyComparator = keyComparator) t@{ key, value ->
        keyPrinter(key, tree)
        when (value) {
          is Node ->
            this@t.withMargin(margin) m@{
              this@m.prettyPrintTree(value, margin, keyComparator, keyPrinter, valPrinter)
            }
          is Leaf -> valPrinter(value.value, tree)
        }
      }
    is Leaf -> valPrinter(tree.value, tree)
  }
}

private sealed class Tree<out K, out V>
private data class Node<K, V>(val children: Map<K, Tree<K, V>>) : Tree<K, V>()
private data class Leaf<V>(val value: V) : Tree<Nothing, V>()

/**
 * Generalized uncurrying:
 *
 * Map<(k, k, ..., k), v> <=> (k, k, ..., k) -> v <=> k -> (k -> ... (k -> v)) => Tree<k, v>
 */
private fun <K, V> Map<List<K>, V>.uncurry(leafKey: K): Tree<K, V> =
  this.entries
    .partition { it.key.isEmpty() }
    .let { (leafCandidate, childrenCandidates) ->
      val leaf = leafCandidate.singleOrNull()?.value?.let { Leaf(it) }
      when {
        childrenCandidates.isEmpty() -> leaf ?: error("Empty or invalid map")
        else -> {
          childrenCandidates
            .groupingBy { it.key.first() }
            .fold({ _, _ -> mutableMapOf<List<K>, V>() }) { _, innerMap, entry ->
              innerMap[entry.key.drop(1)] = entry.value
              innerMap
            }
            .mapValuesTo(mutableMapOf()) { it.value.uncurry(leafKey) }
            .let { children ->
              when {
                leafCandidate.isEmpty() -> Node(children)
                else ->
                  children.put(leafKey, leaf ?: error("Empty or invalid map"))
                    ?.let { error("leaf key must be unique") }
                  ?: Node(children)
              }
            }
        }
      }
    }

//endregion
/* ------------------------------------------------------------------------------------------- */


/* ------------------------------------------------------------------------------------------- */
//region Utils

internal fun <A : Appendable> A.appendReplacing(target: CharSequence, old: String, new: String): A {
  var begin = 0
  var end = target.indexOf(old)
  while (end != -1) {
    append(target, begin, end)
    append(new)
    begin = end + old.length
    end = target.indexOf(old, begin)
  }
  append(target, begin, target.length)
  return this
}

internal fun <A : Appendable> A.withMargin(margin: CharSequence,
                                           withTrailingNewLine: Boolean = true,
                                           targetInit: Appendable.() -> Unit): A {
  object : Appendable {
    val newLineMargin = "\n" + margin

    override fun append(csq: CharSequence?): java.lang.Appendable {
      this@withMargin.appendReplacing<A>(csq ?: "null", "\n", newLineMargin)
      return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): java.lang.Appendable {
      return append(csq?.let { CharBuffer.wrap(it).subSequence(start, end) })
    }

    override fun append(c: Char): java.lang.Appendable {
      this@withMargin.append(c)
      return this
    }
  }
    .apply { if (withTrailingNewLine) appendln() }.targetInit()

  return this
}

//endregion
/* ------------------------------------------------------------------------------------------- */