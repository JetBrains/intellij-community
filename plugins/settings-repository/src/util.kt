/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil
import java.nio.ByteBuffer

fun String?.nullize(): String? = StringUtil.nullize(this)

fun byteBufferToBytes(byteBuffer: ByteBuffer): ByteArray {
  if (byteBuffer.hasArray() && byteBuffer.arrayOffset() == 0) {
    val bytes = byteBuffer.array()
    if (bytes.size == byteBuffer.limit()) {
      return bytes
    }
  }

  val bytes = ByteArray(byteBuffer.limit())
  byteBuffer.get(bytes)
  return bytes
}