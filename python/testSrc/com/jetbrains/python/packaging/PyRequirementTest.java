// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.packaging.PyRequirementParser.fromLine;
import static com.jetbrains.python.packaging.PyRequirementsKt.pyRequirement;
import static com.jetbrains.python.packaging.PyRequirementsKt.pyRequirementVersionSpec;
import static com.jetbrains.python.packaging.requirement.PyRequirementRelation.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * @author vlan
 */
public class PyRequirementTest extends PyTestCase {

  // ARCHIVE URL
  public void testArchiveUrl() {
    doTest("geoip2", "2.2.0", "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz");
  }

  // PY-14230
  public void testArchiveUrlWithMd5() {
    doTest("geoip2", "2.2.0", "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#md5=26259d212447bc840400c25a48275fbc");
  }

  // PY-14230
  public void testArchiveUrlWithSha1() {
    doTest("geoip2", "2.2.0", "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha1=26259d212447bc840400c25a48275fbc");
  }

  // PY-14230
  public void testArchiveUrlWithSha224() {
    doTest("geoip2", "2.2.0",
           "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha224=26259d212447bc840400c25a48275fbc");
  }

  // PY-14230
  public void testArchiveUrlWithSha256() {
    doTest("geoip2", "2.2.0",
           "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha256=26259d212447bc840400c25a48275fbc");
  }

  // PY-14230
  public void testArchiveUrlWithSha384() {
    doTest("geoip2", "2.2.0",
           "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha384=26259d212447bc840400c25a48275fbc");
  }

  // PY-14230
  public void testArchiveUrlWithSha512() {
    doTest("geoip2", "2.2.0",
           "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#sha512=26259d212447bc840400c25a48275fbc");
  }

  // PY-18054
  public void testGithubArchiveUrl() {
    doTest("https://github.com/divio/MyProject1/archive/master.zip?1450634746.0107164");
  }

  // PY-26364
  public void testGitlabArchiveUrl() {
    doTest("https://gitlab.com/mrh1997/MyProject1/repository/master/archive.zip");
  }

  // VCS
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

