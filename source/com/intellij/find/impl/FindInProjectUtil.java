package com.intellij.find.impl;

import com.intellij.Patches;
import com.intellij.navigation.ItemPresentation;
import com.intellij.usages.impl.UsageViewImplUtil;
import com.intellij.usages.*;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.FindProgressIndicator;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.*;
import com.intellij.usageView.AsyncFindUsagesProcessListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
import java.util.regex.Pattern;

public class FindInProjectUtil {

  public static void setDirectoryName(FindModel model, DataContext dataContext) {
    PsiElement psiElement = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);

    String directoryName = null;

    if (psiElement instanceof PsiDirectory) {
      directoryName = ((PsiDirectory)psiElement).getVirtualFile().getPresentableUrl();
    }
    else {
      final PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
      if (psiFile != null) {
        PsiDirectory psiDirectory = psiFile.getContainingDirectory();
        if (psiDirectory != null) {
          directoryName = psiDirectory.getVirtualFile().getPresentableUrl();
        }
      }
    }

    if (directoryName == null && psiElement instanceof PsiPackage) {
      final PsiDirectory[] directories = ((PsiPackage)psiElement).getDirectories();
      directoryName = directories.length > 0 ? directories[0].getVirtualFile().getPresentableUrl():null;
    }

    Module module = (Module)dataContext.getData(DataConstantsEx.MODULE_CONTEXT);
    if (module != null) {
      model.setModuleName(module.getName());
    }

