//! The declarations of one Kotlin source file, read from its tokens alone.
//!
//! The reader knows the shape of a declaration and nothing of types. It splits a file into the declarations of the
//! file and of each class body, and gives each one the names that it mentions, a fingerprint of its tokens and the
//! facts that the reach reads: the modifiers, the supertypes and a `@SerialName`. A comment is no token, so a change of
//! a comment or a KDoc changes no fingerprint.
//!
//! # What is a declaration
//!
//! A declaration starts on a new line of a file or a class body, with its annotations and modifiers, and runs to the
//! start of the next one. A function, a property, a type and a type alias are each one declaration. Each member of a
//! class body is a declaration of its own, and the body of a function belongs to the function. An `init` block and a
//! secondary constructor belong to their class. Two overloads of one name are one declaration.
//!
//! # A suite-keyed entry
//!
//! A string literal `"<suite>/…"` followed by `->` or `to` starts an entry of the suite `<suite>`: a branch of a
//! dispatching `when`, or a pair of a map. The entry runs to its block end, or to the next `,` or line on its own level.
//! It is not a part of its declaration: it has its own names and fingerprint, so a new branch of one suite changes
//! neither the dispatcher nor the entries of other suites. A key that names no suite stays a part of the declaration.

use std::collections::hash_map::DefaultHasher;
use std::collections::{BTreeMap, BTreeSet, HashSet};
use std::hash::{Hash, Hasher};

/// The modifiers a declaration can carry. A modifier is a declaration start only when a declaration keyword follows.
const MODIFIERS: &[&str] = &[
    "public",
    "private",
    "internal",
    "protected",
    "open",
    "abstract",
    "final",
    "sealed",
    "data",
    "enum",
    "annotation",
    "inner",
    "value",
    "override",
    "const",
    "lateinit",
    "inline",
    "suspend",
    "tailrec",
    "operator",
    "infix",
    "external",
    "actual",
    "expect",
    "companion",
];

/// The hard keywords of Kotlin, which name no declaration.
const KEYWORDS: &[&str] = &[
    "as",
    "break",
    "class",
    "continue",
    "do",
    "else",
    "false",
    "for",
    "fun",
    "if",
    "in",
    "interface",
    "is",
    "null",
    "object",
    "package",
    "return",
    "super",
    "this",
    "throw",
    "true",
    "try",
    "typealias",
    "typeof",
    "val",
    "var",
    "when",
    "while",
];

/// The use-site targets of an annotation, `@get:Rule`.
const ANNOTATION_TARGETS: &[&str] = &[
    "file", "get", "set", "field", "param", "property", "receiver", "delegate", "setparam",
];

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
enum TokenKind {
    Identifier,
    /// The text of a string literal, without its quotes. The expressions of its templates are tokens of their own.
    Text,
    Other,
}

#[derive(Clone, Copy, Debug)]
struct Token<'a> {
    kind: TokenKind,
    text: &'a str,
    /// Whether the token is the first one of its line.
    line_start: bool,
}

impl Token<'_> {
    fn is(&self, text: &str) -> bool {
        self.kind != TokenKind::Text && self.text == text
    }

    fn is_identifier(&self) -> bool {
        self.kind == TokenKind::Identifier
    }
}

/// What a declaration is.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Kind {
    Function,
    Property,
    Class,
    Interface,
    Object,
    TypeAlias,
}

impl Kind {
    pub(crate) const fn is_type(self) -> bool {
        matches!(self, Self::Class | Self::Interface | Self::Object)
    }
}

/// One declaration of a file.
#[derive(Clone, Debug)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "each flag is one modifier or fact that the source states on its own"
)]
pub(crate) struct Declaration {
    pub name: String,
    /// The names of its containers and its own, joined by dots: `AirUiTestBridge.openProject`.
    pub path: String,
    pub kind: Kind,
    /// The index of the type whose body holds it, in the same file.
    pub container: Option<usize>,
    pub private: bool,
    pub overrides: bool,
    pub operator: bool,
    pub companion: bool,
    pub sealed: bool,
    /// Whether a property runs code when its type is created: its initializer or its delegate calls a function or
    /// holds a lambda. A constant, a plain value and a getter run nothing then.
    pub runs_on_creation: bool,
    /// The simple names of the supertypes of a type.
    pub supertypes: Vec<String>,
    /// The value of its `@SerialName`.
    pub serial_name: Option<String>,
    /// Every name that it mentions, except its own name, a parameter, a local and a named argument.
    pub references: BTreeSet<String>,
    pub fingerprint: u64,
    /// Its suite-keyed entries, by suite.
    pub entries: BTreeMap<String, Entry>,
}

