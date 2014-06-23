package com.intellij.lang.properties.xml;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 7/26/11
 */
public class XmlPropertiesTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testXmlProperties() throws Exception {
    myFixture.configureByFile("foo.xml");
    List<PropertiesFile> files = PropertiesReferenceManager.getInstance(getProject()).findPropertiesFiles(myModule, "foo");
    assertEquals(1, files.size());
    PropertiesFile file = files.get(0);
    assertEquals(1, file.findPropertiesByKey("foo").size());

    List<IProperty> properties = PropertiesImplUtil.findPropertiesByKey(getProject(), "foo");
    assertEquals(1, properties.size());
  }

  public void testWrongFile() throws Exception {
    PsiFile psiFile = myFixture.configureByFile("wrong.xml");
    PropertiesFile file = PropertiesImplUtil.getPropertiesFile(psiFile);
    assertNull(file);
  }

  public void testHighlighting() throws Exception {
    myFixture.testHighlighting("foo.xml");
  }

  public void testAddProperty() {
    final PsiFile psiFile = myFixture.configureByFile("foo.xml");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
    assertNotNull(propertiesFile);

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      public void run() {
        propertiesFile.addProperty("kkk", "vvv");
      }
    });

    final IProperty property = propertiesFile.findPropertyByKey("kkk");
    assertNotNull(property);
    assertEquals("vvv", property.getValue());
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData/xml/";
  }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }

  public XmlPropertiesTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }
}
