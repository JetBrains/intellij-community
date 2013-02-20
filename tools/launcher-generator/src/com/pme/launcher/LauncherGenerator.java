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
import com.pme.exe.Bin;
import com.pme.exe.ExeReader;
import com.pme.exe.SectionReader;
import com.pme.exe.res.DirectoryEntry;
import com.pme.exe.res.RawResource;
import com.pme.exe.res.ResourceSectionReader;
import com.pme.exe.res.StringTableDirectory;
import com.pme.exe.res.icon.IconResourceInjector;
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
    myReader = new ExeReader(myTemplate.getName());
    RandomAccessFile stream = new RandomAccessFile(myTemplate, "r");
    myReader.read(stream);
    stream.close();
    SectionReader sectionReader = myReader.getSectionReader(".rsrc");
    ResourceSectionReader resourceReader = (ResourceSectionReader) sectionReader.getMember(".rsrc");
    myRoot = resourceReader.getRoot();
    DirectoryEntry subDir = myRoot.findSubDir("IRD6");
    myStringTableDirectory = new StringTableDirectory(subDir);

    DirectoryEntry viDir = myRoot.findSubDir("IRD16").findSubDir( "IRD1" );
    Bin.Bytes viBytes = viDir.getRawResource( 0 ).getBytes();
    ByteArrayInputStream bytesStream = new ByteArrayInputStream(viBytes.getBytes());

    myVersionInfo = new VersionInfo();
    myVersionInfo.read(new OffsetTrackingInputStream(new DataInputStream(bytesStream)));
  }

  public void generate() throws IOException {
    myStringTableDirectory.save();

    myReader.resetOffsets(0);

    myExePath.getParentFile().mkdirs();
    myExePath.createNewFile();
    RandomAccessFile exeStream = new RandomAccessFile(myExePath, "rw");
    myReader.write(exeStream);
    exeStream.close();
  }

  public void setResourceString(int id, String value) {
    myStringTableDirectory.setString(id, value);
  }

  public void injectBitmap(int id, byte[] bitmapData) {
    DirectoryEntry subDirBmp = myRoot.findSubDir("IRD2").findSubDir("IRD" + id);
    RawResource bmpRes = subDirBmp.getRawResource(0);
    bmpRes.setBytes(bitmapData);
  }

  public void injectIcon(String name, final InputStream iconStream) throws IOException {
    File f = File.createTempFile("launcher", "ico");
    Files.copy(new InputSupplier<InputStream>() {
      @Override
      public InputStream getInput() throws IOException {
        return iconStream;
      }
    }, f);
    IconResourceInjector iconInjector = new IconResourceInjector();
    iconInjector.injectIcon(f, myRoot, "IRD101");
  }
}
