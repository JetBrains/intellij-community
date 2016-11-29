/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.annotation.Annotation;
import com.intellij.mock.MockInspectionProfile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.highlighting.*;
import com.intellij.util.xml.impl.DefaultDomAnnotator;
import com.intellij.util.xml.impl.DomTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;

/**
 * @author peter
 */
public class DomHighlightingLiteTest extends DomTestCase {
  private DomElementAnnotationsManagerImpl myAnnotationsManager;
  private MockDomFileElement myElement;
  private MockInspectionProfile myInspectionProfile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myInspectionProfile = new MockInspectionProfile();
    myAnnotationsManager = new DomElementAnnotationsManagerImpl(getProject()) {

      @Override
      protected InspectionProfile getInspectionProfile(final DomFileElement fileElement) {

        return myInspectionProfile;
      }
    };

    final XmlFile file = createXmlFile("<a/>");
    final MockDomElement rootElement = new MockDomElement() {
      @Override
      @Nullable
      public XmlElement getXmlElement() {
        return getXmlTag();
      }

      @Override
      public XmlTag getXmlTag() {
        return file.getRootTag();
      }

      @NotNull
      @Override
      public Type getDomElementType() {
        return DomElement.class;
      }
    };

    myElement = new MockDomFileElement() {
      @Override
      @Nullable
      public XmlElement getXmlElement() {
        return file;
      }

      @Override
      @NotNull
      public XmlFile getFile() {
        return file;
      }

      @Override
      public DomElement getParent() {
        return null;
      }

      @Override
      @NotNull
      public DomElement getRootElement() {
        return rootElement;
      }

      @NotNull
      @Override
      public Class getRootElementClass() {
        return DomElement.class;
      }

      @Override
      public boolean isValid() {
        return true;
      }
    };
  }

  @Override
  public void tearDown() throws Exception {
    myAnnotationsManager = null;
    myElement = null;
    myInspectionProfile = null;

    super.tearDown();
  }

  public void testEmptyProblemDescriptorInTheBeginning() throws Throwable {
    assertEmptyHolder(myAnnotationsManager.getProblemHolder(myElement));
  }

  private static void assertEmptyHolder(final DomElementsProblemsHolder holder) {
    assertFalse(holder instanceof DomElementsProblemsHolderImpl);
    assertEmpty(holder.getAllProblems());
  }

  public void testProblemDescriptorIsCreated() throws Throwable {
    myAnnotationsManager.appendProblems(myElement, createHolder(), MyDomElementsInspection.class);
    final DomElementsProblemsHolderImpl holder = assertNotEmptyHolder(myAnnotationsManager.getProblemHolder(myElement));
    assertEmpty(holder.getAllProblems());
    assertEmpty(holder.getAllProblems(new MyDomElementsInspection()));
  }

  private static DomElementAnnotationHolderImpl createHolder() {
    return new DomElementAnnotationHolderImpl(true);
  }

  private static DomElementsProblemsHolderImpl assertNotEmptyHolder(final DomElementsProblemsHolder holder1) {
    return assertInstanceOf(holder1, DomElementsProblemsHolderImpl.class);
  }

  public void testInspectionMarkedAsPassedAfterAppend() throws Throwable {
    myAnnotationsManager.appendProblems(myElement, createHolder(), MyDomElementsInspection.class);
    final DomElementsProblemsHolderImpl holder = (DomElementsProblemsHolderImpl)myAnnotationsManager.getProblemHolder(myElement);
    assertTrue(holder.isInspectionCompleted(MyDomElementsInspection.class));
    assertFalse(holder.isInspectionCompleted(DomElementsInspection.class));
  }

  public void testHolderRecreationAfterChange() throws Throwable {
    myAnnotationsManager.appendProblems(myElement, createHolder(), MyDomElementsInspection.class);
    assertTrue(DomElementAnnotationsManagerImpl.isHolderUpToDate(myElement));
    final DomElementsProblemsHolder holder = myAnnotationsManager.getProblemHolder(myElement);

    myElement.incModificationCount();
    assertFalse(DomElementAnnotationsManagerImpl.isHolderUpToDate(myElement));
    assertSame(holder, myAnnotationsManager.getProblemHolder(myElement));

    myAnnotationsManager.appendProblems(myElement, createHolder(), MyDomElementsInspection.class);
    assertNotSame(holder, assertNotEmptyHolder(myAnnotationsManager.getProblemHolder(myElement)));
  }

  public void testMockDomInspection() throws Throwable {
    myElement.setFileDescription(new MyNonHighlightingDomFileDescription());
    assertInstanceOf(myAnnotationsManager.getMockInspection(myElement), MockDomInspection.class);
  }

  public void testMockAnnotatingDomInspection() throws Throwable {
    myElement.setFileDescription(new DomFileDescription<>(DomElement.class, "a"));
    assertInstanceOf(myAnnotationsManager.getMockInspection(myElement), MockAnnotatingDomInspection.class);
  }

  public void testNoMockInspection() throws Throwable {
    myElement.setFileDescription(new MyNonHighlightingDomFileDescription());
    myInspectionProfile.setInspectionTools(new LocalInspectionToolWrapper(new MyDomElementsInspection()));
    assertNull(myAnnotationsManager.getMockInspection(myElement));
  }

  public void testDefaultAnnotator() throws Throwable {
    final DefaultDomAnnotator annotator = new DefaultDomAnnotator() {
      @Override
      protected DomElementAnnotationsManagerImpl getAnnotationsManager(final DomElement element) {
        return myAnnotationsManager;
      }
    };
    final StringBuilder s = new StringBuilder();
    final ArrayList<Annotation> toFill = new ArrayList<>();
    final MyDomElementsInspection inspection = new MyDomElementsInspection() {

      @Override
      public void checkFileElement(final DomFileElement fileElement, final DomElementAnnotationHolder holder) {
        s.append("visited");
      }
    };
    annotator.runInspection(inspection, myElement, toFill);
    assertEquals("visited", s.toString());
    final DomElementsProblemsHolderImpl holder = assertNotEmptyHolder(myAnnotationsManager.getProblemHolder(myElement));
    assertEmpty(toFill);

    annotator.runInspection(inspection, myElement, toFill);
    assertEquals("visited", s.toString());
    assertSame(holder, assertNotEmptyHolder(myAnnotationsManager.getProblemHolder(myElement)));
    assertEmpty(toFill);
  }

  public void testHighlightStatus_MockDomInspection() throws Throwable {
    myElement.setFileDescription(new MyNonHighlightingDomFileDescription());
    assertEquals(DomHighlightStatus.NONE, myAnnotationsManager.getHighlightStatus(myElement));

    myAnnotationsManager.appendProblems(myElement, createHolder(), MockDomInspection.class);
    assertEquals(DomHighlightStatus.INSPECTIONS_FINISHED, myAnnotationsManager.getHighlightStatus(myElement));
  }
  public void testHighlightStatus_MockAnnotatingDomInspection() throws Throwable {
    myElement.setFileDescription(new DomFileDescription<>(DomElement.class, "a"));

    myAnnotationsManager.appendProblems(myElement, createHolder(), MockAnnotatingDomInspection.class);
    assertEquals(DomHighlightStatus.INSPECTIONS_FINISHED, myAnnotationsManager.getHighlightStatus(myElement));
  }

  public void testHighlightStatus_OtherInspections() throws Throwable {
    myElement.setFileDescription(new DomFileDescription<>(DomElement.class, "a"));
    final MyDomElementsInspection inspection = new MyDomElementsInspection() {

      @Override
      public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager,
                                           final boolean isOnTheFly) {
        myAnnotationsManager.appendProblems(myElement, createHolder(), this.getClass());
        return ProblemDescriptor.EMPTY_ARRAY;
      }

      @Override
      public void checkFileElement(final DomFileElement fileElement, final DomElementAnnotationHolder holder) {
      }
    };
    registerInspectionKey(inspection);
    myInspectionProfile.setInspectionTools(new LocalInspectionToolWrapper(inspection));

    myAnnotationsManager.appendProblems(myElement, createHolder(), MockAnnotatingDomInspection.class);
    assertEquals(DomHighlightStatus.ANNOTATORS_FINISHED, myAnnotationsManager.getHighlightStatus(myElement));

    myAnnotationsManager.appendProblems(myElement, createHolder(), inspection.getClass());
    assertEquals(DomHighlightStatus.INSPECTIONS_FINISHED, myAnnotationsManager.getHighlightStatus(myElement));
  }

  private static void registerInspectionKey(MyDomElementsInspection inspection) {
    final String shortName = inspection.getShortName();
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null) {
      HighlightDisplayKey.register(shortName);
    }
  }

  public void testHighlightStatus_OtherInspections2() throws Throwable {
    myElement.setFileDescription(new DomFileDescription<>(DomElement.class, "a"));
    final MyDomElementsInspection inspection = new MyDomElementsInspection() {

      @Override
      public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager,
                                           final boolean isOnTheFly) {
        myAnnotationsManager.appendProblems(myElement, createHolder(), this.getClass());
        return ProblemDescriptor.EMPTY_ARRAY;
      }

      @Override
      public void checkFileElement(final DomFileElement fileElement, final DomElementAnnotationHolder holder) {
      }
    };
    registerInspectionKey(inspection);
    LocalInspectionToolWrapper toolWrapper = new LocalInspectionToolWrapper(inspection);
    myInspectionProfile.setInspectionTools(toolWrapper);
    myInspectionProfile.setEnabled(toolWrapper, false);

    myAnnotationsManager.appendProblems(myElement, createHolder(), MockAnnotatingDomInspection.class);
    assertEquals(DomHighlightStatus.INSPECTIONS_FINISHED, myAnnotationsManager.getHighlightStatus(myElement));
  }

  public void testRequiredAttributeWithoutAttributeValue() throws Throwable {
    final MyElement element = createElement("<a id />", MyElement.class);
    new MyBasicDomElementsInspection().checkDomElement(element.getId(), createHolder(), DomHighlightingHelperImpl.INSTANCE);
  }

  private static class MyDomElementsInspection extends DomElementsInspection<DomElement> {
    public MyDomElementsInspection() {
      super(DomElement.class);
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
      throw new UnsupportedOperationException("Method getGroupDisplayName is not yet implemented in " + getClass().getName());
    }

    @Override
    @NotNull
    public String getDisplayName() {
      throw new UnsupportedOperationException("Method getDisplayName is not yet implemented in " + getClass().getName());
    }

    @Override
    @NotNull
    public String getShortName() {
      return "xxx";
    }
  }

  private static class MyBasicDomElementsInspection extends BasicDomElementsInspection<DomElement> {
    public MyBasicDomElementsInspection() {
      super(DomElement.class);
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
      throw new UnsupportedOperationException("Method getGroupDisplayName is not yet implemented in " + getClass().getName());
    }

    @Override
    @NotNull
    public String getDisplayName() {
      throw new UnsupportedOperationException("Method getDisplayName is not yet implemented in " + getClass().getName());
    }

    @Override
    protected void checkDomElement(final DomElement element, final DomElementAnnotationHolder holder, final DomHighlightingHelper helper) {
      super.checkDomElement(element, holder, helper);
    }

    @Override
    @NotNull
    public String getShortName() {
      return "xxx";
    }
  }


  private static class MyNonHighlightingDomFileDescription extends DomFileDescription<DomElement> {
    public MyNonHighlightingDomFileDescription() {
      super(DomElement.class, "a");
    }

    @Override
    public boolean isAutomaticHighlightingEnabled() {
      return false;
    }
  }

  public interface MyElement extends DomElement {
    @Convert(soft=true, value=JvmPsiTypeConverter.class)
    @Required GenericAttributeValue<PsiType> getId();
  }
}
