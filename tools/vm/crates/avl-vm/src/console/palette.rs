//! Where the dashboard's colours come from.
//!
//! A terminal that answers OSC 10 and OSC 11 says its foreground and background. From those two colours the
//! palette derives every other one. Muted text is the foreground blended toward the background until it is just
//! readable. Each hue starts from a seed picked for a light or a dark background and is pushed toward the readable
//! side until it reads against the background too. Readable is the WCAG contrast ratio of 4.5:1 that normal text
//! is asked to meet. The result fits any theme, not only a light or a dark one, because it is computed against the
//! background the person actually has.
//!
//! A terminal that does not answer gets the palette the dashboard always had: its own 16 colours and SGR dim,
//! which most themes tune for their background, and which no query is needed for.

use anstyle::{AnsiColor, Color, RgbColor, Style};

#[cfg(test)]
mod tests;

/// How a derived colour is written: as it is, or as the nearest of the 256-colour palette.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum ColorDepth {
    Ansi256,
    TrueColor,
}

/// What the terminal said about itself: its default foreground and background, and how many colours it takes.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct TerminalColors {
    pub foreground: [u8; 3],
    pub background: [u8; 3],
    pub depth: ColorDepth,
}

/// The six hues the dashboard draws with. Green is a pass, red a failure, yellow a warning, blue a link, cyan the
/// phase in flight, and magenta the header.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Hue {
    Green,
    Red,
    Yellow,
    Blue,
    Cyan,
    Magenta,
}

impl Hue {
    const ALL: [Self; 6] = [Self::Green, Self::Red, Self::Yellow, Self::Blue, Self::Cyan, Self::Magenta];

    /// The hue as one of the terminal's own colours.
    const fn ansi(self) -> AnsiColor {
        match self {
            Self::Green => AnsiColor::Green,
            Self::Red => AnsiColor::Red,
            Self::Yellow => AnsiColor::Yellow,
            Self::Blue => AnsiColor::Blue,
            Self::Cyan => AnsiColor::Cyan,
            Self::Magenta => AnsiColor::Magenta,
        }
    }

    /// Where the hue starts on a light and on a dark background, before the contrast rule moves it.
    const fn seeds(self) -> (Rgb, Rgb) {
        match self {
            Self::Green => ([0x1a, 0x7f, 0x37], [0x3f, 0xb9, 0x50]),
            Self::Red => ([0xcf, 0x22, 0x2e], [0xf8, 0x51, 0x49]),
            Self::Yellow => ([0x9a, 0x67, 0x00], [0xd2, 0x99, 0x22]),
            Self::Blue => ([0x09, 0x69, 0xda], [0x58, 0xa6, 0xff]),
            Self::Cyan => ([0x0f, 0x76, 0x6e], [0x2d, 0xd4, 0xbf]),
            Self::Magenta => ([0x82, 0x50, 0xdf], [0xa3, 0x71, 0xf7]),
        }
    }
}

/// The colours behind every style of the dashboard.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Palette {
    /// One colour per hue, in the order of [`Hue::ALL`].
    hues: [Color; 6],
    /// The text on a banner of each hue.
    banner_text: [Color; 6],
    /// The muted text; `None` is SGR dim.
    muted: Option<Color>,
}

/// The contrast normal text is asked to have against its background.
const READABLE: f64 = 4.5;

type Rgb = [u8; 3];

const BLACK: Rgb = [0, 0, 0];
const WHITE: Rgb = [0xff, 0xff, 0xff];

impl Palette {
    /// The terminal's own colours: what a terminal gets that did not say its colours.
    pub(crate) const FALLBACK: Self = Self {
        hues: [
            Color::Ansi(Hue::Green.ansi()),
            Color::Ansi(Hue::Red.ansi()),
            Color::Ansi(Hue::Yellow.ansi()),
            Color::Ansi(Hue::Blue.ansi()),
            Color::Ansi(Hue::Cyan.ansi()),
            Color::Ansi(Hue::Magenta.ansi()),
        ],
        banner_text: [Color::Ansi(AnsiColor::BrightWhite); 6],
        muted: None,
    };

    /// The palette for a terminal that said its colours, or the fallback for one that did not.
    pub(crate) fn of(colors: Option<TerminalColors>) -> Self {
        colors.map_or(Self::FALLBACK, Self::derived)
    }

