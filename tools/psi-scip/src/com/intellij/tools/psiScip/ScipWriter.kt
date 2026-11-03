// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path

/** The `UTF8` value of the SCIP `TextEncoding` enum. */
private const val TEXT_ENCODING_UTF8 = 1

/** The `UTF16CodeUnitOffsetFromLineStart` value of the SCIP `PositionEncoding` enum. */
private const val POSITION_ENCODING_UTF16 = 2

internal class ScipToolInfo(@JvmField val name: String, @JvmField val version: String, @JvmField val arguments: List<String>)

/**
 * Writes a SCIP `Index` with field numbers of `scip.proto` v0.5.2, without generated message classes.
 *
 * The writer streams the index: the metadata comes first, and then each document as its own field-2 record.
 * The caller gives the documents in the output order.
 */
internal class ScipWriter(file: Path) : AutoCloseable {
  private val stream: OutputStream = BufferedOutputStream(Files.newOutputStream(file), 1 shl 16)
  private val out: CodedOutputStream = CodedOutputStream.newInstance(stream, 1 shl 16)

  fun writeMetadata(projectRoot: String, tool: ScipToolInfo) {
    writeMessage(1, metadataSize(projectRoot, tool)) {
      writeMessage(2, toolInfoSize(tool)) {
        out.writeString(1, tool.name)
        out.writeString(2, tool.version)
        for (argument in tool.arguments) {
          out.writeString(3, argument)
        }
      }
      out.writeString(3, projectRoot)
      out.writeEnum(4, TEXT_ENCODING_UTF8)
    }
  }

  fun writeDocument(document: ScipDocument) {
    writeMessage(2, documentSize(document)) {
      out.writeString(1, document.relativePath)
      for (occurrence in document.occurrences) {
        writeMessage(2, occurrenceSize(occurrence)) {
          writePacked(1, occurrence.range.toArray())
          out.writeString(2, occurrence.symbol)
          if (occurrence.roles != 0) {
            out.writeInt32(3, occurrence.roles)
          }
          occurrence.enclosingRange?.let { writePacked(7, it.toArray()) }
        }
      }
      for (symbol in document.symbols) {
        writeMessage(3, symbolSize(symbol)) {
          out.writeString(1, symbol.symbol)
          for (relationship in symbol.relationships) {
            writeMessage(4, relationshipSize(relationship)) {
              out.writeString(1, relationship.symbol)
              if (relationship.isReference) {
                out.writeBool(2, true)
              }
              if (relationship.isImplementation) {
                out.writeBool(3, true)
              }
            }
          }
          out.writeEnum(5, symbol.kind.number)
          out.writeString(6, symbol.displayName)
        }
      }
      out.writeString(4, document.language)
      out.writeEnum(6, POSITION_ENCODING_UTF16)
    }
  }

  override fun close() {
    out.flush()
    stream.close()
  }

  private inline fun writeMessage(field: Int, size: Int, body: () -> Unit) {
    out.writeTag(field, WireFormat.WIRETYPE_LENGTH_DELIMITED)
    out.writeUInt32NoTag(size)
    body()
  }

  private fun writePacked(field: Int, values: IntArray) {
    out.writeTag(field, WireFormat.WIRETYPE_LENGTH_DELIMITED)
    out.writeUInt32NoTag(packedDataSize(values))
    for (value in values) {
      out.writeInt32NoTag(value)
    }
  }
}

private fun messageSize(field: Int, size: Int): Int {
  return CodedOutputStream.computeTagSize(field) + CodedOutputStream.computeUInt32SizeNoTag(size) + size
}

private fun packedDataSize(values: IntArray): Int = values.sumOf { CodedOutputStream.computeInt32SizeNoTag(it) }

private fun packedSize(field: Int, values: IntArray): Int = messageSize(field, packedDataSize(values))

private fun toolInfoSize(tool: ScipToolInfo): Int {
  return CodedOutputStream.computeStringSize(1, tool.name) +
         CodedOutputStream.computeStringSize(2, tool.version) +
         tool.arguments.sumOf { CodedOutputStream.computeStringSize(3, it) }
}

private fun metadataSize(projectRoot: String, tool: ScipToolInfo): Int {
  return messageSize(2, toolInfoSize(tool)) +
         CodedOutputStream.computeStringSize(3, projectRoot) +
         CodedOutputStream.computeEnumSize(4, TEXT_ENCODING_UTF8)
}

private fun occurrenceSize(occurrence: ScipOccurrence): Int {
  return packedSize(1, occurrence.range.toArray()) +
         CodedOutputStream.computeStringSize(2, occurrence.symbol) +
         (if (occurrence.roles != 0) CodedOutputStream.computeInt32Size(3, occurrence.roles) else 0) +
         (occurrence.enclosingRange?.let { packedSize(7, it.toArray()) } ?: 0)
}

