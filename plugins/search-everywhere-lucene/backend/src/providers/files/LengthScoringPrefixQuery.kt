// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereLucene.backend.providers.files

import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.index.PostingsEnum
import org.apache.lucene.index.Term
import org.apache.lucene.index.TermsEnum
import org.apache.lucene.search.DocIdSetIterator
import org.apache.lucene.search.Explanation
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.QueryVisitor
import org.apache.lucene.search.ScoreMode
import org.apache.lucene.search.Scorer
import org.apache.lucene.search.ScorerSupplier
import org.apache.lucene.search.Weight
import org.apache.lucene.util.BytesRef
import java.util.PriorityQueue

/**
 * A prefix [Query] that scores each matching term by `boost × prefixLength² / termLength`.
 *
 * This gives a higher score to terms that are closer in length to the prefix (i.e. more
 * precise matches), while longer typed queries increase confidence for the same match ratio.
 *
 * Score formula: `boost × prefixLength × (prefixLength / termLength)`
 * - `prefixLength / termLength` — closer match → higher score
 * - `× prefixLength` — longer query → higher score (more typed = more confident)
 *
 * For an exact match (`prefixLength == termLength`): score = `boost × prefixLength`
 *
 * This avoids TooManyClausesException by iterating [TermsEnum]
 * directly rather than expanding to a [org.apache.lucene.search.BooleanQuery].
 *
 * Performance: document iteration uses a min-heap over matching [PostingsEnum] objects,
 * giving O(k log N) per [DocIdSetIterator.nextDoc] (k = terms matching at current doc, typically 1).
 * [Scorer.score] and [Scorer.getMaxScore] are O(1) — scores are precomputed during iteration.
 */
class LengthScoringPrefixQuery(val term: Term) : Query() {
  private val positionBoost:Int = 3
  private val prefixText: String = term.text()
  private val prefixBytes: BytesRef = term.bytes()

  // Byte length: consistent with termBytes.length used in scoring, equal to char length for ASCII.
  private val prefixLength: Int = prefixBytes.length

  override fun toString(field: String?): String = "${term.field()}:$prefixText*"

  override fun visit(visitor: QueryVisitor) {
    if (visitor.acceptField(term.field())) {
      visitor.visitLeaf(this)
    }
  }

  override fun equals(other: Any?): Boolean =
    other is LengthScoringPrefixQuery && term == other.term

  override fun hashCode(): Int = 31 * classHash() + term.hashCode()

  override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
    return object : Weight(this@LengthScoringPrefixQuery) {
      override fun isCacheable(ctx: LeafReaderContext) = true

      override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
        // Cheap existence check: seek without allocating any PostingsEnums.
        val termsField = context.reader().terms(term.field()) ?: return null
        val te = termsField.iterator()
        if (te.seekCeil(prefixBytes) == TermsEnum.SeekStatus.END) return null
        if (!te.term().startsWith(prefixBytes)) return null

        return object : ScorerSupplier() {
          override fun get(leadCost: Long): Scorer {
            // Full term enumeration deferred to here.
            val entries = collectMatchingTerms(context)
            if (entries.isNullOrEmpty()) return emptyScorer()
            return LengthScoringScorer(entries, positionBoost)
          }

          // Conservative upper bound: all docs in the field.
          override fun cost(): Long = termsField.docCount.toLong()
        }
      }

      override fun explain(context: LeafReaderContext, doc: Int): Explanation {
        // Re-walk TermsEnum here so explain() is independent of the hot-path TermEntry.
        val terms = context.reader().terms(term.field())
          ?: return Explanation.noMatch("$prefixText* — field '${term.field()}' not found")
        val te = terms.iterator()
        if (te.seekCeil(prefixBytes) == TermsEnum.SeekStatus.END)
          return Explanation.noMatch("$prefixText* does not match doc $doc")

        var bestBoost = 0f
        var bestTermText = ""
        var bestPostings: PostingsEnum? = null
        while (true) {
          val termBytes = te.term()
          if (!termBytes.startsWith(prefixBytes)) break
          val termText = termBytes.utf8ToString()
          val termBoost = boost * prefixLength * prefixLength.toFloat() / termBytes.length
          val postings = te.postings(null, PostingsEnum.POSITIONS.toInt())
          if (postings.advance(doc) == doc && termBoost > bestBoost) {
            bestBoost = termBoost
            bestTermText = termText
            bestPostings = postings
          }
          if (te.next() == null) break
        }

        return if (bestBoost == 0f) {
          Explanation.noMatch("$prefixText* does not match doc $doc")
        }
        else {
          val position = bestPostings!!.nextPosition()
          val posMultiplier = positionBoost.toFloat() / (positionBoost + position)
          Explanation.match(
            bestBoost * posMultiplier,
            "LengthScoringPrefixQuery(field=${term.field()}, prefix=$prefixText, term=$bestTermText): " +
            "boost=$boost × prefixLength²=${prefixLength * prefixLength} / termLength=${bestTermText.length} × posMultiplier=$posMultiplier (position=$position)"
          )
        }
      }

