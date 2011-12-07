package de.plushnikov.intellij.lombok.util;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.util.BuildNumber;

/**
 * @author Plushnikov Michail
 */
public class IntelliJVersionRangeUtil {
  /**
   * Mapping of IntelliJ Buildnumber to Version Information
   * 9.0 - 93.13
   * 9.0.1 - 93.94
   * 9.0.2 - 95.66
   * 9.0.3 - 95.429
   * 9.0.4 - 95.627
   * 10.0 - 99.18
   * 10.0.1 - 99.32
   * 10.0.2 - 103.72
   * 10.0.3 - 103.255
   * 10.5 - 107.105
   * 10.5.1 - 107.322
   * 10.5.2
   * 10.5.3
   * 10.5.4 - 107.777
   * EAP 11 - 110.3
   * 11.0  -  111.69
   */
  public static IntelliJVersion getIntelliJVersion(@NotNull final BuildNumber buildNumber) {
    IntelliJVersion result = IntelliJVersion.UNKNOWN;

    final int baselineVersion = buildNumber.getBaselineVersion();
    final int build = buildNumber.getBuildNumber();

    if (baselineVersion < 93) {
      result = IntelliJVersion.INTELLIJ_8;
    } else if (baselineVersion < 99) {
      result = IntelliJVersion.INTELLIJ_9;
    } else if (baselineVersion <= 103) {
      result = IntelliJVersion.INTELLIJ_10;
    } else if (baselineVersion < 108) {
      result = IntelliJVersion.INTELLIJ_10_5;
    } else if (baselineVersion >= 110) {
      result = IntelliJVersion.INTELLIJ_11;
    }

    return result;
  }
}
