package de.plushnikov.intellij.plugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Version {
  @NonNls String PLUGIN_NAME = "Lombok plugin";
  /**
   * Current version of lombok plugin
   */
  @NonNls String LAST_LOMBOK_VERSION = "1.18.38";

  @NonNls String LAST_LOMBOK_VERSION_WITH_JPS_FIX = "1.18.16";
  @NonNls String LOMBOK_VERSION_WITH_JDK16_FIX = "1.18.20";

  Pattern VERSION_PATTERN = Pattern.compile("(.*:)([\\d.]+)(.*)");

  static boolean isLessThan(@Nullable String currentVersion, @Nullable String otherVersion) {
    try {
      return StringUtil.compareVersionNumbers(currentVersion, otherVersion) < 0;
    }
    catch (NumberFormatException e) {
      Logger.getInstance(Version.class).info("Unable to parse lombok version: " + currentVersion);
      return false;
    }
  }

  static @Nullable String parseLombokVersion(@Nullable OrderEntry orderEntry) {
    String result = null;
    if (orderEntry != null) {
      final String presentableName = orderEntry.getPresentableName();
      final Matcher matcher = VERSION_PATTERN.matcher(presentableName);
      if (matcher.find()) {
        result = matcher.group(2);
      }
    }
    return result;
  }
}
