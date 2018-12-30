package com.intellij.bash.lexer;

import com.intellij.openapi.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Shared code for the Heredoc handling.
 */
public class HeredocSharedImpl {
  public static String cleanMarker(String marker, boolean ignoredLeadingTabs) {
    String markerText = trimNewline(marker);
    if (markerText.equals("$")) {
      return markerText;
    }

    Pair<Integer, Integer> offsets = getStartEndOffsets(markerText, ignoredLeadingTabs);
    int start = offsets.first;
    int end = offsets.second;

    return end <= markerText.length() && start < end ? markerText.substring(start, end) : marker;
  }

  public static int startMarkerTextOffset(String markerText, boolean ignoredLeadingTabs) {
    return getStartEndOffsets(markerText, ignoredLeadingTabs).first;
  }

  public static int endMarkerTextOffset(String markerText) {
    return getStartEndOffsets(markerText, false).second;
  }

  public static boolean isEvaluatingMarker(String marker) {
    String markerText = trimNewline(marker);

    return !markerText.startsWith("\"") && !markerText.startsWith("'") && !markerText.startsWith("\\") && !markerText.startsWith("$");
  }

  private static String trimNewline(String marker) {
    return StringUtils.removeEnd(marker, "\n");
  }

  public static String wrapMarker(String newName, String originalMarker) {
    Pair<Integer, Integer> offsets = getStartEndOffsets(originalMarker, true);
    int start = offsets.first;
    int end = offsets.second;

    return (end <= originalMarker.length() && start < end)
        ? originalMarker.substring(0, start) + newName + originalMarker.substring(end)
        : newName;
  }

  private static Pair<Integer, Integer> getStartEndOffsets(@NotNull String markerText, boolean ignoredLeadingTabs) {
    if (markerText.isEmpty()) {
      return Pair.create(0, 0);
    }

    if (markerText.length() == 1) {
      return Pair.create(0, 1);
    }

    if (markerText.charAt(0) == '\\' && markerText.length() > 1) {
      return Pair.create(1, markerText.length());
    }

    int length = markerText.length();
    int start = 0;
    int end = length - 1;

    while (ignoredLeadingTabs && start < (length - 1) && markerText.charAt(start) == '\t') {
      start++;
    }

    if (markerText.charAt(start) == '$' && length > (start + 2) && (markerText.charAt(start + 1) == '"' || markerText.charAt(end) == '\'')) {
      start++;
      length--;
    }

    while (end > 0 && markerText.charAt(end) == '\n') {
      end--;
    }

    if (length > 0 && (markerText.charAt(start) == '\'' || markerText.charAt(start) == '"') && markerText.charAt(end) == markerText.charAt(start)) {
      start++;
      end--;
      length -= 2;
    }

    return Pair.create(start, end + 1);
  }
}
