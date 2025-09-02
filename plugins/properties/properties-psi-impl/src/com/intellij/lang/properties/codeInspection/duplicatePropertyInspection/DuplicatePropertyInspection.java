// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.codeInspection.duplicatePropertyInspection;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.LowLevelSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.*;

public final class DuplicatePropertyInspection extends GlobalSimpleInspectionTool {
  public boolean CURRENT_FILE = true;
  public boolean MODULE_WITH_DEPENDENCIES = false;

  public boolean CHECK_DUPLICATE_VALUES = true;
  public boolean CHECK_DUPLICATE_KEYS = true;
  public boolean CHECK_DUPLICATE_KEYS_WITH_DIFFERENT_VALUES = true;

  @Override
  public void checkFile(@NotNull PsiFile psiFile,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    checkFile(psiFile, manager, (GlobalInspectionContextBase)globalContext, globalContext.getRefManager(), problemDescriptionsProcessor);
  }

  private static void surroundWithHref(@NotNull StringBuilder anchor, PsiElement element, final boolean isValue) {
    if (element != null) {
      final PsiElement parent = element.getParent();
      PsiElement elementToLink = isValue ? parent.getFirstChild() : parent.getLastChild();
      if (elementToLink != null) {
        HTMLComposer.appendAfterHeaderIndention(anchor);
        HTMLComposer.appendAfterHeaderIndention(anchor);
        String link = "";
        final PsiFile file = element.getContainingFile();
        if (file != null) {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            link = virtualFile.getUrl() + "#" + elementToLink.getTextRange().getStartOffset();
          }
        }
        HtmlChunk.link(link, elementToLink.getText().replaceAll("\\$", "\\\\\\$")).appendTo(anchor);
        compoundLineLink(anchor, element);
        anchor.append("<br>");
      }
    }
    else {
      HtmlChunk.tag("font")
        .style("font-family:verdana; font-weight:bold; color:#FF0000")
        .addText(PropertiesBundle.message("inspection.export.results.invalidated.item")).appendTo(anchor);
    }
  }

  private static void compoundLineLink(@NotNull StringBuilder lineAnchor, PsiElement psiElement) {
    final PsiFile file = psiElement.getContainingFile();
    if (file != null) {
      final VirtualFile vFile = file.getVirtualFile();
      if (vFile != null) {
        Document doc = FileDocumentManager.getInstance().getDocument(vFile);
        final int lineNumber = doc.getLineNumber(psiElement.getTextOffset()) + 1;
        lineAnchor.append(" ").append(AnalysisBundle.message("inspection.export.results.at.line")).append(" ");
        int offset = doc.getLineStartOffset(lineNumber - 1);
        offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), offset, " \t");
        HtmlChunk.link(vFile.getUrl() + "#" + offset, String.valueOf(lineNumber)).appendTo(lineAnchor);
      }
    }
  }

  private void checkFile(final PsiFile file,
                         final InspectionManager manager,
                         GlobalInspectionContextBase context,
                         final RefManager refManager,
                         final ProblemDescriptionsProcessor processor) {
    if (!(file instanceof PropertiesFile propertiesFile)) return;
    if (!context.isToCheckFile(file, this) || SuppressionUtil.inspectionResultSuppressed(file, this)) return;
    final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(file.getProject());
    final List<IProperty> properties = propertiesFile.getProperties();
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return;
    final GlobalSearchScope scope = GlobalSearchScope.getScopeRestrictedByFileTypes(CURRENT_FILE
                                    ? GlobalSearchScope.fileScope(file)
                                    : MODULE_WITH_DEPENDENCIES
                                      ? GlobalSearchScope.moduleWithDependenciesScope(module)
                                      : GlobalSearchScope.projectScope(file.getProject()), PropertiesFileType.INSTANCE);
    final Map<String, Set<PsiFile>> processedValueToFiles = Collections.synchronizedMap(new HashMap<>());
    final Map<String, Set<PsiFile>> processedKeyToFiles = Collections.synchronizedMap(new HashMap<>());
    final ProgressIndicator original = ProgressManager.getInstance().getProgressIndicator();
    final ProgressIndicator progress = ProgressWrapper.wrap(original);
    ProgressManager.getInstance().runProcess(() -> {
      if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(properties, progress, property -> {
        if (original != null) {
          if (original.isCanceled()) return false;
          original.setText2(PropertiesBundle.message("searching.for.property.key.progress.text", property.getUnescapedKey()));
        }
        processTextUsages(processedValueToFiles, property.getValue(), processedKeyToFiles, searchHelper, scope);
        processTextUsages(processedKeyToFiles, property.getUnescapedKey(), processedValueToFiles, searchHelper, scope);
        return true;
      })) throw new ProcessCanceledException();

      List<ProblemDescriptor> problemDescriptors = new ArrayList<>();
      Map<String, Set<String>> keyToDifferentValues = new HashMap<>();
      if (CHECK_DUPLICATE_KEYS || CHECK_DUPLICATE_KEYS_WITH_DIFFERENT_VALUES) {
        prepareDuplicateKeysByFile(processedKeyToFiles, manager, keyToDifferentValues, problemDescriptors, file, original);
      }
      if (CHECK_DUPLICATE_VALUES) prepareDuplicateValuesByFile(processedValueToFiles, manager, problemDescriptors, file, original);
      if (CHECK_DUPLICATE_KEYS_WITH_DIFFERENT_VALUES) {
        processDuplicateKeysWithDifferentValues(keyToDifferentValues, processedKeyToFiles, problemDescriptors, manager, file, original);
      }
      if (!problemDescriptors.isEmpty()) {
        processor.addProblemElement(refManager.getReference(file),
                                    problemDescriptors.toArray(ProblemDescriptor.EMPTY_ARRAY));
      }
    }, progress);
  }

  private static void processTextUsages(final Map<String, Set<PsiFile>> processedTextToFiles,
                                        final String text,
                                        final Map<String, Set<PsiFile>> processedFoundTextToFiles,
                                        final PsiSearchHelper searchHelper,
                                        final GlobalSearchScope scope) {
    if (!processedTextToFiles.containsKey(text)) {
      if (processedFoundTextToFiles.containsKey(text)) {
        final Set<PsiFile> filesWithValue = processedFoundTextToFiles.get(text);
        processedTextToFiles.put(text, filesWithValue);
      }
      else {
        final Set<PsiFile> resultFiles = new HashSet<>();
        findFilesWithText(text, searchHelper, scope, resultFiles);
        if (resultFiles.isEmpty()) return;
        processedTextToFiles.put(text, resultFiles);
      }
    }
  }


  private static void prepareDuplicateValuesByFile(final Map<String, Set<PsiFile>> valueToFiles,
                                                   final InspectionManager manager,
                                                   final List<? super ProblemDescriptor> problemDescriptors,
                                                   final PsiFile psiFile,
                                                   final ProgressIndicator progress) {
    for (final String value : valueToFiles.keySet()) {
      if (progress != null){
        progress.setText2(PropertiesBundle.message("duplicate.property.value.progress.indicator.text", value));
        progress.checkCanceled();
      }
      if (value.isEmpty()) continue;
      StringSearcher searcher = new StringSearcher(value, true, true);
      @Nls StringBuilder message = new StringBuilder();
      final int[] duplicatesCount = {0};
      Property[] propertyInCurrentFile = new Property[1];
      Set<PsiFile> psiFilesWithDuplicates = valueToFiles.get(value);
      for (final PsiFile file : psiFilesWithDuplicates) {
        CharSequence text = file.getViewProvider().getContents();
        LowLevelSearchUtil.processTexts(text, 0, text.length(), searcher, offset -> {
          PsiElement element = file.findElementAt(offset);
          if (element != null && element.getParent() instanceof Property property) {
            if (Objects.equals(property.getValue(), value) && element.getStartOffsetInParent() != 0) {
              if (duplicatesCount[0] == 0){
                message.append(PropertiesBundle.message("duplicate.property.value.problem.descriptor", property.getValue()));
              }
              surroundWithHref(message, element, true);
              duplicatesCount[0]++;
              if (propertyInCurrentFile[0] == null && psiFile == file) {
                propertyInCurrentFile[0] = property;
              }
            }
          }
          return true;
        });
      }
      if (duplicatesCount[0] > 1) {
        PsiElement elementToHighlight = ObjectUtils.notNull(propertyInCurrentFile[0], psiFile);
        problemDescriptors.add(manager.createProblemDescriptor(elementToHighlight, message.toString(), false, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }


  }

  private void prepareDuplicateKeysByFile(final Map<String, Set<PsiFile>> keyToFiles,
                                          final InspectionManager manager,
                                          final Map<String, Set<String>> keyToValues,
                                          final List<? super ProblemDescriptor> problemDescriptors,
                                          final PsiFile psiFile,
                                          final ProgressIndicator progress) {
    for (String key : keyToFiles.keySet()) {
      if (progress!= null){
        progress.setText2(PropertiesBundle.message("duplicate.property.key.progress.indicator.text", key));
        ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(progress);
      }
      @Nls StringBuilder message = new StringBuilder();
      int duplicatesCount = 0;
      PsiElement propertyInCurrentFile = null;
      Set<PsiFile> psiFilesWithDuplicates = keyToFiles.get(key);
      for (PsiFile file : psiFilesWithDuplicates) {
        if (!(file instanceof PropertiesFile propertiesFile)) continue;
        final List<IProperty> propertiesByKey = propertiesFile.findPropertiesByKey(key);
        for (IProperty property : propertiesByKey) {
          if (duplicatesCount == 0){
            message.append(PropertiesBundle.message("duplicate.property.key.problem.descriptor", key));
          }
          surroundWithHref(message, property.getPsiElement().getFirstChild(), false);
          duplicatesCount ++;
          //prepare for filter same keys different values
          Set<String> values = keyToValues.get(key);
          if (values == null){
            values = new HashSet<>();
            keyToValues.put(key, values);
          }
          values.add(property.getValue());
          if (propertyInCurrentFile == null && file == psiFile) {
            propertyInCurrentFile = property.getPsiElement();
          }
        }
      }
      if (duplicatesCount > 1 && CHECK_DUPLICATE_KEYS) {
        problemDescriptors.add(manager.createProblemDescriptor(ObjectUtils.notNull(propertyInCurrentFile, psiFile), message.toString(), false, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }

  }


  private static void processDuplicateKeysWithDifferentValues(final Map<String, Set<String>> keyToDifferentValues,
                                                              final Map<String, Set<PsiFile>> keyToFiles,
                                                              final List<? super ProblemDescriptor> problemDescriptors,
                                                              final InspectionManager manager,
                                                              final PsiFile psiFile,
                                                              final ProgressIndicator progress) {
    for (String key : keyToDifferentValues.keySet()) {
      if (progress != null) {
        progress.setText2(PropertiesBundle.message("duplicate.property.diff.key.progress.indicator.text", key));
        ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(progress);
      }
      final Set<String> values = keyToDifferentValues.get(key);
      if (values == null || values.size() < 2){
        keyToFiles.remove(key);
      } else {
        @Nls StringBuilder message = new StringBuilder();
        final Set<PsiFile> psiFiles = keyToFiles.get(key);
        boolean firstUsage = true;
        for (PsiFile file : psiFiles) {
          if (!(file instanceof PropertiesFile propertiesFile)) continue;
          final List<IProperty> propertiesByKey = propertiesFile.findPropertiesByKey(key);
          for (IProperty property : propertiesByKey) {
            if (firstUsage){
              message.append(PropertiesBundle.message("duplicate.property.diff.key.problem.descriptor", key));
              firstUsage = false;
            }
            surroundWithHref(message, property.getPsiElement().getFirstChild(), false);
          }
        }
        problemDescriptors.add(manager.createProblemDescriptor(psiFile, message.toString(), false, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }
  }

  private static void findFilesWithText(String stringToFind,
                                        PsiSearchHelper searchHelper,
                                        GlobalSearchScope scope,
                                        final Set<? super PsiFile> resultFiles) {
    final List<String> words = ContainerUtil.sorted(StringUtil.getWordsIn(stringToFind),(o1, o2) -> o2.length() - o1.length());
    if (words.isEmpty()) return;
    for (String word : words) {
      final Set<PsiFile> files = new HashSet<>();
      searchHelper.processAllFilesWithWord(word, scope, Processors.cancelableCollectProcessor(files), true);
      if (resultFiles.isEmpty()) {
        resultFiles.addAll(files);
      }
      else {
        resultFiles.retainAll(files);
      }
      if (resultFiles.isEmpty()) return;
    }
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.properties.files");
  }

  @Override
  public @NotNull String getShortName() {
    return "DuplicatePropertyInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    @SuppressWarnings("InjectedReferences")
    OptDropdown scope = dropdown("SCOPE", PropertiesBundle.message("label.analysis.scope"),
                                 option("file", PropertiesBundle.message("duplicate.property.file.scope.option")),
                                 option("module", PropertiesBundle.message("duplicate.property.module.scope.option")),
                                 option("project", PropertiesBundle.message("duplicate.property.project.scope.option")));
    return pane(
      scope,
      checkbox("CHECK_DUPLICATE_VALUES", PropertiesBundle.message("duplicate.property.value.option")),
      checkbox("CHECK_DUPLICATE_KEYS_WITH_DIFFERENT_VALUES", PropertiesBundle.message("duplicate.property.diff.key.option")),
      checkbox("CHECK_DUPLICATE_KEYS", PropertiesBundle.message("duplicate.property.key.option"))
    );
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController().onValue(
      "SCOPE",
      () -> CURRENT_FILE ? "file" : MODULE_WITH_DEPENDENCIES ? "module" : "project",
      value -> {
        CURRENT_FILE = "file".equals(value);
        MODULE_WITH_DEPENDENCIES = "module".equals(value);
      });
  }
}
