/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.maven.model;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.builders.BuildResult;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.incremental.messages.BuildMessage;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class JpsMavenJavaBuildingTest extends JpsBuildTestCase {
  public void testCompileJava() throws IOException {
    File srcDir = PathManagerEx.findFileUnderProjectHome("plugins/maven/jps-plugin/testData/compiler/classpathTest", getClass());
    File workDir = FileUtil.createTempDirectory("mavenJavaBuild", null);
    FileUtil.copyDir(srcDir, workDir);
    addJdk("1.6");
    loadProject(workDir.getAbsolutePath());
    BuildResult result = doBuild(CompileScopeTestBuilder.rebuild().all());
    result.assertFailed();
    BuildMessage message = assertOneElement(result.getMessages(BuildMessage.Kind.ERROR));
    assertTrue(message.toString(), message.getMessageText().contains("Maven project configuration") && message.getMessageText().contains("isn't available."));
  }
}
