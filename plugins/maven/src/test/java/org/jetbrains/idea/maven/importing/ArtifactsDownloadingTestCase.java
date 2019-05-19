// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.util.io.DigestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;

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

    MessageDigest digest = DigestUtil.sha1();
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
