package com.intellij.pom.xml;

import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.xml.impl.events.XmlChange;
import com.intellij.psi.xml.XmlFile;

public interface XmlChangeSet extends PomChangeSet {
  XmlChange[] getChanges();

  PomModelAspect getAspect();

  void add(XmlChange xmlChange);

  void clear();

  XmlFile getChangedFile();
}
