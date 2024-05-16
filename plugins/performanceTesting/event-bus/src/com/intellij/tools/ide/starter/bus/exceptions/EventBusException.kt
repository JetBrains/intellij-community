package com.intellij.tools.ide.starter.bus.exceptions

class EventBusException(eventName: String, subscriberName: String, allSubscribersForEvent: List<Any>, throwable: Throwable)
  : Exception(
  "An exception occurred while processing the $eventName event by the $subscriberName subscriber. All subscribers $allSubscribersForEvent",
  throwable)