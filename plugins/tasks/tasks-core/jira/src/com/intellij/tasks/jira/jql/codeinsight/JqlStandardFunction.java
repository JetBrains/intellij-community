package com.intellij.tasks.jira.jql.codeinsight;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Mikhail Golubev
 */
public enum JqlStandardFunction {
  RELEASED_VERSIONS("releasedVersions", JqlFieldType.VERSION, true), // () -> [Version]
  LATEST_RELEASED_VERSION("latestReleasedVersion", JqlFieldType.VERSION, false), // () -> Version
  UNRELEASED_VERSIONS("unreleasedVersions", JqlFieldType.VERSION, true), // () -> [Version]
  EARLIEST_UNRELEASED_VERSION("earliestUnreleasedVersion", JqlFieldType.VERSION, false), // () -> Version
  MEMBERS_OF("membersOf", JqlFieldType.USER, true), // String -> [User]
  CURRENT_USER("currentUser", JqlFieldType.USER, false), // () -> User
  COMPONENTS_LEAD_BY_USER("componentsLeadByUser", JqlFieldType.COMPONENT, true), // User -> [Component]
  CURRENT_LOGIN("currentLogin", JqlFieldType.DATE, false), // () -> Date
  LAST_LOGIN("lastLogin", JqlFieldType.DATE, false), // () -> Date
  NOW("now", JqlFieldType.DATE, false), // () -> Date
  START_OF_DAY("startOfDay", JqlFieldType.DATE, false), // () -> Date
  START_OF_WEEK("startOfWeek", JqlFieldType.DATE, false), // () -> Date
  START_OF_MONTH("startOfMonth", JqlFieldType.DATE, false), // () -> Date
  START_OF_YEAR("startOfYear", JqlFieldType.DATE, false), // () -> Date
  END_OF_DAY("endOfDay", JqlFieldType.DATE, false), // () -> Date
  END_OF_WEEK("endOfWeek", JqlFieldType.DATE, false), // () -> Date
  END_OF_MONTH("endOfMonth", JqlFieldType.DATE, false), // () -> Date
  END_OF_YEAR("endOfYear", JqlFieldType.DATE, false), // () -> Date
  ISSUE_HISTORY("issueHistory", JqlFieldType.ISSUE, true), // () -> [Issue]
  LINKED_ISSUES("linkedIssues", JqlFieldType.ISSUE, true), // () -> [Issue]
  VOTED_ISSUES("votedIssues", JqlFieldType.ISSUE, true), // () -> [Issue]
  WATCHED_ISSUES("watchedIssues", JqlFieldType.ISSUE, true), // () -> [Issue]
  PROJECTS_LEAD_BY_USER("projectsLeadByUser", JqlFieldType.PROJECT, true), // User -> [Project]
  PROJECTS_WHERE_USER_HAS_PERMISSION("projectsWhereUserHasPermission", JqlFieldType.PROJECT, true), // ? -> [Project]
  PROJECTS_WHERE_USER_HAS_ROLE("projectsWhereUserHasRole", null, true); // ? -> [Project]

  private final String myName;
  private final JqlFieldType myReturnType;
  private final boolean myHasMultipleResults;

  JqlStandardFunction(String name, JqlFieldType returnType, boolean hasMultipleResults) {
    myName = name;
    myReturnType = returnType;
    myHasMultipleResults = hasMultipleResults;
  }

  public String getName() {
    return myName;
  }

  public JqlFieldType getReturnType() {
    return myReturnType;
  }

  public boolean hasMultipleResults() {
    return myHasMultipleResults;
  }

  private static final JqlStandardFunction[] VALUES = values();

  private static final Map<String, JqlStandardFunction> NAME_LOOKUP = ContainerUtil.newMapFromValues(ContainerUtil.iterate(VALUES), new Convertor<JqlStandardFunction, String>() {
    @Override
    public String convert(JqlStandardFunction field) {
      return field.getName();
    }
  });

  public static JqlStandardFunction byName(@NotNull String name) {
    return NAME_LOOKUP.get(name);
  }

  private static final MultiMap<Pair<JqlFieldType, Boolean>, String> TYPE_LOOKUP = new MultiMap<>();
  static {
    for (JqlStandardFunction function : VALUES) {
      TYPE_LOOKUP.putValue(Pair.create(function.getReturnType(), function.hasMultipleResults()), function.getName());
    }
  }

  public static List<String> allOfType(@NotNull JqlFieldType type, boolean multipleResults) {
    if (type == JqlFieldType.UNKNOWN) {
      return ALL_FUNCTION_NAMES;
    }
    return new ArrayList<>(TYPE_LOOKUP.get(Pair.create(type, multipleResults)));
  }

  public static final List<String> ALL_FUNCTION_NAMES = ContainerUtil.map2List(VALUES, field -> field.myName);
}
