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

/**
 * <h1>Model-view presenter package</h1>
 * <h2>How to use?</h2>
 * <p>
 *   <a href="http://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93presenter">MVP</a> pattern implementation with only view and presenter for now.
 * <dl>
 *  <dt>Presenter</dt>
 *  <dd>Handles all business logic and has <strong>no</strong> references to awt/swing, so it is 100% testable</dd>
 *  <dt>View</dt>
 *  <dd>Handles only view: it may import any swing/awt packages but should contain almost no logic, because it is untestable.</dd>
 *  </dl>
 *  One implements <strong>Presenter</strong> and <strong>View</strong>. Both may have links to each other.
 *  You run {@link com.jetbrains.python.vp.ViewPresenterUtils#linkViewWithPresenterAndLaunch(Class, Class, Creator)} to link and launch them.
 *  See its javadoc
 * </p>
 * <h2>Threading issues</h2>
 *
 * <p>
 *   Presenter and View should be thread-agnostic.
 *   Any call to <strong>view</strong> is invoked in EDT automatically. <br/>
 *   Call to <strong>presenter</strong> may be invoked in background (not implemented yet, see {@link com.jetbrains.python.vp.PresenterHandler})
 * </p>
 *
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.vp;