/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013 JetBrains s.r.o.
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

package com.pme.launcher;

import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.pme.exe.ExeFormat;
import com.pme.exe.ExeReader;
import com.pme.exe.SectionReader;
import com.pme.exe.res.DirectoryEntry;
import com.pme.exe.res.RawResource;
import com.pme.exe.res.ResourceSectionReader;
import com.pme.exe.res.StringTableDirectory;
import com.pme.exe.res.icon.IconResourceInjector;
import com.pme.exe.res.vi.StringTable;
import com.pme.exe.res.vi.VersionInfo;
import com.pme.util.OffsetTrackingInputStream;

import java.io.*;

/**
 * Date: May 6, 2006
 * Time: 10:43:01 AM
 */
public class LauncherGenerator {
  private File myTemplate;
  private File myExePath;
  private StringTableDirectory myStringTableDirectory;
  private DirectoryEntry myRoot;
  private ExeReader myReader;
  private VersionInfo myVersionInfo;

  public LauncherGenerator(File template, File exePath) {
    myTemplate = template;
    myExePath = exePath;
  }

  public void load() throws  IOException {
    RandomAccessFile stream = new RandomAccessFile(myTemplate, "r");
    ExeReader formatReader = new ExeReader(myTemplate.getName(), ExeFormat.UNKNOWN);
    formatReader.read(stream);
    stream.seek(0L);
    myReader = new ExeReader(myTemplate.getName(), formatReader.getExeFormat());
    myReader.read(stream);
    stream.close();
    SectionReader sectionReader = myReader.getSectionReader(".rsrc");
    ResourceSectionReader resourceReader = (ResourceSectionReader) sectionReader.getMember(".rsrc");
    myRoot = resourceReader.getRoot();
    DirectoryEntry subDir = myRoot.findSubDir("IRD6");
    myStringTableDirectory = new StringTableDirectory(subDir);

    RawResource versionInfoResource = getVersionInfoResource();
    ByteArrayInputStream bytesStream = new ByteArrayInputStream(versionInfoResource.getBytes().getBytes());

    myVersionInfo = new VersionInfo();
    myVersionInfo.read(new OffsetTrackingInputStream(new DataInputStream(bytesStream)));
  }

  private RawResource getVersionInfoResource() {
    DirectoryEntry viDir = myRoot.findSubDir("IRD16").findSubDir( "IRD1" );
    return viDir.getRawResource(0);
  }

  private void saveVersionInfo() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    myVersionInfo.resetOffsets(0);
    myVersionInfo.write(new DataOutputStream(baos));
    getVersionInfoResource().setBytes(baos.toByteArray());
  }

  public void generate() throws IOException {
    myStringTableDirectory.save();
    saveVersionInfo();

    myReader.resetOffsets(0);

    myExePath.getParentFile().mkdirs();
    myExePath.createNewFile();
    RandomAccessFile exeStream = new RandomAccessFile(myExePath, "rw");
    myReader.write(exeStream);
    exeStream.close();

//    verifyVersionInfo();
  }

  private void verifyVersionInfo() throws IOException {
    String versionInfoPath = myExePath + ".version";
    RandomAccessFile versionInfoStream = new RandomAccessFile(versionInfoPath, "rw");
    try {
      myVersionInfo.resetOffsets(0);
      myVersionInfo.write(versionInfoStream);
    }
    finally {
      versionInfoStream.close();
    }

    VersionInfo copy = new VersionInfo();
    copy.read(new OffsetTrackingInputStream(new DataInputStream(new FileInputStream(versionInfoPath))));
  }

  public void setResourceString(int id, String value) {
    myStringTableDirectory.setString(id, value);
  }

  public void setVersionInfoString(String key, String value) {
    StringTable stringTable = myVersionInfo.getStringFileInfo().getFirstStringTable();
    if (stringTable != null) {
      stringTable.setStringValue(key, value);
    }
  }

  public void injectBitmap(int id, byte[] bitmapFileData) {
    DirectoryEntry subDirBmp = myRoot.findSubDir("IRD2").findSubDir("IRD" + id);
    RawResource bmpRes = subDirBmp.getRawResource(0);
    // strip off BITMAPFILEHEADER
    byte[] bitmapResourceData = new byte[bitmapFileData.length-14];
    System.arraycopy(bitmapFileData, 14, bitmapResourceData, 0, bitmapResourceData.length);
    bmpRes.setBytes(bitmapResourceData);
  }

  public void injectIcon(int id, final InputStream iconStream) throws IOException {
    File f = File.createTempFile("launcher", "ico");
    Files.copy(new InputSupplier<InputStream>() {
      @Override
      public InputStream getInput() throws IOException {
        return iconStream;
      }
    }, f);
    IconResourceInjector iconInjector = new IconResourceInjector();
    iconInjector.injectIcon(f, myRoot, "IRD" + id);
  }

  public void setVersionNumber(int majorVersion, int minorVersion, int bugfixVersion) {
    int mostSignificantVersion = majorVersion << 16 | minorVersion;
    int leastSignificantVersion = bugfixVersion << 16;
    myVersionInfo.getFixedFileInfo().setFileVersion(mostSignificantVersion, leastSignificantVersion);
    myVersionInfo.getFixedFileInfo().setProductVersion(mostSignificantVersion, leastSignificantVersion);
  }
}
