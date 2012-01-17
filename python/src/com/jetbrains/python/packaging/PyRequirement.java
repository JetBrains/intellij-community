package com.jetbrains.python.packaging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author vlan
 */
public class PyRequirement {
  private static final Pattern NAME = Pattern.compile("\\s*((\\w|[-.])+)\\s*(.*)");
  private static final Pattern VERSION = Pattern.compile("\\s*(<=?|>=?|==|!=)\\s*((\\w|[-.])+).*");

  private final String myName;
  private final String myRelation;
  private final String myVersion;

  public PyRequirement(@NotNull String name) {
    this(name, null, null);
  }

  public PyRequirement(@NotNull String name, @NotNull String version) {
    this(name, "==", version);
  }

  public PyRequirement(@NotNull String name, @Nullable String relation, @Nullable String version) {
    if (relation == null) {
      assert version == null;
    }
    myName = name;
    myRelation = relation;
    myVersion = version;
  }

  @NotNull
  @Override
  public String toString() {
    if (myRelation != null && myVersion != null) {
      return myName + myRelation + myVersion;
    }
    else {
      return myName;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyRequirement that = (PyRequirement)o;

    if (!myName.toLowerCase().equals(that.myName.toLowerCase())) return false;
    if (myRelation != null ? !myRelation.equals(that.myRelation) : that.myRelation != null) return false;
    if (myVersion != null ? !myVersion.equals(that.myVersion) : that.myVersion != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.toLowerCase().hashCode();
    result = 31 * result + (myRelation != null ? myRelation.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    return result;
  }

  @Nullable
  public static PyRequirement parse(@NotNull String s) {
    // TODO: Extras, multi-line requirements '\'
    final Matcher nameMatcher = NAME.matcher(s);
    if (nameMatcher.matches()) {
      final String name = nameMatcher.group(1);
      final String rest = nameMatcher.group(3);
      final String relation;
      final String version;
      if (!rest.trim().isEmpty()) {
        final Matcher versionMatcher = VERSION.matcher(rest);
        if (versionMatcher.matches()) {
          relation = versionMatcher.group(1);
          version = versionMatcher.group(2);
        }
        else {
          return null;
        }
      }
      else {
        relation = null;
        version = null;
      }
      return new PyRequirement(name, relation, version);
    }
    return null;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public String getRelation() {
    return myRelation;
  }

  @Nullable
  public String getVersion() {
    return myVersion;
  }
}
