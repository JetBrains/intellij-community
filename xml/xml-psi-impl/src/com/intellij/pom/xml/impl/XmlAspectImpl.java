/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.pom.xml.impl;

import com.intellij.lang.ASTNode;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.TreeChangeImpl;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.impl.events.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class XmlAspectImpl implements XmlAspect {
  private final PomModel myModel;
  private final TreeAspect myTreeAspect;

  public XmlAspectImpl(PomModel model, TreeAspect aspect) {
    myModel = model;
    myTreeAspect = aspect;
    myModel.registerAspect(XmlAspect.class, this, Collections.singleton(myTreeAspect));
  }

  @Override
  public void update(PomModelEvent event) {
    if (!event.getChangedAspects().contains(myTreeAspect)) return;
    final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(myTreeAspect);
    if (changeSet == null) return;
    final ASTNode rootElement = changeSet.getRootElement();
    final PsiFile file = (PsiFile)rootElement.getPsi();
    if (!(file instanceof XmlFile)) return;
    final XmlAspectChangeSetImpl xmlChangeSet = event.registerChangeSetIfAbsent(this, new XmlAspectChangeSetImpl(myModel));
    xmlChangeSet.addChangedFile((XmlFile)file);

    final ASTNode[] changedElements = changeSet.getChangedElements();
    final CharTable table = ((FileElement)changeSet.getRootElement()).getCharTable();
    for (ASTNode changedElement : changedElements) {
      TreeChangeImpl changesByElement = (TreeChangeImpl)changeSet.getChangesByElement(changedElement);
      PsiElement psiElement;
      while ((psiElement = changedElement.getPsi()) == null) {
        changesByElement = createChildrenChangedInfo(changedElement);
        changedElement = changesByElement.getChangedParent();
      }
      final TreeChange finalChangedElement = changesByElement;
      psiElement.accept(new XmlElementVisitor() {
        TreeChange myChange = finalChangedElement;

        @Override
        public void visitElement(PsiElement element) {
          myChange = createChildrenChangedInfo(element.getNode());
          element.getParent().accept(this);
        }

        @Override
        public void visitXmlAttribute(XmlAttribute attribute) {
          final ASTNode[] affectedChildren = myChange.getAffectedChildren();
          String oldName = null;
          String oldValue = null;
          for (final ASTNode treeElement : affectedChildren) {
            final ChangeInfo changeByChild = myChange.getChangeByChild(treeElement);
            final int changeType = changeByChild.getChangeType();
            if (treeElement.getElementType() == XmlTokenType.XML_NAME) {
              if (changeType == ChangeInfo.REMOVED) {
                oldName = treeElement.getText();
              }
              else if (changeType == ChangeInfo.REPLACE) {
                oldName = getReplacedNode(changeByChild).getText();
              }
            }
            if (treeElement.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE) {
              if (changeType == ChangeInfo.REMOVED) {
                oldValue = treeElement.getText();
              }
              else if (changeType == ChangeInfo.REPLACE) {
                oldValue = getReplacedNode(changeByChild).getText();
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

        private TreeElement getReplacedNode(ChangeInfo info) {
          return ((ChangeInfoImpl)info).getOldChild();
        }

        @Override
        public void visitXmlTag(XmlTag tag) {
          ASTNode[] affectedChildren = myChange.getAffectedChildren();

          for (final ASTNode treeElement : affectedChildren) {
            if (!(treeElement.getPsi() instanceof XmlTagChild)) {
              visitElement(tag);
              return;
            }
          }

          for (final ASTNode treeElement : affectedChildren) {
            final ChangeInfo changeByChild = myChange.getChangeByChild(treeElement);
            final int changeType = changeByChild.getChangeType();
            final IElementType type = treeElement.getElementType();
            if (type == TokenType.WHITE_SPACE) continue;

            final PsiElement element = treeElement.getPsi();

            switch (changeType) {
              case ChangeInfo.ADD:
                xmlChangeSet.add(new XmlTagChildAddImpl(tag, (XmlTagChild)element));
                break;
              case ChangeInfo.REMOVED:
                treeElement.putUserData(CharTable.CHAR_TABLE_KEY, table);
                xmlChangeSet.add(new XmlTagChildRemovedImpl(tag, (XmlTagChild)element));
                break;
              case ChangeInfo.CONTENTS_CHANGED:
                xmlChangeSet.add(new XmlTagChildChangedImpl(tag, (XmlTagChild)element));
                break;
              case ChangeInfo.REPLACE:
                final PsiElement psi = getReplacedNode(changeByChild).getPsi();
                if (psi instanceof XmlTagChild) {
                  final XmlTagChild replaced = (XmlTagChild)psi;
                  replaced.putUserData(CharTable.CHAR_TABLE_KEY, table);
                  xmlChangeSet.add(new XmlTagChildRemovedImpl(tag, replaced));
                  xmlChangeSet.add(new XmlTagChildAddImpl(tag, (XmlTagChild)element));
                }
                break;
            }
          }
        }

        @Override
        public void visitXmlDocument(XmlDocument document) {
          xmlChangeSet.clear();
          xmlChangeSet.add(new XmlDocumentChangedImpl(document));
        }

        @Override
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

  @NotNull
  private static TreeChangeImpl createChildrenChangedInfo(ASTNode changedElement) {
    ASTNode parent = changedElement.getTreeParent();
    TreeChangeImpl changesByElement = new TreeChangeImpl((CompositeElement)parent);
    changesByElement.markChildChanged((TreeElement)changedElement, 0); // nobody cares about lengths here
    return changesByElement;
  }
}
