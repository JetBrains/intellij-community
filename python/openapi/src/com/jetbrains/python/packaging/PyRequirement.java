/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.webcore.packaging.PackageVersionComparator;
import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author vlan
 * @see <a href="http://legacy.python.org/dev/peps/pep-0386/">[PEP-0386]</a>
 */
public class PyRequirement {

  @NotNull
  private static final String EDITABLE_GROUP = "editable";

  @NotNull
  private static final String EDITABLE_REGEXP = "((?<" + EDITABLE_GROUP + ">-e|--editable)[ \t]+)?";

  @NotNull
  private static final String USER_AT_REGEXP = "[\\w-]+@";

  @NotNull
  private static final String PATH_GROUP = "path";

  @NotNull
  private static final String PATH_REGEXP = "(?<" + PATH_GROUP + ">[^@#\\s]+)";

  @NotNull
  private static final String REVISION_REGEXP = "(@[^#\\s]+)?";

  @NotNull
  private static final String EGG_GROUP = "egg";

  @NotNull
  private static final String EGG_REGEXP = "(#egg=(?<" + EGG_GROUP + ">\\S+))?";

  @NotNull
  private static final String COMMENT_REGEXP = "([ \t]+#.*)?";

  @NotNull
  private static final String ARCHIVE_NAME_GROUP = "name";

  @NotNull
  private static final Pattern GITHUB_ARCHIVE_URL =
    Pattern.compile("https?://github\\.com/[^/\\s]+/(?<" + ARCHIVE_NAME_GROUP + ">[^/\\s]+)/archive/.+");

  @NotNull
  private static final Pattern ARCHIVE_URL =
    Pattern.compile("https?://\\S+/" +
                    "(?<" + ARCHIVE_NAME_GROUP + ">\\S+)" +
                    "(\\.tar\\.gz|\\.zip)(#(sha1|sha224|sha256|sha384|sha512|md5)=\\w+)?" + COMMENT_REGEXP);

  // supports: git+user@...
  @NotNull
  private static final Pattern GIT_PROJECT_URL = Pattern.compile(EDITABLE_REGEXP +
                                                                 "git\\+" + USER_AT_REGEXP + "[^:\\s]+:" +
                                                                 PATH_REGEXP + REVISION_REGEXP + EGG_REGEXP + COMMENT_REGEXP);

  @NotNull
  private static final Pattern BZR_PROJECT_URL = Pattern.compile(EDITABLE_REGEXP +
                                                                 "bzr\\+lp:" +
                                                                 PATH_REGEXP + REVISION_REGEXP + EGG_REGEXP + COMMENT_REGEXP);

  // supports: (bzr|git|hg|svn)(+smth)?://...
  @NotNull
  private static final Pattern VCS_PROJECT_URL = Pattern.compile(EDITABLE_REGEXP +
                                                                 "(bzr|git|hg|svn)(\\+[A-Za-z]+)?://?[^/]+/" +
                                                                 PATH_REGEXP + REVISION_REGEXP + EGG_REGEXP + COMMENT_REGEXP);

  @NotNull
  private final String myName;

  @NotNull
  private final String myOptions;

  @NotNull
  private final List<PyRequirementVersionSpec> myVersionSpecs;

  public PyRequirement(@NotNull String name) {
    this(name, Collections.<PyRequirementVersionSpec>emptyList());
  }

  public PyRequirement(@NotNull String name, @NotNull String version) {
    this(name, Collections.singletonList(new PyRequirementVersionSpec(PyRequirementRelation.EQ, version)));
  }

  public PyRequirement(@NotNull String name, @NotNull List<PyRequirementVersionSpec> versionSpecs) {
    myName = name;
    myVersionSpecs = versionSpecs;
    myOptions = toString();
  }

