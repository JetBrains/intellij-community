package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.List;

class ChangeListManagerSerialization {
  @NonNls static final String ATT_NAME = "name";
  @NonNls static final String ATT_COMMENT = "comment";
  @NonNls static final String ATT_DEFAULT = "default";
  @NonNls static final String ATT_READONLY = "readonly";
  @NonNls static final String ATT_VALUE_TRUE = "true";
  @NonNls static final String ATT_CHANGE_TYPE = "type";
  @NonNls static final String ATT_CHANGE_BEFORE_PATH = "beforePath";
  @NonNls static final String ATT_CHANGE_AFTER_PATH = "afterPath";
  @NonNls static final String ATT_PATH = "path";
  @NonNls static final String ATT_MASK = "mask";
  @NonNls static final String NODE_LIST = "list";
  @NonNls static final String NODE_IGNORED = "ignored";
  @NonNls static final String NODE_CHANGE = "change";

  private final IgnoredFilesComponent myIgnoredIdeaLevel;
  private final ChangeListWorker myWorker;

  ChangeListManagerSerialization(final IgnoredFilesComponent ignoredIdeaLevel, final ChangeListWorker worker) {
    myIgnoredIdeaLevel = ignoredIdeaLevel;
    myWorker = worker;
  }

  @SuppressWarnings({"unchecked"})
  public void readExternal(final Element element) throws InvalidDataException {
    final List<Element> listNodes = (List<Element>)element.getChildren(ChangeListManagerSerialization.NODE_LIST);
    for (Element listNode : listNodes) {
      readChangeList(listNode);
    }
    final List<Element> ignoredNodes = (List<Element>)element.getChildren(ChangeListManagerSerialization.NODE_IGNORED);
    for (Element ignoredNode: ignoredNodes) {
      readFileToIgnore(ignoredNode);
    }
  }

  private void readChangeList(final Element listNode) {
    // workaround for loading incorrect settings (with duplicate changelist names)
    final String changeListName = listNode.getAttributeValue(ChangeListManagerSerialization.ATT_NAME);
    LocalChangeList list = myWorker.getCopyByName(changeListName);
    if (list == null) {
      list = myWorker.addChangeList(changeListName, listNode.getAttributeValue(ChangeListManagerSerialization.ATT_COMMENT));
    }
    //noinspection unchecked
    final List<Element> changeNodes = (List<Element>)listNode.getChildren(ChangeListManagerSerialization.NODE_CHANGE);
    for (Element changeNode : changeNodes) {
      try {
        myWorker.addChangeToList(changeListName, readChange(changeNode));
      }
      catch (OutdatedFakeRevisionException e) {
        // Do nothing. Just skip adding outdated revisions to the list.
      }
    }

    if (ChangeListManagerSerialization.ATT_VALUE_TRUE.equals(listNode.getAttributeValue(ChangeListManagerSerialization.ATT_DEFAULT))) {
      myWorker.setDefault(list.getName());
    }
    if (ChangeListManagerSerialization.ATT_VALUE_TRUE.equals(listNode.getAttributeValue(ChangeListManagerSerialization.ATT_READONLY))) {
      list.setReadOnly(true);
    }
  }

  private void readFileToIgnore(final Element ignoredNode) {
    IgnoredFileBean bean = new IgnoredFileBean();
    String path = ignoredNode.getAttributeValue(ChangeListManagerSerialization.ATT_PATH);
    if (path != null) {
      bean.setPath(path);
    }
    String mask = ignoredNode.getAttributeValue(ChangeListManagerSerialization.ATT_MASK);
    if (mask != null) {
      bean.setMask(mask);
    }
    myIgnoredIdeaLevel.add(bean);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final List<LocalChangeList> changeListList = myWorker.getListsCopy();
    for (LocalChangeList list : changeListList) {
        Element listNode = new Element(ChangeListManagerSerialization.NODE_LIST);
        element.addContent(listNode);
        if (list.isDefault()) {
          listNode.setAttribute(ChangeListManagerSerialization.ATT_DEFAULT, ChangeListManagerSerialization.ATT_VALUE_TRUE);
        }
        if (list.isReadOnly()) {
          listNode.setAttribute(ChangeListManagerSerialization.ATT_READONLY, ChangeListManagerSerialization.ATT_VALUE_TRUE);
        }

        listNode.setAttribute(ChangeListManagerSerialization.ATT_NAME, list.getName());
        listNode.setAttribute(ChangeListManagerSerialization.ATT_COMMENT, list.getComment());
        for (Change change : list.getChanges()) {
          writeChange(listNode, change);
        }
      }
    final IgnoredFileBean[] filesToIgnore = myIgnoredIdeaLevel.getFilesToIgnore();
    for(IgnoredFileBean bean: filesToIgnore) {
        Element fileNode = new Element(ChangeListManagerSerialization.NODE_IGNORED);
        element.addContent(fileNode);
        String path = bean.getPath();
        if (path != null) {
          fileNode.setAttribute("path", path);
        }
        String mask = bean.getMask();
        if (mask != null) {
          fileNode.setAttribute("mask", mask);
        }
      }
  }

  private static void writeChange(final Element listNode, final Change change) {
    Element changeNode = new Element(ChangeListManagerSerialization.NODE_CHANGE);
    listNode.addContent(changeNode);
    changeNode.setAttribute(ChangeListManagerSerialization.ATT_CHANGE_TYPE, change.getType().name());

    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    changeNode.setAttribute(ChangeListManagerSerialization.ATT_CHANGE_BEFORE_PATH, bRev != null ? bRev.getFile().getPath() : "");
    changeNode.setAttribute(ChangeListManagerSerialization.ATT_CHANGE_AFTER_PATH, aRev != null ? aRev.getFile().getPath() : "");
  }

  private static Change readChange(Element changeNode) throws OutdatedFakeRevisionException {
    String bRev = changeNode.getAttributeValue(ChangeListManagerSerialization.ATT_CHANGE_BEFORE_PATH);
    String aRev = changeNode.getAttributeValue(ChangeListManagerSerialization.ATT_CHANGE_AFTER_PATH);
    return new Change(StringUtil.isEmpty(bRev) ? null : new FakeRevision(bRev), StringUtil.isEmpty(aRev) ? null : new FakeRevision(aRev));
  }

  static final class OutdatedFakeRevisionException extends Exception {}
}
