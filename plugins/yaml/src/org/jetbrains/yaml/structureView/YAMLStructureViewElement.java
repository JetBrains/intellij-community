package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class YAMLStructureViewElement implements StructureViewTreeElement {
  private final YAMLPsiElement myElement;

  public YAMLStructureViewElement(final YAMLPsiElement element) {
    myElement = element;
  }

  public StructureViewTreeElement[] getChildren() {
    if (myElement instanceof YAMLFile ||
        myElement instanceof YAMLDocument ||
        myElement instanceof YAMLKeyValue && ((YAMLKeyValue)myElement).getValue() instanceof YAMLCompoundValue){
      final List<YAMLPsiElement> children = myElement instanceof YAMLKeyValue
                                        ? ((YAMLCompoundValue)((YAMLKeyValue)myElement).getValue()).getYAMLElements()
                                        : myElement.getYAMLElements();
      final List<StructureViewTreeElement> structureElements = new ArrayList<StructureViewTreeElement>();
      for (YAMLPsiElement child : children) {
        if (child instanceof YAMLDocument || child instanceof YAMLKeyValue){
          structureElements.add(new YAMLStructureViewElement(child));
        }
      }
      return structureElements.toArray(new StructureViewTreeElement[structureElements.size()]);
    }
    return EMPTY_ARRAY;
  }


  public ItemPresentation getPresentation() {
    if (myElement instanceof YAMLKeyValue){
      final YAMLKeyValue kv = (YAMLKeyValue)myElement;
      return new ItemPresentation() {
        public String getPresentableText() {
          return kv.getKeyText();
        }

        public String getLocationString() {
          return null;
        }

        public Icon getIcon(boolean open) {
          final PsiElement value = kv.getValue();
          return value instanceof YAMLCompoundValue && !((YAMLCompoundValue)value).getYAMLElements().isEmpty()
                 ? PlatformIcons.XML_TAG_ICON : PlatformIcons.PROPERTY_ICON;
        }

        public TextAttributesKey getTextAttributesKey() {
          return null;
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

        public TextAttributesKey getTextAttributesKey() {
          return null;
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

