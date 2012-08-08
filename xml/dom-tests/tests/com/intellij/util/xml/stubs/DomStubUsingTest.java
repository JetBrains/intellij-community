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

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;

/**
 * @author Dmitry Avdeev
 *         Date: 8/8/12
 */
public class DomStubUsingTest extends DomStubTest {

  public void testFoo() throws Exception {
    PsiFile file = myFixture.configureByFile("foo.xml");
    DomFileElement<Foo> fileElement = DomManager.getDomManager(getProject()).getFileElement((XmlFile)file, Foo.class);
    assertNotNull(fileElement);
    Foo foo = fileElement.getRootElement();
  }
}
