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
package com.intellij.j2ee.module;

import com.intellij.j2ee.make.MakeUtil;
import com.intellij.j2ee.serverInstances.ApplicationServersManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import org.jdom.Element;

import java.io.File;
import java.util.*;

public class LibraryLinkImpl extends LibraryLink implements ResolvableElement{
  private static final Map<J2EEPackagingMethod, String> methodToDescriptionForDirs = new HashMap<J2EEPackagingMethod, String>();
  private static final Map<J2EEPackagingMethod, String> methodToDescriptionForFiles = new HashMap<J2EEPackagingMethod, String>();
  private static final String URL_ELEMENT_NAME = "url";

  static {
    methodToDescriptionForDirs.put(J2EEPackagingMethod.DO_NOT_PACKAGE, "Do not package");
    methodToDescriptionForDirs.put(J2EEPackagingMethod.COPY_FILES, "Copy directories to");
    methodToDescriptionForDirs.put(J2EEPackagingMethod.JAR_AND_COPY_FILE, "JAR dirs and copy file to");
    methodToDescriptionForDirs.put(J2EEPackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST, "JAR dirs, link via manifest and copy to");
    methodToDescriptionForFiles.put(J2EEPackagingMethod.DO_NOT_PACKAGE, "Do not package");
    methodToDescriptionForFiles.put(J2EEPackagingMethod.COPY_FILES, "Copy files to");
    methodToDescriptionForFiles.put(J2EEPackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST, "Link via manifest and copy files to");
  }

  interface LibraryInfo extends JDOMExternalizable {
    String getName();

    List<String> getUrls();

    String getLevel();

    Library getLibrary();

    void addUrl(String url);
  }

  private class LibraryInfoImpl implements LibraryInfo {
    private final List<String> myUrls = new ArrayList<String>();

    public String getName() {
      return null;
    }

    public List<String> getUrls() {
      return myUrls;
    }

    public String getLevel() {
      return MODULE_LEVEL;
    }

    public void addUrl(String url) {
      myUrls.clear();
      myUrls.add(url);
    }

    public Library getLibrary() {
      return null;
    }

    public void readExternal(Element element) throws InvalidDataException {
      myUrls.clear();
      final List urls = element.getChildren(URL_ELEMENT_NAME);
      for (int i = 0; i < urls.size(); i++) {
        Element url = (Element)urls.get(i);
        myUrls.add(url.getText());
      }
    }

    public void writeExternal(Element element) throws WriteExternalException {
      for (int i = 0; i < myUrls.size(); i++) {
        final String url = myUrls.get(i);
        final Element urlElement = new Element(URL_ELEMENT_NAME);
        urlElement.setText(url);
        element.addContent(urlElement);
      }
    }
  }

  private class LibraryInfoBasedOnLibrary implements LibraryInfo {
    private final Library myLibrary;

    private LibraryInfoBasedOnLibrary(Library library) {
      myLibrary = library;
    }

    public String getName() {
      return myLibrary.getName();
    }

    public List<String> getUrls() {
      return Arrays.asList(myLibrary.getUrls(OrderRootType.CLASSES));
    }

    public String getLevel() {
      if (myLibrary.getTable() == null) {
        return MODULE_LEVEL;
      }
      else {
        return myLibrary.getTable().getTableLevel();
      }
    }

    public Library getLibrary() {
      return myLibrary;
    }

    public void addUrl(String url) {
    }

    public void readExternal(Element element) throws InvalidDataException {
    }

    public void writeExternal(Element element) throws WriteExternalException {
      String name = getName();
      if (name == null) {
        List<String> urls = getUrls();
        for (int i = 0; i < urls.size(); i++) {
          final String url = urls.get(i);
          final Element urlElement = new Element(URL_ELEMENT_NAME);
          urlElement.setText(url);
          element.addContent(urlElement);
        }
      }
      else {
        element.setAttribute("name", name);
      }
    }
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.module.LibraryLink");
  private LibraryInfo myLibraryInfo;

  public LibraryLinkImpl(Library library, Module parentModule) {
    super(parentModule);
    if (library == null) {
      myLibraryInfo = new LibraryInfoImpl();
    }
    else {
      myLibraryInfo = new LibraryInfoBasedOnLibrary(library);
    }

  }

  public Library getLibrary() {
    return myLibraryInfo.getLibrary();
  }

