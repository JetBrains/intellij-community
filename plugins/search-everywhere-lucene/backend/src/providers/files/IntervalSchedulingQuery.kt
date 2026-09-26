// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereLucene.backend.providers.files

import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.DocIdSetIterator
import org.apache.lucene.search.Explanation
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.QueryVisitor
import org.apache.lucene.search.ScoreMode
import org.apache.lucene.search.Scorer
import org.apache.lucene.search.ScorerSupplier
import org.apache.lucene.search.TwoPhaseIterator
import org.apache.lucene.search.Weight
import java.util.Collections
import java.util.IdentityHashMap
import java.util.PriorityQueue

/**
 * Represents a token from the search analyzer as a query-string interval.
 *
 * @param startOffset inclusive start in the compressed, word-relative query position space
 * @param endOffset   exclusive end
 * @param query       the underlying Lucene query for this token; wrap in [org.apache.lucene.search.BoostQuery] to control scoring weight
 */
data class QueryInterval(
  val startOffset: Int,
  val endOffset: Int,
  val query: Query,
)

/**
 * A Lucene [Query] that uses weighted interval scheduling to require exhaustive coverage
 * of the query string.
 *
 * Each [QueryInterval] maps an analyzer token to a `[startOffset, endOffset)` range over
 * the (compressed, word-relative) query position space. A document matches only if the
 * union of all matching intervals covers `[0, queryLength)` completely (no gaps). The score
 * is the maximum-weight non-overlapping subset of matching intervals found via dynamic
 * programming, where each interval's weight is its Lucene query score.
 */
class IntervalSchedulingQuery(
  val intervals: List<QueryInterval>,
  val queryLength: Int,
) : Query() {

  override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
    val childWeights = intervals.map { it to it.query.createWeight(searcher, scoreMode, boost) }
    return IntervalSchedulingWeight(this, childWeights, queryLength)
  }

  override fun rewrite(searcher: IndexSearcher): Query {
    var changed = false
    val newIntervals = intervals.map { interval ->
      val rewritten = interval.query.rewrite(searcher)
      if (rewritten !== interval.query) {
        changed = true
        interval.copy(query = rewritten)
      }
      else interval
    }
    return if (changed) IntervalSchedulingQuery(newIntervals, queryLength) else this
  }

  override fun visit(visitor: QueryVisitor) {
    val sub = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this)
    intervals.forEach { it.query.visit(sub) }
  }

  override fun toString(field: String): String =
    "IntervalSchedulingQuery(len=$queryLength, [${intervals.joinToString { "[${it.startOffset},${it.endOffset}):${it.query}" }}])"

  override fun equals(other: Any?): Boolean =
    this === other || (other is IntervalSchedulingQuery && queryLength == other.queryLength && intervals == other.intervals)

  override fun hashCode(): Int = 31 * queryLength + intervals.hashCode()
}

internal class IntervalSchedulingWeight(
  query: Query,
  private val childWeightPairs: List<Pair<QueryInterval, Weight>>,
  private val queryLength: Int,
) : Weight(query) {

  override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
    val pairs = childWeightPairs.mapNotNull { (interval, weight) ->
      weight.scorer(context)?.let { interval to it }
    }
    if (pairs.isEmpty()) return null
    return object : ScorerSupplier() {
      override fun get(leadCost: Long): Scorer = IntervalSchedulingScorer(pairs, queryLength)
      override fun cost(): Long = pairs.sumOf { (_, scorer) -> scorer.iterator().cost() }
    }
  }

  override fun isCacheable(ctx: LeafReaderContext): Boolean =
    childWeightPairs.all { (_, w) -> w.isCacheable(ctx) }

  override fun explain(context: LeafReaderContext, doc: Int): Explanation {
    val childExplanations: List<Pair<QueryInterval, Explanation>> =
      childWeightPairs.map { (interval, weight) -> interval to weight.explain(context, doc) }

    val matchingPairs = childExplanations.filter { (_, exp) -> exp.isMatch }
    val matchingIntervals = matchingPairs.map { it.first }
    val matchingScores = matchingPairs.map { it.second.value.toFloat() }
    val gapOffset = firstUncoveredOffset(matchingIntervals, queryLength)

    if (gapOffset >= 0) {
      val details = childExplanations.map { (interval, exp) ->
        val label = "[${interval.startOffset},${interval.endOffset}): ${interval.query}"
        if (exp.isMatch) Explanation.match(exp.value, "matched: $label", exp)
        else Explanation.noMatch("no match: $label", exp)
      }
      return Explanation.noMatch(
        "coverage gap at offset $gapOffset (queryLength=$queryLength)",
        details,
      )
    }

    val selectedSet = dpSelectedIntervals(matchingIntervals, matchingScores, queryLength).toCollection(
      Collections.newSetFromMap(IdentityHashMap())
    )
    val matchedPairs = mutableListOf<Pair<Int, Explanation>>()
    val matchedNotSelPairs = mutableListOf<Pair<Int, Explanation>>()
    val noMatchPairs = mutableListOf<Pair<Int, Explanation>>()
    for ((interval, exp) in childExplanations) {
      val label = "[${interval.startOffset},${interval.endOffset}): ${interval.query}"
      when {
        interval in selectedSet -> matchedPairs += interval.startOffset to Explanation.match(exp.value, label, exp)
        exp.isMatch -> matchedNotSelPairs += interval.startOffset to Explanation.noMatch(label, exp)
        else -> noMatchPairs += interval.startOffset to Explanation.noMatch(label)
      }
    }

    fun List<Pair<Int, Explanation>>.sorted() = sortedBy { it.first }.map { it.second }

    val allItems = buildList {
      if (matchedPairs.isNotEmpty()) {
        add(Explanation.noMatch("Matched:"))
        addAll(matchedPairs.sorted())
      }
      if (matchedNotSelPairs.isNotEmpty()) add(Explanation.noMatch("Matched but not selected:", matchedNotSelPairs.sorted()))
      if (noMatchPairs.isNotEmpty()) add(Explanation.noMatch("No Match:", noMatchPairs.sorted()))
    }

    return Explanation.match(
      dpScore(matchingIntervals, matchingScores, queryLength),
      "IntervalSchedulingQuery:",
      allItems,
    )
  }
}

