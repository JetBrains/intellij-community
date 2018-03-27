// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.ContainerUtil.newHashMap
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.idea.svn.SvnUtil
import java.text.DateFormat
import java.text.ParseException
import java.util.*

private val LOG = logger<Revision>()

class Revision private constructor(private val order: Int, val keyword: String? = null, val number: Long = -1, val date: Date? = null) {
  init {
    if (keyword == null && number < 0 && date == null) throwIllegalState()

    keyword?.let { ourKeywordRevisions[keyword] = this }
  }

  val isValid = this !== UNDEFINED
  val isLocal = this === BASE || this === WORKING

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Revision) return false

    return when {
      keyword != null -> keyword == other.keyword
      number >= 0 -> number == other.number
      date != null -> date == other.date
      else -> throwIllegalState()
    }
  }

  override fun hashCode(): Int {
    return when {
      keyword != null -> keyword.hashCode()
      number >= 0 -> number.hashCode()
      date != null -> date.hashCode()
      else -> throwIllegalState()
    }
  }

  override fun toString(): String {
    return when {
      keyword != null -> keyword
      number >= 0 -> number.toString()
      date != null -> DateFormat.getDateTimeInstance().format(date)
      else -> throwIllegalState()
    }
  }

  private fun throwIllegalState(): Nothing = throw IllegalStateException("no keyword, number or date in revision")

  companion object {
    private val ourKeywordRevisions = newHashMap<String, Revision>()

    @JvmField val BASE = Revision(2, "BASE")
    @JvmField val COMMITTED = Revision(3, "COMMITTED")
    @JvmField val HEAD = Revision(0, "HEAD")
    @JvmField val PREV = Revision(4, "PREV")
    // TODO: This one should likely be removed - not in the least of svn revision keywords
    @JvmField val WORKING = Revision(1, "WORKING")
    @JvmField val UNDEFINED = Revision(30, "UNDEFINED")

    @JvmField val GENERAL_ORDER: Comparator<Revision> = compareByDescending { it.order }

    @JvmStatic
    fun of(number: Long) = if (number < 0) UNDEFINED else Revision(10, number = number)

    @JvmStatic
    fun of(date: Date) = Revision(20, date = date)

    @JvmStatic
    fun parse(value: String?): Revision {
      if (value == null) return UNDEFINED

      val revision = fromKeyword(value) ?: fromNumber(value) ?: fromDate(value)
      if (revision == null) LOG.info("Could not parse revision ${value}")
      return revision ?: UNDEFINED
    }

    private fun fromKeyword(value: String) = ourKeywordRevisions[value]

    private fun fromNumber(value: String) = value.toLongOrNull()?.let { Revision.of(it) }

    private fun fromDate(value: String) = parseDate(value.removeSurrounding("{", "}"))?.let { Revision.of(it) }

    private fun parseDate(value: String) = SvnUtil.parseDate(value, false) ?: parseIso8601(value)

    private fun parseIso8601(value: String): Date? {
      try {
        return DateFormatUtil.getIso8601Format().parse(value)
      }
      catch (e: ParseException) {
        return null
      }
    }
  }
}
