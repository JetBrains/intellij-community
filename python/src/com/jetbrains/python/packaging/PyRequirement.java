package com.jetbrains.python.packaging;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
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
  private static final Pattern VERSION = Pattern.compile("\\s*(<=?|>=?|==|!=)\\s*((\\w|[-.])+)");

  public enum Relation {
    LT("<"),
    LTE("<="),
    GT(">"),
    GTE(">="),
    EQ("=="),
    NE("!=");

    @NotNull private final String myValue;

    Relation(@NotNull String value) {
      myValue = value;
    }

    @NotNull
    @Override
    public String toString() {
      return myValue;
    }

    @Nullable
    public static Relation fromString(@NotNull String value) {
      for (Relation relation : Relation.values()) {
        if (relation.myValue.equals(value)) {
          return relation;
        }
      }
      return null;
    }

    public boolean isSuccessful(int comparisonResult) {
      switch (this) {
        case LT:
          return comparisonResult < 0;
        case LTE:
          return comparisonResult <= 0;
        case GT:
          return comparisonResult > 0;
        case GTE:
          return comparisonResult >= 0;
        case EQ:
          return comparisonResult == 0;
        case NE:
          return comparisonResult != 0;
      }
      return false;
    }
  }

  public static class VersionSpec {
    @NotNull private final Relation myRelation;
    @NotNull private final String myVersion;

    public VersionSpec(@NotNull Relation relation, @NotNull String version) {
      myRelation = relation;
      myVersion = version;
    }

    @Override
    public String toString() {
      return myRelation + myVersion;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      VersionSpec spec = (VersionSpec)o;
      if (myRelation != spec.myRelation) return false;
      if (!myVersion.equals(spec.myVersion)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = myRelation.hashCode();
      result = 31 * result + myVersion.hashCode();
      return result;
    }

    @NotNull
    public Relation getRelation() {
      return myRelation;
    }

    @NotNull
    public String getVersion() {
      return myVersion;
    }
  }

  @NotNull private final String myName;
  @NotNull private final List<VersionSpec> myVersionSpecs;

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
      // TODO: Take version modificators (dev, alpha, beta, b, etc.) into account, see pkg_resources parsing for ideas
      return s != null ? StringUtil.split(s, ".") : Collections.<String>emptyList();
    }
  };

  public PyRequirement(@NotNull String name) {
    this(name, Collections.<VersionSpec>emptyList());
  }

  public PyRequirement(@NotNull String name, @NotNull String version) {
    this(name, Collections.singletonList(new VersionSpec(Relation.EQ, version)));
  }

  public PyRequirement(@NotNull String name, @NotNull List<VersionSpec> versionSpecs) {
    myName = name;
    myVersionSpecs = versionSpecs;
  }

  @NotNull
  @Override
  public String toString() {
    return myName + StringUtil.join(myVersionSpecs,
                                    new Function<VersionSpec, String>() {
                                      @Override
                                      public String fun(VersionSpec spec) {
                                        return spec.toString();
                                      }
                                    },
                                    ",");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PyRequirement that = (PyRequirement)o;
    if (!myName.equals(that.myName)) return false;
    if (!myVersionSpecs.equals(that.myVersionSpecs)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myVersionSpecs.hashCode();
    return result;
  }

  public boolean match(@NotNull List<PyPackage> packages) {
    for (PyPackage pkg : packages) {
      if (myName.equalsIgnoreCase(pkg.getName())) {
        for (VersionSpec spec : myVersionSpecs) {
          final int cmp = VERSION_COMPARATOR.compare(pkg.getVersion(), spec.getVersion());
          final Relation relation = spec.getRelation();
          if (!relation.isSuccessful(cmp)) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static PyRequirement fromString(@NotNull String s) {
    // TODO: Extras, multi-line requirements '\'
    final Matcher nameMatcher = NAME.matcher(s);
    if (!nameMatcher.matches()) {
      return null;
    }
    final String name = nameMatcher.group(1);
    final String rest = nameMatcher.group(3);
    final List<VersionSpec> versionSpecs = new ArrayList<VersionSpec>();
    if (!rest.trim().isEmpty()) {
      final Matcher versionMatcher = VERSION.matcher(rest);
      while (versionMatcher.find()) {
        final String rel = versionMatcher.group(1);
        final String version = versionMatcher.group(2);
        final Relation relation = Relation.fromString(rel);
        if (relation == null) {
          return null;
        }
        versionSpecs.add(new VersionSpec(relation, version));
      }
    }
    return new PyRequirement(name, versionSpecs);
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
}
