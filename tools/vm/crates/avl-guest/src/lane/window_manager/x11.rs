//! The X11 half of the window manager: the setup on the root window, and the event loop that turns each request into the
//! [`Effect`]s of [`Clients`].

use std::thread::{self, JoinHandle};

use x11rb::connection::Connection;
use x11rb::errors::ReplyOrIdError;
use x11rb::properties::WmHints;
use x11rb::protocol::Event;
use x11rb::protocol::xproto::{
    AtomEnum, ChangeWindowAttributesAux, ClientMessageEvent, ConfigWindow, ConfigureRequestEvent, ConfigureWindowAux, ConnectionExt as _,
    CreateWindowAux, DestroyNotifyEvent, EventMask, InputFocus, PropMode, StackMode, UnmapNotifyEvent, WindowClass,
};
use x11rb::rust_connection::RustConnection;
use x11rb::wrapper::ConnectionExt as _;
use x11rb::{COPY_DEPTH_FROM_PARENT, COPY_FROM_PARENT, CURRENT_TIME, NONE};

use super::{Clients, Effect, ROOT_COLOR, Restack, WM_NAME, Window};
use crate::lane::channel::log;

x11rb::atom_manager! {
    Atoms: AtomsCookie {
        UTF8_STRING,
        WM_PROTOCOLS,
        WM_STATE,
        WM_TAKE_FOCUS,
        _NET_ACTIVE_WINDOW,
        _NET_CLIENT_LIST,
        _NET_FRAME_EXTENTS,
        _NET_REQUEST_FRAME_EXTENTS,
        _NET_SUPPORTED,
        _NET_SUPPORTING_WM_CHECK,
        _NET_WM_NAME,
    }
}

/// The `WM_STATE` of a mapped window, as ICCCM numbers it.
const NORMAL_STATE: u32 = 1;

/// Takes the window manager role on `display`, and answers the thread of its event loop. The setup is done when this
/// returns: the root has its color, and `_NET_SUPPORTING_WM_CHECK` names the check window. A display with another window
/// manager is refused, because only one client may select the substructure redirect of the root.
///
/// The loop ends when the connection ends, which is when Xvfb stops.
pub(crate) fn start(display: &str) -> Result<JoinHandle<()>, String> {
    let (conn, screen) = RustConnection::connect(Some(display)).map_err(|error| format!("cannot connect to {display}: {error}"))?;
    let root = conn.setup().roots[screen].root;
    let atoms = set_up(&conn, root).map_err(|error| format!("cannot take the window manager role on {display}: {error}"))?;
    thread::Builder::new()
        .name("window-manager".to_owned())
        .spawn(move || {
            let mut manager = Manager {
                conn,
                root,
                atoms,
                clients: Clients::default(),
            };
            if let Err(error) = manager.run() {
                log(&format!("the window manager of the lane display ends: {error}"));
            }
        })
        .map_err(|error| format!("cannot start the thread of the window manager: {error}"))
}

/// Selects the substructure redirect on `root`, paints the root, and sets the EWMH properties of the check window.
fn set_up(conn: &RustConnection, root: Window) -> Result<Atoms, ReplyOrIdError> {
    conn.change_window_attributes(
        root,
        &ChangeWindowAttributesAux::new()
            .event_mask(EventMask::SUBSTRUCTURE_REDIRECT | EventMask::SUBSTRUCTURE_NOTIFY)
            .background_pixel(ROOT_COLOR),
    )?
    .check()?;
    conn.clear_area(false, root, 0, 0, 0, 0)?;
    let atoms = Atoms::new(conn)?.reply()?;
    let check = conn.generate_id()?;
    conn.create_window(
        COPY_DEPTH_FROM_PARENT,
        check,
        root,
        -1,
        -1,
        1,
        1,
        0,
        WindowClass::INPUT_ONLY,
        COPY_FROM_PARENT,
        &CreateWindowAux::new().override_redirect(1),
    )?;
    for window in [root, check] {
        conn.change_property32(
            PropMode::REPLACE,
            window,
            atoms._NET_SUPPORTING_WM_CHECK,
            AtomEnum::WINDOW,
            &[check],
        )?;
    }
    conn.change_property8(PropMode::REPLACE, check, atoms._NET_WM_NAME, atoms.UTF8_STRING, WM_NAME.as_bytes())?;
    let supported = [
        atoms._NET_SUPPORTED,
        atoms._NET_SUPPORTING_WM_CHECK,
        atoms._NET_WM_NAME,
        atoms._NET_ACTIVE_WINDOW,
        atoms._NET_CLIENT_LIST,
        atoms._NET_FRAME_EXTENTS,
        atoms._NET_REQUEST_FRAME_EXTENTS,
    ];
    conn.change_property32(PropMode::REPLACE, root, atoms._NET_SUPPORTED, AtomEnum::ATOM, &supported)?;
    conn.change_property32(PropMode::REPLACE, root, atoms._NET_CLIENT_LIST, AtomEnum::WINDOW, &[])?;
    conn.change_property32(PropMode::REPLACE, root, atoms._NET_ACTIVE_WINDOW, AtomEnum::WINDOW, &[NONE])?;
    conn.sync()?;
    Ok(atoms)
}

struct Manager {
    conn: RustConnection,
    root: Window,
    atoms: Atoms,
    clients: Clients,
}

