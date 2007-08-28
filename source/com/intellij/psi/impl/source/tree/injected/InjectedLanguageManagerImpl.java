package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.injected.DocumentWindow;
import com.intellij.openapi.editor.impl.injected.VirtualFileWindow;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author cdr
 */
public class InjectedLanguageManagerImpl extends InjectedLanguageManager {
  private final WeakList<VirtualFileWindow> cachedFiles = new WeakList<VirtualFileWindow>();
  private final ProjectManagerAdapter myProjectListener;

  public static InjectedLanguageManagerImpl getInstance() {
    return (InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance();
  }

  public InjectedLanguageManagerImpl() {
    myProjectListener = new ProjectManagerAdapter() {
      public void projectClosing(final Project project) {
        VirtualFileWindow[] windows;
        synchronized (cachedFiles) {
          windows = new VirtualFileWindow[cachedFiles.size()];
          Iterator<VirtualFileWindow> iterator = cachedFiles.iterator();
          int i =0;
          while (iterator.hasNext()) {
            windows[i++] = iterator.next();
          }
        }
        for (VirtualFileWindow file : windows) {
          if (file == null) continue;
          DocumentWindow documentWindow = file.getDocumentWindow();
          PsiFile injected = ((PsiManagerEx)PsiManager.getInstance(project)).getFileManager().getCachedPsiFile(file);
          if (injected != null) {
            InjectedLanguageUtil.clearCaches(injected, documentWindow);
          }
        }
      }
    };

    registerMultiHostInjector(PsiLanguageInjectionHost.class, null, new MultiPlaceInjector() {
      public void getLanguagesToInject(@NotNull PsiElement context, @NotNull final MultiPlaceRegistrar injectionPlacesRegistrar) {
        final PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)context;
        PsiManagerEx psiManager = (PsiManagerEx)context.getManager();
        InjectedLanguagePlaces placesRegistrar = new InjectedLanguagePlaces() {
          public void addPlace(@NotNull Language language, @NotNull TextRange rangeInsideHost, @NonNls @Nullable String prefix, @NonNls @Nullable String suffix) {
            injectionPlacesRegistrar
              .startInjecting(language)
              .addPlace(prefix, suffix, host, rangeInsideHost)
              .doneInjecting();
          }
        };
        for (LanguageInjector injector : psiManager.getLanguageInjectors()) {
          injector.getLanguagesToInject(host, placesRegistrar);
        }
        for (LanguageInjector injector : Extensions.getExtensions(LanguageInjector.EXTENSION_POINT_NAME)) {
          injector.getLanguagesToInject(host, placesRegistrar);
        }
      }
    });
  }

  VirtualFileWindow createVirtualFile(final Language language,
                                      final VirtualFile hostVirtualFile,
                                      final DocumentWindow documentWindow,
                                      StringBuilder text,
                                      Project project) {
    clearInvalidFiles(project);
    VirtualFileWindow virtualFile = new VirtualFileWindow(hostVirtualFile, documentWindow, language, text.toString());
    synchronized (cachedFiles) {
      cachedFiles.add(virtualFile);
    }

    return virtualFile;
  }

  private void clearInvalidFiles(Project project) {
    synchronized (cachedFiles) {
      Iterator<VirtualFileWindow> iterator = cachedFiles.iterator();
      while (iterator.hasNext()) {
        VirtualFileWindow cachedFile = iterator.next();
        PsiFile cached = ((PsiManagerEx)PsiManager.getInstance(project)).getFileManager().getCachedPsiFile(cachedFile);
        if (cached == null || cached.getContext() == null || !cached.getContext().isValid()) {
          iterator.remove();
          continue;
        }

        Document cachedDocument = PsiDocumentManager.getInstance(project).getCachedDocument(cached);
        if (!(cachedDocument instanceof DocumentWindow)) {
          iterator.remove();
        }
      }
    }
  }

  public PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    return (PsiLanguageInjectionHost)file.getContext();
  }

  public TextRange injectedToHost(@NotNull PsiElement element, @NotNull TextRange textRange) {
    PsiFile file = element.getContainingFile();
    if (file == null) return textRange;
    Document document = PsiDocumentManager.getInstance(element.getProject()).getCachedDocument(file);
    if (!(document instanceof DocumentWindow)) return textRange;
    DocumentWindow documentWindow = (DocumentWindow)document;
    return documentWindow.injectedToHost(textRange);
  }

  private final ClassMapCachingNulls<List<Pair<ElementFilter, MultiPlaceInjector>>> injectors = new ClassMapCachingNulls<List<Pair<ElementFilter, MultiPlaceInjector>>>();
  public void registerMultiHostInjector(@NotNull Class<? extends PsiElement> place, ElementFilter filter, @NotNull MultiPlaceInjector injector) {
    List<Pair<ElementFilter, MultiPlaceInjector>> collection = injectors.get(place);
    if (collection == null) {
      collection = new SmartList<Pair<ElementFilter, MultiPlaceInjector>>();
      injectors.put(place, collection);
      injectors.clearCachedNulls();
    }
    Pair<ElementFilter, MultiPlaceInjector> pair = Pair.create(filter, injector);
    collection.add(pair);
  }
  public boolean unregisterMultiPlaceInjector(@NotNull MultiPlaceInjector injector) {
    injectors.clearCachedNulls();
    Iterator<List<Pair<ElementFilter, MultiPlaceInjector>>> iterator = injectors.values().iterator();
    boolean removed = false;
    while (iterator.hasNext()) {
      List<Pair<ElementFilter, MultiPlaceInjector>> collection = iterator.next();
      Iterator<Pair<ElementFilter, MultiPlaceInjector>> cot = collection.iterator();
      while (cot.hasNext()) {
        Pair<ElementFilter, MultiPlaceInjector> pair = cot.next();
        if (pair.getSecond() == injector) {
          cot.remove();
          removed = true;
        }
      }
      if (collection.isEmpty()) iterator.remove();
    }
    return removed;
  }
  public void processInPlaceInjectorsFor(@NotNull PsiElement element, @NotNull Processor<MultiPlaceInjector> processor) {
    Collection<Pair<ElementFilter, MultiPlaceInjector>> pairs = injectors.get(element.getClass());
    if (pairs != null) {
      for (Pair<ElementFilter, MultiPlaceInjector> pair : pairs) {
        ElementFilter filter = pair.getFirst();
        if (filter == null || (filter.isClassAcceptable(element.getClass()) && filter.isAcceptable(element, element))) {
          MultiPlaceInjector injector = pair.getSecond();
          if (!processor.process(injector)) return;
        }
      }
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "InjectdManager";
  }

  public void initComponent() {
    ProjectManager.getInstance().addProjectManagerListener(myProjectListener);
  }

  public void disposeComponent() {
    ProjectManager.getInstance().removeProjectManagerListener(myProjectListener);
  }

  public void clearCaches(VirtualFileWindow virtualFile) {
    synchronized (cachedFiles) {
      cachedFiles.remove(virtualFile);
    }
  }
}
