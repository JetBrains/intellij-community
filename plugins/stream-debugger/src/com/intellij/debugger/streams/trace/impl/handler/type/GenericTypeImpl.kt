/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.trace.impl.handler.type

import java.util.Objects

/**
 * @author Vitaliy.Bibaev
 */
open class GenericTypeImpl(override val variableTypeName: String,
                           override val genericTypeName: String,
                           override val defaultValue: String) : GenericType {

  override fun hashCode(): Int {
    return Objects.hash(variableTypeName, genericTypeName)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    return other is GenericType && variableTypeName == other.variableTypeName && genericTypeName == other.genericTypeName
  }
}