      fun collectMatchingTerms(context: LeafReaderContext): List<TermEntry>? {
        val terms = context.reader().terms(term.field()) ?: return null
        val te = terms.iterator()
        if (te.seekCeil(prefixBytes) == TermsEnum.SeekStatus.END) return null

        val result = mutableListOf<TermEntry>()
        while (true) {
          val termBytes = te.term()
          if (!termBytes.startsWith(prefixBytes)) break
          // Use byte length: consistent with prefixLength and avoids utf8ToString allocation.
          val termBoost = if (scoreMode == ScoreMode.COMPLETE_NO_SCORES) 1f
          else boost * prefixLength * prefixLength.toFloat() / termBytes.length
          val postings = te.postings(null, PostingsEnum.POSITIONS.toInt())
          result.add(TermEntry(termBoost, postings))
          if (te.next() == null) break
        }
        return result
      }

    }
  }
}

// termText removed: not needed on the iteration hot path. explain() re-walks TermsEnum independently.
private class TermEntry(val termBoost: Float, val postings: PostingsEnum)

/**
 * Scorer that iterates the union of multiple [PostingsEnum] objects via a min-heap,
 * advancing in O(k log N) per document (k = postings at the same doc, typically 1).
 *
 * The per-document score (max termBoost over all matching postings) is precomputed during
 * [DocIdSetIterator.nextDoc]/[DocIdSetIterator.advance], so [score] is O(1).
 */
private class LengthScoringScorer(entries: List<TermEntry>, private val positionBoost: Int) : Scorer() {
  private val maxBoost: Float = entries.maxOf { it.termBoost }
  private val totalCost: Long = entries.sumOf { it.postings.cost() }
  private var currentDoc: Int = -1
  private var currentScore: Float = 0f

  // Min-heap ordered by postings.docID().
  private val pq = PriorityQueue<TermEntry>(entries.size) { a, b ->
    a.postings.docID().compareTo(b.postings.docID())
  }

  // Entries that are AT currentDoc; must be advanced (via nextDoc) before they re-enter the heap.
  private val atCurrentDoc = ArrayList<TermEntry>(4)

  init {
    // Advance each posting to its first document and seed the heap.
    for (e in entries) {
      if (e.postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) pq.add(e)
    }
  }

  override fun docID(): Int = currentDoc
  override fun getMaxScore(upTo: Int): Float = maxBoost
  override fun score(): Float = currentScore

  override fun iterator(): DocIdSetIterator = object : DocIdSetIterator() {
    override fun docID(): Int = currentDoc

    override fun nextDoc(): Int {
      flushCurrentDoc()
      if (pq.isEmpty()) { currentDoc = NO_MORE_DOCS; return NO_MORE_DOCS }
      return collectCurrentDoc(pq.peek().postings.docID())
    }

    override fun advance(target: Int): Int {
      flushCurrentDoc()
      while (pq.isNotEmpty() && pq.peek().postings.docID() < target) {
        val e = pq.poll()
        if (e.postings.advance(target) != NO_MORE_DOCS) pq.add(e)
      }
      if (pq.isEmpty()) { currentDoc = NO_MORE_DOCS; return NO_MORE_DOCS }
      return collectCurrentDoc(pq.peek().postings.docID())
    }

    override fun cost(): Long = totalCost
  }

  /** Advance every entry that was at [currentDoc] into the heap at their next position. */
  private fun flushCurrentDoc() {
    for (e in atCurrentDoc) {
      val d = e.postings.nextDoc()
      if (d != DocIdSetIterator.NO_MORE_DOCS) pq.add(e)
    }
    atCurrentDoc.clear()
  }

  /** Pop all heap entries at [doc], record max score, update [currentDoc]/[currentScore]. */
  private fun collectCurrentDoc(doc: Int): Int {
    var maxScore = 0f
    var bestEntry: TermEntry? = null
    while (pq.isNotEmpty() && pq.peek().postings.docID() == doc) {
      val e = pq.poll()
      atCurrentDoc.add(e)
      if (e.termBoost > maxScore) {
        maxScore = e.termBoost
        bestEntry = e
      }
    }
    val position = bestEntry?.postings?.nextPosition() ?: 0
    currentScore = maxScore * posMultiplier(position)
    currentDoc = doc
    return doc
  }

  private fun posMultiplier(pos: Int): Float = positionBoost.toFloat() / (positionBoost + pos)
}

private fun emptyScorer(): Scorer = object : Scorer() {
  private val disi = object : DocIdSetIterator() {
    override fun docID() = NO_MORE_DOCS
    override fun nextDoc() = NO_MORE_DOCS
    override fun advance(target: Int) = NO_MORE_DOCS
    override fun cost() = 0L
  }
  override fun docID() = DocIdSetIterator.NO_MORE_DOCS
  override fun iterator() = disi
  override fun score() = 0f
  override fun getMaxScore(upTo: Int) = 0f
}

private fun BytesRef.startsWith(prefix: BytesRef): Boolean {
  if (this.length < prefix.length) return false
  for (i in 0 until prefix.length) {
    if (this.bytes[this.offset + i] != prefix.bytes[prefix.offset + i]) return false
  }
  return true
}
