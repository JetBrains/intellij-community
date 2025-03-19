// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @see <a href="https://pip.pypa.io/en/stable/reference/pip_install/"><code>pip install</code> documentation</a>
 * @see <a href="https://www.python.org/dev/peps/pep-0508/">PEP-508</a>
 * @see <a href="https://www.python.org/dev/peps/pep-0440/">PEP-440</a>
 * @see PyRequirement
 * @see PyPackageVersionNormalizer
 * @see PyPackageManager#parseRequirement(String)
 * @see PyPackageManager#parseRequirements(String)
 * @see PyPackageManager#parseRequirements(VirtualFile)
 */
public final class PyRequirementParser {

  // common regular expressions

  private static final @NotNull String LINE_WS_REGEXP = "[ \t]";

  private static final @NotNull String COMMENT_GROUP = "comment";

  private static final @NotNull String COMMENT_REGEXP = "(?<" + COMMENT_GROUP + ">" + LINE_WS_REGEXP + "+#.*)?";

  private static final @NotNull String NAME_GROUP = "name";

  // PEP-508
  // https://www.python.org/dev/peps/pep-0508/

  private static final @NotNull String IDENTIFIER_REGEXP = "[A-Za-z0-9]([-_\\.]?[A-Za-z0-9])*";

  private static final @NotNull String NAME_REGEXP = "(?<" + NAME_GROUP + ">" + IDENTIFIER_REGEXP + ")";

  private static final @NotNull String EXTRAS_REGEXP =
    "\\[" + IDENTIFIER_REGEXP + "(" + LINE_WS_REGEXP + "*," + LINE_WS_REGEXP + "*" + IDENTIFIER_REGEXP + ")*" + "\\]";

  // archive-related regular expressions

  private static final @NotNull Pattern GITHUB_ARCHIVE_URL =
    Pattern.compile("https?://github\\.com/[^/\\s]+/(?<" + NAME_GROUP + ">[^/\\s]+)/archive/\\S+" + COMMENT_REGEXP);

  private static final @NotNull Pattern GITLAB_ARCHIVE_URL =
    Pattern.compile("https?://gitlab\\.com/[^/\\s]+/(?<" + NAME_GROUP + ">[^/\\s]+)/repository/\\S+" + COMMENT_REGEXP);

  private static final @NotNull Pattern ARCHIVE_URL =
    Pattern.compile("https?://\\S+/" +
                    "(?<" + NAME_GROUP + ">\\S+)" +
                    "(\\.tar\\.gz|\\.zip)(#(sha1|sha224|sha256|sha384|sha512|md5)=\\w+)?" + COMMENT_REGEXP);

  // vcs-related regular expressions
  // don't forget to update calculateVcsInstallOptions(Matcher) after this section changing

  private static final @NotNull String VCS_EDITABLE_GROUP = "editable";

  private static final @NotNull String VCS_EDITABLE_REGEXP = "((?<" + VCS_EDITABLE_GROUP + ">-e|--editable)" + LINE_WS_REGEXP + "+)?";

  private static final @NotNull String VCS_SRC_BEFORE_GROUP = "srcb";

  private static final @NotNull String VCS_SRC_AFTER_GROUP = "srca";

  private static final @NotNull String VCS_SRC_BEFORE_REGEXP =
    "(?<" + VCS_SRC_BEFORE_GROUP + ">--src" + LINE_WS_REGEXP + "+\\S+" + LINE_WS_REGEXP + "+)?";

  private static final @NotNull String VCS_SRC_AFTER_REGEXP =
    "(?<" + VCS_SRC_AFTER_GROUP + ">" + LINE_WS_REGEXP + "+--src" + LINE_WS_REGEXP + "+\\S+)?";

  private static final @NotNull String PATH_IN_VCS_GROUP = "path";

  private static final @NotNull String PATH_IN_VCS_REGEXP = "(?<" + PATH_IN_VCS_GROUP + ">[^@#\\s]+)";

  private static final @NotNull String VCS_REVISION_REGEXP = "(@[^#\\s]+)?";

  private static final @NotNull String VCS_EGG_BEFORE_SUBDIR_GROUP = "eggb";

  private static final @NotNull String VCS_EGG_AFTER_SUBDIR_GROUP = "egga";

  private static final @NotNull String VCS_EXTRAS_BEFORE_SUBDIR_GROUP = "extrasb";

  private static final @NotNull String VCS_EXTRAS_AFTER_SUBDIR_GROUP = "extrasa";