internal class IntervalSchedulingScorer(
  childScorerPairs: List<Pair<QueryInterval, Scorer>>,
  private val queryLength: Int,
) : Scorer() {

  private class ChildEntry(
    val interval: QueryInterval,
    val scorer: Scorer,
    val tpi: TwoPhaseIterator?,
    val approx: DocIdSetIterator,
  )

  private val children: List<ChildEntry> = childScorerPairs.map { (interval, scorer) ->
    val tpi = scorer.twoPhaseIterator()
    ChildEntry(interval, scorer, tpi, tpi?.approximation() ?: scorer.iterator())
  }
    .sortedBy { it.interval.startOffset }

  private val unionApprox = UnionDISI(children.map { it.approx })

  private var cachedDoc = -1
  private var cachedIntervals: List<QueryInterval> = emptyList()
  private var cachedScores: List<Float> = emptyList()

  private val twoPhase = object : TwoPhaseIterator(unionApprox) {
    override fun matches(): Boolean {
      val doc = unionApprox.docID()
      val matchingChildren = children
        .filter { child -> child.approx.docID() == doc && (child.tpi?.matches() ?: true) }
      val matching = matchingChildren.map { it.interval }
      return if (coversAllPresorted(matching, queryLength)) {
        cachedDoc = doc
        cachedIntervals = matching
        cachedScores = matchingChildren.map { it.scorer.score() }
        true
      }
      else false
    }

    override fun matchCost(): Float = children.size.toFloat() * 5f
  }

  override fun twoPhaseIterator(): TwoPhaseIterator = twoPhase
  override fun iterator(): DocIdSetIterator = TwoPhaseIterator.asDocIdSetIterator(twoPhase)
  override fun docID(): Int = unionApprox.docID()

  override fun score(): Float {
    val doc = unionApprox.docID()
    val (intervals, scores) = if (cachedDoc == doc) cachedIntervals to cachedScores
    else {
      val matchingChildren = children.filter { it.approx.docID() == doc }
      matchingChildren.map { it.interval } to matchingChildren.map { it.scorer.score() }
    }
    return dpScore(intervals, scores, queryLength)
  }

  private val maxScore: Float =
    childScorerPairs.sumOf { (_, scorer) -> scorer.getMaxScore(Int.MAX_VALUE).toDouble() }.toFloat()

  override fun getMaxScore(upTo: Int): Float = maxScore

  private fun coversAllPresorted(sortedIntervals: List<QueryInterval>, queryLength: Int): Boolean {
    if (queryLength == 0) return true
    if (sortedIntervals.isEmpty()) return false
    var maxReached = 0
    for (interval in sortedIntervals) {
      if (interval.startOffset > maxReached) return false
      if (interval.endOffset > maxReached) maxReached = interval.endOffset
    }
    return maxReached >= queryLength
  }
}

/**
 * Returns the first offset in `[0, queryLength)` not covered by any interval, or `-1` if fully covered.
 */
internal fun firstUncoveredOffset(intervals: List<QueryInterval>, queryLength: Int): Int {
  if (queryLength == 0) return -1
  if (intervals.isEmpty()) return 0
  val sorted = intervals.sortedBy { it.startOffset }
  var maxReached = 0
  for (interval in sorted) {
    if (interval.startOffset > maxReached) return maxReached
    if (interval.endOffset > maxReached) maxReached = interval.endOffset
  }
  return if (maxReached >= queryLength) -1 else maxReached
}

