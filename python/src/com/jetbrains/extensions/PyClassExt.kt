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
package com.jetbrains.extensions

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * @author Ilya.Kazakevich
 */
fun PyClass.inherits(evalContext: TypeEvalContext, parentNames: Set<String>) =
  this.getAncestorTypes(evalContext).filterNotNull().map { it.classQName }.filterNotNull().any { parentNames.contains(it) }

fun PyClass.inherits(evalContext: TypeEvalContext, vararg parentNames: String)= this.inherits(evalContext, parentNames.toHashSet())