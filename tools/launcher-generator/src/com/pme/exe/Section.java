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

package com.pme.exe;

import com.pme.exe.res.ResourceSectionReader;

import java.io.IOException;
import java.io.OutputStreamWriter;


public abstract class Section extends Bin.Structure {
  @SuppressWarnings("SpellCheckingInspection") public static final String RESOURCES_SECTION_NAME = ".rsrc";
  @SuppressWarnings("SpellCheckingInspection") public static final String RELOCATIONS_SECTION_NAME = ".reloc";

  private final String myName;

  protected Section(ImageSectionHeader header, String name) {
    super(name);
    myName = name;
    addOffsetHolder(header.getPointerToRawData());
    addSizeHolder(header.getSizeOfRawData());
  }

  public static Section newSection(ImageSectionHeader header, ImageOptionalHeader imageOptionalHeader) {
    if (RESOURCES_SECTION_NAME.equals(header.getSectionName())) {
      return new ResourceSectionReader(header, imageOptionalHeader);
    }
    else {
      return new Simple(header);
    }
  }

  public String getSectionName() {
    return myName;
  }

  @Override
  public void report(OutputStreamWriter writer) throws IOException {
    _report(writer, "Section name: " + myName);
    super.report(writer);
  }

  public static class Simple extends Section {

    public Simple(ImageSectionHeader header) {
      super(header, header.getSectionName());
      addMember(new Bin.Bytes("Section Raw Data", header.getSizeOfRawData()));
    }
  }
}
