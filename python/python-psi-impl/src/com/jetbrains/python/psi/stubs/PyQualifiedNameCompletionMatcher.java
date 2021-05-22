package com.jetbrains.python.psi.stubs;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IdFilter;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PyQualifiedNameCompletionMatcher {
  private static final Logger LOG = Logger.getInstance(PyQualifiedNameCompletionMatcher.class);

  private PyQualifiedNameCompletionMatcher() {
  }

  public static void processMatchingExportedNames(@NotNull QualifiedName qualifiedNamePattern,
                                                  @Nullable QualifiedName originallyTypedAlias,
                                                  @NotNull PsiFile currentFile,
                                                  @NotNull GlobalSearchScope scope,
                                                  @NotNull Processor<? super ExportedName> processor) {
    if (qualifiedNamePattern.getComponentCount() < 2) return;
    QualifiedNameMatcher matcher = new QualifiedNameMatcher(qualifiedNamePattern);
    StubIndex stubIndex = StubIndex.getInstance();
    Project project = Objects.requireNonNull(scope.getProject());

    GlobalSearchScope moduleMatchingScope = new ModuleQualifiedNameMatchingScope(scope, matcher, project);
    Set<QualifiedName> alreadySuggestedAttributes = new HashSet<>();
    IndexLookupStats stats = new IndexLookupStats();
    try {
      IdFilter idFilter = IdFilter.getProjectIdFilter(project, true);
      stubIndex.processAllKeys(PyExportedModuleAttributeIndex.KEY, attributeName -> {
        ProgressManager.checkCanceled();
        stats.scannedKeys++;
        if (!matcher.attributeMatches(attributeName)) return true;
        stats.matchingKeys++;
        return stubIndex.processElements(PyExportedModuleAttributeIndex.KEY,
                                         attributeName, project, moduleMatchingScope, idFilter, PyElement.class, element -> {
            ProgressManager.checkCanceled();
            VirtualFile vFile = element.getContainingFile().getVirtualFile();
            QualifiedName moduleQualifiedName = ModuleQualifiedNameMatchingScope.restoreModuleQualifiedName(vFile, project);
            assert moduleQualifiedName != null : vFile;
            QualifiedName canonicalImportPath = findCanonicalImportPath(element, moduleQualifiedName, currentFile);
            QualifiedName importPath;
            if (canonicalImportPath != null && matcher.qualifierMatches(canonicalImportPath)) {
              importPath = canonicalImportPath;
            }
            else {
              importPath = moduleQualifiedName;
            }
            QualifiedName attributeQualifiedName = importPath.append(attributeName);
            if (alreadySuggestedAttributes.add(attributeQualifiedName)) {
              if (!processor.process(new ExportedName(attributeQualifiedName, originallyTypedAlias, element))) {
                return false;
              }
            }
            return true;
          });
      }, moduleMatchingScope, idFilter);
    }
    catch (ProcessCanceledException e) {
      stats.cancelled = true;
      throw e;
    }
    finally {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Index lookup stats for '" + qualifiedNamePattern + "':\n" +
                  "Scanned keys: " + stats.scannedKeys + "\n" +
                  "Matched keys: " + stats.matchingKeys + "\n" +
                  (stats.cancelled ? "Cancelled in " : "Completed in ") + stats.getDuration() + " ms");
      }
    }
  }

  private static class IndexLookupStats {
    long startNanoTime = System.nanoTime();
    int scannedKeys;
    int matchingKeys;
    boolean cancelled;

    long getDuration() {
      return TimeoutUtil.getDurationMillis(startNanoTime);
    }
  }

  @Nullable
  private static QualifiedName findCanonicalImportPath(@NotNull PyElement element,
                                                       @NotNull QualifiedName moduleQualifiedName,
                                                       @NotNull PsiFile currentFile) {
    QualifiedName canonicalImportPath;
    if (moduleQualifiedName.getComponentCount() != 1) {
      canonicalImportPath = QualifiedNameFinder.findCanonicalImportPath(element, currentFile);
    }
    else {
      canonicalImportPath = QualifiedNameFinder.canonizeQualifiedName(element, moduleQualifiedName, currentFile);
    }
    return canonicalImportPath;
  }

  public static final class ExportedName {
    private final QualifiedName myQualifiedName;
    private final QualifiedName myOriginallyTypedQName;
    private final PyElement myElement;

    private ExportedName(@NotNull QualifiedName qualifiedName, @Nullable QualifiedName originallyTypedQName, @NotNull PyElement element) {
      myQualifiedName = qualifiedName;
      myOriginallyTypedQName = originallyTypedQName;
      myElement = element;
    }

    @NotNull
    public QualifiedName getQualifiedName() {
      return myQualifiedName;
    }

    @Nullable
    public QualifiedName getOriginallyTypedQName() {
      return myOriginallyTypedQName;
    }

    @NotNull
    public PyElement getElement() {
      return myElement;
    }

    @NotNull
    public QualifiedName getQualifiedNameWithUserTypedAlias() {
      return myOriginallyTypedQName != null
             ? myOriginallyTypedQName.removeLastComponent().append(myQualifiedName.getLastComponent())
             : myQualifiedName;
    }
  }

  public static final class QualifiedNameMatcher extends PrefixMatcher {
    private final PrefixMatcher myQualifierFirstComponentMatcher;
    private final PrefixMatcher myQualifierRemainderMatcher;
    private final PrefixMatcher myLastComponentMatcher;

    public QualifiedNameMatcher(@NotNull QualifiedName qualifiedName) {
      super(qualifiedName.toString());
      if (qualifiedName.getComponentCount() < 2) {
        throw new IllegalArgumentException("Qualified name should have at least two components, but was '" + qualifiedName + "'");
      }
      myLastComponentMatcher = new CamelHumpMatcher(qualifiedName.getLastComponent(), false);
      QualifiedName qualifier = qualifiedName.removeLastComponent();
      myQualifierFirstComponentMatcher = new CamelHumpMatcher(qualifier.getFirstComponent(), false);
      myQualifierRemainderMatcher = new CamelHumpMatcher(qualifier.removeHead(1).toString(), false);
    }

    @Override
    public boolean prefixMatches(@NotNull String name) {
      QualifiedName qualifiedName = QualifiedName.fromDottedString(name);
      if (qualifiedName.getComponentCount() == 0) return false;
      if (!attributeMatches(qualifiedName.getLastComponent())) return false;
      if (!qualifierMatches(qualifiedName.removeLastComponent())) return false;
      return true;
    }

    private boolean attributeMatches(@Nullable String attribute) {
      return myLastComponentMatcher.isStartMatch(attribute);
    }

    private boolean qualifierMatches(@NotNull QualifiedName qualifier) {
      String firstComponent = Objects.requireNonNullElse(qualifier.getFirstComponent(), "");
      if (!myQualifierFirstComponentMatcher.prefixMatches(firstComponent)) return false;
      String remainder = qualifier.getComponentCount() == 0 ? "" : qualifier.removeHead(1).toString();
      if (!myQualifierRemainderMatcher.prefixMatches(remainder)) return false;
      return true;
    }

    @Override
    public @NotNull PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
      return new QualifiedNameMatcher(QualifiedName.fromDottedString(prefix));
    }
  }

  private static class ModuleQualifiedNameMatchingScope extends DelegatingGlobalSearchScope {
    private final QualifiedNameMatcher myQualifiedNameMatcher;
    private final Project myProject;

    ModuleQualifiedNameMatchingScope(@NotNull GlobalSearchScope baseScope,
                                     @NotNull QualifiedNameMatcher qualifiedNameMatcher,
                                     @NotNull Project project) {
      super(baseScope);
      myQualifiedNameMatcher = qualifiedNameMatcher;
      myProject = project;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      if (!super.contains(file)) return false;
      QualifiedName qualifiedName = restoreModuleQualifiedName(file, myProject);
      if (qualifiedName == null) return false;
      return myQualifiedNameMatcher.qualifierMatches(qualifiedName);
    }

    @Nullable
    private static QualifiedName restoreModuleQualifiedName(@NotNull VirtualFile vFile, @NotNull Project project) {
      ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
      String fileName = vFile.getName();
      VirtualFile nameAnchor = fileName.equals(PyNames.INIT_DOT_PY) || fileName.equals(PyNames.INIT_DOT_PYI) ? vFile.getParent() : vFile;
      VirtualFile closestRoot = findClosestRoot(nameAnchor, projectFileIndex);
      if (closestRoot == null) return null;
      String relativePath = VfsUtilCore.getRelativePath(nameAnchor, closestRoot);
      // A relative path can be empty in case of __init__.py directly inside a root.
      if (relativePath == null || relativePath.isEmpty()) return null;
      return convertPathToImportableQualifiedName(relativePath);
    }

    @Nullable
    private static QualifiedName convertPathToImportableQualifiedName(@NotNull String relativePath) {
      List<String> parts = StringUtil.split(relativePath, VfsUtilCore.VFS_SEPARATOR);
      String fileName = parts.get(parts.size() - 1);
      parts.set(parts.size() - 1, StringUtil.substringBeforeLast(fileName, "."));
      if (ContainerUtil.exists(parts, p -> !PyNames.isIdentifier(p))) return null;
      return QualifiedName.fromComponents(parts);
    }

    @Nullable
    private static VirtualFile findClosestRoot(@NotNull VirtualFile vFile, @NotNull ProjectFileIndex projectFileIndex) {
      VirtualFile sourceRoot = projectFileIndex.getSourceRootForFile(vFile);
      if (sourceRoot != null) return sourceRoot;
      VirtualFile contentRoot = projectFileIndex.getContentRootForFile(vFile);
      if (contentRoot != null) return contentRoot;
      VirtualFile libraryRoot = projectFileIndex.getClassRootForFile(vFile);
      if (libraryRoot != null) return libraryRoot;
      return null;
    }
  }
}
