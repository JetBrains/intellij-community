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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyRequirementTest extends PyTestCase {

  // ARCHIVE URL
  public void testArchiveUrl() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithMd5() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#md5=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithSha1() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha1=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithSha224() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha224=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithSha256() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha256=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithSha384() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha384=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithSha512() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha512=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url), PyRequirement.fromString(url));
  }

  // PY-18054
  public void testGithubArchiveUrl() {
    doTest("https://github.com/divio/MyProject1/archive/master.zip?1450634746.0107164");
  }

  // VCS
  // TODO: subdirectory
  // TODO: editable with --src
  // PY-6328
  public void testGit() {
    doTest("git://git.myproject.org/MyProject#egg=MyProject1");
    doTest("git://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("git://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("git://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("git+git://git.myproject.org/MyProject#egg=MyProject1");
    doTest("git+git://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("git+git://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("git+git://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("git+https://git.myproject.org/MyProject#egg=MyProject1");
    doTest("git+https://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("git+https://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("git+https://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("git+ssh://git.myproject.org/MyProject#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("git+ssh://user@git.myproject.org/MyProject#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/MyProject/#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("git+user@git.myproject.org:MyProject#egg=MyProject1");
    doTest("git+user@git.myproject.org:MyProject/#egg=MyProject1");
    doTest("git+user@git.myproject.org:MyProject.git#egg=MyProject1");
    doTest("git+user@git.myproject.org:MyProject.git/#egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject#egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject/#egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject.git#egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject.git/#egg=MyProject1");
  }

  public void testEditableGit() {
    doTest("-e git://git.myproject.org/MyProject#egg=MyProject1");
    doTest("-e git://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e git://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("-e git://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("-e git://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("-e git://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("-e git://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("-e git://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("-e git+git://git.myproject.org/MyProject#egg=MyProject1");
    doTest("-e git+git://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e git+git://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("-e git+git://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("-e git+git://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("-e git+git://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("-e git+git://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("-e git+git://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("-e git+https://git.myproject.org/MyProject#egg=MyProject1");
    doTest("-e git+https://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e git+https://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("-e git+https://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("-e git+https://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("-e git+https://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("-e git+https://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("-e git+https://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("-e git+ssh://git.myproject.org/MyProject#egg=MyProject1");
    doTest("-e git+ssh://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e git+ssh://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("-e git+ssh://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("-e git+ssh://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("-e git+ssh://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("-e git+ssh://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("-e git+ssh://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("-e git+ssh://user@git.myproject.org/MyProject#egg=MyProject1");
    doTest("-e git+ssh://user@git.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e git+ssh://user@git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("-e git+ssh://user@git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("-e git+user@git.myproject.org:MyProject#egg=MyProject1");
    doTest("-e git+user@git.myproject.org:MyProject/#egg=MyProject1");
    doTest("-e git+user@git.myproject.org:MyProject.git#egg=MyProject1");
    doTest("-e git+user@git.myproject.org:MyProject.git/#egg=MyProject1");
    doTest("-e git+user@git.myproject.org:/path/MyProject#egg=MyProject1");
    doTest("-e git+user@git.myproject.org:/path/MyProject/#egg=MyProject1");
    doTest("-e git+user@git.myproject.org:/path/MyProject.git#egg=MyProject1");
    doTest("-e git+user@git.myproject.org:/path/MyProject.git/#egg=MyProject1");

    doTest("--editable git://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable git://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable git://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--editable git://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--editable git://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--editable git://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--editable git://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--editable git://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--editable git+git://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable git+git://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable git+git://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--editable git+git://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--editable git+git://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--editable git+git://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--editable git+git://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--editable git+git://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--editable git+https://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable git+https://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable git+https://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--editable git+https://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--editable git+https://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--editable git+https://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--editable git+https://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--editable git+https://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--editable git+ssh://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable git+ssh://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable git+ssh://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--editable git+ssh://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--editable git+ssh://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--editable git+ssh://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--editable git+ssh://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--editable git+ssh://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--editable git+ssh://user@git.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable git+ssh://user@git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable git+ssh://user@git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--editable git+ssh://user@git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--editable git+user@git.myproject.org:MyProject#egg=MyProject1");
    doTest("--editable git+user@git.myproject.org:MyProject/#egg=MyProject1");
    doTest("--editable git+user@git.myproject.org:MyProject.git#egg=MyProject1");
    doTest("--editable git+user@git.myproject.org:MyProject.git/#egg=MyProject1");
    doTest("--editable git+user@git.myproject.org:/path/MyProject#egg=MyProject1");
    doTest("--editable git+user@git.myproject.org:/path/MyProject/#egg=MyProject1");
    doTest("--editable git+user@git.myproject.org:/path/MyProject.git#egg=MyProject1");
    doTest("--editable git+user@git.myproject.org:/path/MyProject.git/#egg=MyProject1");
  }

  // PY-8623
  public void testGitWithRevision() {
    doTest("git://git.myproject.org/MyProject@special-feature#egg=MyProject1");
    doTest("git://git.myproject.org/MyProject/@special-feature#egg=MyProject1");
    doTest("git://git.myproject.org/MyProject.git@master#egg=MyProject1");
    doTest("git://git.myproject.org/MyProject.git/@master#egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject/@da39a3ee5e6b#egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject.git@v1.0#egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject.git/@stable/1.5.x#egg=MyProject1");

    doTest("git+git://git.myproject.org/MyProject@special-feature#egg=MyProject1");
    doTest("git+git://git.myproject.org/MyProject/@special-feature#egg=MyProject1");
    doTest("git+git://git.myproject.org/MyProject.git@master#egg=MyProject1");
    doTest("git+git://git.myproject.org/MyProject.git/@master#egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject/@da39a3ee5e6b#egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject.git@v1.0#egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject.git/@stable/1.5.x#egg=MyProject1");

    doTest("git+https://git.myproject.org/MyProject@special-feature#egg=MyProject1");
    doTest("git+https://git.myproject.org/MyProject/@special-feature#egg=MyProject1");
    doTest("git+https://git.myproject.org/MyProject.git@master#egg=MyProject1");
    doTest("git+https://git.myproject.org/MyProject.git/@master#egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject/@da39a3ee5e6b#egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject.git@v1.0#egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject.git/@stable/1.5.x#egg=MyProject1");

    doTest("git+ssh://git.myproject.org/MyProject@special-feature#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/MyProject/@special-feature#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/MyProject.git@master#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/MyProject.git/@master#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject/@da39a3ee5e6b#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject.git@v1.0#egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject.git/@stable/1.5.x#egg=MyProject1");

    doTest("git+ssh://user@git.myproject.org/MyProject@special-feature#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/MyProject/@special-feature#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/MyProject.git@master#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/MyProject.git/@master#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject/@da39a3ee5e6b#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject.git@v1.0#egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject.git/@stable/1.5.x#egg=MyProject1");

    doTest("git+user@git.myproject.org:MyProject@special-feature#egg=MyProject1");
    doTest("git+user@git.myproject.org:MyProject/@special-feature#egg=MyProject1");
    doTest("git+user@git.myproject.org:MyProject.git@master#egg=MyProject1");
    doTest("git+user@git.myproject.org:MyProject.git/@master#egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject/@da39a3ee5e6b#egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject.git@v1.0#egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject.git/@stable/1.5.x#egg=MyProject1");
  }

  // PY-7583
  public void testGitWithoutEgg() {
    doTest("-e git://git.myproject.org/MyProject1");
    doTest("-e git://git.myproject.org/MyProject1/");
    doTest("-e git://git.myproject.org/MyProject1.git");
    doTest("-e git://git.myproject.org/MyProject1.git/");
    doTest("-e git://git.myproject.org/path/MyProject1");
    doTest("-e git://git.myproject.org/path/MyProject1/");
    doTest("-e git://git.myproject.org/path/MyProject1.git");
    doTest("-e git://git.myproject.org/path/MyProject1.git/");

    doTest("-e git+git://git.myproject.org/MyProject1");
    doTest("-e git+git://git.myproject.org/MyProject1/");
    doTest("-e git+git://git.myproject.org/MyProject1.git");
    doTest("-e git+git://git.myproject.org/MyProject1.git/");
    doTest("-e git+git://git.myproject.org/path/MyProject1");
    doTest("-e git+git://git.myproject.org/path/MyProject1/");
    doTest("-e git+git://git.myproject.org/path/MyProject1.git");
    doTest("-e git+git://git.myproject.org/path/MyProject1.git/");

    doTest("-e git+https://git.myproject.org/MyProject1");
    doTest("-e git+https://git.myproject.org/MyProject1/");
    doTest("-e git+https://git.myproject.org/MyProject1.git");
    doTest("-e git+https://git.myproject.org/MyProject1.git/");
    doTest("-e git+https://git.myproject.org/path/MyProject1");
    doTest("-e git+https://git.myproject.org/path/MyProject1/");
    doTest("-e git+https://git.myproject.org/path/MyProject1.git");
    doTest("-e git+https://git.myproject.org/path/MyProject1.git/");

    doTest("-e git+ssh://git.myproject.org/MyProject1");
    doTest("-e git+ssh://git.myproject.org/MyProject1/");
    doTest("-e git+ssh://git.myproject.org/MyProject1.git");
    doTest("-e git+ssh://git.myproject.org/MyProject1.git/");
    doTest("-e git+ssh://git.myproject.org/path/MyProject1");
    doTest("-e git+ssh://git.myproject.org/path/MyProject1/");
    doTest("-e git+ssh://git.myproject.org/path/MyProject1.git");
    doTest("-e git+ssh://git.myproject.org/path/MyProject1.git/");

    doTest("-e git+ssh://user@git.myproject.org/MyProject1");
    doTest("-e git+ssh://user@git.myproject.org/MyProject1/");
    doTest("-e git+ssh://user@git.myproject.org/MyProject1.git");
    doTest("-e git+ssh://user@git.myproject.org/MyProject1.git/");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject1");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject1/");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject1.git");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject1.git/");

    doTest("-e git+user@git.myproject.org:MyProject1");
    doTest("-e git+user@git.myproject.org:MyProject1/");
    doTest("-e git+user@git.myproject.org:MyProject1.git");
    doTest("-e git+user@git.myproject.org:MyProject1.git/");
    doTest("-e git+user@git.myproject.org:/path/MyProject1");
    doTest("-e git+user@git.myproject.org:/path/MyProject1/");
    doTest("-e git+user@git.myproject.org:/path/MyProject1.git");
    doTest("-e git+user@git.myproject.org:/path/MyProject1.git/");
  }

  // PY-7583
  // PY-8623
  public void testGitWithRevisionAndWithoutEgg() {
    doTest("git://git.myproject.org/MyProject1@special-feature");
    doTest("git://git.myproject.org/MyProject1/@special-feature");
    doTest("git://git.myproject.org/MyProject1.git@master");
    doTest("git://git.myproject.org/MyProject1.git/@master");
    doTest("git://git.myproject.org/path/MyProject1@da39a3ee5e6b");
    doTest("git://git.myproject.org/path/MyProject1/@da39a3ee5e6b");
    doTest("git://git.myproject.org/path/MyProject1.git@v1.0");
    doTest("git://git.myproject.org/path/MyProject1.git/@stable/1.5.x");

    doTest("git+git://git.myproject.org/MyProject1@special-feature");
    doTest("git+git://git.myproject.org/MyProject1/@special-feature");
    doTest("git+git://git.myproject.org/MyProject1.git@master");
    doTest("git+git://git.myproject.org/MyProject1.git/@master");
    doTest("git+git://git.myproject.org/path/MyProject1@da39a3ee5e6b");
    doTest("git+git://git.myproject.org/path/MyProject1/@da39a3ee5e6b");
    doTest("git+git://git.myproject.org/path/MyProject1.git@v1.0");
    doTest("git+git://git.myproject.org/path/MyProject1.git/@stable/1.5.x");

    doTest("git+https://git.myproject.org/MyProject1@special-feature");
    doTest("git+https://git.myproject.org/MyProject1/@special-feature");
    doTest("git+https://git.myproject.org/MyProject1.git@master");
    doTest("git+https://git.myproject.org/MyProject1.git/@master");
    doTest("git+https://git.myproject.org/path/MyProject1@da39a3ee5e6b");
    doTest("git+https://git.myproject.org/path/MyProject1/@da39a3ee5e6b");
    doTest("git+https://git.myproject.org/path/MyProject1.git@v1.0");
    doTest("git+https://git.myproject.org/path/MyProject1.git/@stable/1.5.x");

    doTest("git+ssh://git.myproject.org/MyProject1@special-feature");
    doTest("git+ssh://git.myproject.org/MyProject1/@special-feature");
    doTest("git+ssh://git.myproject.org/MyProject1.git@master");
    doTest("git+ssh://git.myproject.org/MyProject1.git/@master");
    doTest("git+ssh://git.myproject.org/path/MyProject1@da39a3ee5e6b");
    doTest("git+ssh://git.myproject.org/path/MyProject1/@da39a3ee5e6b");
    doTest("git+ssh://git.myproject.org/path/MyProject1.git@v1.0");
    doTest("git+ssh://git.myproject.org/path/MyProject1.git/@stable/1.5.x");

    doTest("git+ssh://user@git.myproject.org/MyProject1@special-feature");
    doTest("git+ssh://user@git.myproject.org/MyProject1/@special-feature");
    doTest("git+ssh://user@git.myproject.org/MyProject1.git@master");
    doTest("git+ssh://user@git.myproject.org/MyProject1.git/@master");
    doTest("git+ssh://user@git.myproject.org/path/MyProject1@da39a3ee5e6b");
    doTest("git+ssh://user@git.myproject.org/path/MyProject1/@da39a3ee5e6b");
    doTest("git+ssh://user@git.myproject.org/path/MyProject1.git@v1.0");
    doTest("git+ssh://user@git.myproject.org/path/MyProject1.git/@stable/1.5.x");

    doTest("git+user@git.myproject.org:MyProject1@special-feature");
    doTest("git+user@git.myproject.org:MyProject1/@special-feature");
    doTest("git+user@git.myproject.org:MyProject1.git@master");
    doTest("git+user@git.myproject.org:MyProject1.git/@master");
    doTest("git+user@git.myproject.org:/path/MyProject1@da39a3ee5e6b");
    doTest("git+user@git.myproject.org:/path/MyProject1/@da39a3ee5e6b");
    doTest("git+user@git.myproject.org:/path/MyProject1.git@v1.0");
    doTest("git+user@git.myproject.org:/path/MyProject1.git/@stable/1.5.x");
  }

  public void testMercurial() {
    doTest("hg+http://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("hg+http://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("hg+http://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("hg+http://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("hg+https://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("hg+https://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("hg+https://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("hg+https://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("hg+ssh://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("hg+ssh://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("hg+ssh://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("hg+ssh://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("hg+ssh://user@hg.myproject.org/MyProject#egg=MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject/#egg=MyProject1");
  }

  public void testEditableMercurial() {
    doTest("-e hg+http://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("-e hg+http://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e hg+http://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("-e hg+http://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("-e hg+https://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("-e hg+https://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e hg+https://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("-e hg+https://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("-e hg+ssh://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("-e hg+ssh://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e hg+ssh://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("-e hg+ssh://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("-e hg+ssh://user@hg.myproject.org/MyProject#egg=MyProject1");
    doTest("-e hg+ssh://user@hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e hg+ssh://user@hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("-e hg+ssh://user@hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--editable hg+http://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable hg+http://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable hg+http://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--editable hg+http://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--editable hg+https://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable hg+https://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable hg+https://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--editable hg+https://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--editable hg+ssh://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable hg+ssh://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable hg+ssh://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--editable hg+ssh://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--editable hg+ssh://user@hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable hg+ssh://user@hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable hg+ssh://user@hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--editable hg+ssh://user@hg.myproject.org/path/MyProject/#egg=MyProject1");
  }

  public void testMercurialWithRevision() {
    doTest("hg+http://hg.myproject.org/MyProject@special-feature#egg=MyProject1");
    doTest("hg+http://hg.myproject.org/MyProject/@master#egg=MyProject1");
    doTest("hg+http://hg.myproject.org/path/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("hg+http://hg.myproject.org/path/MyProject/@v1.0#egg=MyProject1");

    doTest("hg+https://hg.myproject.org/MyProject@special-feature#egg=MyProject1");
    doTest("hg+https://hg.myproject.org/MyProject/@master#egg=MyProject1");
    doTest("hg+https://hg.myproject.org/path/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("hg+https://hg.myproject.org/path/MyProject/@v1.0#egg=MyProject1");

    doTest("hg+ssh://hg.myproject.org/MyProject@special-feature#egg=MyProject1");
    doTest("hg+ssh://hg.myproject.org/MyProject/@master#egg=MyProject1");
    doTest("hg+ssh://hg.myproject.org/path/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("hg+ssh://hg.myproject.org/path/MyProject/@v1.0#egg=MyProject1");

    doTest("hg+ssh://user@hg.myproject.org/MyProject@special-feature#egg=MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/MyProject/@master#egg=MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject/@v1.0#egg=MyProject1");
  }

  // PY-7583
  public void testMercurialWithoutEgg() {
    doTest("hg+http://hg.myproject.org/MyProject1");
    doTest("hg+http://hg.myproject.org/MyProject1/");
    doTest("hg+http://hg.myproject.org/path/MyProject1");
    doTest("hg+http://hg.myproject.org/path/MyProject1/");

    doTest("hg+https://hg.myproject.org/MyProject1");
    doTest("hg+https://hg.myproject.org/MyProject1/");
    doTest("hg+https://hg.myproject.org/path/MyProject1");
    doTest("hg+https://hg.myproject.org/path/MyProject1/");

    doTest("hg+ssh://hg.myproject.org/MyProject1");
    doTest("hg+ssh://hg.myproject.org/MyProject1/");
    doTest("hg+ssh://hg.myproject.org/path/MyProject1");
    doTest("hg+ssh://hg.myproject.org/path/MyProject1/");

    doTest("hg+ssh://user@hg.myproject.org/MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/MyProject1/");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject1/");
  }

  // PY-7583
  public void testMercurialWithRevisionAndWithoutEgg() {
    doTest("hg+http://hg.myproject.org/MyProject1@special-feature");
    doTest("hg+http://hg.myproject.org/MyProject1/@master");
    doTest("hg+http://hg.myproject.org/path/MyProject1@da39a3ee5e6b");
    doTest("hg+http://hg.myproject.org/path/MyProject1/@v1.0");

    doTest("hg+https://hg.myproject.org/MyProject1@special-feature");
    doTest("hg+https://hg.myproject.org/MyProject1/@master");
    doTest("hg+https://hg.myproject.org/path/MyProject1@da39a3ee5e6b");
    doTest("hg+https://hg.myproject.org/path/MyProject1/@v1.0");

    doTest("hg+ssh://hg.myproject.org/MyProject1@special-feature");
    doTest("hg+ssh://hg.myproject.org/MyProject1/@master");
    doTest("hg+ssh://hg.myproject.org/path/MyProject1@da39a3ee5e6b");
    doTest("hg+ssh://hg.myproject.org/path/MyProject1/@v1.0");

    doTest("hg+ssh://user@hg.myproject.org/MyProject1@special-feature");
    doTest("hg+ssh://user@hg.myproject.org/MyProject1/@master");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject1@da39a3ee5e6b");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject1/@v1.0");
  }

  public void testSubversion() {
    doTest("svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("svn+http://svn.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("svn+http://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1");
    doTest("svn+http://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1");

    doTest("svn+https://svn.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("svn+https://svn.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("svn+https://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1");
    doTest("svn+https://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1");

    doTest("svn+ssh://svn.myproject.org/MyProject#egg=MyProject1");
    doTest("svn+ssh://svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("svn+ssh://user@svn.myproject.org/MyProject#egg=MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("svn+svn://svn.myproject.org/MyProject#egg=MyProject1");
    doTest("svn+svn://svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("svn+svn://svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("svn+svn://svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("svn+svn://user@svn.myproject.org/MyProject#egg=MyProject1");
    doTest("svn+svn://user@svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject/#egg=MyProject1");
  }

  public void testEditableSubversion() {
    doTest("-e svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("-e svn+http://svn.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("-e svn+http://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1");
    doTest("-e svn+http://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1");

    doTest("-e svn+https://svn.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("-e svn+https://svn.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("-e svn+https://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1");
    doTest("-e svn+https://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1");

    doTest("-e svn+ssh://svn.myproject.org/MyProject#egg=MyProject1");
    doTest("-e svn+ssh://svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e svn+ssh://svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("-e svn+ssh://svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("-e svn+ssh://user@svn.myproject.org/MyProject#egg=MyProject1");
    doTest("-e svn+ssh://user@svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e svn+ssh://user@svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("-e svn+ssh://user@svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("-e svn+svn://svn.myproject.org/MyProject#egg=MyProject1");
    doTest("-e svn+svn://svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e svn+svn://svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("-e svn+svn://svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("-e svn+svn://user@svn.myproject.org/MyProject#egg=MyProject1");
    doTest("-e svn+svn://user@svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("-e svn+svn://user@svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("-e svn+svn://user@svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--editable svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--editable svn+http://svn.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--editable svn+http://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1");
    doTest("--editable svn+http://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1");

    doTest("--editable svn+https://svn.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--editable svn+https://svn.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--editable svn+https://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1");
    doTest("--editable svn+https://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1");

    doTest("--editable svn+ssh://svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable svn+ssh://svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable svn+ssh://svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--editable svn+ssh://svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--editable svn+ssh://user@svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable svn+ssh://user@svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable svn+ssh://user@svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--editable svn+ssh://user@svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--editable svn+svn://svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable svn+svn://svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable svn+svn://svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--editable svn+svn://svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--editable svn+svn://user@svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--editable svn+svn://user@svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--editable svn+svn://user@svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--editable svn+svn://user@svn.myproject.org/svn/MyProject/#egg=MyProject1");
  }

  public void testSubversionWithRevision() {
    doTest("svn+http://svn.myproject.org/MyProject/trunk@master#egg=MyProject1");
    doTest("svn+http://svn.myproject.org/MyProject/trunk/@special-feature#egg=MyProject1");
    doTest("svn+http://svn.myproject.org/svn/MyProject/trunk@da39a3ee5e6b#egg=MyProject1");
    doTest("svn+http://svn.myproject.org/svn/MyProject/trunk/@v1.0#egg=MyProject1");

    doTest("svn+https://svn.myproject.org/MyProject/trunk@master#egg=MyProject1");
    doTest("svn+https://svn.myproject.org/MyProject/trunk/@special-feature#egg=MyProject1");
    doTest("svn+https://svn.myproject.org/svn/MyProject/trunk@da39a3ee5e6b#egg=MyProject1");
    doTest("svn+https://svn.myproject.org/svn/MyProject/trunk/@v1.0#egg=MyProject1");

    doTest("svn+ssh://svn.myproject.org/MyProject@master#egg=MyProject1");
    doTest("svn+ssh://svn.myproject.org/MyProject/@special-feature#egg=MyProject1");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject/@v1.0#egg=MyProject1");

    doTest("svn+ssh://user@svn.myproject.org/MyProject@master#egg=MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/MyProject/@special-feature#egg=MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject/@v1.0#egg=MyProject1");

    doTest("svn+svn://svn.myproject.org/MyProject@master#egg=MyProject1");
    doTest("svn+svn://svn.myproject.org/MyProject/@special-feature#egg=MyProject1");
    doTest("svn+svn://svn.myproject.org/svn/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("svn+svn://svn.myproject.org/svn/MyProject/@v1.0#egg=MyProject1");

    doTest("svn+svn://user@svn.myproject.org/MyProject@master#egg=MyProject1");
    doTest("svn+svn://user@svn.myproject.org/MyProject/@special-feature#egg=MyProject1");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject@da39a3ee5e6b#egg=MyProject1");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject/@v1.0#egg=MyProject1");
  }

  // PY-7583
  public void testSubversionWithoutEgg() {
    doTest("svn+http://svn.myproject.org/MyProject1/trunk");
    doTest("svn+http://svn.myproject.org/MyProject1/trunk/");
    doTest("svn+http://svn.myproject.org/svn/MyProject1/trunk");
    doTest("svn+http://svn.myproject.org/svn/MyProject1/trunk/");

    doTest("svn+https://svn.myproject.org/MyProject1/trunk");
    doTest("svn+https://svn.myproject.org/MyProject1/trunk/");
    doTest("svn+https://svn.myproject.org/svn/MyProject1/trunk");
    doTest("svn+https://svn.myproject.org/svn/MyProject1/trunk/");

    doTest("svn+ssh://svn.myproject.org/MyProject1");
    doTest("svn+ssh://svn.myproject.org/MyProject1/");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject1");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject1/");

    doTest("svn+ssh://user@svn.myproject.org/MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/MyProject1/");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject1/");

    doTest("svn+svn://svn.myproject.org/MyProject1");
    doTest("svn+svn://svn.myproject.org/MyProject1/");
    doTest("svn+svn://svn.myproject.org/svn/MyProject1");
    doTest("svn+svn://svn.myproject.org/svn/MyProject1/");

    doTest("svn+svn://user@svn.myproject.org/MyProject1");
    doTest("svn+svn://user@svn.myproject.org/MyProject1/");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject1");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject1/");
  }

  // PY-7583
  public void testSubversionWithRevisionAndWithoutEgg() {
    doTest("svn+http://svn.myproject.org/MyProject1/trunk@master");
    doTest("svn+http://svn.myproject.org/MyProject1/trunk/@special-feature");
    doTest("svn+http://svn.myproject.org/svn/MyProject1/trunk@da39a3ee5e6b");
    doTest("svn+http://svn.myproject.org/svn/MyProject1/trunk/@v1.0");

    doTest("svn+https://svn.myproject.org/MyProject1/trunk@master");
    doTest("svn+https://svn.myproject.org/MyProject1/trunk/@special-feature");
    doTest("svn+https://svn.myproject.org/svn/MyProject1/trunk@da39a3ee5e6b");
    doTest("svn+https://svn.myproject.org/svn/MyProject1/trunk/@v1.0");

    doTest("svn+ssh://svn.myproject.org/MyProject1@master");
    doTest("svn+ssh://svn.myproject.org/MyProject1/@special-feature");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject1@da39a3ee5e6b");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject1/@v1.0");

    doTest("svn+ssh://user@svn.myproject.org/MyProject1@master");
    doTest("svn+ssh://user@svn.myproject.org/MyProject1/@special-feature");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject1@da39a3ee5e6b");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject1/@v1.0");

    doTest("svn+svn://svn.myproject.org/MyProject1@master");
    doTest("svn+svn://svn.myproject.org/MyProject1/@special-feature");
    doTest("svn+svn://svn.myproject.org/svn/MyProject1@da39a3ee5e6b");
    doTest("svn+svn://svn.myproject.org/svn/MyProject1/@v1.0");

    doTest("svn+svn://user@svn.myproject.org/MyProject1@master");
    doTest("svn+svn://user@svn.myproject.org/MyProject1/@special-feature");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject1@da39a3ee5e6b");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject1/@v1.0");
  }

  public void testBazaar() {
    doTest("bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("bzr+http://bzr.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("bzr+http://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("bzr+http://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("bzr+https://bzr.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("bzr+https://bzr.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("bzr+https://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("bzr+https://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("bzr+sftp://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("bzr+sftp://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("bzr+sftp://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("bzr+sftp://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("bzr+sftp://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("bzr+sftp://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("bzr+sftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("bzr+sftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("bzr+ssh://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("bzr+ssh://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("bzr+ssh://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("bzr+ssh://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("bzr+ssh://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("bzr+ssh://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("bzr+ssh://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("bzr+ssh://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("bzr+ftp://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("bzr+ftp://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("bzr+ftp://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("bzr+ftp://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("bzr+ftp://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("bzr+ftp://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("bzr+ftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("bzr+ftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("bzr+lp:MyProject#egg=MyProject1");
  }

  public void testEditableBazaar() {
    doTest("-e bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+http://bzr.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("-e bzr+http://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+http://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("-e bzr+https://bzr.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+https://bzr.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("-e bzr+https://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+https://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("-e bzr+sftp://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+sftp://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("-e bzr+sftp://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+sftp://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("-e bzr+sftp://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+sftp://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("-e bzr+sftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+sftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("-e bzr+ssh://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+ssh://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("-e bzr+ssh://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+ssh://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("-e bzr+ssh://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+ssh://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("-e bzr+ssh://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+ssh://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("-e bzr+ftp://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+ftp://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("-e bzr+ftp://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+ftp://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("-e bzr+ftp://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+ftp://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("-e bzr+ftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("-e bzr+ftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("-e bzr+lp:MyProject#egg=MyProject1");

    doTest("--editable bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+http://bzr.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--editable bzr+http://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+http://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--editable bzr+https://bzr.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+https://bzr.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--editable bzr+https://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+https://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--editable bzr+sftp://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+sftp://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--editable bzr+sftp://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+sftp://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--editable bzr+sftp://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+sftp://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--editable bzr+sftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+sftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--editable bzr+ssh://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+ssh://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--editable bzr+ssh://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+ssh://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--editable bzr+ssh://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+ssh://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--editable bzr+ssh://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+ssh://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--editable bzr+ftp://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+ftp://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--editable bzr+ftp://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+ftp://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--editable bzr+ftp://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+ftp://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--editable bzr+ftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--editable bzr+ftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--editable bzr+lp:MyProject#egg=MyProject1");
  }

  public void testBazaarWithRevision() {
    doTest("bzr+http://bzr.myproject.org/MyProject/trunk@master#egg=MyProject1");
    doTest("bzr+http://bzr.myproject.org/MyProject/trunk/@special-feature#egg=MyProject1");
    doTest("bzr+http://bzr.myproject.org/path/MyProject/trunk@da39a3ee5e6b#egg=MyProject1");
    doTest("bzr+http://bzr.myproject.org/path/MyProject/trunk/@v1.0#egg=MyProject1");

    doTest("bzr+https://bzr.myproject.org/MyProject/trunk@master#egg=MyProject1");
    doTest("bzr+https://bzr.myproject.org/MyProject/trunk/@special-feature#egg=MyProject1");
    doTest("bzr+https://bzr.myproject.org/path/MyProject/trunk@da39a3ee5e6b#egg=MyProject1");
    doTest("bzr+https://bzr.myproject.org/path/MyProject/trunk/@v1.0#egg=MyProject1");

    doTest("bzr+sftp://myproject.org/MyProject/trunk@master#egg=MyProject1");
    doTest("bzr+sftp://myproject.org/MyProject/trunk/@special-feature#egg=MyProject1");
    doTest("bzr+sftp://myproject.org/path/MyProject/trunk@da39a3ee5e6b#egg=MyProject1");
    doTest("bzr+sftp://myproject.org/path/MyProject/trunk/@v1.0#egg=MyProject1");

    doTest("bzr+sftp://user@myproject.org/MyProject/trunk@master#egg=MyProject1");
    doTest("bzr+sftp://user@myproject.org/MyProject/trunk/@special-feature#egg=MyProject1");
    doTest("bzr+sftp://user@myproject.org/path/MyProject/trunk@da39a3ee5e6b#egg=MyProject1");
    doTest("bzr+sftp://user@myproject.org/path/MyProject/trunk/@v1.0#egg=MyProject1");

    doTest("bzr+ssh://myproject.org/MyProject/trunk@master#egg=MyProject1");
    doTest("bzr+ssh://myproject.org/MyProject/trunk/@special-feature#egg=MyProject1");
    doTest("bzr+ssh://myproject.org/path/MyProject/trunk@da39a3ee5e6b#egg=MyProject1");
    doTest("bzr+ssh://myproject.org/path/MyProject/trunk/@v1.0#egg=MyProject1");

    doTest("bzr+ssh://user@myproject.org/MyProject/trunk@master#egg=MyProject1");
    doTest("bzr+ssh://user@myproject.org/MyProject/trunk/@special-feature#egg=MyProject1");
    doTest("bzr+ssh://user@myproject.org/path/MyProject/trunk@da39a3ee5e6b#egg=MyProject1");
    doTest("bzr+ssh://user@myproject.org/path/MyProject/trunk/@v1.0#egg=MyProject1");

    doTest("bzr+ftp://myproject.org/MyProject/trunk@master#egg=MyProject1");
    doTest("bzr+ftp://myproject.org/MyProject/trunk/@special-feature#egg=MyProject1");
    doTest("bzr+ftp://myproject.org/path/MyProject/trunk@da39a3ee5e6b#egg=MyProject1");
    doTest("bzr+ftp://myproject.org/path/MyProject/trunk/@v1.0#egg=MyProject1");

    doTest("bzr+ftp://user@myproject.org/MyProject/trunk@master#egg=MyProject1");
    doTest("bzr+ftp://user@myproject.org/MyProject/trunk/@special-feature#egg=MyProject1");
    doTest("bzr+ftp://user@myproject.org/path/MyProject/trunk@da39a3ee5e6b#egg=MyProject1");
    doTest("bzr+ftp://user@myproject.org/path/MyProject/trunk/@v1.0#egg=MyProject1");

    doTest("bzr+lp:MyProject@master#egg=MyProject1");
  }

  // PY-7583
  public void testBazaarWithoutEgg() {
    doTest("bzr+http://bzr.myproject.org/MyProject1/trunk");
    doTest("bzr+http://bzr.myproject.org/MyProject1/trunk/");
    doTest("bzr+http://bzr.myproject.org/path/MyProject1/trunk");
    doTest("bzr+http://bzr.myproject.org/path/MyProject1/trunk/");

    doTest("bzr+https://bzr.myproject.org/MyProject1/trunk");
    doTest("bzr+https://bzr.myproject.org/MyProject1/trunk/");
    doTest("bzr+https://bzr.myproject.org/path/MyProject1/trunk");
    doTest("bzr+https://bzr.myproject.org/path/MyProject1/trunk/");

    doTest("bzr+sftp://myproject.org/MyProject1/trunk");
    doTest("bzr+sftp://myproject.org/MyProject1/trunk/");
    doTest("bzr+sftp://myproject.org/path/MyProject1/trunk");
    doTest("bzr+sftp://myproject.org/path/MyProject1/trunk/");

    doTest("bzr+sftp://user@myproject.org/MyProject1/trunk");
    doTest("bzr+sftp://user@myproject.org/MyProject1/trunk/");
    doTest("bzr+sftp://user@myproject.org/path/MyProject1/trunk");
    doTest("bzr+sftp://user@myproject.org/path/MyProject1/trunk/");

    doTest("bzr+ssh://myproject.org/MyProject1/trunk");
    doTest("bzr+ssh://myproject.org/MyProject1/trunk/");
    doTest("bzr+ssh://myproject.org/path/MyProject1/trunk");
    doTest("bzr+ssh://myproject.org/path/MyProject1/trunk/");

    doTest("bzr+ssh://user@myproject.org/MyProject1/trunk");
    doTest("bzr+ssh://user@myproject.org/MyProject1/trunk/");
    doTest("bzr+ssh://user@myproject.org/path/MyProject1/trunk");
    doTest("bzr+ssh://user@myproject.org/path/MyProject1/trunk/");

    doTest("bzr+ftp://myproject.org/MyProject1/trunk");
    doTest("bzr+ftp://myproject.org/MyProject1/trunk/");
    doTest("bzr+ftp://myproject.org/path/MyProject1/trunk");
    doTest("bzr+ftp://myproject.org/path/MyProject1/trunk/");

    doTest("bzr+ftp://user@myproject.org/MyProject1/trunk");
    doTest("bzr+ftp://user@myproject.org/MyProject1/trunk/");
    doTest("bzr+ftp://user@myproject.org/path/MyProject1/trunk");
    doTest("bzr+ftp://user@myproject.org/path/MyProject1/trunk/");

    doTest("bzr+lp:MyProject1");
  }

  // PY-7583
  public void testBazaarWithRevisionAndWithoutEgg() {
    doTest("bzr+http://bzr.myproject.org/MyProject1/trunk@master");
    doTest("bzr+http://bzr.myproject.org/MyProject1/trunk/@special-feature");
    doTest("bzr+http://bzr.myproject.org/path/MyProject1/trunk@da39a3ee5e6b");
    doTest("bzr+http://bzr.myproject.org/path/MyProject1/trunk/@v1.0");

    doTest("bzr+https://bzr.myproject.org/MyProject1/trunk@master");
    doTest("bzr+https://bzr.myproject.org/MyProject1/trunk/@special-feature");
    doTest("bzr+https://bzr.myproject.org/path/MyProject1/trunk@da39a3ee5e6b");
    doTest("bzr+https://bzr.myproject.org/path/MyProject1/trunk/@v1.0");

    doTest("bzr+sftp://myproject.org/MyProject1/trunk@master");
    doTest("bzr+sftp://myproject.org/MyProject1/trunk/@special-feature");
    doTest("bzr+sftp://myproject.org/path/MyProject1/trunk@da39a3ee5e6b");
    doTest("bzr+sftp://myproject.org/path/MyProject1/trunk/@v1.0");

    doTest("bzr+sftp://user@myproject.org/MyProject1/trunk@master");
    doTest("bzr+sftp://user@myproject.org/MyProject1/trunk/@special-feature");
    doTest("bzr+sftp://user@myproject.org/path/MyProject1/trunk@da39a3ee5e6b");
    doTest("bzr+sftp://user@myproject.org/path/MyProject1/trunk/@v1.0");

    doTest("bzr+ssh://myproject.org/MyProject1/trunk@master");
    doTest("bzr+ssh://myproject.org/MyProject1/trunk/@special-feature");
    doTest("bzr+ssh://myproject.org/path/MyProject1/trunk@da39a3ee5e6b");
    doTest("bzr+ssh://myproject.org/path/MyProject1/trunk/@v1.0");

    doTest("bzr+ssh://user@myproject.org/MyProject1/trunk@master");
    doTest("bzr+ssh://user@myproject.org/MyProject1/trunk/@special-feature");
    doTest("bzr+ssh://user@myproject.org/path/MyProject1/trunk@da39a3ee5e6b");
    doTest("bzr+ssh://user@myproject.org/path/MyProject1/trunk/@v1.0");

    doTest("bzr+ftp://myproject.org/MyProject1/trunk@master");
    doTest("bzr+ftp://myproject.org/MyProject1/trunk/@special-feature");
    doTest("bzr+ftp://myproject.org/path/MyProject1/trunk@da39a3ee5e6b");
    doTest("bzr+ftp://myproject.org/path/MyProject1/trunk/@v1.0");

    doTest("bzr+ftp://user@myproject.org/MyProject1/trunk@master");
    doTest("bzr+ftp://user@myproject.org/MyProject1/trunk/@special-feature");
    doTest("bzr+ftp://user@myproject.org/path/MyProject1/trunk@da39a3ee5e6b");
    doTest("bzr+ftp://user@myproject.org/path/MyProject1/trunk/@v1.0");

    doTest("bzr+lp:MyProject1@master");
  }

  // PY-7034
  public void testMinusInRequirementEggName() {
    final String options = "git://github.com/toastdriven/django-haystack.git#egg=django-haystack";

    assertEquals(new PyRequirement("django-haystack", Collections.emptyList(), options), PyRequirement.fromString(options));
  }

  public void testDevInRequirementEggName() {
    final String options1 = "git://github.com/toastdriven/django-haystack.git#egg=django_haystack-dev";
    assertEquals(new PyRequirement("django-haystack", "dev", options1), PyRequirement.fromString(options1));

    final String options2 = "git://github.com/toastdriven/django-haystack.git#egg=django-haystack-dev";
    assertEquals(new PyRequirement("django-haystack", "dev", options2), PyRequirement.fromString(options2));
  }

  // LOCAL DIR
  // TODO: which must contain a setup.py

  // LOCAL FILE
  // TODO: a sdist or wheel format archive

  // REQUIREMENT
  // TODO: name normalization
  // TODO: hashes
  // TODO: multiline
  // TODO: https://pip.pypa.io/en/stable/reference/pip_install/#per-requirement-overrides
  // https://www.python.org/dev/peps/pep-0508/#names
  public void testRequirement() {
    assertEquals(new PyRequirement("Orange-Bioinformatics"), PyRequirement.fromString("Orange-Bioinformatics"));
    assertEquals(new PyRequirement("MOCPy"), PyRequirement.fromString("MOCPy"));
    assertEquals(new PyRequirement("score.webassets"), PyRequirement.fromString("score.webassets"));
    assertEquals(new PyRequirement("pip_helpers"), PyRequirement.fromString("pip_helpers"));
    assertEquals(new PyRequirement("Django"), PyRequirement.fromString("Django"));
    assertEquals(new PyRequirement("django"), PyRequirement.fromString("django"));
    assertEquals(new PyRequirement("pinax-utils"), PyRequirement.fromString("pinax-utils"));
    assertEquals(new PyRequirement("no_limit_nester"), PyRequirement.fromString("no_limit_nester"));
    assertEquals(new PyRequirement("Flask-Celery-py3"), PyRequirement.fromString("Flask-Celery-py3"));
  }

  // https://www.python.org/dev/peps/pep-0440/
  public void testRequirementVersion() {
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5a20"));
    assertEquals(new PyRequirement("MOCPy", "0.1.0.dev0"), PyRequirement.fromString("MOCPy==0.1.0.dev0"));
    assertEquals(new PyRequirement("score.webassets", "0.2.3"), PyRequirement.fromString("score.webassets==0.2.3"));
    assertEquals(new PyRequirement("pip_helpers", "0.5.post6"), PyRequirement.fromString("pip_helpers==0.5.post6"));
    assertEquals(new PyRequirement("Django", "1.9rc1"), PyRequirement.fromString("Django==1.9rc1"));
    assertEquals(new PyRequirement("django", "1!1"), PyRequirement.fromString("django==1!1"));
    assertEquals(new PyRequirement("pinax-utils", "1.0b1.dev3"), PyRequirement.fromString("pinax-utils==1.0b1.dev3"));
    assertEquals(new PyRequirement("Flask-Celery-py3", "0.1.*"), PyRequirement.fromString("Flask-Celery-py3==0.1.*"));
    assertEquals(new PyRequirement("no_limit_nester", "1.0+local.version.10"),
                 PyRequirement.fromString("no_limit_nester==1.0+local.version.10"));
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternatePreReleaseVersion() {
    assertEquals(new PyRequirement("Django", "1.9rc1"), PyRequirement.fromString("Django==1.9RC1"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.a20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.a.20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-a20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-a_20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_a20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_a-20"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5alpha20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.alpha20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.alpha.20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-alpha20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-alpha_20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_alpha20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_alpha-20"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5b20"), PyRequirement.fromString("Orange-Bioinformatics==2.5beta20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5b20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.beta20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5b20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.beta.20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5b20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-beta20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5b20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-beta_20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5b20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_beta20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5b20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_beta-20"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5c20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.c20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.c.20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-c20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-c_20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_c20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_c-20"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5pre20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.pre20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.pre.20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-pre20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-pre_20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_pre20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_pre-20"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5preview20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.preview20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.preview.20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-preview20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-preview_20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_preview20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5rc20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_preview-20"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a0"), PyRequirement.fromString("Orange-Bioinformatics==2.5a"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a0"), PyRequirement.fromString("Orange-Bioinformatics==2.5.a"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a0"), PyRequirement.fromString("Orange-Bioinformatics==2.5-a"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a0"), PyRequirement.fromString("Orange-Bioinformatics==2.5_a"));
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternatePostReleaseVersion() {
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-post20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-post.20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_post20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_post_20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5post20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5post-20"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.r20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-r20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-r.20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_r20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_r_20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5r20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5r-20"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5.rev20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-rev20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-rev.20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_rev20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_rev_20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5rev20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5rev-20"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post0"), PyRequirement.fromString("Orange-Bioinformatics==2.5.post"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post0"), PyRequirement.fromString("Orange-Bioinformatics==2.5-post"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post0"), PyRequirement.fromString("Orange-Bioinformatics==2.5_post"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post0"), PyRequirement.fromString("Orange-Bioinformatics==2.5post"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.post20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-20"));
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternateDevelopmentVersion() {
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.dev20"), PyRequirement.fromString("Orange-Bioinformatics==2.5-dev20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.dev20"), PyRequirement.fromString("Orange-Bioinformatics==2.5_dev20"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.dev20"), PyRequirement.fromString("Orange-Bioinformatics==2.5dev20"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.dev0"), PyRequirement.fromString("Orange-Bioinformatics==2.5-dev"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.dev0"), PyRequirement.fromString("Orange-Bioinformatics==2.5_dev"));
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5.dev0"), PyRequirement.fromString("Orange-Bioinformatics==2.5dev"));
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternateLocalVersion() {
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5+local.version"),
                 PyRequirement.fromString("Orange-Bioinformatics==2.5+local-version"));

    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5+local.version"),
                 PyRequirement.fromString("Orange-Bioinformatics==2.5+local_version"));
  }

  // https://www.python.org/dev/peps/pep-0440/#normalization
  public void testRequirementAlternateVersionStart() {
    assertEquals(new PyRequirement("Orange-Bioinformatics", "2.5a20"), PyRequirement.fromString("Orange-Bioinformatics==v2.5a20"));
    assertEquals(new PyRequirement("MOCPy", "0.1.0.dev0"), PyRequirement.fromString("MOCPy==v0.1.0.dev0"));
    assertEquals(new PyRequirement("score.webassets", "0.2.3"), PyRequirement.fromString("score.webassets==v0.2.3"));
    assertEquals(new PyRequirement("pip_helpers", "0.5.post6"), PyRequirement.fromString("pip_helpers==v0.5.post6"));
    assertEquals(new PyRequirement("Django", "1.9rc1"), PyRequirement.fromString("Django==v1.9rc1"));
    assertEquals(new PyRequirement("django", "1!1"), PyRequirement.fromString("django==v1!1"));
    assertEquals(new PyRequirement("pinax-utils", "1.0b1.dev3"), PyRequirement.fromString("pinax-utils==v1.0b1.dev3"));
    assertEquals(new PyRequirement("no_limit_nester"), PyRequirement.fromString("no_limit_nester==v1.0+local.version.10"));
    assertEquals(new PyRequirement("Flask-Celery-py3", "0.1.*"), PyRequirement.fromString("Flask-Celery-py3==v0.1.*"));
  }

  // https://www.python.org/dev/peps/pep-0440/#version-specifiers
  public void testRequirementRelation() {
    doRequirementRelationTest(PyRequirementRelation.LT, "1.4");
    doRequirementRelationTest(PyRequirementRelation.LTE, "1.4");
    doRequirementRelationTest(PyRequirementRelation.NE, "1.4");
    doRequirementRelationTest(PyRequirementRelation.EQ, "1.4");
    doRequirementRelationTest(PyRequirementRelation.GT, "1.4");
    doRequirementRelationTest(PyRequirementRelation.GTE, "1.4");
    doRequirementRelationTest(PyRequirementRelation.COMPATIBLE, "1.*");
    doRequirementRelationTest(PyRequirementRelation.STR_EQ, "version");

    doRequirementRelationTest(Arrays.asList(PyRequirementRelation.GTE, PyRequirementRelation.EQ), Arrays.asList("2.8.1", "2.8.*"));
    doRequirementRelationTest(Arrays.asList(PyRequirementRelation.LT, PyRequirementRelation.GTE), Arrays.asList("1.4", "1.3.1"));

    doRequirementRelationTest(Arrays.asList(PyRequirementRelation.LT, PyRequirementRelation.GT, PyRequirementRelation.NE,
                                            PyRequirementRelation.LT, PyRequirementRelation.EQ),
                              Arrays.asList("1.6", "1.9", "1.9.6", "2.0a0", "2.4rc1"));

    // PY-14583
    doRequirementRelationTest(Arrays.asList(PyRequirementRelation.GTE, PyRequirementRelation.LTE, PyRequirementRelation.GTE,
                                            PyRequirementRelation.LTE),
                              Arrays.asList("0.8.4", "0.8.99", "0.9.7", "0.9.99"));
  }

  // https://www.python.org/dev/peps/pep-0508/#extras
  // PY-15674
  public void testRequirementExtras() {
    final String name = "MyProject1";
    final List<PyRequirementRelation> relations = Collections.emptyList();
    final List<String> versions = Collections.emptyList();

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

    doRequirementRelationTest(name1, extras1, PyRequirementRelation.LT, "1.4");
    doRequirementRelationTest(name2, extras2, PyRequirementRelation.LTE, "1.4");
    doRequirementRelationTest(name3, extras3, PyRequirementRelation.NE, "1.4");
    doRequirementRelationTest(name1, extras1, PyRequirementRelation.EQ, "1.4");
    doRequirementRelationTest(name2, extras2, PyRequirementRelation.GT, "1.4");
    doRequirementRelationTest(name3, extras3, PyRequirementRelation.GTE, "1.4");
    doRequirementRelationTest(name1, extras1, PyRequirementRelation.COMPATIBLE, "1.*");
    doRequirementRelationTest(name2, extras2, PyRequirementRelation.STR_EQ, "version");

    doRequirementRelationTest(name3, extras3, Arrays.asList(PyRequirementRelation.GTE, PyRequirementRelation.EQ),
                              Arrays.asList("2.8.1", "2.8.*"));

    doRequirementRelationTest(name1, extras1, Arrays.asList(PyRequirementRelation.LT, PyRequirementRelation.GTE),
                              Arrays.asList("1.4", "1.3.1"));

    doRequirementRelationTest(name2, extras2, Arrays.asList(PyRequirementRelation.LT, PyRequirementRelation.GT, PyRequirementRelation.NE,
                                                            PyRequirementRelation.LT, PyRequirementRelation.EQ),
                              Arrays.asList("1.6", "1.9", "1.9.6", "2.0a0", "2.4rc1"));

    // PY-14583
    doRequirementRelationTest(name3, extras3, Arrays.asList(PyRequirementRelation.GTE, PyRequirementRelation.LTE, PyRequirementRelation.GTE,
                                                            PyRequirementRelation.LTE),
                              Arrays.asList("0.8.4", "0.8.99", "0.9.7", "0.9.99"));
  }

  // PY-6355
  public void testTrailingZeroesInVersion() {
    final PyRequirement req080 = PyRequirement.fromString("foo==0.8.0");
    final PyPackage pack08 = new PyPackage("foo", "0.8", null, Collections.<PyRequirement>emptyList());
    assertNotNull(req080);
    assertNotNull(req080.match(Collections.singletonList(pack08)));
  }

  // PY-6438
  public void testUnderscoreMatchesDash() {
    final PyRequirement req = PyRequirement.fromString("pyramid_zcml");
    final PyPackage pkg = new PyPackage("pyramid-zcml", "0.1", null, Collections.<PyRequirement>emptyList());
    assertNotNull(req);
    assertNotNull(req.match(Collections.singletonList(pkg)));
  }

  // OPTIONS
  public void testOptions() {
    assertEmpty(
      PyRequirement.parse(
        "-i URL\n" +
        "--index-url URL\n" +
        "--extra-index-url URL\n" +
        "--no-index\n" +
        "-f URL\n" +
        "--find-links URL\n" +
        "--no-binary SMTH\n" +
        "--only-binary SMTH\n" +
        "--require-hashes"
      )
    );
  }

  // RECURSIVE REQUIREMENTS
  // PY-7011
  // PY-18543
  public void testRecursiveRequirements() {
    final VirtualFile requirementsFile = getVirtualFileByName(getTestDataPath() + "/requirement/recursive/requirements.txt");
    assertNotNull(requirementsFile);

    assertEquals(Arrays.asList(new PyRequirement("bitly_api"), new PyRequirement("numpy"), new PyRequirement("SomeProject")),
                 PyRequirement.parse(requirementsFile));
  }

  // COMMENTS
  // TODO: comment at the end
  public void testComment() {
    assertNull(PyRequirement.fromString("# comment"));
  }

  // ENV MARKERS
  // TODO: https://www.python.org/dev/peps/pep-0426/#environment-markers, https://www.python.org/dev/peps/pep-0508/#environment-markers

  private static void doTest(@NotNull String options) {
    assertEquals(new PyRequirement("MyProject1", Collections.emptyList(), options), PyRequirement.fromString(options));
  }

  private static void doRequirementRelationTest(@NotNull PyRequirementRelation relation, @NotNull String version) {
    doRequirementRelationTest("Django", null, Collections.singletonList(relation), Collections.singletonList(version));
  }

  private static void doRequirementRelationTest(@NotNull List<PyRequirementRelation> relations, @NotNull List<String> versions) {
    doRequirementRelationTest("Django", null, relations, versions);
  }

  private static void doRequirementRelationTest(@NotNull String name,
                                                @Nullable String extras,
                                                @NotNull PyRequirementRelation relation,
                                                @NotNull String version) {
    doRequirementRelationTest(name, extras, Collections.singletonList(relation), Collections.singletonList(version));
  }

  private static void doRequirementRelationTest(@NotNull String name,
                                                @Nullable String extras,
                                                @NotNull List<PyRequirementRelation> relations,
                                                @NotNull List<String> versions) {
    assertEquals(versions.size(), relations.size());

    final StringBuilder sb = new StringBuilder(name);
    final List<PyRequirementVersionSpec> expectedVersionSpecs = new ArrayList<>();

    if (extras != null) sb.append(extras);
    final int initialLength = sb.length();

    for (Pair<PyRequirementRelation, String> pair : ContainerUtil.zip(relations, versions)) {
      final PyRequirementRelation relation = pair.getFirst();
      final String version = pair.getSecond();

      sb.append(relation).append(version).append(",");
      expectedVersionSpecs.add(new PyRequirementVersionSpec(relation, version));
    }

    if (sb.length() != initialLength) {
      sb.setLength(sb.length() - 1);
    }

    final String options = sb.toString();

    assertEquals(new PyRequirement(name, expectedVersionSpecs, options), PyRequirement.fromString(options));
  }
}
