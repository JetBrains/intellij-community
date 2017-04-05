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
package org.jetbrains.uast.test.java

import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.test.common.ValuesTestBase
import java.io.File

abstract class AbstractJavaValuesTest : AbstractJavaUastTest(), ValuesTestBase {
    protected var _evaluatorExtension: UEvaluatorExtension? = null

    override fun getEvaluatorExtension(): UEvaluatorExtension? = _evaluatorExtension

    private fun getTestFile(testName: String, ext: String) =
            File(File(TEST_JAVA_MODEL_DIR, testName).canonicalPath.substringBeforeLast('.') + '.' + ext)

    override fun getValuesFile(testName: String) = getTestFile(testName, "values.txt")

    fun doTest(name: String, extension: UEvaluatorExtension) {
        _evaluatorExtension = extension
        try {
            doTest(name)
        }
        finally {
            _evaluatorExtension = null
        }
    }
}
