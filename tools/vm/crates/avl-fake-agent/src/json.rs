//! The JSON texts that the agent writes by hand, so that each frame keeps the member order of the protocol.

use serde_json::Value;

/// `text` as one JSON string.
pub(crate) fn quote(text: &str) -> String {
    serde_json::to_string(text).unwrap_or_default()
}

/// The JSON array of the strings `items`.
pub(crate) fn quote_all<'a>(items: impl IntoIterator<Item = &'a str>) -> String {
    let quoted: Vec<String> = items.into_iter().map(quote).collect();
    format!("[{}]", quoted.join(","))
}

/// The JSON object of the line `text`, or the reason why the line is no JSON object.
pub(crate) fn parse_object(text: &str) -> Result<Value, String> {
    match serde_json::from_str::<Value>(text) {
        Ok(value) if value.is_object() => Ok(value),
        Ok(_) => Err("the frame is no JSON object".to_owned()),
        Err(error) => Err(error.to_string()),
    }
}

/// A JSON-RPC id as a response repeats it: a string or a number as the request has it, and `null` for any other value.
pub(crate) fn format_id(id: Option<&Value>) -> String {
    match id {
        Some(Value::String(text)) => quote(text),
        Some(Value::Number(number)) => number.to_string(),
        Some(Value::Bool(flag)) => flag.to_string(),
        _ => "null".to_owned(),
    }
}
