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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.semantic.SemService;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.impl.DomFileElementImpl;
import com.intellij.util.xml.impl.DomTestCase;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtenderEP;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * @author peter
 */
public class DomConcurrencyStressTest extends DomTestCase {
  public void testInternalDomLocksReadConsistency() throws Throwable {
    getDomManager().registerFileDescription(new DomFileDescription(MyElement.class, "a"), getTestRootDisposable());

    registerExtender(MyElement.class, MyExtender.class);


    final XmlFile file = createXmlFile("<a attr=\"1\" attr2=\"2\">" +
                                       "<foo-child><foo-child/><custom-foo/></foo-child>" +
                                       "<bar-child><foo-child/></bar-child>" +
                                       "<foo-element>" +
                                       "  <foo-child/>" +
                                       "  <foo-child><foo-child attr=\"\"/></foo-child>" +
                                       "  <custom-foo/>" +
                                       "  <custom-bar/>" +
                                       "</foo-element>" +
                                       "<custom-bar/>" +
                                       "<custom-bar>" +
                                       "  <foo-child/>" +
                                       "  <foo-element/>" +
                                       "  <foo-element/>" +
                                       "  <custom-bar/>" +
                                       "</custom-bar>" +
                                       "<custom-bar attr=\"\">" +
                                       "  <foo-child/>" +
                                       "  <some-child/>" +
                                       "  <custom-bar/>" +
                                       "</custom-bar>" +
                                       "<child/>" +
                                       "<child/>" +
                                       "<bool/>" +
                                       "</a>");

    final int ITERATIONS = Timings.adjustAccordingToMySpeed(239, true);
    System.out.println("ITERATIONS =" + ITERATIONS);
    runThreads(42, new Runnable() {
      @Override
      public void run() {
        for (int i = 0; i < ITERATIONS; i++) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              final DomFileElementImpl<DomElement> element = getDomManager().getFileElement(file);
              assertNotNull(element);
              element.getRootElement().accept(new DomElementVisitor() {
                @Override
                public void visitDomElement(final DomElement element) {
                  if (DomUtil.hasXml(element)) {
                    element.acceptChildren(this);
                  }
                }
              });
            }
          });

          SemService.getSemService(getProject()).clearCache();
        }
      }
    });
  }

  private void registerExtender(final Class elementClass, final Class extenderClass) {
    final DomExtenderEP extenderEP = new DomExtenderEP();
    extenderEP.domClassName = elementClass.getName();
    extenderEP.extenderClassName = extenderClass.getName();
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), DomExtenderEP.EP_NAME, extenderEP, getTestRootDisposable());
  }

  private static void runThreads(int threadCount, final Runnable runnable) throws Throwable {
    for (int i=0; i<threadCount/8 + 1; i++) {
      final Ref<Throwable> exc = Ref.create(null);

      int N = 8;
      final CountDownLatch reads = new CountDownLatch(N);
      List<Thread> threads = ContainerUtil.map(Collections.nCopies(N, ""), (Function<String, Thread>)s -> new Thread("dom concurrency") {
        @Override
        public void run() {
          try {
            runnable.run();
          }
          catch (Throwable e) {
            exc.set(e);
          }
          finally {
            reads.countDown();
          }
        }
      });

      threads.forEach(Thread::start);

      reads.await();
      if (!exc.isNull()) {
        throw exc.get();
      }
      ConcurrencyUtil.joinAll(threads);
    }
  }

  public interface MyElement extends DomElement {
    MyElement getFooChild();
    MyElement getBarChild();

    MyElement getSomeChild();

    List<MyElement> getFooElements();
    MyElement addFooElement();

    List<MyElement> getChildren();
    MyElement addChild();

    GenericAttributeValue<String> getAttr();
    GenericAttributeValue<String> getAttr2();

    @SubTag(indicator = true)
    GenericDomValue<Boolean> getBool();

  }

  public static class MyExtender extends DomExtender<MyElement> {
    private final Random myRandom = new Random();

    @Override
    public void registerExtensions(@NotNull MyElement myElement, @NotNull DomExtensionsRegistrar registrar) {
      for (MyElement element : myElement.getFooElements()) {
          element.getFooElements();
        }
      if (myRandom.nextInt(20) < 2) {
        TimeoutUtil.sleep(1);
      }
      registrar.registerFixedNumberChildExtension(new XmlName("custom-foo", null), MyElement.class);
      myElement.getSomeChild().getFooElements();
      myElement.getFooChild().getFooChild().getAttr();
      myElement.getAttr();
      registrar.registerCollectionChildrenExtension(new XmlName("custom-bar", null), MyElement.class);
    }
  }

  public void testBigCustomFile() throws Throwable {
    final int ITERATIONS = Timings.adjustAccordingToMySpeed(239, true);
    getDomManager().registerFileDescription(new DomFileDescription<>(MyAllCustomElement.class, "component"), getTestRootDisposable());

    registerExtender(MyAllCustomElement.class, MyAllCustomExtender.class);

    String line = "<tag1><tag2><tag3 attr=\"42\">value</tag3></tag2></tag1>\n";
    String text = "<component>\n" + StringUtil.repeat(line, 100) + "</component>";
    XmlFile file = (XmlFile)createFile("a.xml", text);

    runThreads(42, () -> {
      final Random random = new Random();
      for (int i = 0; i < ITERATIONS; i++) {
        ApplicationManager.getApplication().runReadAction(() -> {
          int offset = random.nextInt(file.getTextLength() - 10);
          XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(file, offset, XmlTag.class, false);
          assert tag != null : offset;
          DomElement element = DomUtil.getDomElement(tag);
          assert element instanceof MyAllCustomElement : element;
        });

        if (random.nextInt(20) == 0) {
          SemService.getSemService(getProject()).clearCache();
        }
      }
    });

  }

  public interface MyAllCustomElement extends DomElement {}

  public static class MyAllCustomExtender extends DomExtender<MyAllCustomElement> {
    @Override
    public void registerExtensions(@NotNull MyAllCustomElement element, @NotNull DomExtensionsRegistrar registrar) {
      try {
        DomElement parent = element;
        while (parent != null) {
          for (DomElement child : DomUtil.getDefinedChildren(parent, true, true)) {
            DomElement childParent = child.getParent();
            if (!parent.equals(childParent)) {
              String parentText = parent.getXmlTag().getText();
              String childText = child.getXmlElement().getText();
              child.getParent();
              parent.equals(childParent);
              throw new AssertionError(parent.getXmlElement() + "; " + childParent + "; " + child.getXmlElement().getText());
            }
          }

          parent = parent.getParent();
        }
      }
      catch (StackOverflowError e) {
        System.out.println(Thread.currentThread() + ":    " + element.getXmlTag().getName() + element.getXmlTag().hashCode());
        throw e;
      }
      registrar.registerCustomChildrenExtension(MyAllCustomElement.class);
    }
  }


}
