package com.intellij.tasks.jira;


import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mikhail Golubev
 */
public class JiraVersion {
  private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

  private final int myMajorNumber, myMinorNumber, myBuildNumber;

  public JiraVersion(int majorNumber) {
    this(majorNumber, 0, 0);
  }

  public JiraVersion(int majorNumber, int minorNumber) {
    this(majorNumber, minorNumber, 0);
  }

  public JiraVersion(int majorNumber, int minorNumber, int buildNumber) {
    myMajorNumber = majorNumber;
    myMinorNumber = minorNumber;
    myBuildNumber = buildNumber;
  }

  public JiraVersion(@NotNull String version) {
    Matcher m = VERSION_PATTERN.matcher(version);
    if (!m.matches()) {
      throw new IllegalArgumentException("Illegal JIRA version number: " + version);
    }
    myMajorNumber = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
    myMinorNumber = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
    myBuildNumber = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
  }

  public int getMajorNumber() {
    return myMajorNumber;
  }

  public int getMinorNumber() {
    return myMinorNumber;
  }

  public int getBuildNumber() {
    return myBuildNumber;
  }

  @Override
  public String toString() {
    return String.format("%d.%d.%d", myMajorNumber, myMinorNumber, myBuildNumber);
  }
}
