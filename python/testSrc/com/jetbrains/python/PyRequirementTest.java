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
package com.jetbrains.python;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyRequirement;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;

/**
 * @author vlan
 */
public class PyRequirementTest extends PyTestCase {

  // ARCHIVE URL
  public void testArchiveUrl() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url, false), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithMd5() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#md5=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url, false), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithSha1() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha1=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url, false), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithSha224() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha224=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url, false), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithSha256() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha256=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url, false), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithSha384() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha384=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url, false), PyRequirement.fromString(url));
  }

  // PY-14230
  public void testArchiveUrlWithSha512() {
    final String url = "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha512=26259d212447bc840400c25a48275fbc";

    assertEquals(new PyRequirement("geoip2", "2.2.0", url, false), PyRequirement.fromString(url));
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
    doTest("-e git://git.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e git://git.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e git://git.myproject.org/MyProject.git#egg=MyProject1", true);
    doTest("-e git://git.myproject.org/MyProject.git/#egg=MyProject1", true);
    doTest("-e git://git.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("-e git://git.myproject.org/path/MyProject/#egg=MyProject1", true);
    doTest("-e git://git.myproject.org/path/MyProject.git#egg=MyProject1", true);
    doTest("-e git://git.myproject.org/path/MyProject.git/#egg=MyProject1", true);

    doTest("-e git+git://git.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e git+git://git.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e git+git://git.myproject.org/MyProject.git#egg=MyProject1", true);
    doTest("-e git+git://git.myproject.org/MyProject.git/#egg=MyProject1", true);
    doTest("-e git+git://git.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("-e git+git://git.myproject.org/path/MyProject/#egg=MyProject1", true);
    doTest("-e git+git://git.myproject.org/path/MyProject.git#egg=MyProject1", true);
    doTest("-e git+git://git.myproject.org/path/MyProject.git/#egg=MyProject1", true);

    doTest("-e git+https://git.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e git+https://git.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e git+https://git.myproject.org/MyProject.git#egg=MyProject1", true);
    doTest("-e git+https://git.myproject.org/MyProject.git/#egg=MyProject1", true);
    doTest("-e git+https://git.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("-e git+https://git.myproject.org/path/MyProject/#egg=MyProject1", true);
    doTest("-e git+https://git.myproject.org/path/MyProject.git#egg=MyProject1", true);
    doTest("-e git+https://git.myproject.org/path/MyProject.git/#egg=MyProject1", true);

    doTest("-e git+ssh://git.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e git+ssh://git.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e git+ssh://git.myproject.org/MyProject.git#egg=MyProject1", true);
    doTest("-e git+ssh://git.myproject.org/MyProject.git/#egg=MyProject1", true);
    doTest("-e git+ssh://git.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("-e git+ssh://git.myproject.org/path/MyProject/#egg=MyProject1", true);
    doTest("-e git+ssh://git.myproject.org/path/MyProject.git#egg=MyProject1", true);
    doTest("-e git+ssh://git.myproject.org/path/MyProject.git/#egg=MyProject1", true);

    doTest("-e git+ssh://user@git.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e git+ssh://user@git.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e git+ssh://user@git.myproject.org/MyProject.git#egg=MyProject1", true);
    doTest("-e git+ssh://user@git.myproject.org/MyProject.git/#egg=MyProject1", true);
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject/#egg=MyProject1", true);
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject.git#egg=MyProject1", true);
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject.git/#egg=MyProject1", true);

    doTest("-e git+user@git.myproject.org:MyProject#egg=MyProject1", true);
    doTest("-e git+user@git.myproject.org:MyProject/#egg=MyProject1", true);
    doTest("-e git+user@git.myproject.org:MyProject.git#egg=MyProject1", true);
    doTest("-e git+user@git.myproject.org:MyProject.git/#egg=MyProject1", true);
    doTest("-e git+user@git.myproject.org:/path/MyProject#egg=MyProject1", true);
    doTest("-e git+user@git.myproject.org:/path/MyProject/#egg=MyProject1", true);
    doTest("-e git+user@git.myproject.org:/path/MyProject.git#egg=MyProject1", true);
    doTest("-e git+user@git.myproject.org:/path/MyProject.git/#egg=MyProject1", true);

    doTest("--editable git://git.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable git://git.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable git://git.myproject.org/MyProject.git#egg=MyProject1", true);
    doTest("--editable git://git.myproject.org/MyProject.git/#egg=MyProject1", true);
    doTest("--editable git://git.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("--editable git://git.myproject.org/path/MyProject/#egg=MyProject1", true);
    doTest("--editable git://git.myproject.org/path/MyProject.git#egg=MyProject1", true);
    doTest("--editable git://git.myproject.org/path/MyProject.git/#egg=MyProject1", true);

    doTest("--editable git+git://git.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable git+git://git.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable git+git://git.myproject.org/MyProject.git#egg=MyProject1", true);
    doTest("--editable git+git://git.myproject.org/MyProject.git/#egg=MyProject1", true);
    doTest("--editable git+git://git.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("--editable git+git://git.myproject.org/path/MyProject/#egg=MyProject1", true);
    doTest("--editable git+git://git.myproject.org/path/MyProject.git#egg=MyProject1", true);
    doTest("--editable git+git://git.myproject.org/path/MyProject.git/#egg=MyProject1", true);

    doTest("--editable git+https://git.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable git+https://git.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable git+https://git.myproject.org/MyProject.git#egg=MyProject1", true);
    doTest("--editable git+https://git.myproject.org/MyProject.git/#egg=MyProject1", true);
    doTest("--editable git+https://git.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("--editable git+https://git.myproject.org/path/MyProject/#egg=MyProject1", true);
    doTest("--editable git+https://git.myproject.org/path/MyProject.git#egg=MyProject1", true);
    doTest("--editable git+https://git.myproject.org/path/MyProject.git/#egg=MyProject1", true);

    doTest("--editable git+ssh://git.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable git+ssh://git.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable git+ssh://git.myproject.org/MyProject.git#egg=MyProject1", true);
    doTest("--editable git+ssh://git.myproject.org/MyProject.git/#egg=MyProject1", true);
    doTest("--editable git+ssh://git.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("--editable git+ssh://git.myproject.org/path/MyProject/#egg=MyProject1", true);
    doTest("--editable git+ssh://git.myproject.org/path/MyProject.git#egg=MyProject1", true);
    doTest("--editable git+ssh://git.myproject.org/path/MyProject.git/#egg=MyProject1", true);

    doTest("--editable git+ssh://user@git.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable git+ssh://user@git.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable git+ssh://user@git.myproject.org/MyProject.git#egg=MyProject1", true);
    doTest("--editable git+ssh://user@git.myproject.org/MyProject.git/#egg=MyProject1", true);
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject/#egg=MyProject1", true);
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject.git#egg=MyProject1", true);
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject.git/#egg=MyProject1", true);

    doTest("--editable git+user@git.myproject.org:MyProject#egg=MyProject1", true);
    doTest("--editable git+user@git.myproject.org:MyProject/#egg=MyProject1", true);
    doTest("--editable git+user@git.myproject.org:MyProject.git#egg=MyProject1", true);
    doTest("--editable git+user@git.myproject.org:MyProject.git/#egg=MyProject1", true);
    doTest("--editable git+user@git.myproject.org:/path/MyProject#egg=MyProject1", true);
    doTest("--editable git+user@git.myproject.org:/path/MyProject/#egg=MyProject1", true);
    doTest("--editable git+user@git.myproject.org:/path/MyProject.git#egg=MyProject1", true);
    doTest("--editable git+user@git.myproject.org:/path/MyProject.git/#egg=MyProject1", true);
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
    doTest("-e git://git.myproject.org/MyProject1", true);
    doTest("-e git://git.myproject.org/MyProject1/", true);
    doTest("-e git://git.myproject.org/MyProject1.git", true);
    doTest("-e git://git.myproject.org/MyProject1.git/", true);
    doTest("-e git://git.myproject.org/path/MyProject1", true);
    doTest("-e git://git.myproject.org/path/MyProject1/", true);
    doTest("-e git://git.myproject.org/path/MyProject1.git", true);
    doTest("-e git://git.myproject.org/path/MyProject1.git/", true);

    doTest("-e git+git://git.myproject.org/MyProject1", true);
    doTest("-e git+git://git.myproject.org/MyProject1/", true);
    doTest("-e git+git://git.myproject.org/MyProject1.git", true);
    doTest("-e git+git://git.myproject.org/MyProject1.git/", true);
    doTest("-e git+git://git.myproject.org/path/MyProject1", true);
    doTest("-e git+git://git.myproject.org/path/MyProject1/", true);
    doTest("-e git+git://git.myproject.org/path/MyProject1.git", true);
    doTest("-e git+git://git.myproject.org/path/MyProject1.git/", true);

    doTest("-e git+https://git.myproject.org/MyProject1", true);
    doTest("-e git+https://git.myproject.org/MyProject1/", true);
    doTest("-e git+https://git.myproject.org/MyProject1.git", true);
    doTest("-e git+https://git.myproject.org/MyProject1.git/", true);
    doTest("-e git+https://git.myproject.org/path/MyProject1", true);
    doTest("-e git+https://git.myproject.org/path/MyProject1/", true);
    doTest("-e git+https://git.myproject.org/path/MyProject1.git", true);
    doTest("-e git+https://git.myproject.org/path/MyProject1.git/", true);

    doTest("-e git+ssh://git.myproject.org/MyProject1", true);
    doTest("-e git+ssh://git.myproject.org/MyProject1/", true);
    doTest("-e git+ssh://git.myproject.org/MyProject1.git", true);
    doTest("-e git+ssh://git.myproject.org/MyProject1.git/", true);
    doTest("-e git+ssh://git.myproject.org/path/MyProject1", true);
    doTest("-e git+ssh://git.myproject.org/path/MyProject1/", true);
    doTest("-e git+ssh://git.myproject.org/path/MyProject1.git", true);
    doTest("-e git+ssh://git.myproject.org/path/MyProject1.git/", true);

    doTest("-e git+ssh://user@git.myproject.org/MyProject1", true);
    doTest("-e git+ssh://user@git.myproject.org/MyProject1/", true);
    doTest("-e git+ssh://user@git.myproject.org/MyProject1.git", true);
    doTest("-e git+ssh://user@git.myproject.org/MyProject1.git/", true);
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject1", true);
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject1/", true);
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject1.git", true);
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject1.git/", true);

    doTest("-e git+user@git.myproject.org:MyProject1", true);
    doTest("-e git+user@git.myproject.org:MyProject1/", true);
    doTest("-e git+user@git.myproject.org:MyProject1.git", true);
    doTest("-e git+user@git.myproject.org:MyProject1.git/", true);
    doTest("-e git+user@git.myproject.org:/path/MyProject1", true);
    doTest("-e git+user@git.myproject.org:/path/MyProject1/", true);
    doTest("-e git+user@git.myproject.org:/path/MyProject1.git", true);
    doTest("-e git+user@git.myproject.org:/path/MyProject1.git/", true);
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
    doTest("-e hg+http://hg.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e hg+http://hg.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e hg+http://hg.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("-e hg+http://hg.myproject.org/path/MyProject/#egg=MyProject1", true);

    doTest("-e hg+https://hg.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e hg+https://hg.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e hg+https://hg.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("-e hg+https://hg.myproject.org/path/MyProject/#egg=MyProject1", true);

    doTest("-e hg+ssh://hg.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e hg+ssh://hg.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e hg+ssh://hg.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("-e hg+ssh://hg.myproject.org/path/MyProject/#egg=MyProject1", true);

    doTest("-e hg+ssh://user@hg.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e hg+ssh://user@hg.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e hg+ssh://user@hg.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("-e hg+ssh://user@hg.myproject.org/path/MyProject/#egg=MyProject1", true);

    doTest("--editable hg+http://hg.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable hg+http://hg.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable hg+http://hg.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("--editable hg+http://hg.myproject.org/path/MyProject/#egg=MyProject1", true);

    doTest("--editable hg+https://hg.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable hg+https://hg.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable hg+https://hg.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("--editable hg+https://hg.myproject.org/path/MyProject/#egg=MyProject1", true);

    doTest("--editable hg+ssh://hg.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable hg+ssh://hg.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable hg+ssh://hg.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("--editable hg+ssh://hg.myproject.org/path/MyProject/#egg=MyProject1", true);

    doTest("--editable hg+ssh://user@hg.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable hg+ssh://user@hg.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable hg+ssh://user@hg.myproject.org/path/MyProject#egg=MyProject1", true);
    doTest("--editable hg+ssh://user@hg.myproject.org/path/MyProject/#egg=MyProject1", true);
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
    doTest("-e svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("-e svn+http://svn.myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("-e svn+http://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1", true);
    doTest("-e svn+http://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1", true);

    doTest("-e svn+https://svn.myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("-e svn+https://svn.myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("-e svn+https://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1", true);
    doTest("-e svn+https://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1", true);

    doTest("-e svn+ssh://svn.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e svn+ssh://svn.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e svn+ssh://svn.myproject.org/svn/MyProject#egg=MyProject1", true);
    doTest("-e svn+ssh://svn.myproject.org/svn/MyProject/#egg=MyProject1", true);

    doTest("-e svn+ssh://user@svn.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e svn+ssh://user@svn.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e svn+ssh://user@svn.myproject.org/svn/MyProject#egg=MyProject1", true);
    doTest("-e svn+ssh://user@svn.myproject.org/svn/MyProject/#egg=MyProject1", true);

    doTest("-e svn+svn://svn.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e svn+svn://svn.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e svn+svn://svn.myproject.org/svn/MyProject#egg=MyProject1", true);
    doTest("-e svn+svn://svn.myproject.org/svn/MyProject/#egg=MyProject1", true);

    doTest("-e svn+svn://user@svn.myproject.org/MyProject#egg=MyProject1", true);
    doTest("-e svn+svn://user@svn.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("-e svn+svn://user@svn.myproject.org/svn/MyProject#egg=MyProject1", true);
    doTest("-e svn+svn://user@svn.myproject.org/svn/MyProject/#egg=MyProject1", true);

    doTest("--editable svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable svn+http://svn.myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("--editable svn+http://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable svn+http://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1", true);

    doTest("--editable svn+https://svn.myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable svn+https://svn.myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("--editable svn+https://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable svn+https://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1", true);

    doTest("--editable svn+ssh://svn.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable svn+ssh://svn.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable svn+ssh://svn.myproject.org/svn/MyProject#egg=MyProject1", true);
    doTest("--editable svn+ssh://svn.myproject.org/svn/MyProject/#egg=MyProject1", true);

    doTest("--editable svn+ssh://user@svn.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable svn+ssh://user@svn.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable svn+ssh://user@svn.myproject.org/svn/MyProject#egg=MyProject1", true);
    doTest("--editable svn+ssh://user@svn.myproject.org/svn/MyProject/#egg=MyProject1", true);

    doTest("--editable svn+svn://svn.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable svn+svn://svn.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable svn+svn://svn.myproject.org/svn/MyProject#egg=MyProject1", true);
    doTest("--editable svn+svn://svn.myproject.org/svn/MyProject/#egg=MyProject1", true);

    doTest("--editable svn+svn://user@svn.myproject.org/MyProject#egg=MyProject1", true);
    doTest("--editable svn+svn://user@svn.myproject.org/MyProject/#egg=MyProject1", true);
    doTest("--editable svn+svn://user@svn.myproject.org/svn/MyProject#egg=MyProject1", true);
    doTest("--editable svn+svn://user@svn.myproject.org/svn/MyProject/#egg=MyProject1", true);
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
    doTest("-e bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+http://bzr.myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("-e bzr+http://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+http://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("-e bzr+https://bzr.myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+https://bzr.myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("-e bzr+https://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+https://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("-e bzr+sftp://myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+sftp://myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("-e bzr+sftp://myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+sftp://myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("-e bzr+sftp://user@myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+sftp://user@myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("-e bzr+sftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+sftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("-e bzr+ssh://myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+ssh://myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("-e bzr+ssh://myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+ssh://myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("-e bzr+ssh://user@myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+ssh://user@myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("-e bzr+ssh://user@myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+ssh://user@myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("-e bzr+ftp://myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+ftp://myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("-e bzr+ftp://myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+ftp://myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("-e bzr+ftp://user@myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+ftp://user@myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("-e bzr+ftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("-e bzr+ftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("-e bzr+lp:MyProject#egg=MyProject1", true);

    doTest("--editable bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+http://bzr.myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("--editable bzr+http://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+http://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("--editable bzr+https://bzr.myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+https://bzr.myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("--editable bzr+https://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+https://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("--editable bzr+sftp://myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+sftp://myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("--editable bzr+sftp://myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+sftp://myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("--editable bzr+sftp://user@myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+sftp://user@myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("--editable bzr+sftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+sftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("--editable bzr+ssh://myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+ssh://myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("--editable bzr+ssh://myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+ssh://myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("--editable bzr+ssh://user@myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+ssh://user@myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("--editable bzr+ssh://user@myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+ssh://user@myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("--editable bzr+ftp://myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+ftp://myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("--editable bzr+ftp://myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+ftp://myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("--editable bzr+ftp://user@myproject.org/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+ftp://user@myproject.org/MyProject/trunk/#egg=MyProject1", true);
    doTest("--editable bzr+ftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1", true);
    doTest("--editable bzr+ftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1", true);

    doTest("--editable bzr+lp:MyProject#egg=MyProject1", true);
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

    assertEquals(new PyRequirement("django-haystack", null, options, false), PyRequirement.fromString(options));
  }

  public void testDevInRequirementEggName() {
    final String options1 = "git://github.com/toastdriven/django-haystack.git#egg=django_haystack-dev";
    assertEquals(new PyRequirement("django-haystack", "dev", options1, true), PyRequirement.fromString(options1));

    final String options2 = "git://github.com/toastdriven/django-haystack.git#egg=django-haystack-dev";
    assertEquals(new PyRequirement("django-haystack", "dev", options2, true), PyRequirement.fromString(options2));
  }

  // LOCAL DIR
  // TODO: which must contain a setup.py

  // LOCAL FILE
  // TODO: a sdist or wheel format archive

  // REQUIREMENT
  // TODO: extras
  // TODO: hashes
  // TODO: multiline
  // TODO: https://pip.pypa.io/en/stable/reference/pip_install/#per-requirement-overrides
  public void testRequirement() {
    assertEquals(new PyRequirement("Django"), PyRequirement.fromString("Django"));
    assertEquals(new PyRequirement("django"), PyRequirement.fromString("django"));
  }

  public void testRequirementWithVersion() {
    assertEquals(new PyRequirement("Django", "1.3.1"), PyRequirement.fromString("Django==1.3.1"));

    assertEquals(new PyRequirement("Django", Collections.singletonList(new PyRequirement.VersionSpec(PyRequirement.Relation.LT, "1.4"))),
                 PyRequirement.fromString("   Django       <   1.4   "));

    assertEquals(new PyRequirement("Django", Arrays.asList(new PyRequirement.VersionSpec(PyRequirement.Relation.LT, "1.4"),
                                                           new PyRequirement.VersionSpec(PyRequirement.Relation.GTE, "1.3.1"))),
                 PyRequirement.fromString("   Django       <   1.4 ,     >= 1.3.1   "));
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
    doTest(options, false);
  }

  private static void doTest(@NotNull String options, boolean editable) {
    assertEquals(new PyRequirement("MyProject1", null, options, editable), PyRequirement.fromString(options));
  }
}
