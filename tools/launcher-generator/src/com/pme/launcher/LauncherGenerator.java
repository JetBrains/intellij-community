/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013-2022 JetBrains s.r.o.
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

import com.pme.exe.*;
import com.pme.exe.res.DirectoryEntry;
import com.pme.exe.res.RawResource;
import com.pme.exe.res.ResourceSectionReader;
import com.pme.exe.res.StringTableDirectory;
import com.pme.exe.res.icon.IconResourceInjector;
import com.pme.exe.res.vi.StringTable;
import com.pme.exe.res.vi.VersionInfo;
import com.pme.util.OffsetTrackingInputStream;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.String.format;

/**
 * @author Sergey Zhulin
 * Date: May 6, 2006
 * Time: 10:43:01 AM
 */
public class LauncherGenerator {
  private final File myTemplate;
  private final File myExePath;
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
    myReader = new ExeReader(myTemplate.getName());
    myReader.read(stream);
    stream.close();
    ResourceSectionReader resourceSection = (ResourceSectionReader)myReader.getSectionReader(Section.RESOURCES_SECTION_NAME);
    myRoot = resourceSection.getRoot();
    DirectoryEntry subDir = myRoot.findSubDir(DirectoryEntry.RT_STRING);
    myStringTableDirectory = new StringTableDirectory(subDir);

