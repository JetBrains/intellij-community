/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.utils;

/**
 * @author Alexander Lobas
 */
public interface Position {
  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Direction
  //
  //////////////////////////////////////////////////////////////////////////////////////////
  int NONE = 0;
  int NORTH = 1 << 0;
  int SOUTH = 1 << 1;
  int WEST = 1 << 2;
  int EAST = 1 << 3;
  int NORTH_EAST = NORTH | EAST;
  int NORTH_WEST = NORTH | WEST;
  int SOUTH_EAST = SOUTH | EAST;
  int SOUTH_WEST = SOUTH | WEST;
  int NORTH_SOUTH = NORTH | SOUTH;
  int EAST_WEST = EAST | WEST;
}