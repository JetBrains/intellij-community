package com.jetbrains.python.buildout;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public class BuildoutScriptParserTest extends PyLightFixtureTestCase  {
  public void testParseOldStyleScript() throws IOException {
    File scriptFile = new File(myFixture.getTestDataPath(), "buildout/django-script.py");
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(scriptFile);
    List<String> paths = BuildoutFacet.extractFromScript(vFile);
    assertEquals(7, paths.size());
    assertTrue(paths.contains("c:\\\\src\\\\django\\\\buildout15\\\\eggs\\\\djangorecipe-0.20-py2.6.egg"));
    assertTrue(paths.contains("c:\\\\src\\\\django\\\\buildout15\\\\parts\\\\django"));
  }

  public void testParseSitePy() throws IOException {
    File scriptFile = new File(myFixture.getTestDataPath(), "buildout/site.py");
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(scriptFile);
    List<String> paths = BuildoutFacet.extractFromSitePy(vFile);
    assertSameElements(paths,
                       "c:\\src\\django\\buildout15\\src",
                       "c:\\src\\django\\buildout15\\eggs\\setuptools-0.6c12dev_r88124-py2.6.egg");
  }
}
