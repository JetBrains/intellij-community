package org.jetbrains.plugins.textmate.language.syntax

import org.jetbrains.plugins.textmate.Constants

/**
 * Memory-compact implementation of [SyntaxNodeDescriptor].
 *
 * All node payload is packed into a single [data] array to avoid per-node overhead
 * of separate collections and fixed-size attribute tables:
 * - string attributes that are present, ordered by [Constants.StringKey.ordinal];
 * - capture arrays that are present, ordered by [Constants.CaptureKey.ordinal];
 * - children nodes.
 *
 * [meta] holds the presence bitmasks: bit `ordinal` is set when the corresponding
 * [Constants.StringKey] attribute is present, bit `CAPTURES_SHIFT + ordinal` is set
 * when the corresponding [Constants.CaptureKey] captures are present.
 * The index of an element in [data] is the number of lower presence bits set in [meta],
 * children start right after the last present attribute.
 */
internal class SyntaxNodeDescriptorImpl private constructor(
  private val meta: Int,
  private val data: Array<Any?>,
) : SyntaxNodeDescriptor {
  companion object {
    private val CAPTURES_SHIFT = Constants.StringKey.entries.size

    private val EMPTY_NODE_DATA: Array<Any?> = arrayOfNulls(0)
    init {
      check(CAPTURES_SHIFT + Constants.CaptureKey.entries.size <= 32) {
        "Captures presence mask doesn't fit into Int"
      }
    }

    private fun stringKeyBit(key: Constants.StringKey): Int = 1 shl key.ordinal

    private fun captureKeyBit(key: Constants.CaptureKey): Int = 1 shl (CAPTURES_SHIFT + key.ordinal)

    /**
     * Packs the node payload into the compact [meta]/[data] representation.
     *
     * @param children compiled children nodes, in order.
     * @param captures either `null` or an array sized [Constants.CaptureKey.entries], indexed by
     *   [Constants.CaptureKey.ordinal], holding the capture rules for present keys and `null` otherwise.
     * @param stringAttributes either `null` or an array sized [Constants.StringKey.entries], indexed by
     *   [Constants.StringKey.ordinal], holding the value for present keys and `null` otherwise.
     */
    fun create(
      children: List<SyntaxNodeDescriptor>,
      captures: Array<Array<TextMateCapture?>?>?,
      stringAttributes: Array<CharSequence?>?,
    ): SyntaxNodeDescriptorImpl {
      require(stringAttributes == null || stringAttributes.size == Constants.StringKey.entries.size) {
        "stringAttributes must be either null or define all StringKey entries"
      }
      require(captures == null || captures.size == Constants.CaptureKey.entries.size) {
        "captures must be either null or define all CaptureKey entries"
      }
      var meta = 0
      val payload = buildList {
        if (stringAttributes != null) {
          for (key in Constants.StringKey.entries) {
            val value = stringAttributes[key.ordinal] ?: continue
            meta = meta or stringKeyBit(key)
            add(value)
          }
        }
        if (captures != null) {
          for (key in Constants.CaptureKey.entries) {
            val value = captures[key.ordinal] ?: continue
            meta = meta or captureKeyBit(key)
            add(value)
          }
        }
        addAll(children)
      }
      return SyntaxNodeDescriptorImpl(meta, if (payload.isEmpty()) EMPTY_NODE_DATA else payload.toTypedArray())
    }
  }

  override fun getStringAttribute(key: Constants.StringKey): CharSequence? {
    val bit = stringKeyBit(key)
    return if (meta and bit != 0) data[(meta and (bit - 1)).countOneBits()] as CharSequence else null
  }

  override fun getCaptureRules(key: Constants.CaptureKey): Array<TextMateCapture?>? {
    val bit = captureKeyBit(key)
    @Suppress("UNCHECKED_CAST")
    return if (meta and bit != 0) data[(meta and (bit - 1)).countOneBits()] as Array<TextMateCapture?> else null
  }

  override fun hasBackReference(key: Constants.StringKey): Boolean {
    return true
  }

  override fun hasBackReference(key: Constants.CaptureKey, group: Int): Boolean {
    return true
  }

  override val children: List<SyntaxNodeDescriptor>
    get() {
      val offset = meta.countOneBits()
      return if (offset == data.size) emptyList() else ChildrenList(data, offset)
    }

  /**
   * Replaces every node reachable from this node's payload (children and capture rules)
   * according to [replacement]. Used by the builder to get rid of reference-descriptor
   * indirections once the whole table is compiled.
   */
  internal fun replaceNodeReferences(replacement: (SyntaxNodeDescriptor) -> SyntaxNodeDescriptor) {
    val childrenOffset = meta.countOneBits()
    for (i in childrenOffset until data.size) {
      data[i] = replacement(data[i] as SyntaxNodeDescriptor)
    }
    val capturesMask = meta shr CAPTURES_SHIFT
    if (capturesMask != 0) {
      val capturesOffset = (meta and ((1 shl CAPTURES_SHIFT) - 1)).countOneBits()
      for (i in capturesOffset until childrenOffset) {
        @Suppress("UNCHECKED_CAST")
        val captures = data[i] as Array<TextMateCapture?>
        for (group in captures.indices) {
          val capture = captures[group]
          if (capture is TextMateCapture.Rule) {
            val replaced = replacement(capture.node)
            if (replaced !== capture.node) {
              captures[group] = TextMateCapture.Rule(replaced)
            }
          }
        }
      }
    }
  }

  override fun toString(): String {
    val name = getStringAttribute(Constants.StringKey.NAME)
    return if (name != null) "Syntax rule: $name" else super.toString()
  }

  private class ChildrenList(
    private val data: Array<Any?>,
    private val offset: Int,
  ) : AbstractList<SyntaxNodeDescriptor>() {
    override val size: Int
      get() = data.size - offset

    override fun get(index: Int): SyntaxNodeDescriptor = data[offset + index] as SyntaxNodeDescriptor
  }
}
