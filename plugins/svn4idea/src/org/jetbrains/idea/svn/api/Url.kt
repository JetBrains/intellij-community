// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api

import com.google.common.net.UrlEscapers
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.svn.SvnBundle.message
import org.jetbrains.idea.svn.SvnUtil
import org.jetbrains.idea.svn.commandLine.SvnBindException
import java.net.URI
import java.net.URISyntaxException

private const val FILE_URL_PREFIX = "file:/"

class Url private constructor(innerUri: URI) {
  private val uri = fixDefaultPort(innerUri)

  val protocol: String = uri.scheme.orEmpty()
  val host: String = uri.host.orEmpty()
  val port: Int = uri.port
  val userInfo: String? = uri.userInfo
  val path: String = uri.path.orEmpty().removeSuffix("/")

  @get:NlsSafe
  val tail: String get() = path.substringAfterLast('/')

  fun commonAncestorWith(url: Url): Url? {
    if (protocol != url.protocol || host != url.host || port != url.port || userInfo != url.userInfo) return null

    val commonPath = SvnUtil.ensureStartSlash(getCommonAncestor(path, url.path))
    return try {
      wrap { URI(uri.scheme, uri.userInfo, uri.host, uri.port, commonPath, uri.query, uri.fragment) }
    }
    catch (e: SvnBindException) {
      null
    }
  }

  @Throws(SvnBindException::class)
  fun appendPath(path: String, encoded: Boolean = true): Url =
    if (path.isEmpty() || path == "/") this else wrap { uri.resolve(URI(prepareUri(path.removePrefix("/"), encoded))) }

  @Throws(SvnBindException::class)
  fun setUserInfo(userInfo: String?): Url = wrap { URI(uri.scheme, userInfo, uri.host, uri.port, uri.path, uri.query, uri.fragment) }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Url) return false

    return uri == other.uri
  }

  override fun hashCode(): Int = uri.hashCode()

  override fun toString(): String = fixFileUrlToString(uri.toASCIIString().removeSuffix("/"))
  @NlsSafe
  fun toDecodedString(): String = URLUtil.unescapePercentSequences(toString())

  private fun fixFileUrlToString(url: String) = if (url.startsWith(FILE_URL_PREFIX) && !url.startsWith(
      "$FILE_URL_PREFIX/")) "$FILE_URL_PREFIX//${url.substring(FILE_URL_PREFIX.length)}"
  else url

  companion object {
    @JvmField
    val EMPTY: Url = Url(URI(""))

    @NonNls
    private val DEFAULT_PORTS: Map<String, Int> = mapOf("http" to 80, "https" to 443, "svn" to 3690, "svn+ssh" to 22)

    @JvmStatic
    @Throws(SvnBindException::class)
    fun parse(value: String, encoded: Boolean = true): Url = wrap {
      val uri = URI(prepareUri(value, encoded)).normalize()

      if (!uri.isAbsolute) throw SvnBindException(message("error.url.is.not.absolute", uri))
      if (uri.isOpaque) throw SvnBindException(message("error.url.is.not.hierarchical", uri))
      if (uri.query != null) throw SvnBindException(message("error.url.could.not.contain.query", uri))
      if (uri.fragment != null) throw SvnBindException(message("error.url.could.not.contain.fragment", uri))
      uri
    }

    @JvmStatic
    fun tail(url: String): String = url.removeSuffix("/").substringAfterLast('/')

    @JvmStatic
    fun removeTail(url: String): String = url.removeSuffix("/").substringBeforeLast('/', "")

    @JvmStatic
    fun append(url1: String, url2: String): String {
      val prefix = url1.removeSuffix("/")
      val suffix = url2.removePrefix("/").removeSuffix("/")
      val separator = if (prefix.isEmpty() || suffix.isEmpty()) "" else "/"
      return prefix + separator + suffix
    }

    @JvmStatic
    fun getRelative(parent: String, child: String): String? {
      if (parent == child) return ""
      if (parent.isEmpty()) return child.removePrefix("/")

      val parentWithSlash = ensureEndSlash(parent)
      return if (child.startsWith(parentWithSlash)) child.substring(parentWithSlash.length) else null
    }

    @JvmStatic
    fun isAncestor(parent: String, child: String): Boolean = parent.isEmpty() || child.startsWith(
      parent) && (parent.last() == '/' || child.getOrElse(parent.length, { '/' }) == '/')

    @JvmStatic
    fun getCommonAncestor(url1: String, url2: String): String = (url1.splitToSequence('/') zip url2.splitToSequence('/'))
      .takeWhile { it.first == it.second }
      .joinToString("/") { it.first }

    private fun hasDefaultPort(uri: URI) = uri.port < 0 || uri.port == DEFAULT_PORTS[uri.scheme]
    private fun fixDefaultPort(uri: URI) = if (uri.port >= 0 && hasDefaultPort(uri)) URI(uri.scheme, uri.userInfo, uri.host, -1, uri.path, uri.query, uri.fragment) else uri
    private fun prepareUri(uri: String, encoded: Boolean) = encode(ensureEndSlash(uri), encoded)
    private fun encode(value: String, encoded: Boolean) = if (encoded) value else UrlEscapers.urlFragmentEscaper().escape(value)
    private fun ensureEndSlash(value: String) = if (value.lastOrNull() == '/') value else "$value/"
    private fun wrap(block: () -> URI): Url {
      try {
        return Url(block())
      }
      catch (e: URISyntaxException) {
        throw SvnBindException(e)
      }
    }
  }
}