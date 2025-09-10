// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.stubs;

import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.stubs.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.impl.DomFileElementImpl;
import com.intellij.util.xml.impl.DomInvocationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

public final class XIncludeStub extends ObjectStubBase<ElementStub> {
  private final String myHref;
  private final String myXpointer;
  private volatile CachedValue<DomElement> myCachedValue;

  public XIncludeStub(ElementStub parent, @Nullable String href, @Nullable String xpointer) {
    super(parent);
    myHref = href;
    myXpointer = xpointer;
    parent.addChild(this);
  }

  @Override
  public @NotNull List<? extends Stub> getChildrenStubs() {
    return Collections.emptyList();
  }

  @Override
  public ObjectStubSerializer<?, Stub> getStubType() {
    return DomElementTypeHolder.XIncludeStub;
  }

  public void resolve(DomInvocationHandler parent, List<DomElement> included, XmlName xmlName) {

    XmlFile file = parent.getFile();
    if (myCachedValue == null) {
      myCachedValue = CachedValuesManager.getManager(file.getProject()).createCachedValue(() -> {
        DomElement result = computeValue(parent);
        return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
      });
    }

    DomElement rootElement = myCachedValue.getValue();
    if (rootElement != null) {
      processChildrenWithLocalName(rootElement, xmlName.getLocalName(), new CommonProcessors.CollectProcessor<>(included));
    }
  }

  private static void processChildrenWithLocalName(DomElement parent, String localName, Processor<? super DomElement> processor) {
    parent.acceptChildren(new DomInvocationHandler.DomLocalNameElementVisitor(localName) {
      @Override
      public void visitDomElement(DomElement element) {
        processor.process(element);
      }
    });
  }

  private @Nullable DomElement computeValue(DomInvocationHandler parent) {
    if (StringUtil.isEmpty(myHref) || StringUtil.isEmpty(myXpointer)) {
      return null;
    }
    Matcher matcher = JDOMUtil.XPOINTER_PATTERN.matcher(myXpointer);
    if (!matcher.matches()) {
      return null;
    }
    String group = matcher.group(1);
    matcher = JDOMUtil.CHILDREN_PATTERN.matcher(group);
    if (!matcher.matches()) {
      return null;
    }
    String tagName = matcher.group(1);
    XmlFile file = parent.getFile();
    PsiFileImpl dummy = (PsiFileImpl)PsiFileFactory.getInstance(file.getProject()).createFileFromText(PlainTextLanguage.INSTANCE, myHref);
    dummy.setOriginalFile(file);
    PsiFileSystemItem fileSystemItem = new FileReferenceSet(dummy).resolve();
    if (fileSystemItem instanceof XmlFile) {
      DomFileElementImpl<DomElement> element = parent.getManager().getFileElement((XmlFile)fileSystemItem);
      if (element != null) {
        DomElement result = element.getRootElement();
        if (tagName.equals(result.getXmlElementName())) {
          String subTagName = matcher.group(2);
          if (subTagName != null) {
            CommonProcessors.FindFirstProcessor<DomElement> processor = new CommonProcessors.FindFirstProcessor<>();
            processChildrenWithLocalName(result, subTagName.substring(1), processor);
            return processor.getFoundValue();
          }
          return result;
        }
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "href=" + myHref + " xpointer=" + myXpointer;
  }

  static class XIncludeStubSerializer implements ObjectStubSerializer<XIncludeStub, ElementStub> {

    @Override
    public @NotNull String getExternalId() {
      return "xml.XIncludeStub";
    }

    @Override
    public void serialize(@NotNull XIncludeStub stub, @NotNull StubOutputStream dataStream) throws IOException {
      dataStream.writeUTFFast(StringUtil.notNullize(stub.myHref));
      dataStream.writeUTFFast(StringUtil.notNullize(stub.myXpointer));
    }

    @Override
    public @NotNull XIncludeStub deserialize(@NotNull StubInputStream dataStream, ElementStub parentStub) throws IOException {
      return new XIncludeStub(parentStub, dataStream.readUTFFast(), dataStream.readUTFFast());
    }

    @Override
    public void indexStub(@NotNull XIncludeStub stub, @NotNull IndexSink sink) {

    }

    @Override
    public String toString() {
      return "XInclude";
    }
  }
}