  private static final @NotNull String VCS_PARAMS_REGEXP =
    "(" +
    "(" +
    "#egg=(?<" + VCS_EGG_BEFORE_SUBDIR_GROUP + ">[^&\\s\\[\\]]+)(?<" + VCS_EXTRAS_BEFORE_SUBDIR_GROUP + ">" + EXTRAS_REGEXP + ")?" +
    "(&subdirectory=\\S+)?" +
    ")" +
    "|" +
    "(" +
    "#subdirectory=[^&\\s]+" +
    "&egg=(?<" + VCS_EGG_AFTER_SUBDIR_GROUP + ">[^\\s\\[\\]]+)(?<" + VCS_EXTRAS_AFTER_SUBDIR_GROUP + ">" + EXTRAS_REGEXP + ")?" +
    ")" +
    ")?";

  private static final @NotNull String VCS_GROUP = "vcs";

  private static final @NotNull String VCS_URL_PREFIX = VCS_SRC_BEFORE_REGEXP + VCS_EDITABLE_REGEXP + "(?<" + VCS_GROUP + ">";

  private static final @NotNull String VCS_URL_SUFFIX =
    PATH_IN_VCS_REGEXP + VCS_REVISION_REGEXP + VCS_PARAMS_REGEXP + ")" + VCS_SRC_AFTER_REGEXP + COMMENT_REGEXP;

  private static final @NotNull String GIT_USER_AT_REGEXP = "[\\w-]+@";

  // supports: git+user@...
  private static final @NotNull Pattern GIT_PROJECT_URL =
    Pattern.compile(VCS_URL_PREFIX + "git\\+" + GIT_USER_AT_REGEXP + "[^:\\s]+:" + VCS_URL_SUFFIX);

  // supports: bzr+lp:...
  private static final @NotNull Pattern BZR_PROJECT_URL = Pattern.compile(VCS_URL_PREFIX + "bzr\\+lp:" + VCS_URL_SUFFIX);

  // supports: (bzr|git|hg|svn)(+smth)?://...
  private static final @NotNull Pattern VCS_PROJECT_URL =
    Pattern.compile(VCS_URL_PREFIX + "(bzr|git|hg|svn)(\\+[A-Za-z]+)?://?[^/]+/" + VCS_URL_SUFFIX);

  // requirement-related regular expressions
  // don't forget to update calculateRequirementInstallOptions(Matcher) after this section changing

  private static final @NotNull String REQUIREMENT_EXTRAS_GROUP = "extras";

  private static final @NotNull String REQUIREMENT_EXTRAS_REGEXP = "(?<" + REQUIREMENT_EXTRAS_GROUP + ">" + EXTRAS_REGEXP + ")?";

  // PEP-440
  // https://www.python.org/dev/peps/pep-0440/

  private static final @NotNull String REQUIREMENT_VERSIONS_SPECS_GROUP = "versionspecs";

  private static final @NotNull String REQUIREMENT_VERSION_SPEC_REGEXP = "(<=?|!=|===?|>=?|~=)" + LINE_WS_REGEXP + "*[\\.\\*\\+!\\w-]+";

  private static final @NotNull String REQUIREMENT_VERSIONS_SPECS_REGEXP =
    "(?<" + REQUIREMENT_VERSIONS_SPECS_GROUP + ">" + REQUIREMENT_VERSION_SPEC_REGEXP +
    "(" + LINE_WS_REGEXP + "*," + LINE_WS_REGEXP + "*" + REQUIREMENT_VERSION_SPEC_REGEXP + ")*)?";

  private static final @NotNull String REQUIREMENT_OPTIONS_GROUP = "options";

  private static final @NotNull String REQUIREMENT_OPTIONS_REGEXP =
    "(?<" + REQUIREMENT_OPTIONS_GROUP + ">(" + LINE_WS_REGEXP + "+(--global-option|--install-option)=\"[^\"]*\")+)?";

  private static final @NotNull String REQUIREMENT_GROUP = "requirement";

  private static final @NotNull Pattern REQUIREMENT = Pattern.compile(
    "(?<" + REQUIREMENT_GROUP + ">" +
    NAME_REGEXP +
    LINE_WS_REGEXP + "*" +
    REQUIREMENT_EXTRAS_REGEXP +
    LINE_WS_REGEXP + "*" +
    REQUIREMENT_VERSIONS_SPECS_REGEXP +
    ")" +
    REQUIREMENT_OPTIONS_REGEXP +
    COMMENT_REGEXP);

