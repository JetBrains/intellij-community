// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.jetbrains.python.packaging.PyPackageName
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-SDK map of "installs currently running", keyed by opaque strings — the normalized package
 * name for package installs (see [packageKey]) or the install dialog's namespaced `"location:"` /
 * `"command:"` keys. It is the single source of truth behind the tool-window tree link, the info
 * pane and the install dialog, so a package cannot be installed twice at once into the same
 * interpreter and every surface shows the same in-progress state (PY-91529).
 *
 * Scoped to the [Sdk] (stored in its user data, mirroring [com.jetbrains.python.packaging.management.CancellableJobSerialRunner]'s
 * per-SDK mutex) rather than to the project: installs happen per interpreter, so "is X installing"
 * only makes sense for a given SDK. Change notification is *not* kept here — it is UI-scoped and
 * owned by [PyPackagingToolWindowService], which fires its listeners on the EDT.
 *
 * Thread-safe.
 */
internal class PyActiveInstalls {
  private val installs: ConcurrentHashMap<String, ActiveInstall> = ConcurrentHashMap()

  /** State of one running install. A value type because [ConcurrentHashMap] forbids null values. */
  private class ActiveInstall(val traceUuid: String?)

  /** `true` while an install keyed by exactly [key] is running. */
  fun isInstalling(key: String): Boolean = installs.containsKey(key)

  /** `true` while a package named [packageName] (any version) is being installed. */
  fun isPackageInstalling(packageName: String): Boolean = isInstalling(packageKey(packageName))

  /**
   * Records [key], optionally tagging it with the uuid of the [com.jetbrains.python.TraceContext] the
   * install runs in so its command can be located later (see [traceUuid]). Returns `false` if [key]
   * was already recorded (so a repeated trigger is rejected); the uuid of the install that is
   * actually running is kept in that case, never overwritten by the rejected caller.
   */
  fun mark(key: String, traceUuid: String? = null): Boolean = installs.putIfAbsent(key, ActiveInstall(traceUuid)) == null

  /** Clears [key] along with its trace uuid. Returns `false` if it was not recorded. */
  fun unmark(key: String): Boolean = installs.remove(key) != null

  /**
   * Uuid of the trace the install under [key] runs in, or `null` when nothing is running under [key]
   * or the install was started without a trace — not every surface owns one.
   */
  fun traceUuid(key: String): String? = installs[key]?.traceUuid

  companion object {
    private val KEY = Key.create<PyActiveInstalls>(PyActiveInstalls::class.java.name)

    /** The [Sdk]'s active-installations map, created on first use and living in its user data. */
    fun forSdk(sdk: Sdk): PyActiveInstalls = of(sdk)

    /** Storage accessor by raw [UserDataHolder] — the SDK in production, any holder in tests. */
    internal fun of(holder: UserDataHolder): PyActiveInstalls =
      synchronized(holder) { holder.getOrCreateUserDataUnsafe(KEY) { PyActiveInstalls() } }

    /** Normalized key for a package install: `Django`, `django` and `DJANGO` collapse to one entry. */
    fun packageKey(packageName: String): String = PyPackageName.from(packageName).name
  }
}
