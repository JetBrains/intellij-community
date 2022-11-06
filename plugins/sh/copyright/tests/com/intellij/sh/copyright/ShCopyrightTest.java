// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.copyright;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.psi.UpdateCopyright;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightFactory;

public class ShCopyrightTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/copyright/testData/copyright/";
  }

  public void testEmpty() throws Exception { doTest(); }
  public void testWithShebang() throws Exception { doTest(); }
  public void testWithoutShebang() throws Exception { doTest(); }
  public void testEmptyWithCopyright() throws Exception { doTest(); }
  public void testWithShebangWithComment() throws Exception { doTest(); }
  public void testWithShebangWithLinefeed() throws Exception { doTest(); }
  public void testWithShebangEditedCopyright() throws Exception { doTest(); }
  public void testWithoutShebangWithLinefeed() throws Exception { doTest(); }
  public void testWithoutShebangEditedCopyright() throws Exception { doTest(); }

  private void doTest() throws Exception {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".sh");

    CopyrightProfile profile = new CopyrightProfile();
    profile.setNotice("Copyright notice\nOver multiple lines");
    UpdateCopyright updateCopyright = UpdateCopyrightFactory.createUpdateCopyright(getProject(), getModule(), myFixture.getFile(), profile);
    updateCopyright.prepare();
    updateCopyright.complete();

    myFixture.checkResultByFile(testName + ".after.sh");
  }
}