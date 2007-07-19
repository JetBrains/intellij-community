/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.importProject;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 3, 2007
 */
public class ModuleInsight {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.importProject.ModuleInsight");
  @NotNull private final ProgressIndicatorWrapper myProgress;
  
  private final List<File> myContentRoots = new ArrayList<File>();
  private final List<Pair<File, String>> mySourceRoots = new ArrayList<Pair<File, String>>(); // list of Pair: [sourceRoot-> package prefix]
  private final Set<String> myIgnoredNames = new HashSet<String>();

  private final Map<File, Set<String>> myJarToPackagesMap = new HashMap<File, Set<String>>();
  private final StringInterner myInterner = new StringInterner();
  private final JavaLexer myLexer;

  private List<ModuleDescriptor> myModules;
  private List<LibraryDescriptor> myLibraries;
  
  public ModuleInsight(@Nullable final ProgressIndicator progress) {
    this(progress, Collections.<File>emptyList(), Collections.<Pair<File,String>>emptyList(), Collections.<String>emptySet());
  }
  
  public ModuleInsight(@Nullable final ProgressIndicator progress, List<File> contentRoots, List<Pair<File,String>> sourceRoots, final Set<String> ignoredNames) {
    myLexer = new JavaLexer(LanguageLevel.JDK_1_5);
    myProgress = new ProgressIndicatorWrapper(progress);
    setRoots(contentRoots, sourceRoots, ignoredNames);
  }

  public final void setRoots(final List<File> contentRoots, final List<Pair<File, String>> sourceRoots, final Set<String> ignoredNames) {
    myModules = null;
    myLibraries = null;
    
    myContentRoots.clear();
    myContentRoots.addAll(contentRoots);
    
    mySourceRoots.clear();
    mySourceRoots.addAll(sourceRoots);
    
    myIgnoredNames.clear();
    myIgnoredNames.addAll(ignoredNames);
    
    myJarToPackagesMap.clear();
    myInterner.clear();
  }

  @Nullable
  public List<LibraryDescriptor> getSuggestedLibraries() {
    return myLibraries;
  }

  @Nullable
  public List<ModuleDescriptor> getSuggestedModules() {
    return myModules;
  }

  public void scan() {
    myProgress.setIndeterminate(true);
    myProgress.pushState();
    try {
      scanLibraries();
      if (myProgress.isCanceled()) {
        return;
      }
      scanModules();
    }
    finally {
      myProgress.popState();
    }
  }

