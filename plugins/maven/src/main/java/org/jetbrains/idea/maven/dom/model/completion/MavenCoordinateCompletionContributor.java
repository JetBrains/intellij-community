// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionHandler;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.reposearch.DependencySearchService;
import org.jetbrains.idea.reposearch.RepositoryArtifactData;
import org.jetbrains.idea.reposearch.SearchParameters;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER;
import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
import static org.jetbrains.concurrency.Promise.State.PENDING;

public abstract class MavenCoordinateCompletionContributor extends CompletionContributor {

  private final String myTagId;

  protected MavenCoordinateCompletionContributor(String id) {
    myTagId = id;
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) return;
    PlaceChecker placeChecker = new PlaceChecker(parameters).checkPlace();

    if (placeChecker.isCorrectPlace()) {

      MavenDomShortArtifactCoordinates coordinates = placeChecker.getCoordinates();
      result = amendResultSet(result);
      ConcurrentLinkedDeque<RepositoryArtifactData> cld = new ConcurrentLinkedDeque<>();
      Promise<Integer> promise = find(
        DependencySearchService.getInstance(placeChecker.getProject()),
        coordinates, parameters,
        mdci -> cld.add(mdci)
      );

      fillResults(result, coordinates, cld, promise);
      fillAfter(result);
    }
  }

  protected void fillResults(@NotNull CompletionResultSet result,
                             @NotNull MavenDomShortArtifactCoordinates coordinates,
                             @NotNull ConcurrentLinkedDeque<RepositoryArtifactData> cld, @NotNull Promise<Integer> promise) {
    while (promise.getState() == PENDING || !cld.isEmpty()) {
      ProgressManager.checkCanceled();
      RepositoryArtifactData item = cld.poll();
      if (item instanceof MavenRepositoryArtifactInfo) {
        fillResult(coordinates, result, (MavenRepositoryArtifactInfo)item);
      }
    }
  }

  protected SearchParameters createSearchParameters(CompletionParameters parameters) {
    return new SearchParameters(parameters.getInvocationCount() < 2, ApplicationManager.getApplication().isUnitTestMode());
  }

  protected abstract Promise<Integer> find(@NotNull DependencySearchService service,
                                           @NotNull MavenDomShortArtifactCoordinates coordinates,
                                           @NotNull CompletionParameters parameters,
                                           @NotNull Consumer<RepositoryArtifactData> consumer);


  protected boolean validate(String groupId, String artifactId) {
    return true;
  }

  protected void fillAfter(CompletionResultSet result) {
  }

  protected void fillResult(@NotNull MavenDomShortArtifactCoordinates coordinates,
                            @NotNull CompletionResultSet result,
                            @NotNull MavenRepositoryArtifactInfo item) {
    result
      .addElement(MavenDependencyCompletionUtil.lookupElement(item).withInsertHandler(MavenDependencyInsertionHandler.INSTANCE));
  }

  @NotNull
  protected CompletionResultSet amendResultSet(@NotNull CompletionResultSet result) {
    result.restartCompletionWhenNothingMatches();
    return result;
  }

  @NotNull
  protected static String trimDummy(@Nullable String value) {
    if (value == null) {
      return "";
    }
    return StringUtil.trim(value.replace(DUMMY_IDENTIFIER, "").replace(DUMMY_IDENTIFIER_TRIMMED, ""));
  }

  protected static <T> Consumer<T> withPredicate(Consumer<T> consumer,
                                                 Predicate<T> predicate) {
    return it -> {
      if (predicate.test(it)) {
        consumer.accept(it);
      }
    };
  }

  protected class PlaceChecker {
    private boolean badPlace;
    private CompletionParameters myParameters;
    private Project myProject;
    private MavenDomShortArtifactCoordinates domCoordinates;

    public PlaceChecker(CompletionParameters parameters) {myParameters = parameters;}

    boolean isCorrectPlace() {return !badPlace;}

    Project getProject() {
      return myProject;
    }

    MavenDomShortArtifactCoordinates getCoordinates() {
      return domCoordinates;
    }

    public PlaceChecker checkPlace() {
      if (myParameters.getCompletionType() != CompletionType.BASIC) {
        badPlace = true;
        return this;
      }

      PsiElement element = myParameters.getPosition();

      PsiElement xmlText = element.getParent();
      if (!(xmlText instanceof XmlText)) {
        badPlace = true;
        return this;
      }

      PsiElement tagElement = xmlText.getParent();

      if (!(tagElement instanceof XmlTag)) {
        badPlace = true;
        return this;
      }

      XmlTag tag = (XmlTag)tagElement;

      if (!myTagId.equals(tag.getName())) {
        badPlace = true;
        return this;
      }

      myProject = element.getProject();

      switch (myTagId) {
        case "artifactId":
        case "groupId":
        case "version": {
          checkPlaceForChildrenTags(tag);
          break;
        }

        case "dependency":
        case "extension":
        case "plugin": {
          checkPlaceForParentTags(tag);
          break;
        }
        default:
          badPlace = true;
      }

      return this;
    }

    private void checkPlaceForChildrenTags(XmlTag tag) {
      DomElement domElement = DomManager.getDomManager(myProject).getDomElement(tag);

      if (!(domElement instanceof GenericDomValue)) {
        badPlace = true;
        return;
      }

      DomElement parent = domElement.getParent();
      if (parent instanceof MavenDomShortArtifactCoordinates) {
        domCoordinates = (MavenDomShortArtifactCoordinates)parent;
      }
      else {
        badPlace = true;
      }
    }

    private void checkPlaceForParentTags(XmlTag tag) {
      DomElement domElement = DomManager.getDomManager(myProject).getDomElement(tag);

      if (domElement instanceof MavenDomShortArtifactCoordinates) {
        domCoordinates = (MavenDomShortArtifactCoordinates)domElement;
      }
      else {
        badPlace = true;
      }
    }
  }
}
