package org.jetbrains.plugins.settingsRepository

import com.intellij.openapi.progress.ProgressIndicator

import java.io.File
import java.io.IOException
import java.io.InputStream

public trait RepositoryManager {
  public fun createRepositoryIfNeed(): RepositoryManager

  public fun getUpstream(): String?

  public fun hasUpstream(): Boolean

  /**
   * Return error message if failed
   */
  throws(javaClass<Exception>())
  public fun setUpstream(url: String?, branch: String?)

  throws(javaClass<IOException>())
  public fun read(path: String): InputStream?

  /**
   * @param async Write postpone or immediately
   */
  public fun write(path: String, content: ByteArray, size: Int, async: Boolean)

  public fun delete(path: String)

  public fun listSubFileNames(path: String): Collection<String>

  /**
   * Not all implementations support progress indicator (will not be updated on progress)
   */
  throws(javaClass<Exception>())
  public fun commit(indicator: ProgressIndicator)

  public fun commit(paths: List<String>)

  throws(javaClass<Exception>())
  public fun push(indicator: ProgressIndicator)

  throws(javaClass<Exception>())
  public fun pull(indicator: ProgressIndicator)

  public fun has(path: String): Boolean
}