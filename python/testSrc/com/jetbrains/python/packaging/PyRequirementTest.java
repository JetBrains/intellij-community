// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.requirements.PyPackageVersion;
import com.intellij.python.requirements.PyRequirementImpl;
import com.intellij.python.requirements.parser.PyRequirementParser;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.python.requirements.PyRequirementsKt.pyRequirement;
import static com.intellij.python.requirements.PyRequirementsKt.pyRequirementVersionSpec;
import static com.intellij.python.requirements.parser.PyRequirementParser.fromLine;
import static com.jetbrains.python.packaging.requirement.PyRequirementRelation.COMPATIBLE;
import static com.jetbrains.python.packaging.requirement.PyRequirementRelation.EQ;
import static com.jetbrains.python.packaging.requirement.PyRequirementRelation.GT;
import static com.jetbrains.python.packaging.requirement.PyRequirementRelation.GTE;
import static com.jetbrains.python.packaging.requirement.PyRequirementRelation.LT;
import static com.jetbrains.python.packaging.requirement.PyRequirementRelation.LTE;
import static com.jetbrains.python.packaging.requirement.PyRequirementRelation.NE;
import static com.jetbrains.python.packaging.requirement.PyRequirementRelation.STR_EQ;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Subsystems.PackagingRequirements
@Layers.Functional
public class PyRequirementTest extends PyTestCase {
  // REQUIREMENT
  // TODO: name normalization
  // TODO: hashes
  // https://www.python.org/dev/peps/pep-0508/#names
  public void testRequirement() {
    assertEquals(pyRequirement("Orange-Bioinformatics", null), fromLine("Orange-Bioinformatics"));
    assertEquals(pyRequirement("MOCPy", null), fromLine("MOCPy"));
    assertEquals(pyRequirement("score.webassets", null), fromLine("score.webassets"));
    assertEquals(pyRequirement("pip_helpers", null), fromLine("pip_helpers"));
    assertEquals(pyRequirement("Django", null), fromLine("Django"));
    assertEquals(pyRequirement("django", null), fromLine("django"));
    assertEquals(pyRequirement("pinax-utils", null), fromLine("pinax-utils"));
    assertEquals(pyRequirement("no_limit_nester", null), fromLine("no_limit_nester"));
    assertEquals(pyRequirement("Flask-Celery-py3", null), fromLine("Flask-Celery-py3"));
  }

  // https://www.python.org/dev/peps/pep-0440/
  public void testRequirementVersion() {
    assertEquals(pyRequirement("Orange-Bioinformatics", EQ, "2.5a20"), fromLine("Orange-Bioinformatics==2.5a20"));
    assertEquals(pyRequirement("MOCPy", EQ, "0.1.0.dev0"), fromLine("MOCPy==0.1.0.dev0"));
    assertEquals(pyRequirement("score.webassets", EQ, "0.2.3"), fromLine("score.webassets==0.2.3"));
    assertEquals(pyRequirement("pip_helpers", EQ, "0.5.post6"), fromLine("pip_helpers==0.5.post6"));
    assertEquals(pyRequirement("Django", EQ, "1.9rc1"), fromLine("Django==1.9rc1"));
    assertEquals(pyRequirement("django", EQ, "1!1"), fromLine("django==1!1"));
    assertEquals(pyRequirement("pinax-utils", EQ, "1.0b1.dev3"), fromLine("pinax-utils==1.0b1.dev3"));
    assertEquals(pyRequirement("Flask-Celery-py3", EQ, "0.1.*"), fromLine("Flask-Celery-py3==0.1.*"));
    assertEquals(pyRequirement("no_limit_nester", EQ, "1.0+local.version.10"), fromLine("no_limit_nester==1.0+local.version.10"));
  }

