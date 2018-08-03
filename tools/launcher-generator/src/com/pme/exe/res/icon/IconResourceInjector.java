// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.icon;

import com.pme.exe.Bin;
import com.pme.exe.res.*;

import java.io.*;
import java.util.ArrayList;

/**
 * @author Sergey Zhulin
 * Date: Apr 28, 2006
 * Time: 10:56:55 AM
 */
public class IconResourceInjector {

  public void injectIcon(File file, DirectoryEntry root, String iconId) throws IOException {
    IconFile iconFile = new IconFile(file);
    iconFile.read();

    //updateGroupIcon(root, iconId, iconFile);

    DirectoryEntry iconsDir = root.findSubDir("IRD3");
    Level iconFileLevel = (Level) iconFile.getMember("Level");
    ArrayList icons = iconFileLevel.getMembers();
    if (icons.size() == iconsDir.getSubDirs().size()) {
      for (int i = 0; i < icons.size(); i++) {
        DirectoryEntry subDirIcon = iconsDir.findSubDir("IRD" + (i+1));
        IconDirectory iconDirectory = (IconDirectory) iconFileLevel.getMembers().get(i);
        RawResource rawResource = subDirIcon.getRawResource(0);
        rawResource.setBytes(iconDirectory.getRawBytes());
      }
    }
    else {
      throw new IOException("Count of icons in template file doesn't match the count of icons in provided icon file");
    }
  }

  private void updateGroupIcon(DirectoryEntry root, String iconId, IconFile iconFile) throws IOException {
    DirectoryEntry subDirGroupIcon = root.findSubDir("IRD14").findSubDir(iconId);
    RawResource groupIcon = subDirGroupIcon.getRawResource(0);

    Bin.Value idCount = iconFile.getStructureMember("Header").getValueMember("idCount");

    GroupIconResource groupIconResource = new GroupIconResource(idCount);
    groupIconResource.copyFrom(iconFile);
    Level level = (Level) groupIconResource.getMember("Level");
    ArrayList directories = level.getMembers();
    for (int i = 0; i < directories.size(); ++i) {
      GroupIconResourceDirectory grpDir = (GroupIconResourceDirectory) directories.get(i);
      grpDir.getValueMember("dwId").setValue(i + 1);
    }
    long size = groupIconResource.sizeInBytes();
    ByteArrayOutputStream bytesStream = new ByteArrayOutputStream((int) size);
    DataOutputStream stream = new DataOutputStream(bytesStream);
    groupIconResource.write(stream);
    groupIcon.getBytes().setBytes(bytesStream.toByteArray());
  }

  private void insertIcon(DirectoryEntry iconsDir, IconDirectory iconDirectory, int index) {
    EntryDescription entryDescription = new EntryDescription();
    Bin.Value name = entryDescription.getValueMember("Name");
    name.setValue(index + 1);

    DirectoryEntry entryDirIcon2 = new DirectoryEntry(iconsDir.getSection(), entryDescription, name.getValue());
    iconsDir.insertDirectoryEntry(index, entryDirIcon2);
    iconsDir.addIdEntry(entryDescription);

    EntryDescription entry409 = new EntryDescription();
    Bin.Value name409 = entry409.getValueMember("Name");
    name409.setValue(0x409);

    entryDirIcon2.addIdEntry(entry409);

    Bin.Value offset = entry409.getValueMember("OffsetToData");
    DataEntry dataEntry = new DataEntry(entryDirIcon2.getSection(), offset);

    entryDirIcon2.insertDataEntry(index, dataEntry);
    dataEntry.insertRawData(index);
    RawResource rawRes = dataEntry.getRawResource();
    rawRes.setBytes(iconDirectory.getRawBytes());
  }
}
