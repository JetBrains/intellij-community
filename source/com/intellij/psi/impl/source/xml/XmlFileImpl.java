package com.intellij.psi.impl.source.xml;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.parsing.xml.XmlPsiLexer;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;

/**
 * @author Mike
 */
public class XmlFileImpl extends PsiFileImpl implements XmlFile {
  public XmlFileImpl(Project project, VirtualFile file) {
    super(project, XML_FILE, getChameleonTypeByFile(file), file);
  }

  public XmlFileImpl(Project project, VirtualFile file, IElementType elementType, IElementType contentElementType) {
    super(project, elementType, contentElementType, file);
  }

  private static IElementType getChameleonTypeByFile(VirtualFile file) {
    final FileType fileTypeByFile = FileTypeManager.getInstance().getFileTypeByFile(file);
    return getElementType(fileTypeByFile);
  }

  private static IElementType getElementType(final FileType fileTypeByFile) {
    if (fileTypeByFile == StdFileTypes.XML)   return XML_FILE_TEXT;
    if (fileTypeByFile == StdFileTypes.XHTML) return XHTML_FILE_TEXT;
    if (fileTypeByFile == StdFileTypes.HTML)  return HTML_FILE_TEXT;
    if (fileTypeByFile == StdFileTypes.DTD)   return DTD_FILE_TEXT;
    return null;
  }

  public XmlFileImpl(Project project, String name, CharSequence text, FileType fileType) {
    super(project, XML_FILE, getElementType(fileType), name, text);
  }

  public XmlFileImpl(Project project, String name, CharSequence text, FileType fileType, IElementType elementType) {
    super(project, elementType, getElementType(fileType), name, text);
  }

  public XmlDocument getDocument() {
    CompositeElement treeElement = calcTreeElement();
    ChameleonTransforming.transformChildren(treeElement);
    return (XmlDocument)treeElement.findChildByRoleAsPsiElement(ChildRole.XML_DOCUMENT);
  }

  public boolean processElements(PsiElementProcessor processor, PsiElement place){
    final XmlDocument document = getDocument();
    return document != null ? document.processElements(processor, place) : true;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlFile(this);
  }

  public String toString() {
    return "XmlFile:" + getName();
  }

  private FileType myType = null;
  public FileType getFileType() {
    if(myType != null) return myType;
    return myType = FileTypeManager.getInstance().getFileTypeByFileName(getName());
  }

  public Lexer createLexer() {
    return new XmlPsiLexer();
  }
}
