package com.jetbrains.python.packaging;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author vlan
 */
public class PyRequirement {
  private static final Pattern NAME = Pattern.compile("\\s*(\\w(\\w|[-.])*)\\s*(.*)");
  private static final Pattern VERSION_SPEC = Pattern.compile("\\s*(<=?|>=?|==|!=)\\s*((\\w|[-.])+)");
  private static final Pattern EDITABLE_EGG = Pattern.compile("\\s*-e\\s+([^#]*)#egg=(.*)");
  private static final Pattern RECURSIVE_REQUIREMENT = Pattern.compile("\\s*-r\\s+(.*)");
  private static final Pattern NAME_VERSION = Pattern.compile("\\s*(\\w(\\w|[.])*)-((\\w|[-.])+)");

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
  @Nullable private final String myURL;

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

    @Nullable
    private String replace(@NotNull String s) {
      final Map<String, String> sub = ImmutableMap.of("pre", "c",
                                                      "preview", "c",
                                                      "rc", "c",
                                                      "dev", "@");
      final String tmp = sub.get(s);
      if (tmp != null) {
        s = tmp;
      }
      if (s.equals(".") || s.equals("-")) {
        return null;
      }
      if (s.matches("[0-9]+")) {
        final int value = Integer.parseInt(s);
        return String.format("%08d", value);
      }
      return "*" + s;
    }

    @NotNull
    private List<String> parse(@Nullable String s) {
      // Version parsing from pkg_resources ensures that all the "pre", "alpha", "rc", etc. are sorted correctly
      final Pattern COMPONENT_RE = Pattern.compile("\\d+|[a-z]+|\\.|-|.+");
      final List<String> results = new ArrayList<String>();
      final Matcher matcher = COMPONENT_RE.matcher(s);
      while (matcher.find()) {
        final String component = replace(matcher.group());
        if (component == null) {
          continue;
        }
        results.add(component);
      }
      results.add("*final");
      return results;
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
    myURL = null;
  }

  public PyRequirement(@NotNull String name, @Nullable String version, @NotNull String url) {
    myName = name;
    if (version != null) {
      myVersionSpecs = Collections.singletonList(new VersionSpec(Relation.GTE, version));
    }
    else {
      myVersionSpecs = Collections.emptyList();
    }
    myURL = url;
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

  @NotNull
  public List<String> toOptions() {
    if (myURL != null) {
      final int size = myVersionSpecs.size();
      assert size <= 1;
      final List<String> results = new ArrayList<String>();
      results.add("-e");
      final String urlAndName = myURL + "#egg=" + myName;
      if (size == 0) {
        results.add(urlAndName);
      }
      else {
        final VersionSpec versionSpec = myVersionSpecs.get(0);
        assert versionSpec.getRelation() == Relation.EQ;
        results.add(urlAndName + "-" + versionSpec.getVersion());
      }
      return results;
    }
    else {
      return Collections.singletonList(toString());
    }
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
  public static PyRequirement fromString(@NotNull String line) {
    // TODO: Extras, multi-line requirements '\'
    final PyRequirement editableEgg = parseEditableEgg(line);
    if (editableEgg != null) {
      return editableEgg;
    }
    final Matcher nameMatcher = NAME.matcher(line);
    if (!nameMatcher.matches()) {
      return null;
    }
    final String name = nameMatcher.group(1);
    final String rest = nameMatcher.group(3);
    final List<VersionSpec> versionSpecs = new ArrayList<VersionSpec>();
    if (!rest.trim().isEmpty()) {
      final Matcher versionSpecMatcher = VERSION_SPEC.matcher(rest);
      while (versionSpecMatcher.find()) {
        final String rel = versionSpecMatcher.group(1);
        final String version = versionSpecMatcher.group(2);
        final Relation relation = Relation.fromString(rel);
        if (relation == null) {
          return null;
        }
        versionSpecs.add(new VersionSpec(relation, version));
      }
    }
    return new PyRequirement(name, versionSpecs);
  }

  @NotNull
  public static List<PyRequirement> parse(@NotNull String s) {
    return parse(s, null, new HashSet<VirtualFile>());
  }

  @NotNull
  public static List<PyRequirement> parse(@NotNull VirtualFile file) {
    return parse(file, new HashSet<VirtualFile>());
  }

  @NotNull
  public static List<PyRequirement> parse(@NotNull VirtualFile file, @NotNull Set<VirtualFile> visited) {
    if (!visited.contains(file)) {
      visited.add(file);
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        return parse(document.getText(), file, visited);
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  private static List<PyRequirement> parse(@NotNull String s, @Nullable VirtualFile anchor, @NotNull Set<VirtualFile> visited) {
    final List<PyRequirement> result = new ArrayList<PyRequirement>();
    for (String line : StringUtil.splitByLines(s)) {
      final String trimmed = line.trim();
      if (!trimmed.isEmpty()) {
        final PyRequirement req = fromString(line);
        if (req != null) {
          result.add(req);
        }
        else if (anchor != null) {
          result.addAll(parseRecursiveRequirement(trimmed, anchor, visited));
        }
      }
    }
    return result;
  }

  @NotNull
  private static List<PyRequirement> parseRecursiveRequirement(@NotNull String line, @NotNull VirtualFile anchor,
                                                               @NotNull Set<VirtualFile> visited) {
    final Matcher matcher = RECURSIVE_REQUIREMENT.matcher(line);
    if (matcher.matches()) {
      final String fileName = matcher.group(1);
      final VirtualFile dir = anchor.getParent();
      if (dir != null) {
        VirtualFile file = dir.findFileByRelativePath(fileName);
        if (file == null) {
          file = LocalFileSystem.getInstance().findFileByPath(fileName);
        }
        if (file != null) {
          return parse(file, visited);
        }

      }
    }
    return Collections.emptyList();
  }

  @Nullable
  private static PyRequirement parseEditableEgg(@NotNull String line) {
    final Matcher editableEggMatcher = EDITABLE_EGG.matcher(line);
    if (!editableEggMatcher.matches()) {
      return null;
    }
    final String url = editableEggMatcher.group(1);
    final String egg = editableEggMatcher.group(2);
    final Matcher nameVersionMatcher = NAME_VERSION.matcher(egg);
    if (nameVersionMatcher.matches()) {
      final String name = normalizeName(nameVersionMatcher.group(1));
      final String version = normalizeVersion(nameVersionMatcher.group(3));
      return new PyRequirement(name, version, url);
    }
    else {
      final Matcher nameMatcher = NAME.matcher(egg);
      if (!nameMatcher.matches()) {
        return null;
      }
      final String name = normalizeName(nameMatcher.group(1));
      return new PyRequirement(name, null, url);
    }
  }

  @NotNull
  private static String normalizeName(@NotNull String s) {
    return s.replace("_", "-");
  }

  @NotNull
  private static String normalizeVersion(@NotNull String s) {
    return s.replace("_", "-").replaceAll("-?py[0-9\\.]+", "");
  }
}
