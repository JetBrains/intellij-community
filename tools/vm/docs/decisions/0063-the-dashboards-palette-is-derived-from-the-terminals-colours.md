---
topic: testing
---

# 63. The dashboard's palette is derived from the terminal's colours

Date: 2026-09-27

## Status

Accepted. It amends the terminal row of [ADR 0059](0059-the-ui-lane-tooling-is-rust.md). The palette is
`tests/integration/vm-lane/crates/avl-console/src/palette.rs`, and the query is
`tests/integration/vm-lane/crates/avl-vm/src/terminal.rs`.

## Context

The dashboard of `vm run` drew with the terminal's 16 colours and SGR dim, and trusted the theme to make them
readable. Green, red and blue do read on any theme. Three styles did not read on a light background, and the IDE
terminal in a light theme showed it:

- SGR dim is a blend toward the background in most emulators. Every pending phase, every typical time, the
  host-build detail, the elapsed label and the viewer note came out pale grey on white.
- Cyan and bold, the phase in flight, has little contrast on white.
- Yellow, a warning and the `STOPPED` banner, has less.

Nothing asked the terminal about its background. The Go dashboard before it had the same palette and the same gap.

A terminal can be asked. OSC 10 and OSC 11 answer the default foreground and background, and a device-attributes
query sent after them tells a terminal that does not answer apart from one that is slow. The JetBrains terminal
answers since 2026.2 ([IJPL-218303](https://youtrack.jetbrains.com/issue/IJPL-218303)). It sets
`TERMINAL_EMULATOR=JetBrains-JediTerm`, renders true colour, and sets neither `COLORTERM` nor `COLORFGBG`. Fleet's
terminal and GNU Screen do not answer.

## Decision

1. **The palette is derived from the terminal's foreground and background.** Muted text is the foreground blended
   toward the background until it is just readable. Each hue starts from a seed for a light or a dark background
   and is pushed toward the readable side until it reads against the actual background. Readable is the WCAG
   contrast ratio of 4.5:1. A banner's text is black or white, whichever reads better on the banner. The result
   fits any theme, because it is computed against the background the person has, not against a guess of it.
2. **The terminal is asked once, with `terminal-colorsaurus`.** It is what `bat` and `delta` use: OSC 10 and 11
   with the device-attributes trick, a one-second bound, nothing sent to `TERM=dumb`, raw mode restored on a
   panic. The query runs only when the dashboard is about to draw in colour. A JSON, piped, `--text`, `NO_COLOR`
   or `AIR_VM_DASHBOARD=off` run sends nothing.
3. **A terminal that does not answer keeps the palette it had.** The 16 colours and SGR dim stay as the fallback,
   unchanged. `AIR_VM_THEME=light` or `dark` names the side for such a terminal, and the palette is then derived
   from a plain foreground and background of that side without a query.
4. **The colour depth follows `COLORTERM`, and the JetBrains terminal's name stands in for it.** A derived colour
   is written as true colour under `truecolor`, `24bit` or `TERMINAL_EMULATOR=JetBrains-JediTerm`, and as the
   nearest of the 256-colour palette otherwise, through `anstyle-lossy`.
5. **Styles are `anstyle`.** `console` cannot write an RGB colour. `anstyle` is the style type of the clap
   ecosystem, has no dependencies, and was already in `Cargo.lock`. `console` stays for text width and the
   terminal size.

## Consequences

- On the IDE terminal 2026.2 and later, and on every terminal that answers OSC 11, the dashboard reads in a light
  theme as well as in a dark one, and muted text is muted rather than faint.
- A run on a terminal that answers costs one round trip before the first frame, in the milliseconds. A terminal
  that answers nothing costs the one-second bound once.
- The reworked IDE terminal answers the colour query from its UI thread, so its answer can arrive after the
  device-attributes answer. Such a terminal counts as one that does not answer and keeps the fallback. That is a
  terminal defect to report, not a reason for a second reader here.
- The palette tests pin the contrast of every derived colour on eight themes, and the fallback's exact escape
  sequences.

## Alternatives rejected

- **ratatui.** It has no background detection; its users pair it with `terminal-colorsaurus` themselves. ADR 0059
  rejected it for the dashboard's growing live region, and that stands.
- **The lipgloss ports.** `lipgloss-rs` and `charmed-lipgloss` carry an adaptive colour, chosen by `COLORFGBG`,
  which the terminals this lane runs on do not set.
- **Two fixed palettes, light and dark.** A palette tuned for white fails on Solarized's cream, and one tuned for
  black fails on a grey. The derived palette costs a few lines of arithmetic more and fits both.
- **Bright black instead of dim, without a query.** Some themes set colour 8 to black, and the muted text would
  vanish on a dark background.
