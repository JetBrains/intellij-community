package com.intellij.xml.arrangement;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.XML_ATTRIBUTE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.XML_TAG;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlArrangementVisitor extends XmlElementVisitor {

  private final Stack<XmlElementArrangementEntry> myStack = new Stack<>();

  private final XmlArrangementParseInfo myInfo;
  private final Collection<TextRange> myRanges;

  public XmlArrangementVisitor(@NotNull XmlArrangementParseInfo info, @NotNull Collection<TextRange> ranges) {
    myInfo = info;
    myRanges = ranges;
  }

  @Override
  public void visitXmlFile(XmlFile file) {
    final XmlTag tag = file.getRootTag();

    if (tag != null) {
      tag.accept(this);
    }
  }

  @Override
  public void visitXmlTag(XmlTag tag) {
    final XmlElementArrangementEntry entry = createNewEntry(
      tag.getTextRange(), XML_TAG, null, null, true);
    processEntry(entry, tag);
  }

  @Override
  public void visitXmlAttribute(XmlAttribute attribute) {
    final XmlElementArrangementEntry entry = createNewEntry(
      attribute.getTextRange(), XML_ATTRIBUTE, attribute.getName(), attribute.getNamespace(), true);
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

  @Nullable
  private XmlElementArrangementEntry createNewEntry(@NotNull TextRange range,
                                                    @NotNull ArrangementSettingsToken type,
                                                    @Nullable String name,
                                                    @Nullable String namespace,
                                                    boolean canBeMatched) {
    if (range.getStartOffset() == 0 && range.getEndOffset() == 0 || !isWithinBounds(range)) {
      return null;
    }
    final DefaultArrangementEntry current = getCurrent();
    final XmlElementArrangementEntry entry = new XmlElementArrangementEntry(
      current, range, type, name, namespace, canBeMatched);

    if (current == null) {
      myInfo.addEntry(entry);
    }
    else {
      current.addChild(entry);
    }
    return entry;
  }

  @Nullable
  private DefaultArrangementEntry getCurrent() {
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
