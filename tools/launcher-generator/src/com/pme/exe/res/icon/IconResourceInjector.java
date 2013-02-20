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

package com.pme.exe.res.icon;

import com.pme.exe.res.*;
import com.pme.exe.Bin;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;

/**
 * Date: Apr 28, 2006
 * Time: 10:56:55 AM
 */
public class IconResourceInjector {

  public void injectIcon(File file, DirectoryEntry root, String iconId) throws IOException {
    IconFile iconFile = new IconFile(file);
    iconFile.read();

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

    DirectoryEntry iconsDir = root.findSubDir("IRD3");
    DirectoryEntry subDirIcon = iconsDir.findSubDir("IRD1");
    RawResource rawResource = subDirIcon.getRawResource(0);
    Level iconFileLevel = (Level) iconFile.getMember("Level");
    IconDirectory iconDirectory = (IconDirectory) iconFileLevel.getMembers().get(0);
    rawResource.setBytes(iconDirectory.getRawBytes());

    ArrayList icons = iconFileLevel.getMembers();
    for (int i = 1; i < icons.size(); i++) {
      insertIcon(iconsDir, (IconDirectory) icons.get(i), i);
    }
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
