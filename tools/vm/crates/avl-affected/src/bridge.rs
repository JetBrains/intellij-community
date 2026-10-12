//! Where BT's crates meet the controller: the Air area, and a refusal of the shared `refusal` crate as the
//! controller's.
//!
//! BT builds in the community module, with its own builds of `serde` and `serde_json`, so a value of one side is no
//! value of the other. The refusal details cross as JSON text for that reason.
//!
//! The Air area comes from `bt.json` of the checkout at run time, as `bt` reads it. Each program calls [`install`]
//! once, before it resolves a selector or prints the lanes. A test calls [`install_fixture`], which installs the copy
//! of the Air lane table that `bt_core::fake` holds.

use std::path::Path;
use std::sync::OnceLock;

use avl_base::{Exit, Refusal};
use bt_core::OsRuntime;
use bt_core::areas::{AREAS_FILE, Area, Areas};
use bt_core::lanes::Lanes;

/// The directory of the Air area.
pub const AIR_DIR: &str = "plugins/air";

/// The Air lane table, relative to the repository root.
pub const AIR_LANES_FILE: &str = "plugins/air/tests/integration/lanes.json";

/// The Air area alone, set once by [`install`] or [`install_areas`].
static AREAS: OnceLock<&'static Areas> = OnceLock::new();

/// Reads `bt.json` of the checkout at `root`, and installs the Air area that it names. When the areas are installed
/// already, it reads nothing.
///
/// `bt.json` and every lane table that it names must be valid, as `bt` requires. The installed areas hold the Air area
/// alone, because the controller resolves only in the Air area.
///
/// # Errors
///
/// A BT refusal when `bt.json` or a lane table that it names cannot be read, and `air_area_missing` when `bt.json`
/// names no Air area.
pub fn install(root: &Path) -> Result<(), Refusal> {
    if AREAS.get().is_some() {
        return Ok(());
    }
    let runtime = OsRuntime::resolver(root, |_| {});
    let areas = Areas::load(&runtime).map_err(refusal)?;
    let lanes_file = air_area_of(&areas)?.lanes_file().to_owned();
    let lanes = Lanes::load(&runtime, &lanes_file).map_err(refusal)?;
    install_areas(Box::leak(Box::new(Areas::new(vec![Area::new(AIR_DIR, lanes_file, lanes)]))))
}

/// Installs `areas`, which hold the Air area. The first call wins, and a later call keeps the areas of the first.
///
/// # Errors
///
/// `air_area_missing` when `areas` has no area with the directory [`AIR_DIR`].
pub fn install_areas(areas: &'static Areas) -> Result<(), Refusal> {
    air_area_of(areas)?;
    AREAS.get_or_init(|| areas);
    Ok(())
}

/// The area with the directory [`AIR_DIR`], or the refusal `air_area_missing`.
fn air_area_of(areas: &Areas) -> Result<&Area, Refusal> {
    areas.iter().find(|area| area.dir() == AIR_DIR).ok_or_else(|| {
        Refusal::new(
            "air_area_missing",
            Exit::DATA_ERR,
            format!("`<root>/{AREAS_FILE}` names no area `{AIR_DIR}`; the controller runs in an ultimate checkout"),
        )
    })
}

/// Installs the fixture areas of `bt_core::fake`, a copy of the Air lane table. For tests only, and a second call
/// changes nothing.
///
/// # Panics
///
/// When the fixture has no Air area, which a unit test rules out.
pub fn install_fixture() {
    install_areas(bt_core::fake::areas()).expect("the fixture areas hold the Air area");
}

/// The areas of the checkout, for the selector resolution of the controller and the trace planner.
///
/// # Panics
///
/// When no program installed the areas: call [`install`] first.
pub fn air_areas() -> &'static Areas {
    AREAS.get().expect("call `avl_affected::bridge::install` first")
}

/// The Air area.
///
/// # Panics
///
/// When no program installed the areas: call [`install`] first.
pub fn air_area() -> &'static Area {
    air_area_of(air_areas()).expect("`install_areas` checks the Air area")
}

/// A BT exit code as the controller's [`Exit`] of the same meaning.
///
/// The two tools number their outcomes differently, so the map goes by the name and never by the number: BT's 6 is
/// an infrastructure failure, and the controller's 6 is a red lane. A BT infrastructure refusal of the crates that the
/// controller links is a repository document that BT cannot read: the lane table, a suite catalog, a `BUILD.bazel`
/// with two unfiltered tests. That is the controller's [`Exit::DATA_ERR`]. Zero tests is a red lane for the
/// controller, as its own `no_tests_discovered` is. A number that BT does not name is a plain failure.
pub const fn exit(code: u8) -> Exit {
    match code {
        bt_core::exit::GREEN => Exit::OK,
        bt_core::exit::USAGE => Exit::USAGE,
        bt_core::exit::TEST_FAILED | bt_core::exit::NO_TESTS => Exit::TESTS_FAILED,
        bt_core::exit::BUILD_FAILED => Exit::BUILD_FAILED,
        bt_core::exit::INFRA => Exit::DATA_ERR,
        _ => Exit::FAILURE,
    }
}

/// A refusal of BT's crates as the controller's, with the code, the message and the details kept, and the exit
/// mapped by [`exit`].
///
/// A function and not a `From`: both types are foreign to this crate, so Rust's orphan rule forbids the impl here.
pub fn refusal(refused: refusal::Refusal) -> Refusal {
    let details = refused.details_json_text().map(str::to_owned);
    let converted = Refusal::new(refused.code, exit(refused.exit), refused.message);
    match details {
        Some(text) => converted.with_details_json_text(text),
        None => converted,
    }
}

#[cfg(test)]
mod tests;
