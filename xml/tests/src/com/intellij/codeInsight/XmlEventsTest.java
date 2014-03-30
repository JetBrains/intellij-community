/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;

public class XmlEventsTest extends LightCodeInsightTestCase {
  public void test1() throws Exception{
    final PomModel model = PomManager.getModel(getProject());
    final Listener listener = new Listener(model.getModelAspect(XmlAspect.class));
    model.addModelListener(listener);
    final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a/>");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        tagFromText.setAttribute("a", "b");
      }
    });

    assertFileTextEquals(getTestName(false) + ".txt", listener.getEventString());
  }

  public void test2() throws Exception{
    final PomModel model = PomManager.getModel(getProject());
    final Listener listener = new Listener(model.getModelAspect(XmlAspect.class));
    model.addModelListener(listener);
    final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>aaa</a>");
    final XmlTag otherTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<a/>");
    final XmlText xmlText = tagFromText.getValue().getTextElements()[0];
    WriteCommandAction.runWriteCommandAction(null, new Runnable(){public void run() {
        xmlText.insertAtOffset(otherTag, 2);
      }
    });

    assertFileTextEquals(getTestName(false) + ".txt", listener.getEventString());
  }

  public void test3() throws Exception{
    final PomModel model = PomManager.getModel(getProject());
    final Listener listener = new Listener(model.getModelAspect(XmlAspect.class));
    model.addModelListener(listener);
    final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>aaa</a>");
    final XmlText xmlText = tagFromText.getValue().getTextElements()[0];
    WriteCommandAction.runWriteCommandAction(null, new Runnable(){public void run() {
        xmlText.insertText("bb", 2);
      }
    });

    assertFileTextEquals(getTestName(false) + ".txt", listener.getEventString());
  }

  public void test4() throws Exception{
    final PomModel model = PomManager.getModel(getProject());
    final Listener listener = new Listener(model.getModelAspect(XmlAspect.class));
    model.addModelListener(listener);
    final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>a </a>");
    WriteCommandAction.runWriteCommandAction(null, new Runnable(){public void run() {
        tagFromText.addAfter(tagFromText.getValue().getTextElements()[0], tagFromText.getValue().getTextElements()[0]);
      }
    });

    assertFileTextEquals(getTestName(false) + ".txt", listener.getEventString());
  }

  public void test5() throws Exception{
    final PomModel model = PomManager.getModel(getProject());
    final Listener listener = new Listener(model.getModelAspect(XmlAspect.class));
    model.addModelListener(listener);
    final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>aaa</a>");
    WriteCommandAction.runWriteCommandAction(null, new Runnable(){public void run() {
        tagFromText.delete();
      }
    });

    assertFileTextEquals(getTestName(false) + ".txt", listener.getEventString());
  }

  public void testBulkUpdate() throws Exception{
    final PomModel model = PomManager.getModel(getProject());
    final Listener listener = new Listener(model.getModelAspect(XmlAspect.class));
    model.addModelListener(listener);
    final PsiFile file = createFile("a.xml", "<a/>");
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        ((DocumentEx)document).setInBulkUpdate(true);
        document.insertString(0, " ");
        commitDocument(document);
        ((DocumentEx)document).setInBulkUpdate(false);
      }
    }.execute();
    assertFileTextEquals(getTestName(false) + ".txt", listener.getEventString());
  }

  public void testDocumentChange1() throws Exception{
    final String rootTagText = "<a/>";
    final String stringToInsert = "b=\"c\"";
    final int positionToInsert = 2;

    checkEventsByDocumentChange(rootTagText, positionToInsert, stringToInsert);
  }

  public void testDocumentChange2() throws Exception{
    final String rootTagText = "<a />";
    final String stringToInsert = "b=\"c\"";
    final int positionToInsert = 3;

    checkEventsByDocumentChange(rootTagText, positionToInsert, stringToInsert);
  }


  public void testDocumentChange3() throws Exception{
    final String rootTagText = "<b><a /></b>";
    final String stringToInsert = "b=\"c\"";
    final int positionToInsert = 6;

    checkEventsByDocumentChange(rootTagText, positionToInsert, stringToInsert);
  }

  public void testAttributeValueReplace() throws Exception {
    final String text = "<target name=\"old\"/>";
    final PomModel model = PomManager.getModel(getProject());
    final Listener listener = new Listener(model.getModelAspect(XmlAspect.class));
    model.addModelListener(listener);

    final XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText(text);
    final XmlAttribute attribute = tag.getAttribute("name", null);
    assert attribute != null;
    WriteCommandAction.runWriteCommandAction(null, new Runnable(){public void run() {
        attribute.setValue("new");
      }
    });


    assertEquals(attribute.getValue(), "new");
    assertEquals("(Attribute \"name\" for tag \"target\" set to \"\"new\"\")\n", listener.getEventString());
  }

  private void checkEventsByDocumentChange(final String rootTagText, final int positionToInsert, final String stringToInsert)
    throws Exception {
    final PomModel model = PomManager.getModel(getProject());
    final Listener listener = new Listener(model.getModelAspect(XmlAspect.class));
    model.addModelListener(listener);
    final XmlTag tagFromText = ((XmlFile)createFile("file.xml", rootTagText)).getDocument().getRootTag();
    final PsiFileImpl containingFile = (PsiFileImpl)tagFromText.getContainingFile();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(containingFile);
    WriteCommandAction.runWriteCommandAction(null, new Runnable(){public void run() {
            document.insertString(positionToInsert, stringToInsert);
            documentManager.commitDocument(document);
          }
        });

    assertFileTextEquals(getTestName(false) + ".txt", listener.getEventString());
  }

  private static class Listener implements PomModelListener{
    private final XmlAspect myAspect;
    private final StringBuffer myBuffer = new StringBuffer();

    public Listener(XmlAspect modelAspect) {
      myAspect = modelAspect;
    }

    @Override
    public void modelChanged(PomModelEvent event) {
      final PomChangeSet changeSet = event.getChangeSet(myAspect);
      if(changeSet == null) return;
      myBuffer.append(changeSet);
      myBuffer.append("\n");
    }

    @Override
    public boolean isAspectChangeInteresting(PomModelAspect aspect) {
      return aspect == myAspect;
    }

    String getEventString(){
      return myBuffer.toString();
    }
  }

  private static void assertFileTextEquals(String targetDataName, String treeText) throws Exception {
    String fullName = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/')+ "/xml/tests/testData/psi/events" + File.separatorChar + targetDataName;
    try{
      String expectedText = loadFile(fullName);
      assertEquals(expectedText.trim(), treeText.trim());
    }
    catch(FileNotFoundException e){
      //FileUtil.writeToFile(new File(fullName), StringUtil.convertLineSeparators(treeText).getBytes("UTF-8"));
      fail("No output file found. Created file "+fullName);
    }
  }


  protected static String loadFile(String fullName) throws Exception {
    String text = FileUtil.loadFile(new File(fullName)).trim();
    text = StringUtil.convertLineSeparators(text);
    return text;
  }

  public void testDocumentChange() throws Exception {
    final String xml = "" +
                       "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                       "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "    android:layout_height=\"fill_parent\">\n" +
                       "    <include layout=\"@layout/colorstrip\" />\n" +
                       "\n" +
                       "\n" +
                       "    <LinearLayout\n" +
                       "        android:id=\"@+id/noteArea\"\n" +
                       "        android:layout_width=\"fill_parent\"\n" +
                       "        android:layout_height=\"wrap_content\"\n" +
                       "        android:layout_weight=\"1\"\n" +
                       "        android:layout_margin=\"5dip\">\n" +
                       "    </LinearLayout>\n" +
                       "\n" +
                       "</LinearLayout>\n";
    PsiFile file = createFile("file.xml", xml);
    assertTrue(file instanceof XmlFile);
    XmlDocument xmlDocument = ((XmlFile)file).getDocument();
    assertNotNull(xmlDocument);
    final XmlTag tagFromText = xmlDocument.getRootTag();
    assertNotNull(tagFromText);
    final PsiFileImpl containingFile = (PsiFileImpl)tagFromText.getContainingFile();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(containingFile);
    assertNotNull(document);

    final TestListener listener = new TestListener();
    PsiManager.getInstance(getProject()).addPsiTreeChangeListener(listener);

    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            int positionToInsert = xml.indexOf(" <LinearLayout\n" +
                                               "        android:id=\"@+id/noteArea\"\n");
            assertFalse(positionToInsert == -1);
            String stringToInsert = "<Button android:id=\"@+id/newid\" />\n";
            document.insertString(positionToInsert, stringToInsert);
            documentManager.commitDocument(document);
          }
        });
      }
    }, "", null);

    PsiManager.getInstance(getProject()).removePsiTreeChangeListener(listener);
  }

  private static class TestListener extends PsiTreeChangeAdapter {
    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      if (event.getNewChild() != null) {
        assertNotSame("Received identical before and after children in childReplaced;", event.getOldChild(), event.getNewChild());
      }
    }
  }
}
