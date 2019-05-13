/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.buildout;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.fixtures.PyTestCase;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public class BuildoutScriptParserTest extends PyTestCase {
  public void testParseOldStyleScript() throws IOException {
    File scriptFile = new File(myFixture.getTestDataPath(), "buildout/django-script.py");
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(scriptFile);
    List<String> paths = BuildoutFacet.extractFromScript(vFile);
    assertEquals(7, paths.size());
    assertTrue(paths.contains("c:\\\\src\\\\django\\\\buildout15\\\\eggs\\\\djangorecipe-0.20-py2.6.egg"));
    assertTrue(paths.contains("c:\\\\src\\\\django\\\\buildout15\\\\parts\\\\django"));
  }

  public void testParseSitePy() throws IOException {
    File scriptFile = new File(myFixture.getTestDataPath(), "buildout/site.py");
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(scriptFile);
    List<String> paths = BuildoutFacet.extractFromSitePy(vFile);
    assertSameElements(paths,
                       "c:\\src\\django\\buildout15\\src",
                       "c:\\src\\django\\buildout15\\eggs\\setuptools-0.6c12dev_r88124-py2.6.egg");
  }
}
