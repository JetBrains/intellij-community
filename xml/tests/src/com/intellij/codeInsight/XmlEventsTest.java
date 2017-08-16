/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.TextRange;
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
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

public class XmlEventsTest extends LightCodeInsightTestCase {
  public void test1() {
    final Listener listener = addPomListener();
    final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a/>");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tagFromText.setAttribute("a", "b");
    });

    assertEquals("(Attribute \"a\" for tag \"a\" set to \"b\")\n", listener.getEventString());
  }

  private Listener addPomListener() {
    final PomModel model = PomManager.getModel(getProject());
    final Listener listener = new Listener(model.getModelAspect(XmlAspect.class));
    model.addModelListener(listener, getTestRootDisposable());
    return listener;
  }

  public void test2() {
    final Listener listener = addPomListener();
    final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>aaa</a>");
    final XmlTag otherTag = XmlElementFactory.getInstance(getProject()).createTagFromText("<a/>");
    final XmlText xmlText = tagFromText.getValue().getTextElements()[0];
    WriteCommandAction.runWriteCommandAction(null, () -> {
      xmlText.insertAtOffset(otherTag, 2);
    });

    assertEquals("(text changed to 'aa' was: 'aaa'), (child added to a child: XmlText), (child added to a child: XmlTag:a)\n", listener.getEventString());
  }

  public void test3() {
    final Listener listener = addPomListener();
    final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>aaa</a>");
    final XmlText xmlText = tagFromText.getValue().getTextElements()[0];
    WriteCommandAction.runWriteCommandAction(null, () -> xmlText.insertText("bb", 2));

    assertEquals("(text changed to 'aabba' was: 'aaa')\n", listener.getEventString());
  }

  public void testTagDelete() {
    final Listener listener = addPomListener();
    configureFromFileText("x.xml", "<a>aaa\n<x>xxx</x>\n<y>yyy</y></a>");
    final XmlTag x = ((XmlFile)getFile()).getRootTag().findSubTags("x")[0];
    WriteCommandAction.runWriteCommandAction(null, () -> {
      TextRange range = x.getTextRange();
      getEditor().getDocument().deleteString(range.getStartOffset(), range.getEndOffset() + 1); // plus \n
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });

    assertEquals("(child removed from a child: XmlTag:x), (child removed from a child: XmlText)\n", listener.getEventString());
  }

  public void testTagInsert() {
    final Listener listener = addPomListener();
    configureFromFileText("x.xml", "<a>aaa\n<x>xxx</x>\n<y>yyy</y></a>");
    final XmlTag x = ((XmlFile)getFile()).getRootTag().findSubTags("x")[0];
    WriteCommandAction.runWriteCommandAction(null, () -> {
      TextRange range = x.getTextRange();
      getEditor().getDocument().insertString(range.getEndOffset() + 1, "<z>zxzz</z>\n"); // plus \n
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });

    assertEquals("(child added to a child: XmlText), (child added to a child: XmlTag:z)\n", listener.getEventString());
  }

  public void test4() {
    final Listener listener = addPomListener();
    final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>a </a>");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tagFromText.addAfter(tagFromText.getValue().getTextElements()[0], tagFromText.getValue().getTextElements()[0]);
    });

    assertEquals("(text changed to 'a a ' was: 'a ')\n", listener.getEventString());
  }

  public void test5() {
    final Listener listener = addPomListener();
    final XmlTag tagFromText = XmlElementFactory.getInstance(getProject()).createTagFromText("<a>aaa</a>");
    WriteCommandAction.runWriteCommandAction(null, () -> tagFromText.delete());

    assertEquals("(Xml document changed)\n", listener.getEventString());
  }

  public void testBulkUpdate() {
    final Listener listener = addPomListener();
    final PsiFile file = createFile("a.xml", "<a/>");
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        DocumentUtil.executeInBulk(document, true, ()-> {
          document.insertString(0, " ");
          commitDocument(document);
        });
      }
    }.execute();
    assertEquals("(Xml document changed)", listener.getEventString().trim());
  }

  public void testDocumentChange1() {
    final String rootTagText = "<a/>";
    final String stringToInsert = "b=\"c\"";
    final int positionToInsert = 2;

    checkEventsByDocumentChange(rootTagText, positionToInsert, stringToInsert, "(Xml document changed)\n");
  }

  public void testDocumentChange2() {
    final String rootTagText = "<a />";
    final String stringToInsert = "b=\"c\"";
    final int positionToInsert = 3;

    checkEventsByDocumentChange(rootTagText, positionToInsert, stringToInsert, "(Xml document changed)\n");
  }


  public void testDocumentChange3() {
    final String rootTagText = "<b><a /></b>";
    final String stringToInsert = "b=\"c\"";
    final int positionToInsert = 6;

    checkEventsByDocumentChange(rootTagText, positionToInsert, stringToInsert, "(child changed in b child: XmlTag:a)\n");
  }

  public void testAttributeValueReplace() {
    final String text = "<target name=\"old\"/>";
    final Listener listener = addPomListener();

    final XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText(text);
    final XmlAttribute attribute = tag.getAttribute("name", null);
    assert attribute != null;
    WriteCommandAction.runWriteCommandAction(null, () -> attribute.setValue("new"));


    assertEquals(attribute.getValue(), "new");
    assertEquals("(Attribute \"name\" for tag \"target\" set to \"\"new\"\")\n", listener.getEventString());
  }

  private void checkEventsByDocumentChange(final String rootTagText, final int positionToInsert, final String stringToInsert, String events) {
    final Listener listener = addPomListener();
    final XmlTag tagFromText = ((XmlFile)createFile("file.xml", rootTagText)).getDocument().getRootTag();
    final PsiFileImpl containingFile = (PsiFileImpl)tagFromText.getContainingFile();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(containingFile);
    WriteCommandAction.runWriteCommandAction(null, () -> {
          document.insertString(positionToInsert, stringToInsert);
          documentManager.commitDocument(document);
        });

    assertEquals(events, listener.getEventString());
  }

  private static class Listener implements PomModelListener{
    private final XmlAspect myAspect;
    private final StringBuffer myBuffer = new StringBuffer();

    private Listener(XmlAspect modelAspect) {
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

    private String getEventString(){
      return myBuffer.toString();
    }
  }

  public void testDocumentChange() {
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

    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      int positionToInsert = xml.indexOf(" <LinearLayout\n" +
                                         "        android:id=\"@+id/noteArea\"\n");
      assertFalse(positionToInsert == -1);
      String stringToInsert = "<Button android:id=\"@+id/newid\" />\n";
      document.insertString(positionToInsert, stringToInsert);
      documentManager.commitDocument(document);
    }), "", null);

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
