// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.application.Application;
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
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenArtifactInfoInsertionHandler;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

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
    PlaceChecker placeChecker = new PlaceChecker(parameters).checkPlace();

    if (placeChecker.isCorrectPlace()) {

      MavenDomShortArtifactCoordinates coordinates = placeChecker.getCoordinates();
      result = amendResultSet(result);
      ConcurrentLinkedDeque<MavenRepositoryArtifactInfo> cld = new ConcurrentLinkedDeque<>();
      Promise<Void> promise = find(
        MavenProjectIndicesManager.getInstance(placeChecker.getProject()).getDependencySearchService(),
        coordinates, parameters,
        mdci -> cld.add(mdci)
      );

      while (promise.getState() == PENDING || !cld.isEmpty()) {
        ProgressManager.checkCanceled();
        MavenRepositoryArtifactInfo item = cld.poll();
        if (item != null) {
          fillResult(result, item);
        }
      }
      fillAfter(result);
    }
  }

  protected SearchParameters createSearchParameters(CompletionParameters parameters) {
    if (parameters.getInvocationCount() > 1
        || ApplicationManager.getApplication().isUnitTestMode()/*to be fixed in future*/) {
      return SearchParameters.FULL;
    }
    return SearchParameters.DEFAULT;
  }

  protected abstract Promise<Void> find(@NotNull DependencySearchService service,
                                        @NotNull MavenDomShortArtifactCoordinates coordinates,
                                        @NotNull CompletionParameters parameters,
                                        @NotNull Consumer<MavenRepositoryArtifactInfo> consumer);


  protected boolean validate(String groupId, String artifactId) {
    return true;
  }

  protected void fillAfter(CompletionResultSet result) {
  }

  protected void fillResult(@NotNull CompletionResultSet result, MavenRepositoryArtifactInfo item) {
    result
      .addElement(MavenDependencyCompletionUtil.lookupElement(item).withInsertHandler(MavenArtifactInfoInsertionHandler.INSTANCE));
  }

  @NotNull
  protected CompletionResultSet amendResultSet(@NotNull CompletionResultSet result) {
    result.restartCompletionOnAnyPrefixChange();
    return result;
  }

  @NotNull
  protected static String trimDummy(@Nullable String value) {
    if (value == null) {
      return "";
    }
    return StringUtil.trim(value.replace(DUMMY_IDENTIFIER, "").replace(DUMMY_IDENTIFIER_TRIMMED, ""));
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
