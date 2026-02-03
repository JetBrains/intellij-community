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
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ShArrayLiveTemplateTest extends BasePlatformTestCase {

  public void testArrayCreate() {
    doTest("array create<caret>", "myArray=(item1 item2 item3)\n");
  }
  public void testArrayAll() {
    doTest("array all<caret>", "${myArray[@]}\n");
  }
  public void testArrayAtIndex() {
    doTest("array at index<caret>", "${myArray[index]}\n");
  }
  public void testArrayLength() {
    doTest("array length<caret>", "${#myArray[@]}\n");
  }
  public void testArrayDelete() {
    doTest("array delete<caret>", "unset myArray\n");
  }
  public void testArrayDeleteAt() {
    doTest("array delete at<caret>", "unset myArray[index]\n");
  }
  public void testArraySetElement() {
    doTest("array set element<caret>", "myArray[index]=value\n");
  }
  public void testArrayIteration() {
    doTest("array iteration<caret>", "for item in ${myArray[@]}; do\n    echo \"$item\"\ndone\n");
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