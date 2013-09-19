package org.jetbrains.idea.maven.project;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class MavenTestConsoleFilter implements Filter {

  private static final Pattern REPORT_DIR_PATTERN = Pattern.compile("\\s*(?:\\[INFO\\] +Surefire report directory:|\\[ERROR\\] Please refer to) +(.+?)(?: for the individual test results.)?\\s*");
//  private static final Pattern TEST_PATTERN = Pattern.compile("\\s*(Failed tests:)?\\s*(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)\\(((?:\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)*\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)\\)\\s*");

  private String myReportFolder;

  private boolean myFailedTestsList;

  private final Project myProject;

  public MavenTestConsoleFilter(Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    Matcher matcherReportDir = REPORT_DIR_PATTERN.matcher(line);
    if (matcherReportDir.matches()) {
      final String path = matcherReportDir.group(1);

      myReportFolder = path;
      return new Result(entireLength - line.length() + matcherReportDir.start(1), entireLength - line.length() + matcherReportDir.end(1),
                            new HyperlinkInfo() {
                              @Override
                              public void navigate(Project project) {
                                File f = new File(path);
                                if (f.isDirectory()) {
                                  ShowFilePathAction.openDirectory(f);
                                }
                              }
                            });
    }

    //if (myReportFolder != null) {
    //  Matcher testMatcher = TEST_PATTERN.matcher(line);
    //  if (testMatcher.matches()) {
    //    if (testMatcher.group(1) != null) {
    //      myFailedTestsList = true;
    //    }
    //
    //    if (myFailedTestsList) {
    //      String className = testMatcher.group(3);
    //      String methodName = testMatcher.group(2);
    //
    //      PsiMethod method = null;
    //
    //      PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(className, GlobalSearchScope.allScope(myProject));
    //      for (PsiClass aClass : classes) {
    //        for (PsiMethod m : aClass.findMethodsByName(methodName, true)) {
    //          if (JUnitUtil.isTestMethod(new MethodLocation(myProject, m, new PsiLocation<PsiClass>(myProject, aClass)))) {
    //            method = m;
    //            break;
    //          }
    //        }
    //      }
    //
    //      if (method != null) {
    //        List<ResultItem> items = new ArrayList<ResultItem>(2);
    //
    //        final WeakReference<PsiMethod> methodRef = new WeakReference<PsiMethod>(method);
    //
    //        items.add(new ResultItem(entireLength - line.length() + testMatcher.start(2), entireLength - line.length() + testMatcher.end(2),
    //                                                                  new HyperlinkInfo() {
    //                                                                    @Override
    //                                                                    public void navigate(Project project) {
    //                                                                      PsiMethod method = methodRef.get();
    //                                                                      if (method != null && method.isValid()) {
    //                                                                        method.navigate(true);
    //                                                                      }
    //                                                                    }
    //                                                                  }));
    //
    //        final File report = new File(myReportFolder, className + ".txt");
    //
    //        if (report.exists()) {
    //          items.add(new ResultItem(entireLength - line.length() + testMatcher.start(3), entireLength - line.length() + testMatcher.end(3),
    //                                   new IOFileHyperlinkInfo(report)));
    //        }
    //
    //        return new Result(items);
    //
    //      }
    //    }
    //  }
    //  else {
    //    myFailedTestsList = false;
    //  }
    //}

    return null;
  }

  //private class IOFileHyperlinkInfo implements FileHyperlinkInfo {
  //  private final File myReport;
  //
  //  private OpenFileDescriptor myDescriptor;
  //  private boolean myInitialized;
  //
  //  public IOFileHyperlinkInfo(File report) {
  //    myReport = report;
  //  }
  //
  //  @Nullable
  //  @Override
  //  public OpenFileDescriptor getDescriptor() {
  //    if (!myInitialized) {
  //      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myReport);
  //      if (virtualFile != null) {
  //        myDescriptor = new OpenFileDescriptor(myProject, virtualFile);
  //      }
  //
  //      myInitialized = true;
  //    }
  //    return myDescriptor;
  //  }
  //
  //  @Override
  //  public void navigate(Project project) {
  //    final OpenFileDescriptor fileDesc = getDescriptor();
  //
  //    if (fileDesc != null && fileDesc.getFile().isValid()) {
  //      FileEditorManager.getInstance(project).openTextEditor(fileDesc, true);
  //    }
  //  }
  //}
}
