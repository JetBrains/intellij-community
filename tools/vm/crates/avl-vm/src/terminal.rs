//! What the process's descriptors are, and what the output form and the human renderer may do with them.
//!
//! The rules are the reader's: text when stdout is a terminal and JSON when it is not; a footer redrawn in place
//! only when the form followed the terminal; the live dashboard only for a run on such a terminal.

use std::io::IsTerminal;

use crate::console::{ColorDepth, TerminalColors};
use avl_base::Environment;
use avl_base::config::{Presentation, Theme};
use avl_base::report::{Renderer, Terminal};

/// Starts the live dashboard on stderr for a terminal with these properties, under the process's environment and
/// the theme it names.
pub(crate) type DashboardFactory = Box<dyn Fn(Terminal, &Environment, Option<Theme>) -> Box<dyn Renderer> + Send + Sync>;

/// What `main` found out about the process's descriptors.
#[derive(Default)]
pub(crate) struct TerminalFacts {
    /// Whether stdout is a terminal.
    pub stdout: bool,
    /// Whether stderr is a terminal.
    pub stderr: bool,
    /// Stderr's width in columns, or 0 when it is unknown.
    pub width: u16,
    /// `None` keeps the plain lines, which is what a test gets.
    pub new_dashboard: Option<DashboardFactory>,
}

impl TerminalFacts {
    /// The process's own descriptors, without a dashboard factory.
    ///
    /// `is_terminal` asks the terminal driver rather than reading the file mode: `/dev/null` is a character device
    /// too, and a run redirected there is not one a person reads. The width is asked only of a terminal on
    /// stderr, the one the footer is drawn on.
    pub(crate) fn detect() -> Self {
        let stderr = std::io::stderr().is_terminal();
        let width = if stderr {
            console::Term::stderr().size_checked().map_or(0, |(_, columns)| columns)
        } else {
            0
        };
        Self {
            stdout: std::io::stdout().is_terminal(),
            stderr,
            width,
            new_dashboard: None,
        }
    }
}

/// What an invocation answers in.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Output {
    /// One JSON envelope: what an agent's shell, which pipes stdout, reads.
    Json,
    /// Prose for a person, with the progress as it happens.
    Text,
}

/// The output form an invocation answers in.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Form {
    pub output: Output,
    /// `--json` or `--text` was given, rather than the form following the terminal.
    pub chosen: bool,
}

impl Form {
    /// The form from the last of `--json` and `--text`, or from the terminal when neither was given: JSON unless
    /// stdout is a terminal. A person who pastes a command reads progress as it happens; an agent's shell pipes
    /// stdout, so it reads the one JSON envelope it always did.
    pub(crate) const fn of(requested: Option<Output>, facts: &TerminalFacts) -> Self {
        match requested {
            Some(output) => Self { output, chosen: true },
            None => Self {
                output: if facts.stdout { Output::Text } else { Output::Json },
                chosen: false,
            },
        }
    }
}

/// The footer's bound when the terminal does not say its width.
pub(crate) const DEFAULT_TERMINAL_WIDTH: u16 = 80;

/// What the human renderer may do with stderr.
///
/// The footer is redrawn only when the form followed the terminal. An explicit `--text` is a request for prose,
/// often into a file that a tool happens to open as a terminal, so it gets plain lines. `TERM=dumb` is a terminal
/// that cannot erase a line, and `NO_COLOR` is the convention for no colour.
pub(crate) fn screen_for(facts: &TerminalFacts, form: Form, environment: &Environment) -> Terminal {
    let redraw = form.output == Output::Text && !form.chosen && facts.stderr && environment.get("TERM") != Some("dumb");
    // A pseudo-terminal can answer a width of 0, as `script` does. A footer with no bound then wraps, and a wrapped
    // footer cannot be erased, so the width falls back to `COLUMNS` and then to 80.
    let width = match facts.width {
        0 => environment
            .get("COLUMNS")
            .and_then(|columns| columns.parse::<u16>().ok())
            .filter(|columns| *columns > 0)
            .unwrap_or(DEFAULT_TERMINAL_WIDTH),
        known => known,
    };
    Terminal {
        redraw,
        color: redraw && environment.get("NO_COLOR").is_none(),
        width,
    }
}

/// Dark text on white, and light text on near-black: what `AIR_VM_THEME` stands for.
const LIGHT_THEME: ([u8; 3], [u8; 3]) = ([0x1f, 0x1f, 0x1f], [0xff, 0xff, 0xff]);
const DARK_THEME: ([u8; 3], [u8; 3]) = ([0xd4, 0xd4, 0xd4], [0x1e, 0x1e, 0x1e]);

/// The colours the dashboard derives its palette from, or `None` for the terminal's own 16 colours.
///
/// A `theme` (`AIR_VM_THEME`) answers without a query. Otherwise `query` asks the terminal, and a terminal that does
/// not answer - a multiplexer, an IDE terminal older than 2026.2, a run with every stream redirected - keeps the
/// palette it always had. The depth is true colour when `COLORTERM` says so; the JetBrains terminal renders true
/// colour but does not set it, so its name stands in.
pub(crate) fn colors_for(
    environment: &Environment,
    theme: Option<Theme>,
    query: impl FnOnce() -> Option<([u8; 3], [u8; 3])>,
) -> Option<TerminalColors> {
    let truecolor = matches!(environment.get("COLORTERM"), Some("truecolor" | "24bit"))
        || environment.get("TERMINAL_EMULATOR") == Some("JetBrains-JediTerm");
    let (foreground, background) = match theme {
        Some(Theme::Light) => LIGHT_THEME,
        Some(Theme::Dark) => DARK_THEME,
        None => query()?,
    };
    Some(TerminalColors {
        foreground,
        background,
        depth: if truecolor { ColorDepth::TrueColor } else { ColorDepth::Ansi256 },
    })
}

/// Asks the terminal for its foreground and background over OSC 10 and OSC 11, once, before the dashboard draws
/// its first frame. A terminal that answers a device-attributes query first does not support the colour query,
/// and one that answers nothing within a second is given up on; both are `None`.
pub(crate) fn query_terminal_colors() -> Option<([u8; 3], [u8; 3])> {
    let palette = terminal_colorsaurus::color_palette(terminal_colorsaurus::QueryOptions::default()).ok()?;
    let rgb = |color: terminal_colorsaurus::Color| {
        let (r, g, b) = color.scale_to_8bit();
        [r, g, b]
    };
    Some((rgb(palette.foreground), rgb(palette.background)))
}

/// Whether this invocation draws the live dashboard: a run that a person reads on a terminal that can redraw, with
/// no `--stream`, and with the dashboard not turned off.
pub(crate) fn wants_dashboard(facts: &TerminalFacts, screen: Terminal, stream: bool, is_run: bool, presentation: &Presentation) -> bool {
    facts.new_dashboard.is_some() && screen.redraw && !stream && is_run && presentation.dashboard
}
