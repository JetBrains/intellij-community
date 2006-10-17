package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class PropertyImpl extends PropertiesElementImpl implements Property {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.psi.impl.PropertyImpl");

  public PropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Property:"+getKey();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PropertyImpl property = (PropertyImpl)PropertiesElementFactory.createProperty(getProject(), name,"xxx");
    ASTNode keyNode = getKeyNode();
    ASTNode newKeyNode = property.getKeyNode();
    LOG.assertTrue(newKeyNode != null);
    if (keyNode == null) {
      getNode().addChild(newKeyNode);
    }
    else {
      getNode().replaceChild(keyNode, newKeyNode);
    }
    return this;
  }

  public void setValue(String value) throws IncorrectOperationException {
    StringBuffer escapedName = new StringBuffer(value.length());
    for (int i=0; i<value.length();i++) {
      char c = value.charAt(i);
      if (c == '\n' && (i == 0 || value.charAt(i-1) != '\\')) {
        escapedName.append('\\');
      }
      escapedName.append(c);
    }
    ASTNode node = getValueNode();
    PropertyImpl property = (PropertyImpl)PropertiesElementFactory.createProperty(getProject(), "xxx",escapedName.toString());
    ASTNode valueNode = property.getValueNode();
    if (node == null) {
      if (valueNode != null) {
        getNode().addChild(valueNode);
      }
    }
    else {
      if (valueNode == null) {
        getNode().removeChild(node);
      }
      else {
        getNode().replaceChild(node, valueNode);
      }
    }
  }

  public String getName() {
    return getKey();
  }

  public String getKey() {
    final ASTNode node = getKeyNode();
    if (node == null) {
      return null;
    }
    return node.getText();
  }

  public @Nullable ASTNode getKeyNode() {
    return getNode().findChildByType(PropertiesTokenTypes.KEY_CHARACTERS);
  }
  private @Nullable ASTNode getValueNode() {
    return getNode().findChildByType(PropertiesTokenTypes.VALUE_CHARACTERS);
  }

  public String getValue() {
    final ASTNode node = getNode().findChildByType(PropertiesTokenTypes.VALUE_CHARACTERS);
    if (node == null) {
      return "";
    }
    return node.getText();
  }

  @Nullable
  public String getUnescapedValue() {
    String s = getValue();
    if (s == null) {
      return null;
    }
    int off = 0;
    int len = s.length();
    char aChar;
    StringBuilder out = new StringBuilder();

    while (off < len) {
        aChar = s.charAt(off++);
        if (aChar == '\\') {
            aChar = s.charAt(off++);
            if(aChar == 'u') {
                // Read the xxxx
                int value=0;
                for (int i=0; i<4; i++) {
                    aChar = s.charAt(off++);
                    switch (aChar) {
                      case '0': case '1': case '2': case '3': case '4':
                      case '5': case '6': case '7': case '8': case '9':
                         value = (value << 4) + aChar - '0';
                         break;
                      case 'a': case 'b': case 'c':
                      case 'd': case 'e': case 'f':
                         value = (value << 4) + 10 + aChar - 'a';
                         break;
                      case 'A': case 'B': case 'C':
                      case 'D': case 'E': case 'F':
                         value = (value << 4) + 10 + aChar - 'A';
                         break;
                      default:
                          throw new IllegalArgumentException(
                                       "Malformed \\uxxxx encoding.");
                    }
                }
                out.append((char) value);
            } else {
                if (aChar == 't') aChar = '\t';
                else if (aChar == 'r') aChar = '\r';
                else if (aChar == 'n') aChar = '\n';
                else if (aChar == 'f') aChar = '\f';
                out.append(aChar);
            }
        } else {
            out.append(aChar);
        }
    }
    return out.toString();
  }

  public @Nullable String getKeyValueSeparator() {
    final ASTNode node = getNode().findChildByType(PropertiesTokenTypes.KEY_VALUE_SEPARATOR);
    if (node == null) {
      return null;
    }
    return node.getText();
  }

  public Icon getIcon(int flags) {
    return Icons.PROPERTY_ICON;
  }

  public void delete() throws IncorrectOperationException {
    getParent().getNode().removeChild(getNode());
  }

  public PropertiesFile getContainingFile() {
    return (PropertiesFile)super.getContainingFile();
  }
}
