// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.smartTree.SmartTreeStructure;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class XmlTreeStructureTest extends BaseProjectViewTestCase {
  public void testXmlStructureView() {
    doTest("file1.xml", """
      XmlFile:file1.xml
       XmlTag:root
        XmlTag:a
         XmlTag:a
          XmlTag:b
          XmlTag:text
      """
    );

  }

  public void testDtdStructureView() {
    doTest("file1.dtd", """
      XmlFile:file1.dtd
       book
       draft
       final
       helpset (file:required, path:required)
       myplugin (url:implied, version:implied)
       price
      """);
    doTest("file2.dtd", """
      XmlFile:file2.dtd
       HTMLlat1
       nbsp
      """);
  }

  public void testDtdStructureViewInXml() {
    final String fileName = "file2.xml";
    final String s = """
      XmlFile:file2.xml
       body
       supplements
       title
       XmlTag:title
      """;
    doTest(fileName, s);
  }

  public void testHtmlCustomRegions() {
    doTest(
      getTestName(false) + ".html",
      """
        HtmlFile:HtmlCustomRegions.html
         Region 'A'
          HtmlTag:div
          HtmlTag:div
         Region 'B'
          HtmlTag:ul
           Region 'C'
            HtmlTag:li
            HtmlTag:li
           HtmlTag:li
         HtmlTag:div
        """);
  }

  private void doTest(final String fileName, final String s) {
    XmlFile xmlFile = (XmlFile)getContentDirectory().findFile(fileName);
    VirtualFile virtualFile = xmlFile.getVirtualFile();
    final StructureViewBuilder structureViewBuilder =
      StructureViewBuilder.PROVIDER.getStructureViewBuilder(virtualFile.getFileType(), virtualFile, myProject);
    StructureViewModel viewModel = ((TreeBasedStructureViewBuilder)structureViewBuilder).createStructureViewModel(null);
    
    try {
      AbstractTreeStructure structure = new SmartTreeStructure(myProject, viewModel);
      checkNavigatability(structure.getRootElement());
      ProjectViewTestUtil.assertStructureEqual(structure, s, null);
    }
    finally {
      Disposer.dispose(viewModel);
    }
  }

  private static void checkNavigatability(final Object _element) {
    AbstractTreeNode element = (AbstractTreeNode)_element;
    assertTrue(element.canNavigate());
    for(Object c:element.getChildren()) checkNavigatability(c);
  }

  @NotNull
  @Override
  protected String getTestDirectoryName() {
    return "xmlStructureView";
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }
}
