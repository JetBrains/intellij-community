package com.intellij.pom.xml.impl;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.psi.xml.XmlFile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class XmlAspectChangeSetImpl implements XmlChangeSet {
  private final PomModel myModel;
  private List<XmlChange> myChanges = new ArrayList<XmlChange>();
  private final XmlFile mySubjectToChange;

  public XmlAspectChangeSetImpl(PomModel model, XmlFile fileChanged) {
    myModel = model;
    mySubjectToChange = fileChanged;
  }

  public XmlChange[] getChanges(){
    return myChanges.toArray(new XmlChange[myChanges.size()]);
  }

  public PomModelAspect getAspect() {
    return myModel.getModelAspect(XmlAspect.class);
  }

  public void merge(PomChangeSet blocked) {
    myChanges.addAll(((XmlAspectChangeSetImpl)blocked).myChanges);
  }

  public void add(XmlChange xmlChange) {
    myChanges.add(xmlChange);
  }

  public void clear() {
    myChanges.clear();
  }

  public XmlFile getChangedFile(){
    return mySubjectToChange;
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
}
