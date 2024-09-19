from typing_extensions import TypeAlias

import gdb

ContinueEventRegistry: TypeAlias = gdb.EventRegistry[gdb.ContinueEvent]

cont: ContinueEventRegistry

ExitedEventRegistry: TypeAlias = gdb.EventRegistry[gdb.ExitedEvent]

exited: ExitedEventRegistry

StopEventRegistry: TypeAlias = gdb.EventRegistry[gdb.StopEvent]

stop: StopEventRegistry

NewObjFileEventRegistry: TypeAlias = gdb.EventRegistry[gdb.NewObjFileEvent]

new_objfile: NewObjFileEventRegistry

ClearObjFilesEventRegistry: TypeAlias = gdb.EventRegistry[gdb.ClearObjFilesEvent]

clear_objfiles: ClearObjFilesEventRegistry

InferiorCallEventRegistry: TypeAlias = gdb.EventRegistry[gdb._InferiorCallEvent]

inferior_call: InferiorCallEventRegistry

MemoryChangedEventRegistry: TypeAlias = gdb.EventRegistry[gdb.MemoryChangedEvent]
memory_changed: MemoryChangedEventRegistry

RegisterChangedEventRegistry: TypeAlias = gdb.EventRegistry[gdb.RegisterChangedEvent]

register_changed: RegisterChangedEventRegistry

BreakpointEventRegistry: TypeAlias = gdb.EventRegistry[gdb.Breakpoint]

breakpoint_created: BreakpointEventRegistry
breakpoint_modified: BreakpointEventRegistry
breakpoint_deleted: BreakpointEventRegistry

BeforePromptEventRegistry: TypeAlias = gdb.EventRegistry[None]

before_prompt: BeforePromptEventRegistry

NewInferiorEventRegistry: TypeAlias = gdb.EventRegistry[gdb.NewInferiorEvent]

new_inferior: NewInferiorEventRegistry

InferiorDeletedEventRegistry: TypeAlias = gdb.EventRegistry[gdb.InferiorDeletedEvent]

inferior_deleted: InferiorDeletedEventRegistry

NewThreadEventRegistry: TypeAlias = gdb.EventRegistry[gdb.NewThreadEvent]

new_thread: NewThreadEventRegistry

GdbExitingEventRegistry: TypeAlias = gdb.EventRegistry[gdb.GdbExitingEvent]

gdb_exiting: GdbExitingEventRegistry

ConnectionEventRegistry: TypeAlias = gdb.EventRegistry[gdb.ConnectionEvent]

connection_removed: ConnectionEventRegistry
