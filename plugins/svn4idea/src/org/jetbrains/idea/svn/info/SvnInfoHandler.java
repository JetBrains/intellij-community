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
package org.jetbrains.idea.svn.info;

import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.lock.Lock;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.util.*;

public class SvnInfoHandler extends DefaultHandler {
  @Nullable private final File myBase;
  private final Consumer<org.jetbrains.idea.svn.info.Info> myInfoConsumer;
  private Map<File, org.jetbrains.idea.svn.info.Info> myResultsMap;
  private SvnInfoStructure myPending;
  private final Map<String, Getter<ElementHandlerBase>> myElementsMap;
  private final List<ElementHandlerBase> myParseStack;
  private final StringBuilder mySb;

  public SvnInfoHandler(@Nullable File base, final Consumer<org.jetbrains.idea.svn.info.Info> infoConsumer) {
    myBase = base;
    myInfoConsumer = infoConsumer;
    myPending = createPending();
    myElementsMap = new HashMap<>();
    fillElements();
    myParseStack = new ArrayList<>();
    myParseStack.add(new Fake());
    myResultsMap = new HashMap<>();
    mySb = new StringBuilder();
  }

  private void switchPending() throws SAXException {
    final org.jetbrains.idea.svn.info.Info info;
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
    myPending = createPending();
  }

  private SvnInfoStructure createPending() {
    SvnInfoStructure pending = new SvnInfoStructure();
    pending.myDepth = org.jetbrains.idea.svn.api.Depth.INFINITY;

    return pending;
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

    while (true) {
      final boolean createNewChild = current.startElement(uri, localName, qName, attributes);
      if (createNewChild) {
        assertSAX(myElementsMap.containsKey(qName));
        final ElementHandlerBase newChild = myElementsMap.get(qName).get();
        newChild.setParent(current);
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
  public void endElement(String uri, String localName, String qName) throws SAXException {
    ElementHandlerBase current = myParseStack.get(myParseStack.size() - 1);
    String value = mySb.toString().trim();

    if (!StringUtil.isEmpty(value)) {
      current.characters(value, myPending);
    }

    mySb.setLength(0);
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
    myElementsMap.put("copy-from-url", () -> new CopyFromUrl());
    myElementsMap.put("copy-from-rev", () -> new CopyFromRev());
    myElementsMap.put("changelist", () -> new ChangeList());
    myElementsMap.put("author", () -> new Author());
    myElementsMap.put("checksum", () -> new Checksum());
    myElementsMap.put("commit", () -> new Commit());
    myElementsMap.put("conflict", () -> new Conflict());
    myElementsMap.put("cur-base-file", () -> new CurBase());
    myElementsMap.put("date", () -> new Date());
    myElementsMap.put("depth", () -> new Depth());
    myElementsMap.put("entry", () -> new Entry(myBase));
    myElementsMap.put("info", () -> new Info());
    myElementsMap.put("prev-base-file", () -> new PrevBase());
    myElementsMap.put("prev-wc-file", () -> new PrevWc());
    myElementsMap.put("prop-file", () -> new PropFile());
    myElementsMap.put("repository", () -> new Repository());
    myElementsMap.put("root", () -> new Root());
    myElementsMap.put("schedule", () -> new Schedule());
    myElementsMap.put("text-updated", () -> new TextUpdated());
    myElementsMap.put("tree-conflict", () -> new TreeConflict());
    myElementsMap.put("url", () -> new Url());
    myElementsMap.put("relative-url", () -> new RelativeUrl());
    myElementsMap.put("lock", () -> new LockElement());
    myElementsMap.put("token", () -> new LockToken());
    myElementsMap.put("owner", () -> new LockOwner());
    myElementsMap.put("comment", () -> new LockComment());
    myElementsMap.put("created", () -> new LockCreated());
    myElementsMap.put("uuid", () -> new Uuid());
    myElementsMap.put("version", () -> new Version());
    myElementsMap.put("wc-info", () -> new WcInfo());
    myElementsMap.put("moved-to", () -> new MovedPath());
    myElementsMap.put("moved-from", () -> new MovedPath());
    myElementsMap.put("wcroot-abspath", () -> new WcRoot());
  }

  public Map<File, org.jetbrains.idea.svn.info.Info> getResultsMap() {
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
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
      // TODO: Currently information for conflict (not tree-conflict) available in svn 1.8 is not used
      // TODO: And it also not suite well for SVNKit api
      if (getParent() instanceof Conflict) {
        return;
      }

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
    public void characters(String s, SvnInfoStructure structure) {
    }
  }

  private static class TreeConflict extends ElementHandlerBase {
    private TreeConflict() {
      super(new String[]{}, new String[]{"version"});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
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
    public void characters(String s, SvnInfoStructure structure) {
    }
  }
  
  private static class PropFile extends ElementHandlerBase {
    private PropFile() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      // todo check whether base should be added
      structure.myPropRejectFile = s;
    }
  }
  
  private static class CurBase extends ElementHandlerBase {
    private CurBase() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myConflictNew = new File(s).getName();
    }
  }

  private static class PrevWc extends ElementHandlerBase {
    private PrevWc() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myConflictWorking = new File(s).getName();
    }
  }

  private static class PrevBase extends ElementHandlerBase {
    private PrevBase() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myConflictOld = new File(s).getName();
    }
  }

