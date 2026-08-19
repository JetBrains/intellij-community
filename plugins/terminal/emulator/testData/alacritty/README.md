# Alacritty reference recordings

Recorded PTY output used by `AlacrittyReplayTest` to replay real program sessions through the emulator.

## Contents

* `<name>.recording` — the raw bytes a program wrote to its pty, **vendored from Alacritty** (see below).
* `<name>.txt` — the golden screen + scrollback dump this emulator produces for that stream. Generated;
  do not edit by hand.

The grid size each stream was captured at lives in `AlacrittyReplayTest.recordings()` — replaying at a
different size would reflow the content and invalidate the golden.

## Provenance and license

The `.recording` files come from Alacritty's reference-test corpus:

* Repository: <https://github.com/alacritty/alacritty>, path `alacritty_terminal/tests/ref/<name>/alacritty.recording`
* Commit: `9f8fed7c9e76b013f8c2632105d1abec18e6a64e` (2025-07-24)
* License: Apache-2.0 — Copyright the Alacritty contributors. See
  <https://github.com/alacritty/alacritty/blob/master/LICENSE-APACHE>.

Only the byte streams are reused. Alacritty's own `grid.json` snapshots are **not** vendored: they
serialize Alacritty's internal cell structs, which say nothing about what this emulator should produce.

## How the goldens were verified

A golden that is merely "whatever we printed last time" only catches change, not error. So when these were
created, every screen was diffed against Alacritty's recorded `grid.json` for the same stream (downloaded
separately, not vendored):

| | rows compared | identical |
|---|---|---|
| exact text | 549 | 511 (93.1%) |
| ignoring whitespace runs | 549 | 537 (97.8%) |

The whitespace-only differences are cell-model differences, not behavior: Alacritty stores a literal `\t`
in the cell a tab lands on and keeps zero-width marks in a side table, while this engine stores the
expanded spacing and the full grapheme cluster. `vttest_tab_clear_set` and
`scroll_in_region_up_preserves_history` differ only in that way.

Three real behavior differences remain, all reviewed before accepting the goldens:

1. **`selective_erasure`** — this engine honors DECSCA-protected cells during a selective erase (`CSI ? J`),
   leaving `" B"`; Alacritty erases everything, leaving `"ABC"`. libvterm's `t/65screen_protect.test`
   expects `" B"`, so the goldens here follow the spec. See `EraseTest.selectiveEraseSparesProtectedCells`.
2. **`erase_in_line`** — after printing into the last column, `CSI 1 K` resets the pending-wrap (LCF) state
   in this engine, so the next glyph lands in that column; Alacritty keeps the wrap pending and moves the
   glyph to the next row, which shifts its whole screen down by one. DEC STD-070 lists EL among the
   LCF-resetting operations (this is the cause of the well-known GNU grep "disappearing character" bug), so
   again the goldens follow the spec.
3. **`vttest_insert`** — one row differs: after an insert-mode (IRM) run, Alacritty keeps the 25 characters
   pushed past column 80 on a 105-column screen, this engine drops them. IRM and ICH shifting is correct
   here in isolation — a synthetic replay of the same row content and the same insert reproduces Alacritty's
   result exactly — so the difference comes from state built up earlier in the stream. Reproduce with the
   first 3686 bytes of `vttest_insert.recording` followed by `ESC [ 4 h`, 78 `*`, `ESC [ 4 l`, and compare
   against the same tail applied to a synthetic `"A" * 80 + ESC [ 1;2H + "B" + ESC [ 1 D`. Reported upstream.

## Re-recording the goldens

After an intended behavior change (typically a libghostty-vt upgrade):

```
TERMINAL_EMULATOR_OVERWRITE_TESTDATA=true ./tests.cmd \
  --module intellij.terminal.emulator.tests \
  --test com.intellij.terminal.emulator.AlacrittyReplayTest
```

Then read every changed golden before committing — a diff here is a behavior change, and the point of the
corpus is to make you look at it.

## Adding a corpus

Drop `<name>.recording` in this directory, add a `Recording(name, columns, rows)` entry to
`AlacrittyReplayTest.recordings()` using the size from Alacritty's `size.json`, and generate the golden as
above. Prefer streams that exercise something the hand-written tests in this package do not.
