package com.intellij.python.sdk.common.evolution

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * Single source of truth for the Evo interpreter widget's registry flags, so the frontend widget and the backend
 * provider read them through these getters instead of repeating literal `Registry.is("…")` calls. The keys — and their
 * default values — are declared in `intellij.python.sdk.common.xml`, which is loaded in both the frontend (widget) and
 * backend ([PyEvoSdkApi] provider) processes; the single-argument `Registry` accessors return those declared defaults,
 * so the defaults live only in the descriptor.
 */
@ApiStatus.Internal
object PyEvoRegistry {
  /** Master switch: show the Evo interpreter widget instead of the classic one. */
  val isWidgetEnabled: Boolean
    get() = Registry.`is`("python.evolution.widget")

  /** Seconds a closed popup's built tree is reused before a re-open rebuilds (rescans) it. */
  val popupTreeCacheSeconds: Int
    get() = Registry.intValue("python.evolution.widget.cache.seconds")

  /** A tool whose environment scan exceeds this many seconds is cached and shown with a reload icon. */
  val slowToolThresholdSeconds: Int
    get() = Registry.intValue("python.evolution.widget.slow.threshold.seconds")

  /** Seconds a slow tool's environment list stays cached. */
  val slowToolCacheSeconds: Int
    get() = Registry.intValue("python.evolution.widget.slow.cache.seconds")

  /** How many directory levels below a base dir env discovery descends to find nested virtualenvs (a direct child is level 1). */
  val scanMaxDepth: Int
    get() = Registry.intValue("python.evolution.widget.scan.depth")

  /**
   * Where uv is installed, take the base Python list from uv rather than from the IDE's own interpreter scan.
   *
   * Off restores that scan, which is the way back for a machine whose interpreters uv does not report — a version
   * manager's own directory, which uv reaches only through shims.
   */
  val useUvSystemPythons: Boolean
    get() = Registry.`is`("python.evolution.widget.uv.pythons")

  /** Upper bound on directories examined per env discovery, so a huge project tree can't turn a node expansion into a long walk. */
  val scanMaxDirs: Int
    get() = Registry.intValue("python.evolution.widget.scan.max.dirs")
}
