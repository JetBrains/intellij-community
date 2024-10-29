package com.intellij.tools.ide.starter.bus.exceptions

class EventBusException(eventName: String, subscriberName: String, allSubscribersForEvent: List<Any>, throwable: Throwable)
  : Exception(
  "An exception ${throwable.message} occurred while processing the $eventName event by the $subscriberName subscriber.\n All subscribers $allSubscribersForEvent",
  throwable)