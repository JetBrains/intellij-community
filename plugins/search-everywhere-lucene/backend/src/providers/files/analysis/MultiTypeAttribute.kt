package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

import org.apache.lucene.util.Attribute
import org.apache.lucene.util.AttributeImpl
import org.apache.lucene.util.AttributeReflector

interface MultiTypeAttribute : Attribute {
  val typeFlags: BooleanArray   // indexed by FileTokenType.ordinal; size = FileTokenType.entries.size
  fun setTypes(types: Collection<FileTokenType>)
  fun hasType(type: FileTokenType): Boolean
  fun clearTypes(): MultiTypeAttribute

  fun hasOverlapWith(other: MultiTypeAttribute): Boolean

  fun isEmpty(): Boolean
  fun activeTypes(): List<FileTokenType>
}

@Suppress("unused")
class MultiTypeAttributeImpl : AttributeImpl(), MultiTypeAttribute {
  override val typeFlags: BooleanArray = BooleanArray(FileTokenType.entries.size)
  override fun setTypes(types: Collection<FileTokenType>): Unit = types.forEach { typeFlags[it.ordinal] = true }
  override fun hasType(type: FileTokenType): Boolean = typeFlags[type.ordinal]
  override fun clearTypes(): MultiTypeAttributeImpl = apply { typeFlags.fill(false) }
  override fun hasOverlapWith(other: MultiTypeAttribute): Boolean {
    return other.activeTypes().any { hasType(it) }
  }

  override fun isEmpty(): Boolean = typeFlags.none { it }
  override fun activeTypes(): List<FileTokenType> =
    FileTokenType.entries.filter { typeFlags[it.ordinal] }

  override fun clear() {
    clearTypes()
  }

  override fun copyTo(target: AttributeImpl) {
    val t = target as MultiTypeAttribute
    t.clearTypes()
    t.setTypes(activeTypes())
  }

  override fun reflectWith(reflector: AttributeReflector) {
    reflector.reflect(MultiTypeAttribute::class.java, "types",
                      activeTypes().joinToString(",") { it.name })
  }
}
