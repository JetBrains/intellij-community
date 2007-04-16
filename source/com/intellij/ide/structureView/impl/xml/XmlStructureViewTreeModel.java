/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class XmlStructureViewTreeModel extends TextEditorBasedStructureViewModel{
  private final XmlFile myFile;
  private static final Class[] myClasses = new Class[]{XmlTag.class, XmlFile.class, XmlEntityDecl.class, XmlElementDecl.class, XmlAttlistDecl.class, XmlConditionalSection.class};

  public XmlStructureViewTreeModel(XmlFile file) {
    super(file);
    myFile = file;
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    if (myFile.getLanguage() == StdLanguages.DTD) return new DtdFileTreeElement(myFile);
    return new XmlFileTreeElement(myFile);
  }

  @NotNull
  public Grouper[] getGroupers() {
    List<Grouper> groupers = new ArrayList<Grouper>();
    for (XmlStructureViewElementProvider provider : getProviders()) {
      groupers.addAll(Arrays.asList(provider.getGroupers(myFile)));
    }
    return groupers.toArray(new Grouper[groupers.size()]);
  }

  private static XmlStructureViewElementProvider[] getProviders() {
   return (XmlStructureViewElementProvider[])Extensions.getExtensions(XmlStructureViewElementProvider.EXTENSION_POINT_NAME);
  }

  @NotNull
  public Sorter[] getSorters() {
    List<Sorter> sorters = new ArrayList<Sorter>();
    for (XmlStructureViewElementProvider provider : getProviders()) {
      sorters.addAll(Arrays.asList(provider.getSorters(myFile)));
    }
    return sorters.toArray(new Sorter[sorters.size()]);
  }

  @NotNull
  public Filter[] getFilters() {
    List<Filter> filters = new ArrayList<Filter>();
    for (XmlStructureViewElementProvider provider : getProviders()) {
      filters.addAll(Arrays.asList(provider.getFilters(myFile)));
    }
    return filters.toArray(new Filter[filters.size()]);
  }

  protected PsiFile getPsiFile() {
    return myFile;
  }

  @NotNull
  protected Class[] getSuitableClasses() {
    return myClasses;
  }
}
