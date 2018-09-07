// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.vcs.AbstractJunitVcsTestCase;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.info.SvnInfoHandler;
import org.jetbrains.idea.svn.status.CmdStatusClient;
import org.jetbrains.idea.svn.status.PortableStatus;
import org.jetbrains.idea.svn.status.SvnStatusHandler;
import org.junit.Test;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.util.io.PathKt.inputStream;
import static com.intellij.util.io.PathKt.readText;
import static org.jetbrains.idea.svn.SvnTestCase.getPluginHome;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.junit.Assert.*;

@TestDataPath("$CONTENT_ROOT/../testData/parse/")
public class SvnParseCommandLineParseTest extends AbstractJunitVcsTestCase {

  public static final String LINUX_ROOT = "/c7181320/";

  @NotNull
  private Path getTestData() {
    String fileName = PlatformTestUtil.getTestName(getTestName(), true);
    return Paths.get(getPluginHome(), "testData", "parse", fileName + ".xml");
  }

  @Test
  public void testInfo() throws Exception {
    final Info[] info = new Info[1];
    final SvnInfoHandler handler = new SvnInfoHandler(new File("C:/base/"), info1 -> info[0] = info1);
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(inputStream(getTestData()), handler);

    assertNotNull(info[0]);
  }

  @Test
  public void testStatus() throws Exception {
    final SvnStatusHandler[] handlerArr = new SvnStatusHandler[1];
    final boolean isWindows = SystemInfo.isWindows;
    final String basePath = isWindows ? "C:/base/" : "/base33729/";
    final SvnStatusHandler handler = new
      SvnStatusHandler(new SvnStatusHandler.ExternalDataCallback() {
      @Override
      public void switchPath() {
        handlerArr[0].getPending().getKind();
      }

      @Override
      public void switchChangeList(String newList) {
      }
    }, new File(basePath), o -> {
      try {
        o.getCanonicalFile();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (isWindows) {
        final int idx = o.getPath().indexOf(":");
        assertTrue(idx > 0);
        final int secondIdx = o.getPath().indexOf(":", idx + 1);
        assertTrue(o.getPath(), secondIdx == -1);
      } else {
        if (o.getPath().contains(LINUX_ROOT)) {
          assertFalse(o.getPath().contains(basePath));
        }
      }
      try {
        return createStubInfo(basePath + "1", "http://a.b.c");
      }
      catch (SvnBindException e) {
        throw new RuntimeException(e);
      }
    });
    handlerArr[0] = handler;

    final String osChecked = readText(getTestData());
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(new ByteArrayInputStream(osChecked.getBytes(CharsetToolkit.UTF8_CHARSET)), handler);
    final MultiMap<String,PortableStatus> changes = handler.getCurrentListChanges();
  }

  private String changePathsIfNix(String s) {
    if (SystemInfo.isWindows) return s;
    s = FileUtil.toSystemIndependentName(s);
    return StringUtil.replace(s, "C:/", LINUX_ROOT);
  }

  private Info createStubInfo(final String basePath, final String baseUrl) throws SvnBindException {
    return new Info(basePath, createUrl(baseUrl), Revision.HEAD, NodeKind.FILE, "",
                    createUrl("http://a.b.c"), 1, new Date(), "me", null, Depth.EMPTY);
  }

  @Test
  public void testStatusInExternalMove() throws Exception {
    final String basePath = "C:\\TestProjects\\sortedProjects\\Subversion\\local2\\sep12main\\main";
    Ref<SvnStatusHandler> handler = Ref.create();
    final File baseFile = new File(basePath);
    final SvnStatusHandler.ExternalDataCallback callback = CmdStatusClient.createStatusCallback(status1 -> {
      System.out.println(status1.getURL());
      if (new File(
        "C:\\TestProjects\\sortedProjects\\Subversion\\local2\\sep12main\\main\\slave\\src\\com\\slave\\MacMessagesParser.java")
        .equals(status1.getFile())) {
        assertEquals("http://external/src/com/slave/MacMessagesParser.java", status1.getURL().toString());
      }
      if (new File("C:\\TestProjects\\sortedProjects\\Subversion\\local2\\sep12main\\main\\slave\\src\\com\\slave\\SomeOtherClass.java")
        .equals(status1.getFile())) {
        assertEquals("http://external/src/com/slave/SomeOtherClass.java", status1.getURL().toString());
      }
    }, baseFile, createStubInfo(basePath, "http://mainurl/"), () -> handler.get().getPending());
    handler.set(new SvnStatusHandler(callback, baseFile, o -> {
      try {
        if (new File("C:\\TestProjects\\sortedProjects\\Subversion\\local2\\sep12main\\main\\slave").equals(o)) {
          return createStubInfo(o.getPath(), "http://external");
        }
        return createStubInfo(o.getPath(), "http://12345");
      }
      catch (SvnBindException e) {
        throw new RuntimeException(e);
      }
    }));
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(inputStream(getTestData()), handler.get());
    final MultiMap<String, PortableStatus> changes = handler.get().getCurrentListChanges();
  }

  @Test
  public void testStatusWithSwitched() throws Exception {
    final SvnStatusHandler[] handlerArr = new SvnStatusHandler[1];
    final boolean isWindows = SystemInfo.isWindows;
    final String basePath = isWindows ? "C:/base/" : "/base33729/";
    final Set<PortableStatus> statuses = new HashSet<>();
    final SvnStatusHandler handler = new
      SvnStatusHandler(new SvnStatusHandler.ExternalDataCallback() {
      @Override
      public void switchPath() {
        statuses.add(handlerArr[0].getPending());
        handlerArr[0].getPending().getKind();
      }

      @Override
      public void switchChangeList(String newList) {
      }
    }, new File(basePath), o -> {
      try {
        o.getCanonicalFile();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (isWindows) {
        final int idx = o.getPath().indexOf(":");
        assertTrue(idx > 0);
        final int secondIdx = o.getPath().indexOf(":", idx + 1);
        assertTrue(o.getPath(), secondIdx == -1);
      } else {
        if (o.getPath().contains(LINUX_ROOT)) {
          assertFalse(o.getPath().contains(basePath));
        }
      }
      try {
        return createStubInfo(basePath + "1", "http://a.b.c");
      }
      catch (SvnBindException e) {
        throw new RuntimeException(e);
      }
    });
    handlerArr[0] = handler;

    final String osChecked = changePathsIfNix(readText(getTestData()));
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(new ByteArrayInputStream(osChecked.getBytes(CharsetToolkit.UTF8_CHARSET)), handler);

    final String[] expected = {"root\\source\\s1.txt", "root\\target"};
    for (int i = 0; i < expected.length; i++) {
      expected[i] = FileUtil.toSystemDependentName(expected[i]);
    }
    int cntMatched = 0;
    for (PortableStatus status : statuses) {
      assertTrue(status.isSwitched());
      final String path = FileUtil.toSystemDependentName(status.getPath());
      for (String s1 : expected) {
        if (s1.equals(path)) {
          ++ cntMatched;
          break;
        }
      }
    }
    assertEquals(2, cntMatched);
  }

  @Test
  public void testOneFileInChangeListStatus() throws Exception {
    final SvnStatusHandler[] handlerArr = new SvnStatusHandler[1];
    final boolean isWindows = SystemInfo.isWindows;
    final String basePath = isWindows ? "C:/base/" : "/base33729/";
    final Set<PortableStatus> statuses = new HashSet<>();
    final String[] clName = new String[1];
    final SvnStatusHandler handler = new
      SvnStatusHandler(new SvnStatusHandler.ExternalDataCallback() {
      @Override
      public void switchPath() {
        final PortableStatus pending = handlerArr[0].getPending();
        pending.setChangelistName(clName[0]);
        statuses.add(pending);
        pending.getKind();
      }

      @Override
      public void switchChangeList(String newList) {
        clName[0] = newList;
      }
    }, new File(basePath), o -> {
      try {
        o.getCanonicalFile();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (isWindows) {
        final int idx = o.getPath().indexOf(":");
        assertTrue(idx > 0);
        final int secondIdx = o.getPath().indexOf(":", idx + 1);
        assertTrue(o.getPath(), secondIdx == -1);
      } else {
        if (o.getPath().contains(LINUX_ROOT)) {
          assertFalse(o.getPath().contains(basePath));
        }
      }
      try {
        return createStubInfo(basePath + "1", "http://a.b.c");
      }
      catch (SvnBindException e) {
        throw new RuntimeException(e);
      }
    });
    handlerArr[0] = handler;

    final String osChecked = changePathsIfNix(readText(getTestData()));
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(new ByteArrayInputStream(osChecked.getBytes(CharsetToolkit.UTF8_CHARSET)), handler);

    assertEquals(1, statuses.size());
    final PortableStatus next = statuses.iterator().next();
    assertEquals("a.txt", next.getPath());
    assertEquals("target", next.getChangelistName());
  }
}
