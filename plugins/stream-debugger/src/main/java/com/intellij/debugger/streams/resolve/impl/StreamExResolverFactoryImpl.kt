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
package com.intellij.debugger.streams.resolve.impl

import com.intellij.debugger.streams.resolve.*

/**
 * @author Vitaliy.Bibaev
 */
class StreamExResolverFactoryImpl : ResolverFactory {
  override fun getResolver(methodName: String): ValuesOrderResolver? {
    return when (methodName) {
      "atLeast", "atMost", "less", "greater",
      "filterBy", "filterKeys", "filterValues", "filterKeyValue",
      "nonNull", "nonNullKeys", "nonNullValues",
      "remove", "removeBy", "removeKeys", "removeValues", "removeKeyValue", "without",
      "select", "selectKeys", "selectValues",
      "dropWhile", "takeWhile", "takeWhileInclusive" -> FilterResolver()
      "mapFirst", "mapFirstOrElse", "mapLast", "mapLastOrElse",
      "keys", "values",
      "mapKeyValue", "mapKeys", "mapValues", "mapToEntry", "mapToKey", "mapToValue",
      "elements", "invert", "join", "withFirst" -> MapResolver()
      "pairMap" -> PairMapResolver()
      else -> null
    }
  }
}