  public static @Nullable PyRequirement fromLine(@NotNull String line) {
    final PyRequirement githubArchiveUrl = parseGitArchiveUrl(GITHUB_ARCHIVE_URL, line);
    if (githubArchiveUrl != null) {
      return githubArchiveUrl;
    }

    final PyRequirement gitlabArchiveUrl = parseGitArchiveUrl(GITLAB_ARCHIVE_URL, line);
    if (gitlabArchiveUrl != null) {
      return gitlabArchiveUrl;
    }

    final PyRequirement archiveUrl = parseArchiveUrl(line);
    if (archiveUrl != null) {
      return archiveUrl;
    }

    final PyRequirement vcsProjectUrl = parseVcsProjectUrl(line);
    if (vcsProjectUrl != null) {
      return vcsProjectUrl;
    }

    return parseRequirement(line);
  }

  public static @NotNull List<PyRequirement> fromText(@NotNull String text) {
    return fromText(text, null, new HashSet<>());
  }

  public static @NotNull List<PyRequirement> fromFile(@NotNull VirtualFile file) {
    return fromText(loadText(file), file, new HashSet<>());
  }

  private static @Nullable PyRequirement parseGitArchiveUrl(@NotNull Pattern pattern, @NotNull String line) {
    final Matcher matcher = pattern.matcher(line);

    if (matcher.matches()) {
      return new PyRequirementImpl(matcher.group(NAME_GROUP), Collections.emptyList(),
                                   Collections.singletonList(dropComments(line, matcher)), "");
    }

    return null;
  }

  private static @Nullable PyRequirement parseArchiveUrl(@NotNull String line) {
    final Matcher matcher = ARCHIVE_URL.matcher(line);

    if (matcher.matches()) {
      return createVcsOrArchiveRequirement(parseNameAndVersionFromVcsOrArchive(matcher.group(NAME_GROUP)),
                                           Collections.singletonList(dropComments(line, matcher)),
                                           null);
    }

    return null;
  }

  private static @Nullable PyRequirement parseVcsProjectUrl(@NotNull String line) {
    final Matcher vcsMatcher = VCS_PROJECT_URL.matcher(line);
    if (vcsMatcher.matches()) {
      return createVcsRequirement(vcsMatcher);
    }

    final Matcher gitMatcher = GIT_PROJECT_URL.matcher(line);
    if (gitMatcher.matches()) {
      return createVcsRequirement(gitMatcher);
    }

    final Matcher bzrMatcher = BZR_PROJECT_URL.matcher(line);
    if (bzrMatcher.matches()) {
      return createVcsRequirement(bzrMatcher);
    }

    return null;
  }

  private static @Nullable PyRequirement parseRequirement(@NotNull String line) {
    final Matcher matcher = REQUIREMENT.matcher(line);
    if (matcher.matches()) {
      final String name = matcher.group(NAME_GROUP);
      final List<PyRequirementVersionSpec> versionSpecs = parseVersionSpecs(matcher.group(REQUIREMENT_VERSIONS_SPECS_GROUP));
      final List<String> installOptions = calculateRequirementInstallOptions(matcher);
      final String extras = matcher.group(REQUIREMENT_EXTRAS_GROUP);

      if (extras == null) {
        return new PyRequirementImpl(name, versionSpecs, installOptions, "");
      }
      else {
        return new PyRequirementImpl(name, versionSpecs, installOptions, extras);
      }
    }

    return null;
  }

  @ApiStatus.Internal
  public static @NotNull List<PyRequirement> fromText(@NotNull String text, @Nullable VirtualFile containingFile, @NotNull Set<VirtualFile> visitedFiles) {
    if (containingFile != null) {
      visitedFiles.add(containingFile);
    }

    return StreamEx
      .of(splitByLinesAndCollapse(text))
      .flatCollection(line -> parseLine(line, containingFile, visitedFiles))
      .nonNull()
      .distinct()
      .toList();
  }

  private static @NotNull String loadText(@NotNull VirtualFile file) {
    final Document document = FileDocumentManager.getInstance().getDocument(file);

    return document == null ? "" : document.getText();
  }

  private static @NotNull String dropComments(@NotNull String line, @NotNull Matcher matcher) {
    final int commentIndex = matcher.start(COMMENT_GROUP);

    if (commentIndex == -1) {
      return line;
    }

    return line.substring(0, findFirstNotWhiteSpaceBefore(line, commentIndex) + 1);
  }

  private static @NotNull Pair<String, String> parseNameAndVersionFromVcsOrArchive(@NotNull String name) {
    boolean isName = true;
    final List<String> nameParts = new ArrayList<>();
    final List<String> versionParts = new ArrayList<>();

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

    return Pair.create(normalizeVcsOrArchiveNameParts(nameParts), normalizeVcsOrArchiveVersionParts(versionParts));
  }