private fun relationshipSize(relationship: ScipRelationship): Int {
  return CodedOutputStream.computeStringSize(1, relationship.symbol) +
         (if (relationship.isReference) CodedOutputStream.computeBoolSize(2, true) else 0) +
         (if (relationship.isImplementation) CodedOutputStream.computeBoolSize(3, true) else 0)
}

private fun symbolSize(symbol: ScipSymbolInformation): Int {
  return CodedOutputStream.computeStringSize(1, symbol.symbol) +
         symbol.relationships.sumOf { messageSize(4, relationshipSize(it)) } +
         CodedOutputStream.computeEnumSize(5, symbol.kind.number) +
         CodedOutputStream.computeStringSize(6, symbol.displayName)
}

private fun documentSize(document: ScipDocument): Int {
  return CodedOutputStream.computeStringSize(1, document.relativePath) +
         document.occurrences.sumOf { messageSize(2, occurrenceSize(it)) } +
         document.symbols.sumOf { messageSize(3, symbolSize(it)) } +
         CodedOutputStream.computeStringSize(4, document.language) +
         CodedOutputStream.computeEnumSize(6, POSITION_ENCODING_UTF16)
}

/**
 * Writes the index in the shape of `scip print --json`: field names as in `scip.proto`, enums by name, and default values left out.
 * Each occurrence also carries the booleans `call`, `doc` and `non_name` for the private bits of `symbol_roles`.
 */
internal class ScipJsonWriter(file: Path) : AutoCloseable {
  private val out: Writer = Files.newBufferedWriter(file)
  private var documentCount = 0

  fun writeMetadata(projectRoot: String, tool: ScipToolInfo) {
    out.append("{\"metadata\":{\"tool_info\":{\"name\":").appendJson(tool.name).append(",\"version\":").appendJson(tool.version)
    out.append(",\"arguments\":[")
    tool.arguments.forEachIndexed { index, argument ->
      if (index > 0) out.append(',')
      out.appendJson(argument)
    }
    out.append("]},\"project_root\":").appendJson(projectRoot).append(",\"text_document_encoding\":\"UTF8\"},\n\"documents\":[")
  }

  fun writeDocument(document: ScipDocument) {
    if (documentCount++ > 0) out.append(",")
    out.append("\n{\"relative_path\":").appendJson(document.relativePath)
    out.append(",\"language\":").appendJson(document.language)
    out.append(",\"position_encoding\":\"UTF16CodeUnitOffsetFromLineStart\",\"occurrences\":[")
    document.occurrences.forEachIndexed { index, occurrence ->
      out.append(if (index > 0) ",\n" else "\n")
      out.append("{\"range\":").appendInts(occurrence.range.toArray()).append(",\"symbol\":").appendJson(occurrence.symbol)
      if (occurrence.roles != 0) {
        out.append(",\"symbol_roles\":").append(occurrence.roles.toString())
      }
      occurrence.enclosingRange?.let { out.append(",\"enclosing_range\":").appendInts(it.toArray()) }
      if (occurrence.roles and ROLE_CALL != 0) out.append(",\"call\":true")
      if (occurrence.roles and ROLE_DOC != 0) out.append(",\"doc\":true")
      if (occurrence.roles and ROLE_NON_NAME != 0) out.append(",\"non_name\":true")
      out.append('}')
    }
    out.append("],\"symbols\":[")
    document.symbols.forEachIndexed { index, symbol ->
      out.append(if (index > 0) ",\n" else "\n")
      out.append("{\"symbol\":").appendJson(symbol.symbol)
      if (symbol.relationships.isNotEmpty()) {
        out.append(",\"relationships\":[")
        symbol.relationships.forEachIndexed { relationshipIndex, relationship ->
          if (relationshipIndex > 0) out.append(',')
          out.append("{\"symbol\":").appendJson(relationship.symbol)
          if (relationship.isReference) out.append(",\"is_reference\":true")
          if (relationship.isImplementation) out.append(",\"is_implementation\":true")
          out.append('}')
        }
        out.append(']')
      }
      out.append(",\"kind\":\"").append(symbol.kind.name).append("\",\"display_name\":").appendJson(symbol.displayName).append('}')
    }
    out.append("]}")
  }

  override fun close() {
    out.append("\n]}\n")
    out.close()
  }
}

private fun Writer.appendInts(values: IntArray): Writer {
  append('[')
  values.forEachIndexed { index, value ->
    if (index > 0) append(',')
    append(value.toString())
  }
  return append(']')
}

private fun Writer.appendJson(value: String): Writer {
  append('"')
  for (c in value) {
    when {
      c == '"' -> append("\\\"")
      c == '\\' -> append("\\\\")
      c == '\n' -> append("\\n")
      c == '\r' -> append("\\r")
      c == '\t' -> append("\\t")
      c < ' ' -> append(String.format("\\u%04x", c.code))
      else -> append(c)
    }
  }
  return append('"')
}
