/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xml.structureView;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.smartTree.TreeStructureUtil;
import com.intellij.lang.html.structureView.Html5SectionsNodeProvider;
import com.intellij.testFramework.FileStructureTestBase;

public class HtmlFileStructureTest extends FileStructureTestBase {
  private boolean myHtml5OutlineModeDefault;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myHtml5OutlineModeDefault = PropertiesComponent.getInstance().getBoolean(getHtml5OutlineModePropertyName());
  }

  @Override
  protected void configureDefault() {
    super.configureDefault();
    setHtml5OutlineMode(true);
  }

  @Override
  public void tearDown() throws Exception {
    PropertiesComponent.getInstance().setValue(getHtml5OutlineModePropertyName(), myHtml5OutlineModeDefault);
    super.tearDown();
  }

  private static String getHtml5OutlineModePropertyName() {
    return TreeStructureUtil.getPropertyName(Html5SectionsNodeProvider.HTML5_OUTLINE_PROVIDER_PROPERTY);
  }

  @Override
  protected String getFileExtension() {
    return "html";
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/structureView/";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  public void setHtml5OutlineMode(boolean enabled) {
    myPopupFixture.getPopup().setTreeActionState(Html5SectionsNodeProvider.class, enabled);
    myPopupFixture.update();
  }

  public void testEmpty() {checkTree();}
  public void testSimple() {checkTree();}
  public void testNoSectioningRoot() {checkTree();}
  public void testImplicitSections() {checkTree();}
  public void testMultipleRootTags() {checkTree();}
}