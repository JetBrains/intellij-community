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
package org.jetbrains.idea.svn.status;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.lock.Lock;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.util.*;

public class SvnStatusHandler extends DefaultHandler {

  private static final Logger LOG = Logger.getInstance(SvnStatusHandler.class);

  @Nullable
  public static StatusType getStatus(@NotNull String code) {
    StatusType result = StatusType.forStatusOperation(code);

    if (result == null) {
      LOG.info("Unknown status type " + code);
    }

    return result;
  }

  private String myChangelistName;
  private List<PortableStatus> myDefaultListStatuses;
  private MultiMap<String, PortableStatus> myCurrentListChanges;
  private PortableStatus myPending;
  private boolean myInRemoteStatus;
  private Lock.Builder myLockBuilder;

  private final List<ElementHandlerBase> myParseStack;
  private final Map<String, Getter<ElementHandlerBase>> myElementsMap;
  private final DataCallback myDataCallback;
  private final File myBase;
  private final StringBuilder mySb;
  private boolean myAnythingReported;

  public SvnStatusHandler(final ExternalDataCallback dataCallback, File base, final Convertor<File, Info> infoGetter) {
    myBase = base;
    myParseStack = new ArrayList<>();
    myParseStack.add(new Fake());

    myElementsMap = new HashMap<>();
    fillElements();

    if (dataCallback != null) {
      myDataCallback = new DataCallback() {
        @Override
        public void startLock() {
          myLockBuilder = new Lock.Builder();
        }

        @Override
        public void endLock() {
          if (myInRemoteStatus) {
            myPending.setRemoteLock(myLockBuilder.build());
          }
          else {
            myPending.setLocalLock(myLockBuilder.build());
          }
          myLockBuilder = null;
        }

        @Override
        public void startRemoteStatus() {
          myInRemoteStatus = true;
        }

        @Override
        public void endRemoteStatus() {
          myInRemoteStatus = false;
        }

        @Override
        public void switchPath() {
          myAnythingReported = true;
          dataCallback.switchPath();
          newPending(infoGetter);
        }

        @Override
        public void switchChangeList(String newList) {
          dataCallback.switchChangeList(newList);
        }
      };
    }
    else {
      myDataCallback = new DataCallback() {
        @Override
        public void startLock() {
          myLockBuilder = new Lock.Builder();
        }

        @Override
        public void endLock() {
          if (myInRemoteStatus) {
            myPending.setRemoteLock(myLockBuilder.build());
          }
          else {
            myPending.setLocalLock(myLockBuilder.build());
          }
          myLockBuilder = null;
        }

        @Override
        public void startRemoteStatus() {
          myInRemoteStatus = true;
        }

        @Override
        public void endRemoteStatus() {
          myInRemoteStatus = false;
        }

        @Override
        public void switchPath() {
          myAnythingReported = true;
          if (myChangelistName == null) {
            myDefaultListStatuses.add(myPending);
          }
          else {
            myCurrentListChanges.putValue(myChangelistName, myPending);
          }
          newPending(infoGetter);
        }

        @Override
        public void switchChangeList(String newList) {
          myChangelistName = newList;
        }
      };
    }
    newPending(infoGetter);
    mySb = new StringBuilder();
  }

  public boolean isAnythingReported() {
    return myAnythingReported;
  }

  private void newPending(final Convertor<File, Info> infoGetter) {
    final PortableStatus status = new PortableStatus();
    myPending = status;
    status.setInfoGetter(() -> infoGetter.convert(status.getFile()));
  }

  public PortableStatus getPending() {
    return myPending;
  }

  public List<PortableStatus> getDefaultListStatuses() {
    return myDefaultListStatuses;
  }

  public MultiMap<String, PortableStatus> getCurrentListChanges() {
    return myCurrentListChanges;
  }

