use pretty_assertions::assert_eq;

use super::*;

const FRAME: Window = 0x40_0001;
const DIALOG: Window = 0x40_0002;
const NO_INPUT: Window = 0x40_0003;

// A mapped window goes on top, joins the client list and gets the focus. A window whose hints refuse the input is
// mapped and raised, and the focus stays where it is.
#[test]
fn a_map_request_maps_raises_and_focuses_a_window_that_takes_the_focus() {
    let mut clients = Clients::default();

    assert_eq!(
        clients.map(FRAME, true),
        [
            Effect::Map(FRAME),
            Effect::Raise(FRAME),
            Effect::ClientList(vec![FRAME]),
            Effect::Focus(FRAME)
        ]
    );
    assert_eq!(
        clients.map(NO_INPUT, false),
        [
            Effect::Map(NO_INPUT),
            Effect::Raise(NO_INPUT),
            Effect::ClientList(vec![FRAME, NO_INPUT])
        ]
    );
    assert_eq!(clients.focus(), Some(FRAME));
}

// `toFront()` of the JDK and `_NET_ACTIVE_WINDOW` raise the window, and the message also moves the focus. A window that
// the window manager does not manage, such as a popup with override-redirect, changes nothing.
#[test]
fn an_active_window_message_raises_and_focuses_a_managed_window() {
    let mut clients = Clients::default();
    clients.map(FRAME, true);
    clients.map(DIALOG, true);

    assert_eq!(clients.activate(FRAME), [Effect::Raise(FRAME), Effect::Focus(FRAME)]);
    assert_eq!(clients.focus(), Some(FRAME));
    assert!(clients.activate(0x50_0000).is_empty());

    clients.map(NO_INPUT, false);
    assert_eq!(clients.activate(NO_INPUT), [Effect::Raise(NO_INPUT)]);
    assert_eq!(clients.focus(), Some(FRAME));
}

// A closed dialog gives the focus back to the topmost window that takes it, so the frame below is active again. The
// order of the stack follows the configure requests. A window without the focus leaves the focus alone, and the last
// window gives the focus to the root.
#[test]
fn a_gone_window_with_the_focus_gives_it_to_the_topmost_window_that_takes_it() {
    let mut clients = Clients::default();
    clients.map(FRAME, true);
    clients.map(DIALOG, true);
    clients.map(NO_INPUT, false);
    clients.restack(NO_INPUT, Restack::Bottom);
    clients.activate(DIALOG);

    assert_eq!(clients.gone(NO_INPUT), [Effect::ClientList(vec![FRAME, DIALOG])]);
    assert_eq!(clients.gone(DIALOG), [Effect::ClientList(vec![FRAME]), Effect::Focus(FRAME)]);
    assert!(clients.gone(0x50_0000).is_empty());
    assert_eq!(clients.gone(FRAME), [Effect::ClientList(Vec::new()), Effect::FocusRoot]);
    assert_eq!(clients.focus(), None);
}

// The stack follows a raise of the frame over the dialog, so the dialog is the one that gets the focus back when the
// frame goes.
#[test]
fn a_raise_moves_the_window_to_the_top_of_the_stack() {
    let mut clients = Clients::default();
    clients.map(DIALOG, true);
    clients.map(FRAME, true);
    clients.restack(DIALOG, Restack::Top);
    clients.restack(FRAME, Restack::Top);

    assert_eq!(clients.gone(FRAME), [Effect::ClientList(vec![DIALOG]), Effect::Focus(DIALOG)]);
}
