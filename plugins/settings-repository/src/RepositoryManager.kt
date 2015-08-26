/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository

import com.intellij.openapi.progress.ProgressIndicator
import gnu.trove.THashSet
import java.io.InputStream
import java.util.Collections

public interface RepositoryManager {
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
  public fun setUpstream(url: String?, branch: String? = null)

  public fun read(path: String): InputStream?

  /**
   * Returns false if file is not written (for example, due to ignore rules).
   */
  public fun write(path: String, content: ByteArray, size: Int): Boolean

  public fun delete(path: String)

  public fun processChildren(path: String, filter: (name: String) -> Boolean, processor: (name: String, inputStream: InputStream) -> Boolean)

  /**
   * Not all implementations support progress indicator (will not be updated on progress).
   *
   * syncType will be passed if called before sync.
   */
  public fun commit(indicator: ProgressIndicator? = null, syncType: SyncType? = null): Boolean

  public fun getAheadCommitsCount(): Int

  public fun commit(paths: List<String>)

  public fun push(indicator: ProgressIndicator? = null)

  public fun fetch(indicator: ProgressIndicator? = null): Updater

  public fun pull(indicator: ProgressIndicator? = null): UpdateResult?

  public fun has(path: String): Boolean

  public fun resetToTheirs(indicator: ProgressIndicator): UpdateResult?

  public fun resetToMy(indicator: ProgressIndicator, localRepositoryInitializer: (() -> Unit)?): UpdateResult?

  public fun canCommit(): Boolean

  public interface Updater {
    fun merge(): UpdateResult?

    // valid only if merge was called before
    val definitelySkipPush: Boolean
  }
}

public interface UpdateResult {
  val changed: Collection<String>
  val deleted: Collection<String>
}

val EMPTY_UPDATE_RESULT = ImmutableUpdateResult(Collections.emptySet(), Collections.emptySet())

public data class ImmutableUpdateResult(override val changed: Collection<String>, override val deleted: Collection<String>) : UpdateResult {
  public fun toMutable(): MutableUpdateResult = MutableUpdateResult(changed, deleted)
}

public data class MutableUpdateResult(changed: Collection<String>, deleted: Collection<String>) : UpdateResult {
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

public fun UpdateResult?.isEmpty(): Boolean = this == null || (changed.isEmpty() && deleted.isEmpty())

public fun UpdateResult?.concat(result: UpdateResult?): UpdateResult? {
  if (result.isEmpty()) {
    return this
  }
  else if (isEmpty()) {
    return result
  }
  else {
    this!!
    return MutableUpdateResult(changed, deleted).add(result!!)
  }
}

public class AuthenticationException(cause: Throwable) : RuntimeException(cause.getMessage(), cause)