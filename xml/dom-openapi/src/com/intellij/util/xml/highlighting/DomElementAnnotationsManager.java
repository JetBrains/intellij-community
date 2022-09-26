// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

public abstract class DomElementAnnotationsManager {

  public static DomElementAnnotationsManager getInstance(Project project) {
    return project.getService(DomElementAnnotationsManager.class);
  }

  @NotNull
  public abstract DomElementsProblemsHolder getProblemHolder(DomElement element);

  @NotNull
  public abstract DomElementsProblemsHolder getCachedProblemHolder(DomElement element);

  public abstract List<ProblemDescriptor> createProblemDescriptors(final InspectionManager manager, DomElementProblemDescriptor problemDescriptor);

  public abstract boolean isHighlightingFinished(final DomElement[] domElements);

  public abstract void addHighlightingListener(DomHighlightingListener listener, Disposable parentDisposable);

  public abstract DomHighlightingHelper getHighlightingHelper();

  /**
   * Calls {@link DomElementsInspection#checkFileElement(DomFileElement, DomElementAnnotationHolder)}
   * with appropriate parameters if needed, saves the collected problems to {@link DomElementsProblemsHolder}, which
   * can then be obtained from {@link #getProblemHolder(DomElement)} method, and returns them.
   *
   * @param element file element being checked
   * @param inspection inspection to run on the given file element
   * @return collected DOM problem descriptors
   */
  @NotNull
  public abstract <T extends DomElement> List<DomElementProblemDescriptor> checkFileElement(@NotNull DomFileElement<T> element,
                                                                                            @NotNull DomElementsInspection<T> inspection,
                                                                                            boolean onTheFly);

  public abstract void dropAnnotationsCache();

  public interface DomHighlightingListener extends EventListener {

    /**
     * Called each time when an annotator or inspection has finished error-highlighting of a particular
     * {@link DomFileElement}
     * @param element file element whose highlighting has been finished
     */
    void highlightingFinished(@NotNull DomFileElement element);
  }
}
