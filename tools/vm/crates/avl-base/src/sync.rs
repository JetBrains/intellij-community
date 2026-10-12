//! Locking that survives a panicking holder.

use std::sync::{Mutex, MutexGuard, PoisonError};

/// Locks, and takes over a lock a panicking holder poisoned.
///
/// Every mutex this is used on guards output (a footer, a dashboard frame), bookkeeping (a phase's timings, a
/// signal slot) or a cache whose entries are each inserted whole. Losing the rest of a run to one panic would be
/// worse than a possibly half-updated line, so the poison is ignored. A mutex whose state one panic can leave
/// inconsistent must not be locked through this.
pub fn lock<T: ?Sized>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(PoisonError::into_inner)
}