    RawResource versionInfoResource = getVersionInfoResource();
    myVersionInfo = new VersionInfo();
    myVersionInfo.read(new OffsetTrackingInputStream(new DataInputStream(new ByteArrayInputStream(versionInfoResource.getBytes()))));
  }

  private RawResource getVersionInfoResource() {
    DirectoryEntry viDir = myRoot.findSubDir(DirectoryEntry.RT_VERSION).findSubDir(1);
    return viDir.getRawResource();
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
    myReader.sectionVirtualAddressFixup();

    if (myExePath.exists()) {
      //noinspection ResultOfMethodCallIgnored
      myExePath.delete();
    }
    //noinspection ResultOfMethodCallIgnored
    myExePath.getParentFile().mkdirs();
    //noinspection ResultOfMethodCallIgnored
    myExePath.createNewFile();
    RandomAccessFile exeStream = new RandomAccessFile(myExePath, "rw");
    myReader.write(exeStream);
    exeStream.close();

    verifySize();
    verifySections();
    verifyVersionInfo();
  }

  private void verifySize() throws IOException {
    long fileSize = Files.size(myExePath.toPath());
    long exeSize = myReader.sizeInBytes();
    if (fileSize != exeSize) {
      throw new RuntimeException(format("Produced file size mismatch, on disk: %d, in memory %d", fileSize, exeSize));
    }
  }

  private void verifySections() {
    ImageOptionalHeader imageOptionalHeader = myReader.getPeHeader().getImageOptionalHeader();
    final int FileAlignment = (int)imageOptionalHeader.getFileAlignment().getValue();
    final int SectionAlignment = (int)imageOptionalHeader.getSectionAlignment().getValue();

    List<String> errors = new ArrayList<>();
    Bin.ArrayOfBins<ImageSectionHeader> sections = myReader.getSectionHeaders();
    for (ImageSectionHeader header : sections) {
      String name = header.getSectionName();
      long sizeOfRawData = header.getSizeOfRawData().getValue();
      long pointerToRawData = header.getPointerToRawData().getValue();
      long virtualAddress = header.getVirtualAddress().getValue();
      long virtualSize = header.getVirtualSize().getValue();
      if (pointerToRawData == 0) {
        errors.add(format("Section '%s' may not have zero PointerToRawData", name));
      }
      if (virtualAddress == 0) {
        errors.add(format("Section '%s' may not have zero VirtualAddress", name));
      }
      if (sizeOfRawData % FileAlignment != 0) {
        errors.add(
          format("SizeOfRawData of section '%s' isn't dividable by 'FileAlignment' (%#x): %#x", name, FileAlignment, sizeOfRawData));
      }
      if (pointerToRawData % FileAlignment != 0) {
        errors.add(
          format("PointerToRawData of section '%s' isn't dividable by 'FileAlignment' (%#x): %#x", name, FileAlignment, pointerToRawData));
      }
      if (virtualAddress % SectionAlignment != 0) {
        errors.add(
          format("VirtualAddress of section '%s' isn't dividable by 'SectionAlignment' (%#x): %#x", name, SectionAlignment,
                 virtualAddress));
      }
      if (name.equals(Section.RESOURCES_SECTION_NAME) && virtualSize < sizeOfRawData) {
        errors.add(
          format("VirtualSize of section '%s' is smaller than SizeOfRawData (%#x): %#x", name, sizeOfRawData, virtualSize));
      }
    }
    Bin.ArrayOfBins<ImageDataDirectory> imageDataDirs = imageOptionalHeader.getImageDataDirectories();

    check(errors, "resources",
          imageDataDirs.get(ImageDataDirectory.IMAGE_DIRECTORY_ENTRY_RESOURCE),
          getSection(sections, Section.RESOURCES_SECTION_NAME));
    check(errors, "relocations",
          imageDataDirs.get(ImageDataDirectory.IMAGE_DIRECTORY_ENTRY_BASERELOC),
          getSection(sections, Section.RELOCATIONS_SECTION_NAME));


    //SizeOfImage
    //The size of the image, in bytes, including all headers. Must be a multiple of SectionAlignment.
    long sizeOfImage = imageOptionalHeader.getSizeOfImage().getValue();
    if (sizeOfImage % SectionAlignment != 0) {
      errors.add(format("SizeOfImage isn't dividable by 'SectionAlignment' (%#x): %#x", SectionAlignment, sizeOfImage));
    }

    if (!errors.isEmpty()) {
      StringBuilder msg = new StringBuilder();
      msg.append("Output verification failed with ").append(errors.size()).append(" error");
      if (errors.size() > 1) msg.append("s");
      msg.append(":\n");
      for (String error : errors) {
        msg.append('\t').append(error).append('\n');
      }
      throw new RuntimeException(msg.toString());
    }
  }

  private static void check(List<String> errors, String name, ImageDataDirectory idd, ImageSectionHeader ish) {
    long iddSize = idd.getSize().getValue();
    long ishVirtualSize = ish.getVirtualSize().getValue();

    if (iddSize != ishVirtualSize) {
      errors.add(format("Incorrect '%s' sizes: %#x, %#x", name, iddSize, ishVirtualSize));
    }

    long iddAddress = idd.getVirtualAddress().getValue();
    long ishAddress = ish.getVirtualAddress().getValue();

    if (iddAddress != ishAddress) {
      errors.add(format("Incorrect '%s' virtual address: %#x, %#x", name, iddAddress, ishAddress));
    }
  }

  private static ImageSectionHeader getSection(Bin.ArrayOfBins<ImageSectionHeader> sections, String name) {
    for (ImageSectionHeader header : sections) {
      if (name.equals(header.getSectionName())) {
        return header;
      }
    }
    throw new IllegalStateException("Cannot find section with name " + name);
  }

  private void verifyVersionInfo() throws IOException {
    ByteArrayOutputStream data1 = new ByteArrayOutputStream((int)myVersionInfo.sizeInBytes());
    myVersionInfo.resetOffsets(0);
    myVersionInfo.write(new DataOutputStream(data1));

    VersionInfo copy = new VersionInfo();
    copy.read(new OffsetTrackingInputStream(new DataInputStream(new ByteArrayInputStream(data1.toByteArray()))));

    ByteArrayOutputStream data2 = new ByteArrayOutputStream((int)copy.sizeInBytes());
    copy.resetOffsets(0);
    copy.write(new DataOutputStream(data2));

    if (!Arrays.equals(data1.toByteArray(), data2.toByteArray())) {
      throw new IllegalStateException("Load and save of VersionInfo produced different binary results");
    }
  }

  public void setResourceString(int id, String value) {
    myStringTableDirectory.setString(id, value);
  }

  public void setVersionInfoString(String key, String value) {
    StringTable stringTable = myVersionInfo.getStringFileInfo().getSoleStringTable();
    stringTable.setStringValue(key, value);
  }

  public void injectBitmap(int id, byte[] bitmapFileData) {
    DirectoryEntry subDirBmp = myRoot.findSubDir(DirectoryEntry.RT_BITMAP).findSubDir(id);
    RawResource bmpRes = subDirBmp.getRawResource();
    // strip off BITMAPFILEHEADER
    byte[] bitmapResourceData = new byte[bitmapFileData.length - 14];
    System.arraycopy(bitmapFileData, 14, bitmapResourceData, 0, bitmapResourceData.length);
    bmpRes.setBytes(bitmapResourceData);
  }

  public void injectIcon(int id, final InputStream iconStream) throws IOException {
    IconResourceInjector.injectIcon(iconStream, myRoot, id);
  }

  public void setVersionNumber(int majorVersion, int minorVersion, int bugfixVersion) {
    int mostSignificantVersion = majorVersion << 16 | minorVersion;
    int leastSignificantVersion = bugfixVersion << 16;
    myVersionInfo.getFixedFileInfo().setFileVersion(mostSignificantVersion, leastSignificantVersion);
    myVersionInfo.getFixedFileInfo().setProductVersion(mostSignificantVersion, leastSignificantVersion);
  }
}
