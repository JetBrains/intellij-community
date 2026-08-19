// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty.bindings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemoryLayout.PathElement.groupElement

/**
 * Verifies the hand-written struct geometry in [GhosttyLayouts] against
 * `ghostty_type_json()` — the layout description compiled into the bundled library
 * itself. The C API is pre-1.0 and free to reorder or resize fields; without this
 * check, a library upgrade whose layouts drifted would not fail anywhere — the
 * bridge would silently read garbage.
 */
internal class GhosttyLayoutsTest {

  private val types: JsonObject = Json.parseToJsonElement(LibGhosttyVt.typeJson()).jsonObject

  @Test
  fun point() {
    val point = struct("GhosttyPoint")
    // x/y address the `GhosttyPointCoordinate` arm of the (opaque-in-JSON) `value` union
    val coordinate = struct("GhosttyPointCoordinate")
    assertLayoutMatches(GhosttyLayouts.POINT, point, "tag", "value")
    assertThat(GhosttyLayouts.POINT_OFF_TAG).isEqualTo(point.offset("tag"))
    assertThat(GhosttyLayouts.POINT_OFF_X).isEqualTo(point.offset("value") + coordinate.offset("x"))
    assertThat(GhosttyLayouts.POINT_OFF_Y).isEqualTo(point.offset("value") + coordinate.offset("y"))
  }

  @Test
  fun pointCoordinate() {
    val coordinate = struct("GhosttyPointCoordinate")
    assertLayoutMatches(GhosttyLayouts.POINT_COORD, coordinate, "x", "y")
    assertThat(GhosttyLayouts.POINT_COORD_OFF_Y).isEqualTo(coordinate.offset("y"))
  }

  @Test
  fun gridRef() {
    assertLayoutMatches(GhosttyLayouts.GRID_REF, struct("GhosttyGridRef"), "size", "node", "x", "y")
  }

  @Test
  fun progressReport() {
    val report = struct("GhosttyTerminalProgressReport")
    assertThat(GhosttyLayouts.PROGRESS_REPORT_OFF_STATE).isEqualTo(report.offset("state"))
    assertThat(GhosttyLayouts.PROGRESS_REPORT_OFF_PROGRESS).isEqualTo(report.offset("progress"))
    // `progress` is the last field the bridge reads, so the minimum size is its end
    assertThat(GhosttyLayouts.PROGRESS_REPORT_MIN_SIZE)
      .isEqualTo(report.offset("progress") + report.sizeOf("progress"))
  }

  @Test
  fun mousePosition() {
    val position = struct("GhosttyMousePosition")
    assertLayoutMatches(GhosttyLayouts.MOUSE_POSITION, position, "x", "y")
    assertThat(GhosttyLayouts.MOUSE_POSITION_OFF_X).isEqualTo(position.offset("x"))
    assertThat(GhosttyLayouts.MOUSE_POSITION_OFF_Y).isEqualTo(position.offset("y"))
  }

  @Test
  fun mouseEncoderSize() {
    val encoderSize = struct("GhosttyMouseEncoderSize")
    assertThat(GhosttyLayouts.MOUSE_ENCODER_SIZE_BYTES).isEqualTo(encoderSize.byteSize)
    assertThat(encoderSize.offset("size")).isEqualTo(0)
    assertThat(encoderSize.sizeOf("size")).isEqualTo(GhosttyLayouts.C_LONG.byteSize())
    assertThat(GhosttyLayouts.MOUSE_ENCODER_SIZE_OFF_SCREEN_WIDTH).isEqualTo(encoderSize.offset("screen_width"))
    assertThat(GhosttyLayouts.MOUSE_ENCODER_SIZE_OFF_SCREEN_HEIGHT).isEqualTo(encoderSize.offset("screen_height"))
    assertThat(GhosttyLayouts.MOUSE_ENCODER_SIZE_OFF_CELL_WIDTH).isEqualTo(encoderSize.offset("cell_width"))
    assertThat(GhosttyLayouts.MOUSE_ENCODER_SIZE_OFF_CELL_HEIGHT).isEqualTo(encoderSize.offset("cell_height"))
  }

  @Test
  fun style() {
    val style = struct("GhosttyStyle")
    // fg/bg tag and value offsets compose through the nested `GhosttyStyleColor`
    val color = struct("GhosttyStyleColor")
    assertThat(GhosttyLayouts.STYLE_SIZE).isEqualTo(style.byteSize)
    assertThat(GhosttyLayouts.STYLE_OFF_FG_TAG).isEqualTo(style.offset("fg_color") + color.offset("tag"))
    assertThat(GhosttyLayouts.STYLE_OFF_FG_VAL).isEqualTo(style.offset("fg_color") + color.offset("value"))
    assertThat(GhosttyLayouts.STYLE_OFF_BG_TAG).isEqualTo(style.offset("bg_color") + color.offset("tag"))
    assertThat(GhosttyLayouts.STYLE_OFF_BG_VAL).isEqualTo(style.offset("bg_color") + color.offset("value"))
    assertThat(color.sizeOf("tag")).isEqualTo(GhosttyLayouts.C_INT.byteSize())

    listOf(
      GhosttyLayouts.STYLE_OFF_BOLD to "bold",
      GhosttyLayouts.STYLE_OFF_ITALIC to "italic",
      GhosttyLayouts.STYLE_OFF_FAINT to "faint",
      GhosttyLayouts.STYLE_OFF_BLINK to "blink",
      GhosttyLayouts.STYLE_OFF_INVERSE to "inverse",
      GhosttyLayouts.STYLE_OFF_INVISIBLE to "invisible",
    ).forEach { (offset, field) ->
      assertThat(offset).describedAs(field).isEqualTo(style.offset(field))
      assertThat(style.sizeOf(field)).describedAs(field).isEqualTo(GhosttyLayouts.C_BYTE.byteSize())
    }

    assertThat(GhosttyLayouts.STYLE_OFF_UNDERLINE).isEqualTo(style.offset("underline"))
    assertThat(style.sizeOf("underline")).isEqualTo(GhosttyLayouts.C_INT.byteSize())
  }

  /** Checks [layout]'s total size, alignment, and named [fields] offsets against [json]. */
  private fun assertLayoutMatches(layout: MemoryLayout, json: JsonObject, vararg fields: String) {
    assertThat(layout.byteSize()).isEqualTo(json.byteSize)
    assertThat(layout.byteAlignment()).isEqualTo(json.byteAlignment)
    for (field in fields) {
      assertThat(layout.byteOffset(groupElement(field))).describedAs(field).isEqualTo(json.offset(field))
    }
  }

  private fun struct(name: String): JsonObject =
    requireNotNull(types[name]) { "ghostty_type_json describes no struct named $name" }.jsonObject

  // not `size`/`align`: `JsonObject` is a `Map`, whose members shadow same-named extensions
  private val JsonObject.byteSize: Long get() = getValue("size").jsonPrimitive.long
  private val JsonObject.byteAlignment: Long get() = getValue("align").jsonPrimitive.long
  private fun JsonObject.offset(field: String): Long = field(field).getValue("offset").jsonPrimitive.long
  private fun JsonObject.sizeOf(field: String): Long = field(field).getValue("size").jsonPrimitive.long

  private fun JsonObject.field(name: String): JsonObject =
    requireNotNull(getValue("fields").jsonObject[name]) { "no field named $name" }.jsonObject
}
