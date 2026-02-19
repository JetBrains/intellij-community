// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.dataextractor

import java.awt.Font
import java.awt.font.FontRenderContext



/**
 * `CharByGlyphFinder` is a utility object for retrieving characters corresponding to specific glyph codes
 * based on the rendering context and font information.
 *
 * This object helps in mapping glyph codes to their related Unicode characters
 * using a predefined set of character ranges.
 */
internal object CharByGlyphFinder {
  private const val MAX_CACHE_SIZE = 20
  private val charRanges = listOf(
    32..126, // Basic Latin
    160..255, // Latin-1 Supplement
    256..591, // Latin Extended-A & B
    880..1023, // Greek and Coptic
    1024..1216 // Cyrillic
  )
  private fun createTable(font: Font, frc: FontRenderContext): Map<Int, Char> {
    val map = mutableMapOf<Int, Char>()
    charRanges.forEach { range ->
      range.forEach { code ->
        val glyph = font.createGlyphVector(frc, charArrayOf(code.toChar())).getGlyphCode(0)
        map[glyph] = code.toChar()
      }
    }
    return map
  }
  private val cache =  object : LinkedHashMap<String, Map<Int, Char>>()  {
    override fun removeEldestEntry(eldest: Map.Entry<String?, Map<Int, Char>?>?): Boolean {
      return size > MAX_CACHE_SIZE
    }
  }
  fun findCharByGlyph(font: Font, frc: FontRenderContext, glyph: Int): Char? {
    val key = font.name + font.size + frc.transform.toString()
    return cache.getOrPut(key) { createTable(font, frc) }[glyph]
  }
}