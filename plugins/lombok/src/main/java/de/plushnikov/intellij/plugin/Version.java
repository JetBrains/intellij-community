package de.plushnikov.intellij.plugin;

import com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Version {
  @NonNls String PLUGIN_NAME = "Lombok plugin";
  /**
   * Current version of lombok plugin
   */
  @NonNls String LAST_LOMBOK_VERSION = "1.18.18";

  @NonNls String LAST_LOMBOK_VERSION_WITH_JPS_FIX = "1.18.16";

  static boolean isLessThan(@Nullable OrderEntry orderEntry, @NotNull String version) {
    String lombokVersion = parseLombokVersion(orderEntry);
    return lombokVersion != null && compareVersionString(lombokVersion, version) < 0;
  }

  @Nullable
  static String parseLombokVersion(@Nullable OrderEntry orderEntry) {
    String result = null;
    if (orderEntry != null) {
      final String presentableName = orderEntry.getPresentableName();
      final Matcher matcher = Pattern.compile("(.*:)([\\d.]+)(.*)").matcher(presentableName);
      if (matcher.find()) {
        result = matcher.group(2);
      }
    }
    return result;
  }

  static int compareVersionString(@NotNull String firstVersionOne, @NotNull String secondVersion) {
    String[] firstVersionParts = firstVersionOne.split("\\.");
    String[] secondVersionParts = secondVersion.split("\\.");
    int length = Math.max(firstVersionParts.length, secondVersionParts.length);
    for (int i = 0; i < length; i++) {
      int firstPart = i < firstVersionParts.length && !firstVersionParts[i].isEmpty() ?
                      Integer.parseInt(firstVersionParts[i]) : 0;
      int secondPart = i < secondVersionParts.length && !secondVersionParts[i].isEmpty() ?
                       Integer.parseInt(secondVersionParts[i]) : 0;
      if (firstPart < secondPart) {
        return -1;
      }
      if (firstPart > secondPart) {
        return 1;
      }
    }
    return 0;
  }
}
