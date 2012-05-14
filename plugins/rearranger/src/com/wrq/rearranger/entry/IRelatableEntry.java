/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.entry;

import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.RelatedMethodsSettings;

import java.util.List;

/**
 * Defines methods implemented by entries which may be related to others, i.e. getters and setters,
 * method entries which call other methods, and overloaded method entries.
 */
public interface IRelatableEntry {
  /**
   * Determine if the entry is a getter/setter or is excluded from extracted method consideration by an
   * overriding rule.
   */
  void determineGetterSetterAndExtractedMethodStatus(RearrangerSettings settings);

  /**
   * Determine corresponding setters for getters and identify related methods.
   *
   * @param settings current settings, containing extracted/related method options.
   * @param contents contents of class, containing potentially related methods.
   */
  void determineSettersAndMethodCalls(RearrangerSettings settings,
                                      List<ClassContentsEntry> contents);

  /**
   * Decide if a method is an extracted method.  It is an extracted method if:
   * - it is called by at least one other method, and
   * - it is private, or passes the "never / 1 / >1" caller test.
   */
  void determineExtractedMethod(RelatedMethodsSettings settings);

  /** @return true if the method is related to another.  In this case, the method is exempt from rule matching. */
  boolean isRelatedMethod();

  /** @return true if the method is a setter and will be emitted below a corresponding getter. */
  boolean isEmittableSetter();
}
