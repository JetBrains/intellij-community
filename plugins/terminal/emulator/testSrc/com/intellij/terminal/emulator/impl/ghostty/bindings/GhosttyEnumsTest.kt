// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty.bindings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.enums.enumEntries

/**
 * Cross-checks each Kotlin mirror of a libghostty-vt C enum (`GhosttyEnums.kt`) against the enum
 * descriptor `ghostty_type_json()` compiles into the bundled library: its `values` map (member name
 * to ordinal), minus the `..._MAX_VALUE` sentinel the generator always adds.
 *
 * Two coverage levels, matching the convention `GhosttyEnums.kt` documents for itself:
 * - [Coverage.PARTIAL]: a "selector" enum that intentionally lists only the members the bridge uses.
 *   Every Kotlin member must still match its C counterpart by name and value; the C side may have more.
 * - [Coverage.EXHAUSTIVE]: an enum meant to mirror the whole C enum. The two must describe exactly
 *   the same set of names, so a C-side addition fails the test until the Kotlin side is updated.
 *
 * Not covered: [GhosttyMode] (a `uint16_t` packed DEC/ANSI mode id, not a C `enum`) and [GhosttyMods]
 * (a bit-flag object, not an `enum class`) — `ghostty_type_json()` has no enum descriptor for either.
 */
internal class GhosttyEnumsTest {

  private val types: JsonObject = Json.parseToJsonElement(LibGhosttyVt.typeJson()).jsonObject.getValue("types").jsonObject

  private enum class Coverage { PARTIAL, EXHAUSTIVE }

  @Test
  fun ghosttyResult() {
    assertMirrors<GhosttyResult>("GhosttyResult", Coverage.EXHAUSTIVE) { it.code }
  }

  @Test
  fun ghosttyPointTag() {
    assertMirrors<GhosttyPointTag>("GhosttyPointTag", Coverage.EXHAUSTIVE) { it.code }
  }

  @Test
  fun ghosttyCellWide() {
    assertMirrors<GhosttyCellWide>("GhosttyCellWide", Coverage.EXHAUSTIVE) { it.code }
  }

  @Test
  fun ghosttyStyleColorTag() {
    assertMirrors<GhosttyStyleColorTag>("GhosttyStyleColorTag", Coverage.EXHAUSTIVE) { it.code }
  }

  @Test
  fun ghosttySgrUnderline() {
    assertMirrors<GhosttySgrUnderline>("GhosttySgrUnderline", Coverage.EXHAUSTIVE) { it.code }
  }

  @Test
  fun ghosttyCellContentTag() {
    assertMirrors<GhosttyCellContentTag>("GhosttyCellContentTag", Coverage.EXHAUSTIVE) { it.code }
  }

  @Test
  fun ghosttyTerminalProgressState() {
    assertMirrors<GhosttyTerminalProgressState>("GhosttyTerminalProgressState", Coverage.EXHAUSTIVE) { it.code }
  }

  /**
   * TODO: [GhosttyTerminalScrollbackPull] is not yet added to the libghostty-vt C API types list.
   *  uncomment when it is added.
   */
  //@Test
  //fun ghosttyTerminalScrollbackPull() {
  //  assertMirrors<GhosttyTerminalScrollbackPull>("GhosttyTerminalScrollbackPull", Coverage.EXHAUSTIVE) { it.code }
  //}

  @Test
  fun ghosttyRenderStateDirty() {
    assertMirrors<GhosttyRenderStateDirty>("GhosttyRenderStateDirty", Coverage.EXHAUSTIVE) { it.code }
  }

  @Test
  fun ghosttySysLogLevel() {
    assertMirrors<GhosttySysLogLevel>("GhosttySysLogLevel", Coverage.EXHAUSTIVE) { it.code }
  }

  @Test
  fun ghosttyKeyAction() {
    assertMirrors<GhosttyKeyAction>("GhosttyKeyAction", Coverage.EXHAUSTIVE) { it.code }
  }

  @Test
  fun ghosttyMouseAction() {
    assertMirrors<GhosttyMouseAction>("GhosttyMouseAction", Coverage.EXHAUSTIVE) { it.code }
  }

  // GhosttyCursorVisualStyle mirrors two distinct C enums that happen to share one numeric layout
  // (see its KDoc) — check both independently so a future drift between them is caught either way.

  @Test
  fun ghosttyCursorVisualStyleMatchesRenderState() {
    assertMirrors<GhosttyCursorVisualStyle>("GhosttyRenderStateCursorVisualStyle", Coverage.EXHAUSTIVE) { it.code }
  }

