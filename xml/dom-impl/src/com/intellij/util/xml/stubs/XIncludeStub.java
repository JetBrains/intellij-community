// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.stubs;

import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.util.Key;
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
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.impl.DomFileElementImpl;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xmlb.JDOMXIncluder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

public class XIncludeStub extends ObjectStubBase<ElementStub> {

  private final String myHref;
  private final String myXpointer;
  private volatile CachedValue<DomElement> myCachedValue;

  public XIncludeStub(ElementStub parent, @Nullable String href, @Nullable String xpointer) {
    super(parent);
    myHref = href;
    myXpointer = xpointer;
    parent.addChild(this);
  }

  @NotNull
  @Override
  public List<? extends Stub> getChildrenStubs() {
    return Collections.emptyList();
  }

  @Override
  public ObjectStubSerializer getStubType() {
    return ourSerializer;
  }

  public void resolve(DomInvocationHandler parent, List<DomElement> included, XmlName xmlName) {

    XmlFile file = parent.getFile();
    if (myCachedValue == null) {
      myCachedValue = CachedValuesManager.getManager(file.getProject()).createCachedValue(() -> {
        DomElement result = computeValue(parent);
        return CachedValueProvider.Result.create(result, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      });
    }

    DomElement rootElement = myCachedValue.getValue();
    if (rootElement != null) {
      rootElement.acceptChildren(element -> {
        if (element.getXmlElementName().equals(xmlName.getLocalName())) {
          included.add(element);
        }
      });
    }
  }

  @Nullable
  private DomElement computeValue(DomInvocationHandler parent) {
    if (StringUtil.isEmpty(myHref) || StringUtil.isEmpty(myXpointer)) {
      return null;
    }
    Matcher matcher = JDOMXIncluder.XPOINTER_PATTERN.matcher(myXpointer);
    if (!matcher.matches()) {
      return null;
    }
    String group = matcher.group(1);
    matcher = JDOMXIncluder.CHILDREN_PATTERN.matcher(group);
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
        if (result != null && tagName.equals(result.getXmlElementName())) {
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

  final static ObjectStubSerializer ourSerializer = new ObjectStubSerializer<XIncludeStub, ElementStub>() {

    @NotNull
    @Override
    public String getExternalId() {
      return "XIncludeStub";
    }

    @Override
    public void serialize(@NotNull XIncludeStub stub, @NotNull StubOutputStream dataStream) throws IOException {
      dataStream.writeUTFFast(StringUtil.notNullize(stub.myHref));
      dataStream.writeUTFFast(StringUtil.notNullize(stub.myXpointer));
    }

    @NotNull
    @Override
    public XIncludeStub deserialize(@NotNull StubInputStream dataStream, ElementStub parentStub) throws IOException {
      return new XIncludeStub(parentStub, dataStream.readUTFFast(), dataStream.readUTFFast());
    }

    @Override
    public void indexStub(@NotNull XIncludeStub stub, @NotNull IndexSink sink) {

    }

    @Override
    public String toString() {
      return "XInclide";
    }
  };
}