  private void fillElements() {
    myElementsMap.put("repos-status", () -> new ReposStatus());
    myElementsMap.put("lock", () -> new LockElement());

    myElementsMap.put("token", () -> new LockToken());
    myElementsMap.put("owner", () -> new LockOwner());
    myElementsMap.put("comment", () -> new LockComment());
    myElementsMap.put("created", () -> new LockCreatedDate());
    // --
    myElementsMap.put("status", () -> new Status());
    myElementsMap.put("author", () -> new Author());
    myElementsMap.put("changelist", () -> new Changelist());
    myElementsMap.put("commit", () -> new Commit());
    myElementsMap.put("date", () -> new Date());
    myElementsMap.put("entry", () -> new Entry(myBase));
    myElementsMap.put("target", () -> new Target());
    myElementsMap.put("against", () -> new Against());
    myElementsMap.put("wc-status", () -> new WcStatus());
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    //
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    assertSAX(!myParseStack.isEmpty());
    ElementHandlerBase current = myParseStack.get(myParseStack.size() - 1);
    if (mySb.length() > 0) {
      current.characters(mySb.toString().trim(), myPending, myLockBuilder);
      mySb.setLength(0);
    }

    while (true) {
      final boolean createNewChild = current.startElement(uri, localName, qName, attributes);
      if (createNewChild) {
        assertSAX(myElementsMap.containsKey(qName));
        final ElementHandlerBase newChild = myElementsMap.get(qName).get();
        newChild.preAttributesEffect(myDataCallback);
        newChild.updateStatus(attributes, myPending, myLockBuilder);
        newChild.preEffect(myDataCallback);
        myParseStack.add(newChild);
        return;
      }
      else {
        // go up
        current.postEffect(myDataCallback);
        myParseStack.remove(myParseStack.size() - 1);
        assertSAX(!myParseStack.isEmpty());
        current = myParseStack.get(myParseStack.size() - 1);
      }
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    assertSAX(!myParseStack.isEmpty());
    mySb.append(ch, start, length);
  }

  @Override
  public void endDocument() throws SAXException {
    assertSAX(!myParseStack.isEmpty());
    for (int i = myParseStack.size() - 1; i >= 0; --i) {
      ElementHandlerBase current = myParseStack.get(i);
      current.postEffect(myDataCallback);
    }
    myParseStack.clear();
  }

  private static void assertSAX(final boolean shouldBeTrue) throws SAXException {
    if (!shouldBeTrue) {
      throw new SAXException("can not parse output");
    }
  }

  private static StatusType parseContentsStatus(Attributes attributes) throws SAXException {
    final String item = attributes.getValue("item");
    assertSAX(item != null);
    return getStatus(item);
  }

  private static StatusType parsePropertiesStatus(Attributes attributes) throws SAXException {
    final String props = attributes.getValue("props");
    assertSAX(props != null);
    return getStatus(props);
  }

  private static class Fake extends ElementHandlerBase {
    private Fake() {
      super(new String[]{"status"}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  private static class Date extends ElementHandlerBase {
    private Date() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  private static class Author extends ElementHandlerBase {
    private Author() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  /*        <commit
              revision="25">
            <author>admin</author>
            <date>2011-11-09T12:21:02.401530Z</date>
  */
  private static class Commit extends ElementHandlerBase {
    private Commit() {
      super(new String[]{"author", "date"}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
      final String revision = attributes.getValue("revision");
      if (!StringUtil.isEmpty(revision)) {
        status.setCommittedRevision(Revision.of(Long.valueOf(revision)));
      }
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  /*<lock>
  <token>opaquelocktoken:27ee743a-5376-fc4a-a209-b7834e1a3f39</token>
  <owner>admin</owner>
  <comment>LLL</comment>
  <created>2012-02-21T09:59:39.771077Z</created>
  </lock>*/

  /*<lock>
  <token>opaquelocktoken:e21e93d2-0623-b347-bb39-900b01387555</token>
  <owner>admin</owner>
  <comment>787878</comment>
  <created>2012-02-21T10:17:29.160005Z</created>
  </lock>
  </wc-status>
  <repos-status
     props="none"
     item="none">
  <lock>
  <token>opaquelocktoken:e21e93d2-0623-b347-bb39-900b01387555</token>
  <owner>admin</owner>
  <comment>787878</comment>
  <created>2012-02-21T10:17:29.160005Z</created>
  </lock>
  </repos-status>
  </entry>*/

  private static class LockCreatedDate extends ElementHandlerBase {
    private LockCreatedDate() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
      lock.setCreationDate(SvnUtil.parseDate(s));
    }
  }

  private static class LockComment extends ElementHandlerBase {
    private LockComment() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
      lock.setComment(s);
    }
  }

  private static class LockOwner extends ElementHandlerBase {
    private LockOwner() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
      lock.setOwner(s);
    }
  }

  private static class LockToken extends ElementHandlerBase {
    private LockToken() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
      lock.setToken(s);
    }
  }

  private static class LockElement extends ElementHandlerBase {
    private LockElement() {
      super(new String[]{"token", "owner", "comment", "created"}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(DataCallback callback) {
      callback.endLock();
    }

    @Override
    public void preAttributesEffect(DataCallback callback) {
      callback.startLock();
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  private static class ReposStatus extends ElementHandlerBase {
    private ReposStatus() {
      super(new String[]{"lock"}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) throws SAXException {
      final StatusType propertiesStatus = parsePropertiesStatus(attributes);
      status.setRemotePropertiesStatus(propertiesStatus);

      final StatusType contentsStatus = parseContentsStatus(attributes);
      status.setRemoteContentsStatus(contentsStatus);
    }

    @Override
    public void postEffect(DataCallback callback) {
      callback.endRemoteStatus();
    }

    @Override
    public void preAttributesEffect(DataCallback callback) {
      callback.startLock();
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  /*      <wc-status   props="none"   copied="true"   tree-conflicted="true"   item="added">
      <wc-status       props="none"   item="unversioned">
      <wc-status       props="none"   item="added"   revision="-1">
      <wc-status       props="none"   item="modified"   revision="112">
      <wc-status       props="conflicted"  item="normal"  revision="112">
  */
  private static class WcStatus extends ElementHandlerBase {
    private WcStatus() {
      super(new String[]{"commit", "lock"}, new String[]{});
    }

    /*<wc-status
       props="none"
       wc-locked="true"
       item="normal"
       revision="120">*/

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) throws SAXException {
      final StatusType propertiesStatus = parsePropertiesStatus(attributes);
      status.setPropertiesStatus(propertiesStatus);
      final StatusType contentsStatus = parseContentsStatus(attributes);
      status.setContentsStatus(contentsStatus);

      if (StatusType.STATUS_CONFLICTED.equals(propertiesStatus) || StatusType.STATUS_CONFLICTED.equals(contentsStatus)) {
        status.setIsConflicted(true);
      }

      // optional
      final String locked = attributes.getValue("wc-locked");
      if (locked != null && Boolean.parseBoolean(locked)) {
        status.setIsLocked(true);
      }
      final String copied = attributes.getValue("copied");
      if (copied != null && Boolean.parseBoolean(copied)) {
        status.setIsCopied(true);
      }
      final String treeConflicted = attributes.getValue("tree-conflicted");
      if (treeConflicted != null && Boolean.parseBoolean(treeConflicted)) {
        status.setIsConflicted(true);
      }

      final String switched = attributes.getValue("switched");
      if (switched != null && Boolean.parseBoolean(switched)) {
        status.setIsSwitched(true);
      }

      final String revision = attributes.getValue("revision");
      if (!StringUtil.isEmptyOrSpaces(revision)) {
        try {
          final long number = Long.parseLong(revision);
          status.setRevision(Revision.of(number));
        }
        catch (NumberFormatException e) {
          throw new SAXException(e);
        }
      }
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  private static class Entry extends ElementHandlerBase {
    private final File myBase;

    private Entry(final File base) {
      super(new String[]{"wc-status", "repos-status"}, new String[]{});
      myBase = base;
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) throws SAXException {
      final String path = attributes.getValue("path");
      assertSAX(path != null);
      final File file = SvnUtil.resolvePath(myBase, path);
      status.setFile(file);
      final boolean exists = file.exists();
      if (exists) {
        status.setKind(exists, NodeKind.from(file.isDirectory()));
      }
      else {
        // this is a hack. This is done so because of strange svn native client output:
        /*
        c:\TestProjects\sortedProjects\Subversion\local\withExt82420\mod4>svn st --xml
        <?xml version="1.0" encoding="UTF-8"?>
        <status>
        <target
           path=".">
        <entry
           path="mod4">
        <wc-status
           props="none"
           item="unversioned">
        </wc-status>
        </entry>
        </target>
        </status>

        while

c:\TestProjects\sortedProjects\Subversion\local\withExt82420\mod4>dir
 Volume in drive C has no label.
 Volume Serial Number is B4EA-B379

 Directory of c:\TestProjects\sortedProjects\Subversion\local\withExt82420\mod4

03/09/2012  05:30 PM    <DIR>          .
03/09/2012  05:30 PM    <DIR>          ..
03/09/2012  05:30 PM               437 mod4.iml
03/09/2012  05:30 PM    <DIR>          src

and no "mod4" under

        */
        final StatusType ns = status.getNodeStatus();
        if (myBase.getName().equals(path) && !StatusType.MISSING.equals(ns) &&
            !StatusType.STATUS_DELETED.equals(ns)) {
          status.setKind(true, NodeKind.DIR);
          status.setFile(myBase);
          status.setPath("");
          return;
        }
        status.setKind(exists, NodeKind.UNKNOWN);
      }
      status.setPath(path);
    }

    @Override
    public void postEffect(DataCallback callback) {
      callback.switchPath();
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  private static class Changelist extends ElementHandlerBase {
    private String myName;

    private Changelist() {
      super(new String[]{}, new String[]{"entry"});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) throws SAXException {
      final String name = attributes.getValue("name");
      assertSAX(!StringUtil.isEmptyOrSpaces(name));
      myName = name;
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
      callback.switchChangeList(myName);
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  private static class Target extends ElementHandlerBase {
    private Target() {
      super(new String[]{"against"}, new String[]{"entry"});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  private static class Against extends ElementHandlerBase {
    private Against() {
      super(new String[0], new String[0]);
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  private static class Status extends ElementHandlerBase {
    private Status() {
      super(new String[]{"target"}, new String[]{"changelist"});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) {
    }

    @Override
    public void postEffect(final DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending, Lock.Builder lock) {
    }
  }

  public abstract static class ElementHandlerBase {
    private final Set<String> myAwaitedChildren;
    private final Set<String> myAwaitedChildrenMultiple;

    ElementHandlerBase(String[] awaitedChildren, String[] awaitedChildrenMultiple) {
      myAwaitedChildren = new HashSet<>(Arrays.asList(awaitedChildren));
      myAwaitedChildrenMultiple = new HashSet<>(Arrays.asList(awaitedChildrenMultiple));
    }

    protected abstract void updateStatus(Attributes attributes, PortableStatus status, Lock.Builder lock) throws SAXException;

    public abstract void postEffect(final DataCallback callback);

    public abstract void preEffect(final DataCallback callback);

    public boolean startElement(String uri, String localName, String qName, Attributes attributes) {
      if (myAwaitedChildrenMultiple.contains(qName)) {
        return true;
      }
      return myAwaitedChildren.remove(qName);
    }

    public abstract void characters(String s, PortableStatus pending, Lock.Builder lock);

    public void preAttributesEffect(DataCallback callback) {}
  }

  public interface ExternalDataCallback {
    void switchPath();

    void switchChangeList(final String newList);
  }

  private interface DataCallback extends ExternalDataCallback {
    void startRemoteStatus();

    void endRemoteStatus();

    void startLock();

    void endLock();

    void switchPath();

    void switchChangeList(final String newList);
  }
}
