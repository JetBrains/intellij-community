/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package com.intellij.stats.completion.logger

import com.intellij.stats.completion.LogEventSerializer
import com.intellij.stats.completion.events.LogEvent
import com.intellij.stats.validation.InputSessionValidator

/**
 * @author Vitaliy.Bibaev
 */
class ClientSessionValidator : SessionValidator {
    private val validator: InputSessionValidator = InputSessionValidator()

    override fun validate(session: List<LogEvent>) {
        val lines = session.map { LogEventSerializer.toString(it) }
        val result = validator.validate(lines)
        session.forEach { it.validationStatus = result.validationStatus }
    }
}