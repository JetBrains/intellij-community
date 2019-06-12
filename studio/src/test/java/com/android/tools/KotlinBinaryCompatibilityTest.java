package com.android.tools;

import com.android.testutils.TestUtils;
import com.android.testutils.truth.FileSubject;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

public class KotlinBinaryCompatibilityTest {

  @Test
  public void testKotlinCompatibility() throws Exception {
    File problemsFile = verify(TestUtils.getWorkspaceFile("tools/idea/android-studio.tar.gz"));
    assertAbout(FileSubject.files()).that(problemsFile).doesNotExist();
  }

  private static File verify(File gzippedTarFile) throws IOException, ArchiveException, InterruptedException {
    File tmpDir = TestUtils.createTempDirDeletedOnExit();
    extract(gzippedTarFile, tmpDir);
    ProcessBuilder process = new ProcessBuilder(
      System.getProperty("java.home") + "/bin/java",
      "-Dplugin.verifier.home.dir=" + tmpDir.getPath(),
      "-jar", TestUtils.getWorkspaceFile("prebuilts/tools/common/intellij-plugin-verifier/verifier-cli-1.191-all.jar").getPath(),
      "-ignored-problems", TestUtils.getWorkspaceFile("tools/idea/studio/kotlin_known_problems.txt").getPath(),
      "-verification-reports-dir", TestUtils.getTestOutputDir().getPath(),
      "-runtime-dir", tmpDir.getPath() + "/android-studio/jre",
      "check-plugin",
      tmpDir.getPath() + "/android-studio/plugins/Kotlin",
      tmpDir.getPath() + "/android-studio"
    ).inheritIO();
    int status = process.start().waitFor();
    assertThat(status).isEqualTo(0);

    File[] outputDirs = TestUtils.getWorkspaceRoot().listFiles(((dir, name) -> name.startsWith("verification-")));
    assertWithMessage("should be exactly one output file").that(outputDirs).hasLength(1);

    String productName = Files.readFirstLine(new File(tmpDir.getPath(), "/android-studio/build.txt"), Charsets.UTF_8);
    File kotlinDir = new File(outputDirs[0], productName + "/plugins/org.jetbrains.kotlin/").listFiles()[0];

    assertAbout(FileSubject.files()).that(new File(kotlinDir, "verification-verdict.txt")).exists();
    return new File(kotlinDir, "compatibility-problems.txt");
  }

  private static void extract(File studioZip, File tmpDir) throws IOException, ArchiveException {
    try (ArchiveInputStream in = new ArchiveStreamFactory().createArchiveInputStream(
      "tar", new GzipCompressorInputStream(new FileInputStream(studioZip)))) {
      for(TarArchiveEntry entry = (TarArchiveEntry)in.getNextEntry(); entry != null; entry = (TarArchiveEntry)in.getNextEntry()) {
        if (entry.isDirectory()) {
          continue;
        }
        File outFile = new File(tmpDir, entry.getName());
        if (!outFile.getParentFile().exists()) {
          outFile.getParentFile().mkdirs();
        }

        IOUtils.copy(in, new FileOutputStream(outFile));
      }
    }
  }
}
