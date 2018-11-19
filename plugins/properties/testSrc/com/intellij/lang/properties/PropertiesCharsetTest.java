// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import java.nio.charset.Charset;
import java.util.List;

/**
 * @author cdr
 */
@PlatformTestCase.WrapInCommand
public class PropertiesCharsetTest extends CodeInsightTestCase {
  private boolean myOldIsNative;
  private Charset myOldCs;
  private Charset myOldCharset;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldIsNative = EncodingManager.getInstance().isNative2AsciiForPropertiesFiles();
    myOldCs = EncodingManager.getInstance().getDefaultCharsetForPropertiesFiles(null);
    myOldCharset = EncodingManager.getInstance().getDefaultCharset();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, myOldIsNative);
      EncodingManager.getInstance().setDefaultCharsetForPropertiesFiles(null, myOldCs);
      if (myOldCharset != null) {
        EncodingManager.getInstance().setDefaultCharsetName(myOldCharset.name());
      }
    }
    finally {
      super.tearDown();
    }
  }

  private void configureByText(@NonNls final String text) throws Exception {
    myFile = createFile(getTestName(false) + ".properties", text);
  }

  public void testCharsetOn() throws Exception {
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, true);

    configureByText("\\u1234\\uxxxx\\n\\t\\y=\\u3210\\uzzzz\\n\\t\\y");
    List<IProperty> properties = ((PropertiesFile)myFile).getProperties();
    assertEquals(1, properties.size());
    final Property property = (Property)properties.get(0);
    assertEquals("\u1234\\uxxxx\\n\\t\\y", property.getKey());
    assertEquals("\u3210\\uzzzz\\n\\t\\y", property.getValue());
    ApplicationManager.getApplication().runWriteAction(() -> {
      property.setName("\u041f\\uyyyy\\n\\t\\y");
    });

    FileDocumentManager.getInstance().saveAllDocuments();
    VirtualFile virtualFile = myFile.getVirtualFile();
    // copy to other file type to stop charset mingling

    VirtualFile newFile = copy(virtualFile, virtualFile.getParent(), "xxx.txt");
    myFilesToDelete.add(VfsUtilCore.virtualToIoFile(newFile));
    String chars = VfsUtilCore.loadText(newFile);
    assertEquals("\\u041F\\uyyyy\\n\\t\\y=\\u3210\\uzzzz\\n\\t\\y", chars);
  }

  public void testCharsetOff() {
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, false);
    EncodingManager.getInstance().setDefaultCharsetForPropertiesFiles(null, Charset.forName("ISO-8859-1"));

    PlatformTestUtil.withEncoding("UTF-8", () -> {
      configureByText("\\u1234\\uxxxx\\n\\t\\y=\\u3210\\uzzzz\\n\\t\\y");
      List<IProperty> properties = ((PropertiesFile)myFile).getProperties();
      assertEquals(1, properties.size());
      final Property property = (Property)properties.get(0);
      assertEquals("\\u1234\\uxxxx\\n\\t\\y", property.getKey());
      assertEquals("\\u3210\\uzzzz\\n\\t\\y", property.getValue());
      ApplicationManager.getApplication().runWriteAction(() -> {
        property.setName("\u041f\\uyyyy\\n\\t\\y");
      });

      FileDocumentManager.getInstance().saveAllDocuments();
      VirtualFile virtualFile = myFile.getVirtualFile();
      // copy to other file type to stop charset mingling
      VirtualFile newFile = copy(virtualFile, virtualFile.getParent(), "xxx.txt");
      myFilesToDelete.add(VfsUtilCore.virtualToIoFile(newFile));
      String chars = VfsUtilCore.loadText(newFile);
      // 0x41f converted to '?' because it cannot be represented in ISO-8859-1
      assertEquals("?\\uyyyy\\n\\t\\y=\\u3210\\uzzzz\\n\\t\\y", chars);
    });
  }

  public void testDefaultCharset() {
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, false);
    EncodingManager.getInstance().setDefaultCharsetForPropertiesFiles(null, null);
    EncodingManager.getInstance().setEncoding(null, CharsetToolkit.UTF8_CHARSET);

    PlatformTestUtil.withEncoding("UTF-8", () -> {
      configureByText("\\u1234\\uxxxx\\n\\t\\y=\\u3210\\uzzzz\\n\\t\\y");
      List<IProperty> properties = ((PropertiesFile)myFile).getProperties();
      assertEquals(1, properties.size());
      final Property property = (Property)properties.get(0);
      assertEquals("\\u1234\\uxxxx\\n\\t\\y", property.getKey());
      assertEquals("\\u3210\\uzzzz\\n\\t\\y", property.getValue());
      ApplicationManager.getApplication().runWriteAction(() -> {
        property.setName("\u041f\\uyyyy\\n\\t\\y");
      });

      FileDocumentManager.getInstance().saveAllDocuments();
      VirtualFile virtualFile = myFile.getVirtualFile();
      // copy to other file type to stop charset mingling
      VirtualFile newFile = copy(virtualFile, virtualFile.getParent(), "xxx.txt");
      myFilesToDelete.add(VfsUtilCore.virtualToIoFile(newFile));
      String chars = VfsUtilCore.loadText(newFile);
      assertEquals("\u041f\\uyyyy\\n\\t\\y=\\u3210\\uzzzz\\n\\t\\y", chars);
    });
  }

  public void testCharsBelow128() throws Exception {
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, true);
    EncodingManager.getInstance().setDefaultCharsetForPropertiesFiles(null, null);
    EncodingManager.getInstance().setEncoding(null, CharsetToolkit.UTF8_CHARSET);

    configureByText("xxx=\\u3210\\uzzzz\\n\\t\\y");
    List<IProperty> properties = ((PropertiesFile)myFile).getProperties();
    assertEquals(1, properties.size());
    final IProperty property = properties.get(0);
    ApplicationManager.getApplication().runWriteAction(() -> property.setValue("\u00e7\u007f\u0080\u00ff\u0024"));

    FileDocumentManager.getInstance().saveAllDocuments();
    VirtualFile virtualFile = myFile.getVirtualFile();
    // copy to other file type to stop charset mingling
    VirtualFile newFile = copy(virtualFile, virtualFile.getParent(), "xxx.txt");
    myFilesToDelete.add(VfsUtilCore.virtualToIoFile(newFile));
    String chars = VfsUtilCore.loadText(newFile);
    assertEquals("xxx=\\u00E7\u007F\\u0080\\u00FF\u0024", chars);
  }

  public void testForceRefresh() throws Exception {
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, true);
    EncodingManager.getInstance().setDefaultCharsetForPropertiesFiles(null, null);
    EncodingManager.getInstance().setEncoding(null, CharsetToolkit.UTF8_CHARSET);
    UIUtil.dispatchAllInvocationEvents();

    configureByText("xxx=\\u1234");
    myEditor = createEditor(myFile.getVirtualFile());

    List<IProperty> properties = ((PropertiesFile)myFile).getProperties();
    assertEquals(1, properties.size());
    IProperty property = properties.get(0);
    assertEquals('\u1234' + "", property.getValue());

    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, false);
    UIUtil.dispatchAllInvocationEvents();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    properties = ((PropertiesFile)myFile).getProperties();
    assertEquals(1, properties.size());
    property = properties.get(0);
    assertEquals("\\u1234", property.getValue());
  }

  public void testChangeEncodingMustReloadFile() throws Exception {
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, false);
    EncodingManager.getInstance().setDefaultCharsetForPropertiesFiles(null, CharsetToolkit.UTF8_CHARSET);
    UIUtil.dispatchAllInvocationEvents();

    configureByText("xxx=\\u1234");
    assertEquals(CharsetToolkit.UTF8_CHARSET, myFile.getVirtualFile().getCharset());

    Charset win = Charset.forName("windows-1251");
    EncodingManager.getInstance().setDefaultCharsetForPropertiesFiles(null, win);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(win, myFile.getVirtualFile().getCharset());
  }

  public void testBOMMarkedFileWithNativeConversion() throws Exception {
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, true);
    EncodingManager.getInstance().setDefaultCharsetForPropertiesFiles(null, CharsetToolkit.UTF8_CHARSET);
    UIUtil.dispatchAllInvocationEvents();

    VirtualFile file =
      createTempFile("properties", CharsetToolkit.UTF8_BOM, "general-notice=\\u062a\\u0648\\u062c\\u0647", CharsetToolkit.UTF8_CHARSET);
    PropertiesFile propertiesFile = (PropertiesFile)getPsiManager().findFile(file);
    assertNotNull(propertiesFile);

    assertEquals(Native2AsciiCharset.makeNative2AsciiEncodingName(CharsetToolkit.UTF8), file.getCharset().name());
  }
}
