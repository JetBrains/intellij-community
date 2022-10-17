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

package com.pme.exe.res.icon;

import com.pme.exe.Bin;
import com.pme.exe.res.*;
import com.pme.util.OffsetTrackingInputStream;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Sergey Zhulin
 * Date: Apr 28, 2006
 * Time: 10:56:55 AM
 */
public class IconResourceInjector {

  public static void injectIcon(InputStream stream, DirectoryEntry root, int iconId) throws IOException {
    IconFile iconFile = new IconFile();
    iconFile.read(new OffsetTrackingInputStream(new DataInputStream(stream)));

    long languageId = getLanguage(root);

    DirectoryEntry iconsDir = root.findSubDir(DirectoryEntry.RT_ICON);
    Bin.ArrayOfBins<IconDirectory> sourceIcons = iconFile.getIcons();
    if (sourceIcons.size() < iconsDir.getSubDirs().size()) {
      throw new IOException(
        String.format("Number of icons in template file '%d' is larger than in provided icon file '%d'",
                      iconsDir.getSubDirs().size(), sourceIcons.size()));
    }

    updateGroupIcon(root, iconId, iconFile);

    for (int i = 0; i < sourceIcons.size(); i++) {
      IconDirectory iconDirectory = sourceIcons.get(i);
      DirectoryEntry subDirIcon = iconsDir.findSubDir(i + 1);
      if (subDirIcon == null) {
        subDirIcon = insertIcon(iconsDir, i+1, languageId);
      }
      subDirIcon.getRawResource().setBytes(iconDirectory.getBytes().getBytes());
    }
  }

  private static long getLanguage(DirectoryEntry root) {
    // Language ID is Name of third level entries
    Set<Long> languages = new HashSet<>();

    // First search across icons only
    {
      DirectoryEntry first = root.findSubDir(DirectoryEntry.RT_ICON);
      for (DirectoryEntry second : first.getSubDirs()) {
        for (DataEntry third : second.getData()) {
          languages.add(third.getLanguage().getValue());
        }
      }
    }
    if (languages.size() == 1) {
      return languages.iterator().next();
    }

    // Then across whole file
    for (DirectoryEntry first : root.getSubDirs()) {
      for (DirectoryEntry second : first.getSubDirs()) {
        for (DataEntry third : second.getData()) {
          languages.add(third.getLanguage().getValue());
        }
      }
    }
    if (languages.size() != 1) {
      throw new IllegalStateException("There should be only one language in resources, but found: " + languages);
    }
    return languages.iterator().next();
  }

  private static void updateGroupIcon(DirectoryEntry root, int iconId, IconFile iconFile) throws IOException {
    DirectoryEntry subDirGroupIcon = root.findSubDir(DirectoryEntry.RT_GROUP_ICON).findSubDir(iconId);
    RawResource groupIcon = subDirGroupIcon.getRawResource();

    GroupIconResource gir = new GroupIconResource();
    gir.getHeader().copyFrom(iconFile.getHeader());
    Bin.ArrayOfBins<IconDirectory> sourceIcons = iconFile.getIcons();
    for (int i = 0; i < sourceIcons.size(); i++) {
      IconDirectory icon = sourceIcons.get(i);
      GroupIconResourceDirectory bin = new GroupIconResourceDirectory();
      bin.copyFrom(icon);
      bin.getDwId().setValue(i + 1);
      gir.getIcons().addBin(bin);
    }

    long size = gir.sizeInBytes();
    ByteArrayOutputStream bytesStream = new ByteArrayOutputStream((int)size);
    DataOutputStream stream = new DataOutputStream(bytesStream);
    gir.write(stream);
    groupIcon.setBytes(bytesStream.toByteArray());
  }

  private static DirectoryEntry insertIcon(DirectoryEntry level1, int index, long languageId) {
    // We should construct and correctly add second and third level entries to a resource
    // Second level contains ID of icon
    // Third level contains languageId and raw data

    ResourceSectionReader section = level1.getSection();

    EntryDescription description2 = new EntryDescription();
    description2.getNameW().setValue(index);
    DirectoryEntry level2 = new DirectoryEntry(section, description2, index);

    level1.addDirectoryEntry(level2);
    level1.addIdEntry(description2);

    EntryDescription description3 = new EntryDescription();
    description3.getNameW().setValue(languageId);
    Bin.DWord offset = description3.getOffsetToData();
    DataEntry level3 = new DataEntry(section, offset, description3.getNameW());

    level2.addIdEntry(description3);
    level2.addDataEntry(level3);

    level3.initRawData();

    return level2;
  }
}