  public void scanModules() {
    final Map<File, Set<String>> sourceRootToReferencedPackagesMap = new HashMap<File, Set<String>>();
    final Map<File, Set<String>> sourceRootToPackagesMap = new HashMap<File, Set<String>>();
    final Map<File, ModuleDescriptor> contentRootToModules = new HashMap<File, ModuleDescriptor>();

    try {
      myProgress.pushState();
      
      for (Pair<File, String> pair : mySourceRoots) {
        final File sourceRoot = pair.getFirst();
        if (myIgnoredNames.contains(sourceRoot.getName())) {
          continue;
        }
        myProgress.setText("Scanning " + sourceRoot.getPath());
        
        final HashSet<String> usedPackages = new HashSet<String>();
        sourceRootToReferencedPackagesMap.put(sourceRoot, usedPackages);
  
        final HashSet<String> selfPackages = new HashSet<String>();
        sourceRootToPackagesMap.put(sourceRoot, selfPackages);
        
        scanSources(sourceRoot, pair.getSecond(), usedPackages, selfPackages) ;
        usedPackages.removeAll(selfPackages); 
      }
      myProgress.popState();

      myProgress.pushState();
      myProgress.setText("Building modules layout...");
      for (File srcRoot : sourceRootToPackagesMap.keySet()) {
        final File moduleContentRoot = srcRoot.getParentFile();
        ModuleDescriptor moduleDescriptor = contentRootToModules.get(moduleContentRoot);
        if (moduleDescriptor != null) { // if such module aready exists
          moduleDescriptor.addSourceRoot(srcRoot);
        }
        else {
          moduleDescriptor = new ModuleDescriptor(moduleContentRoot, srcRoot);
          contentRootToModules.put(moduleContentRoot, moduleDescriptor);
        }
      }
      // build dependencies

      final Set<File> moduleContentRoots = contentRootToModules.keySet();

      for (File contentRoot : moduleContentRoots) {
        final ModuleDescriptor checkedModule = contentRootToModules.get(contentRoot);
        myProgress.setText2("Building library dependencies for module " + checkedModule.getName());
        
        // attach libraries
        for (File jarFile : myJarToPackagesMap.keySet()) {
          final Set<String> jarPackages = myJarToPackagesMap.get(jarFile);
          for (File srcRoot : checkedModule.getSourceRoots()) {
            if (intersects(sourceRootToReferencedPackagesMap.get(srcRoot), jarPackages)) {
              checkedModule.addLibraryFile(jarFile);
              break;
            }
          }
        }
        
        myProgress.setText2("Building module dependencies for module " + checkedModule.getName());
        // setup module deps
        for (File aContentRoot : moduleContentRoots) {
          final ModuleDescriptor aModule = contentRootToModules.get(aContentRoot);
          if (checkedModule.equals(aModule)) {
            continue; // avoid self-dependencies
          }
          final Set<File> aModuleRoots = aModule.getSourceRoots();
          checkModules: for (File srcRoot: checkedModule.getSourceRoots()) {
            final Set<String> referencedBySourceRoot = sourceRootToReferencedPackagesMap.get(srcRoot);
            for (File aSourceRoot : aModuleRoots) {
              if (intersects(referencedBySourceRoot, sourceRootToPackagesMap.get(aSourceRoot))) {
                checkedModule.addDependencyOn(aModule);
                break checkModules;
              }
            }
          }
        }
      }
      myProgress.popState();
    }
    catch (ProcessCanceledException ignored) {
    }
    myModules = new ArrayList<ModuleDescriptor>(contentRootToModules.values());
  }

  public void scanLibraries() {
    myProgress.pushState();
    try {
      try {
        for (File root : myContentRoots) {
          myProgress.setText("Scanning for libraries " + root.getPath());
          scanRootForLibraries(root);
        }
      }
      catch (ProcessCanceledException ignored) {
      }
      myProgress.setText("Building initial libraries layout...");
      final List<LibraryDescriptor> libraries = buildInitialLibrariesLayout(myJarToPackagesMap.keySet());
      for (LibraryDescriptor library : libraries) {
        final Collection<File> libJars = library.getJars();
        final boolean renameLib = libJars.size() == 1;
        for (File jar : libJars) {
          if (renameLib) {
            library.setName(jar.getName());
          }
        }
      }
      myLibraries = libraries;
    }
    finally {
      myProgress.popState();
    }
  }

  public void merge(final ModuleDescriptor mainModule, final ModuleDescriptor module) {
    for (File contentRoot : module.getContentRoots()) {
      appendContentRoot(mainModule, contentRoot);
    }
    for (File srcRoot : module.getSourceRoots()) {
      mainModule.addSourceRoot(srcRoot);
    }
    for (File jar : module.getLibraryFiles()) {
      mainModule.addLibraryFile(jar);
    }
    for (ModuleDescriptor dependency : module.getDependencies()) {
      mainModule.addDependencyOn(dependency);
    }
    
    myModules.remove(module);
    
    for (ModuleDescriptor moduleDescr : myModules) {
      if (moduleDescr.getDependencies().contains(module)) {
        moduleDescr.removeDependencyOn(module);
        if (!moduleDescr.equals(mainModule)) { // avoid self-dependencies
          moduleDescr.addDependencyOn(mainModule);
        }
      }
    }
  }

  public void removeLibrary(LibraryDescriptor lib) {
    myLibraries.remove(lib);
  }
  
  public void moveJarsToLibrary(final LibraryDescriptor from, Collection<File> files, LibraryDescriptor to) {
    from.removeJars(files);
    to.addJars(files);
    // remove the library if it became empty
    if (from.getJars().size() == 0) {
      removeLibrary(from);
    }
  }
  