/// One suite-keyed entry of a declaration.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Entry {
    pub references: BTreeSet<String>,
    pub fingerprint: u64,
    /// Whether the entry is a pair of a map, `"<suite>/…" to <value>`, which the map creates for every user.
    pub pair: bool,
}

/// The declarations of one file, and the simple names that its imports bring in.
#[derive(Clone, Debug, Default)]
pub(crate) struct Source {
    pub declarations: Vec<Declaration>,
    /// Each import as written, with its alias.
    pub imports: BTreeSet<String>,
    /// The fingerprint of every token of the file, its package and its imports too.
    pub fingerprint: u64,
}

impl Source {
    /// The declaration with that path.
    pub(crate) fn find(&self, path: &str) -> Option<usize> {
        self.declarations.iter().position(|declaration| declaration.path == path)
    }
}

/// The simple name that an import line brings in: its alias, or its last segment.
pub(crate) fn imported_name(import: &str) -> &str {
    import
        .rsplit_once(" as ")
        .map_or_else(|| import.rsplit('.').next().unwrap_or(import), |(_, alias)| alias)
}

/// Reads the declarations of a file. `is_suite` tells a suite id from any other key of a string-keyed entry.
pub(crate) fn read(text: &str, is_suite: &dyn Fn(&str) -> bool) -> Source {
    let mut tokens = Vec::new();
    lex(text, &mut tokens, true);
    let mut hasher = DefaultHasher::new();
    for token in &tokens {
        token.kind.hash(&mut hasher);
        token.text.hash(&mut hasher);
    }
    let mut body = Vec::with_capacity(tokens.len());
    let mut imports = BTreeSet::new();
    let mut index = 0;
    while index < tokens.len() {
        let token = tokens[index];
        if token.line_start && (token.is("package") || token.is("import")) {
            let start = index + 1;
            index += 1;
            while index < tokens.len() && !tokens[index].line_start {
                index += 1;
            }
            if token.is("import") {
                imports.insert(import_text(&tokens[start..index]));
            }
            continue;
        }
        body.push(token);
        index += 1;
    }
    let mut reader = Reader {
        tokens: &body,
        is_suite,
        declarations: Vec::new(),
    };
    reader.container(0, body.len(), "", None);
    Source {
        declarations: reader.declarations,
        imports,
        fingerprint: hasher.finish(),
    }
}

