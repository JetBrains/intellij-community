use anstyle::Color;
use pretty_assertions::assert_eq;

use super::{BLACK, ColorDepth, Hue, Palette, READABLE, Rgb, TerminalColors, WHITE, contrast};
use crate::console::paint::Paint;

/// Themes people run the dashboard on, as the terminal answers OSC 10 and OSC 11: foreground, background.
const THEMES: [(&str, Rgb, Rgb); 8] = [
    ("IntelliJ Light", [0x08, 0x08, 0x08], [0xff, 0xff, 0xff]),
    ("Darcula", [0xbb, 0xbb, 0xbb], [0x2b, 0x2b, 0x2b]),
    ("Solarized Light", [0x65, 0x7b, 0x83], [0xfd, 0xf6, 0xe3]),
    ("Solarized Dark", [0x83, 0x94, 0x96], [0x00, 0x2b, 0x36]),
    ("Dracula", [0xf8, 0xf8, 0xf2], [0x28, 0x2a, 0x36]),
    ("black on white", BLACK, WHITE),
    ("white on black", WHITE, BLACK),
    ("grey on grey", [0x80, 0x80, 0x80], [0x60, 0x60, 0x60]),
];

fn rgb(color: Color) -> Rgb {
    match color {
        Color::Rgb(color) => [color.0, color.1, color.2],
        other => panic!("not a true colour: {other:?}"),
    }
}

fn colors(foreground: Rgb, background: Rgb, depth: ColorDepth) -> TerminalColors {
    TerminalColors {
        foreground,
        background,
        depth,
    }
}

/// On every theme, each hue reads against the background, a banner's text reads on the banner, and the muted text
/// is just readable: no fainter than 4.5:1, and fainter than the foreground unless the foreground is already
/// below that.
#[test]
fn every_derived_colour_reads_on_its_background() {
    for (name, foreground, background) in THEMES {
        let palette = Palette::derived(colors(foreground, background, ColorDepth::TrueColor));
        for (index, hue) in Hue::ALL.into_iter().enumerate() {
            let color = rgb(palette.hues[index]);
            let ratio = contrast(color, background);
            assert!(
                ratio >= READABLE - 0.01,
                "{name}: {hue:?} {color:02x?} on {background:02x?} is {ratio:.2}:1"
            );
            let text = rgb(palette.banner_text[index]);
            let ratio = contrast(text, color);
            assert!(
                ratio >= 3.0,
                "{name}: the banner text {text:02x?} on {hue:?} {color:02x?} is {ratio:.2}:1"
            );
        }
        let muted = rgb(palette.muted.expect("a derived palette has a muted colour"));
        let ratio = contrast(muted, background);
        if contrast(foreground, background) > READABLE {
            assert!(
                (READABLE - 0.05..READABLE + 0.15).contains(&ratio),
                "{name}: muted {muted:02x?} on {background:02x?} is {ratio:.2}:1"
            );
        } else {
            assert_eq!(muted, foreground, "{name}: a faint theme keeps its foreground");
        }
    }
}

/// The seeds follow the side of the background: a light background gets the darker seed of each hue.
#[test]
fn the_seeds_follow_the_background() {
    let light = Palette::derived(colors(BLACK, WHITE, ColorDepth::TrueColor));
    let dark = Palette::derived(colors(WHITE, BLACK, ColorDepth::TrueColor));
    for (index, hue) in Hue::ALL.into_iter().enumerate() {
        let (on_light, on_dark) = hue.seeds();
        assert_eq!(rgb(light.hues[index]), on_light, "{hue:?} on white");
        assert_eq!(rgb(dark.hues[index]), on_dark, "{hue:?} on black");
    }
    // Black on white: the classic muted grey.
    assert_eq!(rgb(light.muted.unwrap()), [0x76, 0x76, 0x76]);
}

/// A terminal that takes 256 colours gets the nearest of them and never a true colour; one that takes true
/// colour gets the colour as derived.
#[test]
fn the_depth_decides_how_a_colour_is_written() {
    let paint = |depth| Paint {
        color: true,
        links: true,
        palette: Palette::derived(colors(BLACK, WHITE, depth)),
    };
    let indexed = paint(ColorDepth::Ansi256).muted("x");
    assert!(indexed.contains("\x1b[38;5;") && !indexed.contains("38;2;"), "{indexed:?}");
    let truecolor = paint(ColorDepth::TrueColor).muted("x");
    assert_eq!(truecolor, "\x1b[38;2;118;118;118mx\x1b[0m");
    assert_eq!(
        paint(ColorDepth::TrueColor).banner(Hue::Green, " PASSED "),
        "\x1b[1m\x1b[38;2;255;255;255m\x1b[48;2;26;127;55m PASSED \x1b[0m"
    );
}

/// Without an answer from the terminal, the palette is the terminal's own colours, as it always was: the 16
/// colours, SGR dim for muted text, bright white on a colour for a banner.
#[test]
fn the_fallback_is_the_terminals_own_colours() {
    assert_eq!(Palette::of(None), Palette::FALLBACK);
    let paint = Paint::fallback(true, true);
    assert_eq!(paint.pass("✓"), "\x1b[32m✓\x1b[0m");
    assert_eq!(paint.fail("✗"), "\x1b[31m✗\x1b[0m");
    assert_eq!(paint.warn("▲"), "\x1b[33m▲\x1b[0m");
    assert_eq!(paint.active("⠋"), "\x1b[1m\x1b[36m⠋\x1b[0m");
    assert_eq!(paint.muted("○ lease"), "\x1b[2m○ lease\x1b[0m");
    assert_eq!(paint.bold("tests"), "\x1b[1mtests\x1b[0m");
    assert_eq!(
        paint.banner(Hue::Magenta, " AIR UI lane "),
        "\x1b[1m\x1b[97m\x1b[45m AIR UI lane \x1b[0m"
    );
    assert_eq!(
        paint.link("trace", "http://x"),
        "\x1b]8;;http://x\x1b\\\x1b[4m\x1b[34mtrace\x1b[0m\x1b]8;;\x1b\\"
    );
    assert_eq!(paint.pass(""), "");
}
