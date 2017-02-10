package com.intellij.xml.arrangement;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlArrangementParseInfo {

  private final List<XmlElementArrangementEntry> myEntries = new ArrayList<>();

  @NotNull
  public List<XmlElementArrangementEntry> getEntries() {
    return myEntries;
  }

  public void addEntry(@NotNull XmlElementArrangementEntry entry) {
    myEntries.add(entry);
  }
}
