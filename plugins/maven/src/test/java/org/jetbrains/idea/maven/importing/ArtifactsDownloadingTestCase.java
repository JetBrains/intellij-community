/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class ArtifactsDownloadingTestCase extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "plugins", "local1");
    helper.copy("plugins", "local1");
    setRepositoryPath(helper.getTestDataPath("local1"));
  }

  protected void createDummyArtifact(String remoteRepo, String name) throws IOException {
    createEmptyJar(remoteRepo, name);
  }

  public static void createEmptyJar(@NotNull String dir, @NotNull String name) throws IOException {
    File jar = new File(dir, name);
    FileUtil.ensureExists(jar.getParentFile());
    IoTestUtil.createTestJar(jar);

    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA1");
    }
    catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    digest.update(FileUtil.loadFileBytes(jar));
    byte[] sha1 = digest.digest();

    PrintWriter out = new PrintWriter(new File(dir, name + ".sha1"), "UTF-8");
    try {
      for (byte b : sha1) out.printf("%02x", b);
      out.println("  " + name);
    }
    finally {
      out.close();
    }
  }
}
