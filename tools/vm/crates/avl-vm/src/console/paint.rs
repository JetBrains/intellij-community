//! The dashboard's styles and its links.
//!
//! Every styled fragment of the dashboard goes through [`Paint`]: six hues, muted text, bold, banners and OSC 8
//! links. The colours behind them are the [`crate::console::palette`]: derived from the terminal's own foreground and
//! background when it said them, and the terminal's 16 colours when it did not. A style is applied only when
//! colour is on, so `NO_COLOR` and `TERM=dumb` get the same text without SGR codes. Both are the caller's to pass:
//! the dashboard reads no environment of its own.

use anstyle::Style;

pub(crate) use crate::console::palette::Hue;
use crate::console::palette::{Palette, TerminalColors};

/// Whether the dashboard colours its text.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) enum ColorChoice {
    /// Colour unless `NO_COLOR` is set or `TERM` is unset or `dumb`.
    #[default]
    Auto,
    /// Colour and links whatever `NO_COLOR` and `TERM` say. Only a test asks for it, so that its frames do not
    /// depend on the environment of the test.
    #[cfg(test)]
    Always,
    Never,
}

impl ColorChoice {
    /// The paint this choice means under the caller's `NO_COLOR` and `TERM`, over the colours the terminal said. A
    /// dumb terminal shows every escape as text, so it gets no link either; `NO_COLOR` is about colour only, and a
    /// link is not one.
    pub(crate) fn resolve(self, no_color: Option<&str>, term: Option<&str>, colors: Option<TerminalColors>) -> Paint {
        let dumb = term.is_none_or(|term| term.is_empty() || term == "dumb");
        let palette = Palette::of(colors);
        match self {
            #[cfg(test)]
            Self::Always => Paint {
                color: true,
                links: true,
                palette,
            },
            Self::Never => Paint {
                color: false,
                links: !dumb,
                palette,
            },
            Self::Auto => Paint {
                color: !dumb && no_color.is_none_or(str::is_empty),
                links: !dumb,
                palette,
            },
        }
    }
}

/// A resolved paint: whether colour and links are on, and the palette behind the colours.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Paint {
    pub(crate) color: bool,
    pub(crate) links: bool,
    pub(crate) palette: Palette,
}

impl Paint {
    /// Colour and links as given, over the terminal's own colours.
    #[cfg(test)]
    pub(crate) const fn fallback(color: bool, links: bool) -> Self {
        Self {
            color,
            links,
            palette: Palette::FALLBACK,
        }
    }

    fn apply(self, style: Style, text: &str) -> String {
        if text.is_empty() || !self.color {
            return text.to_owned();
        }
        format!("{}{text}{}", style.render(), style.render_reset())
    }

    pub(crate) fn hue(self, hue: Hue, text: &str) -> String {
        self.apply(self.palette.hue(hue), text)
    }

    pub(crate) fn pass(self, text: &str) -> String {
        self.hue(Hue::Green, text)
    }

    pub(crate) fn fail(self, text: &str) -> String {
        self.hue(Hue::Red, text)
    }

    pub(crate) fn warn(self, text: &str) -> String {
        self.hue(Hue::Yellow, text)
    }

    /// The phase in flight and the test that runs now.
    pub(crate) fn active(self, text: &str) -> String {
        self.apply(self.palette.hue(Hue::Cyan).bold(), text)
    }

    /// Text a reader may skip: a pending row, a typical time, a detail.
    pub(crate) fn muted(self, text: &str) -> String {
        self.apply(self.palette.muted(), text)
    }

    pub(crate) fn bold(self, text: &str) -> String {
        self.apply(Style::new().bold(), text)
    }

    /// Bold text on a background of `hue`: the banner of the header and of the card.
    pub(crate) fn banner(self, hue: Hue, text: &str) -> String {
        self.apply(self.palette.banner(hue), text)
    }

    /// `label` as an OSC 8 hyperlink to `url`. The label is the only text, so a long URL neither wraps nor looks
    /// like a URL that the terminal links on its own. A terminal without OSC 8 shows the label: the header and the
    /// card show the run's page with its URL as the label, and that page lists every trace.
    pub(crate) fn link(self, label: &str, url: &str) -> String {
        let label = self.apply(self.palette.hue(Hue::Blue).underline(), label);
        if !self.links {
            return label;
        }
        format!("\x1b]8;;{url}\x1b\\{label}\x1b]8;;\x1b\\")
    }
}
