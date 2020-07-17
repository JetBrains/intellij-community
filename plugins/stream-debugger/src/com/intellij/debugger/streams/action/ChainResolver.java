// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.action;

import com.intellij.debugger.streams.lib.LibrarySupportProvider;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Helps {@link TraceStreamAction} understand if there is a suitable chain under the debugger position or not.
 */
class ChainResolver {
  private static final Logger LOG = Logger.getInstance(ChainResolver.class);

  private ChainsSearchResult mySearchResult = new ChainsSearchResult(0, -1, null);
  private final ExecutorService myExecutor =
    SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Stream debugger chains detector");

  @NotNull ChainStatus tryFindChain(@NotNull PsiElement elementAtDebugger) {
    if (mySearchResult.isSuitableFor(elementAtDebugger)) {
      return mySearchResult.chainsStatus;
    }

    mySearchResult = ChainsSearchResult.of(elementAtDebugger);
    checkChainsExistenceInBackground(elementAtDebugger, mySearchResult, myExecutor);
    return mySearchResult.chainsStatus;
  }

  private static void checkChainsExistenceInBackground(@NotNull PsiElement elementAtDebugger,
                                                       @NotNull ChainsSearchResult searchResult,
                                                       @NotNull ExecutorService executor) {
    List<LibrarySupportProvider> extensions = forLanguage(elementAtDebugger.getLanguage());
    if (extensions.isEmpty()) {
      searchResult.markUnsupportedLanguage();
    }
    else {
      ReadAction.nonBlocking(() -> {
        LibrarySupportProvider provider = ExtensionProcessingHelper
          .findFirstSafe(p -> p.getChainBuilder().isChainExists(elementAtDebugger), extensions);
        boolean found = provider != null;
        if (LOG.isDebugEnabled()) {
          LOG.debug("Chains found:" + found);
        }
        searchResult.updateStatus(found);
      }).inSmartMode(elementAtDebugger.getProject()).submit(executor);
    }
  }

  @NotNull List<StreamChainWithLibrary> getChains(@NotNull PsiElement elementAtDebugger) {
    if (!mySearchResult.isSuitableFor(elementAtDebugger) || !mySearchResult.chainsStatus.equals(ChainStatus.FOUND)) {
      LOG.error("Cannot build chains: " + mySearchResult.chainsStatus);
      return Collections.emptyList();
    }

    // TODO: move to background
    List<StreamChainWithLibrary> chains = new ArrayList<>();
    String elementLanguageId = elementAtDebugger.getLanguage().getID();
    LibrarySupportProvider.EP_NAME.forEachExtensionSafe(provider -> {
      if (provider.getLanguageId().equals(elementLanguageId)) {
        StreamChainBuilder chainBuilder = provider.getChainBuilder();
        if (chainBuilder.isChainExists(elementAtDebugger)) {
          for (StreamChain x : chainBuilder.build(elementAtDebugger)) {
            chains.add(new StreamChainWithLibrary(x, provider));
          }
        }
      }
    });

    return chains;
  }

  private static @NotNull List<LibrarySupportProvider> forLanguage(@NotNull Language language) {
    return LibrarySupportProvider.EP_NAME.getByGroupingKey(language.getID(), ChainResolver.class, LibrarySupportProvider::getLanguageId);
  }

  enum ChainStatus {
    LANGUAGE_NOT_SUPPORTED,
    COMPUTING,
    FOUND,
    NOT_FOUND
  }

  static final class StreamChainWithLibrary {
    final StreamChain chain;
    final LibrarySupportProvider provider;

    StreamChainWithLibrary(@NotNull StreamChain chain, @NotNull LibrarySupportProvider provider) {
      this.chain = chain;
      this.provider = provider;
    }
  }

  private static class ChainsSearchResult {
    final long elementHash;
    final long offset;
    final long fileModificationStamp;
    volatile @NotNull ChainStatus chainsStatus = ChainStatus.COMPUTING;

    ChainsSearchResult(long elementHash, long offsetInFile, @Nullable PsiFile containingFile) {
      this.elementHash = elementHash;
      fileModificationStamp = getModificationStamp(containingFile);
      offset = offsetInFile;
    }

    private static long getModificationStamp(@Nullable PsiFile file) {
      return file == null ? -1 : file.getModificationStamp();
    }

    static @NotNull ChainsSearchResult of(@NotNull PsiElement element) {
      return new ChainsSearchResult(element.hashCode(), element.getTextOffset(), element.getContainingFile());
    }

    void updateStatus(boolean found) {
      LOG.assertTrue(ChainStatus.COMPUTING.equals(chainsStatus));
      chainsStatus = found ? ChainStatus.FOUND : ChainStatus.NOT_FOUND;
    }

    void markUnsupportedLanguage() {
      LOG.assertTrue(ChainStatus.COMPUTING.equals(chainsStatus));
      chainsStatus = ChainStatus.LANGUAGE_NOT_SUPPORTED;
    }

    boolean isSuitableFor(@NotNull PsiElement element) {
      return elementHash == element.hashCode()
             && offset == element.getTextOffset()
             && fileModificationStamp == getModificationStamp(element.getContainingFile());
    }
  }
}
