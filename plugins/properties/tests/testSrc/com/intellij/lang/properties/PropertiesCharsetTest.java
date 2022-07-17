// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

@HeavyPlatformTestCase.WrapInCommand
public class PropertiesCharsetTest extends JavaCodeInsightTestCase {
  private boolean myOldIsNative;
  private Charset myOldCs;
  private Charset myOldCharset;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldIsNative = EncodingProjectManager.getInstance(getProject()).isNative2AsciiForPropertiesFiles();
    myOldCs = EncodingProjectManager.getInstance(getProject()).getDefaultCharsetForPropertiesFiles(null);
    myOldCharset = EncodingProjectManager.getInstance(getProject()).getDefaultCharset();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, myOldIsNative);
      EncodingProjectManager.getInstance(getProject()).setDefaultCharsetForPropertiesFiles(null, myOldCs);
      if (myOldCharset != null) {
        EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(myOldCharset.name());
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void configureByText(@NonNls final String text) throws Exception {
    myFile = createFile(getTestName(false) + ".properties", text);
  }

  public void testCharsetOn() throws Exception {
    EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, true);

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
    String chars = VfsUtilCore.loadText(newFile);
    assertEquals("\\u041F\\uyyyy\\n\\t\\y=\\u3210\\uzzzz\\n\\t\\y", chars);
  }

  public void testCharsetOff() {
    EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, false);
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetForPropertiesFiles(null, StandardCharsets.ISO_8859_1);

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
      String chars = VfsUtilCore.loadText(newFile);
      // 0x41f converted to '?' because it cannot be represented in ISO-8859-1
      assertEquals("?\\uyyyy\\n\\t\\y=\\u3210\\uzzzz\\n\\t\\y", chars);
    });
  }

  public void testDefaultCharset() {
    EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, false);
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetForPropertiesFiles(null, null);
    EncodingProjectManager.getInstance(getProject()).setEncoding(null, StandardCharsets.UTF_8);

    PlatformTestUtil.withEncoding("UTF-8", () -> {
      configureByText("\\u1234\\uxxxx\\n\\t\\y=\\u3210\\uzzzz\\n\\t\\y");
      List<IProperty> properties = ((PropertiesFile)myFile).getProperties();
      assertEquals(1, properties.size());
      final Property property = (Property)properties.get(0);
      assertEquals("\\u1234\\uxxxx\\n\\t\\y", property.getKey());
      assertEquals("\\u3210\\uzzzz\\n\\t\\y", property.getValue());
      ApplicationManager.getApplication().runWriteAction(() -> {
        property.setName("\\u041f\\uyyyy\\n\\t\\y");
      });

      FileDocumentManager.getInstance().saveAllDocuments();
      VirtualFile virtualFile = myFile.getVirtualFile();
      // copy to other file type to stop charset mingling
      VirtualFile newFile = copy(virtualFile, virtualFile.getParent(), "xxx.txt");
      String chars = VfsUtilCore.loadText(newFile);
      assertEquals("\\u041f\\uyyyy\\n\\t\\y=\\u3210\\uzzzz\\n\\t\\y", chars);
    });
  }

  public void testCharsBelow128() throws Exception {
    EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, true);
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetForPropertiesFiles(null, null);
    EncodingProjectManager.getInstance(getProject()).setEncoding(null, StandardCharsets.UTF_8);

    configureByText("xxx=\\u3210\\uzzzz\\n\\t\\y");
    List<IProperty> properties = ((PropertiesFile)myFile).getProperties();
    assertEquals(1, properties.size());
    final IProperty property = properties.get(0);
    ApplicationManager.getApplication().runWriteAction(() -> property.setValue("\u00e7\u007f\u0080\u00ff\u0024"));

    FileDocumentManager.getInstance().saveAllDocuments();
    VirtualFile virtualFile = myFile.getVirtualFile();
    // copy to other file type to stop charset mingling
    VirtualFile newFile = copy(virtualFile, virtualFile.getParent(), "xxx.txt");
    String chars = VfsUtilCore.loadText(newFile);
    assertEquals("xxx=\\u00E7\u007F\\u0080\\u00FF\u0024", chars);
  }

  public void testForceRefresh() throws Exception {
    EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, true);
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetForPropertiesFiles(null, null);
    EncodingProjectManager.getInstance(getProject()).setEncoding(null, StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();

    configureByText("xxx=\\u1234");
    myEditor = createEditor(myFile.getVirtualFile());

    List<IProperty> properties = ((PropertiesFile)myFile).getProperties();
    assertEquals(1, properties.size());
    IProperty property = properties.get(0);
    assertEquals('\u1234' + "", property.getValue());

    EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, false);
    UIUtil.dispatchAllInvocationEvents();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    properties = ((PropertiesFile)myFile).getProperties();
    assertEquals(1, properties.size());
    property = properties.get(0);
    assertEquals("\\u1234", property.getValue());
  }

  public void testChangeEncodingMustReloadFile() throws Exception {
    EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, false);
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetForPropertiesFiles(null, StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();

    configureByText("xxx=\\u1234");
    assertEquals(StandardCharsets.UTF_8, myFile.getVirtualFile().getCharset());

    Charset win = Charset.forName("windows-1251");
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetForPropertiesFiles(null, win);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(win, myFile.getVirtualFile().getCharset());
  }

  public void testBOMMarkedFileWithNativeConversion() throws Exception {
    EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, true);
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetForPropertiesFiles(null, StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();

    File ioFile = FileUtil.createTempFile("copy",".properties");
    FileUtil.writeToFile(ioFile, CharsetToolkit.UTF8_BOM);
    FileUtil.writeToFile(ioFile, "general-notice=\\u062a\\u0648\\u062c\\u0647", true);

    disposeOnTearDown(() -> FileUtil.delete(ioFile));
    VirtualFile file = getVirtualFile(ioFile);

    PropertiesFile propertiesFile = (PropertiesFile)getPsiManager().findFile(file);
    assertNotNull(propertiesFile);

    assertEquals(Native2AsciiCharset.makeNative2AsciiEncodingName(CharsetToolkit.UTF8), file.getCharset().name());
  }
}