  public PyRequirement(@NotNull String name, @Nullable String version, @NotNull String url, boolean editable) {
    myName = name;
    if (version != null) {
      myVersionSpecs = Collections.singletonList(new PyRequirementVersionSpec(PyRequirementRelation.EQ, version));
    }
    else {
      myVersionSpecs = Collections.emptyList();
    }
    myOptions = url;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String toOptions() {
    return myOptions;
  }

  @Override
  public String toString() {
    return myName + StringUtil.join(myVersionSpecs, ",");
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyRequirement that = (PyRequirement)o;

    if (!myName.equals(that.myName)) return false;
    if (!myVersionSpecs.equals(that.myVersionSpecs)) return false;
    if (!myOptions.equals(that.myOptions)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myVersionSpecs.hashCode();
    result = 31 * result + myOptions.hashCode();
    return result;
  }

  @Nullable
  public PyPackage match(@NotNull List<PyPackage> packages) {
    for (PyPackage pkg : packages) {
      if (normalizeName(myName).equalsIgnoreCase(pkg.getName())) {
        for (PyRequirementVersionSpec spec : myVersionSpecs) {
          final int cmp = PackageVersionComparator.VERSION_COMPARATOR.compare(pkg.getVersion(), spec.getVersion());

          if (!spec.getRelation().isSuccessful(cmp)) {
            return null;
          }
        }
        return pkg;
      }
    }

    return null;
  }

  @NotNull
  public static PyRequirement fromStringGuaranteed(@NotNull String line) {
    final PyRequirement requirement = fromString(line);
    if (requirement == null) {
      throw new IllegalArgumentException("Failed to parse " + line);
    }
    return requirement;
  }

  @Nullable
  public static PyRequirement fromString(@NotNull String line) {
    final PyRequirement githubArchiveUrl = parseGithubArchiveUrl(line);
    if (githubArchiveUrl != null) {
      return githubArchiveUrl;
    }

    final PyRequirement archiveUrl = parseArchiveUrl(line);
    if (archiveUrl != null) {
      return archiveUrl;
    }

    final PyRequirement vcsProjectUrl = parseVcsProjectUrl(line);
    if (vcsProjectUrl != null) {
      return vcsProjectUrl;
    }

    return null; // TODO
  }

  @NotNull
  public static List<PyRequirement> parse(@NotNull String text) {
    return parse(text, null, new HashSet<>());
  }

  @NotNull
  public static List<PyRequirement> parse(@NotNull VirtualFile file) {
    return parse(loadText(file), file, new HashSet<>());
  }

  @NotNull
  private static List<PyRequirement> parse(@NotNull String text,
                                           @Nullable VirtualFile containingFile,
                                           @NotNull Set<VirtualFile> visitedFiles) {
    return Arrays
      .stream(StringUtil.splitByLines(text))
      .map(String::trim)
      .filter(line -> !line.isEmpty())
      .map(PyRequirement::fromString)
      .filter(req -> req != null)
      .collect(Collectors.toCollection(LinkedHashSet::new))
      .stream()
      .collect(Collectors.toList());
  }

  @Nullable
  private static PyRequirement parseGithubArchiveUrl(@NotNull String line) {
    final Matcher matcher = GITHUB_ARCHIVE_URL.matcher(line);

    if (matcher.matches()) {
      return new PyRequirement(matcher.group(ARCHIVE_NAME_GROUP), null, line, false);
    }

    return null;
  }

  @Nullable
  private static PyRequirement parseArchiveUrl(@NotNull String line) {
    final Matcher matcher = ARCHIVE_URL.matcher(line);

    if (matcher.matches()) {
      final Pair<String, String> nameAndVersion = parseNameAndVersion(matcher.group(ARCHIVE_NAME_GROUP));

      return new PyRequirement(nameAndVersion.getFirst(), nameAndVersion.getSecond(), line, false);
    }

    return null;
  }

  @Nullable
  private static PyRequirement parseVcsProjectUrl(@NotNull String line) {
    final Matcher vcsMatcher = VCS_PROJECT_URL.matcher(line);
    if (vcsMatcher.matches()) {
      return createVcsRequirement(line, vcsMatcher);
    }

    final Matcher gitMatcher = GIT_PROJECT_URL.matcher(line);
    if (gitMatcher.matches()) {
      return createVcsRequirement(line, gitMatcher);
    }

    final Matcher bzrMatcher = BZR_PROJECT_URL.matcher(line);
    if (bzrMatcher.matches()) {
      return createVcsRequirement(line, bzrMatcher);
    }

    return null;
  }

  @NotNull
  private static String loadText(@NotNull VirtualFile file) {
    final Document document = FileDocumentManager.getInstance().getDocument(file);

    return document == null ? "" : document.getText();
  }

  @NotNull
  private static PyRequirement createVcsRequirement(@NotNull String line, @NotNull Matcher matcher) {
    final boolean editable = matcher.group(EDITABLE_GROUP) != null;
    final String path = matcher.group(PATH_GROUP);
    final String egg = matcher.group(EGG_GROUP);

    final String project = extractProject(dropTrunk(dropRevision(path)));
    final Pair<String, String> nameAndVersion = parseNameAndVersion(egg == null ? StringUtil.trimEnd(project, ".git") : egg);

    return new PyRequirement(nameAndVersion.getFirst(), nameAndVersion.getSecond(), line, editable);
  }

  @NotNull
  private static String dropRevision(@NotNull String path) {
    final int atIndex = path.lastIndexOf("@");

    if (atIndex != -1) {
      return path.substring(0, atIndex);
    }

    return path;
  }

  @NotNull
  private static String dropTrunk(@NotNull String path) {
    final String slashTrunk = "/trunk";

    if (path.endsWith(slashTrunk)) {
      return path.substring(0, path.length() - slashTrunk.length());
    }

    final String slashTrunkSlash = "/trunk/";

    if (path.endsWith(slashTrunkSlash)) {
      return path.substring(0, path.length() - slashTrunkSlash.length());
    }

    return path;
  }

  @NotNull
  private static String extractProject(@NotNull String path) {
    final int end = path.endsWith("/") ? path.length() - 1 : path.length();
    final int slashIndex = path.lastIndexOf("/", end - 1);

    if (slashIndex != -1) {
      return path.substring(slashIndex + 1, end);
    }

    if (end != path.length()) {
      return path.substring(0, end);
    }

    return path;
  }

  @NotNull
  private static Pair<String, String> parseNameAndVersion(@NotNull String name) {
    boolean isName = true;
    final List<String> nameParts = new ArrayList<String>();
    final List<String> versionParts = new ArrayList<String>();

    for (String part : StringUtil.split(name, "-")) {
      final boolean partStartsWithDigit = !part.isEmpty() && Character.isDigit(part.charAt(0));

      if (partStartsWithDigit || "dev".equals(part)) {
        isName = false;
      }

      if (isName) {
        nameParts.add(part);
      }
      else {
        versionParts.add(part);
      }
    }

    return Pair.create(normalizeNameParts(nameParts), normalizeVersionParts(versionParts));
  }

  @NotNull
  private static String normalizeNameParts(@NotNull List<String> nameParts) {
    return normalizeName(StringUtil.join(nameParts, "-"));
  }

  @Nullable
  private static String normalizeVersionParts(@NotNull List<String> versionParts) {
    return versionParts.isEmpty() ? null : normalizeVersion(StringUtil.join(versionParts, "-"));
  }

  @NotNull
  private static String normalizeName(@NotNull String s) {
    return s.replace("_", "-");
  }

  @NotNull
  private static String normalizeVersion(@NotNull String s) {
    return s.replace("_", "-").replaceAll("-?py[\\d\\.]+", "");
  }
}
