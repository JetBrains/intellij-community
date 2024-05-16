// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.pme.launcher;

import com.pme.exe.*;
import com.pme.exe.res.DirectoryEntry;
import com.pme.exe.res.RawResource;
import com.pme.exe.res.ResourceSectionReader;
import com.pme.exe.res.StringTableDirectory;
import com.pme.exe.res.icon.IconResourceInjector;
import com.pme.exe.res.vi.VersionInfo;
import com.pme.util.OffsetTrackingInputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.String.format;

public class LauncherGenerator {
  private final Path myTemplate;
  private final Path myExePath;
  private StringTableDirectory myStringTableDirectory;
  private DirectoryEntry myRoot;
  private ExeReader myReader;
  private VersionInfo myVersionInfo;

  public LauncherGenerator(Path template, Path exePath) {
    myTemplate = template;
    myExePath = exePath;
  }

  public void load() throws IOException {
    myReader = new ExeReader(myTemplate.getFileName().toString());
    try (var stream = new OffsetTrackingInputStream(new DataInputStream(Files.newInputStream(myTemplate)))) {
      myReader.read(stream);
    }

    var resourceSection = (ResourceSectionReader)myReader.getSectionReader(Section.RESOURCES_SECTION_NAME);
    myRoot = resourceSection.getRoot();

    var subDir = myRoot.findSubDir(DirectoryEntry.RT_STRING);
    myStringTableDirectory = subDir != null ? new StringTableDirectory(subDir) : null;

    var versionInfoResource = getVersionInfoResource();
    myVersionInfo = new VersionInfo();
    myVersionInfo.read(new OffsetTrackingInputStream(new DataInputStream(new ByteArrayInputStream(versionInfoResource.getBytes()))));
  }

  private RawResource getVersionInfoResource() {
    return myRoot.findSubDir(DirectoryEntry.RT_VERSION).findSubDir(1).getRawResource();
  }

  public void generate() throws IOException {
    if (myStringTableDirectory != null) {
      myStringTableDirectory.save();
    }

    saveVersionInfo();

    myReader.resetOffsets(0);
    myReader.sectionVirtualAddressFixup();

    Files.deleteIfExists(myExePath);
    Files.createDirectories(myExePath.getParent());
    try (var stream = new DataOutputStream(Files.newOutputStream(myExePath))) {
      myReader.write(stream);
    }

    verifySize();
    verifySections();
    verifyVersionInfo();
  }

  private void saveVersionInfo() throws IOException {
    var out = new ByteArrayOutputStream();
    myVersionInfo.resetOffsets(0);
    myVersionInfo.write(new DataOutputStream(out));
    getVersionInfoResource().setBytes(out.toByteArray());
  }

  private void verifySize() throws IOException {
    long fileSize = Files.size(myExePath), exeSize = myReader.sizeInBytes();
    if (fileSize != exeSize) {
      throw new RuntimeException(format("Produced file size mismatch, on disk: %d, in memory %d", fileSize, exeSize));
    }
  }

  private void verifySections() {
    var imageOptionalHeader = myReader.getPeHeader().getImageOptionalHeader();
    int FileAlignment = (int)imageOptionalHeader.getFileAlignment().getValue();
    int SectionAlignment = (int)imageOptionalHeader.getSectionAlignment().getValue();

    var errors = new ArrayList<String>();
    var sections = myReader.getSectionHeaders();
    for (var header : sections) {
      var name = header.getSectionName();
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
        errors.add(format("SizeOfRawData of section '%s' isn't dividable by 'FileAlignment' (%#x): %#x", name, FileAlignment, sizeOfRawData));
      }
      if (pointerToRawData % FileAlignment != 0) {
        errors.add(format("PointerToRawData of section '%s' isn't dividable by 'FileAlignment' (%#x): %#x", name, FileAlignment, pointerToRawData));
      }
      if (virtualAddress % SectionAlignment != 0) {
        errors.add(format("VirtualAddress of section '%s' isn't dividable by 'SectionAlignment' (%#x): %#x", name, SectionAlignment, virtualAddress));
      }
      if (name.equals(Section.RESOURCES_SECTION_NAME) && virtualSize < sizeOfRawData) {
        errors.add(format("VirtualSize of section '%s' is smaller than SizeOfRawData (%#x): %#x", name, sizeOfRawData, virtualSize));
      }
    }