    if (model.getModuleName() == null || dataContext.getData(DataConstants.EDITOR) == null) {
      model.setDirectoryName(directoryName);
      model.setProjectScope((directoryName == null && module == null) || dataContext.getData(DataConstants.EDITOR) != null);
    }
  }

  public static PsiDirectory getPsiDirectory(final FindModel findModel, Project project) {
    if (findModel.isProjectScope() || findModel.getDirectoryName() == null) {
      return null;
    }

    final PsiManager psiManager = PsiManager.getInstance(project);
    String path = findModel.getDirectoryName().replace(File.separatorChar, '/');
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null || !virtualFile.isDirectory()) {
      if (path.indexOf(JarFileSystem.JAR_SEPARATOR) < 0) {
        path = path + JarFileSystem.JAR_SEPARATOR;
      }
      virtualFile = JarFileSystem.getInstance().findFileByPath(path);
    }
    if (virtualFile != null) {
      return psiManager.findDirectory(virtualFile);
    }
    else {
      return null;
    }
  }

  private static void addVirtualFilesUnderDirectory(VirtualFile directory, List<VirtualFile> vFileList, boolean isRecursive, Pattern fileMaskRegExp) {
    final VirtualFile[] children = directory.getChildren();
    if (children == null) return;

    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (!child.isDirectory() &&
          (fileMaskRegExp == null ||
           fileMaskRegExp.matcher(child.getName()).matches()
          )
      ) {
        vFileList.add(child);
      }
      else if (isRecursive) {
        addVirtualFilesUnderDirectory(child, vFileList, isRecursive, fileMaskRegExp);
      }
    }
  }

  public static UsageInfo[] findUsages(final FindModel findModel, final PsiDirectory psiDirectory, final Project project) {
    class MyAsyncUsageConsumer implements AsyncFindUsagesProcessListener {
      final ArrayList<UsageInfo> usages = new ArrayList<UsageInfo>();

      public void foundUsage(UsageInfo info) {
        usages.add(info);
      }

      public int getCount() {
        return usages.size();
      }

      public void findUsagesCompleted() {
      }
    }

    MyAsyncUsageConsumer consumer = new MyAsyncUsageConsumer();
    findUsages(findModel, psiDirectory, project, consumer);
    return consumer.usages.toArray(new UsageInfo[consumer.usages.size()]);
  }

  private static Pattern createFileMaskRegExp(FindModel findModel) {
    if (findModel.getFileFilter() != null) {
      return Pattern.compile(PatternUtil.convertToRegex(findModel.getFileFilter()), Pattern.CASE_INSENSITIVE);
    }
    else {
      return null;
    }
  }

  public static void findUsages(final FindModel findModel,
                                final PsiDirectory psiDirectory,
                                final Project project,
                                final AsyncFindUsagesProcessListener consumer) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

    final VirtualFile[] virtualFiles = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile[]>() {
      public VirtualFile[] compute() {
        final FileIndex fileIndex = (findModel.getModuleName() == null) ?
                                    (FileIndex)ProjectRootManager.getInstance(project).getFileIndex() :
                                    ModuleRootManager.getInstance(ModuleManager.getInstance(project).findModuleByName(findModel.getModuleName())).getFileIndex();

        if (psiDirectory == null || (findModel.isWithSubdirectories() && fileIndex.isInContent(psiDirectory.getVirtualFile()))) {
          class EnumContentIterator implements ContentIterator {
            List<VirtualFile> myVirtualFiles = new ArrayList<VirtualFile>();
            Pattern fileMaskRegExp = createFileMaskRegExp(findModel);

            public boolean processFile(VirtualFile fileOrDir) {
              if (!fileOrDir.isDirectory() &&
                  (fileMaskRegExp == null ||
                   fileMaskRegExp.matcher(fileOrDir.getName()).matches()
                  )
              ) {
                myVirtualFiles.add(fileOrDir);
              }
              return true;
            }

            public VirtualFile[] getVirtualFiles() {
              return myVirtualFiles.toArray(new VirtualFile[myVirtualFiles.size()]);
            }
          }
          final EnumContentIterator iterator = new EnumContentIterator();


          if (psiDirectory == null) {
            fileIndex.iterateContent(iterator);
          }
          else {
            fileIndex.iterateContentUnderDirectory(psiDirectory.getVirtualFile(), iterator);
          }

          return iterator.getVirtualFiles();
        }
        else {
          List<VirtualFile> vFileList = new ArrayList<VirtualFile>();
          VirtualFile virtualFile = psiDirectory.getVirtualFile();

          addVirtualFilesUnderDirectory(virtualFile,
                                        vFileList,
                                        findModel.isWithSubdirectories(),
                                        createFileMaskRegExp(findModel));
          return vFileList.toArray(new VirtualFile[vFileList.size()]);
        }
      }
    });

    final FileDocumentManager manager = FileDocumentManager.getInstance();
    try {
      for (int i = 0; i < virtualFiles.length; i++) {
        ProgressManager.getInstance().checkCanceled();
        final VirtualFile virtualFile = virtualFiles[i];
        final int index = i;

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (virtualFile.isValid()) {
              if (FileTypeManager.getInstance().getFileTypeByFile(virtualFile).isBinary()) return; // do not decompile .class files
              final Document document = manager.getDocument(virtualFile);
              if (document != null) {
                addToUsages(project, document, consumer, findModel);

                if (progress != null) {
                  progress.setFraction((double)index / virtualFiles.length);
                  String text = "Searching for '" + findModel.getStringToFind() + "' in " + virtualFile.getPresentableUrl() + "...";
                  progress.setText(text);
                  int size = consumer.getCount();
                  progress.setText2((size == 0 ? "No" : Integer.toString(size)) + " occurrence" +
                                    (size != 1 ? "s" : "") + " found so far");
                }
              }
            }
          }
        });
      }
    }
    catch (ProcessCanceledException e) {
    }

    if (progress != null) {
      progress.setText("Search completed");
    }

    consumer.findUsagesCompleted();
  }

  private static void addToUsages(Project project, Document document, AsyncFindUsagesProcessListener consumer, FindModel findModel) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (psiFile != null) {
      CharSequence text = document.getCharsSequence();
      int textLength = document.getTextLength();
      if (text != null) {
        int offset = 0;
        FindManager findManager = FindManager.getInstance(project);
        while (offset < textLength) {
          FindResult result = findManager.findString(text, offset, findModel);
          if (result == null || !result.isStringFound()) break;

          UsageInfo info = new UsageInfo(psiFile, result.getStartOffset(), result.getEndOffset());
          consumer.foundUsage(info);

          final int prevOffset = offset;
          offset = result.getEndOffset();

          if (prevOffset == offset) {
            // for regular expr the size of the match could be zero -> could be infinite loop in finding usages!
            ++offset;
          }
        }
      }
    }
  }

  public static void runProcessWithProgress(final ProgressIndicator progressIndicator,
                                            final Runnable findUsagesRunnable,
                                            final Runnable showResultsRunnable,
                                            Project project) {

    final ProgressIndicator progressIndicator1 = Patches.MAC_HIDE_QUIT_HACK
                                                 ? (ProgressIndicator)progressIndicator
                                                 : new SmoothProgressAdapter(progressIndicator, project);
    UsageViewImplUtil.runProcessWithProgress(progressIndicator1, findUsagesRunnable, showResultsRunnable);
  }

  public static String getTitleForScope(final FindModel findModel) {
    StringBuffer result = new StringBuffer();

    if (findModel.isProjectScope()) {
      result.append("Project");
    }
    else if (findModel.getModuleName() != null) {
      result.append("Module " + findModel.getModuleName());
    }
    else {
      result.append("Directory " + findModel.getDirectoryName());
    }

    if (findModel.getFileFilter() != null) {
      result.append(" Files with Mask " + findModel.getFileFilter());
    }

    return result.toString();
  }

  public static UsageViewPresentation setupViewPresentation(final boolean toOpenInNewTab, final FindModel findModelCopy) {
    final UsageViewPresentation presentation = new UsageViewPresentation();

    presentation.setScopeText(getTitleForScope(findModelCopy));
    presentation.setTabText("Occurrences of '" + findModelCopy.getStringToFind() + "'");
    presentation.setUsagesString("occurrences of '" + findModelCopy.getStringToFind() + "'");
    presentation.setOpenInNewTab(toOpenInNewTab);
    presentation.setCodeUsages(false);

    return presentation;
  }

  public static boolean hasReadOnlyUsages(final Set<Usage> usages) {
    for (Iterator<Usage> iterator = usages.iterator(); iterator.hasNext();) {
      Usage usage = iterator.next();

      if (usage.isReadOnly()) return true;
    }

    return false;
  }

  public static FindUsagesProcessPresentation setupProcessPresentation(final Project project,
                                                                 final boolean showPanelIfOnlyOneUsage,
                                                                 final UsageViewPresentation presentation) {
    FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation();
    processPresentation.setShowNotFoundMessage(true);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);
    processPresentation.setProgressIndicatorFactory(
      new Factory<ProgressIndicator>() {
        public ProgressIndicator create() {
          return new FindProgressIndicator(project, presentation.getScopeText());
        }
      }
    );
    return processPresentation;
  }

  public static class StringUsageTarget implements UsageTarget {
    private String myStringToFind;

    private ItemPresentation myItemPresentation = new ItemPresentation() {
      public String getPresentableText() {
        return "String '" + myStringToFind + "'";
      }

      public String getLocationString() {
        return myStringToFind + "!!";
      }

      public Icon getIcon(boolean open) {
        return null;
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };

    public StringUsageTarget(String _stringToFind) {
      myStringToFind = _stringToFind;
    }

    public void findUsages() {
      throw new UnsupportedOperationException();
    }

    public void findUsagesInEditor(FileEditor editor) {
      throw new UnsupportedOperationException();
    }

    public boolean isValid() {
      return true;
    }

    public boolean isReadOnly() {
      return true;
    }

    public VirtualFile[] getFiles() {
      throw new UnsupportedOperationException();
    }

    public String getName() {
      return myStringToFind;
    }

    public ItemPresentation getPresentation() {
      return myItemPresentation;
    }

    public FileStatus getFileStatus() {
      return null;
    }

    public void navigate(boolean requestFocus) {
      throw new UnsupportedOperationException();
    }

    public boolean canNavigate() {
      return false;
    }

    public boolean canNavigateToSource() {
      return false;
    }
  }

  public static class AsyncFindUsagesProcessListener2ProcessorAdapter implements AsyncFindUsagesProcessListener {
    private final Processor<Usage> processor;
    private int count;

    public AsyncFindUsagesProcessListener2ProcessorAdapter(Processor<Usage> _processor) {
      processor = _processor;
    }

    public void foundUsage(UsageInfo info) {
      ++count;
      processor.process(new UsageInfo2UsageAdapter(info));
    }

    public void findUsagesCompleted() {
    }

    public int getCount() {
      return count;
    }
  }
}