/**
 * Returns `true` if the union of [intervals] covers `[0, queryLength)` with no gaps.
 */
internal fun coversAll(intervals: List<QueryInterval>, queryLength: Int): Boolean {
  if (queryLength == 0) return true
  if (intervals.isEmpty()) return false
  val sorted = intervals.sortedBy { it.startOffset }
  var maxReached = 0
  for (interval in sorted) {
    if (interval.startOffset > maxReached) return false
    if (interval.endOffset > maxReached) maxReached = interval.endOffset
  }
  return maxReached >= queryLength
}

/**
 * Runs the same DP as [dpScore] but traces back to return the selected intervals.
 */
internal fun dpSelectedIntervals(intervals: List<QueryInterval>, scores: List<Float>, queryLength: Int): List<QueryInterval> {
  if (intervals.isEmpty() || queryLength == 0) return emptyList()
  val byEnd = intervals.indices.groupBy { intervals[it].endOffset }
  val dp = FloatArray(queryLength + 1)
  val chosen = IntArray(queryLength + 1) { -1 }
  for (e in 1..queryLength) {
    dp[e] = dp[e - 1]
    byEnd[e]?.forEach { i ->
      val s = intervals[i].startOffset.coerceIn(0, queryLength)
      val candidate = dp[s] + scores[i]
      if (candidate > dp[e]) { dp[e] = candidate; chosen[e] = i }
    }
  }
  val selected = mutableListOf<QueryInterval>()
  var pos = queryLength
  while (pos > 0) {
    val i = chosen[pos]
    if (i >= 0) { selected.add(intervals[i]); pos = intervals[i].startOffset.coerceIn(0, queryLength) }
    else pos--
  }
  return selected
}

/**
 * Computes the maximum-weight non-overlapping subset score via DP.
 * Assumes [coversAll] has already confirmed that full coverage is achievable.
 */
internal fun dpScore(intervals: List<QueryInterval>, scores: List<Float>, queryLength: Int): Float {
  if (intervals.isEmpty() || queryLength == 0) return 0f
  val byEnd = intervals.indices.groupBy { intervals[it].endOffset }
  val dp = FloatArray(queryLength + 1)
  for (e in 1..queryLength) {
    dp[e] = dp[e - 1]
    byEnd[e]?.forEach { i ->
      val s = intervals[i].startOffset.coerceIn(0, queryLength)
      val candidate = dp[s] + scores[i]
      if (candidate > dp[e]) dp[e] = candidate
    }
  }
  return dp[queryLength]
}

/**
 * A [DocIdSetIterator] that is the logical OR (union) of multiple child iterators.
 *
 * Advances to each document where at least one child is positioned. Children that are AT
 * the current document are NOT advanced until the next call to [nextDoc] or [advance],
 * allowing callers to inspect `child.docID()` to identify which children matched.
 */
internal class UnionDISI(iterators: List<DocIdSetIterator>) : DocIdSetIterator() {
  private var doc = -1

  // Min-heap ordered by current docID.
  private val pq = PriorityQueue<DocIdSetIterator>(maxOf(1, iterators.size)) { a, b ->
    a.docID().compareTo(b.docID())
  }

  // Iterators positioned at the current document; advanced lazily on the next nextDoc()/advance() call.
  private val atDoc = ArrayList<DocIdSetIterator>(4)

  private val totalCost = iterators.sumOf { it.cost() }

  init {
    for (iter in iterators) {
      if (iter.nextDoc() != NO_MORE_DOCS) pq.add(iter)
    }
  }

  override fun docID() = doc

  override fun nextDoc(): Int {
    for (iter in atDoc) { if (iter.nextDoc() != NO_MORE_DOCS) pq.add(iter) }
    atDoc.clear()
    if (pq.isEmpty()) { doc = NO_MORE_DOCS; return NO_MORE_DOCS }
    return collectCurrentDoc(pq.peek().docID())
  }

  override fun advance(target: Int): Int {
    for (iter in atDoc) { if (iter.advance(target) != NO_MORE_DOCS) pq.add(iter) }
    atDoc.clear()
    while (pq.isNotEmpty() && pq.peek().docID() < target) {
      val iter = pq.poll()
      if (iter.advance(target) != NO_MORE_DOCS) pq.add(iter)
    }
    if (pq.isEmpty()) { doc = NO_MORE_DOCS; return NO_MORE_DOCS }
    return collectCurrentDoc(pq.peek().docID())
  }

  override fun cost() = totalCost

  /** Pops all heap entries at [d] into [atDoc] and sets [doc]. */
  private fun collectCurrentDoc(d: Int): Int {
    while (pq.isNotEmpty() && pq.peek().docID() == d) atDoc.add(pq.poll())
    doc = d
    return d
  }
}
