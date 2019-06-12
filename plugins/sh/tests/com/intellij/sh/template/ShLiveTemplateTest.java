// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.template;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class ShLiveTemplateTest extends LightCodeInsightFixtureTestCase {

  public void testForiExpression() {
    doTest("fori<caret>", "for i in {1..5} ; do\n    \ndone");
  }

  public void testCmd() {
    doTest("cmd<caret>", "`command`");
  }

  public void testCmdCheckResult() {
    doTest("cmd success check<caret>", "if [[ $? == 0 ]]; then\n    echo \"Succeed\"\nelse\n    echo \"Failed\"\nfi");
  }

  public void testTarCompress() {
    doTest("tar compress<caret>", "tar -czvf /path/to/archive.tar.gz /path/to/directory");
  }

  public void testTarDecompress() {
    doTest("tar decompress<caret>", "tar -C /extract/to/path -xzvf /path/to/archive.tar.gz");
  }

  public void testMkdir() {
    doTest("mkdir<caret>", "mkdir \"dirname\"");
  }

  public void testGitBranchCreate() {
    doTest("git branch create<caret>", "git checkout -b branch_name");
  }

  public void testGitPush() {
    doTest("git push<caret>", "git push origin branch_name");
  }

  public void testGitCommit() {
    doTest("git commit<caret>", "git commit -m \"commit_message\"");
  }

  public void testCurl() {
    doTest("curl<caret>", "curl --request GET -sL \\\n     --url 'http://example.com'\\\n     --output './path/to/file'");
  }

  public void testRm() {
    doTest("rm<caret>", "rm -f ./path/file");
  }

  public void testFind() {
    doTest("find<caret>", "find ./path -type f -name \"file_name\"");
  }

  public void testXargs() {
    doTest("xargs<caret>", " | xargs command");
  }

  public void testHeredoc() {
    doTest("heredoc<caret>", "<<EOF\n    text\nEOF");
  }

  public void testKernelInfo() {
    doTest("system kernel info<caret>", "uname -a");
  }

  public void testLinuxInfo() {
    doTest("system info linux<caret>", "lsb_release -a");
  }

  public void testMacOSInfo() {
    doTest("system info mac<caret>", "sw_vers");
  }

  private void doTest(String actual, String expected) {
    myFixture.configureByText("a.sh", actual);
    final Editor editor = myFixture.getEditor();
    final Project project = editor.getProject();
    assertNotNull(project);
    new ListTemplatesAction().actionPerformedImpl(project, editor);
    final LookupImpl lookup = (LookupImpl) LookupManager.getActiveLookup(editor);
    assertNotNull(lookup);
    lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    TemplateState template = TemplateManagerImpl.getTemplateState(editor);
    if (template != null) {
      Disposer.dispose(template);
    }
    myFixture.checkResult(expected);
  }
}