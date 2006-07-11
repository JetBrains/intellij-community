package com.intellij.pom.xml.impl;

import com.intellij.lang.ASTNode;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.ReplaceChangeInfo;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.TreeChangeImpl;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.impl.events.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.xml.*;
import com.intellij.util.CharTable;

import java.util.Collections;

public class XmlAspectImpl implements XmlAspect {
  private final PomModel myModel;
  private final TreeAspect myTreeAspect;

  public XmlAspectImpl(PomModel model, TreeAspect aspect) {
    myModel = model;
    myTreeAspect = aspect;
    myModel.registerAspect(XmlAspect.class, this, Collections.singleton((PomModelAspect)myTreeAspect));
  }

  public void update(PomModelEvent event) {
    if (!event.getChangedAspects().contains(myTreeAspect)) return;
    final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(myTreeAspect);
    if (changeSet == null) return;
    final ASTNode rootElement = changeSet.getRootElement();
    final PsiFile file = (PsiFile)rootElement.getPsi();
    if (!(file instanceof XmlFile)) return;
    final XmlAspectChangeSetImpl xmlChangeSet = new XmlAspectChangeSetImpl(myModel, (XmlFile)file);
    event.registerChangeSet(this, xmlChangeSet);

    final ASTNode[] changedElements = changeSet.getChangedElements();
    final CharTable table = ((FileElement)changeSet.getRootElement()).getCharTable();
    for (int i = 0; i < changedElements.length; i++) {
      ASTNode changedElement = changedElements[i];
      TreeChange changesByElement = changeSet.getChangesByElement(changedElement);
      PsiElement psiElement = null;
      while (changedElement != null && (psiElement = changedElement.getPsi()) == null) {
        final ASTNode parent = changedElement.getTreeParent();
        final ChangeInfoImpl changeInfo = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, changedElement);
        changeInfo.compactChange(changedElement, changesByElement);
        changesByElement = new TreeChangeImpl(parent);
        changesByElement.addChange(changedElement, changeInfo);
        changedElement = parent;
      }
      if(changedElement == null) continue;
      final TreeChange finalChangedElement = changesByElement;
      psiElement.accept(new PsiElementVisitor() {
        TreeChange myChange = finalChangedElement;

        public void visitReferenceExpression(PsiReferenceExpression expression) { }

        public void visitElement(PsiElement element) {
          final ASTNode child = element.getNode();
          final ASTNode treeParent = child.getTreeParent();
          if (treeParent == null) return;
          final PsiElement parent = treeParent.getPsi();
          final ChangeInfoImpl changeInfo = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, child);

          changeInfo.compactChange(child, myChange);
          myChange = new TreeChangeImpl(treeParent);

          myChange.addChange(child, changeInfo);
          parent.accept(this);
        }

        public void visitXmlAttribute(XmlAttribute attribute) {
          final ASTNode[] affectedChildren = myChange.getAffectedChildren();
          String oldName = null;
          String oldValue = null;
          for (int j = 0; j < affectedChildren.length; j++) {
            final ASTNode treeElement = affectedChildren[j];
            final ChangeInfo changeByChild = myChange.getChangeByChild(treeElement);
            final int changeType = changeByChild.getChangeType();
            if (treeElement.getElementType() == XmlTokenType.XML_NAME) {
              if (changeType == ChangeInfo.REMOVED) {
                oldName = treeElement.getText();
              }
              else if (changeType == ChangeInfo.REPLACE) {
                oldName = ((ReplaceChangeInfo)changeByChild).getReplaced().getText();
              }
            }
            if (treeElement.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE) {
              if (changeType == ChangeInfo.REMOVED) {
                oldValue = treeElement.getText();
              }
              else if (changeType == ChangeInfo.REPLACE) {
                oldValue = ((ReplaceChangeInfo)changeByChild).getReplaced().getText();
              }
            }
          }
          if (oldName != null && !oldName.equals(attribute.getName())) {
            xmlChangeSet.add(new XmlAttributeSetImpl(attribute.getParent(), oldName, null));
            xmlChangeSet.add(new XmlAttributeSetImpl(attribute.getParent(), attribute.getName(), attribute.getValue()));
          }
          else if (oldValue != null) {
            xmlChangeSet.add(new XmlAttributeSetImpl(attribute.getParent(), attribute.getName(), attribute.getValue()));
          }
          else {
            xmlChangeSet.add(new XmlElementChangedImpl(attribute));
          }
        }

        public void visitXmlTag(XmlTag tag) {
          ASTNode[] affectedChildren = shortenChange(myChange.getAffectedChildren(), changeSet);

          for (final ASTNode treeElement : affectedChildren) {
            /*final IElementType type = treeElement.getElementType();
            if (type == ElementType.WHITE_SPACE) continue;
            if (type == ElementType.XML_NAME) {
              if (myChange.getChangeByChild(treeElement).getChangeType() == ChangeInfo.REPLACE) {
                continue;
              }
            }*/
            if (!(treeElement.getPsi() instanceof XmlTagChild)) {
              visitElement(tag);
              return;
            }
          }

          for (ASTNode treeElement : affectedChildren) {
            final ChangeInfo changeByChild = myChange.getChangeByChild(treeElement);
            final int changeType = changeByChild.getChangeType();
            final IElementType type = treeElement.getElementType();
            if (type == ElementType.WHITE_SPACE) continue;
            /*
            if (type == ElementType.XML_NAME) {
              final XmlToken xmlToken = (XmlToken)((ReplaceChangeInfo)changeByChild).getReplaced();
              xmlChangeSet.add(new XmlTagNameChangedImpl(tag, xmlToken.getText()));
              continue;
            }
            */

            switch (changeType) {
              case ChangeInfo.ADD:
                xmlChangeSet.add(new XmlTagChildAddImpl(tag, (XmlTagChild)treeElement));
                break;
              case ChangeInfo.REMOVED:
                treeElement.putUserData(CharTable.CHAR_TABLE_KEY, table);
                xmlChangeSet.add(new XmlTagChildRemovedImpl(tag, (XmlTagChild)treeElement));
                break;
              case ChangeInfo.CONTENTS_CHANGED:
                xmlChangeSet.add(new XmlTagChildChangedImpl(tag, (XmlTagChild)treeElement));
                break;
              case ChangeInfo.REPLACE:
                final XmlTagChild replaced = (XmlTagChild)((ReplaceChangeInfo)changeByChild).getReplaced();
                replaced.putUserData(CharTable.CHAR_TABLE_KEY, table);
                xmlChangeSet.add(new XmlTagChildRemovedImpl(tag, replaced));
                xmlChangeSet.add(new XmlTagChildAddImpl(tag, (XmlTagChild)treeElement));
                break;
            }
          }
        }

        public void visitXmlDocument(XmlDocument document) {
          xmlChangeSet.clear();
          xmlChangeSet.add(new XmlDocumentChangedImpl(document));
        }

        public void visitFile(PsiFile file) {
          final XmlDocument document = ((XmlFile)file).getDocument();

          if (document != null) {
            xmlChangeSet.clear();
            xmlChangeSet.add(new XmlDocumentChangedImpl(document));
          }
        }
      });
    }
  }

  private ASTNode[] shortenChange(ASTNode[] affectedChildren, TreeChangeEvent event) {
    // TODO
    return affectedChildren;
  }

  public void projectOpened() { }

  public void projectClosed() { }

  public void initComponent() { }

  public void disposeComponent() { }

  public String getComponentName() {
    return "XML POM aspect";
  }
}
