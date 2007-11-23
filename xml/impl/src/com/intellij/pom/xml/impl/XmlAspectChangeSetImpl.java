package com.intellij.pom.xml.impl;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class XmlAspectChangeSetImpl implements XmlChangeSet {
  private final PomModel myModel;
  private List<XmlChange> myChanges = new ArrayList<XmlChange>();
  private List<XmlFile> myChangedFiles = new ArrayList<XmlFile>();

  public XmlAspectChangeSetImpl(PomModel model) {
    myModel = model;
  }

  public XmlAspectChangeSetImpl(final PomModel model, final XmlFile xmlFile) {
    this(model);
    addChangedFile(xmlFile);
  }

  public List<XmlChange> getChanges(){
    return Collections.unmodifiableList(myChanges);
  }

  public PomModelAspect getAspect() {
    return myModel.getModelAspect(XmlAspect.class);
  }

  public void merge(PomChangeSet blocked) {
    final List<XmlChange> changes = ((XmlAspectChangeSetImpl)blocked).myChanges;
    for (XmlChange xmlChange : changes) {
      add(xmlChange);
    }
  }

  public void add(XmlChange xmlChange) {
    myChanges.add(xmlChange);
  }

  public void clear() {
    myChanges.clear();
  }

  @NotNull
  public Iterable<XmlFile> getChangedFiles() {
    return myChangedFiles;
  }

  public String toString(){
    final StringBuffer buffer = new StringBuffer();
    final Iterator<XmlChange> iterator = myChanges.iterator();
    while (iterator.hasNext()) {
      XmlChange xmlChange = iterator.next();
      buffer.append("(");
      buffer.append(xmlChange.toString());
      buffer.append(")");
      if(iterator.hasNext()) buffer.append(", ");
    }
    return buffer.toString();
  }

  public void addChangedFile(final XmlFile xmlFile) {
    myChangedFiles.add(xmlFile);
  }
}
