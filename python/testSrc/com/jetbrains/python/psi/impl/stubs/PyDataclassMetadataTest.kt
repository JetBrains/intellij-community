// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * [PyDataclassMetadata] is the single extension slot dataclass class/field stubs offer to frameworks. Core never
 * interprets the payload.
 */
class PyDataclassMetadataTest {

  @Test
  fun `payload round-trips`() {
    val metadata = PyDataclassMetadata.encode { out ->
      out.writeByte(1)
      out.writeBoolean(true)
      out.writeUTF("validation_alias")
    }

    val decoded = metadata.decode { input ->
      Triple(input.readByte(), input.readBoolean(), input.readUTF())
    }

    assertEquals(Triple(1.toByte(), true, "validation_alias"), decoded)
  }

  @Test
  fun `reader demanding more than was written yields null`() {
    val metadata = PyDataclassMetadata.encode { out -> out.writeByte(1) }

    assertNull(metadata.decode { input -> input.readByte() to input.readUTF() })
  }

  @Test
  fun `empty payload yields null for any reader`() {
    val metadata = PyDataclassMetadata.encode { }

    assertNull(metadata.decode { input -> input.readByte() })
  }

  @Test
  fun `reader rejecting a stale version yields null`() {
    val metadata = PyDataclassMetadata.encode { out ->
      out.writeByte(1)
      out.writeUTF("written by the previous layout")
    }

    val decoded = metadata.decode { input ->
      val version = input.readByte()
      if (version != 2.toByte()) throw IOException("unsupported version $version")
      input.readUTF()
    }

    assertNull(decoded)
  }
}
