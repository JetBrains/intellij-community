package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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


  private ContentEntryImpl(VirtualFilePointer root, RootModelImpl rootModel) {
    super(rootModel);
    myRootModel = rootModel;
    myRoot = rootModel.pointerFactory().duplicate(root);
  }

  ContentEntryImpl(Element element, RootModelImpl rootModel) throws InvalidDataException {
    super(rootModel);
    myRootModel = rootModel;
    LOG.assertTrue(ELEMENT_NAME.equals(element.getName()));
    final String urlAttrValue = element.getAttributeValue(URL_ATTR);
    if (urlAttrValue == null) throw new InvalidDataException();
    myRoot = myRootModel.pointerFactory().create(urlAttrValue);

    mySourceFolders.clear();
    final List sourceChildren = element.getChildren(SourceFolderImpl.ELEMENT_NAME);
    for (Object aSourceChildren : sourceChildren) {
      Element sourceEntryElement = (Element)aSourceChildren;
      mySourceFolders.add(new SourceFolderImpl(sourceEntryElement, this));
    }

    myExcludeFolders.clear();
    final List excludeChildren = element.getChildren(ExcludeFolderImpl.ELEMENT_NAME);
    for (Object aExcludeChildren : excludeChildren) {
      Element excludeFolderElement = (Element)aExcludeChildren;
      myExcludeFolders.add(new ExcludeFolderImpl(excludeFolderElement, this));
    }
  }

  ContentEntryImpl(VirtualFile file, RootModelImpl rootModel) {
    super(rootModel);
    myRootModel = rootModel;
    myRoot = myRootModel.pointerFactory().create(file);
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
        ProjectRootManagerImpl projectRootManager = ProjectRootManagerImpl.getInstanceImpl(myRootModel.getModule().getProject());
        final String outputUrl = projectRootManager.getCompilerOutputUrl();
        if (outputUrl != null){
          if (new File(VfsUtil.urlToPath(outputUrl)).exists()){
            addExcludeForOutputPath(projectRootManager.getCompilerOutputPointer(), result);
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
    return addSourceFolder(file, isTestSource, SourceFolderImpl.DEFAULT_PACKAGE_PREFIX);
  }

  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix) {
    myRootModel.assertWritable();
    assertFolderUnderMe(file);
    final SourceFolderImpl simpleSourceFolder = new SourceFolderImpl(file, isTestSource, packagePrefix, this);
    mySourceFolders.add(simpleSourceFolder);
    return simpleSourceFolder;
  }

  private void assertFolderUnderMe(VirtualFile file) {
    final VirtualFile rootFile = getFile();
    if (rootFile == null) {
      LOG.assertTrue(false, "Content entry file is null");
      return;
    }
    if (!VfsUtil.isAncestor(rootFile, file, false)) {
      LOG.assertTrue(false, "The file " + file.getPresentableUrl() + " is not under content entry root " + rootFile.getPresentableUrl());
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

  public boolean isSynthetic() {
    return false;
  }

  public void removeSourceFolder(@NotNull SourceFolder sourceFolder) {
    myRootModel.assertWritable();
    LOG.assertTrue(mySourceFolders.contains(sourceFolder));
    mySourceFolders.remove(sourceFolder);
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

  public ExcludeFolder addExcludeFolder(@NotNull VirtualFile file) {
    myRootModel.assertWritable();
    assertFolderUnderMe(file);
    final ExcludeFolderImpl excludeFolder = new ExcludeFolderImpl(file, this);
    myExcludeFolders.add(excludeFolder);
    return excludeFolder;
  }

  public void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder) {
    myRootModel.assertWritable();
    LOG.assertTrue(myExcludeFolders.contains(excludeFolder));
    myExcludeFolders.remove(excludeFolder);
  }

  public ContentEntry cloneEntry(RootModelImpl rootModel) {
    final ContentEntryImpl cloned = new ContentEntryImpl(myRoot, rootModel);
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

  RootModelImpl getRootModel() {
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

  private final static class ContentFolderComparator implements Comparator<ContentFolder> {
    public static final ContentFolderComparator INSTANCE = new ContentFolderComparator();

    public int compare(ContentFolder o1, ContentFolder o2) {
      return o1.getUrl().compareTo(o2.getUrl());
    }
  }
}