fn import_text(tokens: &[Token<'_>]) -> String {
    let mut text = String::new();
    for token in tokens {
        if token.is("as") {
            text.push_str(" as ");
        } else {
            text.push_str(token.text);
        }
    }
    text
}

/// Splits `text` into tokens. A comment is dropped. A string literal is one [`TokenKind::Text`] token, and the
/// expressions of its templates are lexed as tokens before it.
fn lex<'a>(text: &'a str, out: &mut Vec<Token<'a>>, first_line_start: bool) {
    let bytes = text.as_bytes();
    let mut index = 0;
    let mut line_start = first_line_start;
    let push = |out: &mut Vec<Token<'a>>, kind: TokenKind, token: &'a str, line_start: &mut bool| {
        out.push(Token {
            kind,
            text: token,
            line_start: *line_start,
        });
        *line_start = false;
    };
    while index < bytes.len() {
        let byte = bytes[index];
        match byte {
            b'\n' => {
                line_start = true;
                index += 1;
            }
            b' ' | b'\t' | b'\r' => index += 1,
            b'/' if bytes.get(index + 1) == Some(&b'/') => {
                while index < bytes.len() && bytes[index] != b'\n' {
                    index += 1;
                }
            }
            b'/' if bytes.get(index + 1) == Some(&b'*') => {
                // A Kotlin block comment nests.
                let mut depth = 0;
                while index < bytes.len() {
                    if bytes[index..].starts_with(b"/*") {
                        depth += 1;
                        index += 2;
                    } else if bytes[index..].starts_with(b"*/") {
                        depth -= 1;
                        index += 2;
                        if depth == 0 {
                            break;
                        }
                    } else {
                        index += 1;
                    }
                }
            }
            b'"' => {
                let (content, end) = string_literal(text, index, out);
                push(out, TokenKind::Text, content, &mut line_start);
                index = end;
            }
            b'\'' => {
                let mut end = index + 1;
                while end < bytes.len() && bytes[end] != b'\'' && bytes[end] != b'\n' {
                    end += if bytes[end] == b'\\' { 2 } else { 1 };
                }
                let end = (end + 1).min(bytes.len());
                push(out, TokenKind::Other, &text[index..end], &mut line_start);
                index = end;
            }
            b'`' => {
                let end = text[index + 1..].find(['`', '\n']).map_or(bytes.len(), |offset| index + 1 + offset);
                push(out, TokenKind::Identifier, &text[index + 1..end], &mut line_start);
                index = (end + 1).min(bytes.len());
            }
            _ if is_identifier_start(byte) => {
                let end = identifier_end(bytes, index);
                push(out, TokenKind::Identifier, &text[index..end], &mut line_start);
                index = end;
            }
            b'0'..=b'9' => {
                let mut end = index;
                while end < bytes.len() && (bytes[end].is_ascii_alphanumeric() || bytes[end] == b'_' || bytes[end] == b'.') {
                    end += 1;
                }
                push(out, TokenKind::Other, &text[index..end], &mut line_start);
                index = end;
            }
            _ => {
                let pair = bytes.get(index..index + 2);
                let width = if matches!(pair, Some(b"->" | b"::" | b"?.")) {
                    2
                } else {
                    // A byte of a multibyte character outside an identifier is not ASCII: keep the character whole.
                    text[index..].chars().next().map_or(1, char::len_utf8)
                };
                push(out, TokenKind::Other, &text[index..index + width], &mut line_start);
                // A semicolon ends a statement as a line break does, so `class A; class B` holds two declarations.
                line_start = bytes[index] == b';';
                index += width;
            }
        }
    }
}

const fn is_identifier_start(byte: u8) -> bool {
    byte.is_ascii_alphabetic() || byte == b'_' || byte >= 0x80
}

const fn identifier_end(bytes: &[u8], start: usize) -> usize {
    let mut end = start;
    while end < bytes.len() && (is_identifier_start(bytes[end]) || bytes[end].is_ascii_digit()) {
        end += 1;
    }
    end
}

/// The content of the string literal that starts at `start`, and the index after it. The expression of each template
/// goes to `out` as tokens, and a `$name` template as an identifier.
fn string_literal<'a>(text: &'a str, start: usize, out: &mut Vec<Token<'a>>) -> (&'a str, usize) {
    let bytes = text.as_bytes();
    let raw = bytes[start..].starts_with(b"\"\"\"");
    let quote = if raw { 3 } else { 1 };
    let content_start = start + quote;
    let mut index = content_start;
    while index < bytes.len() {
        let byte = bytes[index];
        if !raw && byte == b'\\' {
            index += 2;
            continue;
        }
        if !raw && byte == b'\n' {
            // An unterminated literal ends at its line, so one typo does not swallow the file.
            return (&text[content_start..index], index);
        }
        if raw && bytes[index..].starts_with(b"\"\"\"") {
            let mut end = index + 3;
            // A raw literal may end with more quotes than three, and the extra ones are its content.
            while bytes.get(end) == Some(&b'"') {
                end += 1;
            }
            return (&text[content_start..end - 3], end);
        }
        if !raw && byte == b'"' {
            return (&text[content_start..index], index + 1);
        }
        if byte == b'$' && bytes.get(index + 1) == Some(&b'{') {
            let mut depth = 1;
            let mut end = index + 2;
            while end < bytes.len() && depth > 0 {
                match bytes[end] {
                    b'{' => depth += 1,
                    b'}' => depth -= 1,
                    _ => {}
                }
                end += 1;
            }
            let inner_end = if depth == 0 { end - 1 } else { end };
            lex(&text[index + 2..inner_end], out, false);
            index = end;
            continue;
        }
        if byte == b'$' && bytes.get(index + 1).is_some_and(|&next| is_identifier_start(next)) {
            let end = identifier_end(bytes, index + 1);
            out.push(Token {
                kind: TokenKind::Identifier,
                text: &text[index + 1..end],
                line_start: false,
            });
            index = end;
            continue;
        }
        index += 1;
    }
    (&text[content_start..], bytes.len())
}