  public LibraryDescriptor extractToNewLibrary(final LibraryDescriptor from, Collection<File> jars, String libraryName) {
    final LibraryDescriptor libraryDescriptor = new LibraryDescriptor(libraryName, new HashSet<File>());
    myLibraries.add(libraryDescriptor);
    moveJarsToLibrary(from, jars, libraryDescriptor);
    return libraryDescriptor;
  }
  
  public Collection<LibraryDescriptor> getLibraryDependencies(ModuleDescriptor module) {
    final Set<LibraryDescriptor> libs = new HashSet<LibraryDescriptor>();
    for (LibraryDescriptor library : myLibraries) {
      if (intersects(library.getJars(), module.getLibraryFiles())) {
        libs.add(library);
      }
    }
    return libs;
  }
  
  private static void appendContentRoot(final ModuleDescriptor module, final File contentRoot) {
    final Set<File> moduleRoots = module.getContentRoots();
    for (File moduleRoot : moduleRoots) {
      try {
        if (FileUtil.isAncestor(moduleRoot, contentRoot, false)) {
          return; // no need to include a separate root
        }
        if (FileUtil.isAncestor(contentRoot, moduleRoot, true)) {
          module.removeContentRoot(moduleRoot);
          module.addContentRoot(contentRoot);
          return; // no need to include a separate root
        }
      }
      catch (IOException ignored) {
      }
    }
    module.addContentRoot(contentRoot);
  }
  
  
  private static List<LibraryDescriptor> buildInitialLibrariesLayout(final Set<File> jars) {
    final Map<File, LibraryDescriptor> rootToLibraryMap = new HashMap<File, LibraryDescriptor>();
    for (File jar : jars) {
      final File parent = jar.getParentFile();
      LibraryDescriptor lib = rootToLibraryMap.get(parent);
      if (lib == null) {
        lib = new LibraryDescriptor(parent.getName(), new HashSet<File>());
        rootToLibraryMap.put(parent, lib);
      }
      lib.addJars(Collections.singleton(jar));
    }
    return new ArrayList<LibraryDescriptor>(rootToLibraryMap.values());
  }

  private static <T> boolean intersects(Collection<T> set1, Collection<T> set2) {
    for (final T item : set1) {
      if (set2.contains(item)) {
        return true;
      }
    }
    return false;
  }
  
  private void scanSources(final File fromRoot, final String parentPackageName, final Set<String> usedPackages, final Set<String> selfPackages) {
    if (myIgnoredNames.contains(fromRoot.getName())) {
      return;
    }
    final File[] files = fromRoot.listFiles();
    if (files != null) {
      myProgress.checkCanceled();
      boolean includeParentName = false;
      for (File file : files) {
        if (file.isDirectory()) {
          final String subPackageName;
          final StringBuilder builder = StringBuilderSpinAllocator.alloc();
          try {
            builder.append(parentPackageName);
            if (builder.length() > 0) {
              builder.append(".");
            }
            builder.append(file.getName());
            subPackageName = builder.toString();
          }
          finally {
            StringBuilderSpinAllocator.dispose(builder);
          }
          scanSources(file, subPackageName, usedPackages, selfPackages);
        }
        else {
          if (StringUtil.endsWithIgnoreCase(file.getName(), ".java")) {
            includeParentName = true;
            scanJavaFile(file, usedPackages);
          }
        }
      }
      if (includeParentName) {
        selfPackages.add(myInterner.intern(parentPackageName));
      }
    }
  }
  
