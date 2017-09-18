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
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.Language
import com.intellij.debugger.streams.trace.TraceExpressionBuilder
import com.intellij.debugger.streams.trace.dsl.StatementFactory
import com.intellij.debugger.streams.trace.dsl.impl.kotlin.KotlinStatementFactory
import com.intellij.debugger.streams.trace.impl.TraceExpressionBuilderImpl
import com.intellij.openapi.project.Project

/**
 * @author Vitaliy.Bibaev
 */
class KotlinLanguage(project: Project) : Language {
  override val name: String = "Kotlin"
  override val statementFactory: StatementFactory = KotlinStatementFactory()
  override val expressionBuilder: TraceExpressionBuilder = TraceExpressionBuilderImpl(project)
}