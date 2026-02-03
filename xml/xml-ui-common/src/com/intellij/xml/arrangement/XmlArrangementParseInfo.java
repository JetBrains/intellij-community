// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.arrangement;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class XmlArrangementParseInfo {

  private final List<XmlElementArrangementEntry> myEntries = new ArrayList<>();

  public @NotNull List<XmlElementArrangementEntry> getEntries() {
    return myEntries;
  }

  public void addEntry(@NotNull XmlElementArrangementEntry entry) {
    myEntries.add(entry);
  }
}
