/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.importProject;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NonNls;
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
  private final List<File> myContentRoots;
  private final List<Pair<File, String>> mySourceRoots; // list of Pair: [sourceRoot-> package prefix]
  private final Set<File> myIgnoredRoots;

  private final Map<File, Set<String>> myJarToPackagesMap = new HashMap<File, Set<String>>();
  private final Map<File, Set<String>> mySourceRootToReferencedPackagesMap = new HashMap<File, Set<String>>();
  private final Map<File, Set<String>> mySourceRootToPackagesMap = new HashMap<File, Set<String>>();
  
  private final Map<File, ModuleDescriptor> myContentRootToModules = new HashMap<File, ModuleDescriptor>();
  
  private final StringInterner myInterner = new StringInterner();
  private final JavaLexer myLexer;

  public ModuleInsight(@Nullable final ProgressIndicator progress, List<File> contentRoots, List<Pair<File,String>> sourceRoots, final Set<File> ignoredRoots) {
    myProgress = new ProgressIndicatorWrapper(progress);
    myContentRoots = contentRoots;
    mySourceRoots = sourceRoots;
    myIgnoredRoots = ignoredRoots;
    myLexer = new JavaLexer(LanguageLevel.JDK_1_5);
  }
  
  public List<ModuleDescriptor> getSuggestedModules() {
    return new ArrayList<ModuleDescriptor>(myContentRootToModules.values());
  }
  
  public void scan() {
    myProgress.setIndeterminate(true);
    myProgress.pushState();
    try {
      myProgress.pushState();
      for (File root : myContentRoots) {
        myProgress.setText("Scanning for libraries " + root.getPath());
        scanLibraries(root);
      }
      myProgress.popState();
      
      myProgress.pushState();
      for (Pair<File, String> pair : mySourceRoots) {
        final File sourceRoot = pair.getFirst();
        if (myIgnoredRoots.contains(sourceRoot)) {
          continue;
        }
        myProgress.setText("Scanning " + sourceRoot.getPath());
        
        final HashSet<String> usedPackages = new HashSet<String>();
        mySourceRootToReferencedPackagesMap.put(sourceRoot, usedPackages);

        final HashSet<String> selfPackages = new HashSet<String>();
        mySourceRootToPackagesMap.put(sourceRoot, selfPackages);
        
        scanSources(sourceRoot, pair.getSecond(), usedPackages, selfPackages) ;
        usedPackages.removeAll(selfPackages); 
      }
      myProgress.popState();
      
      myProgress.setText("Creating modules layout...");
      for (File srcRoot : mySourceRootToPackagesMap.keySet()) {
        File moduleContentRoot = srcRoot.getParentFile();
        ModuleDescriptor moduleDescriptor = myContentRootToModules.get(moduleContentRoot);
        if (moduleDescriptor != null) { // if such module aready exists
          moduleDescriptor.addSourceRoot(srcRoot);
        }
        else {
          moduleDescriptor = new ModuleDescriptor(moduleContentRoot, srcRoot);
          myContentRootToModules.put(moduleContentRoot, moduleDescriptor);
        }
      }
      // build dependencies
      
      final Set<File> moduleContentRoots = myContentRootToModules.keySet();
      
      for (File contentRoot : moduleContentRoots) {
        final ModuleDescriptor checkedModule = myContentRootToModules.get(contentRoot);
        
        // attach libraries
        for (File jarFile : myJarToPackagesMap.keySet()) {
          final Set<String> jarPackages = myJarToPackagesMap.get(jarFile);
          for (File srcRoot : checkedModule.getSourceRoots()) {
            if (intersects(mySourceRootToReferencedPackagesMap.get(srcRoot), jarPackages)) {
              checkedModule.attachLibrary(jarFile);
              break;
            }
          }
        }
        
        // setup module deps
        for (File aContentRoot : moduleContentRoots) {
          final ModuleDescriptor aModule = myContentRootToModules.get(aContentRoot);
          if (checkedModule.equals(aModule)) {
            continue; // avoid self-dependencies
          }
          final Set<File> aModuleRoots = aModule.getSourceRoots();
          checkModules: for (File srcRoot: checkedModule.getSourceRoots()) {
            final Set<String> referencedBySourceRoot = mySourceRootToReferencedPackagesMap.get(srcRoot);
            for (File aSourceRoot : aModuleRoots) {
              if (intersects(referencedBySourceRoot, mySourceRootToPackagesMap.get(aSourceRoot))) {
                checkedModule.dependsOn(aModule);
                break checkModules;
              }
            }
          }
        }
      }
    }
    finally {
      myProgress.popState();
    }
  }

  private static <T> boolean intersects(Set<T> set1, Set<T> set2) {
    for (final T item : set1) {
      if (set2.contains(item)) {
        return true;
      }
    }
    return false;
  }
  
  private void scanSources(final File fromRoot, final String parentPackageName, final Set<String> usedPackages, final Set<String> selfPackages) {
    if (myIgnoredRoots.contains(fromRoot)) {
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
  
  private void scanLibraries(File fromRoot) {
    if (myIgnoredRoots.contains(fromRoot)) {
      return;
    }
    final File[] files = fromRoot.listFiles();
    if (files != null) {
      myProgress.checkCanceled();
      for (File file : files) {
        if (file.isDirectory()) {
          scanLibraries(file);
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
  
  
  public static class ModuleDescriptor {
    final Set<File> myContentRoots = new HashSet<File>();
    final Set<File> mySourceRoots = new HashSet<File>();
    final Set<File> myLibraries = new HashSet<File>();
    final Set<ModuleDescriptor> myDependencies = new HashSet<ModuleDescriptor>();
    
    public ModuleDescriptor(final File contentRoot, final File sourceRoot) {
      myContentRoots.add(contentRoot);
      mySourceRoots.add(sourceRoot);
    }

    public Set<File> getContentRoots() {
      return myContentRoots;
    }

    public Set<File> getSourceRoots() {
      return mySourceRoots;
    }

    public void addSourceRoot(File sourceRoot) {
      mySourceRoots.add(sourceRoot);
    }
    
    public void dependsOn(ModuleDescriptor dependence) {
      myDependencies.add(dependence);
    }
    
    public void attachLibrary(File libFile) {
      myLibraries.add(libFile);
    }

    public Set<File> getLibraries() {
      return myLibraries;
    }

    public Set<ModuleDescriptor> getDependencies() {
      return myDependencies;
    }

    /**
     * For debug purposes only
     */
    public String toString() {
      @NonNls final StringBuilder builder = new StringBuilder();
      builder.append("[Module: ").append(myContentRoots).append(" | ");
      for (File sourceRoot : mySourceRoots) {
        builder.append(sourceRoot.getName()).append(",");
      }
      builder.append("]");
      return builder.toString();
    }
  }
  
  
}