  @Test
  fun ghosttyCursorVisualStyleMatchesTerminalCursorStyle() {
    assertMirrors<GhosttyCursorVisualStyle>("GhosttyTerminalCursorStyle", Coverage.EXHAUSTIVE) { it.code }
  }

  // ---- selector enums: GhosttyEnums's own doc calls these out as intentionally partial ----

  @Test
  fun ghosttyTerminalData() {
    assertMirrors<GhosttyTerminalData>("GhosttyTerminalData", Coverage.PARTIAL) { it.code }
  }

  @Test
  fun ghosttyCellData() {
    assertMirrors<GhosttyCellData>("GhosttyCellData", Coverage.PARTIAL) { it.code }
  }

  @Test
  fun ghosttyRowData() {
    assertMirrors<GhosttyRowData>("GhosttyRowData", Coverage.PARTIAL) { it.code }
  }

  @Test
  fun ghosttyTerminalOption() {
    assertMirrors<GhosttyTerminalOption>("GhosttyTerminalOption", Coverage.PARTIAL) { it.code }
  }

  @Test
  fun ghosttyRenderStateData() {
    assertMirrors<GhosttyRenderStateData>("GhosttyRenderStateData", Coverage.PARTIAL) { it.code }
  }

  @Test
  fun ghosttyRenderStateRowData() {
    assertMirrors<GhosttyRenderStateRowData>("GhosttyRenderStateRowData", Coverage.PARTIAL) { it.code }
  }

  @Test
  fun ghosttyRenderStateRowOption() {
    assertMirrors<GhosttyRenderStateRowOption>("GhosttyRenderStateRowOption", Coverage.PARTIAL) { it.code }
  }

  @Test
  fun ghosttyRenderStateOption() {
    assertMirrors<GhosttyRenderStateOption>("GhosttyRenderStateOption", Coverage.PARTIAL) { it.code }
  }

  @Test
  fun ghosttySysOption() {
    assertMirrors<GhosttySysOption>("GhosttySysOption", Coverage.PARTIAL) { it.code }
  }

  @Test
  fun ghosttyMouseEncoderOption() {
    assertMirrors<GhosttyMouseEncoderOption>("GhosttyMouseEncoderOption", Coverage.PARTIAL) { it.code }
  }

  // Not a "selector" by intent, but currently missing SIX..ELEVEN: no domain-level mouse event above
  // the bridge (TerminalMouseButton) can produce more than five buttons, so those C members have
  // nothing to mirror them to yet. Tracked as PARTIAL until that changes.
  @Test
  fun ghosttyMouseButton() {
    assertMirrors<GhosttyMouseButton>("GhosttyMouseButton", Coverage.PARTIAL) { it.code }
  }

  /**
   * Asserts every constant of [E] matches a same-named member of the C enum [cEnumName] by [code].
   * [coverage] additionally asserts the C enum has no member [E] lacks.
   */
  private inline fun <reified E : Enum<E>> assertMirrors(cEnumName: String, coverage: Coverage, code: (E) -> Int) {
    val cValues = cEnumValues(cEnumName)
    val kotlinValues = enumEntries<E>().associate { it.name to code(it) }

    assertThat(kotlinValues.keys - cValues.keys)
      .describedAs("$cEnumName: Kotlin members the C enum no longer has").isEmpty()
    assertThat(kotlinValues.filter { (name, value) -> cValues[name] != value }.keys)
      .describedAs("$cEnumName: Kotlin/C value mismatch").isEmpty()

    if (coverage == Coverage.EXHAUSTIVE) {
      assertThat(cValues.keys - kotlinValues.keys)
        .describedAs("$cEnumName: C members not mirrored in Kotlin").isEmpty()
    }
  }

  /** [cEnumName]'s `values` map from `ghostty_type_json()`, minus the always-present `..._MAX_VALUE` sentinel. */
  private fun cEnumValues(cEnumName: String): Map<String, Int> {
    val descriptor = requireNotNull(types[cEnumName]) { "ghostty_type_json describes no type named $cEnumName" }.jsonObject
    assertThat(descriptor.getValue("kind").jsonPrimitive.content).describedAs("$cEnumName kind").isEqualTo("enum")
    return descriptor.getValue("values").jsonObject
      .mapValues { it.value.jsonPrimitive.int }
      .filterValues { it != Int.MAX_VALUE }
  }
}
