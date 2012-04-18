/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/27/12
 * Time: 1:00 PM
 */
public class SvnInfoHandler extends DefaultHandler {
  private final File myBase;
  private final Consumer<SVNInfo> myInfoConsumer;
  private Map<File, SVNInfo> myResultsMap;
  private SvnInfoStructure myPending;
  private final Map<String, Getter<ElementHandlerBase>> myElementsMap;
  private final List<ElementHandlerBase> myParseStack;
  private final StringBuilder mySb;

  public SvnInfoHandler(File base, final Consumer<SVNInfo> infoConsumer) {
    myBase = base;
    myInfoConsumer = infoConsumer;
    myPending = new SvnInfoStructure();
    myElementsMap = new HashMap<String, Getter<ElementHandlerBase>>();
    fillElements();
    myParseStack = new ArrayList<ElementHandlerBase>();
    myParseStack.add(new Fake());
    myResultsMap = new HashMap<File, SVNInfo>();
    mySb = new StringBuilder();
  }

  private void switchPending() throws SAXException {
    final SVNInfo info;
    try {
      info = myPending.convert();
    }
    catch (SVNException e) {
      throw new SAXException(e);
    }
    if (myInfoConsumer != null) {
      myInfoConsumer.consume(info);
    }
    myResultsMap.put(info.getFile(), info);
    myPending = new SvnInfoStructure();
  }

