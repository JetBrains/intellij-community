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

import org.apache.commons.codec.binary.Base64InputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

class CompressTest {
    
    @Test
    fun testSimple() {
        val text = """1470911998827	completion-stats	cd1f9318cd9f	46de626e4ea1	COMPLETION_STARTED	{"completionListLength":1,"performExperiment":true,"experimentVersion":2,"completionListIds":[0],"newCompletionListItems":[{"id":0,"length":4,"relevance":{"frozen":"true","sorter":"1","liftShorterClasses":"false","templates":"false","middleMatching":"false","liftShorter":"false","priority":"0.0","methodsChains":"00_0_2147483647","com.jetbrains.python.codeInsight.completion.PythonCompletionWeigher@5731acb0":"0","stats":"0","prefix":"-1133","kind":"localOrParameter","expectedType":"expected","recursion":"normal","nameEnd":"0","nonGeneric":"0","accessible":"NORMAL","simple":"0","explicitProximity":"0","proximity":"[referenceList\u003dunknown, samePsiMember\u003d2, explicitlyImported\u003d300, javaInheritance\u003dnull, groovyReferenceListWeigher\u003dunknown, openedInEditor\u003dtrue, sameDirectory\u003dtrue, sameLogicalRoot\u003dtrue, sameModule\u003d2, knownElement\u003d0, inResolveScope\u003dtrue, sdkOrLibrary\u003dfalse]","sameWords":"0","shorter":"0","grouping":"0"}}],"currentPosition":0,"userUid":"cd1f9318cd9f"}"""
        val array = GzipBase64Compressor.compress(text)

        val gzipStream = GZIPInputStream(Base64InputStream(ByteArrayInputStream(array)))
        val encoded = gzipStream.reader().readText()
        
        assertThat(encoded).isEqualTo(text)
    }
    
}