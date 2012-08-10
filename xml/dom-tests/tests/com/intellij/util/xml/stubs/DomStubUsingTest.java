/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.xml.stubs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 8/8/12
 */
public class DomStubUsingTest extends DomStubTest {

  public void testFoo() throws Exception {

    DomFileElement<Foo> fileElement = prepare("foo.xml");
    PsiFile file = fileElement.getFile();
    assertFalse(file.getNode().isParsed());

    Foo foo = fileElement.getRootElement();
    List<Bar> bars = foo.getBars();
    assertFalse(file.getNode().isParsed());

    assertEquals(2, bars.size());
    Bar bar = bars.get(0);
    String value = bar.getString().getStringValue();
    assertEquals("xxx", value);

    Object o = bar.getString().getValue();
    assertEquals("xxx", o);

    Integer integer = bar.getInt().getValue();
    assertEquals(666, integer.intValue());

    assertFalse(file.getNode().isParsed());

    Bar emptyBar = bars.get(1);
    GenericAttributeValue<String> string = emptyBar.getString();
    assertNull(string.getXmlElement());

    assertFalse(file.getNode().isParsed());
  }

  public void testAccessingPsi() throws Exception {
    DomFileElement<Foo> element = prepare("foo.xml");
    assertNotNull(element.getXmlElement());

    XmlTag tag = element.getRootTag();
    assertNotNull(tag);

    Foo foo = element.getRootElement();
    assertNotNull(foo.getXmlTag());

    Bar bar = foo.getBars().get(0);
    assertNotNull(bar.getXmlElement());

    XmlAttribute attribute = bar.getString().getXmlAttribute();
    assertNotNull(attribute);
  }

  private DomFileElement<Foo> prepare(String path) {
    PsiFile file = myFixture.configureByFile(path);
    assertFalse(file.getNode().isParsed());
    VirtualFile virtualFile = file.getVirtualFile();
    ObjectStubTree tree = StubTreeLoader.getInstance().readOrBuild(getProject(), virtualFile, file);
    assertNotNull(tree);

    ((PsiManagerImpl)getPsiManager()).cleanupForNextTest();
    file = getPsiManager().findFile(virtualFile);
    assertFalse(file.getNode().isParsed());

    DomFileElement<Foo> fileElement = DomManager.getDomManager(getProject()).getFileElement((XmlFile)file, Foo.class);
    assertNotNull(fileElement);
    return fileElement;
  }
}
