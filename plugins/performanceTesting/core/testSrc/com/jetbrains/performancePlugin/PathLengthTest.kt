package com.jetbrains.performancePlugin

import com.jetbrains.performancePlugin.PathLength.LIMIT
import com.jetbrains.performancePlugin.PathLength.pathThatFits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

private const val TIMESTAMP = "20260816193137"

/** How much of a file name survives the directory it goes into. Named after the profiler snapshots that run into the limit first. */
class PathLengthTest {

  @Test
  fun everyPartIsKeptWhenTheyAllFit() {
    val name = "RM-263.SNAPSHOT-completion-$TIMESTAMP.jfr"
    val dir = dirLeavingRoomFor(name)

    assertEquals(pathIn(dir, name), snapshotPathIn(dir))
  }

  /** The first optional part goes first: every snapshot of a run has the same build number. */
  @Test
  fun theFrontIsGivenUpFirst() {
    val name = "completion-$TIMESTAMP.jfr"
    val dir = dirLeavingRoomFor(name)

    assertEquals(pathIn(dir, name), snapshotPathIn(dir))
  }

  @Test
  fun theLastOptionalPartIsCutWhileTheRequiredOneStays() {
    val name = "com-$TIMESTAMP.jfr"
    val dir = dirLeavingRoomFor(name)

    assertEquals(pathIn(dir, name), snapshotPathIn(dir))
  }

  /** With the directory alone over the limit there is nothing left to give, and a file to complain about beats no file. */
  @Test
  fun aDirectoryOverTheLimitStillGetsTheRequiredPart() {
    val dir = dirOfLength(LIMIT)

    assertEquals(pathIn(dir, "$TIMESTAMP.jfr"), snapshotPathIn(dir))
  }

  /**
   * `RustRoverLocalInspectionWithStmtTypingTest` reported into a 228-character directory, and
   * `RR-263.SNAPSHOT-local_inspections_open_file-20260817045919.jfr` on top of it came to 291. async-profiler answered with
   * `Could not open Flight Recorder output file`, so the run kept no profile at all.
   */
  @Test
  fun aSnapshotOfADeeplyNestedRustRoverLaunchFitsWithinTheLimit() {
    val dir = dirOfLength(228)

    val path = pathThatFits(dir, "20260817045919.jfr", "RR-263.SNAPSHOT", "local_inspections_open_file")

    assertEquals(pathIn(dir, "local_inspe-20260817045919.jfr"), path)
    assertTrue(path.length < LIMIT, path)
  }

  private fun snapshotPathIn(directory: String): String =
    pathThatFits(directory, "$TIMESTAMP.jfr", "RM-263.SNAPSHOT", "completion")

  private fun pathIn(directory: String, name: String): String = directory + File.separator + name

  private fun dirOfLength(length: Int): String = File.separator + "d".repeat(length - 1)

  /** A directory in which [name] is the longest file name that still fits: one character more would reach [LIMIT]. */
  private fun dirLeavingRoomFor(name: String): String = dirOfLength(LIMIT - 2 - name.length)
}
