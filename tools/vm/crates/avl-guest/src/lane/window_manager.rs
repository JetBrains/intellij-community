//! The window manager of the X display that `lane-run` owns: the smallest one that the host probe and the driver need.
//!
//! The host probe reads `_NET_SUPPORTING_WM_CHECK` on the root window and on the window that it names. The driver raises
//! a frame with `toFront()` and activates it through the window manager, and a keyboard scenario needs the X input focus
//! on the frame. So the window manager takes the substructure redirect of the root, maps each window that asks, applies
//! each configure request as it comes, raises a window on a request to stack it above, and gives the input focus to a
//! window that it maps or that `_NET_ACTIVE_WINDOW` names. It does not reparent a window and draws no decoration. The
//! IDE gets `_JAVA_AWT_WM_NONREPARENTING`, so the JDK expects no frame window around its own.
//!
//! [`Clients`] holds the decisions, so a test reads them without an X server. The X11 half lives in `x11`, on Linux.

#[cfg(target_os = "linux")]
pub(crate) mod x11;

#[cfg(test)]
mod tests;

/// The `_NET_WM_NAME` of the window manager, on the window that `_NET_SUPPORTING_WM_CHECK` names.
pub(crate) const WM_NAME: &str = "air-lane-wm";
/// The color of the root window, a neutral dark gray, as the 24-bit pixel of a TrueColor screen.
#[cfg(target_os = "linux")]
pub(crate) const ROOT_COLOR: u32 = 0x002b_2d30;
/// The variable that tells the JDK that the window manager does not reparent its windows.
pub(crate) const NONREPARENTING_VARIABLE: &str = "_JAVA_AWT_WM_NONREPARENTING";

/// An X window id.
#[cfg(any(target_os = "linux", test))]
pub(crate) type Window = u32;

/// How a configure request restacks its window, when it names no sibling.
#[cfg(any(target_os = "linux", test))]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Restack {
    /// Above every other window: `XRaiseWindow`, as `toFront()` sends it.
    Top,
    /// Below every other window.
    Bottom,
}

/// One change that the window manager makes on the X server.
#[cfg(any(target_os = "linux", test))]
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Effect {
    /// Map the window, and set its `WM_STATE` to normal and its `_NET_FRAME_EXTENTS` to zero.
    Map(Window),
    /// Stack the window above every other window.
    Raise(Window),
    /// Give the window the input focus, and name it in `_NET_ACTIVE_WINDOW`.
    Focus(Window),
    /// Give the input focus to the root, and name no window in `_NET_ACTIVE_WINDOW`.
    FocusRoot,
    /// Set `_NET_CLIENT_LIST` to these windows, in the order of their map.
    ClientList(Vec<Window>),
}

/// One managed window.
#[cfg(any(target_os = "linux", test))]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct Client {
    window: Window,
    /// Whether the window takes the input focus: its `WM_HINTS` input is not false, or it asks for `WM_TAKE_FOCUS`.
    focusable: bool,
}

/// The windows that the window manager mapped, and the decisions about them.
#[cfg(any(target_os = "linux", test))]
#[derive(Debug, Default)]
pub(crate) struct Clients {
    /// The managed windows in the order of their map.
    mapped: Vec<Client>,
    /// The managed windows from the bottom to the top.
    stack: Vec<Window>,
    focus: Option<Window>,
}

#[cfg(any(target_os = "linux", test))]
impl Clients {
    /// A map request of `window`: map it on top of the others, and give it the focus when it takes one.
    pub(crate) fn map(&mut self, window: Window, focusable: bool) -> Vec<Effect> {
        if !self.is_managed(window) {
            self.mapped.push(Client { window, focusable });
        }
        self.raise_in_stack(window);
        let mut effects = vec![Effect::Map(window), Effect::Raise(window), self.client_list()];
        if focusable {
            self.focus = Some(window);
            effects.push(Effect::Focus(window));
        }
        effects
    }

    /// A configure request of `window` that restacks it as `restack`. The X11 half applies the request itself, so this
    /// only keeps the order of the stack.
    pub(crate) fn restack(&mut self, window: Window, restack: Restack) {
        if !self.is_managed(window) {
            return;
        }
        match restack {
            Restack::Top => self.raise_in_stack(window),
            Restack::Bottom => {
                self.stack.retain(|stacked| *stacked != window);
                self.stack.insert(0, window);
            }
        }
    }

    /// A `_NET_ACTIVE_WINDOW` message for `window`: raise it, and give it the focus when it takes one. A window that the
    /// window manager does not manage changes nothing.
    pub(crate) fn activate(&mut self, window: Window) -> Vec<Effect> {
        let Some(client) = self.mapped.iter().find(|client| client.window == window).copied() else {
            return Vec::new();
        };
        self.raise_in_stack(window);
        let mut effects = vec![Effect::Raise(window)];
        if client.focusable {
            self.focus = Some(window);
            effects.push(Effect::Focus(window));
        }
        effects
    }

    /// An unmap or a destroy of `window`. When it had the focus, the topmost window that takes the focus gets it, and
    /// without one the root does. So a closed dialog gives the focus back to the frame below it.
    pub(crate) fn gone(&mut self, window: Window) -> Vec<Effect> {
        if !self.is_managed(window) {
            return Vec::new();
        }
        self.mapped.retain(|client| client.window != window);
        self.stack.retain(|stacked| *stacked != window);
        let mut effects = vec![self.client_list()];
        if self.focus == Some(window) {
            let next = self
                .stack
                .iter()
                .rev()
                .copied()
                .find(|stacked| self.mapped.iter().any(|client| client.window == *stacked && client.focusable));
            self.focus = next;
            effects.push(next.map_or(Effect::FocusRoot, Effect::Focus));
        }
        effects
    }

    /// The window that has the focus, as the window manager gave it.
    #[cfg(test)]
    pub(crate) const fn focus(&self) -> Option<Window> {
        self.focus
    }

    fn is_managed(&self, window: Window) -> bool {
        self.mapped.iter().any(|client| client.window == window)
    }

    fn raise_in_stack(&mut self, window: Window) {
        self.stack.retain(|stacked| *stacked != window);
        self.stack.push(window);
    }

    fn client_list(&self) -> Effect {
        Effect::ClientList(self.mapped.iter().map(|client| client.window).collect())
    }
}