  // PY-19544
  public void testGitWithSubdirectory() {
    doTest("git://git.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git://git.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git://git.myproject.org/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git://git.myproject.org/MyProject.git/#egg=MyProject1&subdirectory=clients/python");
    doTest("git://git.myproject.org/path/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git://git.myproject.org/path/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git://git.myproject.org/path/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git://git.myproject.org/path/MyProject.git/#egg=MyProject1&subdirectory=clients/python");

    doTest("git+git://git.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git+git://git.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+git://git.myproject.org/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git+git://git.myproject.org/MyProject.git/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+git://git.myproject.org/path/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git+git://git.myproject.org/path/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+git://git.myproject.org/path/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git+git://git.myproject.org/path/MyProject.git/#egg=MyProject1&subdirectory=clients/python");

    doTest("git+https://git.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git+https://git.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+https://git.myproject.org/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git+https://git.myproject.org/MyProject.git/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+https://git.myproject.org/path/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git+https://git.myproject.org/path/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+https://git.myproject.org/path/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git+https://git.myproject.org/path/MyProject.git/#egg=MyProject1&subdirectory=clients/python");

    doTest("git+ssh://git.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://git.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://git.myproject.org/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://git.myproject.org/MyProject.git/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://git.myproject.org/path/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://git.myproject.org/path/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://git.myproject.org/path/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://git.myproject.org/path/MyProject.git/#egg=MyProject1&subdirectory=clients/python");

    doTest("git+ssh://user@git.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://user@git.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://user@git.myproject.org/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://user@git.myproject.org/MyProject.git/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://user@git.myproject.org/path/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://user@git.myproject.org/path/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://user@git.myproject.org/path/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git+ssh://user@git.myproject.org/path/MyProject.git/#egg=MyProject1&subdirectory=clients/python");

    doTest("git+user@git.myproject.org:MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git+user@git.myproject.org:MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+user@git.myproject.org:MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git+user@git.myproject.org:MyProject.git/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+user@git.myproject.org:/path/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("git+user@git.myproject.org:/path/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("git+user@git.myproject.org:/path/MyProject.git#egg=MyProject1&subdirectory=clients/python");
    doTest("git+user@git.myproject.org:/path/MyProject.git/#egg=MyProject1&subdirectory=clients/python");

    doTest("git://git.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git://git.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git://git.myproject.org/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git://git.myproject.org/MyProject.git/#subdirectory=clients/python&egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git://git.myproject.org/path/MyProject.git/#subdirectory=clients/python&egg=MyProject1");

    doTest("git+git://git.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git+git://git.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+git://git.myproject.org/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git+git://git.myproject.org/MyProject.git/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git+git://git.myproject.org/path/MyProject.git/#subdirectory=clients/python&egg=MyProject1");

    doTest("git+https://git.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git+https://git.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+https://git.myproject.org/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git+https://git.myproject.org/MyProject.git/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git+https://git.myproject.org/path/MyProject.git/#subdirectory=clients/python&egg=MyProject1");

    doTest("git+ssh://git.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://git.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://git.myproject.org/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://git.myproject.org/MyProject.git/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://git.myproject.org/path/MyProject.git/#subdirectory=clients/python&egg=MyProject1");

    doTest("git+ssh://user@git.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/MyProject.git/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git+ssh://user@git.myproject.org/path/MyProject.git/#subdirectory=clients/python&egg=MyProject1");

    doTest("git+user@git.myproject.org:MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git+user@git.myproject.org:MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+user@git.myproject.org:MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git+user@git.myproject.org:MyProject.git/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject.git#subdirectory=clients/python&egg=MyProject1");
    doTest("git+user@git.myproject.org:/path/MyProject.git/#subdirectory=clients/python&egg=MyProject1");
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

  public void testEditableWithSrcGit() {
    doTest("--src ./mysrc/src -e git://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src -e git+git://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+git://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+git://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+git://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+git://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+git://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+git://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+git://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src -e git+https://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+https://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+https://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+https://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+https://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+https://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+https://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+https://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src -e git+ssh://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src -e git+ssh://user@git.myproject.org/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://user@git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://user@git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://user@git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://user@git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://user@git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://user@git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+ssh://user@git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src -e git+user@git.myproject.org:MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+user@git.myproject.org:MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+user@git.myproject.org:MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+user@git.myproject.org:MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+user@git.myproject.org:/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+user@git.myproject.org:/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+user@git.myproject.org:/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src -e git+user@git.myproject.org:/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src --editable git://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src --editable git+git://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+git://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+git://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+git://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+git://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+git://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+git://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+git://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src --editable git+https://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+https://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+https://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+https://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+https://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+https://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+https://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+https://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src --editable git+ssh://git.myproject.org/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src --editable git+ssh://user@git.myproject.org/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://user@git.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://user@git.myproject.org/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://user@git.myproject.org/MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://user@git.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://user@git.myproject.org/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://user@git.myproject.org/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+ssh://user@git.myproject.org/path/MyProject.git/#egg=MyProject1");

    doTest("--src ./mysrc/src --editable git+user@git.myproject.org:MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+user@git.myproject.org:MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+user@git.myproject.org:MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+user@git.myproject.org:MyProject.git/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+user@git.myproject.org:/path/MyProject#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+user@git.myproject.org:/path/MyProject/#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+user@git.myproject.org:/path/MyProject.git#egg=MyProject1");
    doTest("--src ./mysrc/src --editable git+user@git.myproject.org:/path/MyProject.git/#egg=MyProject1");

    doTest("-e git://git.myproject.org/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git://git.myproject.org/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git://git.myproject.org/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git://git.myproject.org/MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("-e git://git.myproject.org/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git://git.myproject.org/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git://git.myproject.org/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git://git.myproject.org/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("-e git+git://git.myproject.org/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git+git://git.myproject.org/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git+git://git.myproject.org/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git+git://git.myproject.org/MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("-e git+git://git.myproject.org/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git+git://git.myproject.org/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git+git://git.myproject.org/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git+git://git.myproject.org/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("-e git+https://git.myproject.org/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git+https://git.myproject.org/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git+https://git.myproject.org/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git+https://git.myproject.org/MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("-e git+https://git.myproject.org/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git+https://git.myproject.org/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git+https://git.myproject.org/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git+https://git.myproject.org/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("-e git+ssh://git.myproject.org/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://git.myproject.org/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://git.myproject.org/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://git.myproject.org/MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://git.myproject.org/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://git.myproject.org/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://git.myproject.org/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://git.myproject.org/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("-e git+ssh://user@git.myproject.org/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://user@git.myproject.org/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://user@git.myproject.org/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://user@git.myproject.org/MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git+ssh://user@git.myproject.org/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("-e git+user@git.myproject.org:MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git+user@git.myproject.org:MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git+user@git.myproject.org:MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git+user@git.myproject.org:MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("-e git+user@git.myproject.org:/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("-e git+user@git.myproject.org:/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("-e git+user@git.myproject.org:/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("-e git+user@git.myproject.org:/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("--editable git://git.myproject.org/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git://git.myproject.org/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git://git.myproject.org/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git://git.myproject.org/MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("--editable git://git.myproject.org/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git://git.myproject.org/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git://git.myproject.org/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git://git.myproject.org/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("--editable git+git://git.myproject.org/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git+git://git.myproject.org/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git+git://git.myproject.org/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git+git://git.myproject.org/MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("--editable git+git://git.myproject.org/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git+git://git.myproject.org/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git+git://git.myproject.org/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git+git://git.myproject.org/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("--editable git+https://git.myproject.org/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git+https://git.myproject.org/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git+https://git.myproject.org/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git+https://git.myproject.org/MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("--editable git+https://git.myproject.org/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git+https://git.myproject.org/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git+https://git.myproject.org/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git+https://git.myproject.org/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("--editable git+ssh://git.myproject.org/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://git.myproject.org/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://git.myproject.org/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://git.myproject.org/MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://git.myproject.org/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://git.myproject.org/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://git.myproject.org/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://git.myproject.org/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("--editable git+ssh://user@git.myproject.org/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://user@git.myproject.org/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://user@git.myproject.org/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://user@git.myproject.org/MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git+ssh://user@git.myproject.org/path/MyProject.git/#egg=MyProject1 --src mysrc");

    doTest("--editable git+user@git.myproject.org:MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git+user@git.myproject.org:MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git+user@git.myproject.org:MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git+user@git.myproject.org:MyProject.git/#egg=MyProject1 --src mysrc");
    doTest("--editable git+user@git.myproject.org:/path/MyProject#egg=MyProject1 --src mysrc");
    doTest("--editable git+user@git.myproject.org:/path/MyProject/#egg=MyProject1 --src mysrc");
    doTest("--editable git+user@git.myproject.org:/path/MyProject.git#egg=MyProject1 --src mysrc");
    doTest("--editable git+user@git.myproject.org:/path/MyProject.git/#egg=MyProject1 --src mysrc");
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

  // PY-19544
  public void testMercurialWithSubdirectory() {
    doTest("hg+http://hg.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+http://hg.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+http://hg.myproject.org/path/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+http://hg.myproject.org/path/MyProject/#egg=MyProject1&subdirectory=clients/python");

    doTest("hg+https://hg.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+https://hg.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+https://hg.myproject.org/path/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+https://hg.myproject.org/path/MyProject/#egg=MyProject1&subdirectory=clients/python");

    doTest("hg+ssh://hg.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+ssh://hg.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+ssh://hg.myproject.org/path/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+ssh://hg.myproject.org/path/MyProject/#egg=MyProject1&subdirectory=clients/python");

    doTest("hg+ssh://user@hg.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+ssh://user@hg.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject/#egg=MyProject1&subdirectory=clients/python");

    doTest("hg+http://hg.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+http://hg.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+http://hg.myproject.org/path/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+http://hg.myproject.org/path/MyProject/#subdirectory=clients/python&egg=MyProject1");

    doTest("hg+https://hg.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+https://hg.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+https://hg.myproject.org/path/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+https://hg.myproject.org/path/MyProject/#subdirectory=clients/python&egg=MyProject1");

    doTest("hg+ssh://hg.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+ssh://hg.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+ssh://hg.myproject.org/path/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+ssh://hg.myproject.org/path/MyProject/#subdirectory=clients/python&egg=MyProject1");

    doTest("hg+ssh://user@hg.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("hg+ssh://user@hg.myproject.org/path/MyProject/#subdirectory=clients/python&egg=MyProject1");
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

  public void testEditableWithSrcMercurial() {
    doTest("--src mysrc -e hg+http://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc -e hg+http://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc -e hg+http://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src mysrc -e hg+http://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--src mysrc -e hg+https://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc -e hg+https://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc -e hg+https://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src mysrc -e hg+https://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--src mysrc -e hg+ssh://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc -e hg+ssh://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc -e hg+ssh://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src mysrc -e hg+ssh://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--src mysrc -e hg+ssh://user@hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc -e hg+ssh://user@hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc -e hg+ssh://user@hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src mysrc -e hg+ssh://user@hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--src mysrc --editable hg+http://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc --editable hg+http://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc --editable hg+http://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src mysrc --editable hg+http://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--src mysrc --editable hg+https://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc --editable hg+https://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc --editable hg+https://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src mysrc --editable hg+https://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--src mysrc --editable hg+ssh://hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc --editable hg+ssh://hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc --editable hg+ssh://hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src mysrc --editable hg+ssh://hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("--src mysrc --editable hg+ssh://user@hg.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc --editable hg+ssh://user@hg.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc --editable hg+ssh://user@hg.myproject.org/path/MyProject#egg=MyProject1");
    doTest("--src mysrc --editable hg+ssh://user@hg.myproject.org/path/MyProject/#egg=MyProject1");

    doTest("-e hg+http://hg.myproject.org/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+http://hg.myproject.org/MyProject/#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+http://hg.myproject.org/path/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+http://hg.myproject.org/path/MyProject/#egg=MyProject1 --src ./mysrc");

    doTest("-e hg+https://hg.myproject.org/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+https://hg.myproject.org/MyProject/#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+https://hg.myproject.org/path/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+https://hg.myproject.org/path/MyProject/#egg=MyProject1 --src ./mysrc");

    doTest("-e hg+ssh://hg.myproject.org/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+ssh://hg.myproject.org/MyProject/#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+ssh://hg.myproject.org/path/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+ssh://hg.myproject.org/path/MyProject/#egg=MyProject1 --src ./mysrc");

    doTest("-e hg+ssh://user@hg.myproject.org/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+ssh://user@hg.myproject.org/MyProject/#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+ssh://user@hg.myproject.org/path/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("-e hg+ssh://user@hg.myproject.org/path/MyProject/#egg=MyProject1 --src ./mysrc");

    doTest("--editable hg+http://hg.myproject.org/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+http://hg.myproject.org/MyProject/#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+http://hg.myproject.org/path/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+http://hg.myproject.org/path/MyProject/#egg=MyProject1 --src ./mysrc");

    doTest("--editable hg+https://hg.myproject.org/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+https://hg.myproject.org/MyProject/#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+https://hg.myproject.org/path/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+https://hg.myproject.org/path/MyProject/#egg=MyProject1 --src ./mysrc");

    doTest("--editable hg+ssh://hg.myproject.org/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+ssh://hg.myproject.org/MyProject/#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+ssh://hg.myproject.org/path/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+ssh://hg.myproject.org/path/MyProject/#egg=MyProject1 --src ./mysrc");

    doTest("--editable hg+ssh://user@hg.myproject.org/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+ssh://user@hg.myproject.org/MyProject/#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+ssh://user@hg.myproject.org/path/MyProject#egg=MyProject1 --src ./mysrc");
    doTest("--editable hg+ssh://user@hg.myproject.org/path/MyProject/#egg=MyProject1 --src ./mysrc");
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

  // PY-19544
  public void testSubversionWithSubdirectory() {
    doTest("svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+http://svn.myproject.org/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+http://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+http://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");

    doTest("svn+https://svn.myproject.org/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+https://svn.myproject.org/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+https://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+https://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");

    doTest("svn+ssh://svn.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+ssh://svn.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject/#egg=MyProject1&subdirectory=clients/python");

    doTest("svn+ssh://user@svn.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+ssh://user@svn.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject/#egg=MyProject1&subdirectory=clients/python");

    doTest("svn+svn://svn.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+svn://svn.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+svn://svn.myproject.org/svn/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+svn://svn.myproject.org/svn/MyProject/#egg=MyProject1&subdirectory=clients/python");

    doTest("svn+svn://user@svn.myproject.org/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+svn://user@svn.myproject.org/MyProject/#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject#egg=MyProject1&subdirectory=clients/python");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject/#egg=MyProject1&subdirectory=clients/python");

    doTest("svn+http://svn.myproject.org/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+http://svn.myproject.org/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+http://svn.myproject.org/svn/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+http://svn.myproject.org/svn/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");

    doTest("svn+https://svn.myproject.org/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+https://svn.myproject.org/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+https://svn.myproject.org/svn/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+https://svn.myproject.org/svn/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");

    doTest("svn+ssh://svn.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+ssh://svn.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+ssh://svn.myproject.org/svn/MyProject/#subdirectory=clients/python&egg=MyProject1");

    doTest("svn+ssh://user@svn.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+ssh://user@svn.myproject.org/svn/MyProject/#subdirectory=clients/python&egg=MyProject1");

    doTest("svn+svn://svn.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+svn://svn.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+svn://svn.myproject.org/svn/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+svn://svn.myproject.org/svn/MyProject/#subdirectory=clients/python&egg=MyProject1");

    doTest("svn+svn://user@svn.myproject.org/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+svn://user@svn.myproject.org/MyProject/#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject#subdirectory=clients/python&egg=MyProject1");
    doTest("svn+svn://user@svn.myproject.org/svn/MyProject/#subdirectory=clients/python&egg=MyProject1");
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

  public void testEditableWithSrcSubversion() {
    doTest("--src mysrc/src -e svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src mysrc/src -e svn+http://svn.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src mysrc/src -e svn+http://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1");
    doTest("--src mysrc/src -e svn+http://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1");

    doTest("--src mysrc/src -e svn+https://svn.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src mysrc/src -e svn+https://svn.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src mysrc/src -e svn+https://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1");
    doTest("--src mysrc/src -e svn+https://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1");

    doTest("--src mysrc/src -e svn+ssh://svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc/src -e svn+ssh://svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc/src -e svn+ssh://svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--src mysrc/src -e svn+ssh://svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--src mysrc/src -e svn+ssh://user@svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc/src -e svn+ssh://user@svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc/src -e svn+ssh://user@svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--src mysrc/src -e svn+ssh://user@svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--src mysrc/src -e svn+svn://svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc/src -e svn+svn://svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc/src -e svn+svn://svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--src mysrc/src -e svn+svn://svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--src mysrc/src -e svn+svn://user@svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc/src -e svn+svn://user@svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc/src -e svn+svn://user@svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--src mysrc/src -e svn+svn://user@svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--src mysrc/src --editable svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+http://svn.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+http://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+http://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1");

    doTest("--src mysrc/src --editable svn+https://svn.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+https://svn.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+https://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+https://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1");

    doTest("--src mysrc/src --editable svn+ssh://svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+ssh://svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+ssh://svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+ssh://svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--src mysrc/src --editable svn+ssh://user@svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+ssh://user@svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+ssh://user@svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+ssh://user@svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--src mysrc/src --editable svn+svn://svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+svn://svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+svn://svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+svn://svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("--src mysrc/src --editable svn+svn://user@svn.myproject.org/MyProject#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+svn://user@svn.myproject.org/MyProject/#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+svn://user@svn.myproject.org/svn/MyProject#egg=MyProject1");
    doTest("--src mysrc/src --editable svn+svn://user@svn.myproject.org/svn/MyProject/#egg=MyProject1");

    doTest("-e svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+http://svn.myproject.org/MyProject/trunk/#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+http://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+http://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1 --src mysrc/src");

    doTest("-e svn+https://svn.myproject.org/MyProject/trunk#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+https://svn.myproject.org/MyProject/trunk/#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+https://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+https://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1 --src mysrc/src");

    doTest("-e svn+ssh://svn.myproject.org/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+ssh://svn.myproject.org/MyProject/#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+ssh://svn.myproject.org/svn/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+ssh://svn.myproject.org/svn/MyProject/#egg=MyProject1 --src mysrc/src");

    doTest("-e svn+ssh://user@svn.myproject.org/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+ssh://user@svn.myproject.org/MyProject/#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+ssh://user@svn.myproject.org/svn/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+ssh://user@svn.myproject.org/svn/MyProject/#egg=MyProject1 --src mysrc/src");

    doTest("-e svn+svn://svn.myproject.org/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+svn://svn.myproject.org/MyProject/#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+svn://svn.myproject.org/svn/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+svn://svn.myproject.org/svn/MyProject/#egg=MyProject1 --src mysrc/src");

    doTest("-e svn+svn://user@svn.myproject.org/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+svn://user@svn.myproject.org/MyProject/#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+svn://user@svn.myproject.org/svn/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("-e svn+svn://user@svn.myproject.org/svn/MyProject/#egg=MyProject1 --src mysrc/src");

    doTest("--editable svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+http://svn.myproject.org/MyProject/trunk/#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+http://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+http://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1 --src mysrc/src");

    doTest("--editable svn+https://svn.myproject.org/MyProject/trunk#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+https://svn.myproject.org/MyProject/trunk/#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+https://svn.myproject.org/svn/MyProject/trunk#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+https://svn.myproject.org/svn/MyProject/trunk/#egg=MyProject1 --src mysrc/src");

    doTest("--editable svn+ssh://svn.myproject.org/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+ssh://svn.myproject.org/MyProject/#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+ssh://svn.myproject.org/svn/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+ssh://svn.myproject.org/svn/MyProject/#egg=MyProject1 --src mysrc/src");

    doTest("--editable svn+ssh://user@svn.myproject.org/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+ssh://user@svn.myproject.org/MyProject/#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+ssh://user@svn.myproject.org/svn/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+ssh://user@svn.myproject.org/svn/MyProject/#egg=MyProject1 --src mysrc/src");

    doTest("--editable svn+svn://svn.myproject.org/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+svn://svn.myproject.org/MyProject/#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+svn://svn.myproject.org/svn/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+svn://svn.myproject.org/svn/MyProject/#egg=MyProject1 --src mysrc/src");

    doTest("--editable svn+svn://user@svn.myproject.org/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+svn://user@svn.myproject.org/MyProject/#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+svn://user@svn.myproject.org/svn/MyProject#egg=MyProject1 --src mysrc/src");
    doTest("--editable svn+svn://user@svn.myproject.org/svn/MyProject/#egg=MyProject1 --src mysrc/src");
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

  // PY-19544
  public void testBazaarWithSubdirectory() {
    doTest("bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+http://bzr.myproject.org/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+http://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+http://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");

    doTest("bzr+https://bzr.myproject.org/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+https://bzr.myproject.org/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+https://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+https://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");

    doTest("bzr+sftp://myproject.org/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+sftp://myproject.org/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+sftp://myproject.org/path/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+sftp://myproject.org/path/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");

    doTest("bzr+sftp://user@myproject.org/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+sftp://user@myproject.org/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+sftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+sftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");

    doTest("bzr+ssh://myproject.org/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ssh://myproject.org/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ssh://myproject.org/path/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ssh://myproject.org/path/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");

    doTest("bzr+ssh://user@myproject.org/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ssh://user@myproject.org/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ssh://user@myproject.org/path/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ssh://user@myproject.org/path/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");

    doTest("bzr+ftp://myproject.org/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ftp://myproject.org/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ftp://myproject.org/path/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ftp://myproject.org/path/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");

    doTest("bzr+ftp://user@myproject.org/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ftp://user@myproject.org/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1&subdirectory=clients/python");
    doTest("bzr+ftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1&subdirectory=clients/python");

    doTest("bzr+lp:MyProject#egg=MyProject1&subdirectory=clients/python");

    doTest("bzr+http://bzr.myproject.org/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+http://bzr.myproject.org/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+http://bzr.myproject.org/path/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+http://bzr.myproject.org/path/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");

    doTest("bzr+https://bzr.myproject.org/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+https://bzr.myproject.org/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+https://bzr.myproject.org/path/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+https://bzr.myproject.org/path/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");

    doTest("bzr+sftp://myproject.org/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+sftp://myproject.org/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+sftp://myproject.org/path/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+sftp://myproject.org/path/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");

    doTest("bzr+sftp://user@myproject.org/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+sftp://user@myproject.org/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+sftp://user@myproject.org/path/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+sftp://user@myproject.org/path/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");

    doTest("bzr+ssh://myproject.org/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ssh://myproject.org/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ssh://myproject.org/path/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ssh://myproject.org/path/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");

    doTest("bzr+ssh://user@myproject.org/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ssh://user@myproject.org/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ssh://user@myproject.org/path/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ssh://user@myproject.org/path/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");

    doTest("bzr+ftp://myproject.org/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ftp://myproject.org/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ftp://myproject.org/path/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ftp://myproject.org/path/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");

    doTest("bzr+ftp://user@myproject.org/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ftp://user@myproject.org/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ftp://user@myproject.org/path/MyProject/trunk#subdirectory=clients/python&egg=MyProject1");
    doTest("bzr+ftp://user@myproject.org/path/MyProject/trunk/#subdirectory=clients/python&egg=MyProject1");

    doTest("bzr+lp:MyProject#subdirectory=clients/python&egg=MyProject1");
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

  public void testEditableWithSrcBazaar() {
    doTest("--src . -e bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+http://bzr.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . -e bzr+http://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+http://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . -e bzr+https://bzr.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+https://bzr.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . -e bzr+https://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+https://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . -e bzr+sftp://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+sftp://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . -e bzr+sftp://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+sftp://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . -e bzr+sftp://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+sftp://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . -e bzr+sftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+sftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . -e bzr+ssh://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+ssh://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . -e bzr+ssh://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+ssh://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . -e bzr+ssh://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+ssh://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . -e bzr+ssh://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+ssh://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . -e bzr+ftp://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+ftp://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . -e bzr+ftp://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+ftp://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . -e bzr+ftp://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+ftp://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . -e bzr+ftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . -e bzr+ftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . -e bzr+lp:MyProject#egg=MyProject1");

    doTest("--src . --editable bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+http://bzr.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . --editable bzr+http://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+http://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . --editable bzr+https://bzr.myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+https://bzr.myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . --editable bzr+https://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+https://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . --editable bzr+sftp://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+sftp://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . --editable bzr+sftp://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+sftp://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . --editable bzr+sftp://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+sftp://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . --editable bzr+sftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+sftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . --editable bzr+ssh://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+ssh://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . --editable bzr+ssh://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+ssh://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . --editable bzr+ssh://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+ssh://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . --editable bzr+ssh://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+ssh://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . --editable bzr+ftp://myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+ftp://myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . --editable bzr+ftp://myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+ftp://myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . --editable bzr+ftp://user@myproject.org/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+ftp://user@myproject.org/MyProject/trunk/#egg=MyProject1");
    doTest("--src . --editable bzr+ftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1");
    doTest("--src . --editable bzr+ftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1");

    doTest("--src . --editable bzr+lp:MyProject#egg=MyProject1");

    doTest("-e bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+http://bzr.myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("-e bzr+http://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+http://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("-e bzr+https://bzr.myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+https://bzr.myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("-e bzr+https://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+https://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("-e bzr+sftp://myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+sftp://myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("-e bzr+sftp://myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+sftp://myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("-e bzr+sftp://user@myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+sftp://user@myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("-e bzr+sftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+sftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("-e bzr+ssh://myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+ssh://myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("-e bzr+ssh://myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+ssh://myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("-e bzr+ssh://user@myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+ssh://user@myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("-e bzr+ssh://user@myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+ssh://user@myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("-e bzr+ftp://myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+ftp://myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("-e bzr+ftp://myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+ftp://myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("-e bzr+ftp://user@myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+ftp://user@myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("-e bzr+ftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("-e bzr+ftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("-e bzr+lp:MyProject#egg=MyProject1 --src .");

    doTest("--editable bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+http://bzr.myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("--editable bzr+http://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+http://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("--editable bzr+https://bzr.myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+https://bzr.myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("--editable bzr+https://bzr.myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+https://bzr.myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("--editable bzr+sftp://myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+sftp://myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("--editable bzr+sftp://myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+sftp://myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("--editable bzr+sftp://user@myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+sftp://user@myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("--editable bzr+sftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+sftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("--editable bzr+ssh://myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+ssh://myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("--editable bzr+ssh://myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+ssh://myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("--editable bzr+ssh://user@myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+ssh://user@myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("--editable bzr+ssh://user@myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+ssh://user@myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("--editable bzr+ftp://myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+ftp://myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("--editable bzr+ftp://myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+ftp://myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("--editable bzr+ftp://user@myproject.org/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+ftp://user@myproject.org/MyProject/trunk/#egg=MyProject1 --src .");
    doTest("--editable bzr+ftp://user@myproject.org/path/MyProject/trunk#egg=MyProject1 --src .");
    doTest("--editable bzr+ftp://user@myproject.org/path/MyProject/trunk/#egg=MyProject1 --src .");

    doTest("--editable bzr+lp:MyProject#egg=MyProject1 --src .");
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
    final String line = "git://github.com/toastdriven/django-haystack.git#egg=django-haystack";

    assertEquals(new PyRequirementImpl("django-haystack", emptyList(), singletonList(line), ""), fromLine(line));
  }

  public void testDevInRequirementEggName() {
    doTest("django-haystack", "dev", "git://github.com/toastdriven/django-haystack.git#egg=django_haystack-dev");
    doTest("django-haystack", "dev", "git://github.com/toastdriven/django-haystack.git#egg=django_haystack-dev");
  }

  // PY-26844
  public void testExtrasInRequirementEggName() {
    final String line1 = "git://github.com/python-social-auth/social-core.git#egg=social-auth-core[openidconnect]";
    assertEquals(new PyRequirementImpl("social-auth-core", emptyList(), singletonList(line1), "[openidconnect]"), fromLine(line1));

    final String line2 = "git://github.com/python-social-auth/social-core.git#egg=social-auth-core[openidconnect,security]";
    assertEquals(new PyRequirementImpl("social-auth-core", emptyList(), singletonList(line2), "[openidconnect,security]"), fromLine(line2));

    final String line3 =
      "git://github.com/python-social-auth/social-core.git#egg=social-auth-core[openidconnect]&subdirectory=clients/python";
    assertEquals(new PyRequirementImpl("social-auth-core", emptyList(), singletonList(line3), "[openidconnect]"), fromLine(line3));

    final String line4 =
      "git://github.com/python-social-auth/social-core.git#egg=social-auth-core[openidconnect,security]&subdirectory=clients/python";
    assertEquals(new PyRequirementImpl("social-auth-core", emptyList(), singletonList(line4), "[openidconnect,security]"), fromLine(line4));

    final String line5 =
      "git://github.com/python-social-auth/social-core.git#subdirectory=clients/python&egg=social-auth-core[openidconnect]";
    assertEquals(new PyRequirementImpl("social-auth-core", emptyList(), singletonList(line5), "[openidconnect]"), fromLine(line5));

    final String line6 =
      "git://github.com/python-social-auth/social-core.git#subdirectory=clients/python&egg=social-auth-core[openidconnect,security]";
    assertEquals(new PyRequirementImpl("social-auth-core", emptyList(), singletonList(line6), "[openidconnect,security]"), fromLine(line6));
  }

  // LOCAL DIR
  // TODO: which must contain a setup.py

  // LOCAL FILE
  // TODO: a sdist or wheel format archive

  // REQUIREMENT
  // TODO: name normalization
  // TODO: hashes
  // https://www.python.org/dev/peps/pep-0508/#names
  public void testRequirement() {
    assertEquals(pyRequirement("Orange-Bioinformatics"), fromLine("Orange-Bioinformatics"));
    assertEquals(pyRequirement("MOCPy"), fromLine("MOCPy"));
    assertEquals(pyRequirement("score.webassets"), fromLine("score.webassets"));
    assertEquals(pyRequirement("pip_helpers"), fromLine("pip_helpers"));
    assertEquals(pyRequirement("Django"), fromLine("Django"));
    assertEquals(pyRequirement("django"), fromLine("django"));
    assertEquals(pyRequirement("pinax-utils"), fromLine("pinax-utils"));
    assertEquals(pyRequirement("no_limit_nester"), fromLine("no_limit_nester"));
    assertEquals(pyRequirement("Flask-Celery-py3"), fromLine("Flask-Celery-py3"));
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
    final List<PyRequirementVersionSpec> versionSpecs = singletonList(pyRequirementVersionSpec(STR_EQ, version));

    assertEquals(new PyRequirementImpl(name, versionSpecs, singletonList(line), ""), fromLine(line));
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
        StringUtil.trimLeading(extras2)
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

    final List<PyRequirementVersionSpec> versionSpecs = singletonList(pyRequirementVersionSpec(GTE, release(version)));

    final List<String> installOptions1 = Arrays.asList(linePrefix,
                                                       "--global-option", "--no-user-cfg",
                                                       "--install-option", "--prefix='/usr/local'",
                                                       "--install-option", "--no-compile");
    final String line1 = linePrefix + " " +
                         "--global-option=\"--no-user-cfg\" " +
                         "--install-option=\"--prefix='/usr/local'\" " +
                         "--install-option=\"--no-compile\"";
    assertEquals(new PyRequirementImpl(name, versionSpecs, installOptions1, ""), fromLine(line1));

    final List<String> installOptions2 = Arrays.asList(linePrefix, "--install-option", "--install-scripts=/usr/local/bin");
    final String line2 = linePrefix + " --install-option=\"--install-scripts=/usr/local/bin\"";
    assertEquals(new PyRequirementImpl(name, versionSpecs, installOptions2, ""), fromLine(line2));
  }

  public void testMultilineRequirement() {
    final String name = "MyProject1";
    final String version = "1.2";
    final String textPrefix = name + " >= " + version;

    final List<PyRequirementVersionSpec> versionSpecs = singletonList(pyRequirementVersionSpec(GTE, release(version)));

    final String text = textPrefix + " " +
                        "--global-option=\"--no-user-cfg\" \\\n" +
                        "--install-option=\"--prefix='/usr/local'\" \\\n" +
                        "--install-option=\"--no-compile\"";

    final List<String> installOptions = Arrays.asList(textPrefix,
                                                      "--global-option", "--no-user-cfg",
                                                      "--install-option", "--prefix='/usr/local'",
                                                      "--install-option", "--no-compile");

    assertEquals(singletonList(new PyRequirementImpl(name, versionSpecs, installOptions, "")), PyRequirementParser.fromText(text));
  }

  // PY-6355
  public void testTrailingZeroesInVersion() {
    final PyRequirement req = fromLine("foo==0.8.0");
    final PyPackage pkg = new PyPackage("foo", "0.8", null, emptyList());
    assertNotNull(req);
    assertEquals(pkg, req.match(singletonList(pkg)));
  }

  // PY-6438
  public void testUnderscoreMatchesDash() {
    final PyRequirement req = fromLine("pyramid_zcml");
    final PyPackage pkg = new PyPackage("pyramid-zcml", "0.1", null, emptyList());
    assertNotNull(req);
    assertEquals(pkg, req.match(singletonList(pkg)));
  }

  // PY-20242
  public void testVersionInterpretedAsString() {
    final PyRequirement req = fromLine("foo===version");
    final PyPackage pkg = new PyPackage("foo", "version", null, emptyList());
    assertNotNull(req);
    assertEquals(pkg, req.match(singletonList(pkg)));
  }

  // PY-20880
  public void testMatchingLocalVersions() {
    final PyPackage firstPackageWithLocalVersion = new PyPackage("foo", "1.0+foo0100", null, emptyList());
    final PyPackage secondPackageWithLocalVersion = new PyPackage("foo", "1.0+foo0101", null, emptyList());

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
    final PyPackage release = new PyPackage("foo", "1.1.2", null, emptyList());
    final PyPackage pre = new PyPackage("foo", "1.1.2a1", null, emptyList());
    final PyPackage post = new PyPackage("foo", "1.1.2.post1", null, emptyList());
    final PyPackage dev = new PyPackage("foo", "1.1.2.dev1", null, emptyList());
    final PyPackage localVersion = new PyPackage("foo", "1.1.2+local.version", null, emptyList());

    assertEquals(release, requirement.match(singletonList(release)));
    assertEquals(pre, requirement.match(singletonList(pre)));
    assertEquals(post, requirement.match(singletonList(post)));
    assertEquals(dev, requirement.match(singletonList(dev)));
    assertEquals(localVersion, requirement.match(singletonList(localVersion)));

    final PyRequirement negativeRequirement = fromLine("foo!=1.1.*");
    final PyPackage negativeRelease = new PyPackage("foo", "1.2.2", null, emptyList());
    final PyPackage negativePre = new PyPackage("foo", "1.2.2a1", null, emptyList());
    final PyPackage negativePost = new PyPackage("foo", "1.2.2.post1", null, emptyList());
    final PyPackage negativeDev = new PyPackage("foo", "1.2.2.dev1", null, emptyList());
    final PyPackage negativeLocalVersion = new PyPackage("foo", "1.2.2+local.version", null, emptyList());

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
    final PyPackage release = new PyPackage("foo", "2.3", null, emptyList());
    final PyPackage pre = new PyPackage("foo", "2.3a1", null, emptyList());
    final PyPackage post = new PyPackage("foo", "2.3.post1", null, emptyList());
    final PyPackage dev = new PyPackage("foo", "2.3.dev1", null, emptyList());
    final PyPackage localVersion = new PyPackage("foo", "2.3+local.version", null, emptyList());

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
    final PyPackage release = new PyPackage("foo", "2.20.3", null, emptyList());
    final PyPackage pre = new PyPackage("foo", "2.20.3a1", null, emptyList());
    final PyPackage post = new PyPackage("foo", "2.20.3.post1", null, emptyList());
    final PyPackage dev = new PyPackage("foo", "2.20.3.dev1", null, emptyList());
    final PyPackage localVersion = new PyPackage("foo", "2.20.3+local.version", null, emptyList());

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

    final PyPackage pkg = new PyPackage("social-auth-app-django", "2.0.0", null, emptyList());

    assertEquals(pkg, requirement1.match(singletonList(pkg)));
    assertEquals(pkg, requirement2.match(singletonList(pkg)));
  }

  // OPTIONS
  public void testOptions() {
    assertEmpty(
      PyRequirementParser.fromText(
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

    assertEquals(Arrays.asList(pyRequirement("bitly_api"), pyRequirement("numpy"), pyRequirement("SomeProject")),
                 PyRequirementParser.fromFile(requirementsFile));
  }

  // COMMENTS
  public void testComment() {
    assertNull(fromLine("# comment"));
  }

  public void testCommentAtTheEnd() {
    // ARCHIVE
    doCommentAtTheEndTest("geoip2", "2.2.0", "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz # comment");

    doCommentAtTheEndTest("geoip2", "2.2.0",
                          "https://pypi.python.org/packages/source/g/geoip2/geoip2-2.2.0.tar.gz#md5=26259d212447bc840400c25a48275fbc # comment");

    doCommentAtTheEndTest("https://github.com/divio/MyProject1/archive/master.zip?1450634746.0107164 # comment");

    // VCS
    doCommentAtTheEndTest("git://git.myproject.org/MyProject#egg=MyProject1 # comment");
    doCommentAtTheEndTest("-e git://git.myproject.org/MyProject1 # comment");

    doCommentAtTheEndTest("hg+http://hg.myproject.org/MyProject#egg=MyProject1 # comment");
    doCommentAtTheEndTest("hg+http://hg.myproject.org/MyProject1 # comment");

    doCommentAtTheEndTest("svn+http://svn.myproject.org/MyProject/trunk#egg=MyProject1 # comment");
    doCommentAtTheEndTest("svn+http://svn.myproject.org/MyProject1/trunk # comment");

    doCommentAtTheEndTest("bzr+http://bzr.myproject.org/MyProject/trunk#egg=MyProject1 # comment");
    doCommentAtTheEndTest("bzr+http://bzr.myproject.org/MyProject1/trunk # comment");

    // REQUIREMENT
    final String name = "MyProject1";
    final String version = "2.5a20";

    doCommentAtTheEndTest(name + " # comment");
    doCommentAtTheEndTest(name, version, name + "==" + version + " # comment");

    assertEquals(new PyRequirementImpl(name, emptyList(), singletonList(name + "[PDF]"), "[PDF]"),
                 fromLine(name + "[PDF] # comment"));

    final PyRequirement requirement = new PyRequirementImpl(name, emptyList(), Arrays.asList(name, "--install-option", "option"), "");

    assertEquals(requirement, fromLine(name + " --install-option=\"option\" # comment"));
    assertEquals(singletonList(requirement), PyRequirementParser.fromText(name + " \\\n--install-option=\"option\" # comment"));
  }

  // ENV MARKERS
  // TODO: https://www.python.org/dev/peps/pep-0426/#environment-markers, https://www.python.org/dev/peps/pep-0508/#environment-markers

  private static void doTest(@NotNull String line) {
    assertEquals(new PyRequirementImpl("MyProject1", emptyList(), Arrays.asList(line.split("\\s+")), ""), fromLine(line));
  }

  private static void doTest(@NotNull String name, @NotNull String version, @NotNull String line) {
    final PyRequirementVersionSpec versionSpec = pyRequirementVersionSpec(EQ, version);
    assertEquals(new PyRequirementImpl(name, singletonList(versionSpec), singletonList(line), ""), fromLine(line));
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
      assertEquals(new PyRequirementImpl(name, expectedVersionSpecs, singletonList(options), ""), fromLine(options));
    }
    else {
      assertEquals(new PyRequirementImpl(name, expectedVersionSpecs, singletonList(options), StringUtil.trimLeading(extras)),
                   fromLine(options));
    }
  }

  @NotNull
  private static PyPackageVersion release(@NotNull String version) {
    return new PyPackageVersion(null, version, null, null, null, null);
  }
}
