/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

public abstract class DomElementAnnotationsManager extends SimpleModificationTracker {

  public static DomElementAnnotationsManager getInstance(Project project) {
    return ServiceManager.getService(project, DomElementAnnotationsManager.class);
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
   * Calls {@link com.intellij.util.xml.highlighting.DomElementsInspection#checkFileElement(com.intellij.util.xml.DomFileElement, DomElementAnnotationHolder)}
   * with appropriate parameters if needed, saves the collected problems to {@link com.intellij.util.xml.highlighting.DomElementsProblemsHolder}, which
   * can then be obtained from {@link #getProblemHolder(com.intellij.util.xml.DomElement)} method, and returns them.
   *
   * @param element file element being checked
   * @param inspection inspection to run on the given file element
   * @param onTheFly
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
     * {@link com.intellij.util.xml.DomFileElement} 
     * @param element file element whose highlighting has been finished
     */
    void highlightingFinished(@NotNull DomFileElement element);
  }
}
