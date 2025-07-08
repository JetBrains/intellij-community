// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.arrangement;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.XML_ATTRIBUTE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.XML_TAG;

public class XmlArrangementVisitor extends XmlElementVisitor {

  private final Stack<XmlElementArrangementEntry> myStack = new Stack<>();

  private final XmlArrangementParseInfo myInfo;
  private final Collection<? extends TextRange> myRanges;

  public XmlArrangementVisitor(@NotNull XmlArrangementParseInfo info, @NotNull Collection<? extends TextRange> ranges) {
    myInfo = info;
    myRanges = ranges;
  }

  @Override
  public void visitXmlFile(@NotNull XmlFile file) {
    XmlDocument document = file.getDocument();
    List<XmlTag> tags = PsiTreeUtil.getChildrenOfTypeAsList(document, XmlTag.class);

    for (XmlTag tag : tags) {
      if (tag != null) {
        tag.accept(this);
      }
    }
  }

  @Override
  public void visitXmlTag(@NotNull XmlTag tag) {
    final XmlElementArrangementEntry entry = createNewEntry(tag, XML_TAG, tag.getName(), tag.getNamespace());
    processEntry(entry, tag);
    if (entry != null) {
      postProcessTag(tag, entry);
    }
  }

  protected void postProcessTag(@NotNull XmlTag xmlTagValue, @NotNull XmlElementArrangementEntry entry) {
  }

  @Override
  public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
    final XmlElementArrangementEntry entry = createNewEntry(attribute, XML_ATTRIBUTE, attribute.getName(), attribute.getNamespace());
    processEntry(entry, null);
  }

  private void processEntry(@Nullable XmlElementArrangementEntry entry, @Nullable PsiElement nextElement) {
    if (entry == null || nextElement == null) {
      return;
    }
    myStack.push(entry);
    try {
      nextElement.acceptChildren(this);
    }
    finally {
      myStack.pop();
    }
  }

  private @Nullable XmlElementArrangementEntry createNewEntry(@NotNull PsiElement element,
                                                              @NotNull ArrangementSettingsToken type,
                                                              @Nullable String name,
                                                              @Nullable String namespace) {
    TextRange range = element.getTextRange();
    if (range.getStartOffset() == 0 && range.getEndOffset() == 0 || !isWithinBounds(range)) {
      return null;
    }
    ArrangementEntry current = getCurrent();
    if (current != null && type == XML_ATTRIBUTE) {
      current = current.getChildren().get(0);
    }
    final XmlElementArrangementEntry entry = new XmlElementArrangementEntry(current, range, type, name, namespace, true);
    if (type == XML_TAG) {
      ASTNode startName = XmlChildRole.START_TAG_NAME_FINDER.findChild(element.getNode());
      assert startName != null;
      ASTNode end = XmlChildRole.START_TAG_END_FINDER.findChild(element.getNode());
      end = end == null ? XmlChildRole.EMPTY_TAG_END_FINDER.findChild(element.getNode()) : end;
      TextRange attributesRange = new UnfairTextRange(startName.getTextRange().getEndOffset(),
                                                end != null ? end.getStartOffset() : range.getEndOffset());
      if (attributesRange.getLength() > 0) {
        entry.addChild(new XmlElementArrangementEntry(entry, attributesRange, XML_ATTRIBUTE, null, null, false));
      }
    }

    if (current == null) {
      myInfo.addEntry(entry);
    }
    else {
      ((DefaultArrangementEntry)current).addChild(entry);
    }
    return entry;
  }

  private @Nullable DefaultArrangementEntry getCurrent() {
    return myStack.isEmpty() ? null : myStack.peek();
  }

  private boolean isWithinBounds(@NotNull TextRange range) {
    for (TextRange textRange : myRanges) {
      if (textRange.intersects(range)) {
        return true;
      }
    }
    return false;
  }
  
  
}
