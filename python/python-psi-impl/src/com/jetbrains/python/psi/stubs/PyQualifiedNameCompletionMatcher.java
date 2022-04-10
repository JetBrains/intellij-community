package com.jetbrains.python.psi.stubs;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PyQualifiedNameCompletionMatcher {
  private static final Logger LOG = Logger.getInstance(PyQualifiedNameCompletionMatcher.class);

  private PyQualifiedNameCompletionMatcher() {
  }

  public static void processMatchingExportedNames(@NotNull QualifiedName qualifiedNamePattern,
                                                  @NotNull PsiFile currentFile,
                                                  @NotNull GlobalSearchScope scope,
                                                  @NotNull Processor<? super ExportedName> processor) {
    if (qualifiedNamePattern.getComponentCount() < 2) return;
    QualifiedNameMatcher matcher = new QualifiedNameMatcher(qualifiedNamePattern);
    StubIndex stubIndex = StubIndex.getInstance();
    Project project = Objects.requireNonNull(scope.getProject());
    PsiManager psiManager = PsiManager.getInstance(project);

    GlobalSearchScope moduleMatchingScope = scope.intersectWith(new ModuleQualifiedNameMatchingScope(matcher, project));
    Set<QualifiedName> alreadySuggestedAttributes = new HashSet<>();
    IndexLookupStats stats = new IndexLookupStats();
    try {
      List<String> matchingAttributeNames = new ArrayList<>();
      stubIndex.processAllKeys(PyExportedModuleAttributeIndex.KEY, attributeName -> {
        ProgressManager.checkCanceled();
        stats.scannedKeys++;
        if (!matcher.attributeMatches(attributeName)) return true;
        stats.matchingKeys++;
        matchingAttributeNames.add(attributeName);
        return true;
      }, moduleMatchingScope);

      for (String attributeName : matchingAttributeNames) {
        stubIndex.processElements(PyExportedModuleAttributeIndex.KEY,
                                  attributeName, project, moduleMatchingScope, null, PyElement.class, element -> {
            ProgressManager.checkCanceled();
            VirtualFile vFile = element.getContainingFile().getVirtualFile();
            QualifiedName moduleQualifiedName = findQualifiedNameInClosestRoot(vFile, project);
            assert moduleQualifiedName != null : vFile;
            QualifiedName canonicalImportPath = findCanonicalImportPath(element, moduleQualifiedName, currentFile);
            QualifiedName importPath;
            if (canonicalImportPath != null && matcher.qualifierMatches(canonicalImportPath)) {
              importPath = canonicalImportPath;
            }
            else {
              importPath = moduleQualifiedName;
            }
            if (ContainerUtil.exists(importPath.getComponents(), c -> c.startsWith("_")) && !psiManager.isInProject(element)) {
              return true;
            }
            QualifiedName attributeQualifiedName = importPath.append(attributeName);
            if (alreadySuggestedAttributes.add(attributeQualifiedName)) {
              if (!processor.process(new ExportedName(attributeQualifiedName, element))) {
                return false;
              }
            }
            return true;
          });
      }
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
    private final PyElement myElement;

    private ExportedName(@NotNull QualifiedName qualifiedName, @NotNull PyElement element) {
      myQualifiedName = qualifiedName;
      myElement = element;
    }

    @NotNull
    public QualifiedName getQualifiedName() {
      return myQualifiedName;
    }

    @NotNull
    public PyElement getElement() {
      return myElement;
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
      if (!myQualifierFirstComponentMatcher.isStartMatch(firstComponent)) return false;
      String remainder = qualifier.getComponentCount() == 0 ? "" : qualifier.removeHead(1).toString();
      if (!myQualifierRemainderMatcher.prefixMatches(remainder)) return false;
      return true;
    }

    @Override
    public @NotNull PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
      return new QualifiedNameMatcher(QualifiedName.fromDottedString(prefix));
    }
  }

  private static class ModuleQualifiedNameMatchingScope extends QualifiedNameFinder.QualifiedNameBasedScope {
    private final QualifiedNameMatcher myQualifiedNameMatcher;

    ModuleQualifiedNameMatchingScope(@NotNull QualifiedNameMatcher qualifiedNameMatcher, @NotNull Project project) {
      super(project);
      myQualifiedNameMatcher = qualifiedNameMatcher;
    }

    @Override
    protected boolean containsQualifiedNameInRoot(@NotNull VirtualFile root, @NotNull QualifiedName qName) {
      return ContainerUtil.all(qName.getComponents(), PyNames::isIdentifier) && myQualifiedNameMatcher.qualifierMatches(qName);
    }
  }

  @Nullable
  private static QualifiedName findQualifiedNameInClosestRoot(@NotNull VirtualFile file, @NotNull Project project) {
    // TODO Come up with a better API for exposing these internals
    Ref<QualifiedName> result = Ref.create();
    //noinspection ResultOfMethodCallIgnored
    new QualifiedNameFinder.QualifiedNameBasedScope(project) {
      @Override
      protected boolean containsQualifiedNameInRoot(@NotNull VirtualFile root, @NotNull QualifiedName qName) {
        result.set(qName);
        return true;
      }
    }.contains(file);
    return result.get();
  }
}
