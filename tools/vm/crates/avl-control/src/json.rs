//! The one JSON read that the proxy and the fake agent share beyond `serde_json`.

use std::collections::HashMap;

use serde_json::value::RawValue;

/// The text of the member `name` of the JSON object `text`, as the object spells it, so its member order survives.
pub fn raw_member(text: &str, name: &str) -> Option<String> {
    let mut members = serde_json::from_str::<HashMap<String, Box<RawValue>>>(text).ok()?;
    members.remove(name).map(|raw| raw.get().to_owned())
}
