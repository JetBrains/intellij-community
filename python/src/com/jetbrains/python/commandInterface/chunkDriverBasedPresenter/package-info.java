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
 * {@link com.jetbrains.python.commandInterface.CommandInterfacePresenter} implementation based on ideas of <strong>chunk</strong>
 * and {@link com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkDriver}.
 * This presenter explodes command line into several parts or chunks.
 * Chunks then passed to {@link com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkDriver driver} and it returns
 * all information it has about each chunk. Presenter uses this information to display chunks correctly using view.
 * To use this package, {@link com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkDriver} should be implemented.
 *
 * See {@link com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkDriverBasedPresenter} as entry point
 *
 *
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.commandInterface.chunkDriverBasedPresenter;