  @Override
  public void endDocument() throws SAXException {
    assertSAX(! myParseStack.isEmpty());
    for (int i = myParseStack.size() - 1; i >= 0; -- i) {
      ElementHandlerBase current = myParseStack.get(i);
      if (current instanceof Entry) {
        switchPending();
        break;
      }
    }
    myParseStack.clear();
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    assertSAX(! myParseStack.isEmpty());
    ElementHandlerBase current = myParseStack.get(myParseStack.size() - 1);
    if (mySb.length() > 0) {
      current.characters(mySb.toString().trim(), myPending);
      mySb.setLength(0);
    }

    while (true) {
      final boolean createNewChild = current.startElement(uri, localName, qName, attributes);
      if (createNewChild) {
        assertSAX(myElementsMap.containsKey(qName));
        final ElementHandlerBase newChild = myElementsMap.get(qName).get();
        newChild.updateInfo(attributes, myPending);
        myParseStack.add(newChild);
        return;
      } else {
        // go up
        if (current instanceof Entry) {
          switchPending();
        }
        myParseStack.remove(myParseStack.size() - 1);
        assertSAX(! myParseStack.isEmpty());
        current = myParseStack.get(myParseStack.size() - 1);
      }
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    assertSAX(! myParseStack.isEmpty());
    mySb.append(ch, start, length);
  }

  public Map<String, Getter<ElementHandlerBase>> getElementsMap() {
    return myElementsMap;
  }

  private void fillElements() {
    myElementsMap.put("copy-from-url", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new CopyFromUrl();
      }
    });
    myElementsMap.put("copy-from-rev", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new CopyFromRev();
      }
    });
    myElementsMap.put("changelist", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new ChangeList();
      }
    });
    myElementsMap.put("author", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Author();
      }
    });
    myElementsMap.put("checksum", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Checksum();
      }
    });
    myElementsMap.put("commit", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Commit();
      }
    });
    myElementsMap.put("conflict", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Conflict();
      }
    });
    myElementsMap.put("cur-base-file", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new CurBase();
      }
    });
    myElementsMap.put("date", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Date();
      }
    });
    myElementsMap.put("depth", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Depth();
      }
    });
    myElementsMap.put("entry", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Entry(myBase);
      }
    });
    myElementsMap.put("info", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Info();
      }
    });
    myElementsMap.put("prev-base-file", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new PrevBase();
      }
    });
    myElementsMap.put("prev-wc-file", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new PrevWc();
      }
    });
    myElementsMap.put("prop-file", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new PropFile();
      }
    });
    myElementsMap.put("repository", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Repository();
      }
    });
    myElementsMap.put("root", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Root();
      }
    });
    myElementsMap.put("schedule", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Schedule();
      }
    });
    myElementsMap.put("text-updated", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new TextUpdated();
      }
    });
    myElementsMap.put("tree-conflict", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new TreeConflict();
      }
    });
    myElementsMap.put("url", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Url();
      }
    });
    myElementsMap.put("uuid", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Uuid();
      }
    });
    myElementsMap.put("version", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Version();
      }
    });
    myElementsMap.put("wc-info", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new WcInfo();
      }
    });
    myElementsMap.put("wcroot-abspath", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new WcRoot();
      }
    });
  }

  public Map<File, SVNInfo> getResultsMap() {
    return myResultsMap;
  }

  private static void assertSAX(final boolean shouldBeTrue) throws SAXException {
    if (! shouldBeTrue) {
      throw new SAXException("can not parse output");
    }
  }

  private static class Version extends ElementHandlerBase {
    private Version() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
      final String side = attributes.getValue("side");
      if ("source-left".equals(side)) {
        final SvnInfoStructure.ConflictVersion conflictVersion = new SvnInfoStructure.ConflictVersion();
        structure.myTreeConflict.mySourceLeft = conflictVersion;
        setConflictFields(attributes, conflictVersion);
      } else if ("source-right".equals(side)) {
        final SvnInfoStructure.ConflictVersion conflictVersion = new SvnInfoStructure.ConflictVersion();
        structure.myTreeConflict.mySourceRight = conflictVersion;
        setConflictFields(attributes, conflictVersion);
      }
    }

    private void setConflictFields(Attributes attributes, SvnInfoStructure.ConflictVersion conflictVersion) {
      conflictVersion.myKind = attributes.getValue("kind");
      conflictVersion.myPathInRepo = attributes.getValue("path-in-repos");
      conflictVersion.myRepoUrl = attributes.getValue("repos-url");
      conflictVersion.myRevision = attributes.getValue("revision");
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
    }
  }

  private static class TreeConflict extends ElementHandlerBase {
    private TreeConflict() {
      super(new String[]{}, new String[]{"version"});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
      final SvnInfoStructure.TreeConflictDescription d = new SvnInfoStructure.TreeConflictDescription();
      structure.myTreeConflict = d;
      final String operation = attributes.getValue("operation");
      if (! StringUtil.isEmptyOrSpaces(operation)) {
        d.myOperation = operation;
      }
      final String kind = attributes.getValue("kind");
      if (! StringUtil.isEmptyOrSpaces(kind)) {
        d.myKind = kind;
      }
      final String reason = attributes.getValue("reason");
      if (! StringUtil.isEmptyOrSpaces(reason)) {
        d.myReason = reason;
      }
      final String victim = attributes.getValue("victim");
      if (! StringUtil.isEmptyOrSpaces(victim)) {
        d.myVictim = victim;
      }
      final String action = attributes.getValue("action");
      if (! StringUtil.isEmptyOrSpaces(action)) {
        d.myAction = action;
      }
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
    }
  }
  
  private static class PropFile extends ElementHandlerBase {
    private PropFile() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      // todo check whether base should be added
      structure.myPropRejectFile = s;
    }
  }
  
  private static class CurBase extends ElementHandlerBase {
    private CurBase() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      structure.myConflictWorking = s;
    }
  }

  private static class PrevWc extends ElementHandlerBase {
    private PrevWc() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      structure.myConflictNew = s;
    }
  }

  private static class PrevBase extends ElementHandlerBase {
    private PrevBase() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      // todo path? or plus base
      structure.myConflictOld = s;
    }
  }

  private static class Conflict extends ElementHandlerBase {
    private Conflict() {
      super(new String[]{"prev-base-file","prev-wc-file","cur-base-file","prop-file"}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
    }
  }

  private static class Date extends ElementHandlerBase {
    private Date() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      final SVNDate date = SVNDate.parseDate(s);
      structure.myCommittedDate = date;
    }
  }

  private static class Author extends ElementHandlerBase {
    private Author() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      structure.myAuthor = s;
    }
  }

  private static class Commit extends ElementHandlerBase {
    private Commit() {
      super(new String[]{"author","date"}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
      final String revision = attributes.getValue("revision");
      try {
        final long number = Long.parseLong(revision);
        structure.myCommittedRevision = number;
      } catch (NumberFormatException e) {
        throw new SAXException(e);
      }
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
    }
  }

  private static class Checksum extends ElementHandlerBase {
    private Checksum() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      structure.myChecksum = s;
    }
  }

  private static class TextUpdated extends ElementHandlerBase {
    private TextUpdated() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      final SVNDate date = SVNDate.parseDate(s);
      structure.myTextTime = date;
    }
  }

  private static class Depth extends ElementHandlerBase {
    private Depth() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      structure.myDepth = SVNDepth.fromString(s);
    }
  }

  private static class Schedule extends ElementHandlerBase {
    private Schedule() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      structure.mySchedule = s;
    }
  }

  private static class WcRoot extends ElementHandlerBase {
    private WcRoot() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      // there is no such thing???
    }
  }
  
  private static class ChangeList extends ElementHandlerBase {
    private ChangeList() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      structure.myChangelistName = s;
    }
  }

  private static class CopyFromUrl extends ElementHandlerBase {
    private CopyFromUrl() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      try {
        structure.myCopyFromURL = SVNURL.parseURIEncoded(s);
      }
      catch (SVNException e) {
        throw new SAXException(e);
      }
    }
  }

  private static class CopyFromRev extends ElementHandlerBase {
    private CopyFromRev() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      try {
        final long number = Long.parseLong(s);
        structure.myCopyFromRevision = number;
      } catch (NumberFormatException e) {
        throw new SAXException(e);
      }
    }
  }

  private static class WcInfo extends ElementHandlerBase {
    private WcInfo() {
      super(new String[]{"wcroot-abspath", "schedule", "depth", "text-updated", "checksum", "changelist", "copy-from-url",
      "copy-from-rev"}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
    }
  }
  
  private static class Uuid extends ElementHandlerBase {
    private Uuid() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      structure.myUuid = s;
    }
  }

  private static class Root extends ElementHandlerBase {
    private Root() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      try {
        structure.myRootURL = SVNURL.parseURIEncoded(s);
      }
      catch (SVNException e) {
        throw new SAXException(e);
      }
    }
  }

  private static class Repository extends ElementHandlerBase {
    private Repository() {
      super(new String[]{"root", "uuid"}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
    }
  }

  private static class Url extends ElementHandlerBase {
    private Url() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) throws SAXException {
      try {
        structure.myUrl = SVNURL.parseURIEncoded(s);
      }
      catch (SVNException e) {
        throw new SAXException(e);
      }
    }
  }

  private static class Entry extends ElementHandlerBase {
    private final File myBase;

    private Entry(final File base) {
      super(new String[]{"url","repository","wc-info","commit","conflict","tree-conflict"}, new String[]{});
      myBase = base;
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
      final String kind = attributes.getValue("kind");
      assertSAX(! StringUtil.isEmptyOrSpaces(kind));
      structure.myKind = SVNNodeKind.parseKind(kind);
      
      final String path = attributes.getValue("path");
      assertSAX(! StringUtil.isEmptyOrSpaces(path));
      structure.myFile = new File(myBase, path);

      final String revision = attributes.getValue("revision");
      assertSAX(! StringUtil.isEmptyOrSpaces(revision));
      try {
        final long number = Long.parseLong(revision);
        structure.myRevision = number;
      } catch (NumberFormatException e) {
        structure.myRevision = -1;
        //throw new SAXException(e);
      }
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
    }
  }

  private static class Info extends ElementHandlerBase {
    private Info() {
      super(new String[]{}, new String[]{"entry"});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
    }
  }

  private static class Fake extends ElementHandlerBase {
    private Fake() {
      super(new String[]{"info"}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
    }
  }

  private abstract static class ElementHandlerBase {
    private final Set<String> myAwaitedChildren;
    private final Set<String> myAwaitedChildrenMultiple;

    ElementHandlerBase(String[] awaitedChildren, String[] awaitedChildrenMultiple) {
      myAwaitedChildren = new HashSet<String>(Arrays.asList(awaitedChildren));
      myAwaitedChildrenMultiple = new HashSet<String>(Arrays.asList(awaitedChildrenMultiple));
    }

    protected abstract void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException;

    public boolean startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (myAwaitedChildrenMultiple.contains(qName)) {
        return true;
      }
      return myAwaitedChildren.remove(qName);
    }

    public abstract void characters(String s, SvnInfoStructure structure) throws SAXException;
  }
}