  private void scanJavaFile(File file, final Set<String> usedPackages) {
    myProgress.setText2(file.getName());
    try {
      final char[] chars = FileUtil.loadFileText(file);
      scanImportStatements(chars, myLexer, usedPackages);
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }
  
  private void scanRootForLibraries(File fromRoot) {
    if (myIgnoredNames.contains(fromRoot.getName())) {
      return;
    }
    final File[] files = fromRoot.listFiles();
    if (files != null) {
      myProgress.checkCanceled();
      for (File file : files) {
        if (file.isDirectory()) {
          scanRootForLibraries(file);
        }
        else {
          final String fileName = file.getName();
          if (StringUtil.endsWithIgnoreCase(fileName, ".jar") || StringUtil.endsWithIgnoreCase(fileName, ".zip")) {
            if (!myJarToPackagesMap.containsKey(file)) {
              final HashSet<String> libraryPackages = new HashSet<String>();
              myJarToPackagesMap.put(file, libraryPackages);
              
              scanLibrary(file, libraryPackages);
            }
          }
        }
      }
    }
  }
  
  
  private void scanLibrary(File file, Set<String> libraryPackages) {
    myProgress.pushState();
    myProgress.setText2(file.getName());
    try {
      final ZipFile zip = new ZipFile(file);
      final Enumeration<? extends ZipEntry> entries = zip.entries();
      while(entries.hasMoreElements()) {
        final String entryName = entries.nextElement().getName();
        if (StringUtil.endsWithIgnoreCase(entryName, ".class")) {
          final int index = entryName.lastIndexOf('/');
          if (index > 0) {
            final String packageName = entryName.substring(0, index).replace('/', '.');
            if (!libraryPackages.contains(packageName)) {
              libraryPackages.add(myInterner.intern(packageName));
            }
          }
        }
      }
      zip.close();
    }
    catch (IOException e) {
      LOG.info(e);
    }
    finally {
      myProgress.popState();
    }
  }
  
  private void scanImportStatements(char[] text, final Lexer lexer, final Set<String> usedPackages){
    lexer.start(new CharArrayCharSequence(text), 0, text.length, 0);
    
    skipWhiteSpaceAndComments(lexer);
    if (lexer.getTokenType() != JavaTokenType.PACKAGE_KEYWORD) {
      return;
    }
    
    advanceLexer(lexer);
    if (readPackageName(text, lexer) == null) {
      return;
    }
    
    while (true) {
      if (lexer.getTokenType() != JavaTokenType.SEMICOLON) {
        return;
      }
      advanceLexer(lexer);
      
      if (lexer.getTokenType() != JavaTokenType.IMPORT_KEYWORD) {
        return;
      }
      advanceLexer(lexer);
      
      boolean isStaticImport = false;
      if (lexer.getTokenType() == JavaTokenType.STATIC_KEYWORD) {
        isStaticImport = true;
        advanceLexer(lexer);
      }
      
      final String packageName = readPackageName(text, lexer);
      if (packageName == null) {
        return;
      }
      
      if (packageName.endsWith(".*")) {
        usedPackages.add(myInterner.intern(packageName.substring(0, packageName.length() - ".*".length())));
      }
      else {
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot > 0) {
          String _packageName = packageName.substring(0, lastDot);
          if (isStaticImport) {
            lastDot = _packageName.lastIndexOf('.');
            _packageName = lastDot > 0? _packageName.substring(0, lastDot) : null;
          }
          if (_packageName != null) {
            usedPackages.add(myInterner.intern(_packageName));
          }
        }
      }
    }
  }

  @Nullable
  private static String readPackageName(final char[] text, final Lexer lexer) {
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      while(true){
      if (lexer.getTokenType() != JavaTokenType.IDENTIFIER && lexer.getTokenType() != JavaTokenType.ASTERISK) {
          break;
        }
        buffer.append(text, lexer.getTokenStart(), lexer.getTokenEnd() - lexer.getTokenStart());

        advanceLexer(lexer);
        if (lexer.getTokenType() != JavaTokenType.DOT) {
          break;
        }
        buffer.append('.');

        advanceLexer(lexer);
      }

      String packageName = buffer.toString();
      if (packageName.length() == 0 || StringUtil.endsWithChar(packageName, '.') || StringUtil.startsWithChar(packageName, '*') ) {
        return null;
      }
      return packageName;
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  private static void advanceLexer(final Lexer lexer) {
    lexer.advance();
    skipWhiteSpaceAndComments(lexer);
  }
  
  private static void skipWhiteSpaceAndComments(Lexer lexer){
    while(JavaTokenType.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(lexer.getTokenType())) {
      lexer.advance();
    }
  }


}
