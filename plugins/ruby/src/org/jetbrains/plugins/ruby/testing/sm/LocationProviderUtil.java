package org.jetbrains.plugins.ruby.testing.sm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;

import java.util.List;
import java.util.Collections;

/**
 * @author Roman Chernyatchik
 */
public class LocationProviderUtil {
  @NonNls private static final String PROTOCOL_SEPARATOR = "://";

  private LocationProviderUtil() {
  }

  @Nullable
  public static String extractProtocol(@NotNull final String locationUrl) {
    final int index = locationUrl.indexOf(PROTOCOL_SEPARATOR);
    if (index >= 0) {
      return locationUrl.substring(0, index);
    }
    return null;
  }

  @Nullable
  public static String extractPath(@NotNull final String locationUrl) {
    final int index = locationUrl.indexOf(PROTOCOL_SEPARATOR);
    if (index >= 0) {
      return locationUrl.substring(index + PROTOCOL_SEPARATOR.length());
    }
    return null;
  }

  public static List<VirtualFile> findSuitableFilesFor(final String filePath) {
    final VirtualFile fileByPath = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (fileByPath != null) {
      return Collections.singletonList(fileByPath);
    }
    //TODO improve
    return Collections.emptyList();
  }
}
