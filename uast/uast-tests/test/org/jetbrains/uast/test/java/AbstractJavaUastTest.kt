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

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.uast.test.env.AbstractUastFixtureTest
import java.io.File

abstract class AbstractJavaUastTest : AbstractUastFixtureTest() {
    protected companion object {
        val TEST_JAVA_MODEL_DIR = File(PathManagerEx.getCommunityHomePath(), "uast/uast-tests/java")
    }

    override fun getVirtualFile(testName: String): VirtualFile {
        val localPath = File(TEST_JAVA_MODEL_DIR, testName).path
        val vFile = LocalFileSystem.getInstance().findFileByPath(localPath)
        return vFile ?: throw IllegalStateException("Couldn't find virtual file for $localPath")
    }
}