package com.intellij.xml.structureView;

import com.intellij.ide.util.FileStructurePopup;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.html.structureView.Html5SectionsNodeProvider;
import com.intellij.testFramework.FileStructureTestBase;

public class HtmlFileStructureTest extends FileStructureTestBase {
  private boolean myHtml5OutlineModeDefault;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myHtml5OutlineModeDefault = PropertiesComponent.getInstance().getBoolean(getHtml5OutlineModePropertyName(), false);
    setHtml5OutlineMode(true);
  }

  @Override
  public void tearDown() throws Exception {
    PropertiesComponent.getInstance().setValue(getHtml5OutlineModePropertyName(), String.valueOf(myHtml5OutlineModeDefault));
    super.tearDown();
  }

  private static String getHtml5OutlineModePropertyName() {
    return FileStructurePopup.getPropertyName(Html5SectionsNodeProvider.HTML5_OUTLINE_PROVIDER_PROPERTY);
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

  public void setHtml5OutlineMode(boolean enabled) throws Exception {
    myPopup.setTreeActionState(Html5SectionsNodeProvider.class, enabled);
    update();
  }

  public void testEmpty() throws Exception {checkTree();}
  public void testSimple() throws Exception {checkTree();}
  public void testNoSectioningRoot() throws Exception {checkTree();}
  public void testImplicitSections() throws Exception {checkTree();}
  public void testMultipleRootTags() throws Exception {checkTree();}
}