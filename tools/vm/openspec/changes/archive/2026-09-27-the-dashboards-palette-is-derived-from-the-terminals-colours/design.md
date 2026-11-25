# The dashboard palette is derived from the foreground and the background of the terminal

## Context

The dashboard of `vm run` draws with the 16 colours of the terminal and SGR dim. Green, red and blue read on any
theme. On a light background, SGR dim, cyan and yellow have too little contrast, so muted text and warnings come out
pale.

A terminal can tell its colours. OSC 10 and OSC 11 answer the default foreground and background. A device-attributes
query sent after them tells a terminal that does not answer apart from a slow one. The JetBrains terminal answers. It
sets `TERMINAL_EMULATOR=JetBrains-JediTerm` and renders true colour, but it sets neither `COLORTERM` nor `COLORFGBG`.

## Decisions

1. **The palette is derived from the foreground and the background of the terminal.** Muted text is the foreground
   blended toward the background until it is just readable. Each hue starts from a seed for a light or a dark
   background and moves until it reads against the actual background. Readable is the WCAG contrast ratio of 4.5:1. A
   banner text is black or white, whichever reads better. [The palette](../../../../crates/avl-vm/src/console/palette.rs)
   holds the arithmetic.
2. **The terminal is asked once, with `terminal-colorsaurus`.** The crate sends OSC 10 and 11 with the
   device-attributes query, under a one-second bound, and sends nothing to `TERM=dumb`. The query runs only when the
   dashboard is about to draw in colour. A JSON, piped, `--text`, `NO_COLOR` or `AIR_VM_DASHBOARD=off` run sends
   nothing. [The terminal module](../../../../crates/avl-vm/src/terminal.rs) holds the query.
3. **A terminal that does not answer keeps the 16 colours and SGR dim.** `AIR_VM_THEME=light` or `dark` names the side
   for such a terminal. The palette is then derived from a plain foreground and background of that side, without a
   query.
4. **The colour depth follows `COLORTERM`.** A derived colour is true colour under `truecolor`, `24bit` or
   `TERMINAL_EMULATOR=JetBrains-JediTerm`. Otherwise `anstyle-lossy` writes the nearest of the 256-colour palette.
5. **Styles are `anstyle`.** `console` cannot write an RGB colour, and `anstyle` is the style type of the clap
   ecosystem. `console` stays for the text width and the terminal size.

[Supervision and evidence](../../../../spec/lane-controller.spec.md#supervision-and-evidence) state the contract of
the dashboard.

## Alternatives rejected

- **ratatui.** It has no background detection. The decision
  [the UI-lane tooling is Rust](../2026-09-26-the-ui-lane-tooling-is-rust/design.md) holds the main reason against it.
- **The lipgloss ports.** Their adaptive colour reads `COLORFGBG`, which these terminals do not set.
- **Two fixed palettes, light and dark.** A fixed palette fails on a cream or a grey background.
- **Bright black instead of dim, without a query.** Some themes set colour 8 to black, so muted text vanishes on a
  dark background.

## Consequences

- On every terminal that answers OSC 11, the dashboard reads in a light and in a dark theme.
- A terminal that answers costs one round trip before the first frame. A terminal that answers nothing costs the
  one-second bound once.
- A terminal that answers the colour query after the device-attributes answer counts as one that does not answer. It
  keeps the fallback.
- The palette tests pin the contrast of every derived colour on eight themes, and the exact escape sequences of the
  fallback.
