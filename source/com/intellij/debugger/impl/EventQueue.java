package com.intellij.debugger.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.util.concurrency.Semaphore;

import java.util.LinkedList;


public class EventQueue<E> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.EventQueue");

  private final LinkedList [] myEvents;

  private E     myCurrentEvent;

  private boolean myIsClosed = false;

  public EventQueue (int countPriorities) {
    myEvents = new LinkedList[countPriorities];
    for (int i = 0; i < myEvents.length; i++) {
      myEvents[i] = new LinkedList<E>();
    }
  }

  public boolean isTerminated() {
    return myIsClosed;
  }

  public void put(E event, int priority) {
    LOG.assertTrue(event != null);
    if(LOG.isDebugEnabled()) {
      LOG.debug("put event " + event);
    }

    synchronized(myEvents) {
      myEvents[priority].addLast(event);
      myEvents.notify();
    }
  }

  public void close(){
    if (LOG.isDebugEnabled()) {
      LOG.debug("events closed");
    }
    myIsClosed = true;
    synchronized(myEvents) {
      myEvents.notifyAll();
    }
  }

  private E getEvent() throws EventQueueClosedException {
    for (int i = 0; i < myEvents.length; i++) {
      LinkedList<E> event = (LinkedList<E>)myEvents[i];
      if(!event.isEmpty()) return event.removeFirst();
    }

    if(myIsClosed) throw new EventQueueClosedException();

    return null;
  }

  public E get() throws EventQueueClosedException {
    synchronized(myEvents) {
      E event = getEvent();

      if(event == null) {
        try {
          myCurrentEvent = null;
          myEvents.wait();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }

        event = getEvent();
      }

      myCurrentEvent = event;

      LOG.assertTrue(event != null);
      if(LOG.isDebugEnabled()) {
        LOG.debug("get event " + event);
      }

      return event;
    }
  }

  public boolean isClosed() {
    return myIsClosed;
  }

  public static interface EventGetter<E> {
    public void event(E event);
  }

  public void getCurrentEvent(EventGetter<E> getter) {
    synchronized(myEvents) {
      getter.event(myCurrentEvent);
    }
  }
}
