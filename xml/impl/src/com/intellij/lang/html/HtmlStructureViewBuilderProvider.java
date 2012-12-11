package com.intellij.lang.html;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.ide.structureView.xml.XmlStructureViewBuilderProvider;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlStructureViewBuilderProvider implements XmlStructureViewBuilderProvider {
  @Nullable
  public StructureViewBuilder createStructureViewBuilder(@NotNull final XmlFile file) {
    if (file.getViewProvider().getVirtualFile().getFileType() != HtmlFileType.INSTANCE) return null;

    return new TreeBasedStructureViewBuilder() {
      public boolean isRootNodeShown() {
        return false;
      }

      @NotNull
      public StructureViewModel createStructureViewModel() {
        return new XmlStructureViewTreeModel(file) {
          @NotNull
          public Sorter[] getSorters() {
            return Sorter.EMPTY_ARRAY;
          }

          @NotNull
          public StructureViewTreeElement getRoot() {
            final XmlDocument document = ((XmlFile)getPsiFile()).getDocument();
            final XmlTag rootTag = document == null ? null : document.getRootTag();

            if (rootTag != null && "html".equalsIgnoreCase(rootTag.getLocalName())) {
              final XmlTag[] subTags = rootTag.getSubTags();
              if (subTags.length == 1 &&
                  ("head".equalsIgnoreCase(subTags[0].getLocalName()) || "body".equalsIgnoreCase(subTags[0].getLocalName()))) {
                return new HtmlStructureViewElementProvider.HtmlTagTreeElement(subTags[0]);
              }

              return new HtmlStructureViewElementProvider.HtmlTagTreeElement(rootTag);
            }

            return super.getRoot();
          }
        };
      }
    };
  }
}
