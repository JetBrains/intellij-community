/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
   * Add another element to the end of our linked list
   * @param another
   * @return
   */
  protected ChainedListBase<TPayload> add(TPayload another) {
    if (myPayload == null) {
      myPayload = another;
    }
    else {
      ChainedListBase<TPayload> farthest = this;
      while (farthest.myNext != null) farthest = farthest.myNext;
      farthest.myNext = /*createInstance*/new ChainedListBase<>(another);
    }
    return this;
  }
}
