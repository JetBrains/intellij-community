package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;
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
      final List<YAMLPsiElement> list = myElement instanceof YAMLKeyValue 
                                        ? ((YAMLCompoundValue)((YAMLKeyValue)myElement).getValue()).getYAMLElements()
                                        : myElement.getYAMLElements();
      final StructureViewTreeElement[] structureElements = new StructureViewTreeElement[list.size()];
      for (int i = 0; i < structureElements.length; i++) {
        structureElements[i] = new YAMLStructureViewElement(list.get(i));
      }
      return structureElements;
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
                 ? Icons.XML_TAG_ICON : Icons.PROPERTY_ICON;
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