  private static @NotNull PyRequirement createVcsOrArchiveRequirement(@NotNull Pair<String, String> nameAndVersion,
                                                                      @NotNull List<String> installOptions,
                                                                      @Nullable String extras) {
    final String name = nameAndVersion.getFirst();
    final String version = nameAndVersion.getSecond();

    if (version == null) {
      if (extras == null) {
        return new PyRequirementImpl(name, Collections.emptyList(), installOptions, "");
      }
      else {
        return new PyRequirementImpl(name, Collections.emptyList(), installOptions, extras);
      }
    }

    final List<PyRequirementVersionSpec> versionSpecs = Collections.singletonList(PyRequirementsKt.pyRequirementVersionSpec(
      PyRequirementRelation.EQ, version));
    if (extras == null) {
      return new PyRequirementImpl(name, versionSpecs, installOptions, "");
    }
    else {
      return new PyRequirementImpl(name, versionSpecs, installOptions, extras);
    }
  }

  private static @NotNull PyRequirement createVcsRequirement(@NotNull Matcher matcher) {
    final String path = matcher.group(PATH_IN_VCS_GROUP);
    final String egg = getEgg(matcher);

    final String project = extractProject(dropTrunk(dropRevision(path)));
    final Pair<String, String> nameAndVersion =
      parseNameAndVersionFromVcsOrArchive(egg == null ? StringUtil.trimEnd(project, ".git") : egg);

    return createVcsOrArchiveRequirement(nameAndVersion, calculateVcsInstallOptions(matcher), getVcsExtras(matcher));
  }

  private static @NotNull List<PyRequirementVersionSpec> parseVersionSpecs(@Nullable String versionSpecs) {
    if (versionSpecs == null) return Collections.emptyList();

    return StreamSupport
      .stream(StringUtil.tokenize(versionSpecs, ",").spliterator(), false)
      .map(String::trim)
      .map(PyRequirementParser::parseVersionSpec)
      .filter(req -> req != null)
      .collect(Collectors.toList());
  }

  private static @NotNull List<String> calculateRequirementInstallOptions(@NotNull Matcher matcher) {
    final List<String> result = new ArrayList<>();
    result.add(matcher.group(REQUIREMENT_GROUP));

    final String requirementOptions = matcher.group(REQUIREMENT_OPTIONS_GROUP);
    if (requirementOptions != null) {
      Arrays.stream(requirementOptions.split(" "))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(result::add);
    }

    return result;
  }

  private static @NotNull List<String> splitByLinesAndCollapse(@NotNull String text) {
    final List<String> result = new ArrayList<>();
    final StringBuilder sb = new StringBuilder();

    for (String line : StringUtil.splitByLines(text)) {
      if (line.endsWith("\\") && !line.endsWith("\\\\")) {
        sb.append(line, 0, line.length() - 1);
      }
      else {
        if (sb.isEmpty()) {
          result.add(line);
        }
        else {
          sb.append(line);

          result.add(sb.toString());

          sb.setLength(0);
        }
      }
    }

    return result;
  }

  private static @NotNull List<PyRequirement> parseLine(@NotNull String line,
                                                        @Nullable VirtualFile containingFile,
                                                        @NotNull Set<VirtualFile> visitedFiles) {
    if (line.startsWith("-r")) {
      return parseRecursiveLine(line, containingFile, visitedFiles, "-r".length());
    }

    if (line.startsWith("--requirement ")) {
      return parseRecursiveLine(line, containingFile, visitedFiles, "--requirement ".length());
    }

    return Collections.singletonList(fromLine(line));
  }

  private static @NotNull String normalizeVcsOrArchiveNameParts(@NotNull List<String> nameParts) {
    return normalizeName(StringUtil.join(nameParts, "-"));
  }

  private static @Nullable String normalizeVcsOrArchiveVersionParts(@NotNull List<String> versionParts) {
    return versionParts.isEmpty() ? null : normalizeVersion(StringUtil.join(versionParts, "-"));
  }

  private static @NotNull List<String> calculateVcsInstallOptions(@NotNull Matcher matcher) {
    final List<String> result = new ArrayList<>();

    final String srcBefore = matcher.group(VCS_SRC_BEFORE_GROUP);
    if (srcBefore != null) {
      result.addAll(Arrays.asList(srcBefore.split("\\s+")));
    }

    final String editable = matcher.group(VCS_EDITABLE_GROUP);
    if (editable != null) {
      result.add(editable);
    }

    result.add(matcher.group(VCS_GROUP));

    final String srcAfter = matcher.group(VCS_SRC_AFTER_GROUP);
    if (srcAfter != null) {
      result.addAll(Arrays.asList(srcAfter.split("\\s+")).subList(1, 3)); // skip spaces before --src and get only two values
    }

    return result;
  }

