package com.intellij.spellchecker.generator;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspections.CheckArea;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public abstract class SpellCheckerDictionaryGenerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.spellchecker.generator.SpellCheckerDictionaryGenerator");
  private final Set<String> globalSeenNames = new HashSet<String>();
  protected final Project myProject;
  private final String myDefaultDictName;
  protected final String myDictOutputFolder;
  protected final MultiMap<String, VirtualFile> myDict2FolderMap;
  protected SpellCheckerManager mySpellCheckerManager;
  protected NamesValidator[] myNamesValidators;

  public SpellCheckerDictionaryGenerator(final Project project, final String dictOutputFolder, final String defaultDictName) {
    myDict2FolderMap = new MultiMap<String, VirtualFile>();
    myProject = project;
    myDefaultDictName = defaultDictName;
    mySpellCheckerManager = SpellCheckerManager.getInstance(myProject);
    myNamesValidators = SpellCheckingInspection.getNamesValidators();
    myDictOutputFolder = dictOutputFolder;
    SpellCheckingInspection.ensureFactoriesAreLoaded();
  }

  public void addFolder(String dictName, VirtualFile path) {
    myDict2FolderMap.putValue(dictName, path);
  }

  public void generate() {
    // let's do result a bit more predictable

    // ruby dictionary
    generate(myDefaultDictName);

    // other gem-related dictionaries in alphabet order
    final List<String> dictsList = new ArrayList<String>(myDict2FolderMap.keySet());
    Collections.sort(dictsList);

    for (String dict : dictsList) {
      if (myDefaultDictName.equals(dict)) {
        continue;
      }
      generate(dict);
    }
  }

  private void generate(@NotNull String dict) {
    System.out.println("Processing dictionary: " + dict);
    generateDictionary(myProject, myDict2FolderMap.get(dict), myDictOutputFolder + "/" + dict + ".dic");
    System.out.println("Done");
  }

  private void generateDictionary(final Project project, final Collection<VirtualFile> folderPaths, final String outFile) {
    final HashSet<String> seenNames = new HashSet<String>();
    // Collect stuff
    for (VirtualFile folder : folderPaths) {
      System.out.println("  Scanning folder: " + folder.getPath());
      final PsiManager manager = PsiManager.getInstance(project);
      processFolder(seenNames, manager, folder);
    }

    if (seenNames.isEmpty()) {
      System.out.println("  No new words was found.");
      return;
    }

    final StringBuilder builder = new StringBuilder();
    // Sort names
    final ArrayList<String> names = new ArrayList<String>(seenNames);
    Collections.sort(names);
    for (String name : names) {
      if (builder.length() > 0){
        builder.append("\n");
      }
      builder.append(name);
    }
    try {
      final File dictionaryFile = new File(outFile);
      FileUtil.createIfDoesntExist(dictionaryFile);
      final FileWriter writer = new FileWriter(dictionaryFile.getPath());
      try {
        writer.write(builder.toString());
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void processFolder(final HashSet<String> seenNames, final PsiManager manager,
                             final VirtualFile folder) {
    for (VirtualFile virtualFile : folder.getChildren()) {
      if (virtualFile.isDirectory()){
        processFolder(seenNames, manager, virtualFile);
        continue;
      }
      final PsiFile file = manager.findFile(virtualFile);
      if (file != null) {
        processFile(file, seenNames);
      }
    }
  }

  protected abstract void processFile(PsiFile file, HashSet<String> seenNames);

  protected void process(final PsiElement element, @NotNull final HashSet<String> seenNames) {
    final int endOffset = element.getTextRange().getEndOffset();

    // collect leafs  (spell checker inspection works with leafs)
    final List<PsiElement> leafs = new ArrayList<PsiElement>();
    if (element.getChildren().length == 0) {
      // if no children - it is a leaf!
      leafs.add(element);
    } else {
      // else collect leafs under given element
      PsiElement currentLeaf = PsiTreeUtil.firstChild(element);
      while (currentLeaf != null && currentLeaf.getTextRange().getEndOffset() <= endOffset) {
        leafs.add(currentLeaf);
        currentLeaf = PsiTreeUtil.nextLeaf(currentLeaf);
      }
    }

    for (PsiElement leaf : leafs) {
      processLeafsNames(leaf, seenNames);
    }
  }

  protected void processLeafsNames(@NotNull final PsiElement leafElement, @NotNull final HashSet<String> seenNames) {
    final Token[] tokens = SpellCheckingInspection.tokenize(leafElement, leafElement.getLanguage());
    if (tokens != null) {
      for (Token token : tokens) {
        final List<CheckArea> areas = token.getAreas();
        if (areas == null) {
          continue;
        }

        for (CheckArea area : areas) {
          boolean ignored = area.isIgnored();
          final TextRange textRange = area.getTextRange();

          if (ignored || textRange ==null){
            continue;
          }

          final String word = area.getWord();
          if (word == null) {
            continue;
          }

          final String lowerWord = word.toLowerCase();
          if (globalSeenNames.contains(lowerWord)) {
            continue;
          }

          boolean keyword = SpellCheckingInspection.isKeyword(myNamesValidators, token.getElement(), word);
          if (keyword){
            continue;
          }
          globalSeenNames.add(lowerWord);
          if (mySpellCheckerManager.hasProblem(lowerWord)){
            seenNames.add(lowerWord);
          }
        }
      }
    }
  }
}
