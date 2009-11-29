package com.jetbrains.python.toolbox;

/**
 * Linked list to base chain iterators an iterables on.
 * User: dcheryasov
 * Date: Nov 20, 2009 9:43:02 AM
 */
public /*abstract */class ChainedListBase<TPayload> {
  protected TPayload myPayload;
  protected ChainedListBase<TPayload> myNext;

  protected ChainedListBase(TPayload initial) {
    myPayload = initial;
  }

  /**
   * Wrap payload into a new linked list element.
   * @param payload
   * @return
   */
  /*
  abstract protected ChainedListBase<TPayload> createInstance(TPayload payload);
  */

  /**
   * Add another element to the end of our linked list
   * @param another
   * @return
   */
  protected ChainedListBase<TPayload> add(TPayload another) {
    if (myPayload == null) myPayload = another;
    else {
      ChainedListBase<TPayload> farthest = this;
      while (farthest.myNext != null) farthest = farthest.myNext;
      farthest.myNext = /*createInstance*/new ChainedListBase<TPayload>(another);
    }
    return this;
  }

  // become to our next
  public void moveOn() {
    if (myNext != null) {
      myPayload = myNext.myPayload;
      myNext = myNext.myNext;
    }
    else myPayload = null; // position 'after the end'
  }

  public boolean hasPayload() {
    return myPayload != null;
  }
}