  private static class Conflict extends ElementHandlerBase {
    private Conflict() {
      super(new String[]{"prev-base-file","prev-wc-file","cur-base-file","prop-file"}, new String[]{"version"});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
    }
  }

  private static class Date extends ElementHandlerBase {
    private Date() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myCommittedDate = s;
    }
  }

  private static class Author extends ElementHandlerBase {
    private Author() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
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
    public void characters(String s, SvnInfoStructure structure) {
    }
  }

  private static class Checksum extends ElementHandlerBase {
    private Checksum() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myChecksum = s;
    }
  }

  /**
   * "moved-from" and "moved-to" elements are represented by this class.
   */
  private static class MovedPath extends ElementHandlerBase {

    private MovedPath() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      // TODO: is there some field to initialize from this value?
    }
  }

  private static class TextUpdated extends ElementHandlerBase {
    private TextUpdated() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myTextTime = s;
    }
  }

  private static class Depth extends ElementHandlerBase {
    private Depth() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myDepth = org.jetbrains.idea.svn.api.Depth.from(s);
    }
  }

  private static class Schedule extends ElementHandlerBase {
    private Schedule() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.mySchedule = s;
    }
  }

  private static class WcRoot extends ElementHandlerBase {
    private WcRoot() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      // there is no such thing???
    }
  }
  
  private static class ChangeList extends ElementHandlerBase {
    private ChangeList() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myChangelistName = s;
    }
  }

  private static class CopyFromUrl extends ElementHandlerBase {
    private CopyFromUrl() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
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
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
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
      "copy-from-rev", "moved-to", "moved-from"}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
    }
  }
  
  private static class Uuid extends ElementHandlerBase {
    private Uuid() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myUuid = s;
    }
  }

  private static class Root extends ElementHandlerBase {
    private Root() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
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
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
    }
  }

  private static class Url extends ElementHandlerBase {
    private Url() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
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

  private static class RelativeUrl extends Url{
    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.relativeUrl = s;
    }
  }

  private static class LockElement extends ElementHandlerBase {
    private LockElement() {
      super(new String[]{"token", "owner", "comment", "created"}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
      structure.myLockBuilder = new Lock.Builder();
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
    }
  }

  private static class LockToken extends ElementHandlerBase {
    private LockToken() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myLockBuilder.setToken(s);
    }
  }

  private static class LockOwner extends ElementHandlerBase {
    private LockOwner() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myLockBuilder.setOwner(s);
    }
  }

  private static class LockComment extends ElementHandlerBase {
    private LockComment() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myLockBuilder.setComment(s);
    }
  }

  private static class LockCreated extends ElementHandlerBase {
    private LockCreated() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) {
    }

    @Override
    public void characters(String s, SvnInfoStructure structure) {
      structure.myLockBuilder.setCreationDate(SVNDate.parseDate(s));
    }
  }

  private static class Entry extends ElementHandlerBase {
    @Nullable private final File myBase;

    private Entry(@Nullable final File base) {
      super(new String[]{"url", "relative-url", "lock", "repository","wc-info","commit","tree-conflict"}, new String[]{"conflict"});
      myBase = base;
    }

    @Override
    protected void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException {
      final String kind = attributes.getValue("kind");
      assertSAX(! StringUtil.isEmptyOrSpaces(kind));
      structure.myKind = NodeKind.from(kind);

      if (myBase != null) {
        final String path = attributes.getValue("path");
        assertSAX(!StringUtil.isEmptyOrSpaces(path));
        structure.myFile = SvnUtil.resolvePath(myBase, path);
      }

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
    private ElementHandlerBase parent;

    ElementHandlerBase(String[] awaitedChildren, String[] awaitedChildrenMultiple) {
      myAwaitedChildren = new HashSet<>(Arrays.asList(awaitedChildren));
      myAwaitedChildrenMultiple = new HashSet<>(Arrays.asList(awaitedChildrenMultiple));
    }

    @NotNull
    public ElementHandlerBase getParent() {
      return parent;
    }

    public void setParent(@NotNull ElementHandlerBase parent) {
      this.parent = parent;
    }

    protected abstract void updateInfo(Attributes attributes, SvnInfoStructure structure) throws SAXException;

    public boolean startElement(String uri, String localName, String qName, Attributes attributes) {
      if (myAwaitedChildrenMultiple.contains(qName)) {
        return true;
      }
      return myAwaitedChildren.remove(qName);
    }

    public abstract void characters(String s, SvnInfoStructure structure) throws SAXException;
  }
}