    var imageDataDirs = imageOptionalHeader.getImageDataDirectories();
    check(errors, "resources",
          imageDataDirs.get(ImageDataDirectory.IMAGE_DIRECTORY_ENTRY_RESOURCE),
          getSection(sections, Section.RESOURCES_SECTION_NAME));
    check(errors, "relocations",
          imageDataDirs.get(ImageDataDirectory.IMAGE_DIRECTORY_ENTRY_BASERELOC),
          getSection(sections, Section.RELOCATIONS_SECTION_NAME));

    // SizeOfImage: The size of the image, in bytes, including all headers. Must be a multiple of SectionAlignment.
    long sizeOfImage = imageOptionalHeader.getSizeOfImage().getValue();
    if (sizeOfImage % SectionAlignment != 0) {
      errors.add(format("SizeOfImage isn't dividable by 'SectionAlignment' (%#x): %#x", SectionAlignment, sizeOfImage));
    }

    if (!errors.isEmpty()) {
      var msg = new StringBuilder();
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
    for (var header : sections) {
      if (name.equals(header.getSectionName())) {
        return header;
      }
    }
    throw new IllegalStateException("Cannot find section with name " + name);
  }

  private void verifyVersionInfo() throws IOException {
    var data1 = new ByteArrayOutputStream((int)myVersionInfo.sizeInBytes());
    myVersionInfo.resetOffsets(0);
    myVersionInfo.write(new DataOutputStream(data1));

    var copy = new VersionInfo();
    copy.read(new OffsetTrackingInputStream(new DataInputStream(new ByteArrayInputStream(data1.toByteArray()))));
    copy.resetOffsets(0);
    var data2 = new ByteArrayOutputStream((int)copy.sizeInBytes());
    copy.write(new DataOutputStream(data2));

    if (!Arrays.equals(data1.toByteArray(), data2.toByteArray())) {
      throw new IllegalStateException("Load and save of VersionInfo produced different binary results");
    }
  }

  public void setResourceString(int id, String value) {
    if (myStringTableDirectory == null) throw new IllegalStateException("Cannot find string table in " + myTemplate);
    myStringTableDirectory.setString(id, value);
  }

  public void setVersionInfoString(String key, String value) {
    myVersionInfo.getStringFileInfo().getSoleStringTable().setStringValue(key, value);
  }

  @SuppressWarnings("unused")
  public void injectBitmap(int id, byte[] bitmapFileData) {
    DirectoryEntry subDirBmp = myRoot.findSubDir(DirectoryEntry.RT_BITMAP).findSubDir(id);
    RawResource bmpRes = subDirBmp.getRawResource();
    // strip off file header
    byte[] bitmapResourceData = new byte[bitmapFileData.length - 14];
    System.arraycopy(bitmapFileData, 14, bitmapResourceData, 0, bitmapResourceData.length);
    bmpRes.setBytes(bitmapResourceData);
  }

  public void injectIcon(int id, final InputStream iconStream) throws IOException {
    IconResourceInjector.injectIcon(iconStream, myRoot, id);
  }

  public void setFileVersionNumber(int[] parts) {
    myVersionInfo.getFixedFileInfo().setFileVersion(parts[0] << 16 | parts[1], parts[2] << 16 | parts[3]);
  }

  public void setProductVersionNumber(int[] parts) {
    myVersionInfo.getFixedFileInfo().setProductVersion(parts[0] << 16 | parts[1], parts[2] << 16 | parts[3]);
  }
}
