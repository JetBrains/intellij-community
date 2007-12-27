package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;

/**
 *  @author dsl
 */
public class ContentEntryImpl extends RootModelComponentBase implements ContentEntry, ClonableContentEntry {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.SimpleContentEntryImpl");
  private final VirtualFilePointer myRoot;
  private RootModelImpl myRootModel;
  @NonNls final static String ELEMENT_NAME = "content";
  private final TreeSet<SourceFolder> mySourceFolders = new TreeSet<SourceFolder>(ContentFolderComparator.INSTANCE);
  private final TreeSet<ExcludeFolder> myExcludeFolders = new TreeSet<ExcludeFolder>(ContentFolderComparator.INSTANCE);
  private final TreeSet<ExcludedOutputFolder> myExcludedOutputFolders = new TreeSet<ExcludedOutputFolder>(ContentFolderComparator.INSTANCE);
  @NonNls private static final String URL_ATTR = "url";


  ContentEntryImpl(VirtualFile file, RootModelImpl m) {
    this(m.pointerFactory().create(file), m);
  }

  ContentEntryImpl(String url, RootModelImpl m) {
    this(m.pointerFactory().create(url), m);
  }

  ContentEntryImpl(Element e, RootModelImpl m) throws InvalidDataException {
    this(getUrlFrom(e), m);
    initSourceFolders(e);
    initExcludeFolders(e);
  }

  private static String getUrlFrom(Element e) throws InvalidDataException {
    LOG.assertTrue(ELEMENT_NAME.equals(e.getName()));
    
    String url = e.getAttributeValue(URL_ATTR);
    if (url == null) throw new InvalidDataException();
    return url;
  }

  private void initSourceFolders(Element e) throws InvalidDataException {
    mySourceFolders.clear();
    for (Object child : e.getChildren(SourceFolderImpl.ELEMENT_NAME)) {
      mySourceFolders.add(new SourceFolderImpl((Element)child, this));
    }
  }

  private void initExcludeFolders(Element e) throws InvalidDataException {
    myExcludeFolders.clear();
    for (Object child : e.getChildren(ExcludeFolderImpl.ELEMENT_NAME)) {
      myExcludeFolders.add(new ExcludeFolderImpl((Element)child, this));
    }
  }

  private ContentEntryImpl copyWith(RootModelImpl m) {
    return new ContentEntryImpl(m.pointerFactory().duplicate(myRoot), m);
  }

  private ContentEntryImpl(VirtualFilePointer root, RootModelImpl m) {
    super(m);
    myRootModel = m;
    myRoot = root;
  }

  public VirtualFile getFile() {
    final VirtualFile file = myRoot.getFile();
    if (file == null || file.isDirectory()) {
      return file;
    } else {
      return null;
    }
  }

  @NotNull
  public String getUrl() {
    return myRoot.getUrl();
  }

  public SourceFolder[] getSourceFolders() {
    return mySourceFolders.toArray(new SourceFolder[mySourceFolders.size()]);
  }

