package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author oleg
 */
public class YAMLStructureViewElement implements StructureViewTreeElement {
  private final YAMLPsiElement myElement;

  public YAMLStructureViewElement(final YAMLPsiElement element) {
    myElement = element;
  }

  @NotNull
  public StructureViewTreeElement[] getChildren() {
    final Collection<? extends YAMLPsiElement> children;
    if (myElement instanceof YAMLFile) {
      children = ((YAMLFile)myElement).getDocuments();
    }
    else if (myElement instanceof YAMLDocument) {
      children = getChildrenForValue(((YAMLDocument)myElement).getTopLevelValue());
    }
    else if (myElement instanceof YAMLSequenceItem) {
      children = getChildrenForValue(((YAMLSequenceItem)myElement).getValue());
    }
    else if (myElement instanceof YAMLKeyValue) {
      children = getChildrenForValue(((YAMLKeyValue)myElement).getValue());
    }
    else {
      children = Collections.emptyList();
    }
    
    final List<StructureViewTreeElement> structureElements = new ArrayList<>();
    for (YAMLPsiElement child : children) {
      structureElements.add(new YAMLStructureViewElement(child));
    }
    return structureElements.toArray(new StructureViewTreeElement[structureElements.size()]);
  }
  
  @NotNull
  private static Collection<? extends YAMLPsiElement> getChildrenForValue(@Nullable YAMLPsiElement element) {
    if (element instanceof YAMLMapping) {
      return ((YAMLMapping)element).getKeyValues();
    }
    if (element instanceof YAMLSequence) {
      return ((YAMLSequence)element).getItems();
    }
    return Collections.emptyList();
  }
  


  @NotNull
  public ItemPresentation getPresentation() {
    if (myElement instanceof YAMLKeyValue){
      final YAMLKeyValue kv = (YAMLKeyValue)myElement;
      return new ItemPresentation() {
        public String getPresentableText() {
          return kv.getKeyText();
        }

        public String getLocationString() {
          if (kv.getValue() instanceof YAMLScalar) {
            return kv.getValueText();
          }
          else {
            return null;
          }
        }

        public Icon getIcon(boolean open) {
          final YAMLValue value = kv.getValue();
          return value instanceof YAMLScalar ? kv.getIcon(0) : PlatformIcons.XML_TAG_ICON;
        }
      };
    }
    if (myElement instanceof YAMLDocument){
      return new ItemPresentation() {
        public String getPresentableText() {
          return "YAML document";
        }

        public String getLocationString() {
          return null;
        }

        public Icon getIcon(boolean open) {
          return PlatformIcons.XML_TAG_ICON;
        }
      };
    }
    if (myElement instanceof YAMLSequenceItem) {
      final YAMLSequenceItem item = ((YAMLSequenceItem)myElement);
      return new ItemPresentation() {
        @Nullable
        @Override
        public String getPresentableText() {
          if (item.getValue() instanceof YAMLScalar) {
            return ((YAMLScalar)item.getValue()).getTextValue();
          }
          else {
            return "Sequence Item";
          }
        }

        @Nullable
        @Override
        public String getLocationString() {
          return null;
        }

        @Nullable
        @Override
        public Icon getIcon(boolean unused) {
          return item.getValue() instanceof YAMLScalar ? PlatformIcons.PROPERTY_ICON : PlatformIcons.XML_TAG_ICON;
        }
      };
    }
    return myElement.getPresentation();
  }

  public YAMLPsiElement getValue() {
    return myElement;
  }

  public void navigate(boolean requestFocus) {
    myElement.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return myElement.canNavigate();
  }

  public boolean canNavigateToSource() {
    return myElement.canNavigateToSource();
  }
}