  public String toString() {
    return "Library Link: " + getPresentableName() + " -> " + getURI();
  }

  public String getPresentableName() {
    if (getName() != null) return getName();
    List<String> urls = myLibraryInfo.getUrls();
    if (urls.size() == 0) return "Empty Library";
    final String url = urls.get(0);
    final String path = PathUtil.toPresentableUrl(url);

    return FileUtil.toSystemDependentName(path);
  }

  public String getDescription() {
    String levelName = myLibraryInfo.getLevel();
    if (levelName.equals(MODULE_LEVEL)) {
      return "Module Library";
    }
    else if (LibraryTablesRegistrar.APPLICATION_LEVEL.equals(levelName)) {
      return "Global Library";
    }
    else if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(levelName)) {
      return "Project Library";
    }
    else if (ApplicationServersManager.APPLICATION_SERVER_MODULE_LIBRARIES.equals(levelName)) {
      return "Application Server Library";
    }
    else {
      return "???";
    }
  }

  public String getDescriptionForPackagingMethod(J2EEPackagingMethod method) {
    if (hasDirectoriesOnly()) {
      return methodToDescriptionForDirs.get(method);
    }
    else {
      return methodToDescriptionForFiles.get(method);
    }
  }

  public void addUrl(String url) {
    myLibraryInfo.addUrl(url);
  }

  public List<String> getUrls() {
    return myLibraryInfo.getUrls();
  }

  public boolean equalsIgnoreAttributes(ContainerElement otherElement) {
    if (!(otherElement instanceof LibraryLink)) return false;
    final LibraryLink otherLibraryLink = (LibraryLink)otherElement;
    if (!Comparing.strEqual(getName(), otherLibraryLink.getName())) return false;
    if (!getUrls().equals(otherLibraryLink.getUrls())) return false;
    return true;
  }

  public String getSingleFileName() {
    // non-module-level libs can contain multiple files
    final String table = getLevel();
    if (!MODULE_LEVEL.equals(table)) return null;

    List<String> urls = getUrls();
    if (urls.size() != 1) return null;
    File file = new File(PathUtil.toPresentableUrl(urls.get(0)));
    return file.getName();
  }

  public boolean hasDirectoriesOnly() {
    List<String> urls = getUrls();
    boolean hasDirsOnly = true;
    for (int i = 0; i < urls.size(); i++) {
      final String url = urls.get(i);
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      VirtualFile localFile = file == null ? null :
                                      LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(PathUtil.getLocalPath(file)));
      if (localFile != null && !localFile.isDirectory()) {
        hasDirsOnly = false;
        break;
      }
    }
    return hasDirsOnly;
  }

  public String getName() {
    return myLibraryInfo.getName();
  }

  public String getLevel() {
    return myLibraryInfo.getLevel();
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myLibraryInfo.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    myLibraryInfo.writeExternal(element);
  }

  public boolean resolveElement(ModuleByNameProvider provider) {
    if (getLibrary() != null){
      return true;
    }
    if (!MODULE_LEVEL.equals(getLevel())){
      return false;
    }

    List<String> urls = getUrls();
    if (urls.size() != 1){
      return false;
    }

    String url = urls.get(0);

    Module[] modules = getAllDependentModules();
    for (int i = 0; i < modules.length; i++) {
      Library moduleLibrary = findModuleLibrary(modules[i], url);
      if (moduleLibrary != null){
        myLibraryInfo = new LibraryInfoBasedOnLibrary(moduleLibrary);
        return true;
      }
    }

    return false;

  }

  protected Module[] getAllDependentModules() {
    HashSet<Module> result = new HashSet<Module>();
    Module parentModule = getParentModule();
    addDependencies(parentModule, result);

    return new ArrayList<Module>(result).toArray(new Module[result.size()]);
  }

  protected void addDependencies(Module module, HashSet<Module> result) {
    if (result.contains(module)) return;
    result.add(module);
    Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
    for (int i = 0; i < dependencies.length; i++) {
      addDependencies(dependencies[i], result);
    }
  }

  public LibraryLink clone() {
    LibraryLink libraryLink = MakeUtil.getInstance().createLibraryLink(getLibrary(), getParentModule());
    Element temp = new Element("temp");
    try {
      writeExternal(temp);
      libraryLink.readExternal(temp);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return libraryLink;
  }
}
