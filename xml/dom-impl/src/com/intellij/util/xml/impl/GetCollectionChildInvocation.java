package com.intellij.util.xml.impl;

/**
 * @author peter
 */
public class GetCollectionChildInvocation implements Invocation {
  private final CollectionChildDescriptionImpl myDescription;

  public GetCollectionChildInvocation(final CollectionChildDescriptionImpl qname) {
    myDescription = qname;
  }

  public Object invoke(final DomInvocationHandler<?, ?> handler, final Object[] args) throws Throwable {
    return handler.getCollectionChildren(myDescription, myDescription.getTagsGetter());
  }

}