impl Manager {
    /// Serves the requests until the connection ends. An error of one request, such as a window that is gone, is a line
    /// of the log, and the loop goes on.
    fn run(&mut self) -> Result<(), ReplyOrIdError> {
        loop {
            let event = self.conn.wait_for_event()?;
            let effects = match event {
                Event::MapRequest(request) => {
                    let focusable = self.takes_focus(request.window);
                    self.clients.map(request.window, focusable)
                }
                Event::ConfigureRequest(request) => {
                    self.configure(&request)?;
                    Vec::new()
                }
                Event::ClientMessage(message) if message.type_ == self.atoms._NET_ACTIVE_WINDOW => self.clients.activate(message.window),
                Event::ClientMessage(message) if message.type_ == self.atoms._NET_REQUEST_FRAME_EXTENTS => {
                    self.zero_frame_extents(message.window)?;
                    Vec::new()
                }
                Event::UnmapNotify(UnmapNotifyEvent { window, .. }) | Event::DestroyNotify(DestroyNotifyEvent { window, .. }) => {
                    self.clients.gone(window)
                }
                Event::Error(error) => {
                    log(&format!("the window manager of the lane display got an X error: {error:?}"));
                    Vec::new()
                }
                _ => Vec::new(),
            };
            for effect in &effects {
                self.apply(effect)?;
            }
            self.conn.flush()?;
        }
    }

    /// Applies a configure request as the client asked, and keeps the order of the stack.
    fn configure(&mut self, request: &ConfigureRequestEvent) -> Result<(), ReplyOrIdError> {
        self.conn
            .configure_window(request.window, &ConfigureWindowAux::from_configure_request(request))?;
        if request.value_mask.contains(ConfigWindow::STACK_MODE) && !request.value_mask.contains(ConfigWindow::SIBLING) {
            match request.stack_mode {
                StackMode::ABOVE => self.clients.restack(request.window, Restack::Top),
                StackMode::BELOW => self.clients.restack(request.window, Restack::Bottom),
                _ => {}
            }
        }
        Ok(())
    }

    fn apply(&self, effect: &Effect) -> Result<(), ReplyOrIdError> {
        match *effect {
            Effect::Map(window) => {
                self.conn.change_property32(
                    PropMode::REPLACE,
                    window,
                    self.atoms.WM_STATE,
                    self.atoms.WM_STATE,
                    &[NORMAL_STATE, NONE],
                )?;
                self.zero_frame_extents(window)?;
                self.conn.map_window(window)?;
            }
            Effect::Raise(window) => {
                self.conn
                    .configure_window(window, &ConfigureWindowAux::new().stack_mode(StackMode::ABOVE))?;
            }
            Effect::Focus(window) => {
                if self.wants_input(window) {
                    self.conn.set_input_focus(InputFocus::PARENT, window, CURRENT_TIME)?;
                }
                if self.protocols(window).contains(&self.atoms.WM_TAKE_FOCUS) {
                    let message = ClientMessageEvent::new(
                        32,
                        window,
                        self.atoms.WM_PROTOCOLS,
                        [self.atoms.WM_TAKE_FOCUS, CURRENT_TIME, 0, 0, 0],
                    );
                    self.conn.send_event(false, window, EventMask::NO_EVENT, message)?;
                }
                self.conn.change_property32(
                    PropMode::REPLACE,
                    self.root,
                    self.atoms._NET_ACTIVE_WINDOW,
                    AtomEnum::WINDOW,
                    &[window],
                )?;
            }
            Effect::FocusRoot => {
                self.conn
                    .set_input_focus(InputFocus::POINTER_ROOT, InputFocus::POINTER_ROOT, CURRENT_TIME)?;
                self.conn.change_property32(
                    PropMode::REPLACE,
                    self.root,
                    self.atoms._NET_ACTIVE_WINDOW,
                    AtomEnum::WINDOW,
                    &[NONE],
                )?;
            }
            Effect::ClientList(ref windows) => {
                self.conn
                    .change_property32(PropMode::REPLACE, self.root, self.atoms._NET_CLIENT_LIST, AtomEnum::WINDOW, windows)?;
            }
        }
        Ok(())
    }

    fn zero_frame_extents(&self, window: Window) -> Result<(), ReplyOrIdError> {
        self.conn.change_property32(
            PropMode::REPLACE,
            window,
            self.atoms._NET_FRAME_EXTENTS,
            AtomEnum::CARDINAL,
            &[0; 4],
        )?;
        Ok(())
    }

    /// Whether `window` takes the input focus, by ICCCM: its `WM_HINTS` input is not false, or it asks for
    /// `WM_TAKE_FOCUS`.
    fn takes_focus(&self, window: Window) -> bool {
        self.wants_input(window) || self.protocols(window).contains(&self.atoms.WM_TAKE_FOCUS)
    }

    /// Whether the `WM_HINTS` of `window` leave the input on. A window without the hint takes the input.
    fn wants_input(&self, window: Window) -> bool {
        WmHints::get(&self.conn, window)
            .ok()
            .and_then(|cookie| cookie.reply().ok())
            .flatten()
            .and_then(|hints| hints.input)
            .unwrap_or(true)
    }

    /// The `WM_PROTOCOLS` of `window`. A window that is gone has none.
    fn protocols(&self, window: Window) -> Vec<u32> {
        self.conn
            .get_property(false, window, self.atoms.WM_PROTOCOLS, AtomEnum::ATOM, 0, 32)
            .ok()
            .and_then(|cookie| cookie.reply().ok())
            .and_then(|reply| reply.value32().map(Iterator::collect))
            .unwrap_or_default()
    }
}
