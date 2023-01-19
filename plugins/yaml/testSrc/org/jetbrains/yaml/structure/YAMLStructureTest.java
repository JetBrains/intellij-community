// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.structure;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.yaml.structureView.YAMLAliasResolveNodeProvider;

import javax.swing.tree.TreePath;

public class YAMLStructureTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/structure/data/";
  }


  public void testAnchorsAndAliases() {
    doTest();
  }

  private void doTest() {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".yml");
    doStructureTest(testName, false);
    doStructureTest(testName, true);
  }

  private void doStructureTest(String testName, boolean resolveAliases) {
    myFixture.testStructureView(svc -> {
      svc.setActionActive(YAMLAliasResolveNodeProvider.ID, resolveAliases);
      PlatformTestUtil.expandAll(svc.getTree());
      PlatformTestUtil.waitForPromise(svc.select(svc.getTreeModel().getCurrentEditorElement(), false));
      TreePath leadPath = svc.getTree().getLeadSelectionPath();
      String print = PlatformTestUtil.print(svc.getTree(), false);

      String suffix = resolveAliases ? "resolved" : "unresolved";
      assertSameLinesWithFile(getTestDataPath() + testName + "." + suffix + ".txt",
                              print + "\n\nLeadPath: " + leadPath);
    });
  }
}
