package org.jetbrains.settingsRepository

import com.intellij.openapi.progress.ProgressIndicator

import java.io.IOException
import java.io.InputStream
import gnu.trove.THashSet

public trait RepositoryManager {
  public fun createRepositoryIfNeed(): Boolean

  /**
   * Think twice before use
   */
  public fun deleteRepository()

  public fun isRepositoryExists(): Boolean

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
  public fun pull(indicator: ProgressIndicator): UpdateResult?

  public fun has(path: String): Boolean

  public fun resetToTheirs(indicator: ProgressIndicator): UpdateResult?

  public fun resetToMy(indicator: ProgressIndicator): UpdateResult?

  public fun canCommit(): Boolean
}

public trait UpdateResult {
  val changed: Collection<String>
  val deleted: Collection<String>
  //val unmerged: Collection<String> = listOf()
}

public data class ImmutableUpdateResult(override val changed: Collection<String>, override val deleted: Collection<String>) : UpdateResult {
  public fun toMutable(): MutableUpdateResult = MutableUpdateResult(changed, deleted)
}

public data class MutableUpdateResult(changed: Collection<String>, deleted: Collection<String>) : UpdateResult {
  override val changed = THashSet(changed)
  override val deleted = THashSet(deleted)

  fun add(result: UpdateResult) {
    add(result.changed, result.deleted)
  }

  fun add(newChanged: Collection<String>, newDeleted: Collection<String>): MutableUpdateResult {
    changed.removeAll(newDeleted)
    deleted.removeAll(newChanged)

    changed.addAll(newChanged)
    deleted.addAll(newDeleted)
    return this
  }

  fun addChanged(newChanged: Collection<String>): MutableUpdateResult {
    deleted.removeAll(newChanged)
    changed.addAll(newChanged)
    return this
  }
}