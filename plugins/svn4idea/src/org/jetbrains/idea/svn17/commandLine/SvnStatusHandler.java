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
package org.jetbrains.idea.svn17.commandLine;

import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.idea.svn17.portable.PortableStatus;
import org.jetbrains.idea.svn17.portable.StatusCallbackConvertor;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 7:59 PM
 */
public class SvnStatusHandler extends DefaultHandler {
  private String myChangelistName;
  private List<PortableStatus> myDefaultListStatuses;
  private MultiMap<String, PortableStatus> myCurrentListChanges;
  private PortableStatus myPending;

  private final List<ElementHandlerBase> myParseStack;
  private final Map<String, Getter<ElementHandlerBase>> myElementsMap;
  private final DataCallback myDataCallback;
  private final File myBase;
  private final StringBuilder mySb;
  private boolean myAnythingReported;

  public SvnStatusHandler(final DataCallback dataCallback, File base, final Convertor<File, SVNInfo> infoGetter) {
    myBase = base;
    myParseStack = new ArrayList<ElementHandlerBase>();
    myParseStack.add(new Fake());

    myElementsMap = new HashMap<String, Getter<ElementHandlerBase>>();
    fillElements();

    if (dataCallback != null) {
      myDataCallback = new DataCallback() {
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
    } else {
      myDataCallback = new DataCallback() {
        @Override
        public void switchPath() {
          myAnythingReported = true;
          if (myChangelistName == null) {
            myDefaultListStatuses.add(myPending);
          } else {
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

  private void newPending(final Convertor<File, SVNInfo> infoGetter) {
    final PortableStatus status = new PortableStatus();
    myPending = status;
    status.setInfoGetter(new Getter<SVNInfo>() {
      @Override
      public SVNInfo get() {
        return infoGetter.convert(status.getFile());
      }
    });
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
    myElementsMap.put("status", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Status();
      }
    });
    myElementsMap.put("author", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Author();
      }
    });
    myElementsMap.put("changelist", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Changelist();
      }
    });
    myElementsMap.put("commit", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Commit();
      }
    });
    myElementsMap.put("date", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Date();
      }
    });
    myElementsMap.put("entry", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Entry(myBase);
      }
    });
    myElementsMap.put("target", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new Target();
      }
    });
    myElementsMap.put("wc-status", new Getter<ElementHandlerBase>() {
      @Override
      public ElementHandlerBase get() {
        return new WcStatus();
      }
    });
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {
    //
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
        newChild.updateStatus(attributes, myPending);
        myParseStack.add(newChild);
        return;
      } else {
        // go up
        current.postEffect(myDataCallback);
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

  @Override
  public void endDocument() throws SAXException {
    assertSAX(! myParseStack.isEmpty());
    for (int i = myParseStack.size() - 1; i >= 0; -- i) {
      ElementHandlerBase current = myParseStack.get(i);
      current.postEffect(myDataCallback);
    }
    myParseStack.clear();
  }

  private static void assertSAX(final boolean shouldBeTrue) throws SAXException {
    if (! shouldBeTrue) {
      throw new SAXException("can not parse output");
    }
  }

  private static class Fake extends ElementHandlerBase {
    private Fake() {
      super(new String[]{"status"}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status) throws SAXException {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending) {
    }
  }

  private static class Date extends ElementHandlerBase {
    private Date() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status) throws SAXException {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending) {
      final SVNDate date = SVNDate.parseDate(s);
      //if (SVNDate.NULL.equals(date)) return;
      pending.setRemoteDate(date);
    }
  }

  private static class Author extends ElementHandlerBase {
    private Author() {
      super(new String[]{}, new String[]{});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status) throws SAXException {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending) {
      pending.setRemoteAuthor(s);
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
    protected void updateStatus(Attributes attributes, PortableStatus status) throws SAXException {
      final String revision = attributes.getValue("revision");
      if (! StringUtil.isEmptyOrSpaces(revision)) {
        try {
          final long number = Long.parseLong(revision);
          status.setRemoteRevision(SVNRevision.create(number));
        } catch (NumberFormatException e) {
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
    public void characters(String s, PortableStatus pending) {
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
      super(new String[]{"commit"}, new String[]{});
    }

    /*<wc-status
       props="none"
       wc-locked="true"
       item="normal"
       revision="120">*/

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status) throws SAXException {
      final String props = attributes.getValue("props");
      assertSAX(props != null);
      final SVNStatusType propertiesStatus = StatusCallbackConvertor.convert(org.apache.subversion.javahl.types.Status.Kind.valueOf(props));
      status.setPropertiesStatus(propertiesStatus);
      final String item = attributes.getValue("item");
      assertSAX(item != null);
      final SVNStatusType contentsStatus = StatusCallbackConvertor.convert(org.apache.subversion.javahl.types.Status.Kind.valueOf(item));
      status.setContentsStatus(contentsStatus);

      if (SVNStatusType.STATUS_CONFLICTED.equals(propertiesStatus) || SVNStatusType.STATUS_CONFLICTED.equals(contentsStatus)) {
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

      final String revision = attributes.getValue("revision");
      if (! StringUtil.isEmptyOrSpaces(revision)) {
        try {
          final long number = Long.parseLong(revision);
          status.setRevision(SVNRevision.create(number));
        } catch (NumberFormatException e) {
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
    public void characters(String s, PortableStatus pending) {
    }
  }

  private static class Entry extends ElementHandlerBase {
    private final File myBase;

    private Entry(final File base) {
      super(new String[]{"wc-status"}, new String[]{});
      myBase = base;
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status) throws SAXException {
      final String path = attributes.getValue("path");
      assertSAX(path != null);
      final File file = new File(myBase, path);
      status.setFile(file);
      final boolean exists = file.exists();
      if (exists) {
        status.setKind(exists, file.isDirectory() ? SVNNodeKind.DIR : SVNNodeKind.FILE);
      } else {
        status.setKind(exists, SVNNodeKind.UNKNOWN);
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
    public void characters(String s, PortableStatus pending) {
    }
  }

  private static class Changelist extends ElementHandlerBase {
    private String myName;
    
    private Changelist() {
      super(new String[]{}, new String[]{"entry"});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status) throws SAXException {
      final String name = attributes.getValue("name");
      assertSAX(! StringUtil.isEmptyOrSpaces(name));
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
    public void characters(String s, PortableStatus pending) {
    }
  }

  private static class Target extends ElementHandlerBase {
    private Target() {
      super(new String[]{}, new String[]{"entry"});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status) {
    }

    @Override
    public void postEffect(DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending) {
    }
  }

  private static class Status extends ElementHandlerBase {
    private Status() {
      super(new String[]{"target"}, new String[]{"changelist"});
    }

    @Override
    protected void updateStatus(Attributes attributes, PortableStatus status) {
    }

    @Override
    public void postEffect(final DataCallback callback) {
    }

    @Override
    public void preEffect(DataCallback callback) {
    }

    @Override
    public void characters(String s, PortableStatus pending) {
    }
  }

  public abstract static class ElementHandlerBase {
    private final Set<String> myAwaitedChildren;
    private final Set<String> myAwaitedChildrenMultiple;

    ElementHandlerBase(String[] awaitedChildren, String[] awaitedChildrenMultiple) {
      myAwaitedChildren = new HashSet<String>(Arrays.asList(awaitedChildren));
      myAwaitedChildrenMultiple = new HashSet<String>(Arrays.asList(awaitedChildrenMultiple));
    }

    protected abstract void updateStatus(Attributes attributes, PortableStatus status) throws SAXException;
    public abstract void postEffect(final DataCallback callback);
    public abstract void preEffect(final DataCallback callback);

    public boolean startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (myAwaitedChildrenMultiple.contains(qName)) {
        return true;
      }
      return myAwaitedChildren.remove(qName);
    }

    public abstract void characters(String s, PortableStatus pending);
  }

  public interface DataCallback {
    void switchPath();
    void switchChangeList(final String newList);
  }
}
