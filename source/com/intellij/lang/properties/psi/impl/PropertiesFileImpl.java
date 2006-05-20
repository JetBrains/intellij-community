package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleImpl;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 12:25:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFileImpl extends PsiFileBase implements PropertiesFile {
  private static TokenSet outPropertiesListSet = TokenSet.create(PropertiesElementTypes.PROPERTIES_LIST);
  private Map<String,List<Property>> myPropertiesMap;
  private List<Property> myProperties;

  public PropertiesFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, StdFileTypes.PROPERTIES.getLanguage());
  }

  @NotNull
  public FileType getFileType() {
    return StdFileTypes.PROPERTIES;
  }

  public String toString() {
    return "Properties file:" + getName();
  }

  @NotNull
  public List<Property> getProperties() {
    ensurePropertiesLoaded();
    return myProperties;
  }

  private ASTNode getPropertiesList() {
    final ASTNode[] nodes = getNode().getChildren(outPropertiesListSet);
    return nodes.length > 0 ? nodes[0]:null;
  }

  private void ensurePropertiesLoaded() {
    synchronized (PsiLock.LOCK) {
      if (myPropertiesMap != null) {
        return;
      }
      final ASTNode[] props = getPropertiesList().getChildren(PropertiesElementTypes.PROPERTIES);
      myPropertiesMap = new LinkedHashMap<String, List<Property>>();
      myProperties = new ArrayList<Property>(props.length);
      for (final ASTNode prop : props) {
        final Property property = (Property) prop.getPsi();
        String key = property.getKey();
        List<Property> list = myPropertiesMap.get(key);
        if (list == null) {
          list = new SmartList<Property>();
          myPropertiesMap.put(key, list);
        }
        list.add(property);
        myProperties.add(property);
      }
    }
  }

  public Property findPropertyByKey(@NotNull String key) {
    ensurePropertiesLoaded();
    List<Property> list = myPropertiesMap.get(key);
    return list == null ? null : list.get(0);
  }

  @NotNull
  public List<Property> findPropertiesByKey(@NotNull String key) {
    ensurePropertiesLoaded();
    List<Property> list = myPropertiesMap.get(key);
    return list == null ? Collections.<Property>emptyList() : list;
  }

  @NotNull
  public ResourceBundle getResourceBundle() {
    VirtualFile virtualFile = getVirtualFile();
    if (!isValid() || virtualFile == null) {
      return ResourceBundleImpl.NULL;
    }
    String baseName = PropertiesUtil.getBaseName(virtualFile);
    return new ResourceBundleImpl(getContainingFile().getContainingDirectory().getVirtualFile(), baseName);
  }

  @NotNull
  public Locale getLocale() {
    return PropertiesUtil.getLocale(getVirtualFile());
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException {
    if (element instanceof Property) {
      throw new IncorrectOperationException("Use addProperty() instead");
    }
    return super.add(element);
  }

  @NotNull
  public PsiElement addProperty(@NotNull Property property) throws IncorrectOperationException {
    if (haveToAddNewLine()) {
      String text = "\n";
      LeafElement ws = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, text.toCharArray(), 0, text.length(), getTreeElement().getCharTable(), myManager);
      ChangeUtil.addChild((CompositeElement)getPropertiesList(), ws, null);
    }
    final TreeElement copy = ChangeUtil.copyToElement(property);
    getPropertiesList().addChild(copy);
    return copy.getPsi();
  }

  private boolean haveToAddNewLine() {
    ASTNode lastChild = getPropertiesList().getLastChildNode();
    if (lastChild == null) return false;
    return lastChild.getText().indexOf('\n') == -1;
  }

  @NotNull
  public Map<String, String> getNamesMap() {
    THashMap<String, String> result = new THashMap<String, String>();
    for (Property property : getProperties()) {
      result.put(property.getName(), property.getValue());
    }
    return result;
  }

  public synchronized void subtreeChanged() {
    super.subtreeChanged();
    PropertiesReferenceManager.getInstance(getProject()).beforePropertiesFileChange(this, myPropertiesMap == null ? null : myPropertiesMap.keySet());
    myPropertiesMap = null;
    myProperties = null;
  }
}