struct Reader<'t, 'a> {
    tokens: &'t [Token<'a>],
    is_suite: &'t dyn Fn(&str) -> bool,
    declarations: Vec<Declaration>,
}

/// One declaration of a container: its tokens are `start..end`, and `keyword` is the index of its keyword.
struct Item {
    start: usize,
    end: usize,
    keyword: usize,
}

impl Reader<'_, '_> {
    fn token(&self, index: usize) -> Option<&Token<'_>> {
        self.tokens.get(index)
    }

    fn is_at(&self, index: usize, text: &str) -> bool {
        self.token(index).is_some_and(|token| token.is(text))
    }

    /// The index after the bracket that closes the one at `start`, or `end` when it is not closed before.
    fn after_balanced(&self, start: usize, end: usize) -> usize {
        let (open, close) = match self.tokens[start].text {
            "(" => ("(", ")"),
            "[" => ("[", "]"),
            "{" => ("{", "}"),
            _ => ("<", ">"),
        };
        let mut depth = 0;
        let mut index = start;
        while index < end {
            let token = &self.tokens[index];
            if token.is(open) {
                depth += 1;
            } else if token.is(close) {
                depth -= 1;
                if depth == 0 {
                    return index + 1;
                }
            } else if open == "<" && (token.is("{") || token.is("=") || token.is(";")) {
                // A `<` that opens no type parameter list is a comparison: give up on it.
                return start + 1;
            }
            index += 1;
        }
        end
    }

    /// The keyword of the declaration that starts at `start`, or `None` when no declaration starts there: the
    /// annotations and modifiers must lead to a declaration keyword.
    fn declaration_keyword(&self, start: usize, end: usize) -> Option<usize> {
        let mut index = start;
        let mut companion = false;
        while index < end {
            let token = &self.tokens[index];
            if token.is("@") {
                index += 1;
                if self.token(index).is_some_and(|target| ANNOTATION_TARGETS.contains(&target.text)) && self.is_at(index + 1, ":") {
                    index += 2;
                }
                while self.token(index).is_some_and(Token::is_identifier) {
                    index += 1;
                    if self.is_at(index, ".") {
                        index += 1;
                    } else {
                        break;
                    }
                }
                if self.is_at(index, "<") {
                    index = self.after_balanced(index, end);
                }
                if self.is_at(index, "(") && !self.tokens[index].line_start {
                    index = self.after_balanced(index, end);
                }
                continue;
            }
            if !token.is_identifier() {
                return None;
            }
            let next = self.token(index + 1).map_or("", |next| next.text);
            match token.text {
                // An anonymous object or function in an expression is no declaration, and a companion object needs no name.
                "object" if !companion && (next == ":" || next == "{") => return None,
                "fun" if next == "(" => return None,
                "fun" if next == "interface" => index += 1,
                "constructor" if next != "(" => return None,
                // A context parameter list leads a declaration as an annotation does.
                "context" if next == "(" => index = self.after_balanced(index + 1, end),
                "fun" | "val" | "var" | "class" | "interface" | "object" | "typealias" | "init" | "constructor" => {
                    return Some(index);
                }
                text if MODIFIERS.contains(&text) => {
                    companion |= text == "companion";
                    index += 1;
                }
                _ => return None,
            }
        }
        None
    }

    /// Reads the declarations of the file or of a class body, `start..end`, and answers the indexes of the tokens
    /// before its first declaration: the enum entries of a body.
    fn container(&mut self, start: usize, end: usize, prefix: &str, owner: Option<usize>) -> Vec<usize> {
        let mut items: Vec<Item> = Vec::new();
        let mut preamble = Vec::new();
        let mut depth = 0i32;
        let mut current: Option<(usize, usize)> = None;
        for index in start..end {
            let token = &self.tokens[index];
            if depth == 0
                && token.line_start
                && current.is_none_or(|(_, keyword)| index > keyword)
                && let Some(keyword) = self.declaration_keyword(index, end)
            {
                if let Some((item_start, item_keyword)) = current {
                    items.push(Item {
                        start: item_start,
                        end: index,
                        keyword: item_keyword,
                    });
                }
                current = Some((index, keyword));
            }
            if token.kind == TokenKind::Other {
                match token.text {
                    "(" | "[" | "{" => depth += 1,
                    ")" | "]" | "}" => depth -= 1,
                    _ => {}
                }
            }
            if current.is_none() {
                preamble.push(index);
            }
        }
        if let Some((item_start, keyword)) = current {
            items.push(Item {
                start: item_start,
                end,
                keyword,
            });
        }
        for item in items {
            self.item(&item, prefix, owner);
        }
        preamble
    }

    fn item(&mut self, item: &Item, prefix: &str, owner: Option<usize>) {
        let keyword = self.tokens[item.keyword].text;
        if keyword == "init" || keyword == "constructor" {
            // Both run when the class is created, so they are a part of the class.
            if let Some(owner) = owner {
                let indexes: Vec<usize> = (item.start..item.end).collect();
                let references = self.references(&indexes, None);
                let fingerprint = self.fingerprint(&indexes);
                let declaration = &mut self.declarations[owner];
                declaration.references.extend(references);
                declaration.fingerprint = combine(declaration.fingerprint, fingerprint);
            }
            return;
        }
        let kind = match keyword {
            "fun" => Kind::Function,
            "val" | "var" => Kind::Property,
            "class" => Kind::Class,
            "interface" => Kind::Interface,
            "object" => Kind::Object,
            _ => Kind::TypeAlias,
        };
        let Some((name, name_index)) = self.name(kind, item) else {
            return;
        };
        let modifiers: HashSet<&str> = (item.start..item.keyword)
            .map(|index| &self.tokens[index])
            .filter(|token| token.is_identifier())
            .map(|token| token.text)
            .collect();
        let serial_name = (item.start..item.keyword).find_map(|index| {
            (self.tokens[index].is("SerialName") && self.is_at(index + 1, "("))
                .then(|| self.token(index + 2))
                .flatten()
                .filter(|token| token.kind == TokenKind::Text)
                .map(|token| token.text.to_owned())
        });
        let path = format!("{prefix}{name}");
        let index = if let Some(known) = self.declarations.iter().position(|known| known.path == path) {
            known
        } else {
            self.declarations.push(Declaration {
                name,
                path: path.clone(),
                kind,
                container: owner,
                private: false,
                overrides: false,
                operator: false,
                companion: false,
                sealed: false,
                runs_on_creation: false,
                supertypes: Vec::new(),
                serial_name: None,
                references: BTreeSet::new(),
                fingerprint: 0,
                entries: BTreeMap::new(),
            });
            self.declarations.len() - 1
        };
        {
            let declaration = &mut self.declarations[index];
            declaration.private |= modifiers.contains("private");
            declaration.overrides |= modifiers.contains("override");
            declaration.operator |= modifiers.contains("operator");
            declaration.companion |= modifiers.contains("companion");
            declaration.sealed |= modifiers.contains("sealed");
            if serial_name.is_some() {
                declaration.serial_name = serial_name;
            }
        }
        if kind.is_type() {
            self.type_item(item, index, name_index, &path);
        } else {
            self.member_item(item, index, name_index);
        }
    }

    /// The name of a declaration and the index of its token. A function is named by the identifier before its first
    /// `(`, so a receiver type is skipped. A property is named by the last identifier before its type, its initializer
    /// or its delegate. A companion object without a name is `Companion`.
    fn name(&self, kind: Kind, item: &Item) -> Option<(String, usize)> {
        let mut index = item.keyword + 1;
        if kind.is_type() || kind == Kind::TypeAlias {
            return match self.token(index) {
                Some(token) if token.is_identifier() && index < item.end => Some((token.text.to_owned(), index)),
                _ if kind == Kind::Object => Some(("Companion".to_owned(), item.keyword)),
                _ => None,
            };
        }
        if self.is_at(index, "<") {
            index = self.after_balanced(index, item.end);
        }
        let mut last = None;
        while index < item.end {
            let token = &self.tokens[index];
            if index > item.keyword + 1 && token.line_start {
                break;
            }
            match kind {
                Kind::Function if token.is("(") => break,
                Kind::Property if token.is(":") || token.is("=") || token.is("by") => break,
                Kind::Property if (token.is("get") || token.is("set")) && self.is_at(index + 1, "(") => break,
                _ => {}
            }
            if token.is("<") {
                index = self.after_balanced(index, item.end);
                continue;
            }
            if token.is_identifier() {
                last = Some(index);
            }
            index += 1;
        }
        last.map(|index| (self.tokens[index].text.to_owned(), index))
    }

    fn type_item(&mut self, item: &Item, index: usize, name_index: usize, path: &str) {
        let mut body = None;
        let mut depth = 0;
        for at in item.keyword + 1..item.end {
            let token = &self.tokens[at];
            if token.is("(") || token.is("[") {
                depth += 1;
            } else if token.is(")") || token.is("]") {
                depth -= 1;
            } else if token.is("{") && depth == 0 {
                body = Some(at);
                break;
            }
        }
        let header_end = body.unwrap_or(item.end);
        let header: Vec<usize> = (item.start..header_end).collect();
        let supertypes = self.supertypes(item.keyword + 1, header_end);
        let mut references = self.references(&header, Some(name_index));
        let mut fingerprint = self.fingerprint(&header);
        if let Some(body) = body {
            let close = self.after_balanced(body, item.end);
            let preamble = self.container(body + 1, close.saturating_sub(1).max(body + 1), &format!("{path}."), Some(index));
            let rest: Vec<usize> = preamble.into_iter().chain(close..item.end).collect();
            references.extend(self.references(&rest, None));
            fingerprint = combine(fingerprint, self.fingerprint(&rest));
        }
        let declaration = &mut self.declarations[index];
        declaration.supertypes.extend(supertypes);
        declaration.references.extend(references);
        declaration.fingerprint = combine(declaration.fingerprint, fingerprint);
    }

    /// The simple names of the supertypes in the header `start..end`: each identifier after the `:` of the header that
    /// is outside a bracket and not a qualifier.
    fn supertypes(&self, start: usize, end: usize) -> Vec<String> {
        let mut supertypes = Vec::new();
        let mut depth = 0;
        let mut after_colon = false;
        for index in start..end {
            let token = &self.tokens[index];
            match token.text {
                "(" | "<" | "[" if token.kind == TokenKind::Other => depth += 1,
                ")" | ">" | "]" if token.kind == TokenKind::Other => depth -= 1,
                ":" if depth == 0 && token.kind == TokenKind::Other => after_colon = true,
                _ if after_colon && depth == 0 && token.is_identifier() && !self.is_at(index + 1, ".") && token.text != "by" => {
                    supertypes.push(token.text.to_owned());
                }
                _ => {}
            }
        }
        supertypes
    }

    fn member_item(&mut self, item: &Item, index: usize, name_index: usize) {
        let mut own = Vec::new();
        let mut pairs = Vec::new();
        let mut entries: BTreeMap<String, (Vec<usize>, bool)> = BTreeMap::new();
        let mut at = item.start;
        while at < item.end {
            if let Some((suite, entry_end)) = self.entry(at, item.end) {
                let pair = self.is_at(at + 1, "to");
                let entry = entries.entry(suite).or_default();
                entry.0.extend(at..entry_end);
                entry.1 |= pair;
                if pair {
                    pairs.extend(at..entry_end);
                }
                at = entry_end;
                continue;
            }
            own.push(at);
            at += 1;
        }
        let mut references = self.references(&own, Some(name_index));
        // A pair of a map is created with the map, for every user of the map, so what its value names is reached by
        // every user. Its tokens keep their own fingerprint.
        references.extend(self.references(&pairs, None));
        let fingerprint = self.fingerprint(&own);
        let runs_on_creation = self.declarations[index].kind == Kind::Property && self.runs_on_creation(name_index, item.end);
        let entries: Vec<(String, Entry)> = entries
            .into_iter()
            .map(|(suite, (indexes, pair))| {
                let entry = Entry {
                    references: self.references(&indexes, None),
                    fingerprint: self.fingerprint(&indexes),
                    pair,
                };
                (suite, entry)
            })
            .collect();
        let declaration = &mut self.declarations[index];
        declaration.references.extend(references);
        declaration.fingerprint = combine(declaration.fingerprint, fingerprint);
        declaration.runs_on_creation |= runs_on_creation;
        for (suite, entry) in entries {
            let known = declaration.entries.entry(suite).or_default();
            known.references.extend(entry.references);
            known.fingerprint = combine(known.fingerprint, entry.fingerprint);
            known.pair |= entry.pair;
        }
    }

    /// Whether the property after its name `start` runs code when its type is created: a call, a lambda or a delegate
    /// before its first accessor.
    fn runs_on_creation(&self, start: usize, end: usize) -> bool {
        for index in start + 1..end {
            let token = &self.tokens[index];
            let call = self.is_at(index + 1, "(");
            if (token.is("get") || token.is("set")) && call {
                return false;
            }
            if token.is("by") || token.is("{") || (token.is_identifier() && call) {
                return true;
            }
        }
        false
    }

    /// The suite and the end of the suite-keyed entry that starts at `start`, or `None` when none starts there.
    fn entry(&self, start: usize, end: usize) -> Option<(String, usize)> {
        let key = self.token(start).filter(|token| token.kind == TokenKind::Text)?;
        let (suite, _) = key.text.split_once('/')?;
        let arrow = self.token(start + 1)?;
        if !(arrow.is("->") || arrow.is("to")) || !(self.is_suite)(suite) {
            return None;
        }
        let value = start + 2;
        if self.is_at(value, "{") {
            return Some((suite.to_owned(), self.after_balanced(value, end)));
        }
        let mut depth = 0i32;
        let mut index = value;
        while index < end {
            let token = &self.tokens[index];
            if index > value && depth == 0 && token.is(",") {
                // The comma after a pair belongs to the pair, so a new pair changes no other token.
                return Some((suite.to_owned(), index + 1));
            }
            if index > value && depth == 0 && token.line_start {
                break;
            }
            if token.kind == TokenKind::Other {
                match token.text {
                    "(" | "[" | "{" => depth += 1,
                    ")" | "]" | "}" => {
                        depth -= 1;
                        if depth < 0 {
                            break;
                        }
                    }
                    _ => {}
                }
            }
            index += 1;
        }
        Some((suite.to_owned(), index))
    }

    fn fingerprint(&self, indexes: &[usize]) -> u64 {
        let mut hasher = DefaultHasher::new();
        for &index in indexes {
            let token = &self.tokens[index];
            token.kind.hash(&mut hasher);
            token.text.hash(&mut hasher);
        }
        hasher.finish()
    }

    /// Every identifier of `indexes` that can name a declaration of another place.
    ///
    /// Left out: the token at `skip` (the own name), a hard keyword, and each token that binds a name: a parameter, a
    /// local, a loop variable, a lambda parameter and a named argument. A later use of a bound name stays a reference.
    /// It may name a declaration of the same name, and a reference too many costs a suite, never a missed one.
    fn references(&self, indexes: &[usize], skip: Option<usize>) -> BTreeSet<String> {
        let tokens = Selection {
            tokens: self.tokens,
            indexes,
        };
        let mut references = BTreeSet::new();
        for (position, &index) in indexes.iter().enumerate() {
            let token = &self.tokens[index];
            if !token.is_identifier() || Some(index) == skip || KEYWORDS.contains(&token.text) || tokens.binds(position) {
                continue;
            }
            references.insert(token.text.to_owned());
        }
        references
    }
}

