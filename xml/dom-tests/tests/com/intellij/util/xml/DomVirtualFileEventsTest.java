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
package com.intellij.util.xml;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.impl.DomFileElementImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DomVirtualFileEventsTest extends DomHardCoreTestCase{

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getDomManager().registerFileDescription(new DomFileDescription(MyElement.class, "a") {

      @Override
      public boolean isMyFile(@NotNull final XmlFile file, final Module module) {
        return super.isMyFile(file, module) && file.getName().contains("a");
      }
    }, getTestRootDisposable());
  }

  public void testCreateFile() {
    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        final VirtualFile dir = getVirtualFile(createTempDirectory());
        addSourceContentToRoots(getModule(), dir);
        final VirtualFile childData = dir.createChildData(this, "abc.xml");
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        assertResultsAndClear();
        setFileText(childData, "<a/>");
        assertEventCount(0);
        assertResultsAndClear();
      }
    }.execute().throwException();
  }

  public void testDeleteFile() {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        final VirtualFile dir = getVirtualFile(createTempDirectory());
        addSourceContentToRoots(getModule(), dir);
        final VirtualFile childData = dir.createChildData(this, "abc.xml");
        assertResultsAndClear();
        setFileText(childData, "<a/>");
        final DomFileElementImpl<DomElement> fileElement = getFileElement(childData);
        assertResultsAndClear();

        childData.delete(this);
        assertEventCount(1);
        putExpected(new DomEvent(fileElement, false));
        assertResultsAndClear();
        assertFalse(fileElement.isValid());
      }
    }.execute().throwException();
  }

  public void testRenameFile() {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        final VirtualFile dir = getVirtualFile(createTempDirectory());
        addSourceContentToRoots(getModule(), dir);
        final VirtualFile data = dir.createChildData(this, "abc.xml");
        setFileText(data, "<a/>");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        DomFileElementImpl<DomElement> fileElement = getFileElement(data);
        assertEventCount(0);
        assertResultsAndClear();

        data.rename(this, "deaf.xml");
        assertEventCount(1);
        putExpected(new DomEvent(fileElement, false));
        assertResultsAndClear();
        assertEquals(fileElement, getFileElement(data));
        assertTrue(fileElement.isValid());
        fileElement = getFileElement(data);

        data.rename(this, "fff.xml");
        assertEventCount(1);
        putExpected(new DomEvent(fileElement, false));
        assertResultsAndClear();
        assertNull(getFileElement(data));
        assertFalse(fileElement.isValid());
      }
    }.execute().throwException();
  }

  private DomFileElementImpl<DomElement> getFileElement(final VirtualFile file) {
    return getDomManager().getFileElement((XmlFile)getPsiManager().findFile(file));
  }

  public interface MyElement extends DomElement {
  }

}
