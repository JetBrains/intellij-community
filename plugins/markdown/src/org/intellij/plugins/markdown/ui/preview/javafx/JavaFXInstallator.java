package org.intellij.plugins.markdown.ui.preview.javafx;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

enum JavaFXInstallator {
  INSTANCE;

  private static final NotNullLazyValue<String> URL = new NotNullLazyValue<String>() {
    @NotNull
    @Override
    protected String compute() {
      return getJavaFXSdkURL();
    }
  };

  @NotNull
  private static String getJavaFXSdkURL() {
    final InputStream resource = JavaFXInstallator.class.getResourceAsStream("/org/intellij/plugins/markdown/javafx/location.properties");
    try {
      final Properties properties = new Properties();
      properties.load(resource);
      return properties.getProperty("javafx_sdk_overlay_location");
    }
    catch (IOException e) {
      throw new IllegalStateException("Could not find location.properties. Check your installation.");
    }
  }

  public boolean installOpenJFXAndReport(@NotNull JComponent parentComponent) {
    final DownloadableFileService fileService = DownloadableFileService.getInstance();
    final DownloadableFileDescription fileDescription = fileService.createFileDescription(URL.getValue(), "javafx-sdk-overlay.zip");
    final FileDownloader downloader = fileService.createDownloader(Collections.singletonList(fileDescription), "OpenJFX");

    final List<Pair<VirtualFile, DownloadableFileDescription>> progress =
      downloader.downloadWithProgress(getInstallationPath(), null, parentComponent);

    if (progress == null) {
      return false;
    }

    boolean success = false;
    for (Pair<VirtualFile, DownloadableFileDescription> pair : progress) {
      if (!pair.getSecond().equals(fileDescription)) {
        Logger.getInstance(JavaFXInstallator.class).warn("Another file downloaded: " + pair);
        continue;
      }
      final VirtualFile file = pair.getFirst();
      if (file == null) {
        continue;
      }

      final File archiveFile = VfsUtilCore.virtualToIoFile(file);
      try {
        ZipUtil.extract(archiveFile, new File(getInstallationPath()), null, true);
        Logger.getInstance(JavaFXInstallator.class).info("Downloaded and installed OpenJFX in " + archiveFile.getParent());
        success = true;
      }
      catch (IOException ignore) {
      }
    }

    return success;
  }

  public String getInstallationPath() {
    return PathManager.getConfigPath() + "/openjfx";
  }
}
