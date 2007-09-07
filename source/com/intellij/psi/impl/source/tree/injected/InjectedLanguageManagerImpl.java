package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.injected.DocumentWindow;
import com.intellij.openapi.editor.impl.injected.VirtualFileWindow;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author cdr
 */
public class InjectedLanguageManagerImpl extends InjectedLanguageManager {
  private final WeakList<VirtualFileWindow> cachedFiles = new WeakList<VirtualFileWindow>();
  private final Project myProject;

  public static InjectedLanguageManagerImpl getInstance(Project project) {
    return (InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance(project);
  }

  public void projectOpened() {
    //Object[] extensions = Extensions.getExtensions(CONCATENATION_INJECTOR_EP_NAME, myProject);
    //for (Object injector : extensions) {
    //   registerConcatenationInjector((ConcatenationAwareInjector)injector);
    //}
    //final ExtensionPoint<ConcatenationAwareInjector> point = Extensions.getArea(myProject).getExtensionPoint(CONCATENATION_INJECTOR_EP_NAME);
    //
    //point.addExtensionPointListener(new ExtensionPointListener<ConcatenationAwareInjector>() {
    //  public void extensionAdded(ConcatenationAwareInjector extension, @Nullable PluginDescriptor pluginDescriptor) {
    //    registerConcatenationInjector(extension);
    //  }
    //
    //  public void extensionRemoved(ConcatenationAwareInjector extension, @Nullable PluginDescriptor pluginDescriptor) {
    //
    //  }
    //});
  }

  public void projectClosed() {

  }

  public InjectedLanguageManagerImpl(Project project) {
    myProject = project;
    registerMultiHostInjector(PsiLanguageInjectionHost.class, null, new MultiHostInjector() {
      public void getLanguagesToInject(@NotNull PsiElement context, @NotNull final MultiHostRegistrar injectionPlacesRegistrar) {
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
    registerMultiHostInjector(PsiElement.class, null, new Concatenation2InjectorAdapter());

    final ExtensionPoint<ConcatenationAwareInjector> point = Extensions.getArea(myProject).getExtensionPoint(CONCATENATION_INJECTOR_EP_NAME);

    point.addExtensionPointListener(new ExtensionPointListener<ConcatenationAwareInjector>() {
      public void extensionAdded(ConcatenationAwareInjector extension, @Nullable PluginDescriptor pluginDescriptor) {
        registerConcatenationInjector(extension);
      }

      public void extensionRemoved(ConcatenationAwareInjector extension, @Nullable PluginDescriptor pluginDescriptor) {

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

  private final Map<Class, List<Pair<ElementFilter, MultiHostInjector>>> injectors = new ConcurrentHashMap<Class, List<Pair<ElementFilter, MultiHostInjector>>>();
  private final ClassMapCachingNulls<Pair<ElementFilter, MultiHostInjector>> cachedInjectors = new ClassMapCachingNulls<Pair<ElementFilter, MultiHostInjector>>(injectors);
  public void registerMultiHostInjector(@NotNull Class<? extends PsiElement> place, ElementFilter filter, @NotNull MultiHostInjector injector) {
    List<Pair<ElementFilter, MultiHostInjector>> collection = injectors.get(place);
    if (collection == null) {
      collection = new SmartList<Pair<ElementFilter, MultiHostInjector>>();
      injectors.put(place, collection);
    }
    Pair<ElementFilter, MultiHostInjector> pair = Pair.create(filter, injector);
    collection.add(pair);
    cachedInjectors.clearCache();
  }
  public boolean unregisterMultiPlaceInjector(@NotNull MultiHostInjector injector) {
    Iterator<List<Pair<ElementFilter, MultiHostInjector>>> iterator = injectors.values().iterator();
    boolean removed = false;
    while (iterator.hasNext()) {
      List<Pair<ElementFilter, MultiHostInjector>> collection = iterator.next();
      Iterator<Pair<ElementFilter, MultiHostInjector>> cot = collection.iterator();
      while (cot.hasNext()) {
        Pair<ElementFilter, MultiHostInjector> pair = cot.next();
        if (pair.getSecond() == injector) {
          cot.remove();
          removed = true;
        }
      }
      if (collection.isEmpty()) iterator.remove();
    }
    cachedInjectors.clearCache();
    return removed;
  }

  private class Concatenation2InjectorAdapter implements MultiHostInjector {
    public void getLanguagesToInject(@NotNull PsiElement context, @NotNull MultiHostRegistrar injectionPlacesRegistrar) {
      if (myConcatenationInjectors.isEmpty()) return;
      PsiElement parent = context.getParent();
      if (parent instanceof PsiBinaryExpression) return;
      if (context instanceof PsiBinaryExpression) {
        List<PsiElement> operands = new ArrayList<PsiElement>();
        collectOperands(context, operands);
        PsiElement[] elements = operands.toArray(new PsiElement[operands.size()]);
        tryInjectors(injectionPlacesRegistrar, elements);  
      }
      else if (context instanceof PsiLanguageInjectionHost) {
        tryInjectors(injectionPlacesRegistrar, context);
      }
    }

    private void collectOperands(PsiElement expression, List<PsiElement> operands) {
      if (expression instanceof PsiBinaryExpression) {
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
        collectOperands(binaryExpression.getLOperand(), operands);
        collectOperands(binaryExpression.getROperand(), operands);
      }
      else {
        operands.add(expression);
      }
    }

    void tryInjectors(MultiHostRegistrar registrar, PsiElement... elements) {
      for (ConcatenationAwareInjector concatenationInjector : myConcatenationInjectors) {
        concatenationInjector.getLanguagesToInject(registrar, elements);
      }
    }
  }
  private final List<ConcatenationAwareInjector> myConcatenationInjectors = new CopyOnWriteArrayList<ConcatenationAwareInjector>();
  public void registerConcatenationInjector(@NotNull ConcatenationAwareInjector injector) {
    myConcatenationInjectors.add(injector);
  }

  public boolean unregisterConcatenationInjector(@NotNull ConcatenationAwareInjector injector) {
    return myConcatenationInjectors.remove(injector);
  }

  public void processInPlaceInjectorsFor(@NotNull PsiElement element, @NotNull Processor<MultiHostInjector> processor) {
    List<Pair<ElementFilter, MultiHostInjector>> pairs = cachedInjectors.get(element.getClass());
    if (pairs != null) {
      for (Pair<ElementFilter, MultiHostInjector> pair : pairs) {
        ElementFilter filter = pair.getFirst();
        if (filter == null || (filter.isClassAcceptable(element.getClass()) && filter.isAcceptable(element, element))) {
          MultiHostInjector injector = pair.getSecond();
          if (!processor.process(injector)) return;
        }
      }
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "InjectedLanguageManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void clearCaches(VirtualFileWindow virtualFile) {
    synchronized (cachedFiles) {
      cachedFiles.remove(virtualFile);
    }
  }
}
