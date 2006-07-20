package com.intellij.find.impl;

import com.intellij.CommonBundle;
import com.intellij.Patches;
import com.intellij.find.*;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.usageView.AsyncFindUsagesProcessListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageViewImplUtil;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

public class FindInProjectUtil {
  private static final int USAGES_LIMIT = 1000;
  private static final int FILES_SIZE_LIMIT = 70 * 1024 * 1024; // megabytes.
  private static final int SINGLE_FILE_SIZE_LIMIT = 5 * 1024 * 1024; // megabytes.

  private static final int SKIP = 0;
  private static final int PROCESS = 1;
  private static final int SKIP_ALL = 2;
  private static final int PROCESS_ALL = 3;

  private FindInProjectUtil() {}

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
      directoryName = directories.length == 1 ? directories[0].getVirtualFile().getPresentableUrl():null;
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

  @Nullable
  public static PsiDirectory getPsiDirectory(final FindModel findModel, Project project) {
    if (findModel.isProjectScope() || findModel.getDirectoryName() == null) {
      return null;
    }

    final PsiManager psiManager = PsiManager.getInstance(project);
    String path = findModel.getDirectoryName().replace(File.separatorChar, '/');
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null || !virtualFile.isDirectory()) {
      if (path.indexOf(JarFileSystem.JAR_SEPARATOR) < 0) {
        path += JarFileSystem.JAR_SEPARATOR;
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

  private static void addFilesUnderDirectory(PsiDirectory directory, List<PsiFile> fileList, boolean isRecursive, Pattern fileMaskRegExp) {
    final PsiElement[] children = directory.getChildren();

    for (PsiElement child : children) {
      if (child instanceof PsiFile &&
          (fileMaskRegExp == null ||
           fileMaskRegExp.matcher(((PsiFile)child).getName()).matches()
          )
        ) {
        fileList.add((PsiFile)child);
      }
      else if (isRecursive && child instanceof PsiDirectory) {
        addFilesUnderDirectory((PsiDirectory)child, fileList, isRecursive, fileMaskRegExp);
      }
    }
  }

  @NotNull
  public static List<UsageInfo> findUsages(final FindModel findModel, final PsiDirectory psiDirectory, final Project project) {
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
    return consumer.usages;
  }

  @Nullable
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

    final Collection<PsiFile> psiFiles = getFilesToSearchIn(findModel, project, psiDirectory);
    final FileDocumentManager manager = FileDocumentManager.getInstance();
    boolean skipAllLarge = false;
    boolean processAllLarge = false;
    try {
      int i =0;
      long totalFilesSize = 0;
      final int[] count = new int[]{0};
      boolean warningShown = false;

      for (final PsiFile psiFile : psiFiles) {
        ProgressManager.getInstance().checkCanceled();
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        final int index = i++;
        if (virtualFile == null) continue;

        long fileLength = getFileLength(virtualFile);
        if (fileLength == -1) continue; // Binary or invalid

        if (fileLength > SINGLE_FILE_SIZE_LIMIT) {
          if (skipAllLarge) continue;
          if (!processAllLarge) {
            int retCode = showMessage(project, FindBundle.message("find.skip.large.file.prompt",
                                                                  ApplicationNamesInfo.getInstance().getProductName(),
                                                                  getPresentablePath(virtualFile), presentableSize(fileLength)),
                                      FindBundle.message("find.skip.large.file.title"));
            if (retCode == SKIP_ALL) {
              skipAllLarge = true;
              continue;
            }
            else if (retCode == SKIP) {
              continue;
            }
            else if (retCode == PROCESS_ALL) {
              processAllLarge = true;
            }
          }
        }

        int countBefore = count[0];

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (virtualFile.isValid()) {
              // Check once more if valid and text since we're in new read action and things might have been changed.
              if (FileTypeManager.getInstance().getFileTypeByFile(virtualFile).isBinary()) return; // do not decompile .class files
              final Document document = manager.getDocument(virtualFile);
              if (document != null) {
                addToUsages(project, document, consumer, findModel, psiFile, count);

                if (progress != null) {
                  progress.setFraction((double)index / psiFiles.size());
                  String text = FindBundle.message("find.searching.for.string.in.file.progress",
                                                   findModel.getStringToFind(), virtualFile.getPresentableUrl());
                  progress.setText(text);
                  int size = consumer.getCount();
                  progress.setText2(FindBundle.message("find.searching.for.string.in.file.occurrences.progress", size));
                }
              }
            }
          }
        });

        if (countBefore < count[0]) {
          totalFilesSize += fileLength;
          if (totalFilesSize > FILES_SIZE_LIMIT && !warningShown) {
            showTooManyUsagesWaring(project, FindBundle.message("find.excessive.total.size.prompt", presentableSize(totalFilesSize),
                                                                ApplicationNamesInfo.getInstance().getProductName()));
            warningShown = true;
          }
        }

        if (count[0] > USAGES_LIMIT && !warningShown) {
          showTooManyUsagesWaring(project, FindBundle.message("find.excessive.usage.count.prompt", count[0]));
          warningShown = true;
        }
      }
    }
    catch (ProcessCanceledException e) {
      // fine
    }

    if (progress != null) {
      progress.setText(FindBundle.message("find.progress.search.completed"));
    }

    consumer.findUsagesCompleted();
  }

  private static String getPresentablePath(final VirtualFile virtualFile) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return virtualFile.getPresentableUrl();
      }
    });
  }

  private static String presentableSize(long bytes) {
    long megabytes = bytes / (1024 * 1024);
    return FindBundle.message("find.file.size.megabytes", Long.toString(megabytes));
  }

  private static long getFileLength(final VirtualFile virtualFile) {
    final long[] length = new long[] {-1L};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (!virtualFile.isValid()) return;
        if (FileTypeManager.getInstance().getFileTypeByFile(virtualFile).isBinary()) return;
        length[0] = virtualFile.getLength();
      }
    });
    return length[0];
  }

  private static void showTooManyUsagesWaring(final Project project, final String message) {
    int retCode = invokeAndWait(new Computable<Integer>() {
      public Integer compute() {
        return Messages.showYesNoDialog(project, message, FindBundle.message("find.excessive.usages.title"), Messages.getWarningIcon());
      }
    });

    if (retCode != DialogWrapper.OK_EXIT_CODE) {
      throw new ProcessCanceledException();
    }
  }

  private static int invokeAndWait(final Computable<Integer> f) {
    final int[] answer = new int[1];
    try {
      SwingUtilities.invokeAndWait(new Runnable() {
        public void run() {
          answer[0] = f.compute();
        }
      });
    }
    catch (Exception e) {
      answer[0] = 0;
    }

    return answer[0];
  }

  private static int showMessage(final Project project, final String message, final String title) {
    return invokeAndWait(new Computable<Integer>() {
      public Integer compute() {
        return Messages.showDialog(project, message, title,
                                          new String[] {
                                            CommonBundle.message("button.yes"),
                                            CommonBundle.message("button.no"),
                                            CommonBundle.message("button.yes.for.all"),
                                            CommonBundle.message("button.no.for.all")
                                          }, 0, Messages.getWarningIcon());
      }
    });
  }

  private static Collection<PsiFile> getFilesToSearchIn(final FindModel findModel, final Project project, final PsiDirectory psiDirectory) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiFile>>() {
      public Collection<PsiFile> compute() {
        return getFilesToSearchInReadAction(findModel, project, psiDirectory);
      }
    });
  }
  private static Collection<PsiFile> getFilesToSearchInReadAction(final FindModel findModel, final Project project, final PsiDirectory psiDirectory) {
    Module module = findModel.getModuleName() == null ? null : ModuleManager.getInstance(project).findModuleByName(findModel.getModuleName());
    final FileIndex fileIndex = module == null ?
                                ProjectRootManager.getInstance(project).getFileIndex() :
                                ModuleRootManager.getInstance(module).getFileIndex();

    final Pattern fileMaskRegExp = createFileMaskRegExp(findModel);
    if (psiDirectory == null || (findModel.isWithSubdirectories() && fileIndex.isInContent(psiDirectory.getVirtualFile()))) {
      if (canOptimizeForFastWordSearch(findModel)) {
        // optimization
        CacheManager cacheManager = ((PsiManagerImpl)PsiManager.getInstance(project)).getCacheManager();

        GlobalSearchScope scope = psiDirectory == null ?
                                  module == null ?
                                  GlobalSearchScope.projectScope(project) :
                                  moduleContentScope(module) :
                                                             GlobalSearchScope.directoryScope(psiDirectory, true);

        List<String> words = StringUtil.getWordsIn(findModel.getStringToFind());
        // if no words specified in search box, fallback to brute force search
        if (words.size() != 0) {
          // search for longer strings first
          Collections.sort(words, new Comparator<String>() {
            public int compare(final String o1, final String o2) {
              return o2.length() - o1.length();
            }
          });
          Set<PsiFile> resultFiles = new THashSet<PsiFile>();
          for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            PsiFile[] files = cacheManager.getFilesWithWord(word, UsageSearchContext.ANY, scope, findModel.isCaseSensitive());

            final List<PsiFile> psiFiles = Arrays.asList(files);
            if (i == 0) {
              resultFiles.addAll(psiFiles);
            }
            else {
              resultFiles.retainAll(psiFiles);
            }
            filterMaskedFiles(resultFiles, fileMaskRegExp);
            if (resultFiles.size() == 0) break;
          }
          return resultFiles;
        }
      }
      class EnumContentIterator implements ContentIterator {
        List<VirtualFile> myVirtualFiles = new ArrayList<VirtualFile>();

        public boolean processFile(VirtualFile fileOrDir) {
          if (!fileOrDir.isDirectory() &&
              (fileMaskRegExp == null || fileMaskRegExp.matcher(fileOrDir.getName()).matches()) ) {
            myVirtualFiles.add(fileOrDir);
          }
          return true;
        }

        public Collection<PsiFile> getFiles() {
          final ArrayList<PsiFile> psiFiles = new ArrayList<PsiFile>(myVirtualFiles.size());
          final PsiManager manager = PsiManager.getInstance(project);
          for (VirtualFile virtualFile : myVirtualFiles) {
            final PsiFile psiFile = manager.findFile(virtualFile);
            if (psiFile != null) {
              psiFiles.add(psiFile);
            }
          }
          return psiFiles;
        }
      }
      final EnumContentIterator iterator = new EnumContentIterator();

      if (psiDirectory == null) {
        fileIndex.iterateContent(iterator);
      }
      else {
        fileIndex.iterateContentUnderDirectory(psiDirectory.getVirtualFile(), iterator);
      }
      return iterator.getFiles();
    }
    else {
      ArrayList<PsiFile> fileList = new ArrayList<PsiFile>();

      addFilesUnderDirectory(psiDirectory,
                             fileList,
                             findModel.isWithSubdirectories(),
                             createFileMaskRegExp(findModel));
      return fileList;
    }
  }

  private static GlobalSearchScope moduleContentScope(final Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    GlobalSearchScope result = null;
    PsiManager psiManager = PsiManager.getInstance(module.getProject());
    for (VirtualFile root : contentRoots) {
      PsiDirectory directory = psiManager.findDirectory(root);
      GlobalSearchScope moduleContent = GlobalSearchScope.directoryScope(directory, true);
      if (result == null) {
        result = moduleContent;
      }
      else {
        result = result.uniteWith(moduleContent);
      }
    }
    return result;
  }

  private static void filterMaskedFiles(final Set<PsiFile> resultFiles, final Pattern fileMaskRegExp) {
    if (fileMaskRegExp != null) {
      for (Iterator<PsiFile> iterator = resultFiles.iterator(); iterator.hasNext();) {
        PsiFile file = iterator.next();
        if (!fileMaskRegExp.matcher(file.getName()).matches()) {
          iterator.remove();
        }
      }
    }
  }

  private static boolean canOptimizeForFastWordSearch(final FindModel findModel) {
    // $ is used to separate words when indexing plain-text files but not when indexing
    // Java identifiers, so we can't consistently break a string containing $ characters
    // into words
    return findModel.isWholeWordsOnly() && !findModel.isRegularExpressions() &&
      findModel.getStringToFind().indexOf('$') < 0;
  }

  private static void addToUsages(Project project,
                                  Document document,
                                  AsyncFindUsagesProcessListener consumer,
                                  FindModel findModel,
                                  final PsiFile psiFile,
                                  final int[] count) {

    if (psiFile == null) {
      return;
    }
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
        count[0]++;

        final int prevOffset = offset;
        offset = result.getEndOffset();

        if (prevOffset == offset) {
          // for regular expr the size of the match could be zero -> could be infinite loop in finding usages!
          ++offset;
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
    String result;

    if (findModel.isProjectScope()) {
      result = FindBundle.message("find.scope.project.title");
    }
    else if (findModel.getModuleName() != null) {
      result = FindBundle.message("find.scope.module.title", findModel.getModuleName());
    }
    else {
      result = FindBundle.message("find.scope.directory.title", findModel.getDirectoryName());
    }

    if (findModel.getFileFilter() != null) {
      result = FindBundle.message("find.scope.files.with.mask", result, findModel.getFileFilter());
    }

    return result;
  }

  public static UsageViewPresentation setupViewPresentation(final boolean toOpenInNewTab, final FindModel findModelCopy) {
    final UsageViewPresentation presentation = new UsageViewPresentation();

    final String scope = getTitleForScope(findModelCopy);
    final String stringToFind = findModelCopy.getStringToFind();
    presentation.setScopeText(scope);
    presentation.setTabText(FindBundle.message("find.usage.view.tab.text", stringToFind));
    presentation.setToolwindowTitle(FindBundle.message("find.usage.view.toolwindow.title", stringToFind, scope));
    presentation.setUsagesString(FindBundle.message("find.usage.view.usages.text", stringToFind));
    presentation.setOpenInNewTab(toOpenInNewTab);
    presentation.setCodeUsages(false);

    return presentation;
  }

  public static boolean hasReadOnlyUsages(final Collection<Usage> usages) {
    for (Usage usage : usages) {
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
        return FindBundle.message("find.usage.target.string.text", myStringToFind);
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

    public void findUsages() {}
    public void findUsagesInEditor(FileEditor editor) {}

    public boolean isValid() {
      return true;
    }

    public boolean isReadOnly() {
      return true;
    }

    @Nullable
    public VirtualFile[] getFiles() {
      return null;
    }

    public void update() {
    }

    public String getName() {
      return myStringToFind;
    }

    public ItemPresentation getPresentation() {
      return myItemPresentation;
    }

    public FileStatus getFileStatus() {
      return FileStatus.NOT_CHANGED;
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