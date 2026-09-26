package test

import dependency.WithSerialization

fun usage() {
    WithSerialization.<caret>serializer()
    WithSerialization.Companion.<caret>serializer()
}

val companionRefShort: WithSerialization.<caret>Companion = <caret>WithSerialization
val companionRefFull: WithSerialization.Companion = WithSerialization.<caret>Companion


// REF1: (in dependency.WithSerialization.Companion).serializer()
// REF2: (in dependency.WithSerialization.Companion).serializer()
// REF3: companion object of (dependency).WithSerialization
// REF4: companion object of (dependency).WithSerialization
// REF5: companion object of (dependency).WithSerialization

// CLS_REF1: (in dependency.WithSerialization.Companion).serializer()
// CLS_REF2: (in dependency.WithSerialization.Companion).serializer()
// CLS_REF3: companion object of (dependency).WithSerialization
// CLS_REF4: companion object of (dependency).WithSerialization
// CLS_REF5: companion object of (dependency).WithSerialization
