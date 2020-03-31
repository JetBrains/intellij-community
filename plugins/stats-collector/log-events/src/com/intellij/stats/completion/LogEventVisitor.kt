/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.stats.completion

import com.intellij.stats.completion.events.*


abstract class LogEventVisitor {
    open fun visit(event: CompletionStartedEvent) {}
    open fun visit(event: TypeEvent) {}
    open fun visit(event: DownPressedEvent) {}
    open fun visit(event: UpPressedEvent) {}
    open fun visit(event: BackspaceEvent) {}
    open fun visit(event: CompletionCancelledEvent) {}
    open fun visit(event: ExplicitSelectEvent) {}
    open fun visit(event: TypedSelectEvent) {}
    open fun visit(event: CustomMessageEvent) {}
}