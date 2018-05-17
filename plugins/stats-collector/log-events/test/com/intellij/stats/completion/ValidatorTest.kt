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

package com.intellij.stats.completion

import com.intellij.stats.validation.EventLine
import com.intellij.stats.validation.InputSessionValidator
import com.intellij.stats.validation.SessionValidationResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class ValidatorTest {
    lateinit var validator: InputSessionValidator
    val sessionStatuses = hashMapOf<String, Boolean>()

    @Before
    fun setup() {
        sessionStatuses.clear()
        val result = object : SessionValidationResult {
            override fun addErrorSession(errorSession: List<EventLine>) {
                val sessionUid = errorSession.first().sessionUid ?: return
                sessionStatuses[sessionUid] = false
            }

            override fun addValidSession(validSession: List<EventLine>) {
                val sessionUid = validSession.first().sessionUid ?: return
                sessionStatuses[sessionUid] = true
            }
        }
        validator = InputSessionValidator(result)
    }

    private fun file(path: String): File {
        return File(javaClass.classLoader.getResource(path).file)
    }

    @Test
    fun testValidData() {
        val file = file("data/valid_data.txt")
        validator.validate(file.readLines())
        assertThat(sessionStatuses["fb16691974d3"]).isTrue()
    }

    @Test
    fun testDataWithAbsentFieldInvalid() {
        val file = file("data/absent_field.txt")
        validator.validate(file.readLines())
        assertThat(sessionStatuses["fb16691974d3"]).isFalse()
    }

    @Test
    fun testInvalidWithoutBacket() {
        val file = file("data/no_bucket.txt")
        validator.validate(file.readLines())
        assertThat(sessionStatuses["fb16691974d3"]).isFalse()
    }

    @Test
    fun testInvalidWithoutVersion() {
        val file = file("data/no_version.txt")
        validator.validate(file.readLines())
        assertThat(sessionStatuses["fb16691974d3"]).isFalse()
    }

    @Test
    fun testDataWithExtraFieldInvalid() {
        val file = file("data/extra_field.txt")
        validator.validate(file.readLines())
        assertThat(sessionStatuses["fb16691974d3"]).isFalse()
    }
}