    /// Every colour derived from the terminal's foreground and background.
    pub(crate) fn derived(colors: TerminalColors) -> Self {
        let TerminalColors {
            foreground,
            background,
            depth,
        } = colors;
        let light = luminance(background) > luminance(foreground);
        let mut hues = [BLACK; 6];
        let mut banner_text = [BLACK; 6];
        for (index, hue) in Hue::ALL.into_iter().enumerate() {
            let (on_light, on_dark) = hue.seeds();
            let seed = if light { on_light } else { on_dark };
            let color = toward_contrast(seed, background, READABLE);
            hues[index] = color;
            banner_text[index] = if contrast(WHITE, color) >= contrast(BLACK, color) {
                WHITE
            } else {
                BLACK
            };
        }
        Self {
            hues: hues.map(|rgb| emit(rgb, depth)),
            banner_text: banner_text.map(|rgb| emit(rgb, depth)),
            muted: Some(emit(muted(foreground, background, READABLE), depth)),
        }
    }

    pub(crate) const fn hue(self, hue: Hue) -> Style {
        Style::new().fg_color(Some(self.hues[hue as usize]))
    }

    /// Bold text on a background of `hue`, in black or white, whichever reads better on it.
    pub(crate) const fn banner(self, hue: Hue) -> Style {
        Style::new()
            .bold()
            .fg_color(Some(self.banner_text[hue as usize]))
            .bg_color(Some(self.hues[hue as usize]))
    }

    pub(crate) const fn muted(self) -> Style {
        match self.muted {
            Some(color) => Style::new().fg_color(Some(color)),
            None => Style::new().dimmed(),
        }
    }
}

/// The colour as the terminal takes it.
const fn emit(rgb: Rgb, depth: ColorDepth) -> Color {
    let color = RgbColor(rgb[0], rgb[1], rgb[2]);
    match depth {
        ColorDepth::TrueColor => Color::Rgb(color),
        ColorDepth::Ansi256 => Color::Ansi256(anstyle_lossy::rgb_to_xterm(color)),
    }
}

/// The foreground moved toward the background as far as it stays readable; the foreground itself when it is not
/// readable to begin with, because moving it would only make that worse.
fn muted(foreground: Rgb, background: Rgb, target: f64) -> Rgb {
    if contrast(foreground, background) <= target {
        return foreground;
    }
    // The contrast falls as the blend approaches the background, so the largest blend that still reads is found
    // by bisection.
    let (mut readable, mut faint) = (0.0_f64, 1.0_f64);
    for _ in 0..24 {
        let middle = f64::midpoint(readable, faint);
        if contrast(blend(foreground, background, middle), background) >= target {
            readable = middle;
        } else {
            faint = middle;
        }
    }
    blend(foreground, background, readable)
}

/// `seed` when it reads against `background`, else the seed moved toward white on a dark background and toward
/// black on a light one until it does.
fn toward_contrast(seed: Rgb, background: Rgb, target: f64) -> Rgb {
    if contrast(seed, background) >= target {
        return seed;
    }
    let pole = if luminance(background) < 0.5 { WHITE } else { BLACK };
    let (mut faint, mut readable) = (0.0_f64, 1.0_f64);
    for _ in 0..24 {
        let middle = f64::midpoint(faint, readable);
        if contrast(blend(seed, pole, middle), background) >= target {
            readable = middle;
        } else {
            faint = middle;
        }
    }
    blend(seed, pole, readable)
}

/// `from` at 0, `to` at 1, mixed channel by channel.
fn blend(from: Rgb, to: Rgb, amount: f64) -> Rgb {
    let amount = amount.clamp(0.0, 1.0);
    let mut out = [0; 3];
    for (channel, (a, b)) in out.iter_mut().zip(from.into_iter().zip(to)) {
        #[expect(
            clippy::cast_possible_truncation,
            clippy::cast_sign_loss,
            reason = "a blend of two u8 channels at an amount in 0..=1 stays in 0..=255"
        )]
        let mixed = (f64::from(a) + (f64::from(b) - f64::from(a)) * amount).round() as u8;
        *channel = mixed;
    }
    out
}

/// The WCAG contrast ratio of two colours, from 1 to 21.
pub(crate) fn contrast(a: Rgb, b: Rgb) -> f64 {
    let (lighter, darker) = {
        let (a, b) = (luminance(a), luminance(b));
        (a.max(b), a.min(b))
    };
    (lighter + 0.05) / (darker + 0.05)
}

/// The WCAG relative luminance of an sRGB colour, from 0 (black) to 1 (white).
fn luminance(rgb: Rgb) -> f64 {
    let linear = |channel: u8| {
        let value = f64::from(channel) / 255.0;
        if value <= 0.039_28 {
            value / 12.92
        } else {
            ((value + 0.055) / 1.055).powf(2.4)
        }
    };
    0.2126 * linear(rgb[0]) + 0.7152 * linear(rgb[1]) + 0.0722 * linear(rgb[2])
}
