// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicatePropertyInspection;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalSimpleInspectionTool;
import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.LowLevelSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processors;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashSet;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import org.jetbrains.annotations.NotNull;

public class DuplicatePropertyInspection extends GlobalSimpleInspectionTool {
  public boolean CURRENT_FILE = true;
  public boolean MODULE_WITH_DEPENDENCIES = false;

  public boolean CHECK_DUPLICATE_VALUES = true;
  public boolean CHECK_DUPLICATE_KEYS = true;
  public boolean CHECK_DUPLICATE_KEYS_WITH_DIFFERENT_VALUES = true;

  @Override
  public void checkFile(@NotNull PsiFile file,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    checkFile(file, manager, (GlobalInspectionContextBase)globalContext, globalContext.getRefManager(), problemDescriptionsProcessor);
  }

  private static void surroundWithHref(@NotNull StringBuilder anchor, PsiElement element, final boolean isValue) {
    if (element != null) {
      final PsiElement parent = element.getParent();
      PsiElement elementToLink = isValue ? parent.getFirstChild() : parent.getLastChild();
      if (elementToLink != null) {
        HTMLComposer.appendAfterHeaderIndention(anchor);
        HTMLComposer.appendAfterHeaderIndention(anchor);
        anchor.append("<a HREF=\"");
        final PsiFile file = element.getContainingFile();
        if (file != null) {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            anchor.append(virtualFile.getUrl()).append("#").append(elementToLink.getTextRange().getStartOffset());
          }
        }
        anchor.append("\">");
        anchor.append(elementToLink.getText().replaceAll("\\$", "\\\\\\$"));
        anchor.append("</a>");
        compoundLineLink(anchor, element);
        anchor.append("<br>");
      }
    }
    else {
      anchor.append("<font style=\"font-family:verdana; font-weight:bold; color:#FF0000\";>");
      anchor.append(PropertiesBundle.message("inspection.export.results.invalidated.item"));
      anchor.append("</font>");
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
        lineAnchor.append("<a HREF=\"");
        int offset = doc.getLineStartOffset(lineNumber - 1);
        offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), offset, " \t");
        lineAnchor.append(vFile.getUrl()).append("#").append(offset);
        lineAnchor.append("\">");
        lineAnchor.append(lineNumber);
        lineAnchor.append("</a>");
      }
    }
  }

  private void checkFile(final PsiFile file,
                         final InspectionManager manager,
                         GlobalInspectionContextBase context,
                         final RefManager refManager,
                         final ProblemDescriptionsProcessor processor) {
    if (!(file instanceof PropertiesFile)) return;
    if (!context.isToCheckFile(file, this) || SuppressionUtil.inspectionResultSuppressed(file, this)) return;
    final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(file.getProject());
    final PropertiesFile propertiesFile = (PropertiesFile)file;
    final List<IProperty> properties = propertiesFile.getProperties();
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return;
    final GlobalSearchScope scope = CURRENT_FILE
                                    ? GlobalSearchScope.fileScope(file)
                                    : MODULE_WITH_DEPENDENCIES
                                      ? GlobalSearchScope.moduleWithDependenciesScope(module)
                                      : GlobalSearchScope.projectScope(file.getProject());
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
      if (value.length() == 0) continue;
      StringSearcher searcher = new StringSearcher(value, true, true);
      StringBuilder message = new StringBuilder();
      final int[] duplicatesCount = {0};
      Property[] propertyInCurrentFile = new Property[1];
      Set<PsiFile> psiFilesWithDuplicates = valueToFiles.get(value);
      for (final PsiFile file : psiFilesWithDuplicates) {
        CharSequence text = file.getViewProvider().getContents();
        LowLevelSearchUtil.processTextOccurrences(text, 0, text.length(), searcher, offset -> {
          PsiElement element = file.findElementAt(offset);
          if (element != null && element.getParent() instanceof Property) {
            final Property property = (Property)element.getParent();
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
      StringBuilder message = new StringBuilder();
      int duplicatesCount = 0;
      PsiElement propertyInCurrentFile = null;
      Set<PsiFile> psiFilesWithDuplicates = keyToFiles.get(key);
      for (PsiFile file : psiFilesWithDuplicates) {
        if (!(file instanceof PropertiesFile)) continue;
        PropertiesFile propertiesFile = (PropertiesFile)file;
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
        StringBuilder message = new StringBuilder();
        final Set<PsiFile> psiFiles = keyToFiles.get(key);
        boolean firstUsage = true;
        for (PsiFile file : psiFiles) {
          if (!(file instanceof PropertiesFile)) continue;
          PropertiesFile propertiesFile = (PropertiesFile)file;
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
    final List<String> words = StringUtil.getWordsIn(stringToFind);
    if (words.isEmpty()) return;
    words.sort((o1, o2) -> o2.length() - o1.length());
    for (String word : words) {
      final Set<PsiFile> files = new THashSet<>();
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
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.properties.files");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "DuplicatePropertyInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel().myWholePanel;
  }

  public class OptionsPanel {
    private JRadioButton myFileScope;
    private JRadioButton myModuleScope;
    private JRadioButton myProjectScope;
    private JCheckBox myDuplicateValues;
    private JCheckBox myDuplicateKeys;
    private JCheckBox myDuplicateBoth;
    private JPanel myWholePanel;

    OptionsPanel() {
      ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(myFileScope);
      buttonGroup.add(myModuleScope);
      buttonGroup.add(myProjectScope);

      myFileScope.setSelected(CURRENT_FILE);
      myModuleScope.setSelected(MODULE_WITH_DEPENDENCIES);
      myProjectScope.setSelected(!(CURRENT_FILE || MODULE_WITH_DEPENDENCIES));

      myFileScope.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          CURRENT_FILE = myFileScope.isSelected();
        }
      });
      myModuleScope.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          MODULE_WITH_DEPENDENCIES = myModuleScope.isSelected();
          if (MODULE_WITH_DEPENDENCIES) {
            CURRENT_FILE = false;
          }
        }
      });
      myProjectScope.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myProjectScope.isSelected()) {
            CURRENT_FILE = false;
            MODULE_WITH_DEPENDENCIES = false;
          }
        }
      });

      myDuplicateKeys.setSelected(CHECK_DUPLICATE_KEYS);
      myDuplicateValues.setSelected(CHECK_DUPLICATE_VALUES);
      myDuplicateBoth.setSelected(CHECK_DUPLICATE_KEYS_WITH_DIFFERENT_VALUES);

      myDuplicateKeys.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          CHECK_DUPLICATE_KEYS = myDuplicateKeys.isSelected();
        }
      });
      myDuplicateValues.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          CHECK_DUPLICATE_VALUES = myDuplicateValues.isSelected();
        }
      });
      myDuplicateBoth.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          CHECK_DUPLICATE_KEYS_WITH_DIFFERENT_VALUES = myDuplicateBoth.isSelected();
        }
      });
    }
  }
}
