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

import com.pme.exe.ExeReader;
import com.pme.exe.SectionReader;
import com.pme.exe.Bin;
import com.pme.exe.res.ResourceSectionReader;
import com.pme.exe.res.DirectoryEntry;
import com.pme.exe.res.RawResource;
import com.pme.exe.res.StringTable;
import com.pme.exe.res.bmp.PictureResourceInjector;
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
  private File myIcon;
  private File myBmp;
  private File myExePath;
  private int mySplashTimeout = 3;
  private String myJreBundlePath;
  private String myVmParameters;
  private String myStartJar;
  private String myWorkDir;
  private String myJavaVersion;
  private boolean mySearchJdkOnly = false;

  public LauncherGenerator(File template, File exePath) {
    myTemplate = template;
    myExePath = exePath;
  }

  public void setWorkDir(String workDir) {
    myWorkDir = workDir;
  }

  public void setSplashTimeout(int splashTimeout) {
    mySplashTimeout = splashTimeout;
  }

  public void setJreBundlePath(String jreBundlePath) {
    myJreBundlePath = jreBundlePath;
  }

  public void setVmParameters(String vmParameters) {
    myVmParameters = vmParameters;
  }

  public void setStartJar(String startJar) {
    myStartJar = startJar;
  }

  public void setIcon(File icon) {
    myIcon = icon;
  }

  public void setBmp(File bmp) {
    myBmp = bmp;
  }

  public void generate() throws IOException {
    ExeReader reader = new ExeReader(myTemplate.getName());
    RandomAccessFile stream = new RandomAccessFile(myTemplate, "r");
    reader.read(stream);
    stream.close();
    SectionReader sectionReader = reader.getSectionReader(".rsrc");
    ResourceSectionReader resourceReader = (ResourceSectionReader) sectionReader.getMember(".rsrc");
    DirectoryEntry root = resourceReader.getRoot();
    DirectoryEntry subDir = root.findSubDir("IRD6");
    DirectoryEntry ird7 = subDir.findSubDir("IRD7");

    RawResource rawResource = ird7.getRawResource(0);
    Bin.Bytes bytes = rawResource.getBytes();
    StringTable srtingTable = new StringTable(bytes.getBytes());

    if (myBmp != null) {
      srtingTable.setString(0, "splash"); // show splash
    }
    srtingTable.setString(1, "alive"); // stay alive
    srtingTable.setString(2, "" + mySplashTimeout); // splash timeout
    if (myJreBundlePath != null) {
      srtingTable.setString(3, myJreBundlePath); // jre bundled path
    }
    if (myVmParameters != null) {
      srtingTable.setString(4, myVmParameters); // PROP_VM_PARAMETERS
    }
    if (myStartJar != null) {
      srtingTable.setString(5, myStartJar); // PROP_START_JAR
    }
    srtingTable.setString(6, "waitForWindow"); // PROP_WAIT_FOR_WINDOW

    if (myWorkDir != null) {
      srtingTable.setString(7, myWorkDir); // PROP_WORK_DIR
    }

    if (myJavaVersion != null) {
      StringBuffer buffer = new StringBuffer( myJavaVersion.length() );
      for( int i = 0; i < myJavaVersion.length(); ++i){
        char ch = myJavaVersion.charAt( i );
        if ( Character.isDigit( ch ) || ch == '.' ){
          buffer.append( ch );
        }
      }
      srtingTable.setString(8, buffer.toString()); // PROP_JAVA_VERSION
    }
    if (mySearchJdkOnly) {
      srtingTable.setString(9, "SearchJdkOnly"); // PROP_SEARCH_JDK_ONLY
    }

    byte[] a = srtingTable.getBytes();
    rawResource.setBytes(a);

    if (myIcon != null) {
      IconResourceInjector iconInjector = new IconResourceInjector();
      iconInjector.injectIcon(myIcon, root, "IRD101");
    }

    if (myBmp != null) {
      PictureResourceInjector bmpInjector = new PictureResourceInjector();
      bmpInjector.inject(myBmp, root, "IRD104");
    }



    DirectoryEntry viDir = root.findSubDir("IRD16").findSubDir( "IRD1" );
    Bin.Bytes viBytes = viDir.getRawResource( 0 ).getBytes();
    ByteArrayInputStream bytesStream = new ByteArrayInputStream(viBytes.getBytes());

    VersionInfo viReader = new VersionInfo();
    viReader.read(new OffsetTrackingInputStream(new DataInputStream(bytesStream)));

    reader.resetOffsets(0);

    myExePath.getParentFile().mkdirs();
    myExePath.createNewFile();
    RandomAccessFile exeStream = new RandomAccessFile(myExePath, "rw");
    reader.write(exeStream);
    exeStream.close();
  }

  public void setJavaVersion(String javaVersion) {
    myJavaVersion = javaVersion;
  }

  public void setSearchJdkOnly(boolean searchJdkOnly) {
    mySearchJdkOnly = searchJdkOnly;
  }
}