  public void testRequirementVersionWithBraces() {
    assertEquals(pyRequirement("Orange-Bioinformatics", EQ, "2.5a20"), fromLine("Orange-Bioinformatics (==2.5a20)"));
    assertEquals(pyRequirement("MOCPy", EQ, "0.1.0.dev0"), fromLine("MOCPy (==0.1.0.dev0)"));
    assertEquals(pyRequirement("score.webassets", EQ, "0.2.3"), fromLine("score.webassets (==0.2.3)"));
    assertEquals(pyRequirement("pip_helpers", EQ, "0.5.post6"), fromLine("pip_helpers (==0.5.post6)"));
    assertEquals(pyRequirement("Django", EQ, "1.9rc1"), fromLine("Django (==1.9rc1)"));
    assertEquals(pyRequirement("django", EQ, "1!1"), fromLine("django (==1!1)"));
    assertEquals(pyRequirement("pinax-utils", EQ, "1.0b1.dev3"), fromLine("pinax-utils (==1.0b1.dev3)"));
    assertEquals(pyRequirement("Flask-Celery-py3", EQ, "0.1.*"), fromLine("Flask-Celery-py3 (==0.1.*)"));
    assertEquals(pyRequirement("no_limit_nester", EQ, "1.0+local.version.10"), fromLine("no_limit_nester (==1.0+local.version.10)"));
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternatePreReleaseVersion() {
    doRequirementVersionNormalizationTest("1.9rc1", "1.9RC1");

    doRequirementVersionNormalizationTest("2.5a20", "2.5.a20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5.a.20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5-a20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5-a_20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5_a20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5_a-20");

    doRequirementVersionNormalizationTest("2.5a20", "2.5alpha20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5.alpha20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5.alpha.20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5-alpha20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5-alpha_20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5_alpha20");
    doRequirementVersionNormalizationTest("2.5a20", "2.5_alpha-20");

    doRequirementVersionNormalizationTest("2.5b20", "2.5beta20");
    doRequirementVersionNormalizationTest("2.5b20", "2.5.beta20");
    doRequirementVersionNormalizationTest("2.5b20", "2.5.beta.20");
    doRequirementVersionNormalizationTest("2.5b20", "2.5-beta20");
    doRequirementVersionNormalizationTest("2.5b20", "2.5-beta_20");
    doRequirementVersionNormalizationTest("2.5b20", "2.5_beta20");
    doRequirementVersionNormalizationTest("2.5b20", "2.5_beta-20");

    doRequirementVersionNormalizationTest("2.5rc20", "2.5c20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5.c20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5.c.20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5-c20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5-c_20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5_c20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5_c-20");

    doRequirementVersionNormalizationTest("2.5rc20", "2.5pre20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5.pre20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5.pre.20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5-pre20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5-pre_20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5_pre20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5_pre-20");

    doRequirementVersionNormalizationTest("2.5rc20", "2.5preview20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5.preview20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5.preview.20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5-preview20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5-preview_20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5_preview20");
    doRequirementVersionNormalizationTest("2.5rc20", "2.5_preview-20");

    doRequirementVersionNormalizationTest("2.5a0", "2.5a");
    doRequirementVersionNormalizationTest("2.5a0", "2.5.a");
    doRequirementVersionNormalizationTest("2.5a0", "2.5-a");
    doRequirementVersionNormalizationTest("2.5a0", "2.5_a");
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternatePostReleaseVersion() {
    doRequirementVersionNormalizationTest("2.5.post20", "2.5-post20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5-post.20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5_post20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5_post_20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5post20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5post-20");

    doRequirementVersionNormalizationTest("2.5.post20", "2.5.r20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5-r20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5-r.20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5_r20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5_r_20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5r20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5r-20");

    doRequirementVersionNormalizationTest("2.5.post20", "2.5.rev20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5-rev20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5-rev.20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5_rev20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5_rev_20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5rev20");
    doRequirementVersionNormalizationTest("2.5.post20", "2.5rev-20");

    doRequirementVersionNormalizationTest("2.5.post0", "2.5.post");
    doRequirementVersionNormalizationTest("2.5.post0", "2.5-post");
    doRequirementVersionNormalizationTest("2.5.post0", "2.5_post");
    doRequirementVersionNormalizationTest("2.5.post0", "2.5post");

    doRequirementVersionNormalizationTest("2.5.post20", "2.5-20");
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternateDevelopmentVersion() {
    doRequirementVersionNormalizationTest("2.5.dev20", "2.5-dev20");
    doRequirementVersionNormalizationTest("2.5.dev20", "2.5_dev20");
    doRequirementVersionNormalizationTest("2.5.dev20", "2.5dev20");

    doRequirementVersionNormalizationTest("2.5.dev0", "2.5-dev");
    doRequirementVersionNormalizationTest("2.5.dev0", "2.5_dev");
    doRequirementVersionNormalizationTest("2.5.dev0", "2.5dev");
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternateLocalVersion() {
    doRequirementVersionNormalizationTest("2.5+local.version", "2.5+local-version");
    doRequirementVersionNormalizationTest("2.5+local.version", "2.5+local_version");
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternateVersionStart() {
    doRequirementVersionNormalizationTest("2.5a20", "v2.5a20");
    doRequirementVersionNormalizationTest("0.1.0.dev0", "v0.1.0.dev0");
    doRequirementVersionNormalizationTest("0.2.3", "v0.2.3");
    doRequirementVersionNormalizationTest("0.5.post6", "v0.5.post6");
    doRequirementVersionNormalizationTest("1.9rc1", "v1.9rc1");
    doRequirementVersionNormalizationTest("1!1", "v1!1");
    doRequirementVersionNormalizationTest("1.0b1.dev3", "v1.0b1.dev3");
    doRequirementVersionNormalizationTest("1.0+local.version.10", "v1.0+local.version.10");
    doRequirementVersionNormalizationTest("0.1.*", "v0.1.*");
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternateVersionNumber() {
    doRequirementVersionNormalizationTest("900", "0900");
    doRequirementVersionNormalizationTest("201607251407", "0201607251407");
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternateLocalVersionNumber() {
    doRequirementVersionNormalizationTest("1.0+foo0100", "1.0+foo0100");
  }

  // PY-20223
  public void testRequirementVersionWithBigInteger() {
    assertEquals(pyRequirement("pkg-name", EQ, "3.4.201607251407"), fromLine("pkg-name==3.4.201607251407"));
  }

  // PY-11835
  public void testRequirementNotNormalizableVersion() {
    final String name = "django_compressor";
    final String version = "dev";
    final String line = name + "==" + version;
    final List<PyRequirementVersionSpec> versionSpecs = singletonList(
      pyRequirementVersionSpec(STR_EQ, version));

    assertEquals(new PyRequirementImpl(name, versionSpecs, singletonList(line), "", null), fromLine(line));
  }

  // https://www.python.org/dev/peps/pep-0440/#version-specifiers
  public void testRequirementRelation() {
    doRequirementRelationTest(LT, release("1.4"));
    doRequirementRelationTest(LTE, release("1.4"));
    doRequirementRelationTest(NE, release("1.4"));
    doRequirementRelationTest(EQ, release("1.4"));
    doRequirementRelationTest(GT, release("1.4"));
    doRequirementRelationTest(GTE, release("1.4"));
    doRequirementRelationTest(COMPATIBLE, release("1.*"));

    assertEquals(pyRequirement("name", STR_EQ, "version"), fromLine("name===version"));

    doRequirementRelationTest(Arrays.asList(GTE, EQ), Arrays.asList(release("2.8.1"), release("2.8.*")));
    doRequirementRelationTest(Arrays.asList(LT, GTE), Arrays.asList(release("1.4"), release("1.3.1")));

    doRequirementRelationTest(Arrays.asList(LT, GT, NE, LT, EQ),
                              Arrays.asList(release("1.6"),
                                            release("1.9"),
                                            release("1.9.6"),
                                            new PyPackageVersion(null, "2.0", "a0", null, null, null),
                                            new PyPackageVersion(null, "2.4", "rc1", null, null, null)));

    // PY-14583
    doRequirementRelationTest(Arrays.asList(GTE, LTE, GTE, LTE),
                              Arrays.asList(release("0.8.4"), release("0.8.99"), release("0.9.7"), release("0.9.99")));
  }

  // https://www.python.org/dev/peps/pep-0508/#extras
  // PY-15674
  public void testRequirementExtras() {
    final String name = "MyProject1";
    final List<PyRequirementRelation> relations = emptyList();
    final List<PyPackageVersion> versions = emptyList();

    doRequirementRelationTest(name, "[PDF]", relations, versions);
    doRequirementRelationTest(name, " [extra1, extra2]", relations, versions);
    doRequirementRelationTest(name, "[security,tests]", relations, versions);
  }

  // https://www.python.org/dev/peps/pep-0508/#extras
  // PY-15674
  public void testRequirementExtrasAndRelation() {
    final String extras1 = "[PDF]";
    final String name1 = "MyPackage";

    final String extras2 = " [foo, bar]";
    final String name2 = "Fizzy";

    final String extras3 = " [security,tests]";
    final String name3 = "requests";

    doRequirementRelationTest(name1, extras1, LT, release("1.4"));
    doRequirementRelationTest(name2, extras2, LTE, release("1.4"));
    doRequirementRelationTest(name3, extras3, NE, release("1.4"));
    doRequirementRelationTest(name1, extras1, EQ, release("1.4"));
    doRequirementRelationTest(name2, extras2, GT, release("1.4"));
    doRequirementRelationTest(name3, extras3, GTE, release("1.4"));
    doRequirementRelationTest(name1, extras1, COMPATIBLE, release("1.*"));

    final String line1 = name2 + extras2 + STR_EQ.getPresentableText() + "version";
    assertEquals(
      new PyRequirementImpl(
        name2,
        singletonList(pyRequirementVersionSpec(STR_EQ, "version")),
        singletonList(line1),
        StringUtil.trimLeading(extras2),
        null
      ),
      fromLine(line1)
    );

    doRequirementRelationTest(name3, extras3, Arrays.asList(GTE, EQ), Arrays.asList(release("2.8.1"), release("2.8.*")));
    doRequirementRelationTest(name1, extras1, Arrays.asList(LT, GTE), Arrays.asList(release("1.4"), release("1.3.1")));

    doRequirementRelationTest(name2,
                              extras2,
                              Arrays.asList(LT, GT, NE, LT, EQ),
                              Arrays.asList(release("1.6"),
                                            release("1.9"),
                                            release("1.9.6"),
                                            new PyPackageVersion(null, "2.0", "a0", null, null, null),
                                            new PyPackageVersion(null, "2.4", "rc1", null, null, null)));

    // PY-14583
    doRequirementRelationTest(name3,
                              extras3,
                              Arrays.asList(GTE, LTE, GTE, LTE),
                              Arrays.asList(release("0.8.4"), release("0.8.99"), release("0.9.7"), release("0.9.99")));
  }

  // https://pip.pypa.io/en/stable/reference/pip_install/#per-requirement-overrides
  public void testRequirementOptions() {
    final String name = "MyProject1";
    final String version = "1.2";
    final String linePrefix = name + " >= " + version;

    final List<PyRequirementVersionSpec> versionSpecs = singletonList(
      pyRequirementVersionSpec(GTE, release(version)));

    final List<String> installOptions1 = Arrays.asList(linePrefix,
                                                       "--global-option=\"--no-user-cfg\"",
                                                       "--install-option=\"--prefix='/usr/local'\"",
                                                       "--install-option=\"--no-compile\"");
    final String line1 = linePrefix + " " +
                         "--global-option=\"--no-user-cfg\" " +
                         "--install-option=\"--prefix='/usr/local'\" " +
                         "--install-option=\"--no-compile\"";
    assertEquals(new PyRequirementImpl(name, versionSpecs, installOptions1, "", null), fromLine(line1));

    final List<String> installOptions2 = Arrays.asList(linePrefix, "--install-option=\"--install-scripts=/usr/local/bin\"");
    final String line2 = linePrefix + " --install-option=\"--install-scripts=/usr/local/bin\"";
    assertEquals(new PyRequirementImpl(name, versionSpecs, installOptions2, "", null), fromLine(line2));
  }

  public void testMultilineRequirement() {
    final String name = "MyProject1";
    final String version = "1.2";
    final String textPrefix = name + " >= " + version;

    final List<PyRequirementVersionSpec> versionSpecs = singletonList(
      pyRequirementVersionSpec(GTE, release(version)));

    final String text = textPrefix + " " +
                        "--global-option=\"--no-user-cfg\" \\\n" +
                        "--install-option=\"--prefix='/usr/local'\" \\\n" +
                        "--install-option=\"--no-compile\"";

    final List<String> installOptions = Arrays.asList(textPrefix,
                                                      "--global-option=\"--no-user-cfg\"",
                                                      "--install-option=\"--prefix='/usr/local'\"",
                                                      "--install-option=\"--no-compile\"");

    assertEquals(singletonList(new PyRequirementImpl(name, versionSpecs, installOptions, "", null)), PyRequirementParser.fromText(text));
  }

  // PY-6355
  public void testTrailingZeroesInVersion() {
    final PyRequirement req = fromLine("foo==0.8.0");
    final PyPackage pkg = new PyPackage("foo", "0.8");
    assertNotNull(req);
    assertEquals(pkg, req.match(singletonList(pkg)));
  }

  // PY-20242
  public void testVersionInterpretedAsString() {
    final PyRequirement req = fromLine("foo===version");
    final PyPackage pkg = new PyPackage("foo", "version");
    assertNotNull(req);
    assertEquals(pkg, req.match(singletonList(pkg)));
  }

  // PY-20880
  public void testMatchingLocalVersions() {
    final PyPackage firstPackageWithLocalVersion = new PyPackage("foo", "1.0+foo0100");
    final PyPackage secondPackageWithLocalVersion = new PyPackage("foo", "1.0+foo0101");

    final PyRequirement requirement = fromLine("foo==1.0");
    assertEquals(firstPackageWithLocalVersion, requirement.match(singletonList(firstPackageWithLocalVersion)));
    assertEquals(secondPackageWithLocalVersion, requirement.match(singletonList(secondPackageWithLocalVersion)));

    final PyRequirement requirementWithLocalVersion = fromLine("foo==1.0+foo0100");
    assertEquals(firstPackageWithLocalVersion, requirementWithLocalVersion.match(singletonList(firstPackageWithLocalVersion)));
    assertNull(requirementWithLocalVersion.match(singletonList(secondPackageWithLocalVersion)));
  }

  // https://www.python.org/dev/peps/pep-0440/#version-matching
  // PY-22275
  public void testMatchingStar() {
    final PyRequirement requirement = fromLine("foo==1.1.*");
    final PyPackage release = new PyPackage("foo", "1.1.2");
    final PyPackage pre = new PyPackage("foo", "1.1.2a1");
    final PyPackage post = new PyPackage("foo", "1.1.2.post1");
    final PyPackage dev = new PyPackage("foo", "1.1.2.dev1");
    final PyPackage localVersion = new PyPackage("foo", "1.1.2+local.version");

    assertEquals(release, requirement.match(singletonList(release)));
    assertEquals(pre, requirement.match(singletonList(pre)));
    assertEquals(post, requirement.match(singletonList(post)));
    assertEquals(dev, requirement.match(singletonList(dev)));
    assertEquals(localVersion, requirement.match(singletonList(localVersion)));

    final PyRequirement negativeRequirement = fromLine("foo!=1.1.*");
    final PyPackage negativeRelease = new PyPackage("foo", "1.2.2");
    final PyPackage negativePre = new PyPackage("foo", "1.2.2a1");
    final PyPackage negativePost = new PyPackage("foo", "1.2.2.post1");
    final PyPackage negativeDev = new PyPackage("foo", "1.2.2.dev1");
    final PyPackage negativeLocalVersion = new PyPackage("foo", "1.2.2+local.version");

    assertNull(negativeRequirement.match(Arrays.asList(release, pre, post, dev, localVersion)));
    assertEquals(negativeRelease, negativeRequirement.match(singletonList(negativeRelease)));
    assertEquals(negativePre, negativeRequirement.match(singletonList(negativePre)));
    assertEquals(negativePost, negativeRequirement.match(singletonList(negativePost)));
    assertEquals(negativeDev, negativeRequirement.match(singletonList(negativeDev)));
    assertEquals(negativeLocalVersion, negativeRequirement.match(singletonList(negativeLocalVersion)));
  }

  // https://www.python.org/dev/peps/pep-0440/#compatible-release
  // PY-20522
  public void testMatchingCompatible() {
    final PyRequirement requirement = fromLine("foo~=2.2");
    final PyPackage release = new PyPackage("foo", "2.3");
    final PyPackage pre = new PyPackage("foo", "2.3a1");
    final PyPackage post = new PyPackage("foo", "2.3.post1");
    final PyPackage dev = new PyPackage("foo", "2.3.dev1");
    final PyPackage localVersion = new PyPackage("foo", "2.3+local.version");

    assertEquals(release, requirement.match(singletonList(release)));
    assertEquals(pre, requirement.match(singletonList(pre)));
    assertEquals(post, requirement.match(singletonList(post)));
    assertEquals(dev, requirement.match(singletonList(dev)));
    assertEquals(localVersion, requirement.match(singletonList(localVersion)));

    final PyRequirement moreModernRequirement = fromLine("foo~=2.4");
    assertNull(moreModernRequirement.match(Arrays.asList(release, pre, post, dev, localVersion)));
  }

  // https://www.python.org/dev/peps/pep-0440/#compatible-release
  // PY-20522
  public void testMatchingCompatibleWithTrailingZero() {
    final PyRequirement requirement = fromLine("foo~=2.20.0");
    final PyPackage release = new PyPackage("foo", "2.20.3");
    final PyPackage pre = new PyPackage("foo", "2.20.3a1");
    final PyPackage post = new PyPackage("foo", "2.20.3.post1");
    final PyPackage dev = new PyPackage("foo", "2.20.3.dev1");
    final PyPackage localVersion = new PyPackage("foo", "2.20.3+local.version");

    assertEquals(release, requirement.match(singletonList(release)));
    assertEquals(pre, requirement.match(singletonList(pre)));
    assertEquals(post, requirement.match(singletonList(post)));
    assertEquals(dev, requirement.match(singletonList(dev)));
    assertEquals(localVersion, requirement.match(singletonList(localVersion)));

    final PyRequirement moreModernRequirement = fromLine("foo~=2.21.0");
    assertNull(moreModernRequirement.match(Arrays.asList(release, pre, post, dev, localVersion)));
  }

  // PY-27076
  public void testMatchingAsteriskAndCompatibleWithTwoTrailingZeros() {
    final PyRequirement requirement1 = fromLine("social-auth-app-django==2.0.*");
    final PyRequirement requirement2 = fromLine("social-auth-app-django~=2.0.0");

    final PyPackage pkg = new PyPackage("social-auth-app-django", "2.0.0");

    assertEquals(pkg, requirement1.match(singletonList(pkg)));
    assertEquals(pkg, requirement2.match(singletonList(pkg)));
  }

  // OPTIONS
  public void testOptions() {
    assertEmpty(
      PyRequirementParser.fromText(
        """
          -i URL
          --index-url URL
          --extra-index-url URL
          --no-index
          -f URL
          --find-links URL
          --no-binary SMTH
          --only-binary SMTH
          --require-hashes"""
      )
    );
  }

  // RECURSIVE REQUIREMENTS
  // PY-7011
  // PY-18543
  public void testRecursiveRequirements() {
    final VirtualFile requirementsFile = getVirtualFileByName(getTestDataPath() + "/requirement/recursive/requirements.txt");
    assertNotNull(requirementsFile);

    assertEquals(Arrays.asList(pyRequirement("bitly_api", null),
                               pyRequirement("numpy", null),
                               pyRequirement("SomeProject", null)),
                 PyRequirementParser.fromFile(requirementsFile));
  }

  // COMMENTS
  public void testComment() {
    assertNull(fromLine("# comment"));
  }

  public void testCommentAtTheEnd() {
    final String name = "MyProject1";
    final String version = "2.5a20";

    doCommentAtTheEndTest(name + " # comment");
    doCommentAtTheEndTest(name, version, name + "==" + version + " # comment");

    assertEquals(new PyRequirementImpl(name, emptyList(), singletonList(name + "[PDF]"), "[PDF]", null),
                 fromLine(name + "[PDF] # comment"));

    final PyRequirement requirement = new PyRequirementImpl(name, emptyList(), Arrays.asList(name, "--install-option=\"option\""), "", null);

    assertEquals(requirement, fromLine(name + " --install-option=\"option\" # comment"));
    assertEquals(singletonList(requirement), PyRequirementParser.fromText(name + " \\\n--install-option=\"option\" # comment"));
  }

  // HASH OPTIONS
  // Test for parsing requirements with hash options
  public void testRequirementWithHash() {
    doTest("certifi", "2018.4.16", "certifi==2018.4.16 --hash=sha256:13e698f54293db9f89122b0581843a782ad0934a4fe0172d2a980ba77fc61bb7");
    doTest("certifi", "2018.4.16",
           "certifi==2018.4.16 --hash=sha256:13e698f54293db9f89122b0581843a782ad0934a4fe0172d2a980ba77fc61bb7 --hash=sha256:9fa520c1bacfb634fa7af20a76bcbd3d5fb390481724c597da32c719a7dca4b0");
  }

  // Test for parsing requirements with hash options from text (including line continuation)
  public void testRequirementWithHashFromText() {
    final List<PyRequirement> requirements = PyRequirementParser.fromText(
      "certifi==2018.4.16 \\\n    --hash=sha256:13e698f54293db9f89122b0581843a782ad0934a4fe0172d2a980ba77fc61bb7 \\\n    --hash=sha256:9fa520c1bacfb634fa7af20a76bcbd3d5fb390481724c597da32c719a7dca4b0");
    assertFalse(requirements.isEmpty());

    final PyRequirement requirement = requirements.get(0);
    assertNotNull(requirement);
    assertEquals("certifi", requirement.getName());
    assertEquals("2018.4.16", requirement.getVersionSpecs().get(0).getVersion());

    final List<String> installOptions = requirement.getInstallOptions();
    assertTrue(installOptions.contains("--hash=sha256:13e698f54293db9f89122b0581843a782ad0934a4fe0172d2a980ba77fc61bb7"));
    assertTrue(installOptions.contains("--hash=sha256:9fa520c1bacfb634fa7af20a76bcbd3d5fb390481724c597da32c719a7dca4b0"));
  }

  // ENV MARKERS
  // TODO: https://www.python.org/dev/peps/pep-0426/#environment-markers, https://www.python.org/dev/peps/pep-0508/#environment-markers

  private static void doTest(@NotNull String line) {
    assertEquals(new PyRequirementImpl("MyProject1", emptyList(), Arrays.asList(line.split("\\s+")), "", null), fromLine(line));
  }

  private static void doTest(@NotNull String name, @NotNull String version, @NotNull String line) {
    final PyRequirementVersionSpec versionSpec = pyRequirementVersionSpec(EQ, version);
    assertEquals(new PyRequirementImpl(name, singletonList(versionSpec), singletonList(line), "", null), fromLine(line));
  }

  private static void doRequirementVersionNormalizationTest(@NotNull String expectedVersion, @NotNull String actualVersion) {
    final String name = "name";
    doTest(name, expectedVersion, name + "==" + actualVersion);
  }

  private static void doCommentAtTheEndTest(@NotNull String line) {
    doTest(line.substring(0, line.lastIndexOf('#') - 1));
  }

  private static void doCommentAtTheEndTest(@NotNull String name, @NotNull String version, @NotNull String line) {
    doTest(name, version, line.substring(0, line.lastIndexOf('#') - 1));
  }

  private static void doRequirementRelationTest(@NotNull PyRequirementRelation relation, @NotNull PyPackageVersion version) {
    doRequirementRelationTest("Django", null, singletonList(relation), singletonList(version));
  }

  private static void doRequirementRelationTest(@NotNull List<PyRequirementRelation> relations, @NotNull List<PyPackageVersion> versions) {
    doRequirementRelationTest("Django", null, relations, versions);
  }

  private static void doRequirementRelationTest(@NotNull String name,
                                                @Nullable String extras,
                                                @NotNull PyRequirementRelation relation,
                                                @NotNull PyPackageVersion version) {
    doRequirementRelationTest(name, extras, singletonList(relation), singletonList(version));
  }

  private static void doRequirementRelationTest(@NotNull String name,
                                                @Nullable String extras,
                                                @NotNull List<PyRequirementRelation> relations,
                                                @NotNull List<PyPackageVersion> versions) {
    assertEquals(versions.size(), relations.size());

    final StringBuilder sb = new StringBuilder(name);
    final List<PyRequirementVersionSpec> expectedVersionSpecs = new ArrayList<>();

    if (extras != null) sb.append(extras);

    for (Pair<PyRequirementRelation, PyPackageVersion> pair : ContainerUtil.zip(relations, versions)) {
      final PyRequirementRelation relation = pair.getFirst();
      final PyPackageVersion version = pair.getSecond();

      expectedVersionSpecs.add(pyRequirementVersionSpec(relation, version));
    }

    sb.append(StringUtil.join(expectedVersionSpecs, PyRequirementVersionSpec::getPresentableText, ","));

    final String options = sb.toString();

    if (extras == null) {
      assertEquals(new PyRequirementImpl(name, expectedVersionSpecs, singletonList(options), "", null), fromLine(options));
    }
    else {
      assertEquals(new PyRequirementImpl(name, expectedVersionSpecs, singletonList(options), StringUtil.trimLeading(extras), null),
                   fromLine(options));
    }
  }

  @NotNull
  private static PyPackageVersion release(@NotNull String version) {
    return new PyPackageVersion(null, version, null, null, null, null);
  }
}