  public VirtualFile[] getSourceFolderFiles() {
    final SourceFolder[] sourceFolders = getSourceFolders();
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (SourceFolder sourceFolder : sourceFolders) {
      final VirtualFile file = sourceFolder.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public ExcludeFolder[] getExcludeFolders() {
    return calculateExcludeFolders();
  }

  private ExcludeFolder[] calculateExcludeFolders() {
    if (!myRootModel.isExcludeOutput() && !myRootModel.isExcludeExplodedDirectory() && myExcludedOutputFolders.isEmpty()) { // optimization
      return myExcludeFolders.toArray(new ExcludeFolder[myExcludeFolders.size()]);
    }
    final ArrayList<ExcludeFolder> result = new ArrayList<ExcludeFolder>(myExcludeFolders);
    result.addAll(myExcludedOutputFolders);
    if (myRootModel.isExcludeOutput()) {
      if (!myRootModel.isCompilerOutputPathInherited()) {
        addExcludeForOutputPath(myRootModel.myCompilerOutputPointer, result);
        addExcludeForOutputPath(myRootModel.myCompilerOutputPathForTestsPointer, result);
      } else {
        CompilerProjectExtension compilerProjectExtension = CompilerProjectExtension.getInstance(myRootModel.getModule().getProject());
        final String outputUrl = compilerProjectExtension.getCompilerOutputUrl();
        if (outputUrl != null){
          if (new File(VfsUtil.urlToPath(outputUrl)).exists()){
            addExcludeForOutputPath(compilerProjectExtension.getCompilerOutputPointer(), result);
          }
        }
      }
    }
    if (myRootModel.isExcludeExplodedDirectory()) {
      addExcludeForOutputPath(myRootModel.myExplodedDirectoryPointer, result);
    }
    return result.toArray(new ExcludeFolder[result.size()]);
  }

  private void addExcludeForOutputPath(final VirtualFilePointer outputPath, ArrayList<ExcludeFolder> result) {
    if (outputPath == null) return;
    final VirtualFile outputPathFile = outputPath.getFile();
    final VirtualFile file = myRoot.getFile();
    if (outputPathFile != null && file != null /* TODO: ??? && VfsUtil.isAncestor(file, outputPathFile, false) */) {
      result.add(new ExcludedOutputFolderImpl(this, outputPath));
    }
  }

  public VirtualFile[] getExcludeFolderFiles() {
    final ExcludeFolder[] excludeFolders = getExcludeFolders();
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (ExcludeFolder excludeFolder : excludeFolders) {
      final VirtualFile file = excludeFolder.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource) {
    assertCanAddFolder(file);
    return addSourceFolder(new SourceFolderImpl(file, isTestSource, this));
  }

  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix) {
    assertCanAddFolder(file);
    return addSourceFolder(new SourceFolderImpl(file, isTestSource, packagePrefix, this));
  }

  public SourceFolder addSourceFolder(@NotNull String url, boolean isTestSource) {
    assertFolderUnderMe(url);
    return addSourceFolder(new SourceFolderImpl(url, isTestSource, this));
  }

  private SourceFolder addSourceFolder(SourceFolderImpl f) {
    mySourceFolders.add(f);
    return f;
  }

  public void removeSourceFolder(@NotNull SourceFolder sourceFolder) {
    assertCanRemoveFrom(sourceFolder, mySourceFolders);
    mySourceFolders.remove(sourceFolder);
  }

  public ExcludeFolder addExcludeFolder(@NotNull VirtualFile file) {
    assertCanAddFolder(file);
    return addExcludeFolder(new ExcludeFolderImpl(file, this));
  }

  public ExcludeFolder addExcludeFolder(@NotNull String url) {
    assertCanAddFolder(url);
    return addExcludeFolder(new ExcludeFolderImpl(url, this));
  }

  private void assertCanAddFolder(VirtualFile file) {
    assertCanAddFolder(file.getUrl());
  }

  private void assertCanAddFolder(String url) {
    myRootModel.assertWritable();
    assertFolderUnderMe(url);
  }

  public void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder) {
    assertCanRemoveFrom(excludeFolder, myExcludeFolders);
    myExcludeFolders.remove(excludeFolder);
  }

  private ExcludeFolder addExcludeFolder(ExcludeFolder f) {
    myExcludeFolders.add(f);
    return f;
  }

  private <T extends ContentFolder> void assertCanRemoveFrom(T f, TreeSet<T> ff) {
    myRootModel.assertWritable();
    LOG.assertTrue(ff.contains(f));
  }

  public ExcludedOutputFolder addExcludedOutputFolder(VirtualFilePointer directory) {
    myRootModel.assertWritable();
    ExcludedOutputFolderImpl folder = new ExcludedOutputFolderImpl(this, directory);
    myExcludedOutputFolders.add(folder);
    return folder;
  }

  public boolean removeExcludedOutputFolder(ExcludedOutputFolder folder) {
    return myExcludedOutputFolders.remove(folder);
  }

  private void assertFolderUnderMe(String url) {
    final String rootUrl = getUrl();
    try {
      if (!FileUtil.isAncestor(new File(rootUrl), new File(url), false)) {
        LOG.assertTrue(false, "The file " + url + " is not under content entry root " + rootUrl);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isSynthetic() {
    return false;
  }

  public ContentEntry cloneEntry(RootModelImpl rootModel) {
    final ContentEntryImpl cloned = copyWith(rootModel);
    for (final SourceFolder sourceFolder : mySourceFolders) {
      if (sourceFolder instanceof ClonableContentFolder) {
        ContentFolder folder = ((ClonableContentFolder)sourceFolder).cloneFolder(cloned);
        cloned.mySourceFolders.add((SourceFolder)folder);
      }
    }

    for (final ExcludeFolder excludeFolder : myExcludeFolders) {
      if (excludeFolder instanceof ClonableContentFolder) {
        ContentFolder folder = ((ClonableContentFolder)excludeFolder).cloneFolder(cloned);
        cloned.myExcludeFolders.add((ExcludeFolder)folder);
      }
    }

    for (ExcludedOutputFolder excludedOutputFolder : myExcludedOutputFolders) {
      if (excludedOutputFolder instanceof ClonableContentFolder) {
        ContentFolder folder = ((ClonableContentFolder)excludedOutputFolder).cloneFolder(cloned);
        cloned.myExcludedOutputFolders.add((ExcludedOutputFolder)folder);
      }
    }

    return cloned;
  }

  public RootModelImpl getRootModel() {
    return myRootModel;
  }

  protected void dispose() {
    super.dispose();
    VirtualFilePointerManager.getInstance().kill(myRoot, null);
    for (final Object mySourceFolder : mySourceFolders) {
      ContentFolder contentFolder = (ContentFolder)mySourceFolder;
      ((RootModelComponentBase)contentFolder).dispose();
    }
    for (final ExcludeFolder excludeFolder : myExcludeFolders) {
      ((RootModelComponentBase)excludeFolder).dispose();
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    LOG.assertTrue(ELEMENT_NAME.equals(element.getName()));
    element.setAttribute(URL_ATTR, myRoot.getUrl());
    for (final SourceFolder sourceFolder : mySourceFolders) {
      if (sourceFolder instanceof SourceFolderImpl) {
        final Element subElement = new Element(SourceFolderImpl.ELEMENT_NAME);
        ((SourceFolderImpl)sourceFolder).writeExternal(subElement);
        element.addContent(subElement);
      }
    }

    for (final ExcludeFolder excludeFolder : myExcludeFolders) {
      if (excludeFolder instanceof ExcludeFolderImpl) {
        final Element subElement = new Element(ExcludeFolderImpl.ELEMENT_NAME);
        ((ExcludeFolderImpl)excludeFolder).writeExternal(subElement);
        element.addContent(subElement);
      }
    }
  }

  private final static class ContentFolderComparator implements Comparator<ContentFolder> {
    public static final ContentFolderComparator INSTANCE = new ContentFolderComparator();

    public int compare(ContentFolder o1, ContentFolder o2) {
      return o1.getUrl().compareTo(o2.getUrl());
    }
  }
}
