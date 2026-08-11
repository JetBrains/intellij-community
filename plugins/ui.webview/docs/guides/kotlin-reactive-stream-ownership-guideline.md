# Kotlin Reactive Stream Ownership Guideline

This guideline defines how an agent should design and review Kotlin code that uses `Channel`, `Flow`, `StateFlow`, and `SharedFlow`.

## Core Rule

- Treat reactive streams as implementation details of domain objects.
- Preserve ownership context across architectural boundaries.
- Pass owners, not orphaned stream primitives.

## Must Rules

- `MUST` pass domain objects (or domain interfaces) instead of raw `Channel`/`Flow` through service/layer/module boundaries.
- `MUST` keep stream creation, lifecycle, and configuration in the owner type.
- `MUST` access streams through owner properties (`eventBus.events`) to preserve semantics.
- `MUST` expose read-only stream APIs:
  - `ReceiveChannel<T>` instead of `Channel<T>`
  - `StateFlow<T>` via `.asStateFlow()`
  - `SharedFlow<T>` via `.asSharedFlow()`
- `MUST` mutate stream-backed state only through domain methods with validation.

## Avoid Rules

- `AVOID` constructor/method parameters like `ReceiveChannel<T>`, `Flow<T>`, `StateFlow<T>` in non-infrastructure layers when they represent domain signals.
- `AVOID` nested reactive primitives that hide ownership semantics (`Flow<Flow<T>>`, `StateFlow<Flow<T>>`) unless clearly modeling infrastructure mechanics.
- `AVOID` exposing mutable interfaces (`Channel`, `MutableStateFlow`, `MutableSharedFlow`) from domain objects.
- `AVOID` calling `close()`/`cancel()` on a stream when ownership is not explicit.
- `AVOID` semantic drift in naming (`events` -> `channel` -> `dataSource`) by anchoring usage to domain types.

## Allowed Exceptions

Direct stream passing is acceptable when ownership remains explicit and local:

- private methods within the same owning class
- tightly scoped internal implementation details inside one cohesive module
- framework/utility transformations where ownership passes through unchanged
- immediate producer-consumer handoff at the same abstraction level

If in doubt: treat it as a boundary-crossing case and pass the owner.

## Agent Review Checklist

When generating or reviewing code, flag and refactor if any answer is unclear:

- Who created this stream?
- Who owns its lifecycle?
- Who is allowed to mutate/close/cancel it?
- What domain entity does this stream represent?
- Can the same behavior be expressed by passing a domain owner instead?

## Refactoring Pattern

1. Introduce/identify owner type (`EventBus`, `Session`, `OrderFeed`, etc.).
2. Move stream field and lifecycle logic into owner.
3. Replace stream parameters with owner parameters in cross-layer APIs.
4. Expose read-only stream views from owner.
5. Add explicit domain methods for mutation/publishing.

## Minimal Example

```kotlin
// Bad
class EventService(private val events: Flow<Event>)

// Good
class EventService(private val eventBus: EventBus)

class EventBus {
    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun publish(event: Event) {
        _events.tryEmit(event)
    }
}
```