/// The tokens at some indexes, in order: what [`Reader::references`] reads.
struct Selection<'t, 'a> {
    tokens: &'t [Token<'a>],
    indexes: &'t [usize],
}

impl Selection<'_, '_> {
    fn token(&self, position: usize) -> Option<&Token<'_>> {
        self.indexes.get(position).map(|&index| &self.tokens[index])
    }

    /// The text of the token at `position`, a quote for a string literal, and nothing past the end.
    fn text(&self, position: usize) -> &str {
        self.token(position)
            .map_or("", |token| if token.kind == TokenKind::Text { "\"" } else { token.text })
    }

    fn previous(&self, position: usize) -> &str {
        position.checked_sub(1).map_or("", |previous| self.text(previous))
    }

    /// Whether the identifier at `position` binds a name: after `val` or `var`, a parameter before its `:`, a loop
    /// variable, a named argument before its `=`, or a lambda parameter.
    fn binds(&self, position: usize) -> bool {
        let previous = self.previous(position);
        let next = self.text(position + 1);
        matches!(previous, "val" | "var")
            || (next == ":" && matches!(previous, "(" | "," | "vararg" | "crossinline" | "noinline" | "<"))
            || (next == "=" && matches!(previous, "(" | ","))
            || (previous == "(" && position >= 2 && self.text(position - 2) == "for")
            || (matches!(next, "->" | "," | ":" | ")") && self.is_lambda_parameter(position))
    }

    /// Whether the identifier at `position` is a parameter of a lambda.
    ///
    /// A parameter follows the `{` of the lambda, a comma, or the `(` of a destructuring, and an `->` follows the
    /// parameters before any brace or call. A type after `:`, `is` or `as`, and a name after a dot, are never one. The
    /// `{` of a `when` body opens no lambda, so the first branch of a `when` binds nothing.
    fn is_lambda_parameter(&self, position: usize) -> bool {
        let previous = self.previous(position);
        let destructuring = previous == "(" && matches!(self.previous(position - 1), "{" | ",");
        if !(matches!(previous, "{" | ",") || destructuring) {
            return false;
        }
        let mut back = position;
        let open = loop {
            let Some(token) = self.token(back) else {
                return false;
            };
            if token.is("{") {
                break back;
            }
            if !(token.is_identifier() || [",", "(", ")", ":", ".", "<", ">", "?"].iter().any(|text| token.is(text))) || self.is_call(back)
            {
                return false;
            }
            let Some(previous) = back.checked_sub(1) else {
                return false;
            };
            back = previous;
        };
        if self.opens_when_body(open) {
            return false;
        }
        let mut forward = position;
        while let Some(token) = self.token(forward) {
            if token.is("->") {
                return true;
            }
            if token.is("{") || token.is("}") || self.is_call(forward) {
                return false;
            }
            forward += 1;
        }
        false
    }

    /// Whether the `{` at `open` opens the body of a `when`: `when {` or `when (subject) {`.
    fn opens_when_body(&self, open: usize) -> bool {
        let Some(before) = open.checked_sub(1) else {
            return false;
        };
        match self.text(before) {
            "when" => true,
            ")" => {
                let mut depth = 0;
                let mut at = before;
                loop {
                    match self.text(at) {
                        ")" => depth += 1,
                        "(" => {
                            depth -= 1;
                            if depth == 0 {
                                return at.checked_sub(1).is_some_and(|keyword| self.text(keyword) == "when");
                            }
                        }
                        _ => {}
                    }
                    let Some(previous) = at.checked_sub(1) else {
                        return false;
                    };
                    at = previous;
                }
            }
            _ => false,
        }
    }

    /// Whether the token at `position` is the `(` of a call: an identifier right before it.
    fn is_call(&self, position: usize) -> bool {
        self.token(position).is_some_and(|token| token.is("("))
            && position
                .checked_sub(1)
                .and_then(|previous| self.token(previous))
                .is_some_and(Token::is_identifier)
    }
}

fn combine(first: u64, second: u64) -> u64 {
    let mut hasher = DefaultHasher::new();
    first.hash(&mut hasher);
    second.hash(&mut hasher);
    hasher.finish()
}

#[cfg(test)]
mod tests;
