/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * {@link com.jetbrains.python.commandInterface.CommandInterfacePresenter} implementation based on ideas of <strong>range info</strong>
 * and {@link com.jetbrains.python.commandInterface.rangeBasedPresenter.RangeInfoDriver}.
 * This presenter passes command line to driver, and it returns pack of range information.
 * Each record contains everything presenter needs to know about certain range (i.e. error between 2 and 5 chars).
 * Special type of range {@link com.jetbrains.python.commandInterface.rangeBasedPresenter.RangeInfo#TERMINATION_RANGE} exists, that
 * you may need to check.
 * <p/>
 * Presenter uses this information to display chunks correctly using view.
 * To use this package, {@link com.jetbrains.python.commandInterface.rangeBasedPresenter.RangeInfoDriver} should be implemented.
 * <p/>
 * See {@link com.jetbrains.python.commandInterface.rangeBasedPresenter.RangeBasedPresenter} as entry point
 *
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.commandInterface.rangeBasedPresenter;