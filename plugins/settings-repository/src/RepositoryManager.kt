// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.intellij.openapi.progress.ProgressIndicator
import gnu.trove.THashSet
import java.io.InputStream
import java.util.*

interface RepositoryManager {
  fun createRepositoryIfNeed(): Boolean

  /**
   * Think twice before use
   */
  fun deleteRepository()

  fun isRepositoryExists(): Boolean

  fun getUpstream(): String?

  fun hasUpstream(): Boolean

  /**
   * Return error message if failed
   */
  fun setUpstream(url: String?, branch: String? = null)

  fun <R> read(path: String, consumer: (InputStream?) -> R): R

  /**
   * Returns false if file is not written (for example, due to ignore rules).
   */
  fun write(path: String, content: ByteArray, size: Int): Boolean

  fun delete(path: String): Boolean

  fun processChildren(path: String, filter: (name: String) -> Boolean, processor: (name: String, inputStream: InputStream) -> Boolean)

  /**
   * Not all implementations support progress indicator (will not be updated on progress).
   *
   * syncType will be passed if called before sync.
   *
   * If fixStateIfCannotCommit, repository state will be fixed before commit.
   */
  suspend fun commit(indicator: ProgressIndicator? = null, syncType: SyncType? = null, fixStateIfCannotCommit: Boolean = true): Boolean

  fun getAheadCommitsCount(): Int

  fun push(indicator: ProgressIndicator? = null)

  fun fetch(indicator: ProgressIndicator? = null): Updater

  suspend fun pull(indicator: ProgressIndicator? = null): UpdateResult?

  fun has(path: String): Boolean

  suspend fun resetToTheirs(indicator: ProgressIndicator): UpdateResult?

  suspend fun resetToMy(indicator: ProgressIndicator, localRepositoryInitializer: (() -> Unit)?): UpdateResult?

  fun canCommit(): Boolean

  interface Updater {
    suspend fun merge(): UpdateResult?

    // valid only if merge was called before
    val definitelySkipPush: Boolean
  }
}

interface UpdateResult {
  val changed: Collection<String>
  val deleted: Collection<String>
}

internal val EMPTY_UPDATE_RESULT = ImmutableUpdateResult(Collections.emptySet(), Collections.emptySet())

internal data class ImmutableUpdateResult(override val changed: Collection<String>, override val deleted: Collection<String>) : UpdateResult {
  fun toMutable() = MutableUpdateResult(changed, deleted)
}

internal class MutableUpdateResult(changed: Collection<String>, deleted: Collection<String>) : UpdateResult {
  override val changed = THashSet(changed)
  override val deleted = THashSet(deleted)

  fun add(result: UpdateResult?): MutableUpdateResult {
    if (result != null) {
      add(result.changed, result.deleted)
    }
    return this
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

internal fun UpdateResult?.isEmpty() = this == null || (changed.isEmpty() && deleted.isEmpty())

internal class AuthenticationException(cause: Throwable) : RuntimeException(cause.message, cause)