  private static @Nullable String getEgg(@NotNull Matcher matcher) {
    final String beforeSubdir = matcher.group(VCS_EGG_BEFORE_SUBDIR_GROUP);

    return beforeSubdir == null ? matcher.group(VCS_EGG_AFTER_SUBDIR_GROUP) : beforeSubdir;
  }

  private static @NotNull String extractProject(@NotNull String path) {
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

  private static @NotNull String dropTrunk(@NotNull String path) {
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

  private static @NotNull String dropRevision(@NotNull String path) {
    final int atIndex = path.lastIndexOf("@");

    if (atIndex != -1) {
      return path.substring(0, atIndex);
    }

    return path;
  }

  private static @Nullable String getVcsExtras(@NotNull Matcher matcher) {
    final String beforeSubdir = matcher.group(VCS_EXTRAS_BEFORE_SUBDIR_GROUP);

    return beforeSubdir == null ? matcher.group(VCS_EXTRAS_AFTER_SUBDIR_GROUP) : beforeSubdir;
  }

  private static @Nullable PyRequirementVersionSpec parseVersionSpec(@NotNull String versionSpec) {
    PyRequirementRelation relation = null;

    if (versionSpec.startsWith("===")) {
      relation = PyRequirementRelation.STR_EQ;
    }
    else if (versionSpec.startsWith("==")) {
      relation = PyRequirementRelation.EQ;
    }
    else if (versionSpec.startsWith("<=")) {
      relation = PyRequirementRelation.LTE;
    }
    else if (versionSpec.startsWith(">=")) {
      relation = PyRequirementRelation.GTE;
    }
    else if (versionSpec.startsWith("<")) {
      relation = PyRequirementRelation.LT;
    }
    else if (versionSpec.startsWith(">")) {
      relation = PyRequirementRelation.GT;
    }
    else if (versionSpec.startsWith("~=")) {
      relation = PyRequirementRelation.COMPATIBLE;
    }
    else if (versionSpec.startsWith("!=")) {
      relation = PyRequirementRelation.NE;
    }

    if (relation != null) {
      final int versionIndex = findFirstNotWhiteSpaceAfter(versionSpec, relation.getPresentableText().length());
      return PyRequirementsKt.pyRequirementVersionSpec(relation, versionSpec.substring(versionIndex));
    }

    return null;
  }

  private static @NotNull List<PyRequirement> parseRecursiveLine(@NotNull String line,
                                                                 @Nullable VirtualFile containingFile,
                                                                 @NotNull Set<VirtualFile> visitedFiles,
                                                                 int flagLength) {
    if (containingFile == null) return Collections.emptyList();

    final int pathIndex = findFirstNotWhiteSpaceAfter(line, flagLength);
    if (pathIndex == line.length()) return Collections.emptyList();

    final String path = FileUtil.toSystemIndependentName(line.substring(pathIndex));
    final VirtualFile file = findRecursiveFile(containingFile, path);

    if (file != null && !visitedFiles.contains(file)) {
      return fromText(loadText(file), file, visitedFiles);
    }

    return Collections.emptyList();
  }

  private static @NotNull String normalizeName(@NotNull String s) {
    return s.replace('_', '-');
  }

  private static @NotNull String normalizeVersion(@NotNull String s) {
    return s.replace('_', '-').replaceAll("-?py[\\d\\.]+", "");
  }

  private static int findFirstNotWhiteSpaceAfter(@NotNull String line, int beginIndex) {
    for (int i = beginIndex; i < line.length(); i++) {
      if (!StringUtil.isWhiteSpace(line.charAt(i))) {
        return i;
      }
    }

    return line.length();
  }

  private static int findFirstNotWhiteSpaceBefore(@NotNull String line, int beginIndex) {
    for (int i = beginIndex; i >= 0; i--) {
      if (!StringUtil.isWhiteSpace(line.charAt(i))) {
        return i;
      }
    }

    return -1;
  }

  private static @Nullable VirtualFile findRecursiveFile(@NotNull VirtualFile containingFile, @NotNull String path) {
    final VirtualFile dir = containingFile.getParent();
    if (dir == null) return null;

    final VirtualFile file = dir.findFileByRelativePath(path);
    if (file != null) return file;

    return LocalFileSystem.getInstance().findFileByPath(path);
  }
}
