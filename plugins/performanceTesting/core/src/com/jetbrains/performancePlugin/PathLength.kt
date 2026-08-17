package com.jetbrains.performancePlugin

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * How long a path the IDE under test may write. The directories come from whoever launched the IDE and can be any depth, so a file name is
 * what gives way when the two together are too long. Writing an over-long path anyway loses the file: async-profiler, for one, answers with
 * `Could not open Flight Recorder output file` and the run keeps no profile at all.
 */
@ApiStatus.Internal
object PathLength {
  /** Windows' longest path, a terminating null included. The same limit as `ReportingPathUtils.PATH_LENGTH_LIMIT` of IDE Starter. */
  const val LIMIT: Int = 260

  /**
   * `<directory>/<optional>-<required>`, with the [optional] parts given up from the front, and the last one left cut down, until the whole
   * path fits within [LIMIT]. [required] always stays whole, so name the part that tells one file from another there.
   *
   * [directory] is measured as it is given, so pass it absolute; a relative one is resolved against a working directory this cannot see.
   * With [directory] alone over the limit there is nothing left to give up and the path comes back over it anyway, which is worth a warning
   * from whoever chose the directory.
   */
  @JvmStatic
  fun pathThatFits(directory: String, required: String, vararg optional: String): String {
    // what is left for the optional parts, the separator above them and the hyphen joining them to [required] included
    val room = LIMIT - 2 - directory.length - required.length
    val named = optional.indices.asSequence()
                  .map { optional.drop(it).joinToString("-") }
                  .firstOrNull { it.length < room }
                ?: optional.lastOrNull()?.take((room - 1).coerceAtLeast(0)).orEmpty()
    return Path.of(directory, if (named.isEmpty()) required else "$named-$required").toString()
  }
}
