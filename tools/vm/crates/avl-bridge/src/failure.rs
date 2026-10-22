//! A failure inside the IDE, as the bridge answers it.

use std::fmt;

use serde_json::Value;

#[cfg(test)]
mod tests;

/// One throwable of the IDE and its cause chain: `AirUiTestFailure` of `AirUiTestHttpProtocol.kt`, the `failure` of
/// each answer of the request endpoint that did not succeed.
#[derive(Clone, PartialEq, Eq, Debug)]
pub struct BridgeFailure {
    /// The class name of the throwable, the field `type`.
    pub type_name: String,
    pub message: Option<String>,
    /// The frames of the stack, outermost first, as `StackTraceElement.toString` writes them.
    pub stack: Vec<String>,
    pub cause: Option<Box<Self>>,
    /// Whether the body aborted its scenario: an `AirUiTestAbortException`, the field `aborted`. The bridge writes the
    /// field only when it is true.
    pub aborted: bool,
}

impl BridgeFailure {
    /// Decodes a failure `{type, message, stack, cause, aborted}`. A field the bridge adds later is ignored. A missing
    /// `message`, `stack`, `cause` or `aborted` is the Kotlin default: no message, no frames, no cause, no abort.
    pub fn decode(value: &Value) -> Result<Self, String> {
        let Value::Object(fields) = value else {
            return Err(format!("the failure {value} is not an object"));
        };
        let type_name = match fields.get("type") {
            Some(Value::String(type_name)) => type_name.clone(),
            _ => return Err(format!("the failure {value} has no text `type`")),
        };
        let message = match fields.get("message") {
            None | Some(Value::Null) => None,
            Some(Value::String(message)) => Some(message.clone()),
            Some(other) => return Err(format!("the `message` {other} of the failure {type_name} is not a text")),
        };
        let stack = match fields.get("stack") {
            None => Vec::new(),
            Some(Value::Array(frames)) => frames
                .iter()
                .map(|frame| frame.as_str().map(str::to_owned))
                .collect::<Option<Vec<_>>>()
                .ok_or_else(|| format!("the `stack` of the failure {type_name} holds a frame that is not a text"))?,
            Some(other) => return Err(format!("the `stack` {other} of the failure {type_name} is not a list")),
        };
        let cause = match fields.get("cause") {
            None | Some(Value::Null) => None,
            Some(cause) => Some(Box::new(Self::decode(cause)?)),
        };
        let aborted = match fields.get("aborted") {
            None | Some(Value::Null) => false,
            Some(Value::Bool(aborted)) => *aborted,
            Some(other) => return Err(format!("the `aborted` {other} of the failure {type_name} is not a boolean")),
        };
        Ok(Self {
            type_name,
            message,
            stack,
            cause,
            aborted,
        })
    }
}

/// The chain as `AirUiTestFailure.describe` writes it: the outermost failure first, its frames below it, and each
/// cause under a `Caused by: ` line.
impl fmt::Display for BridgeFailure {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        let mut failure = Some(self);
        let mut prefix = "";
        while let Some(current) = failure {
            write!(formatter, "{prefix}{}", current.type_name)?;
            if let Some(message) = &current.message {
                write!(formatter, ": {message}")?;
            }
            for frame in &current.stack {
                write!(formatter, "\n\tat {frame}")?;
            }
            prefix = "\nCaused by: ";
            failure = current.cause.as_deref();
        }
        Ok(())
    }
}
