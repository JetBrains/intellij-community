package com.jetbrains.python.packaging;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

  public static final Comparator<String> VERSION_COMPARATOR = new Comparator<String>() {
    @Override
    public int compare(String version1, String version2) {
      final List<String> vs1 = parse(version1);
      final List<String> vs2 = parse(version2);
      int result = 0;
      for (int i = 0; i < vs1.size() && i < vs2.size(); i++) {
        result = vs1.get(i).compareTo(vs2.get(i));
        if (result != 0) {
          break;
        }
      }
      if (result == 0) {
        return vs1.size() - vs2.size();
      }
      return result;
    }

    @NotNull
    private List<String> parse(@Nullable String s) {
      // TODO: Take version modificators (dev, alpha, beta, b, etc.) into account
      return s != null ? StringUtil.split(s, ".") : Collections.<String>emptyList();
    }
  };

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

  public boolean match(@NotNull List<PyPackage> packages) {
    for (PyPackage pkg : packages) {
      if (myName.equalsIgnoreCase(pkg.getName())) {
        if (myVersion == null || VERSION_COMPARATOR.compare(myVersion, pkg.getVersion()) == 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static PyRequirement fromString(@NotNull String s) {
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

  @Nullable
  public static List<PyRequirement> parse(@NotNull String s) {
    final List<PyRequirement> result = new ArrayList<PyRequirement>();
    for (String line : StringUtil.splitByLines(s)) {
      final String trimmed = line.trim();
      if (!trimmed.isEmpty()) {
        final PyRequirement req = fromString(line);
        if (req != null) {
          result.add(req);
        }
        else {
          return null;
        }
      }
    }
    return result;
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
