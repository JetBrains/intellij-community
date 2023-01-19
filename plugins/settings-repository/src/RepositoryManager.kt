// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.containers.CollectionFactory
import java.io.InputStream
import java.util.*

interface RepositoryManager {
  fun createRepositoryIfNeeded(): Boolean

  /**
   * Think twice before use
   */
  fun deleteRepository()

  fun isRepositoryExists(): Boolean

  @NlsSafe
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
  fun write(path: String, content: ByteArray): Boolean

  fun delete(path: String): Boolean

  fun processChildren(path: String, filter: (name: String) -> Boolean, processor: (name: String, inputStream: InputStream) -> Boolean)

  /**
   * syncType will be passed if called before sync.
   *
   * If fixStateIfCannotCommit, repository state will be fixed before commit.
   */
  suspend fun commit(syncType: SyncType? = null, fixStateIfCannotCommit: Boolean = true): Boolean

  fun getAheadCommitsCount(): Int

  suspend fun push()

  suspend fun fetch(): Updater

  suspend fun pull(): UpdateResult?

  fun has(path: String): Boolean

  suspend fun resetToTheirs(): UpdateResult?

  suspend fun resetToMy(localRepositoryInitializer: (() -> Unit)?): UpdateResult?

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
  override val changed = CollectionFactory.createSmallMemoryFootprintSet(changed)
  override val deleted = CollectionFactory.createSmallMemoryFootprintSet(deleted)

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