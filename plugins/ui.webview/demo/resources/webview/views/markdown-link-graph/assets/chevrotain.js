function cc(char) {
	return char.charCodeAt(0);
}
function insertToSet(item, set) {
	if (Array.isArray(item)) item.forEach(function(subItem) {
		set.push(subItem);
	});
	else set.push(item);
}
function addFlag(flagObj, flagKey) {
	if (flagObj[flagKey] === true) throw "duplicate flag " + flagKey;
	flagObj[flagKey];
	flagObj[flagKey] = true;
}
function ASSERT_EXISTS(obj) {
	// istanbul ignore next
	if (obj === void 0) throw Error("Internal Error - Should never get here!");
	return true;
}
// istanbul ignore next
function ASSERT_NEVER_REACH_HERE() {
	throw Error("Internal Error - Should never get here!");
}
function isCharacter(obj) {
	return obj["type"] === "Character";
}
var digitsCharCodes = [];
for (let i = cc("0"); i <= cc("9"); i++) digitsCharCodes.push(i);
var wordCharCodes = [cc("_")].concat(digitsCharCodes);
for (let i = cc("a"); i <= cc("z"); i++) wordCharCodes.push(i);
for (let i = cc("A"); i <= cc("Z"); i++) wordCharCodes.push(i);
var whitespaceCodes = [
	cc(" "),
	cc("\f"),
	cc("\n"),
	cc("\r"),
	cc("	"),
	cc("\v"),
	cc("	"),
	cc("\xA0"),
	cc(" "),
	cc(" "),
	cc(" "),
	cc(" "),
	cc(" "),
	cc(" "),
	cc(" "),
	cc(" "),
	cc(" "),
	cc(" "),
	cc(" "),
	cc(" "),
	cc("\u2028"),
	cc("\u2029"),
	cc(" "),
	cc(" "),
	cc("　"),
	cc("﻿")
];
var hexDigitPattern = /[0-9a-fA-F]/;
var decimalPattern = /[0-9]/;
var decimalPatternNoZero = /[1-9]/;
var RegExpParser = class {
	constructor() {
		this.idx = 0;
		this.input = "";
		this.groupIdx = 0;
	}
	saveState() {
		return {
			idx: this.idx,
			input: this.input,
			groupIdx: this.groupIdx
		};
	}
	restoreState(newState) {
		this.idx = newState.idx;
		this.input = newState.input;
		this.groupIdx = newState.groupIdx;
	}
	pattern(input) {
		this.idx = 0;
		this.input = input;
		this.groupIdx = 0;
		this.consumeChar("/");
		const value = this.disjunction();
		this.consumeChar("/");
		const flags = {
			type: "Flags",
			loc: {
				begin: this.idx,
				end: input.length
			},
			global: false,
			ignoreCase: false,
			multiLine: false,
			unicode: false,
			sticky: false
		};
		while (this.isRegExpFlag()) switch (this.popChar()) {
			case "g":
				addFlag(flags, "global");
				break;
			case "i":
				addFlag(flags, "ignoreCase");
				break;
			case "m":
				addFlag(flags, "multiLine");
				break;
			case "u":
				addFlag(flags, "unicode");
				break;
			case "y":
				addFlag(flags, "sticky");
				break;
		}
		if (this.idx !== this.input.length) throw Error("Redundant input: " + this.input.substring(this.idx));
		return {
			type: "Pattern",
			flags,
			value,
			loc: this.loc(0)
		};
	}
	disjunction() {
		const alts = [];
		const begin = this.idx;
		alts.push(this.alternative());
		while (this.peekChar() === "|") {
			this.consumeChar("|");
			alts.push(this.alternative());
		}
		return {
			type: "Disjunction",
			value: alts,
			loc: this.loc(begin)
		};
	}
	alternative() {
		const terms = [];
		const begin = this.idx;
		while (this.isTerm()) terms.push(this.term());
		return {
			type: "Alternative",
			value: terms,
			loc: this.loc(begin)
		};
	}
	term() {
		if (this.isAssertion()) return this.assertion();
		else return this.atom();
	}
	assertion() {
		const begin = this.idx;
		switch (this.popChar()) {
			case "^": return {
				type: "StartAnchor",
				loc: this.loc(begin)
			};
			case "$": return {
				type: "EndAnchor",
				loc: this.loc(begin)
			};
			case "\\":
				switch (this.popChar()) {
					case "b": return {
						type: "WordBoundary",
						loc: this.loc(begin)
					};
					case "B": return {
						type: "NonWordBoundary",
						loc: this.loc(begin)
					};
				}
				/* c8 ignore next */
				throw Error("Invalid Assertion Escape");
			case "(":
				this.consumeChar("?");
				let type;
				switch (this.popChar()) {
					case "=":
						type = "Lookahead";
						break;
					case "!":
						type = "NegativeLookahead";
						break;
					case "<":
						switch (this.popChar()) {
							case "=":
								type = "Lookbehind";
								break;
							case "!": type = "NegativeLookbehind";
						}
						break;
				}
				ASSERT_EXISTS(type);
				const disjunction = this.disjunction();
				this.consumeChar(")");
				return {
					type,
					value: disjunction,
					loc: this.loc(begin)
				};
		}
		// istanbul ignore next
		return ASSERT_NEVER_REACH_HERE();
	}
	quantifier(isBacktracking = false) {
		let range = void 0;
		const begin = this.idx;
		switch (this.popChar()) {
			case "*":
				range = {
					atLeast: 0,
					atMost: Infinity
				};
				break;
			case "+":
				range = {
					atLeast: 1,
					atMost: Infinity
				};
				break;
			case "?":
				range = {
					atLeast: 0,
					atMost: 1
				};
				break;
			case "{":
				const atLeast = this.integerIncludingZero();
				switch (this.popChar()) {
					case "}":
						range = {
							atLeast,
							atMost: atLeast
						};
						break;
					case ",":
						let atMost;
						if (this.isDigit()) {
							atMost = this.integerIncludingZero();
							range = {
								atLeast,
								atMost
							};
						} else range = {
							atLeast,
							atMost: Infinity
						};
						this.consumeChar("}");
						break;
				}
				if (isBacktracking === true && range === void 0) return;
				ASSERT_EXISTS(range);
				break;
		}
		if (isBacktracking === true && range === void 0) return;
		// istanbul ignore else
		if (ASSERT_EXISTS(range)) {
			if (this.peekChar(0) === "?") {
				this.consumeChar("?");
				range.greedy = false;
			} else range.greedy = true;
			range.type = "Quantifier";
			range.loc = this.loc(begin);
			return range;
		}
	}
	atom() {
		let atom;
		const begin = this.idx;
		switch (this.peekChar()) {
			case ".":
				atom = this.dotAll();
				break;
			case "\\":
				atom = this.atomEscape();
				break;
			case "[":
				atom = this.characterClass();
				break;
			case "(":
				atom = this.group();
				break;
		}
		if (atom === void 0 && this.isPatternCharacter()) atom = this.patternCharacter();
		// istanbul ignore else
		if (ASSERT_EXISTS(atom)) {
			atom.loc = this.loc(begin);
			if (this.isQuantifier()) atom.quantifier = this.quantifier();
			return atom;
		}
		// istanbul ignore next
		return ASSERT_NEVER_REACH_HERE();
	}
	dotAll() {
		this.consumeChar(".");
		return {
			type: "Set",
			complement: true,
			value: [
				cc("\n"),
				cc("\r"),
				cc("\u2028"),
				cc("\u2029")
			]
		};
	}
	atomEscape() {
		this.consumeChar("\\");
		switch (this.peekChar()) {
			case "1":
			case "2":
			case "3":
			case "4":
			case "5":
			case "6":
			case "7":
			case "8":
			case "9": return this.decimalEscapeAtom();
			case "d":
			case "D":
			case "s":
			case "S":
			case "w":
			case "W": return this.characterClassEscape();
			case "f":
			case "n":
			case "r":
			case "t":
			case "v": return this.controlEscapeAtom();
			case "c": return this.controlLetterEscapeAtom();
			case "0": return this.nulCharacterAtom();
			case "x": return this.hexEscapeSequenceAtom();
			case "u": return this.regExpUnicodeEscapeSequenceAtom();
			default: return this.identityEscapeAtom();
		}
	}
	decimalEscapeAtom() {
		return {
			type: "GroupBackReference",
			value: this.positiveInteger()
		};
	}
	characterClassEscape() {
		let set;
		let complement = false;
		switch (this.popChar()) {
			case "d":
				set = digitsCharCodes;
				break;
			case "D":
				set = digitsCharCodes;
				complement = true;
				break;
			case "s":
				set = whitespaceCodes;
				break;
			case "S":
				set = whitespaceCodes;
				complement = true;
				break;
			case "w":
				set = wordCharCodes;
				break;
			case "W":
				set = wordCharCodes;
				complement = true;
				break;
		}
		// istanbul ignore else
		if (ASSERT_EXISTS(set)) return {
			type: "Set",
			value: set,
			complement
		};
		// istanbul ignore next
		return ASSERT_NEVER_REACH_HERE();
	}
	controlEscapeAtom() {
		let escapeCode;
		switch (this.popChar()) {
			case "f":
				escapeCode = cc("\f");
				break;
			case "n":
				escapeCode = cc("\n");
				break;
			case "r":
				escapeCode = cc("\r");
				break;
			case "t":
				escapeCode = cc("	");
				break;
			case "v":
				escapeCode = cc("\v");
				break;
		}
		// istanbul ignore else
		if (ASSERT_EXISTS(escapeCode)) return {
			type: "Character",
			value: escapeCode
		};
		// istanbul ignore next
		return ASSERT_NEVER_REACH_HERE();
	}
	controlLetterEscapeAtom() {
		this.consumeChar("c");
		const letter = this.popChar();
		if (/[a-zA-Z]/.test(letter) === false) throw Error("Invalid ");
		return {
			type: "Character",
			value: letter.toUpperCase().charCodeAt(0) - 64
		};
	}
	nulCharacterAtom() {
		this.consumeChar("0");
		return {
			type: "Character",
			value: cc("\0")
		};
	}
	hexEscapeSequenceAtom() {
		this.consumeChar("x");
		return this.parseHexDigits(2);
	}
	regExpUnicodeEscapeSequenceAtom() {
		this.consumeChar("u");
		return this.parseHexDigits(4);
	}
	identityEscapeAtom() {
		return {
			type: "Character",
			value: cc(this.popChar())
		};
	}
	classPatternCharacterAtom() {
		switch (this.peekChar()) {
			// istanbul ignore next
			case "\n":
			// istanbul ignore next
			case "\r":
			// istanbul ignore next
			case "\u2028":
			// istanbul ignore next
			case "\u2029":
			// istanbul ignore next
			case "\\":
			// istanbul ignore next
			case "]": throw Error("TBD");
			default: return {
				type: "Character",
				value: cc(this.popChar())
			};
		}
	}
	characterClass() {
		const set = [];
		let complement = false;
		this.consumeChar("[");
		if (this.peekChar(0) === "^") {
			this.consumeChar("^");
			complement = true;
		}
		while (this.isClassAtom()) {
			const from = this.classAtom();
			from.type;
			if (isCharacter(from) && this.isRangeDash()) {
				this.consumeChar("-");
				const to = this.classAtom();
				to.type;
				if (isCharacter(to)) {
					if (to.value < from.value) throw Error("Range out of order in character class");
					set.push({
						from: from.value,
						to: to.value
					});
				} else {
					insertToSet(from.value, set);
					set.push(cc("-"));
					insertToSet(to.value, set);
				}
			} else insertToSet(from.value, set);
		}
		this.consumeChar("]");
		return {
			type: "Set",
			complement,
			value: set
		};
	}
	classAtom() {
		switch (this.peekChar()) {
			// istanbul ignore next
			case "]":
			// istanbul ignore next
			case "\n":
			// istanbul ignore next
			case "\r":
			// istanbul ignore next
			case "\u2028":
			// istanbul ignore next
			case "\u2029": throw Error("TBD");
			case "\\": return this.classEscape();
			default: return this.classPatternCharacterAtom();
		}
	}
	classEscape() {
		this.consumeChar("\\");
		switch (this.peekChar()) {
			case "b":
				this.consumeChar("b");
				return {
					type: "Character",
					value: cc("\b")
				};
			case "d":
			case "D":
			case "s":
			case "S":
			case "w":
			case "W": return this.characterClassEscape();
			case "f":
			case "n":
			case "r":
			case "t":
			case "v": return this.controlEscapeAtom();
			case "c": return this.controlLetterEscapeAtom();
			case "0": return this.nulCharacterAtom();
			case "x": return this.hexEscapeSequenceAtom();
			case "u": return this.regExpUnicodeEscapeSequenceAtom();
			default: return this.identityEscapeAtom();
		}
	}
	group() {
		let capturing = true;
		this.consumeChar("(");
		switch (this.peekChar(0)) {
			case "?":
				this.consumeChar("?");
				this.consumeChar(":");
				capturing = false;
				break;
			default:
				this.groupIdx++;
				break;
		}
		const value = this.disjunction();
		this.consumeChar(")");
		const groupAst = {
			type: "Group",
			capturing,
			value
		};
		if (capturing) groupAst["idx"] = this.groupIdx;
		return groupAst;
	}
	positiveInteger() {
		let number = this.popChar();
		// istanbul ignore next - can't ever get here due to previous lookahead checks
		if (decimalPatternNoZero.test(number) === false) throw Error("Expecting a positive integer");
		while (decimalPattern.test(this.peekChar(0))) number += this.popChar();
		return parseInt(number, 10);
	}
	integerIncludingZero() {
		let number = this.popChar();
		if (decimalPattern.test(number) === false) throw Error("Expecting an integer");
		while (decimalPattern.test(this.peekChar(0))) number += this.popChar();
		return parseInt(number, 10);
	}
	patternCharacter() {
		const nextChar = this.popChar();
		switch (nextChar) {
			// istanbul ignore next
			case "\n":
			// istanbul ignore next
			case "\r":
			// istanbul ignore next
			case "\u2028":
			// istanbul ignore next
			case "\u2029":
			// istanbul ignore next
			case "^":
			// istanbul ignore next
			case "$":
			// istanbul ignore next
			case "\\":
			// istanbul ignore next
			case ".":
			// istanbul ignore next
			case "*":
			// istanbul ignore next
			case "+":
			// istanbul ignore next
			case "?":
			// istanbul ignore next
			case "(":
			// istanbul ignore next
			case ")":
			// istanbul ignore next
			case "[":
			// istanbul ignore next
			case "|":
 // istanbul ignore next
			throw Error("TBD");
			default: return {
				type: "Character",
				value: cc(nextChar)
			};
		}
	}
	isRegExpFlag() {
		switch (this.peekChar(0)) {
			case "g":
			case "i":
			case "m":
			case "u":
			case "y": return true;
			default: return false;
		}
	}
	isRangeDash() {
		return this.peekChar() === "-" && this.isClassAtom(1);
	}
	isDigit() {
		return decimalPattern.test(this.peekChar(0));
	}
	isClassAtom(howMuch = 0) {
		switch (this.peekChar(howMuch)) {
			case "]":
			case "\n":
			case "\r":
			case "\u2028":
			case "\u2029": return false;
			default: return true;
		}
	}
	isTerm() {
		return this.isAtom() || this.isAssertion();
	}
	isAtom() {
		if (this.isPatternCharacter()) return true;
		switch (this.peekChar(0)) {
			case ".":
			case "\\":
			case "[":
			case "(": return true;
			default: return false;
		}
	}
	isAssertion() {
		switch (this.peekChar(0)) {
			case "^":
			case "$": return true;
			case "\\": switch (this.peekChar(1)) {
				case "b":
				case "B": return true;
				default: return false;
			}
			case "(": return this.peekChar(1) === "?" && (this.peekChar(2) === "=" || this.peekChar(2) === "!" || this.peekChar(2) === "<" && (this.peekChar(3) === "=" || this.peekChar(3) === "!"));
			default: return false;
		}
	}
	isQuantifier() {
		const prevState = this.saveState();
		try {
			return this.quantifier(true) !== void 0;
		} catch (e) {
			return false;
		} finally {
			this.restoreState(prevState);
		}
	}
	isPatternCharacter() {
		switch (this.peekChar()) {
			case "^":
			case "$":
			case "\\":
			case ".":
			case "*":
			case "+":
			case "?":
			case "(":
			case ")":
			case "[":
			case "|":
			case "/":
			case "\n":
			case "\r":
			case "\u2028":
			case "\u2029": return false;
			default: return true;
		}
	}
	parseHexDigits(howMany) {
		let hexString = "";
		for (let i = 0; i < howMany; i++) {
			const hexChar = this.popChar();
			if (hexDigitPattern.test(hexChar) === false) throw Error("Expecting a HexDecimal digits");
			hexString += hexChar;
		}
		return {
			type: "Character",
			value: parseInt(hexString, 16)
		};
	}
	peekChar(howMuch = 0) {
		return this.input[this.idx + howMuch];
	}
	popChar() {
		const nextChar = this.peekChar(0);
		this.consumeChar(void 0);
		return nextChar;
	}
	consumeChar(char) {
		if (char !== void 0 && this.input[this.idx] !== char) throw Error("Expected: '" + char + "' but found: '" + this.input[this.idx] + "' at offset: " + this.idx);
		if (this.idx >= this.input.length) throw Error("Unexpected end of input");
		this.idx++;
	}
	loc(begin) {
		return {
			begin,
			end: this.idx
		};
	}
};
var BaseRegExpVisitor = class {
	visitChildren(node) {
		for (const key in node) {
			const child = node[key];
			/* istanbul ignore else */
			if (node.hasOwnProperty(key)) {
				if (child.type !== void 0) this.visit(child);
				else if (Array.isArray(child)) child.forEach((subChild) => {
					this.visit(subChild);
				}, this);
			}
		}
	}
	visit(node) {
		switch (node.type) {
			case "Pattern":
				this.visitPattern(node);
				break;
			case "Flags":
				this.visitFlags(node);
				break;
			case "Disjunction":
				this.visitDisjunction(node);
				break;
			case "Alternative":
				this.visitAlternative(node);
				break;
			case "StartAnchor":
				this.visitStartAnchor(node);
				break;
			case "EndAnchor":
				this.visitEndAnchor(node);
				break;
			case "WordBoundary":
				this.visitWordBoundary(node);
				break;
			case "NonWordBoundary":
				this.visitNonWordBoundary(node);
				break;
			case "Lookahead":
				this.visitLookahead(node);
				break;
			case "NegativeLookahead":
				this.visitNegativeLookahead(node);
				break;
			case "Lookbehind":
				this.visitLookbehind(node);
				break;
			case "NegativeLookbehind":
				this.visitNegativeLookbehind(node);
				break;
			case "Character":
				this.visitCharacter(node);
				break;
			case "Set":
				this.visitSet(node);
				break;
			case "Group":
				this.visitGroup(node);
				break;
			case "GroupBackReference":
				this.visitGroupBackReference(node);
				break;
			case "Quantifier":
				this.visitQuantifier(node);
				break;
		}
		this.visitChildren(node);
	}
	visitPattern(node) {}
	visitFlags(node) {}
	visitDisjunction(node) {}
	visitAlternative(node) {}
	visitStartAnchor(node) {}
	visitEndAnchor(node) {}
	visitWordBoundary(node) {}
	visitNonWordBoundary(node) {}
	visitLookahead(node) {}
	visitNegativeLookahead(node) {}
	visitLookbehind(node) {}
	visitNegativeLookbehind(node) {}
	visitCharacter(node) {}
	visitSet(node) {}
	visitGroup(node) {}
	visitGroupBackReference(node) {}
	visitQuantifier(node) {}
};
function PRINT_ERROR(msg) {
	/* istanbul ignore else - can't override global.console in node.js */
	if (console && console.error) console.error(`Error: ${msg}`);
}
function PRINT_WARNING(msg) {
	/* istanbul ignore else - can't override global.console in node.js*/
	if (console && console.warn) console.warn(`Warning: ${msg}`);
}
function timer(func) {
	const start = (/* @__PURE__ */ new Date()).getTime();
	const val = func();
	return {
		time: (/* @__PURE__ */ new Date()).getTime() - start,
		value: val
	};
}
function toFastProperties(toBecomeFast) {
	function FakeConstructor() {}
	FakeConstructor.prototype = toBecomeFast;
	const fakeInstance = new FakeConstructor();
	function fakeAccess() {
		return typeof fakeInstance.bar;
	}
	fakeAccess();
	fakeAccess();
	return toBecomeFast;
}
function tokenLabel$1(tokType) {
	if (hasTokenLabel$1(tokType)) return tokType.LABEL;
	else return tokType.name;
}
function hasTokenLabel$1(obj) {
	return typeof obj.LABEL === "string" && obj.LABEL !== "";
}
var AbstractProduction = class {
	get definition() {
		return this._definition;
	}
	set definition(value) {
		this._definition = value;
	}
	constructor(_definition) {
		this._definition = _definition;
	}
	accept(visitor) {
		visitor.visit(this);
		this.definition.forEach((prod) => {
			prod.accept(visitor);
		});
	}
};
var NonTerminal = class extends AbstractProduction {
	constructor(options) {
		super([]);
		this.idx = 1;
		Object.assign(this, pickOnlyDefined(options));
	}
	set definition(definition) {}
	get definition() {
		if (this.referencedRule !== void 0) return this.referencedRule.definition;
		return [];
	}
	accept(visitor) {
		visitor.visit(this);
	}
};
var Rule = class extends AbstractProduction {
	constructor(options) {
		super(options.definition);
		this.orgText = "";
		Object.assign(this, pickOnlyDefined(options));
	}
};
var Alternative = class extends AbstractProduction {
	constructor(options) {
		super(options.definition);
		this.ignoreAmbiguities = false;
		Object.assign(this, pickOnlyDefined(options));
	}
};
var Option = class extends AbstractProduction {
	constructor(options) {
		super(options.definition);
		this.idx = 1;
		Object.assign(this, pickOnlyDefined(options));
	}
};
var RepetitionMandatory = class extends AbstractProduction {
	constructor(options) {
		super(options.definition);
		this.idx = 1;
		Object.assign(this, pickOnlyDefined(options));
	}
};
var RepetitionMandatoryWithSeparator = class extends AbstractProduction {
	constructor(options) {
		super(options.definition);
		this.idx = 1;
		Object.assign(this, pickOnlyDefined(options));
	}
};
var Repetition = class extends AbstractProduction {
	constructor(options) {
		super(options.definition);
		this.idx = 1;
		Object.assign(this, pickOnlyDefined(options));
	}
};
var RepetitionWithSeparator = class extends AbstractProduction {
	constructor(options) {
		super(options.definition);
		this.idx = 1;
		Object.assign(this, pickOnlyDefined(options));
	}
};
var Alternation = class extends AbstractProduction {
	get definition() {
		return this._definition;
	}
	set definition(value) {
		this._definition = value;
	}
	constructor(options) {
		super(options.definition);
		this.idx = 1;
		this.ignoreAmbiguities = false;
		this.hasPredicates = false;
		Object.assign(this, pickOnlyDefined(options));
	}
};
var Terminal = class {
	constructor(options) {
		this.idx = 1;
		Object.assign(this, pickOnlyDefined(options));
	}
	accept(visitor) {
		visitor.visit(this);
	}
};
function serializeGrammar(topRules) {
	return topRules.map(serializeProduction);
}
function serializeProduction(node) {
	function convertDefinition(definition) {
		return definition.map(serializeProduction);
	}
	/* istanbul ignore else */
	if (node instanceof NonTerminal) {
		const serializedNonTerminal = {
			type: "NonTerminal",
			name: node.nonTerminalName,
			idx: node.idx
		};
		if (typeof node.label === "string") serializedNonTerminal.label = node.label;
		return serializedNonTerminal;
	} else if (node instanceof Alternative) return {
		type: "Alternative",
		definition: convertDefinition(node.definition)
	};
	else if (node instanceof Option) return {
		type: "Option",
		idx: node.idx,
		definition: convertDefinition(node.definition)
	};
	else if (node instanceof RepetitionMandatory) return {
		type: "RepetitionMandatory",
		idx: node.idx,
		definition: convertDefinition(node.definition)
	};
	else if (node instanceof RepetitionMandatoryWithSeparator) return {
		type: "RepetitionMandatoryWithSeparator",
		idx: node.idx,
		separator: serializeProduction(new Terminal({ terminalType: node.separator })),
		definition: convertDefinition(node.definition)
	};
	else if (node instanceof RepetitionWithSeparator) return {
		type: "RepetitionWithSeparator",
		idx: node.idx,
		separator: serializeProduction(new Terminal({ terminalType: node.separator })),
		definition: convertDefinition(node.definition)
	};
	else if (node instanceof Repetition) return {
		type: "Repetition",
		idx: node.idx,
		definition: convertDefinition(node.definition)
	};
	else if (node instanceof Alternation) return {
		type: "Alternation",
		idx: node.idx,
		definition: convertDefinition(node.definition)
	};
	else if (node instanceof Terminal) {
		const serializedTerminal = {
			type: "Terminal",
			name: node.terminalType.name,
			label: tokenLabel$1(node.terminalType),
			idx: node.idx
		};
		if (typeof node.label === "string") serializedTerminal.terminalLabel = node.label;
		const pattern = node.terminalType.PATTERN;
		if (node.terminalType.PATTERN) serializedTerminal.pattern = pattern instanceof RegExp ? pattern.source : pattern;
		return serializedTerminal;
	} else if (node instanceof Rule) return {
		type: "Rule",
		name: node.name,
		orgText: node.orgText,
		definition: convertDefinition(node.definition)
	};
	else throw Error("non exhaustive match");
}
function pickOnlyDefined(obj) {
	return Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== void 0));
}
var GAstVisitor = class {
	visit(node) {
		const nodeAny = node;
		switch (nodeAny.constructor) {
			case NonTerminal: return this.visitNonTerminal(nodeAny);
			case Alternative: return this.visitAlternative(nodeAny);
			case Option: return this.visitOption(nodeAny);
			case RepetitionMandatory: return this.visitRepetitionMandatory(nodeAny);
			case RepetitionMandatoryWithSeparator: return this.visitRepetitionMandatoryWithSeparator(nodeAny);
			case RepetitionWithSeparator: return this.visitRepetitionWithSeparator(nodeAny);
			case Repetition: return this.visitRepetition(nodeAny);
			case Alternation: return this.visitAlternation(nodeAny);
			case Terminal: return this.visitTerminal(nodeAny);
			case Rule: return this.visitRule(nodeAny);
			/* c8 ignore next 2 */
			default: throw Error("non exhaustive match");
		}
	}
	/* c8 ignore next */
	visitNonTerminal(node) {}
	/* c8 ignore next */
	visitAlternative(node) {}
	/* c8 ignore next */
	visitOption(node) {}
	/* c8 ignore next */
	visitRepetition(node) {}
	/* c8 ignore next */
	visitRepetitionMandatory(node) {}
	/* c8 ignore next 3 */
	visitRepetitionMandatoryWithSeparator(node) {}
	/* c8 ignore next */
	visitRepetitionWithSeparator(node) {}
	/* c8 ignore next */
	visitAlternation(node) {}
	/* c8 ignore next */
	visitTerminal(node) {}
	/* c8 ignore next */
	visitRule(node) {}
};
function isSequenceProd(prod) {
	return prod instanceof Alternative || prod instanceof Option || prod instanceof Repetition || prod instanceof RepetitionMandatory || prod instanceof RepetitionMandatoryWithSeparator || prod instanceof RepetitionWithSeparator || prod instanceof Terminal || prod instanceof Rule;
}
function isOptionalProd(prod, alreadyVisited = []) {
	if (prod instanceof Option || prod instanceof Repetition || prod instanceof RepetitionWithSeparator) return true;
	if (prod instanceof Alternation) return prod.definition.some((subProd) => {
		return isOptionalProd(subProd, alreadyVisited);
	});
	else if (prod instanceof NonTerminal && alreadyVisited.includes(prod)) return false;
	else if (prod instanceof AbstractProduction) {
		if (prod instanceof NonTerminal) alreadyVisited.push(prod);
		return prod.definition.every((subProd) => {
			return isOptionalProd(subProd, alreadyVisited);
		});
	} else return false;
}
function isBranchingProd(prod) {
	return prod instanceof Alternation;
}
function getProductionDslName(prod) {
	/* istanbul ignore else */
	if (prod instanceof NonTerminal) return "SUBRULE";
	else if (prod instanceof Option) return "OPTION";
	else if (prod instanceof Alternation) return "OR";
	else if (prod instanceof RepetitionMandatory) return "AT_LEAST_ONE";
	else if (prod instanceof RepetitionMandatoryWithSeparator) return "AT_LEAST_ONE_SEP";
	else if (prod instanceof RepetitionWithSeparator) return "MANY_SEP";
	else if (prod instanceof Repetition) return "MANY";
	else if (prod instanceof Terminal) return "CONSUME";
	else throw Error("non exhaustive match");
}
/**
*  A Grammar Walker that computes the "remaining" grammar "after" a productions in the grammar.
*/
var RestWalker = class {
	walk(prod, prevRest = []) {
		prod.definition.forEach((subProd, index) => {
			const currRest = prod.definition.slice(index + 1);
			/* istanbul ignore else */
			if (subProd instanceof NonTerminal) this.walkProdRef(subProd, currRest, prevRest);
			else if (subProd instanceof Terminal) this.walkTerminal(subProd, currRest, prevRest);
			else if (subProd instanceof Alternative) this.walkFlat(subProd, currRest, prevRest);
			else if (subProd instanceof Option) this.walkOption(subProd, currRest, prevRest);
			else if (subProd instanceof RepetitionMandatory) this.walkAtLeastOne(subProd, currRest, prevRest);
			else if (subProd instanceof RepetitionMandatoryWithSeparator) this.walkAtLeastOneSep(subProd, currRest, prevRest);
			else if (subProd instanceof RepetitionWithSeparator) this.walkManySep(subProd, currRest, prevRest);
			else if (subProd instanceof Repetition) this.walkMany(subProd, currRest, prevRest);
			else if (subProd instanceof Alternation) this.walkOr(subProd, currRest, prevRest);
			else throw Error("non exhaustive match");
		});
	}
	walkTerminal(terminal, currRest, prevRest) {}
	walkProdRef(refProd, currRest, prevRest) {}
	walkFlat(flatProd, currRest, prevRest) {
		const fullOrRest = currRest.concat(prevRest);
		this.walk(flatProd, fullOrRest);
	}
	walkOption(optionProd, currRest, prevRest) {
		const fullOrRest = currRest.concat(prevRest);
		this.walk(optionProd, fullOrRest);
	}
	walkAtLeastOne(atLeastOneProd, currRest, prevRest) {
		const fullAtLeastOneRest = [new Option({ definition: atLeastOneProd.definition })].concat(currRest, prevRest);
		this.walk(atLeastOneProd, fullAtLeastOneRest);
	}
	walkAtLeastOneSep(atLeastOneSepProd, currRest, prevRest) {
		const fullAtLeastOneSepRest = restForRepetitionWithSeparator(atLeastOneSepProd, currRest, prevRest);
		this.walk(atLeastOneSepProd, fullAtLeastOneSepRest);
	}
	walkMany(manyProd, currRest, prevRest) {
		const fullManyRest = [new Option({ definition: manyProd.definition })].concat(currRest, prevRest);
		this.walk(manyProd, fullManyRest);
	}
	walkManySep(manySepProd, currRest, prevRest) {
		const fullManySepRest = restForRepetitionWithSeparator(manySepProd, currRest, prevRest);
		this.walk(manySepProd, fullManySepRest);
	}
	walkOr(orProd, currRest, prevRest) {
		const fullOrRest = currRest.concat(prevRest);
		orProd.definition.forEach((alt) => {
			const prodWrapper = new Alternative({ definition: [alt] });
			this.walk(prodWrapper, fullOrRest);
		});
	}
};
function restForRepetitionWithSeparator(repSepProd, currRest, prevRest) {
	return [new Option({ definition: [new Terminal({ terminalType: repSepProd.separator })].concat(repSepProd.definition) })].concat(currRest, prevRest);
}
function first(prod) {
	/* istanbul ignore else */
	if (prod instanceof NonTerminal) return first(prod.referencedRule);
	else if (prod instanceof Terminal) return firstForTerminal(prod);
	else if (isSequenceProd(prod)) return firstForSequence(prod);
	else if (isBranchingProd(prod)) return firstForBranching(prod);
	else throw Error("non exhaustive match");
}
function firstForSequence(prod) {
	let firstSet = [];
	const seq = prod.definition;
	let nextSubProdIdx = 0;
	let hasInnerProdsRemaining = seq.length > nextSubProdIdx;
	let currSubProd;
	let isLastInnerProdOptional = true;
	while (hasInnerProdsRemaining && isLastInnerProdOptional) {
		currSubProd = seq[nextSubProdIdx];
		isLastInnerProdOptional = isOptionalProd(currSubProd);
		firstSet = firstSet.concat(first(currSubProd));
		nextSubProdIdx = nextSubProdIdx + 1;
		hasInnerProdsRemaining = seq.length > nextSubProdIdx;
	}
	return [...new Set(firstSet)];
}
function firstForBranching(prod) {
	const allAlternativesFirsts = prod.definition.map((innerProd) => {
		return first(innerProd);
	});
	return [...new Set(allAlternativesFirsts.flat())];
}
function firstForTerminal(terminal) {
	return [terminal.terminalType];
}
var IN = "_~IN~_";
var ResyncFollowsWalker = class extends RestWalker {
	constructor(topProd) {
		super();
		this.topProd = topProd;
		this.follows = {};
	}
	startWalking() {
		this.walk(this.topProd);
		return this.follows;
	}
	walkTerminal(terminal, currRest, prevRest) {}
	walkProdRef(refProd, currRest, prevRest) {
		const followName = buildBetweenProdsFollowPrefix(refProd.referencedRule, refProd.idx) + this.topProd.name;
		const t_in_topProd_follows = first(new Alternative({ definition: currRest.concat(prevRest) }));
		this.follows[followName] = t_in_topProd_follows;
	}
};
function computeAllProdsFollows(topProductions) {
	const reSyncFollows = {};
	topProductions.forEach((topProd) => {
		const currRefsFollow = new ResyncFollowsWalker(topProd).startWalking();
		Object.assign(reSyncFollows, currRefsFollow);
	});
	return reSyncFollows;
}
function buildBetweenProdsFollowPrefix(inner, occurenceInParent) {
	return inner.name + occurenceInParent + IN;
}
var regExpAstCache = {};
var regExpParser = new RegExpParser();
function getRegExpAst(regExp) {
	const regExpStr = regExp.toString();
	if (regExpAstCache.hasOwnProperty(regExpStr)) return regExpAstCache[regExpStr];
	else {
		const regExpAst = regExpParser.pattern(regExpStr);
		regExpAstCache[regExpStr] = regExpAst;
		return regExpAst;
	}
}
function clearRegExpParserCache() {
	regExpAstCache = {};
}
var complementErrorMessage = "Complement Sets are not supported for first char optimization";
var failedOptimizationPrefixMsg = "Unable to use \"first char\" lexer optimizations:\n";
function getOptimizedStartCodesIndices(regExp, ensureOptimizations = false) {
	try {
		const ast = getRegExpAst(regExp);
		return firstCharOptimizedIndices(ast.value, {}, ast.flags.ignoreCase);
	} catch (e) {
		/* istanbul ignore next */
		if (e.message === complementErrorMessage) {
			if (ensureOptimizations) PRINT_WARNING(`${failedOptimizationPrefixMsg}\tUnable to optimize: < ${regExp.toString()} >\n	Complement Sets cannot be automatically optimized.
	This will disable the lexer's first char optimizations.
	See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#COMPLEMENT for details.`);
		} else {
			let msgSuffix = "";
			if (ensureOptimizations) msgSuffix = "\n	This will disable the lexer's first char optimizations.\n	See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#REGEXP_PARSING for details.";
			PRINT_ERROR(`${failedOptimizationPrefixMsg}\n\tFailed parsing: < ${regExp.toString()} >\n\tUsing the @chevrotain/regexp-to-ast library\n	Please open an issue at: https://github.com/chevrotain/chevrotain/issues` + msgSuffix);
		}
	}
	return [];
}
function firstCharOptimizedIndices(ast, result, ignoreCase) {
	switch (ast.type) {
		case "Disjunction":
			for (let i = 0; i < ast.value.length; i++) firstCharOptimizedIndices(ast.value[i], result, ignoreCase);
			break;
		case "Alternative":
			const terms = ast.value;
			for (let i = 0; i < terms.length; i++) {
				const term = terms[i];
				switch (term.type) {
					case "EndAnchor":
					case "GroupBackReference":
					case "Lookahead":
					case "NegativeLookahead":
					case "Lookbehind":
					case "NegativeLookbehind":
					case "StartAnchor":
					case "WordBoundary":
					case "NonWordBoundary": continue;
				}
				const atom = term;
				switch (atom.type) {
					case "Character":
						addOptimizedIdxToResult(atom.value, result, ignoreCase);
						break;
					case "Set":
						if (atom.complement === true) throw Error(complementErrorMessage);
						atom.value.forEach((code) => {
							if (typeof code === "number") addOptimizedIdxToResult(code, result, ignoreCase);
							else {
								const range = code;
								if (ignoreCase === true) for (let rangeCode = range.from; rangeCode <= range.to; rangeCode++) addOptimizedIdxToResult(rangeCode, result, ignoreCase);
								else {
									for (let rangeCode = range.from; rangeCode <= range.to && rangeCode < 256; rangeCode++) addOptimizedIdxToResult(rangeCode, result, ignoreCase);
									if (range.to >= 256) {
										const minUnOptVal = range.from >= 256 ? range.from : 256;
										const maxUnOptVal = range.to;
										const minOptIdx = charCodeToOptimizedIndex(minUnOptVal);
										const maxOptIdx = charCodeToOptimizedIndex(maxUnOptVal);
										for (let currOptIdx = minOptIdx; currOptIdx <= maxOptIdx; currOptIdx++) result[currOptIdx] = currOptIdx;
									}
								}
							}
						});
						break;
					case "Group":
						firstCharOptimizedIndices(atom.value, result, ignoreCase);
						break;
					/* istanbul ignore next */
					default: throw Error("Non Exhaustive Match");
				}
				const isOptionalQuantifier = atom.quantifier !== void 0 && atom.quantifier.atLeast === 0;
				if (atom.type === "Group" && isWholeOptional(atom) === false || atom.type !== "Group" && isOptionalQuantifier === false) break;
			}
			break;
		/* istanbul ignore next */
		default: throw Error("non exhaustive match!");
	}
	return Object.values(result);
}
function addOptimizedIdxToResult(code, result, ignoreCase) {
	const optimizedCharIdx = charCodeToOptimizedIndex(code);
	result[optimizedCharIdx] = optimizedCharIdx;
	if (ignoreCase === true) handleIgnoreCase(code, result);
}
function handleIgnoreCase(code, result) {
	const char = String.fromCharCode(code);
	const upperChar = char.toUpperCase();
	/* istanbul ignore else */
	if (upperChar !== char) {
		const optimizedCharIdx = charCodeToOptimizedIndex(upperChar.charCodeAt(0));
		result[optimizedCharIdx] = optimizedCharIdx;
	} else {
		const lowerChar = char.toLowerCase();
		if (lowerChar !== char) {
			const optimizedCharIdx = charCodeToOptimizedIndex(lowerChar.charCodeAt(0));
			result[optimizedCharIdx] = optimizedCharIdx;
		}
	}
}
function findCode(setNode, targetCharCodes) {
	return setNode.value.find((codeOrRange) => {
		if (typeof codeOrRange === "number") return targetCharCodes.includes(codeOrRange);
		else {
			const range = codeOrRange;
			return targetCharCodes.find((targetCode) => range.from <= targetCode && targetCode <= range.to) !== void 0;
		}
	});
}
function isWholeOptional(ast) {
	const quantifier = ast.quantifier;
	if (quantifier && quantifier.atLeast === 0) return true;
	if (!ast.value) return false;
	return Array.isArray(ast.value) ? ast.value.every(isWholeOptional) : isWholeOptional(ast.value);
}
var CharCodeFinder = class extends BaseRegExpVisitor {
	constructor(targetCharCodes) {
		super();
		this.targetCharCodes = targetCharCodes;
		this.found = false;
	}
	visitChildren(node) {
		if (this.found === true) return;
		switch (node.type) {
			case "Lookahead":
				this.visitLookahead(node);
				return;
			case "NegativeLookahead":
				this.visitNegativeLookahead(node);
				return;
			case "Lookbehind":
				this.visitLookbehind(node);
				return;
			case "NegativeLookbehind":
				this.visitNegativeLookbehind(node);
				return;
		}
		super.visitChildren(node);
	}
	visitCharacter(node) {
		if (this.targetCharCodes.includes(node.value)) this.found = true;
	}
	visitSet(node) {
		if (node.complement) {
			if (findCode(node, this.targetCharCodes) === void 0) this.found = true;
		} else if (findCode(node, this.targetCharCodes) !== void 0) this.found = true;
	}
};
function canMatchCharCode(charCodes, pattern) {
	if (pattern instanceof RegExp) {
		const ast = getRegExpAst(pattern);
		const charCodeFinder = new CharCodeFinder(charCodes);
		charCodeFinder.visit(ast);
		return charCodeFinder.found;
	} else {
		for (const char of pattern) {
			const charCode = char.charCodeAt(0);
			if (charCodes.includes(charCode)) return true;
		}
		return false;
	}
}
var PATTERN = "PATTERN";
var DEFAULT_MODE = "defaultMode";
function analyzeTokenTypes(tokenTypes, options) {
	options = Object.assign({
		safeMode: false,
		positionTracking: "full",
		lineTerminatorCharacters: ["\r", "\n"],
		tracer: (msg, action) => action()
	}, options);
	const tracer = options.tracer;
	tracer("initCharCodeToOptimizedIndexMap", () => {
		initCharCodeToOptimizedIndexMap();
	});
	let onlyRelevantTypes;
	tracer("Reject Lexer.NA", () => {
		onlyRelevantTypes = tokenTypes.filter((currType) => {
			return currType[PATTERN] !== Lexer.NA;
		});
	});
	let hasCustom = false;
	let allTransformedPatterns;
	tracer("Transform Patterns", () => {
		hasCustom = false;
		allTransformedPatterns = onlyRelevantTypes.map((currType) => {
			const currPattern = currType[PATTERN];
			/* istanbul ignore else */
			if (currPattern instanceof RegExp) {
				const regExpSource = currPattern.source;
				if (regExpSource.length === 1 && regExpSource !== "^" && regExpSource !== "$" && regExpSource !== "." && !currPattern.ignoreCase) return regExpSource;
				else if (regExpSource.length === 2 && regExpSource[0] === "\\" && ![
					"d",
					"D",
					"s",
					"S",
					"t",
					"r",
					"n",
					"t",
					"0",
					"c",
					"b",
					"B",
					"f",
					"v",
					"w",
					"W"
				].includes(regExpSource[1])) return regExpSource[1];
				else return addStickyFlag(currPattern);
			} else if (typeof currPattern === "function") {
				hasCustom = true;
				return { exec: currPattern };
			} else if (typeof currPattern === "object") {
				hasCustom = true;
				return currPattern;
			} else if (typeof currPattern === "string") if (currPattern.length === 1) return currPattern;
			else {
				const escapedRegExpString = currPattern.replace(/[\\^$.*+?()[\]{}|]/g, "\\$&");
				return addStickyFlag(new RegExp(escapedRegExpString));
			}
			else throw Error("non exhaustive match");
		});
	});
	let patternIdxToType;
	let patternIdxToGroup;
	let patternIdxToLongerAltIdxArr;
	let patternIdxToPushMode;
	let patternIdxToPopMode;
	tracer("misc mapping", () => {
		patternIdxToType = onlyRelevantTypes.map((currType) => currType.tokenTypeIdx);
		patternIdxToGroup = onlyRelevantTypes.map((clazz) => {
			const groupName = clazz.GROUP;
			/* istanbul ignore next */
			if (groupName === Lexer.SKIPPED) return;
			else if (typeof groupName === "string") return groupName;
			else if (groupName === void 0) return false;
			else throw Error("non exhaustive match");
		});
		patternIdxToLongerAltIdxArr = onlyRelevantTypes.map((clazz) => {
			const longerAltType = clazz.LONGER_ALT;
			if (longerAltType) return Array.isArray(longerAltType) ? longerAltType.map((type) => onlyRelevantTypes.indexOf(type)) : [onlyRelevantTypes.indexOf(longerAltType)];
		});
		patternIdxToPushMode = onlyRelevantTypes.map((clazz) => clazz.PUSH_MODE);
		patternIdxToPopMode = onlyRelevantTypes.map((clazz) => Object.hasOwn(clazz, "POP_MODE"));
	});
	let patternIdxToCanLineTerminator;
	tracer("Line Terminator Handling", () => {
		const lineTerminatorCharCodes = getCharCodes(options.lineTerminatorCharacters);
		patternIdxToCanLineTerminator = onlyRelevantTypes.map((tokType) => false);
		if (options.positionTracking !== "onlyOffset") patternIdxToCanLineTerminator = onlyRelevantTypes.map((tokType) => {
			if (Object.hasOwn(tokType, "LINE_BREAKS")) return !!tokType.LINE_BREAKS;
			else return checkLineBreaksIssues(tokType, lineTerminatorCharCodes) === false && canMatchCharCode(lineTerminatorCharCodes, tokType.PATTERN);
		});
	});
	let patternIdxToIsCustom;
	let patternIdxToShort;
	let emptyGroups;
	let patternIdxToConfig;
	tracer("Misc Mapping #2", () => {
		patternIdxToIsCustom = onlyRelevantTypes.map(isCustomPattern);
		patternIdxToShort = allTransformedPatterns.map(isShortPattern);
		emptyGroups = onlyRelevantTypes.reduce((acc, clazz) => {
			const groupName = clazz.GROUP;
			if (typeof groupName === "string" && !(groupName === Lexer.SKIPPED)) acc[groupName] = [];
			return acc;
		}, {});
		patternIdxToConfig = allTransformedPatterns.map((x, idx) => {
			return {
				pattern: allTransformedPatterns[idx],
				longerAlt: patternIdxToLongerAltIdxArr[idx],
				canLineTerminator: patternIdxToCanLineTerminator[idx],
				isCustom: patternIdxToIsCustom[idx],
				short: patternIdxToShort[idx],
				group: patternIdxToGroup[idx],
				push: patternIdxToPushMode[idx],
				pop: patternIdxToPopMode[idx],
				tokenTypeIdx: patternIdxToType[idx],
				tokenType: onlyRelevantTypes[idx]
			};
		});
	});
	let canBeOptimized = true;
	let charCodeToPatternIdxToConfig = [];
	if (!options.safeMode) tracer("First Char Optimization", () => {
		charCodeToPatternIdxToConfig = onlyRelevantTypes.reduce((result, currTokType, idx) => {
			if (typeof currTokType.PATTERN === "string") addToMapOfArrays(result, charCodeToOptimizedIndex(currTokType.PATTERN.charCodeAt(0)), patternIdxToConfig[idx]);
			else if (Array.isArray(currTokType.START_CHARS_HINT)) {
				let lastOptimizedIdx;
				currTokType.START_CHARS_HINT.forEach((charOrInt) => {
					const currOptimizedIdx = charCodeToOptimizedIndex(typeof charOrInt === "string" ? charOrInt.charCodeAt(0) : charOrInt);
					/* istanbul ignore else */
					if (lastOptimizedIdx !== currOptimizedIdx) {
						lastOptimizedIdx = currOptimizedIdx;
						addToMapOfArrays(result, currOptimizedIdx, patternIdxToConfig[idx]);
					}
				});
			} else if (currTokType.PATTERN instanceof RegExp) if (currTokType.PATTERN.unicode) {
				canBeOptimized = false;
				if (options.ensureOptimizations) PRINT_ERROR(`${failedOptimizationPrefixMsg}\tUnable to analyze < ${currTokType.PATTERN.toString()} > pattern.\n	The regexp unicode flag is not currently supported by the regexp-to-ast library.
	This will disable the lexer's first char optimizations.
	For details See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#UNICODE_OPTIMIZE`);
			} else {
				const optimizedCodes = getOptimizedStartCodesIndices(currTokType.PATTERN, options.ensureOptimizations);
				/* istanbul ignore if */
				if (optimizedCodes.length === 0) canBeOptimized = false;
				optimizedCodes.forEach((code) => {
					addToMapOfArrays(result, code, patternIdxToConfig[idx]);
				});
			}
			else {
				if (options.ensureOptimizations) PRINT_ERROR(`${failedOptimizationPrefixMsg}\tTokenType: <${currTokType.name}> is using a custom token pattern without providing <start_chars_hint> parameter.\n	This will disable the lexer's first char optimizations.
	For details See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#CUSTOM_OPTIMIZE`);
				canBeOptimized = false;
			}
			return result;
		}, []);
	});
	return {
		emptyGroups,
		patternIdxToConfig,
		charCodeToPatternIdxToConfig,
		hasCustom,
		canBeOptimized
	};
}
function validatePatterns(tokenTypes, validModesNames) {
	let errors = [];
	const missingResult = findMissingPatterns(tokenTypes);
	errors = errors.concat(missingResult.errors);
	const invalidResult = findInvalidPatterns(missingResult.valid);
	const validTokenTypes = invalidResult.valid;
	errors = errors.concat(invalidResult.errors);
	errors = errors.concat(validateRegExpPattern(validTokenTypes));
	errors = errors.concat(findInvalidGroupType(validTokenTypes));
	errors = errors.concat(findModesThatDoNotExist(validTokenTypes, validModesNames));
	errors = errors.concat(findUnreachablePatterns(validTokenTypes));
	return errors;
}
function validateRegExpPattern(tokenTypes) {
	let errors = [];
	const withRegExpPatterns = tokenTypes.filter((currTokType) => currTokType[PATTERN] instanceof RegExp);
	errors = errors.concat(findEndOfInputAnchor(withRegExpPatterns));
	errors = errors.concat(findStartOfInputAnchor(withRegExpPatterns));
	errors = errors.concat(findUnsupportedFlags(withRegExpPatterns));
	errors = errors.concat(findDuplicatePatterns(withRegExpPatterns));
	errors = errors.concat(findEmptyMatchRegExps(withRegExpPatterns));
	return errors;
}
function findMissingPatterns(tokenTypes) {
	const tokenTypesWithMissingPattern = tokenTypes.filter((currType) => {
		return !Object.hasOwn(currType, PATTERN);
	});
	return {
		errors: tokenTypesWithMissingPattern.map((currType) => {
			return {
				message: "Token Type: ->" + currType.name + "<- missing static 'PATTERN' property",
				type: LexerDefinitionErrorType.MISSING_PATTERN,
				tokenTypes: [currType]
			};
		}),
		valid: tokenTypes.filter((x) => !tokenTypesWithMissingPattern.includes(x))
	};
}
function findInvalidPatterns(tokenTypes) {
	const tokenTypesWithInvalidPattern = tokenTypes.filter((currType) => {
		const pattern = currType[PATTERN];
		return !(pattern instanceof RegExp) && !(typeof pattern === "function") && !Object.hasOwn(pattern, "exec") && !(typeof pattern === "string");
	});
	return {
		errors: tokenTypesWithInvalidPattern.map((currType) => {
			return {
				message: "Token Type: ->" + currType.name + "<- static 'PATTERN' can only be a RegExp, a Function matching the {CustomPatternMatcherFunc} type or an Object matching the {ICustomPattern} interface.",
				type: LexerDefinitionErrorType.INVALID_PATTERN,
				tokenTypes: [currType]
			};
		}),
		valid: tokenTypes.filter((x) => !tokenTypesWithInvalidPattern.includes(x))
	};
}
var end_of_input = /[^\\][$]/;
function findEndOfInputAnchor(tokenTypes) {
	class EndAnchorFinder extends BaseRegExpVisitor {
		constructor() {
			super(...arguments);
			this.found = false;
		}
		visitEndAnchor(node) {
			this.found = true;
		}
	}
	return tokenTypes.filter((currType) => {
		const pattern = currType.PATTERN;
		try {
			const regexpAst = getRegExpAst(pattern);
			const endAnchorVisitor = new EndAnchorFinder();
			endAnchorVisitor.visit(regexpAst);
			return endAnchorVisitor.found;
		} catch (e) {
			/* istanbul ignore next - cannot ensure an error in regexp-to-ast*/
			return end_of_input.test(pattern.source);
		}
	}).map((currType) => {
		return {
			message: "Unexpected RegExp Anchor Error:\n	Token Type: ->" + currType.name + "<- static 'PATTERN' cannot contain end of input anchor '$'\n	See chevrotain.io/docs/guide/resolving_lexer_errors.html#ANCHORS	for details.",
			type: LexerDefinitionErrorType.EOI_ANCHOR_FOUND,
			tokenTypes: [currType]
		};
	});
}
function findEmptyMatchRegExps(tokenTypes) {
	return tokenTypes.filter((currType) => {
		return currType.PATTERN.test("");
	}).map((currType) => {
		return {
			message: "Token Type: ->" + currType.name + "<- static 'PATTERN' must not match an empty string",
			type: LexerDefinitionErrorType.EMPTY_MATCH_PATTERN,
			tokenTypes: [currType]
		};
	});
}
var start_of_input = /[^\\[][\^]|^\^/;
function findStartOfInputAnchor(tokenTypes) {
	class StartAnchorFinder extends BaseRegExpVisitor {
		constructor() {
			super(...arguments);
			this.found = false;
		}
		visitStartAnchor(node) {
			this.found = true;
		}
	}
	return tokenTypes.filter((currType) => {
		const pattern = currType.PATTERN;
		try {
			const regexpAst = getRegExpAst(pattern);
			const startAnchorVisitor = new StartAnchorFinder();
			startAnchorVisitor.visit(regexpAst);
			return startAnchorVisitor.found;
		} catch (e) {
			/* istanbul ignore next - cannot ensure an error in regexp-to-ast*/
			return start_of_input.test(pattern.source);
		}
	}).map((currType) => {
		return {
			message: "Unexpected RegExp Anchor Error:\n	Token Type: ->" + currType.name + "<- static 'PATTERN' cannot contain start of input anchor '^'\n	See https://chevrotain.io/docs/guide/resolving_lexer_errors.html#ANCHORS	for details.",
			type: LexerDefinitionErrorType.SOI_ANCHOR_FOUND,
			tokenTypes: [currType]
		};
	});
}
function findUnsupportedFlags(tokenTypes) {
	return tokenTypes.filter((currType) => {
		const pattern = currType[PATTERN];
		return pattern instanceof RegExp && (pattern.multiline || pattern.global);
	}).map((currType) => {
		return {
			message: "Token Type: ->" + currType.name + "<- static 'PATTERN' may NOT contain global('g') or multiline('m')",
			type: LexerDefinitionErrorType.UNSUPPORTED_FLAGS_FOUND,
			tokenTypes: [currType]
		};
	});
}
function findDuplicatePatterns(tokenTypes) {
	const found = [];
	let identicalPatterns = tokenTypes.map((outerType) => {
		return tokenTypes.reduce((result, innerType) => {
			if (outerType.PATTERN.source === innerType.PATTERN.source && !found.includes(innerType) && innerType.PATTERN !== Lexer.NA) {
				found.push(innerType);
				result.push(innerType);
				return result;
			}
			return result;
		}, []);
	});
	identicalPatterns = identicalPatterns.filter(Boolean);
	return identicalPatterns.filter((currIdenticalSet) => {
		return currIdenticalSet.length > 1;
	}).map((setOfIdentical) => {
		const tokenTypeNames = setOfIdentical.map((currType) => {
			return currType.name;
		});
		return {
			message: `The same RegExp pattern ->${setOfIdentical[0].PATTERN}<-has been used in all of the following Token Types: ${tokenTypeNames.join(", ")} <-`,
			type: LexerDefinitionErrorType.DUPLICATE_PATTERNS_FOUND,
			tokenTypes: setOfIdentical
		};
	});
}
function findInvalidGroupType(tokenTypes) {
	return tokenTypes.filter((clazz) => {
		if (!Object.hasOwn(clazz, "GROUP")) return false;
		const group = clazz.GROUP;
		return group !== Lexer.SKIPPED && group !== Lexer.NA && !(typeof group === "string");
	}).map((currType) => {
		return {
			message: "Token Type: ->" + currType.name + "<- static 'GROUP' can only be Lexer.SKIPPED/Lexer.NA/A String",
			type: LexerDefinitionErrorType.INVALID_GROUP_TYPE_FOUND,
			tokenTypes: [currType]
		};
	});
}
function findModesThatDoNotExist(tokenTypes, validModes) {
	return tokenTypes.filter((clazz) => {
		return clazz.PUSH_MODE !== void 0 && !validModes.includes(clazz.PUSH_MODE);
	}).map((tokType) => {
		return {
			message: `Token Type: ->${tokType.name}<- static 'PUSH_MODE' value cannot refer to a Lexer Mode ->${tokType.PUSH_MODE}<-which does not exist`,
			type: LexerDefinitionErrorType.PUSH_MODE_DOES_NOT_EXIST,
			tokenTypes: [tokType]
		};
	});
}
function findUnreachablePatterns(tokenTypes) {
	const errors = [];
	const canBeTested = tokenTypes.reduce((result, tokType, idx) => {
		const pattern = tokType.PATTERN;
		if (pattern === Lexer.NA) return result;
		if (typeof pattern === "string") result.push({
			str: pattern,
			idx,
			tokenType: tokType
		});
		else if (pattern instanceof RegExp && noMetaChar(pattern)) result.push({
			str: pattern.source,
			idx,
			tokenType: tokType
		});
		return result;
	}, []);
	tokenTypes.forEach((aTokType, aIdx) => {
		canBeTested.forEach(({ str: bStr, idx: bIdx, tokenType: bTokType }) => {
			if (aIdx < bIdx && tryToMatchStrToPattern(bStr, aTokType.PATTERN)) {
				const msg = `Token: ->${bTokType.name}<- can never be matched.\nBecause it appears AFTER the Token Type ->${aTokType.name}<-in the lexer's definition.\nSee https://chevrotain.io/docs/guide/resolving_lexer_errors.html#UNREACHABLE`;
				errors.push({
					message: msg,
					type: LexerDefinitionErrorType.UNREACHABLE_PATTERN,
					tokenTypes: [aTokType, bTokType]
				});
			}
		});
	});
	return errors;
}
function tryToMatchStrToPattern(str, pattern) {
	if (pattern instanceof RegExp) {
		if (usesLookAheadOrBehind(pattern)) return false;
		const regExpArray = pattern.exec(str);
		return regExpArray !== null && regExpArray.index === 0;
	} else if (typeof pattern === "function") return pattern(str, 0, [], {});
	else if (Object.hasOwn(pattern, "exec")) return pattern.exec(str, 0, [], {});
	else if (typeof pattern === "string") return pattern === str;
	else throw Error("non exhaustive match");
}
function noMetaChar(regExp) {
	return [
		".",
		"\\",
		"[",
		"]",
		"|",
		"^",
		"$",
		"(",
		")",
		"?",
		"*",
		"+",
		"{"
	].find((char) => regExp.source.indexOf(char) !== -1) === void 0;
}
function usesLookAheadOrBehind(regExp) {
	return /(\(\?=)|(\(\?!)|(\(\?<=)|(\(\?<!)/.test(regExp.source);
}
function addStickyFlag(pattern) {
	const flags = pattern.ignoreCase ? "iy" : "y";
	return new RegExp(`${pattern.source}`, flags);
}
function performRuntimeChecks(lexerDefinition, trackLines, lineTerminatorCharacters) {
	const errors = [];
	if (!Object.hasOwn(lexerDefinition, "defaultMode")) errors.push({
		message: "A MultiMode Lexer cannot be initialized without a <defaultMode> property in its definition\n",
		type: LexerDefinitionErrorType.MULTI_MODE_LEXER_WITHOUT_DEFAULT_MODE
	});
	if (!Object.hasOwn(lexerDefinition, "modes")) errors.push({
		message: "A MultiMode Lexer cannot be initialized without a <modes> property in its definition\n",
		type: LexerDefinitionErrorType.MULTI_MODE_LEXER_WITHOUT_MODES_PROPERTY
	});
	if (Object.hasOwn(lexerDefinition, "modes") && Object.hasOwn(lexerDefinition, "defaultMode") && !Object.hasOwn(lexerDefinition.modes, lexerDefinition.defaultMode)) errors.push({
		message: `A MultiMode Lexer cannot be initialized with a ${DEFAULT_MODE}: <${lexerDefinition.defaultMode}>which does not exist\n`,
		type: LexerDefinitionErrorType.MULTI_MODE_LEXER_DEFAULT_MODE_VALUE_DOES_NOT_EXIST
	});
	if (Object.hasOwn(lexerDefinition, "modes")) Object.keys(lexerDefinition.modes).forEach((currModeName) => {
		const currModeValue = lexerDefinition.modes[currModeName];
		currModeValue.forEach((currTokType, currIdx) => {
			if (currTokType === void 0) errors.push({
				message: `A Lexer cannot be initialized using an undefined Token Type. Mode:<${currModeName}> at index: <${currIdx}>\n`,
				type: LexerDefinitionErrorType.LEXER_DEFINITION_CANNOT_CONTAIN_UNDEFINED
			});
			else if (Object.hasOwn(currTokType, "LONGER_ALT")) (Array.isArray(currTokType.LONGER_ALT) ? currTokType.LONGER_ALT : [currTokType.LONGER_ALT]).forEach((currLongerAlt) => {
				if (currLongerAlt !== void 0 && !currModeValue.includes(currLongerAlt)) errors.push({
					message: `A MultiMode Lexer cannot be initialized with a longer_alt <${currLongerAlt.name}> on token <${currTokType.name}> outside of mode <${currModeName}>\n`,
					type: LexerDefinitionErrorType.MULTI_MODE_LEXER_LONGER_ALT_NOT_IN_CURRENT_MODE
				});
			});
		});
	});
	return errors;
}
function performWarningRuntimeChecks(lexerDefinition, trackLines, lineTerminatorCharacters) {
	const warnings = [];
	let hasAnyLineBreak = false;
	const concreteTokenTypes = Object.values(lexerDefinition.modes || {}).flat().filter(Boolean).filter((currType) => currType[PATTERN] !== Lexer.NA);
	const terminatorCharCodes = getCharCodes(lineTerminatorCharacters);
	if (trackLines) concreteTokenTypes.forEach((tokType) => {
		const currIssue = checkLineBreaksIssues(tokType, terminatorCharCodes);
		if (currIssue !== false) {
			const warningDescriptor = {
				message: buildLineBreakIssueMessage(tokType, currIssue),
				type: currIssue.issue,
				tokenType: tokType
			};
			warnings.push(warningDescriptor);
		} else if (Object.hasOwn(tokType, "LINE_BREAKS")) {
			if (tokType.LINE_BREAKS === true) hasAnyLineBreak = true;
		} else if (canMatchCharCode(terminatorCharCodes, tokType.PATTERN)) hasAnyLineBreak = true;
	});
	if (trackLines && !hasAnyLineBreak) warnings.push({
		message: "Warning: No LINE_BREAKS Found.\n	This Lexer has been defined to track line and column information,\n	But none of the Token Types can be identified as matching a line terminator.\n	See https://chevrotain.io/docs/guide/resolving_lexer_errors.html#LINE_BREAKS \n	for details.",
		type: LexerDefinitionErrorType.NO_LINE_BREAKS_FLAGS
	});
	return warnings;
}
function cloneEmptyGroups(emptyGroups) {
	const clonedResult = {};
	Object.keys(emptyGroups).forEach((currKey) => {
		const currGroupValue = emptyGroups[currKey];
		/* istanbul ignore else */
		if (Array.isArray(currGroupValue)) clonedResult[currKey] = [];
		else throw Error("non exhaustive match");
	});
	return clonedResult;
}
function isCustomPattern(tokenType) {
	const pattern = tokenType.PATTERN;
	/* istanbul ignore else */
	if (pattern instanceof RegExp) return false;
	else if (typeof pattern === "function") return true;
	else if (Object.hasOwn(pattern, "exec")) return true;
	else if (typeof pattern === "string") return false;
	else throw Error("non exhaustive match");
}
function isShortPattern(pattern) {
	if (typeof pattern === "string" && pattern.length === 1) return pattern.charCodeAt(0);
	else return false;
}
/**
* Faster than using a RegExp for default newline detection during lexing.
*/
var LineTerminatorOptimizedTester = {
	test: function(text) {
		const len = text.length;
		for (let i = this.lastIndex; i < len; i++) {
			const c = text.charCodeAt(i);
			if (c === 10) {
				this.lastIndex = i + 1;
				return true;
			} else if (c === 13) {
				if (text.charCodeAt(i + 1) === 10) this.lastIndex = i + 2;
				else this.lastIndex = i + 1;
				return true;
			}
		}
		return false;
	},
	lastIndex: 0
};
function checkLineBreaksIssues(tokType, lineTerminatorCharCodes) {
	if (Object.hasOwn(tokType, "LINE_BREAKS")) return false;
	else if (tokType.PATTERN instanceof RegExp) {
		try {
			canMatchCharCode(lineTerminatorCharCodes, tokType.PATTERN);
		} catch (e) {
			/* istanbul ignore next - to test this we would have to mock <canMatchCharCode> to throw an error */
			return {
				issue: LexerDefinitionErrorType.IDENTIFY_TERMINATOR,
				errMsg: e.message
			};
		}
		return false;
	} else if (typeof tokType.PATTERN === "string") return false;
	else if (isCustomPattern(tokType)) return { issue: LexerDefinitionErrorType.CUSTOM_LINE_BREAK };
	else throw Error("non exhaustive match");
}
function buildLineBreakIssueMessage(tokType, details) {
	/* istanbul ignore else */
	if (details.issue === LexerDefinitionErrorType.IDENTIFY_TERMINATOR) return `Warning: unable to identify line terminator usage in pattern.
\tThe problem is in the <${tokType.name}> Token Type\n\t Root cause: ${details.errMsg}.\n	For details See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#IDENTIFY_TERMINATOR`;
	else if (details.issue === LexerDefinitionErrorType.CUSTOM_LINE_BREAK) return `Warning: A Custom Token Pattern should specify the <line_breaks> option.
\tThe problem is in the <${tokType.name}> Token Type\n	For details See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#CUSTOM_LINE_BREAK`;
	else throw Error("non exhaustive match");
}
function getCharCodes(charsOrCodes) {
	return charsOrCodes.map((numOrString) => {
		if (typeof numOrString === "string") return numOrString.charCodeAt(0);
		else return numOrString;
	});
}
function addToMapOfArrays(map, key, value) {
	if (map[key] === void 0) map[key] = [value];
	else map[key].push(value);
}
/**
* We are mapping charCode above ASCI (256) into buckets each in the size of 256.
* This is because ASCI are the most common start chars so each one of those will get its own
* possible token configs vector.
*
* Tokens starting with charCodes "above" ASCI are uncommon, so we can "afford"
* to place these into buckets of possible token configs, What we gain from
* this is avoiding the case of creating an optimization 'charCodeToPatternIdxToConfig'
* which would contain 10,000+ arrays of small size (e.g unicode Identifiers scenario).
* Our 'charCodeToPatternIdxToConfig' max size will now be:
* 256 + (2^16 / 2^8) - 1 === 511
*
* note the hack for fast division integer part extraction
* See: https://stackoverflow.com/a/4228528
*/
var charCodeToOptimizedIdxMap = [];
function charCodeToOptimizedIndex(charCode) {
	return charCode < 256 ? charCode : charCodeToOptimizedIdxMap[charCode];
}
/**
* This is a compromise between cold start / hot running performance
* Creating this array takes ~3ms on a modern machine,
* But if we perform the computation at runtime as needed the CSS Lexer benchmark
* performance degrades by ~10%
*
* TODO: Perhaps it should be lazy initialized only if a charCode > 255 is used.
*/
function initCharCodeToOptimizedIndexMap() {
	if (charCodeToOptimizedIdxMap.length === 0) {
		charCodeToOptimizedIdxMap = new Array(65536);
		for (let i = 0; i < 65536; i++) charCodeToOptimizedIdxMap[i] = i > 255 ? 255 + ~~(i / 255) : i;
	}
}
function tokenStructuredMatcher(tokInstance, tokConstructor) {
	const instanceType = tokInstance.tokenTypeIdx;
	if (instanceType === tokConstructor.tokenTypeIdx) return true;
	else return tokConstructor.isParent === true && tokConstructor.categoryMatchesMap[instanceType] === true;
}
function tokenStructuredMatcherNoCategories(token, tokType) {
	return token.tokenTypeIdx === tokType.tokenTypeIdx;
}
var tokenShortNameIdx = 1;
var tokenIdxToClass = {};
function augmentTokenTypes(tokenTypes) {
	const tokenTypesAndParents = expandCategories(tokenTypes);
	assignTokenDefaultProps(tokenTypesAndParents);
	assignCategoriesMapProp(tokenTypesAndParents);
	assignCategoriesTokensProp(tokenTypesAndParents);
	tokenTypesAndParents.forEach((tokType) => {
		tokType.isParent = tokType.categoryMatches.length > 0;
	});
}
function expandCategories(tokenTypes) {
	let result = [...tokenTypes];
	let categories = tokenTypes;
	let searching = true;
	while (searching) {
		categories = categories.map((currTokType) => currTokType.CATEGORIES).flat().filter(Boolean);
		const newCategories = categories.filter((x) => !result.includes(x));
		result = result.concat(newCategories);
		if (newCategories.length === 0) searching = false;
		else categories = newCategories;
	}
	return result;
}
function assignTokenDefaultProps(tokenTypes) {
	tokenTypes.forEach((currTokType) => {
		if (!hasShortKeyProperty(currTokType)) {
			tokenIdxToClass[tokenShortNameIdx] = currTokType;
			currTokType.tokenTypeIdx = tokenShortNameIdx++;
		}
		if (hasCategoriesProperty(currTokType) && !Array.isArray(currTokType.CATEGORIES)) currTokType.CATEGORIES = [currTokType.CATEGORIES];
		if (!hasCategoriesProperty(currTokType)) currTokType.CATEGORIES = [];
		if (!hasExtendingTokensTypesProperty(currTokType)) currTokType.categoryMatches = [];
		if (!hasExtendingTokensTypesMapProperty(currTokType)) currTokType.categoryMatchesMap = {};
	});
}
function assignCategoriesTokensProp(tokenTypes) {
	tokenTypes.forEach((currTokType) => {
		currTokType.categoryMatches = [];
		Object.keys(currTokType.categoryMatchesMap).forEach((key) => {
			currTokType.categoryMatches.push(tokenIdxToClass[key].tokenTypeIdx);
		});
	});
}
function assignCategoriesMapProp(tokenTypes) {
	tokenTypes.forEach((currTokType) => {
		singleAssignCategoriesToksMap([], currTokType);
	});
}
function singleAssignCategoriesToksMap(path, nextNode) {
	path.forEach((pathNode) => {
		nextNode.categoryMatchesMap[pathNode.tokenTypeIdx] = true;
	});
	nextNode.CATEGORIES.forEach((nextCategory) => {
		const newPath = path.concat(nextNode);
		if (!newPath.includes(nextCategory)) singleAssignCategoriesToksMap(newPath, nextCategory);
	});
}
function hasShortKeyProperty(tokType) {
	return Object.hasOwn(tokType !== null && tokType !== void 0 ? tokType : {}, "tokenTypeIdx");
}
function hasCategoriesProperty(tokType) {
	return Object.hasOwn(tokType !== null && tokType !== void 0 ? tokType : {}, "CATEGORIES");
}
function hasExtendingTokensTypesProperty(tokType) {
	return Object.hasOwn(tokType !== null && tokType !== void 0 ? tokType : {}, "categoryMatches");
}
function hasExtendingTokensTypesMapProperty(tokType) {
	return Object.hasOwn(tokType !== null && tokType !== void 0 ? tokType : {}, "categoryMatchesMap");
}
function isTokenType(tokType) {
	return Object.hasOwn(tokType !== null && tokType !== void 0 ? tokType : {}, "tokenTypeIdx");
}
var defaultLexerErrorProvider = {
	buildUnableToPopLexerModeMessage(token) {
		return `Unable to pop Lexer Mode after encountering Token ->${token.image}<- The Mode Stack is empty`;
	},
	buildUnexpectedCharactersMessage(fullText, startOffset, length, line, column, mode) {
		return `unexpected character: ->${fullText.charAt(startOffset)}<- at offset: ${startOffset}, skipped ${length} characters.`;
	}
};
var LexerDefinitionErrorType;
(function(LexerDefinitionErrorType) {
	LexerDefinitionErrorType[LexerDefinitionErrorType["MISSING_PATTERN"] = 0] = "MISSING_PATTERN";
	LexerDefinitionErrorType[LexerDefinitionErrorType["INVALID_PATTERN"] = 1] = "INVALID_PATTERN";
	LexerDefinitionErrorType[LexerDefinitionErrorType["EOI_ANCHOR_FOUND"] = 2] = "EOI_ANCHOR_FOUND";
	LexerDefinitionErrorType[LexerDefinitionErrorType["UNSUPPORTED_FLAGS_FOUND"] = 3] = "UNSUPPORTED_FLAGS_FOUND";
	LexerDefinitionErrorType[LexerDefinitionErrorType["DUPLICATE_PATTERNS_FOUND"] = 4] = "DUPLICATE_PATTERNS_FOUND";
	LexerDefinitionErrorType[LexerDefinitionErrorType["INVALID_GROUP_TYPE_FOUND"] = 5] = "INVALID_GROUP_TYPE_FOUND";
	LexerDefinitionErrorType[LexerDefinitionErrorType["PUSH_MODE_DOES_NOT_EXIST"] = 6] = "PUSH_MODE_DOES_NOT_EXIST";
	LexerDefinitionErrorType[LexerDefinitionErrorType["MULTI_MODE_LEXER_WITHOUT_DEFAULT_MODE"] = 7] = "MULTI_MODE_LEXER_WITHOUT_DEFAULT_MODE";
	LexerDefinitionErrorType[LexerDefinitionErrorType["MULTI_MODE_LEXER_WITHOUT_MODES_PROPERTY"] = 8] = "MULTI_MODE_LEXER_WITHOUT_MODES_PROPERTY";
	LexerDefinitionErrorType[LexerDefinitionErrorType["MULTI_MODE_LEXER_DEFAULT_MODE_VALUE_DOES_NOT_EXIST"] = 9] = "MULTI_MODE_LEXER_DEFAULT_MODE_VALUE_DOES_NOT_EXIST";
	LexerDefinitionErrorType[LexerDefinitionErrorType["LEXER_DEFINITION_CANNOT_CONTAIN_UNDEFINED"] = 10] = "LEXER_DEFINITION_CANNOT_CONTAIN_UNDEFINED";
	LexerDefinitionErrorType[LexerDefinitionErrorType["SOI_ANCHOR_FOUND"] = 11] = "SOI_ANCHOR_FOUND";
	LexerDefinitionErrorType[LexerDefinitionErrorType["EMPTY_MATCH_PATTERN"] = 12] = "EMPTY_MATCH_PATTERN";
	LexerDefinitionErrorType[LexerDefinitionErrorType["NO_LINE_BREAKS_FLAGS"] = 13] = "NO_LINE_BREAKS_FLAGS";
	LexerDefinitionErrorType[LexerDefinitionErrorType["UNREACHABLE_PATTERN"] = 14] = "UNREACHABLE_PATTERN";
	LexerDefinitionErrorType[LexerDefinitionErrorType["IDENTIFY_TERMINATOR"] = 15] = "IDENTIFY_TERMINATOR";
	LexerDefinitionErrorType[LexerDefinitionErrorType["CUSTOM_LINE_BREAK"] = 16] = "CUSTOM_LINE_BREAK";
	LexerDefinitionErrorType[LexerDefinitionErrorType["MULTI_MODE_LEXER_LONGER_ALT_NOT_IN_CURRENT_MODE"] = 17] = "MULTI_MODE_LEXER_LONGER_ALT_NOT_IN_CURRENT_MODE";
})(LexerDefinitionErrorType || (LexerDefinitionErrorType = {}));
var DEFAULT_LEXER_CONFIG = {
	deferDefinitionErrorsHandling: false,
	positionTracking: "full",
	lineTerminatorsPattern: /\n|\r\n?/g,
	lineTerminatorCharacters: ["\n", "\r"],
	ensureOptimizations: false,
	safeMode: false,
	errorMessageProvider: defaultLexerErrorProvider,
	traceInitPerf: false,
	skipValidations: false,
	recoveryEnabled: true
};
Object.freeze(DEFAULT_LEXER_CONFIG);
var Lexer = class {
	constructor(lexerDefinition, config = DEFAULT_LEXER_CONFIG) {
		this.lexerDefinition = lexerDefinition;
		this.lexerDefinitionErrors = [];
		this.lexerDefinitionWarning = [];
		this.patternIdxToConfig = {};
		this.charCodeToPatternIdxToConfig = {};
		this.modes = [];
		this.emptyGroups = {};
		this.trackStartLines = true;
		this.trackEndLines = true;
		this.hasCustom = false;
		this.canModeBeOptimized = {};
		this.TRACE_INIT = (phaseDesc, phaseImpl) => {
			if (this.traceInitPerf === true) {
				this.traceInitIndent++;
				const indent = new Array(this.traceInitIndent + 1).join("	");
				if (this.traceInitIndent < this.traceInitMaxIdent) console.log(`${indent}--> <${phaseDesc}>`);
				const { time, value } = timer(phaseImpl);
				/* istanbul ignore next - Difficult to reproduce specific performance behavior (>10ms) in tests */
				const traceMethod = time > 10 ? console.warn : console.log;
				if (this.traceInitIndent < this.traceInitMaxIdent) traceMethod(`${indent}<-- <${phaseDesc}> time: ${time}ms`);
				this.traceInitIndent--;
				return value;
			} else return phaseImpl();
		};
		if (typeof config === "boolean") throw Error("The second argument to the Lexer constructor is now an ILexerConfig Object.\na boolean 2nd argument is no longer supported");
		this.config = Object.assign({}, DEFAULT_LEXER_CONFIG, config);
		const traceInitVal = this.config.traceInitPerf;
		if (traceInitVal === true) {
			this.traceInitMaxIdent = Infinity;
			this.traceInitPerf = true;
		} else if (typeof traceInitVal === "number") {
			this.traceInitMaxIdent = traceInitVal;
			this.traceInitPerf = true;
		}
		this.traceInitIndent = -1;
		this.TRACE_INIT("Lexer Constructor", () => {
			let actualDefinition;
			let hasOnlySingleMode = true;
			this.TRACE_INIT("Lexer Config handling", () => {
				if (this.config.lineTerminatorsPattern === DEFAULT_LEXER_CONFIG.lineTerminatorsPattern) this.config.lineTerminatorsPattern = LineTerminatorOptimizedTester;
				else if (this.config.lineTerminatorCharacters === DEFAULT_LEXER_CONFIG.lineTerminatorCharacters) throw Error("Error: Missing <lineTerminatorCharacters> property on the Lexer config.\n	For details See: https://chevrotain.io/docs/guide/resolving_lexer_errors.html#MISSING_LINE_TERM_CHARS");
				if (config.safeMode && config.ensureOptimizations) throw Error("\"safeMode\" and \"ensureOptimizations\" flags are mutually exclusive.");
				this.trackStartLines = /full|onlyStart/i.test(this.config.positionTracking);
				this.trackEndLines = /full/i.test(this.config.positionTracking);
				if (Array.isArray(lexerDefinition)) actualDefinition = {
					modes: { defaultMode: [...lexerDefinition] },
					defaultMode: DEFAULT_MODE
				};
				else {
					hasOnlySingleMode = false;
					actualDefinition = Object.assign({}, lexerDefinition);
				}
			});
			if (this.config.skipValidations === false) {
				this.TRACE_INIT("performRuntimeChecks", () => {
					this.lexerDefinitionErrors = this.lexerDefinitionErrors.concat(performRuntimeChecks(actualDefinition, this.trackStartLines, this.config.lineTerminatorCharacters));
				});
				this.TRACE_INIT("performWarningRuntimeChecks", () => {
					this.lexerDefinitionWarning = this.lexerDefinitionWarning.concat(performWarningRuntimeChecks(actualDefinition, this.trackStartLines, this.config.lineTerminatorCharacters));
				});
			}
			actualDefinition.modes = actualDefinition.modes ? actualDefinition.modes : {};
			Object.entries(actualDefinition.modes).forEach(([currModeName, currModeValue]) => {
				actualDefinition.modes[currModeName] = currModeValue.filter((currTokType) => currTokType !== void 0);
			});
			const allModeNames = Object.keys(actualDefinition.modes);
			Object.entries(actualDefinition.modes).forEach(([currModName, currModDef]) => {
				this.TRACE_INIT(`Mode: <${currModName}> processing`, () => {
					this.modes.push(currModName);
					if (this.config.skipValidations === false) this.TRACE_INIT(`validatePatterns`, () => {
						this.lexerDefinitionErrors = this.lexerDefinitionErrors.concat(validatePatterns(currModDef, allModeNames));
					});
					if (this.lexerDefinitionErrors.length === 0) {
						augmentTokenTypes(currModDef);
						let currAnalyzeResult;
						this.TRACE_INIT(`analyzeTokenTypes`, () => {
							currAnalyzeResult = analyzeTokenTypes(currModDef, {
								lineTerminatorCharacters: this.config.lineTerminatorCharacters,
								positionTracking: config.positionTracking,
								ensureOptimizations: config.ensureOptimizations,
								safeMode: config.safeMode,
								tracer: this.TRACE_INIT
							});
						});
						this.patternIdxToConfig[currModName] = currAnalyzeResult.patternIdxToConfig;
						this.charCodeToPatternIdxToConfig[currModName] = currAnalyzeResult.charCodeToPatternIdxToConfig;
						this.emptyGroups = Object.assign({}, this.emptyGroups, currAnalyzeResult.emptyGroups);
						this.hasCustom = currAnalyzeResult.hasCustom || this.hasCustom;
						this.canModeBeOptimized[currModName] = currAnalyzeResult.canBeOptimized;
					}
				});
			});
			this.defaultMode = actualDefinition.defaultMode;
			if (this.lexerDefinitionErrors.length > 0 && !this.config.deferDefinitionErrorsHandling) {
				const allErrMessagesString = this.lexerDefinitionErrors.map((error) => {
					return error.message;
				}).join("-----------------------\n");
				throw new Error("Errors detected in definition of Lexer:\n" + allErrMessagesString);
			}
			this.lexerDefinitionWarning.forEach((warningDescriptor) => {
				PRINT_WARNING(warningDescriptor.message);
			});
			this.TRACE_INIT("Choosing sub-methods implementations", () => {
				if (hasOnlySingleMode) this.handleModes = () => {};
				if (this.trackStartLines === false) this.computeNewColumn = (x) => x;
				if (this.trackEndLines === false) this.updateTokenEndLineColumnLocation = () => {};
				if (/full/i.test(this.config.positionTracking)) this.createTokenInstance = this.createFullToken;
				else if (/onlyStart/i.test(this.config.positionTracking)) this.createTokenInstance = this.createStartOnlyToken;
				else if (/onlyOffset/i.test(this.config.positionTracking)) this.createTokenInstance = this.createOffsetOnlyToken;
				else throw Error(`Invalid <positionTracking> config option: "${this.config.positionTracking}"`);
				if (this.hasCustom) {
					this.addToken = this.addTokenUsingPush;
					this.handlePayload = this.handlePayloadWithCustom;
				} else {
					this.addToken = this.addTokenUsingMemberAccess;
					this.handlePayload = this.handlePayloadNoCustom;
				}
			});
			this.TRACE_INIT("Failed Optimization Warnings", () => {
				const unOptimizedModes = Object.entries(this.canModeBeOptimized).reduce((cannotBeOptimized, [modeName, canBeOptimized]) => {
					if (canBeOptimized === false) cannotBeOptimized.push(modeName);
					return cannotBeOptimized;
				}, []);
				if (config.ensureOptimizations && unOptimizedModes.length > 0) throw Error(`Lexer Modes: < ${unOptimizedModes.join(", ")} > cannot be optimized.\n	 Disable the "ensureOptimizations" lexer config flag to silently ignore this and run the lexer in an un-optimized mode.
	 Or inspect the console log for details on how to resolve these issues.`);
			});
			this.TRACE_INIT("clearRegExpParserCache", () => {
				clearRegExpParserCache();
			});
			this.TRACE_INIT("toFastProperties", () => {
				toFastProperties(this);
			});
		});
	}
	tokenize(text, initialMode = this.defaultMode) {
		if (this.lexerDefinitionErrors.length > 0) {
			const allErrMessagesString = this.lexerDefinitionErrors.map((error) => {
				return error.message;
			}).join("-----------------------\n");
			throw new Error("Unable to Tokenize because Errors detected in definition of Lexer:\n" + allErrMessagesString);
		}
		return this.tokenizeInternal(text, initialMode);
	}
	tokenizeInternal(text, initialMode) {
		let i, j, k, matchAltImage, longerAlt, matchedImage, payload, altPayload, imageLength, group, tokType, newToken, errLength, msg, match;
		const orgText = text;
		const orgLength = orgText.length;
		let offset = 0;
		let matchedTokensIndex = 0;
		const guessedNumberOfTokens = this.hasCustom ? 0 : Math.floor(text.length / 10);
		const matchedTokens = new Array(guessedNumberOfTokens);
		const errors = [];
		let line = this.trackStartLines ? 1 : void 0;
		let column = this.trackStartLines ? 1 : void 0;
		const groups = cloneEmptyGroups(this.emptyGroups);
		const trackLines = this.trackStartLines;
		const lineTerminatorPattern = this.config.lineTerminatorsPattern;
		let currModePatternsLength = 0;
		let patternIdxToConfig = [];
		let currCharCodeToPatternIdxToConfig = [];
		const modeStack = [];
		const emptyArray = [];
		Object.freeze(emptyArray);
		let isOptimizedMode = false;
		const pop_mode = (popToken) => {
			if (modeStack.length === 1 && popToken.tokenType.PUSH_MODE === void 0) {
				const msg = this.config.errorMessageProvider.buildUnableToPopLexerModeMessage(popToken);
				errors.push({
					offset: popToken.startOffset,
					line: popToken.startLine,
					column: popToken.startColumn,
					length: popToken.image.length,
					message: msg
				});
			} else {
				modeStack.pop();
				const newMode = modeStack.at(-1);
				patternIdxToConfig = this.patternIdxToConfig[newMode];
				currCharCodeToPatternIdxToConfig = this.charCodeToPatternIdxToConfig[newMode];
				currModePatternsLength = patternIdxToConfig.length;
				const modeCanBeOptimized = this.canModeBeOptimized[newMode] && this.config.safeMode === false;
				if (currCharCodeToPatternIdxToConfig && modeCanBeOptimized) isOptimizedMode = true;
				else isOptimizedMode = false;
			}
		};
		function push_mode(newMode) {
			modeStack.push(newMode);
			currCharCodeToPatternIdxToConfig = this.charCodeToPatternIdxToConfig[newMode];
			patternIdxToConfig = this.patternIdxToConfig[newMode];
			currModePatternsLength = patternIdxToConfig.length;
			currModePatternsLength = patternIdxToConfig.length;
			const modeCanBeOptimized = this.canModeBeOptimized[newMode] && this.config.safeMode === false;
			if (currCharCodeToPatternIdxToConfig && modeCanBeOptimized) isOptimizedMode = true;
			else isOptimizedMode = false;
		}
		push_mode.call(this, initialMode);
		let currConfig;
		const recoveryEnabled = this.config.recoveryEnabled;
		while (offset < orgLength) {
			matchedImage = null;
			imageLength = -1;
			const nextCharCode = orgText.charCodeAt(offset);
			let chosenPatternIdxToConfig;
			if (isOptimizedMode) {
				const optimizedCharIdx = charCodeToOptimizedIndex(nextCharCode);
				const possiblePatterns = currCharCodeToPatternIdxToConfig[optimizedCharIdx];
				chosenPatternIdxToConfig = possiblePatterns !== void 0 ? possiblePatterns : emptyArray;
			} else chosenPatternIdxToConfig = patternIdxToConfig;
			const chosenPatternsLength = chosenPatternIdxToConfig.length;
			for (i = 0; i < chosenPatternsLength; i++) {
				currConfig = chosenPatternIdxToConfig[i];
				const currPattern = currConfig.pattern;
				payload = null;
				const singleCharCode = currConfig.short;
				if (singleCharCode !== false) {
					if (nextCharCode === singleCharCode) {
						imageLength = 1;
						matchedImage = currPattern;
					}
				} else if (currConfig.isCustom === true) {
					match = currPattern.exec(orgText, offset, matchedTokens, groups);
					if (match !== null) {
						matchedImage = match[0];
						imageLength = matchedImage.length;
						if (match.payload !== void 0) payload = match.payload;
					} else matchedImage = null;
				} else {
					currPattern.lastIndex = offset;
					imageLength = this.matchLength(currPattern, text, offset);
				}
				if (imageLength !== -1) {
					longerAlt = currConfig.longerAlt;
					if (longerAlt !== void 0) {
						matchedImage = text.substring(offset, offset + imageLength);
						const longerAltLength = longerAlt.length;
						for (k = 0; k < longerAltLength; k++) {
							const longerAltConfig = patternIdxToConfig[longerAlt[k]];
							const longerAltPattern = longerAltConfig.pattern;
							altPayload = null;
							if (longerAltConfig.isCustom === true) {
								match = longerAltPattern.exec(orgText, offset, matchedTokens, groups);
								if (match !== null) {
									matchAltImage = match[0];
									if (match.payload !== void 0) altPayload = match.payload;
								} else matchAltImage = null;
							} else {
								longerAltPattern.lastIndex = offset;
								matchAltImage = this.match(longerAltPattern, text, offset);
							}
							if (matchAltImage && matchAltImage.length > matchedImage.length) {
								matchedImage = matchAltImage;
								imageLength = matchAltImage.length;
								payload = altPayload;
								currConfig = longerAltConfig;
								break;
							}
						}
					}
					break;
				}
			}
			if (imageLength !== -1) {
				group = currConfig.group;
				if (group !== void 0) {
					matchedImage = matchedImage !== null ? matchedImage : text.substring(offset, offset + imageLength);
					tokType = currConfig.tokenTypeIdx;
					newToken = this.createTokenInstance(matchedImage, offset, tokType, currConfig.tokenType, line, column, imageLength);
					this.handlePayload(newToken, payload);
					if (group === false) matchedTokensIndex = this.addToken(matchedTokens, matchedTokensIndex, newToken);
					else groups[group].push(newToken);
				}
				if (trackLines === true && currConfig.canLineTerminator === true) {
					let numOfLTsInMatch = 0;
					let foundTerminator;
					let lastLTEndOffset;
					lineTerminatorPattern.lastIndex = 0;
					do {
						matchedImage = matchedImage !== null ? matchedImage : text.substring(offset, offset + imageLength);
						foundTerminator = lineTerminatorPattern.test(matchedImage);
						if (foundTerminator === true) {
							lastLTEndOffset = lineTerminatorPattern.lastIndex - 1;
							numOfLTsInMatch++;
						}
					} while (foundTerminator === true);
					if (numOfLTsInMatch !== 0) {
						line = line + numOfLTsInMatch;
						column = imageLength - lastLTEndOffset;
						this.updateTokenEndLineColumnLocation(newToken, group, lastLTEndOffset, numOfLTsInMatch, line, column, imageLength);
					} else column = this.computeNewColumn(column, imageLength);
				} else column = this.computeNewColumn(column, imageLength);
				offset = offset + imageLength;
				this.handleModes(currConfig, pop_mode, push_mode, newToken);
			} else {
				const errorStartOffset = offset;
				const errorLine = line;
				const errorColumn = column;
				let foundResyncPoint = recoveryEnabled === false;
				while (foundResyncPoint === false && offset < orgLength) {
					offset++;
					for (j = 0; j < currModePatternsLength; j++) {
						const currConfig = patternIdxToConfig[j];
						const currPattern = currConfig.pattern;
						const singleCharCode = currConfig.short;
						if (singleCharCode !== false) {
							if (orgText.charCodeAt(offset) === singleCharCode) foundResyncPoint = true;
						} else if (currConfig.isCustom === true) foundResyncPoint = currPattern.exec(orgText, offset, matchedTokens, groups) !== null;
						else {
							currPattern.lastIndex = offset;
							foundResyncPoint = currPattern.exec(text) !== null;
						}
						if (foundResyncPoint === true) break;
					}
				}
				errLength = offset - errorStartOffset;
				column = this.computeNewColumn(column, errLength);
				msg = this.config.errorMessageProvider.buildUnexpectedCharactersMessage(orgText, errorStartOffset, errLength, errorLine, errorColumn, modeStack.at(-1));
				errors.push({
					offset: errorStartOffset,
					line: errorLine,
					column: errorColumn,
					length: errLength,
					message: msg
				});
				if (recoveryEnabled === false) break;
			}
		}
		if (!this.hasCustom) matchedTokens.length = matchedTokensIndex;
		return {
			tokens: matchedTokens,
			groups,
			errors
		};
	}
	handleModes(config, pop_mode, push_mode, newToken) {
		if (config.pop === true) {
			const pushMode = config.push;
			pop_mode(newToken);
			if (pushMode !== void 0) push_mode.call(this, pushMode);
		} else if (config.push !== void 0) push_mode.call(this, config.push);
	}
	updateTokenEndLineColumnLocation(newToken, group, lastLTIdx, numOfLTsInMatch, line, column, imageLength) {
		let lastCharIsLT, fixForEndingInLT;
		if (group !== void 0) {
			lastCharIsLT = lastLTIdx === imageLength - 1;
			fixForEndingInLT = lastCharIsLT ? -1 : 0;
			if (!(numOfLTsInMatch === 1 && lastCharIsLT === true)) {
				newToken.endLine = line + fixForEndingInLT;
				newToken.endColumn = column - 1 + -fixForEndingInLT;
			}
		}
	}
	computeNewColumn(oldColumn, imageLength) {
		return oldColumn + imageLength;
	}
	createOffsetOnlyToken(image, startOffset, tokenTypeIdx, tokenType) {
		return {
			image,
			startOffset,
			tokenTypeIdx,
			tokenType
		};
	}
	createStartOnlyToken(image, startOffset, tokenTypeIdx, tokenType, startLine, startColumn) {
		return {
			image,
			startOffset,
			startLine,
			startColumn,
			tokenTypeIdx,
			tokenType
		};
	}
	createFullToken(image, startOffset, tokenTypeIdx, tokenType, startLine, startColumn, imageLength) {
		return {
			image,
			startOffset,
			endOffset: startOffset + imageLength - 1,
			startLine,
			endLine: startLine,
			startColumn,
			endColumn: startColumn + imageLength - 1,
			tokenTypeIdx,
			tokenType
		};
	}
	addTokenUsingPush(tokenVector, index, tokenToAdd) {
		tokenVector.push(tokenToAdd);
		return index;
	}
	addTokenUsingMemberAccess(tokenVector, index, tokenToAdd) {
		tokenVector[index] = tokenToAdd;
		index++;
		return index;
	}
	handlePayloadNoCustom(token, payload) {}
	handlePayloadWithCustom(token, payload) {
		if (payload !== null) token.payload = payload;
	}
	match(pattern, text, offset) {
		if (pattern.test(text) === true) return text.substring(offset, pattern.lastIndex);
		return null;
	}
	matchLength(pattern, text, offset) {
		if (pattern.test(text) === true) return pattern.lastIndex - offset;
		return -1;
	}
};
Lexer.SKIPPED = "This marks a skipped Token pattern, this means each token identified by it will be consumed and then thrown into oblivion, this can be used to for example to completely ignore whitespace.";
Lexer.NA = /NOT_APPLICABLE/;
function tokenLabel(tokType) {
	if (hasTokenLabel(tokType)) return tokType.LABEL;
	else return tokType.name;
}
function hasTokenLabel(obj) {
	return typeof obj.LABEL === "string" && obj.LABEL !== "";
}
var PARENT = "parent";
var CATEGORIES = "categories";
var LABEL = "label";
var GROUP = "group";
var PUSH_MODE = "push_mode";
var POP_MODE = "pop_mode";
var LONGER_ALT = "longer_alt";
var LINE_BREAKS = "line_breaks";
var START_CHARS_HINT = "start_chars_hint";
function createToken(config) {
	return createTokenInternal(config);
}
function createTokenInternal(config) {
	const pattern = config.pattern;
	const tokenType = {};
	tokenType.name = config.name;
	if (pattern !== void 0) tokenType.PATTERN = pattern;
	if (Object.hasOwn(config, PARENT)) throw "The parent property is no longer supported.\nSee: https://github.com/chevrotain/chevrotain/issues/564#issuecomment-349062346 for details.";
	if (Object.hasOwn(config, CATEGORIES)) tokenType.CATEGORIES = config[CATEGORIES];
	augmentTokenTypes([tokenType]);
	if (Object.hasOwn(config, LABEL)) tokenType.LABEL = config[LABEL];
	if (Object.hasOwn(config, GROUP)) tokenType.GROUP = config[GROUP];
	if (Object.hasOwn(config, POP_MODE)) tokenType.POP_MODE = config[POP_MODE];
	if (Object.hasOwn(config, PUSH_MODE)) tokenType.PUSH_MODE = config[PUSH_MODE];
	if (Object.hasOwn(config, LONGER_ALT)) tokenType.LONGER_ALT = config[LONGER_ALT];
	if (Object.hasOwn(config, LINE_BREAKS)) tokenType.LINE_BREAKS = config[LINE_BREAKS];
	if (Object.hasOwn(config, START_CHARS_HINT)) tokenType.START_CHARS_HINT = config[START_CHARS_HINT];
	return tokenType;
}
var EOF = createToken({
	name: "EOF",
	pattern: Lexer.NA
});
augmentTokenTypes([EOF]);
function createTokenInstance(tokType, image, startOffset, endOffset, startLine, endLine, startColumn, endColumn) {
	return {
		image,
		startOffset,
		endOffset,
		startLine,
		endLine,
		startColumn,
		endColumn,
		tokenTypeIdx: tokType.tokenTypeIdx,
		tokenType: tokType
	};
}
function tokenMatcher(token, tokType) {
	return tokenStructuredMatcher(token, tokType);
}
var defaultParserErrorProvider = {
	buildMismatchTokenMessage({ expected, actual, previous, ruleName }) {
		return `Expecting ${hasTokenLabel(expected) ? `--> ${tokenLabel(expected)} <--` : `token of type --> ${expected.name} <--`} but found --> '${actual.image}' <--`;
	},
	buildNotAllInputParsedMessage({ firstRedundant, ruleName }) {
		return "Redundant input, expecting EOF but found: " + firstRedundant.image;
	},
	buildNoViableAltMessage({ expectedPathsPerAlt, actual, previous, customUserDescription, ruleName }) {
		const errPrefix = "Expecting: ";
		const errSuffix = "\nbut found: '" + actual[0].image + "'";
		if (customUserDescription) return errPrefix + customUserDescription + errSuffix;
		else return `Expecting: one of these possible Token sequences:\n${expectedPathsPerAlt.reduce((result, currAltPaths) => result.concat(currAltPaths), []).map((currPath) => `[${currPath.map((currTokenType) => tokenLabel(currTokenType)).join(", ")}]`).map((itemMsg, idx) => `  ${idx + 1}. ${itemMsg}`).join("\n")}` + errSuffix;
	},
	buildEarlyExitMessage({ expectedIterationPaths, actual, customUserDescription, ruleName }) {
		const errPrefix = "Expecting: ";
		const errSuffix = "\nbut found: '" + actual[0].image + "'";
		if (customUserDescription) return errPrefix + customUserDescription + errSuffix;
		else return `Expecting: expecting at least one iteration which starts with one of these possible Token sequences::\n  <${expectedIterationPaths.map((currPath) => `[${currPath.map((currTokenType) => tokenLabel(currTokenType)).join(",")}]`).join(" ,")}>` + errSuffix;
	}
};
Object.freeze(defaultParserErrorProvider);
var defaultGrammarResolverErrorProvider = { buildRuleNotFoundError(topLevelRule, undefinedRule) {
	return "Invalid grammar, reference to a rule which is not defined: ->" + undefinedRule.nonTerminalName + "<-\ninside top level rule: ->" + topLevelRule.name + "<-";
} };
var defaultGrammarValidatorErrorProvider = {
	buildDuplicateFoundError(topLevelRule, duplicateProds) {
		function getExtraProductionArgument(prod) {
			if (prod instanceof Terminal) return prod.terminalType.name;
			else if (prod instanceof NonTerminal) return prod.nonTerminalName;
			else return "";
		}
		const topLevelName = topLevelRule.name;
		const duplicateProd = duplicateProds[0];
		const index = duplicateProd.idx;
		const dslName = getProductionDslName(duplicateProd);
		const extraArgument = getExtraProductionArgument(duplicateProd);
		let msg = `->${dslName}${index > 0 ? index : ""}<- ${extraArgument ? `with argument: ->${extraArgument}<-` : ""}
                  appears more than once (${duplicateProds.length} times) in the top level rule: ->${topLevelName}<-.                  
                  For further details see: https://chevrotain.io/docs/FAQ.html#NUMERICAL_SUFFIXES 
                  `;
		msg = msg.replace(/[ \t]+/g, " ");
		msg = msg.replace(/\s\s+/g, "\n");
		return msg;
	},
	buildNamespaceConflictError(rule) {
		return `Namespace conflict found in grammar.\nThe grammar has both a Terminal(Token) and a Non-Terminal(Rule) named: <${rule.name}>.\nTo resolve this make sure each Terminal and Non-Terminal names are unique\nThis is easy to accomplish by using the convention that Terminal names start with an uppercase letter\nand Non-Terminal names start with a lower case letter.`;
	},
	buildAlternationPrefixAmbiguityError(options) {
		const pathMsg = options.prefixPath.map((currTok) => tokenLabel(currTok)).join(", ");
		const occurrence = options.alternation.idx === 0 ? "" : options.alternation.idx;
		return `Ambiguous alternatives: <${options.ambiguityIndices.join(" ,")}> due to common lookahead prefix\nin <OR${occurrence}> inside <${options.topLevelRule.name}> Rule,\n<${pathMsg}> may appears as a prefix path in all these alternatives.\nSee: https://chevrotain.io/docs/guide/resolving_grammar_errors.html#COMMON_PREFIX\nFor Further details.`;
	},
	buildAlternationAmbiguityError(options) {
		const occurrence = options.alternation.idx === 0 ? "" : options.alternation.idx;
		const isEmptyPath = options.prefixPath.length === 0;
		let currMessage = `Ambiguous Alternatives Detected: <${options.ambiguityIndices.join(" ,")}> in <OR${occurrence}> inside <${options.topLevelRule.name}> Rule,\n`;
		if (isEmptyPath) currMessage += "These alternatives are all empty (match no tokens), making them indistinguishable.\nOnly the last alternative may be empty.\n";
		else {
			const pathMsg = options.prefixPath.map((currtok) => tokenLabel(currtok)).join(", ");
			currMessage += `<${pathMsg}> may appears as a prefix path in all these alternatives.\n`;
		}
		currMessage += "See: https://chevrotain.io/docs/guide/resolving_grammar_errors.html#AMBIGUOUS_ALTERNATIVES\nFor Further details.";
		return currMessage;
	},
	buildEmptyRepetitionError(options) {
		let dslName = getProductionDslName(options.repetition);
		if (options.repetition.idx !== 0) dslName += options.repetition.idx;
		return `The repetition <${dslName}> within Rule <${options.topLevelRule.name}> can never consume any tokens.\nThis could lead to an infinite loop.`;
	},
	buildTokenNameError(options) {
		/* istanbul ignore next */
		return "deprecated";
	},
	buildEmptyAlternationError(options) {
		return `Ambiguous empty alternative: <${options.emptyChoiceIdx + 1}> in <OR${options.alternation.idx}> inside <${options.topLevelRule.name}> Rule.\nOnly the last alternative may be an empty alternative.`;
	},
	buildTooManyAlternativesError(options) {
		return `An Alternation cannot have more than 256 alternatives:\n<OR${options.alternation.idx}> inside <${options.topLevelRule.name}> Rule.\n has ${options.alternation.definition.length + 1} alternatives.`;
	},
	buildLeftRecursionError(options) {
		const ruleName = options.topLevelRule.name;
		return `Left Recursion found in grammar.\nrule: <${ruleName}> can be invoked from itself (directly or indirectly)\nwithout consuming any Tokens. The grammar path that causes this is: \n ${`${ruleName} --> ${options.leftRecursionPath.map((currRule) => currRule.name).concat([ruleName]).join(" --> ")}`}\n To fix this refactor your grammar to remove the left recursion.\nsee: https://en.wikipedia.org/wiki/LL_parser#Left_factoring.`;
	},
	buildInvalidRuleNameError(options) {
		/* istanbul ignore next */
		return "deprecated";
	},
	buildDuplicateRuleNameError(options) {
		let ruleName;
		if (options.topLevelRule instanceof Rule) ruleName = options.topLevelRule.name;
		else ruleName = options.topLevelRule;
		return `Duplicate definition, rule: ->${ruleName}<- is already defined in the grammar: ->${options.grammarName}<-`;
	}
};
function resolveGrammar$1(topLevels, errMsgProvider) {
	const refResolver = new GastRefResolverVisitor(topLevels, errMsgProvider);
	refResolver.resolveRefs();
	return refResolver.errors;
}
var GastRefResolverVisitor = class extends GAstVisitor {
	constructor(nameToTopRule, errMsgProvider) {
		super();
		this.nameToTopRule = nameToTopRule;
		this.errMsgProvider = errMsgProvider;
		this.errors = [];
	}
	resolveRefs() {
		Object.values(this.nameToTopRule).forEach((prod) => {
			this.currTopLevel = prod;
			prod.accept(this);
		});
	}
	visitNonTerminal(node) {
		const ref = this.nameToTopRule[node.nonTerminalName];
		if (!ref) {
			const msg = this.errMsgProvider.buildRuleNotFoundError(this.currTopLevel, node);
			this.errors.push({
				message: msg,
				type: ParserDefinitionErrorType.UNRESOLVED_SUBRULE_REF,
				ruleName: this.currTopLevel.name,
				unresolvedRefName: node.nonTerminalName
			});
		} else node.referencedRule = ref;
	}
};
var AbstractNextPossibleTokensWalker = class extends RestWalker {
	constructor(topProd, path) {
		super();
		this.topProd = topProd;
		this.path = path;
		this.possibleTokTypes = [];
		this.nextProductionName = "";
		this.nextProductionOccurrence = 0;
		this.found = false;
		this.isAtEndOfPath = false;
	}
	startWalking() {
		this.found = false;
		if (this.path.ruleStack[0] !== this.topProd.name) throw Error("The path does not start with the walker's top Rule!");
		this.ruleStack = [...this.path.ruleStack].reverse();
		this.occurrenceStack = [...this.path.occurrenceStack].reverse();
		this.ruleStack.pop();
		this.occurrenceStack.pop();
		this.updateExpectedNext();
		this.walk(this.topProd);
		return this.possibleTokTypes;
	}
	walk(prod, prevRest = []) {
		if (!this.found) super.walk(prod, prevRest);
	}
	walkProdRef(refProd, currRest, prevRest) {
		if (refProd.referencedRule.name === this.nextProductionName && refProd.idx === this.nextProductionOccurrence) {
			const fullRest = currRest.concat(prevRest);
			this.updateExpectedNext();
			this.walk(refProd.referencedRule, fullRest);
		}
	}
	updateExpectedNext() {
		if (this.ruleStack.length === 0) {
			this.nextProductionName = "";
			this.nextProductionOccurrence = 0;
			this.isAtEndOfPath = true;
		} else {
			this.nextProductionName = this.ruleStack.pop();
			this.nextProductionOccurrence = this.occurrenceStack.pop();
		}
	}
};
var NextAfterTokenWalker = class extends AbstractNextPossibleTokensWalker {
	constructor(topProd, path) {
		super(topProd, path);
		this.path = path;
		this.nextTerminalName = "";
		this.nextTerminalOccurrence = 0;
		this.nextTerminalName = this.path.lastTok.name;
		this.nextTerminalOccurrence = this.path.lastTokOccurrence;
	}
	walkTerminal(terminal, currRest, prevRest) {
		if (this.isAtEndOfPath && terminal.terminalType.name === this.nextTerminalName && terminal.idx === this.nextTerminalOccurrence && !this.found) {
			const restProd = new Alternative({ definition: currRest.concat(prevRest) });
			this.possibleTokTypes = first(restProd);
			this.found = true;
		}
	}
};
/**
* This walker only "walks" a single "TOP" level in the Grammar Ast, this means
* it never "follows" production refs
*/
var AbstractNextTerminalAfterProductionWalker = class extends RestWalker {
	constructor(topRule, occurrence) {
		super();
		this.topRule = topRule;
		this.occurrence = occurrence;
		this.result = {
			token: void 0,
			occurrence: void 0,
			isEndOfRule: void 0
		};
	}
	startWalking() {
		this.walk(this.topRule);
		return this.result;
	}
};
var NextTerminalAfterManyWalker = class extends AbstractNextTerminalAfterProductionWalker {
	walkMany(manyProd, currRest, prevRest) {
		if (manyProd.idx === this.occurrence) {
			const firstAfterMany = currRest.concat(prevRest)[0];
			this.result.isEndOfRule = firstAfterMany === void 0;
			if (firstAfterMany instanceof Terminal) {
				this.result.token = firstAfterMany.terminalType;
				this.result.occurrence = firstAfterMany.idx;
			}
		} else super.walkMany(manyProd, currRest, prevRest);
	}
};
var NextTerminalAfterManySepWalker = class extends AbstractNextTerminalAfterProductionWalker {
	walkManySep(manySepProd, currRest, prevRest) {
		if (manySepProd.idx === this.occurrence) {
			const firstAfterManySep = currRest.concat(prevRest)[0];
			this.result.isEndOfRule = firstAfterManySep === void 0;
			if (firstAfterManySep instanceof Terminal) {
				this.result.token = firstAfterManySep.terminalType;
				this.result.occurrence = firstAfterManySep.idx;
			}
		} else super.walkManySep(manySepProd, currRest, prevRest);
	}
};
var NextTerminalAfterAtLeastOneWalker = class extends AbstractNextTerminalAfterProductionWalker {
	walkAtLeastOne(atLeastOneProd, currRest, prevRest) {
		if (atLeastOneProd.idx === this.occurrence) {
			const firstAfterAtLeastOne = currRest.concat(prevRest)[0];
			this.result.isEndOfRule = firstAfterAtLeastOne === void 0;
			if (firstAfterAtLeastOne instanceof Terminal) {
				this.result.token = firstAfterAtLeastOne.terminalType;
				this.result.occurrence = firstAfterAtLeastOne.idx;
			}
		} else super.walkAtLeastOne(atLeastOneProd, currRest, prevRest);
	}
};
var NextTerminalAfterAtLeastOneSepWalker = class extends AbstractNextTerminalAfterProductionWalker {
	walkAtLeastOneSep(atleastOneSepProd, currRest, prevRest) {
		if (atleastOneSepProd.idx === this.occurrence) {
			const firstAfterfirstAfterAtLeastOneSep = currRest.concat(prevRest)[0];
			this.result.isEndOfRule = firstAfterfirstAfterAtLeastOneSep === void 0;
			if (firstAfterfirstAfterAtLeastOneSep instanceof Terminal) {
				this.result.token = firstAfterfirstAfterAtLeastOneSep.terminalType;
				this.result.occurrence = firstAfterfirstAfterAtLeastOneSep.idx;
			}
		} else super.walkAtLeastOneSep(atleastOneSepProd, currRest, prevRest);
	}
};
function possiblePathsFrom(targetDef, maxLength, currPath = []) {
	currPath = [...currPath];
	let result = [];
	let i = 0;
	function remainingPathWith(nextDef) {
		return nextDef.concat(targetDef.slice(i + 1));
	}
	function getAlternativesForProd(definition) {
		const alternatives = possiblePathsFrom(remainingPathWith(definition), maxLength, currPath);
		return result.concat(alternatives);
	}
	/**
	* Mandatory productions will halt the loop as the paths computed from their recursive calls will already contain the
	* following (rest) of the targetDef.
	*
	* For optional productions (Option/Repetition/...) the loop will continue to represent the paths that do not include the
	* the optional production.
	*/
	while (currPath.length < maxLength && i < targetDef.length) {
		const prod = targetDef[i];
		/* istanbul ignore else */
		if (prod instanceof Alternative) return getAlternativesForProd(prod.definition);
		else if (prod instanceof NonTerminal) return getAlternativesForProd(prod.definition);
		else if (prod instanceof Option) result = getAlternativesForProd(prod.definition);
		else if (prod instanceof RepetitionMandatory) return getAlternativesForProd(prod.definition.concat([new Repetition({ definition: prod.definition })]));
		else if (prod instanceof RepetitionMandatoryWithSeparator) return getAlternativesForProd([new Alternative({ definition: prod.definition }), new Repetition({ definition: [new Terminal({ terminalType: prod.separator })].concat(prod.definition) })]);
		else if (prod instanceof RepetitionWithSeparator) result = getAlternativesForProd(prod.definition.concat([new Repetition({ definition: [new Terminal({ terminalType: prod.separator })].concat(prod.definition) })]));
		else if (prod instanceof Repetition) result = getAlternativesForProd(prod.definition.concat([new Repetition({ definition: prod.definition })]));
		else if (prod instanceof Alternation) {
			prod.definition.forEach((currAlt) => {
				if (currAlt.definition.length !== 0) result = getAlternativesForProd(currAlt.definition);
			});
			return result;
		} else if (prod instanceof Terminal) currPath.push(prod.terminalType);
		else throw Error("non exhaustive match");
		i++;
	}
	result.push({
		partialPath: currPath,
		suffixDef: targetDef.slice(i)
	});
	return result;
}
function nextPossibleTokensAfter(initialDef, tokenVector, tokMatcher, maxLookAhead) {
	const EXIT_NON_TERMINAL = "EXIT_NONE_TERMINAL";
	const EXIT_NON_TERMINAL_ARR = [EXIT_NON_TERMINAL];
	const EXIT_ALTERNATIVE = "EXIT_ALTERNATIVE";
	let foundCompletePath = false;
	const tokenVectorLength = tokenVector.length;
	const minimalAlternativesIndex = tokenVectorLength - maxLookAhead - 1;
	const result = [];
	const possiblePaths = [];
	possiblePaths.push({
		idx: -1,
		def: initialDef,
		ruleStack: [],
		occurrenceStack: []
	});
	while (possiblePaths.length !== 0) {
		const currPath = possiblePaths.pop();
		if (currPath === EXIT_ALTERNATIVE) {
			if (foundCompletePath && possiblePaths.at(-1).idx <= minimalAlternativesIndex) possiblePaths.pop();
			continue;
		}
		const currDef = currPath.def;
		const currIdx = currPath.idx;
		const currRuleStack = currPath.ruleStack;
		const currOccurrenceStack = currPath.occurrenceStack;
		if (currDef.length === 0) continue;
		const prod = currDef[0];
		/* istanbul ignore else */
		if (prod === EXIT_NON_TERMINAL) {
			const nextPath = {
				idx: currIdx,
				def: currDef.slice(1),
				ruleStack: currRuleStack.slice(0, -1),
				occurrenceStack: currOccurrenceStack.slice(0, -1)
			};
			possiblePaths.push(nextPath);
		} else if (prod instanceof Terminal)
 /* istanbul ignore else */
		if (currIdx < tokenVectorLength - 1) {
			const nextIdx = currIdx + 1;
			const actualToken = tokenVector[nextIdx];
			if (tokMatcher(actualToken, prod.terminalType)) {
				const nextPath = {
					idx: nextIdx,
					def: currDef.slice(1),
					ruleStack: currRuleStack,
					occurrenceStack: currOccurrenceStack
				};
				possiblePaths.push(nextPath);
			}
		} else if (currIdx === tokenVectorLength - 1) {
			result.push({
				nextTokenType: prod.terminalType,
				nextTokenOccurrence: prod.idx,
				ruleStack: currRuleStack,
				occurrenceStack: currOccurrenceStack
			});
			foundCompletePath = true;
		} else throw Error("non exhaustive match");
		else if (prod instanceof NonTerminal) {
			const newRuleStack = [...currRuleStack];
			newRuleStack.push(prod.nonTerminalName);
			const newOccurrenceStack = [...currOccurrenceStack];
			newOccurrenceStack.push(prod.idx);
			const nextPath = {
				idx: currIdx,
				def: prod.definition.concat(EXIT_NON_TERMINAL_ARR, currDef.slice(1)),
				ruleStack: newRuleStack,
				occurrenceStack: newOccurrenceStack
			};
			possiblePaths.push(nextPath);
		} else if (prod instanceof Option) {
			const nextPathWithout = {
				idx: currIdx,
				def: currDef.slice(1),
				ruleStack: currRuleStack,
				occurrenceStack: currOccurrenceStack
			};
			possiblePaths.push(nextPathWithout);
			possiblePaths.push(EXIT_ALTERNATIVE);
			const nextPathWith = {
				idx: currIdx,
				def: prod.definition.concat(currDef.slice(1)),
				ruleStack: currRuleStack,
				occurrenceStack: currOccurrenceStack
			};
			possiblePaths.push(nextPathWith);
		} else if (prod instanceof RepetitionMandatory) {
			const secondIteration = new Repetition({
				definition: prod.definition,
				idx: prod.idx
			});
			const nextPath = {
				idx: currIdx,
				def: prod.definition.concat([secondIteration], currDef.slice(1)),
				ruleStack: currRuleStack,
				occurrenceStack: currOccurrenceStack
			};
			possiblePaths.push(nextPath);
		} else if (prod instanceof RepetitionMandatoryWithSeparator) {
			const secondIteration = new Repetition({
				definition: [new Terminal({ terminalType: prod.separator })].concat(prod.definition),
				idx: prod.idx
			});
			const nextPath = {
				idx: currIdx,
				def: prod.definition.concat([secondIteration], currDef.slice(1)),
				ruleStack: currRuleStack,
				occurrenceStack: currOccurrenceStack
			};
			possiblePaths.push(nextPath);
		} else if (prod instanceof RepetitionWithSeparator) {
			const nextPathWithout = {
				idx: currIdx,
				def: currDef.slice(1),
				ruleStack: currRuleStack,
				occurrenceStack: currOccurrenceStack
			};
			possiblePaths.push(nextPathWithout);
			possiblePaths.push(EXIT_ALTERNATIVE);
			const nthRepetition = new Repetition({
				definition: [new Terminal({ terminalType: prod.separator })].concat(prod.definition),
				idx: prod.idx
			});
			const nextPathWith = {
				idx: currIdx,
				def: prod.definition.concat([nthRepetition], currDef.slice(1)),
				ruleStack: currRuleStack,
				occurrenceStack: currOccurrenceStack
			};
			possiblePaths.push(nextPathWith);
		} else if (prod instanceof Repetition) {
			const nextPathWithout = {
				idx: currIdx,
				def: currDef.slice(1),
				ruleStack: currRuleStack,
				occurrenceStack: currOccurrenceStack
			};
			possiblePaths.push(nextPathWithout);
			possiblePaths.push(EXIT_ALTERNATIVE);
			const nthRepetition = new Repetition({
				definition: prod.definition,
				idx: prod.idx
			});
			const nextPathWith = {
				idx: currIdx,
				def: prod.definition.concat([nthRepetition], currDef.slice(1)),
				ruleStack: currRuleStack,
				occurrenceStack: currOccurrenceStack
			};
			possiblePaths.push(nextPathWith);
		} else if (prod instanceof Alternation) for (let i = prod.definition.length - 1; i >= 0; i--) {
			const currAltPath = {
				idx: currIdx,
				def: prod.definition[i].definition.concat(currDef.slice(1)),
				ruleStack: currRuleStack,
				occurrenceStack: currOccurrenceStack
			};
			possiblePaths.push(currAltPath);
			possiblePaths.push(EXIT_ALTERNATIVE);
		}
		else if (prod instanceof Alternative) possiblePaths.push({
			idx: currIdx,
			def: prod.definition.concat(currDef.slice(1)),
			ruleStack: currRuleStack,
			occurrenceStack: currOccurrenceStack
		});
		else if (prod instanceof Rule) possiblePaths.push(expandTopLevelRule(prod, currIdx, currRuleStack, currOccurrenceStack));
		else throw Error("non exhaustive match");
	}
	return result;
}
function expandTopLevelRule(topRule, currIdx, currRuleStack, currOccurrenceStack) {
	const newRuleStack = [...currRuleStack];
	newRuleStack.push(topRule.name);
	const newCurrOccurrenceStack = [...currOccurrenceStack];
	newCurrOccurrenceStack.push(1);
	return {
		idx: currIdx,
		def: topRule.definition,
		ruleStack: newRuleStack,
		occurrenceStack: newCurrOccurrenceStack
	};
}
var PROD_TYPE;
(function(PROD_TYPE) {
	PROD_TYPE[PROD_TYPE["OPTION"] = 0] = "OPTION";
	PROD_TYPE[PROD_TYPE["REPETITION"] = 1] = "REPETITION";
	PROD_TYPE[PROD_TYPE["REPETITION_MANDATORY"] = 2] = "REPETITION_MANDATORY";
	PROD_TYPE[PROD_TYPE["REPETITION_MANDATORY_WITH_SEPARATOR"] = 3] = "REPETITION_MANDATORY_WITH_SEPARATOR";
	PROD_TYPE[PROD_TYPE["REPETITION_WITH_SEPARATOR"] = 4] = "REPETITION_WITH_SEPARATOR";
	PROD_TYPE[PROD_TYPE["ALTERNATION"] = 5] = "ALTERNATION";
})(PROD_TYPE || (PROD_TYPE = {}));
function getProdType(prod) {
	/* istanbul ignore else */
	if (prod instanceof Option || prod === "Option") return PROD_TYPE.OPTION;
	else if (prod instanceof Repetition || prod === "Repetition") return PROD_TYPE.REPETITION;
	else if (prod instanceof RepetitionMandatory || prod === "RepetitionMandatory") return PROD_TYPE.REPETITION_MANDATORY;
	else if (prod instanceof RepetitionMandatoryWithSeparator || prod === "RepetitionMandatoryWithSeparator") return PROD_TYPE.REPETITION_MANDATORY_WITH_SEPARATOR;
	else if (prod instanceof RepetitionWithSeparator || prod === "RepetitionWithSeparator") return PROD_TYPE.REPETITION_WITH_SEPARATOR;
	else if (prod instanceof Alternation || prod === "Alternation") return PROD_TYPE.ALTERNATION;
	else throw Error("non exhaustive match");
}
function getLookaheadPaths(options) {
	const { occurrence, rule, prodType, maxLookahead } = options;
	const type = getProdType(prodType);
	if (type === PROD_TYPE.ALTERNATION) return getLookaheadPathsForOr(occurrence, rule, maxLookahead);
	else return getLookaheadPathsForOptionalProd(occurrence, rule, type, maxLookahead);
}
function buildLookaheadFuncForOr(occurrence, ruleGrammar, maxLookahead, hasPredicates, dynamicTokensEnabled, laFuncBuilder) {
	const lookAheadPaths = getLookaheadPathsForOr(occurrence, ruleGrammar, maxLookahead);
	return laFuncBuilder(lookAheadPaths, hasPredicates, areTokenCategoriesNotUsed(lookAheadPaths) ? tokenStructuredMatcherNoCategories : tokenStructuredMatcher, dynamicTokensEnabled);
}
/**
*  When dealing with an Optional production (OPTION/MANY/2nd iteration of AT_LEAST_ONE/...) we need to compare
*  the lookahead "inside" the production and the lookahead immediately "after" it in the same top level rule (context free).
*
*  Example: given a production:
*  ABC(DE)?DF
*
*  The optional '(DE)?' should only be entered if we see 'DE'. a single Token 'D' is not sufficient to distinguish between the two
*  alternatives.
*
*  @returns A Lookahead function which will return true IFF the parser should parse the Optional production.
*/
function buildLookaheadFuncForOptionalProd(occurrence, ruleGrammar, k, dynamicTokensEnabled, prodType, lookaheadBuilder) {
	const lookAheadPaths = getLookaheadPathsForOptionalProd(occurrence, ruleGrammar, prodType, k);
	const tokenMatcher = areTokenCategoriesNotUsed(lookAheadPaths) ? tokenStructuredMatcherNoCategories : tokenStructuredMatcher;
	return lookaheadBuilder(lookAheadPaths[0], tokenMatcher, dynamicTokensEnabled);
}
function buildAlternativesLookAheadFunc(alts, hasPredicates, tokenMatcher, dynamicTokensEnabled) {
	const numOfAlts = alts.length;
	const areAllOneTokenLookahead = alts.every((currAlt) => {
		return currAlt.every((currPath) => {
			return currPath.length === 1;
		});
	});
	if (hasPredicates)
 /**
	* @returns {number} - The chosen alternative index
	*/
	return function(orAlts) {
		const predicates = orAlts.map((currAlt) => currAlt.GATE);
		for (let t = 0; t < numOfAlts; t++) {
			const currAlt = alts[t];
			const currNumOfPaths = currAlt.length;
			const currPredicate = predicates[t];
			if (currPredicate !== void 0 && currPredicate.call(this) === false) continue;
			nextPath: for (let j = 0; j < currNumOfPaths; j++) {
				const currPath = currAlt[j];
				const currPathLength = currPath.length;
				for (let i = 0; i < currPathLength; i++) if (tokenMatcher(this.LA_FAST(i + 1), currPath[i]) === false) continue nextPath;
				return t;
			}
		}
	};
	else if (areAllOneTokenLookahead && !dynamicTokensEnabled) {
		const choiceToAlt = alts.map((currAlt) => {
			return currAlt.flat();
		}).reduce((result, currAlt, idx) => {
			currAlt.forEach((currTokType) => {
				if (!(currTokType.tokenTypeIdx in result)) result[currTokType.tokenTypeIdx] = idx;
				currTokType.categoryMatches.forEach((currExtendingType) => {
					if (!Object.hasOwn(result, currExtendingType)) result[currExtendingType] = idx;
				});
			});
			return result;
		}, {});
		/**
		* @returns {number} - The chosen alternative index
		*/
		return function() {
			return choiceToAlt[this.LA_FAST(1).tokenTypeIdx];
		};
	} else
 /**
	* @returns {number} - The chosen alternative index
	*/
	return function() {
		for (let t = 0; t < numOfAlts; t++) {
			const currAlt = alts[t];
			const currNumOfPaths = currAlt.length;
			nextPath: for (let j = 0; j < currNumOfPaths; j++) {
				const currPath = currAlt[j];
				const currPathLength = currPath.length;
				for (let i = 0; i < currPathLength; i++) if (tokenMatcher(this.LA_FAST(i + 1), currPath[i]) === false) continue nextPath;
				return t;
			}
		}
	};
}
function buildSingleAlternativeLookaheadFunction(alt, tokenMatcher, dynamicTokensEnabled) {
	const areAllOneTokenLookahead = alt.every((currPath) => {
		return currPath.length === 1;
	});
	const numOfPaths = alt.length;
	if (areAllOneTokenLookahead && !dynamicTokensEnabled) {
		const singleTokensTypes = alt.flat();
		if (singleTokensTypes.length === 1 && singleTokensTypes[0].categoryMatches.length === 0) {
			const expectedTokenUniqueKey = singleTokensTypes[0].tokenTypeIdx;
			return function() {
				return this.LA_FAST(1).tokenTypeIdx === expectedTokenUniqueKey;
			};
		} else {
			const choiceToAlt = singleTokensTypes.reduce((result, currTokType, idx) => {
				result[currTokType.tokenTypeIdx] = true;
				currTokType.categoryMatches.forEach((currExtendingType) => {
					result[currExtendingType] = true;
				});
				return result;
			}, []);
			return function() {
				return choiceToAlt[this.LA_FAST(1).tokenTypeIdx] === true;
			};
		}
	} else return function() {
		nextPath: for (let j = 0; j < numOfPaths; j++) {
			const currPath = alt[j];
			const currPathLength = currPath.length;
			for (let i = 0; i < currPathLength; i++) if (tokenMatcher(this.LA_FAST(i + 1), currPath[i]) === false) continue nextPath;
			return true;
		}
		return false;
	};
}
var RestDefinitionFinderWalker = class extends RestWalker {
	constructor(topProd, targetOccurrence, targetProdType) {
		super();
		this.topProd = topProd;
		this.targetOccurrence = targetOccurrence;
		this.targetProdType = targetProdType;
	}
	startWalking() {
		this.walk(this.topProd);
		return this.restDef;
	}
	checkIsTarget(node, expectedProdType, currRest, prevRest) {
		if (node.idx === this.targetOccurrence && this.targetProdType === expectedProdType) {
			this.restDef = currRest.concat(prevRest);
			return true;
		}
		return false;
	}
	walkOption(optionProd, currRest, prevRest) {
		if (!this.checkIsTarget(optionProd, PROD_TYPE.OPTION, currRest, prevRest)) super.walkOption(optionProd, currRest, prevRest);
	}
	walkAtLeastOne(atLeastOneProd, currRest, prevRest) {
		if (!this.checkIsTarget(atLeastOneProd, PROD_TYPE.REPETITION_MANDATORY, currRest, prevRest)) super.walkOption(atLeastOneProd, currRest, prevRest);
	}
	walkAtLeastOneSep(atLeastOneSepProd, currRest, prevRest) {
		if (!this.checkIsTarget(atLeastOneSepProd, PROD_TYPE.REPETITION_MANDATORY_WITH_SEPARATOR, currRest, prevRest)) super.walkOption(atLeastOneSepProd, currRest, prevRest);
	}
	walkMany(manyProd, currRest, prevRest) {
		if (!this.checkIsTarget(manyProd, PROD_TYPE.REPETITION, currRest, prevRest)) super.walkOption(manyProd, currRest, prevRest);
	}
	walkManySep(manySepProd, currRest, prevRest) {
		if (!this.checkIsTarget(manySepProd, PROD_TYPE.REPETITION_WITH_SEPARATOR, currRest, prevRest)) super.walkOption(manySepProd, currRest, prevRest);
	}
};
/**
* Returns the definition of a target production in a top level level rule.
*/
var InsideDefinitionFinderVisitor = class extends GAstVisitor {
	constructor(targetOccurrence, targetProdType, targetRef) {
		super();
		this.targetOccurrence = targetOccurrence;
		this.targetProdType = targetProdType;
		this.targetRef = targetRef;
		this.result = [];
	}
	checkIsTarget(node, expectedProdName) {
		if (node.idx === this.targetOccurrence && this.targetProdType === expectedProdName && (this.targetRef === void 0 || node === this.targetRef)) this.result = node.definition;
	}
	visitOption(node) {
		this.checkIsTarget(node, PROD_TYPE.OPTION);
	}
	visitRepetition(node) {
		this.checkIsTarget(node, PROD_TYPE.REPETITION);
	}
	visitRepetitionMandatory(node) {
		this.checkIsTarget(node, PROD_TYPE.REPETITION_MANDATORY);
	}
	visitRepetitionMandatoryWithSeparator(node) {
		this.checkIsTarget(node, PROD_TYPE.REPETITION_MANDATORY_WITH_SEPARATOR);
	}
	visitRepetitionWithSeparator(node) {
		this.checkIsTarget(node, PROD_TYPE.REPETITION_WITH_SEPARATOR);
	}
	visitAlternation(node) {
		this.checkIsTarget(node, PROD_TYPE.ALTERNATION);
	}
};
function initializeArrayOfArrays(size) {
	const result = new Array(size);
	for (let i = 0; i < size; i++) result[i] = [];
	return result;
}
/**
* A sort of hash function between a Path in the grammar and a string.
* Note that this returns multiple "hashes" to support the scenario of token categories.
* -  A single path with categories may match multiple **actual** paths.
*/
function pathToHashKeys(path) {
	let keys = [""];
	for (let i = 0; i < path.length; i++) {
		const tokType = path[i];
		const longerKeys = [];
		for (let j = 0; j < keys.length; j++) {
			const currShorterKey = keys[j];
			longerKeys.push(currShorterKey + "_" + tokType.tokenTypeIdx);
			for (let t = 0; t < tokType.categoryMatches.length; t++) {
				const categoriesKeySuffix = "_" + tokType.categoryMatches[t];
				longerKeys.push(currShorterKey + categoriesKeySuffix);
			}
		}
		keys = longerKeys;
	}
	return keys;
}
/**
* Imperative style due to being called from a hot spot
*/
function isUniquePrefixHash(altKnownPathsKeys, searchPathKeys, idx) {
	for (let currAltIdx = 0; currAltIdx < altKnownPathsKeys.length; currAltIdx++) {
		if (currAltIdx === idx) continue;
		const otherAltKnownPathsKeys = altKnownPathsKeys[currAltIdx];
		for (let searchIdx = 0; searchIdx < searchPathKeys.length; searchIdx++) if (otherAltKnownPathsKeys[searchPathKeys[searchIdx]] === true) return false;
	}
	return true;
}
function lookAheadSequenceFromAlternatives(altsDefs, k) {
	const partialAlts = altsDefs.map((currAlt) => possiblePathsFrom([currAlt], 1));
	const finalResult = initializeArrayOfArrays(partialAlts.length);
	const altsHashes = partialAlts.map((currAltPaths) => {
		const dict = {};
		currAltPaths.forEach((item) => {
			pathToHashKeys(item.partialPath).forEach((currKey) => {
				dict[currKey] = true;
			});
		});
		return dict;
	});
	let newData = partialAlts;
	for (let pathLength = 1; pathLength <= k; pathLength++) {
		const currDataset = newData;
		newData = initializeArrayOfArrays(currDataset.length);
		for (let altIdx = 0; altIdx < currDataset.length; altIdx++) {
			const currAltPathsAndSuffixes = currDataset[altIdx];
			for (let currPathIdx = 0; currPathIdx < currAltPathsAndSuffixes.length; currPathIdx++) {
				const currPathPrefix = currAltPathsAndSuffixes[currPathIdx].partialPath;
				const suffixDef = currAltPathsAndSuffixes[currPathIdx].suffixDef;
				const prefixKeys = pathToHashKeys(currPathPrefix);
				if (isUniquePrefixHash(altsHashes, prefixKeys, altIdx) || suffixDef.length === 0 || currPathPrefix.length === k) {
					const currAltResult = finalResult[altIdx];
					if (containsPath(currAltResult, currPathPrefix) === false) {
						currAltResult.push(currPathPrefix);
						for (let j = 0; j < prefixKeys.length; j++) {
							const currKey = prefixKeys[j];
							altsHashes[altIdx][currKey] = true;
						}
					}
				} else {
					const newPartialPathsAndSuffixes = possiblePathsFrom(suffixDef, pathLength + 1, currPathPrefix);
					newData[altIdx] = newData[altIdx].concat(newPartialPathsAndSuffixes);
					newPartialPathsAndSuffixes.forEach((item) => {
						pathToHashKeys(item.partialPath).forEach((key) => {
							altsHashes[altIdx][key] = true;
						});
					});
				}
			}
		}
	}
	return finalResult;
}
function getLookaheadPathsForOr(occurrence, ruleGrammar, k, orProd) {
	const visitor = new InsideDefinitionFinderVisitor(occurrence, PROD_TYPE.ALTERNATION, orProd);
	ruleGrammar.accept(visitor);
	return lookAheadSequenceFromAlternatives(visitor.result, k);
}
function getLookaheadPathsForOptionalProd(occurrence, ruleGrammar, prodType, k) {
	const insideDefVisitor = new InsideDefinitionFinderVisitor(occurrence, prodType);
	ruleGrammar.accept(insideDefVisitor);
	const insideDef = insideDefVisitor.result;
	const afterDef = new RestDefinitionFinderWalker(ruleGrammar, occurrence, prodType).startWalking();
	return lookAheadSequenceFromAlternatives([new Alternative({ definition: insideDef }), new Alternative({ definition: afterDef })], k);
}
function containsPath(alternative, searchPath) {
	compareOtherPath: for (let i = 0; i < alternative.length; i++) {
		const otherPath = alternative[i];
		if (otherPath.length !== searchPath.length) continue;
		for (let j = 0; j < otherPath.length; j++) {
			const searchTok = searchPath[j];
			const otherTok = otherPath[j];
			if ((searchTok === otherTok || otherTok.categoryMatchesMap[searchTok.tokenTypeIdx] !== void 0) === false) continue compareOtherPath;
		}
		return true;
	}
	return false;
}
function isStrictPrefixOfPath(prefix, other) {
	return prefix.length < other.length && prefix.every((tokType, idx) => {
		const otherTokType = other[idx];
		return tokType === otherTokType || otherTokType.categoryMatchesMap[tokType.tokenTypeIdx];
	});
}
function areTokenCategoriesNotUsed(lookAheadPaths) {
	return lookAheadPaths.every((singleAltPaths) => singleAltPaths.every((singlePath) => singlePath.every((token) => token.categoryMatches.length === 0)));
}
function validateLookahead(options) {
	return options.lookaheadStrategy.validate({
		rules: options.rules,
		tokenTypes: options.tokenTypes,
		grammarName: options.grammarName
	}).map((errorMessage) => Object.assign({ type: ParserDefinitionErrorType.CUSTOM_LOOKAHEAD_VALIDATION }, errorMessage));
}
function validateGrammar$1(topLevels, tokenTypes, errMsgProvider, grammarName) {
	const duplicateErrors = topLevels.flatMap((currTopLevel) => validateDuplicateProductions(currTopLevel, errMsgProvider));
	const termsNamespaceConflictErrors = checkTerminalAndNoneTerminalsNameSpace(topLevels, tokenTypes, errMsgProvider);
	const tooManyAltsErrors = topLevels.flatMap((curRule) => validateTooManyAlts(curRule, errMsgProvider));
	const duplicateRulesError = topLevels.flatMap((curRule) => validateRuleDoesNotAlreadyExist(curRule, topLevels, grammarName, errMsgProvider));
	return duplicateErrors.concat(termsNamespaceConflictErrors, tooManyAltsErrors, duplicateRulesError);
}
function validateDuplicateProductions(topLevelRule, errMsgProvider) {
	const collectorVisitor = new OccurrenceValidationCollector();
	topLevelRule.accept(collectorVisitor);
	const allRuleProductions = collectorVisitor.allProductions;
	const productionGroups = Object.groupBy(allRuleProductions, identifyProductionForDuplicates);
	const duplicates = Object.fromEntries(Object.entries(productionGroups).filter(([_k, currGroup]) => currGroup.length > 1));
	return Object.values(duplicates).map((currDuplicates) => {
		const firstProd = currDuplicates[0];
		const msg = errMsgProvider.buildDuplicateFoundError(topLevelRule, currDuplicates);
		const dslName = getProductionDslName(firstProd);
		const defError = {
			message: msg,
			type: ParserDefinitionErrorType.DUPLICATE_PRODUCTIONS,
			ruleName: topLevelRule.name,
			dslName,
			occurrence: firstProd.idx
		};
		const param = getExtraProductionArgument(firstProd);
		if (param) defError.parameter = param;
		return defError;
	});
}
function identifyProductionForDuplicates(prod) {
	return `${getProductionDslName(prod)}_#_${prod.idx}_#_${getExtraProductionArgument(prod)}`;
}
function getExtraProductionArgument(prod) {
	if (prod instanceof Terminal) return prod.terminalType.name;
	else if (prod instanceof NonTerminal) return prod.nonTerminalName;
	else return "";
}
var OccurrenceValidationCollector = class extends GAstVisitor {
	constructor() {
		super(...arguments);
		this.allProductions = [];
	}
	visitNonTerminal(subrule) {
		this.allProductions.push(subrule);
	}
	visitOption(option) {
		this.allProductions.push(option);
	}
	visitRepetitionWithSeparator(manySep) {
		this.allProductions.push(manySep);
	}
	visitRepetitionMandatory(atLeastOne) {
		this.allProductions.push(atLeastOne);
	}
	visitRepetitionMandatoryWithSeparator(atLeastOneSep) {
		this.allProductions.push(atLeastOneSep);
	}
	visitRepetition(many) {
		this.allProductions.push(many);
	}
	visitAlternation(or) {
		this.allProductions.push(or);
	}
	visitTerminal(terminal) {
		this.allProductions.push(terminal);
	}
};
function validateRuleDoesNotAlreadyExist(rule, allRules, className, errMsgProvider) {
	const errors = [];
	if (allRules.reduce((result, curRule) => {
		if (curRule.name === rule.name) return result + 1;
		return result;
	}, 0) > 1) {
		const errMsg = errMsgProvider.buildDuplicateRuleNameError({
			topLevelRule: rule,
			grammarName: className
		});
		errors.push({
			message: errMsg,
			type: ParserDefinitionErrorType.DUPLICATE_RULE_NAME,
			ruleName: rule.name
		});
	}
	return errors;
}
function validateRuleIsOverridden(ruleName, definedRulesNames, className) {
	const errors = [];
	let errMsg;
	if (!definedRulesNames.includes(ruleName)) {
		errMsg = `Invalid rule override, rule: ->${ruleName}<- cannot be overridden in the grammar: ->${className}<-as it is not defined in any of the super grammars `;
		errors.push({
			message: errMsg,
			type: ParserDefinitionErrorType.INVALID_RULE_OVERRIDE,
			ruleName
		});
	}
	return errors;
}
function validateNoLeftRecursion(topRule, currRule, errMsgProvider, path = []) {
	const errors = [];
	const nextNonTerminals = getFirstNoneTerminal(currRule.definition);
	if (nextNonTerminals.length === 0) return [];
	else {
		const ruleName = topRule.name;
		if (nextNonTerminals.includes(topRule)) errors.push({
			message: errMsgProvider.buildLeftRecursionError({
				topLevelRule: topRule,
				leftRecursionPath: path
			}),
			type: ParserDefinitionErrorType.LEFT_RECURSION,
			ruleName
		});
		const excluded = path.concat([topRule]);
		const errorsFromNextSteps = nextNonTerminals.filter((x) => !excluded.includes(x)).flatMap((currRefRule) => {
			const newPath = [...path];
			newPath.push(currRefRule);
			return validateNoLeftRecursion(topRule, currRefRule, errMsgProvider, newPath);
		});
		return errors.concat(errorsFromNextSteps);
	}
}
function getFirstNoneTerminal(definition) {
	let result = [];
	if (definition.length === 0) return result;
	const firstProd = definition[0];
	/* istanbul ignore else */
	if (firstProd instanceof NonTerminal) result.push(firstProd.referencedRule);
	else if (firstProd instanceof Alternative || firstProd instanceof Option || firstProd instanceof RepetitionMandatory || firstProd instanceof RepetitionMandatoryWithSeparator || firstProd instanceof RepetitionWithSeparator || firstProd instanceof Repetition) result = result.concat(getFirstNoneTerminal(firstProd.definition));
	else if (firstProd instanceof Alternation) result = firstProd.definition.map((currSubDef) => getFirstNoneTerminal(currSubDef.definition)).flat();
	else if (firstProd instanceof Terminal) {} else throw Error("non exhaustive match");
	const isFirstOptional = isOptionalProd(firstProd);
	const hasMore = definition.length > 1;
	if (isFirstOptional && hasMore) {
		const rest = definition.slice(1);
		return result.concat(getFirstNoneTerminal(rest));
	} else return result;
}
var OrCollector = class extends GAstVisitor {
	constructor() {
		super(...arguments);
		this.alternations = [];
	}
	visitAlternation(node) {
		this.alternations.push(node);
	}
};
function validateEmptyOrAlternative(topLevelRule, errMsgProvider) {
	const orCollector = new OrCollector();
	topLevelRule.accept(orCollector);
	return orCollector.alternations.flatMap((currOr) => {
		return currOr.definition.slice(0, -1).flatMap((currAlternative, currAltIdx) => {
			if (nextPossibleTokensAfter([currAlternative], [], tokenStructuredMatcher, 1).length === 0) return [{
				message: errMsgProvider.buildEmptyAlternationError({
					topLevelRule,
					alternation: currOr,
					emptyChoiceIdx: currAltIdx
				}),
				type: ParserDefinitionErrorType.NONE_LAST_EMPTY_ALT,
				ruleName: topLevelRule.name,
				occurrence: currOr.idx,
				alternative: currAltIdx + 1
			}];
			else return [];
		});
	});
}
function validateAmbiguousAlternationAlternatives(topLevelRule, globalMaxLookahead, errMsgProvider) {
	const orCollector = new OrCollector();
	topLevelRule.accept(orCollector);
	let ors = orCollector.alternations;
	ors = ors.filter((currOr) => currOr.ignoreAmbiguities !== true);
	return ors.flatMap((currOr) => {
		const currOccurrence = currOr.idx;
		const alternatives = getLookaheadPathsForOr(currOccurrence, topLevelRule, currOr.maxLookahead || globalMaxLookahead, currOr);
		const altsAmbiguityErrors = checkAlternativesAmbiguities(alternatives, currOr, topLevelRule, errMsgProvider);
		const altsPrefixAmbiguityErrors = checkPrefixAlternativesAmbiguities(alternatives, currOr, topLevelRule, errMsgProvider);
		return altsAmbiguityErrors.concat(altsPrefixAmbiguityErrors);
	});
}
var RepetitionCollector = class extends GAstVisitor {
	constructor() {
		super(...arguments);
		this.allProductions = [];
	}
	visitRepetitionWithSeparator(manySep) {
		this.allProductions.push(manySep);
	}
	visitRepetitionMandatory(atLeastOne) {
		this.allProductions.push(atLeastOne);
	}
	visitRepetitionMandatoryWithSeparator(atLeastOneSep) {
		this.allProductions.push(atLeastOneSep);
	}
	visitRepetition(many) {
		this.allProductions.push(many);
	}
};
function validateTooManyAlts(topLevelRule, errMsgProvider) {
	const orCollector = new OrCollector();
	topLevelRule.accept(orCollector);
	return orCollector.alternations.flatMap((currOr) => {
		if (currOr.definition.length > 255) return [{
			message: errMsgProvider.buildTooManyAlternativesError({
				topLevelRule,
				alternation: currOr
			}),
			type: ParserDefinitionErrorType.TOO_MANY_ALTS,
			ruleName: topLevelRule.name,
			occurrence: currOr.idx
		}];
		else return [];
	});
}
function validateSomeNonEmptyLookaheadPath(topLevelRules, maxLookahead, errMsgProvider) {
	const errors = [];
	topLevelRules.forEach((currTopRule) => {
		const collectorVisitor = new RepetitionCollector();
		currTopRule.accept(collectorVisitor);
		collectorVisitor.allProductions.forEach((currProd) => {
			const prodType = getProdType(currProd);
			const actualMaxLookahead = currProd.maxLookahead || maxLookahead;
			const currOccurrence = currProd.idx;
			if (getLookaheadPathsForOptionalProd(currOccurrence, currTopRule, prodType, actualMaxLookahead)[0].flat().length === 0) {
				const errMsg = errMsgProvider.buildEmptyRepetitionError({
					topLevelRule: currTopRule,
					repetition: currProd
				});
				errors.push({
					message: errMsg,
					type: ParserDefinitionErrorType.NO_NON_EMPTY_LOOKAHEAD,
					ruleName: currTopRule.name
				});
			}
		});
	});
	return errors;
}
function checkAlternativesAmbiguities(alternatives, alternation, rule, errMsgProvider) {
	const foundAmbiguousPaths = [];
	return alternatives.reduce((result, currAlt, currAltIdx) => {
		if (alternation.definition[currAltIdx].ignoreAmbiguities === true) return result;
		currAlt.forEach((currPath) => {
			const altsCurrPathAppearsIn = [currAltIdx];
			alternatives.forEach((currOtherAlt, currOtherAltIdx) => {
				if (currAltIdx !== currOtherAltIdx && containsPath(currOtherAlt, currPath) && alternation.definition[currOtherAltIdx].ignoreAmbiguities !== true) altsCurrPathAppearsIn.push(currOtherAltIdx);
			});
			if (altsCurrPathAppearsIn.length > 1 && !containsPath(foundAmbiguousPaths, currPath)) {
				foundAmbiguousPaths.push(currPath);
				result.push({
					alts: altsCurrPathAppearsIn,
					path: currPath
				});
			}
		});
		return result;
	}, []).map((currAmbDescriptor) => {
		const ambgIndices = currAmbDescriptor.alts.map((currAltIdx) => currAltIdx + 1);
		return {
			message: errMsgProvider.buildAlternationAmbiguityError({
				topLevelRule: rule,
				alternation,
				ambiguityIndices: ambgIndices,
				prefixPath: currAmbDescriptor.path
			}),
			type: ParserDefinitionErrorType.AMBIGUOUS_ALTS,
			ruleName: rule.name,
			occurrence: alternation.idx,
			alternatives: currAmbDescriptor.alts
		};
	});
}
function checkPrefixAlternativesAmbiguities(alternatives, alternation, rule, errMsgProvider) {
	const pathsAndIndices = alternatives.reduce((result, currAlt, idx) => {
		const currPathsAndIdx = currAlt.map((currPath) => {
			return {
				idx,
				path: currPath
			};
		});
		return result.concat(currPathsAndIdx);
	}, []);
	return pathsAndIndices.flatMap((currPathAndIdx) => {
		if (alternation.definition[currPathAndIdx.idx].ignoreAmbiguities === true) return [];
		const targetIdx = currPathAndIdx.idx;
		const targetPath = currPathAndIdx.path;
		return pathsAndIndices.filter((searchPathAndIdx) => {
			return alternation.definition[searchPathAndIdx.idx].ignoreAmbiguities !== true && searchPathAndIdx.idx < targetIdx && isStrictPrefixOfPath(searchPathAndIdx.path, targetPath);
		}).map((currAmbPathAndIdx) => {
			const ambgIndices = [currAmbPathAndIdx.idx + 1, targetIdx + 1];
			const occurrence = alternation.idx === 0 ? "" : alternation.idx;
			return {
				message: errMsgProvider.buildAlternationPrefixAmbiguityError({
					topLevelRule: rule,
					alternation,
					ambiguityIndices: ambgIndices,
					prefixPath: currAmbPathAndIdx.path
				}),
				type: ParserDefinitionErrorType.AMBIGUOUS_PREFIX_ALTS,
				ruleName: rule.name,
				occurrence,
				alternatives: ambgIndices
			};
		});
	});
}
function checkTerminalAndNoneTerminalsNameSpace(topLevels, tokenTypes, errMsgProvider) {
	const errors = [];
	const tokenNames = tokenTypes.map((currToken) => currToken.name);
	topLevels.forEach((currRule) => {
		const currRuleName = currRule.name;
		if (tokenNames.includes(currRuleName)) {
			const errMsg = errMsgProvider.buildNamespaceConflictError(currRule);
			errors.push({
				message: errMsg,
				type: ParserDefinitionErrorType.CONFLICT_TOKENS_RULES_NAMESPACE,
				ruleName: currRuleName
			});
		}
	});
	return errors;
}
function resolveGrammar(options) {
	const actualOptions = Object.assign({ errMsgProvider: defaultGrammarResolverErrorProvider }, options);
	const topRulesTable = {};
	options.rules.forEach((rule) => {
		topRulesTable[rule.name] = rule;
	});
	return resolveGrammar$1(topRulesTable, actualOptions.errMsgProvider);
}
function validateGrammar(options) {
	var _a;
	const errMsgProvider = (_a = options.errMsgProvider) !== null && _a !== void 0 ? _a : defaultGrammarValidatorErrorProvider;
	return validateGrammar$1(options.rules, options.tokenTypes, errMsgProvider, options.grammarName);
}
var MISMATCHED_TOKEN_EXCEPTION = "MismatchedTokenException";
var NO_VIABLE_ALT_EXCEPTION = "NoViableAltException";
var EARLY_EXIT_EXCEPTION = "EarlyExitException";
var NOT_ALL_INPUT_PARSED_EXCEPTION = "NotAllInputParsedException";
var RECOGNITION_EXCEPTION_NAMES = [
	MISMATCHED_TOKEN_EXCEPTION,
	NO_VIABLE_ALT_EXCEPTION,
	EARLY_EXIT_EXCEPTION,
	NOT_ALL_INPUT_PARSED_EXCEPTION
];
Object.freeze(RECOGNITION_EXCEPTION_NAMES);
function isRecognitionException(error) {
	return RECOGNITION_EXCEPTION_NAMES.includes(error.name);
}
var RecognitionException = class extends Error {
	constructor(message, token) {
		super(message);
		this.token = token;
		this.resyncedTokens = [];
		Object.setPrototypeOf(this, new.target.prototype);
		/* istanbul ignore next - V8 workaround to remove constructor from stacktrace when typescript target is ES5 */
		if (Error.captureStackTrace) Error.captureStackTrace(this, this.constructor);
	}
};
var MismatchedTokenException = class extends RecognitionException {
	constructor(message, token, previousToken) {
		super(message, token);
		this.previousToken = previousToken;
		this.name = MISMATCHED_TOKEN_EXCEPTION;
	}
};
var NoViableAltException = class extends RecognitionException {
	constructor(message, token, previousToken) {
		super(message, token);
		this.previousToken = previousToken;
		this.name = NO_VIABLE_ALT_EXCEPTION;
	}
};
var NotAllInputParsedException = class extends RecognitionException {
	constructor(message, token) {
		super(message, token);
		this.name = NOT_ALL_INPUT_PARSED_EXCEPTION;
	}
};
var EarlyExitException = class extends RecognitionException {
	constructor(message, token, previousToken) {
		super(message, token);
		this.previousToken = previousToken;
		this.name = EARLY_EXIT_EXCEPTION;
	}
};
var EOF_FOLLOW_KEY = {};
var IN_RULE_RECOVERY_EXCEPTION = "InRuleRecoveryException";
var InRuleRecoveryException = class extends Error {
	constructor(message) {
		super(message);
		this.name = IN_RULE_RECOVERY_EXCEPTION;
	}
};
/**
* This trait is responsible for the error recovery and fault tolerant logic
*/
var Recoverable = class {
	initRecoverable(config) {
		this.firstAfterRepMap = {};
		this.resyncFollows = {};
		this.recoveryEnabled = Object.hasOwn(config, "recoveryEnabled") ? config.recoveryEnabled : DEFAULT_PARSER_CONFIG.recoveryEnabled;
		if (this.recoveryEnabled) this.attemptInRepetitionRecovery = attemptInRepetitionRecovery;
	}
	getTokenToInsert(tokType) {
		const tokToInsert = createTokenInstance(tokType, "", NaN, NaN, NaN, NaN, NaN, NaN);
		tokToInsert.isInsertedInRecovery = true;
		return tokToInsert;
	}
	canTokenTypeBeInsertedInRecovery(tokType) {
		return true;
	}
	canTokenTypeBeDeletedInRecovery(tokType) {
		return true;
	}
	tryInRepetitionRecovery(grammarRule, grammarRuleArgs, lookAheadFunc, expectedTokType) {
		const reSyncTokType = this.findReSyncTokenType();
		const savedLexerState = this.exportLexerState();
		const resyncedTokens = [];
		let passedResyncPoint = false;
		const nextTokenWithoutResync = this.LA_FAST(1);
		let currToken = this.LA_FAST(1);
		const generateErrorMessage = () => {
			const previousToken = this.LA(0);
			const error = new MismatchedTokenException(this.errorMessageProvider.buildMismatchTokenMessage({
				expected: expectedTokType,
				actual: nextTokenWithoutResync,
				previous: previousToken,
				ruleName: this.getCurrRuleFullName()
			}), nextTokenWithoutResync, this.LA(0));
			error.resyncedTokens = resyncedTokens.slice(0, -1);
			this.SAVE_ERROR(error);
		};
		while (!passedResyncPoint) if (this.tokenMatcher(currToken, expectedTokType)) {
			generateErrorMessage();
			return;
		} else if (lookAheadFunc.call(this)) {
			generateErrorMessage();
			grammarRule.apply(this, grammarRuleArgs);
			return;
		} else if (this.tokenMatcher(currToken, reSyncTokType)) passedResyncPoint = true;
		else {
			currToken = this.SKIP_TOKEN();
			this.addToResyncTokens(currToken, resyncedTokens);
		}
		this.importLexerState(savedLexerState);
	}
	shouldInRepetitionRecoveryBeTried(expectTokAfterLastMatch, nextTokIdx, notStuck) {
		if (notStuck === false) return false;
		if (this.tokenMatcher(this.LA_FAST(1), expectTokAfterLastMatch)) return false;
		if (this.isBackTracking()) return false;
		if (this.canPerformInRuleRecovery(expectTokAfterLastMatch, this.getFollowsForInRuleRecovery(expectTokAfterLastMatch, nextTokIdx))) return false;
		return true;
	}
	getNextPossibleTokenTypes(grammarPath) {
		const topRuleName = grammarPath.ruleStack[0];
		const topProduction = this.getGAstProductions()[topRuleName];
		return new NextAfterTokenWalker(topProduction, grammarPath).startWalking();
	}
	getFollowsForInRuleRecovery(tokType, tokIdxInRule) {
		const grammarPath = this.getCurrentGrammarPath(tokType, tokIdxInRule);
		return this.getNextPossibleTokenTypes(grammarPath);
	}
	tryInRuleRecovery(expectedTokType, follows) {
		if (this.canRecoverWithSingleTokenInsertion(expectedTokType, follows)) return this.getTokenToInsert(expectedTokType);
		if (this.canRecoverWithSingleTokenDeletion(expectedTokType)) {
			const nextTok = this.SKIP_TOKEN();
			this.consumeToken();
			return nextTok;
		}
		throw new InRuleRecoveryException("sad sad panda");
	}
	canPerformInRuleRecovery(expectedToken, follows) {
		return this.canRecoverWithSingleTokenInsertion(expectedToken, follows) || this.canRecoverWithSingleTokenDeletion(expectedToken);
	}
	canRecoverWithSingleTokenInsertion(expectedTokType, follows) {
		if (!this.canTokenTypeBeInsertedInRecovery(expectedTokType)) return false;
		if (follows.length === 0) return false;
		const mismatchedTok = this.LA_FAST(1);
		return follows.find((possibleFollowsTokType) => {
			return this.tokenMatcher(mismatchedTok, possibleFollowsTokType);
		}) !== void 0;
	}
	canRecoverWithSingleTokenDeletion(expectedTokType) {
		if (!this.canTokenTypeBeDeletedInRecovery(expectedTokType)) return false;
		return this.tokenMatcher(this.LA(2), expectedTokType);
	}
	isInCurrentRuleReSyncSet(tokenTypeIdx) {
		const followKey = this.getCurrFollowKey();
		return this.getFollowSetFromFollowKey(followKey).includes(tokenTypeIdx);
	}
	findReSyncTokenType() {
		const allPossibleReSyncTokTypes = this.flattenFollowSet();
		let nextToken = this.LA_FAST(1);
		let k = 2;
		while (true) {
			const foundMatch = allPossibleReSyncTokTypes.find((resyncTokType) => {
				return tokenMatcher(nextToken, resyncTokType);
			});
			if (foundMatch !== void 0) return foundMatch;
			nextToken = this.LA(k);
			k++;
		}
	}
	getCurrFollowKey() {
		if (this.RULE_STACK_IDX === 0) return EOF_FOLLOW_KEY;
		const currRuleShortName = this.currRuleShortName;
		const currRuleIdx = this.getLastExplicitRuleOccurrenceIndex();
		const prevRuleShortName = this.getPreviousExplicitRuleShortName();
		return {
			ruleName: this.shortRuleNameToFullName(currRuleShortName),
			idxInCallingRule: currRuleIdx,
			inRule: this.shortRuleNameToFullName(prevRuleShortName)
		};
	}
	buildFullFollowKeyStack() {
		const explicitRuleStack = this.RULE_STACK;
		const explicitOccurrenceStack = this.RULE_OCCURRENCE_STACK;
		const len = this.RULE_STACK_IDX + 1;
		const result = new Array(len);
		for (let idx = 0; idx < len; idx++) if (idx === 0) result[idx] = EOF_FOLLOW_KEY;
		else result[idx] = {
			ruleName: this.shortRuleNameToFullName(explicitRuleStack[idx]),
			idxInCallingRule: explicitOccurrenceStack[idx],
			inRule: this.shortRuleNameToFullName(explicitRuleStack[idx - 1])
		};
		return result;
	}
	flattenFollowSet() {
		return this.buildFullFollowKeyStack().map((currKey) => {
			return this.getFollowSetFromFollowKey(currKey);
		}).flat();
	}
	getFollowSetFromFollowKey(followKey) {
		if (followKey === EOF_FOLLOW_KEY) return [EOF];
		const followName = followKey.ruleName + followKey.idxInCallingRule + IN + followKey.inRule;
		return this.resyncFollows[followName];
	}
	addToResyncTokens(token, resyncTokens) {
		if (!this.tokenMatcher(token, EOF)) resyncTokens.push(token);
		return resyncTokens;
	}
	reSyncTo(tokType) {
		const resyncedTokens = [];
		let nextTok = this.LA_FAST(1);
		while (this.tokenMatcher(nextTok, tokType) === false) {
			nextTok = this.SKIP_TOKEN();
			this.addToResyncTokens(nextTok, resyncedTokens);
		}
		return resyncedTokens.slice(0, -1);
	}
	attemptInRepetitionRecovery(prodFunc, args, lookaheadFunc, dslMethodIdx, prodOccurrence, nextToksWalker, notStuck) {}
	getCurrentGrammarPath(tokType, tokIdxInRule) {
		return {
			ruleStack: this.getHumanReadableRuleStack(),
			occurrenceStack: this.RULE_OCCURRENCE_STACK.slice(0, this.RULE_OCCURRENCE_STACK_IDX + 1),
			lastTok: tokType,
			lastTokOccurrence: tokIdxInRule
		};
	}
	getHumanReadableRuleStack() {
		const len = this.RULE_STACK_IDX + 1;
		const result = new Array(len);
		for (let i = 0; i < len; i++) result[i] = this.shortRuleNameToFullName(this.RULE_STACK[i]);
		return result;
	}
};
function attemptInRepetitionRecovery(prodFunc, args, lookaheadFunc, dslMethodIdx, prodOccurrence, nextToksWalker, notStuck) {
	const key = this.getKeyForAutomaticLookahead(dslMethodIdx, prodOccurrence);
	let firstAfterRepInfo = this.firstAfterRepMap[key];
	if (firstAfterRepInfo === void 0) {
		const currRuleName = this.getCurrRuleFullName();
		const ruleGrammar = this.getGAstProductions()[currRuleName];
		firstAfterRepInfo = new nextToksWalker(ruleGrammar, prodOccurrence).startWalking();
		this.firstAfterRepMap[key] = firstAfterRepInfo;
	}
	let expectTokAfterLastMatch = firstAfterRepInfo.token;
	let nextTokIdx = firstAfterRepInfo.occurrence;
	const isEndOfRule = firstAfterRepInfo.isEndOfRule;
	if (this.RULE_STACK_IDX === 0 && isEndOfRule && expectTokAfterLastMatch === void 0) {
		expectTokAfterLastMatch = EOF;
		nextTokIdx = 1;
	}
	if (expectTokAfterLastMatch === void 0 || nextTokIdx === void 0) return;
	if (this.shouldInRepetitionRecoveryBeTried(expectTokAfterLastMatch, nextTokIdx, notStuck)) this.tryInRepetitionRecovery(prodFunc, args, lookaheadFunc, expectTokAfterLastMatch);
}
var AT_LEAST_ONE_IDX = 1024;
var MANY_SEP_IDX = 1280;
var AT_LEAST_ONE_SEP_IDX = 1536;
function getKeyForAutomaticLookahead(ruleIdx, dslMethodIdx, occurrence) {
	return occurrence | dslMethodIdx | ruleIdx;
}
var LLkLookaheadStrategy = class {
	constructor(options) {
		var _a;
		this.maxLookahead = (_a = options === null || options === void 0 ? void 0 : options.maxLookahead) !== null && _a !== void 0 ? _a : DEFAULT_PARSER_CONFIG.maxLookahead;
	}
	validate(options) {
		const leftRecursionErrors = this.validateNoLeftRecursion(options.rules);
		if (leftRecursionErrors.length === 0) {
			const emptyAltErrors = this.validateEmptyOrAlternatives(options.rules);
			const ambiguousAltsErrors = this.validateAmbiguousAlternationAlternatives(options.rules, this.maxLookahead);
			const emptyRepetitionErrors = this.validateSomeNonEmptyLookaheadPath(options.rules, this.maxLookahead);
			return [
				...leftRecursionErrors,
				...emptyAltErrors,
				...ambiguousAltsErrors,
				...emptyRepetitionErrors
			];
		}
		return leftRecursionErrors;
	}
	validateNoLeftRecursion(rules) {
		return rules.flatMap((currTopRule) => validateNoLeftRecursion(currTopRule, currTopRule, defaultGrammarValidatorErrorProvider));
	}
	validateEmptyOrAlternatives(rules) {
		return rules.flatMap((currTopRule) => validateEmptyOrAlternative(currTopRule, defaultGrammarValidatorErrorProvider));
	}
	validateAmbiguousAlternationAlternatives(rules, maxLookahead) {
		return rules.flatMap((currTopRule) => validateAmbiguousAlternationAlternatives(currTopRule, maxLookahead, defaultGrammarValidatorErrorProvider));
	}
	validateSomeNonEmptyLookaheadPath(rules, maxLookahead) {
		return validateSomeNonEmptyLookaheadPath(rules, maxLookahead, defaultGrammarValidatorErrorProvider);
	}
	buildLookaheadForAlternation(options) {
		return buildLookaheadFuncForOr(options.prodOccurrence, options.rule, options.maxLookahead, options.hasPredicates, options.dynamicTokensEnabled, buildAlternativesLookAheadFunc);
	}
	buildLookaheadForOptional(options) {
		return buildLookaheadFuncForOptionalProd(options.prodOccurrence, options.rule, options.maxLookahead, options.dynamicTokensEnabled, getProdType(options.prodType), buildSingleAlternativeLookaheadFunction);
	}
};
/**
* Trait responsible for the lookahead related utilities and optimizations.
*/
var LooksAhead = class {
	initLooksAhead(config) {
		this.dynamicTokensEnabled = Object.hasOwn(config, "dynamicTokensEnabled") ? config.dynamicTokensEnabled : DEFAULT_PARSER_CONFIG.dynamicTokensEnabled;
		this.maxLookahead = Object.hasOwn(config, "maxLookahead") ? config.maxLookahead : DEFAULT_PARSER_CONFIG.maxLookahead;
		this.lookaheadStrategy = Object.hasOwn(config, "lookaheadStrategy") ? config.lookaheadStrategy : new LLkLookaheadStrategy({ maxLookahead: this.maxLookahead });
		this.lookAheadFuncsCache = /* @__PURE__ */ new Map();
	}
	preComputeLookaheadFunctions(rules) {
		rules.forEach((currRule) => {
			this.TRACE_INIT(`${currRule.name} Rule Lookahead`, () => {
				const { alternation, repetition, option, repetitionMandatory, repetitionMandatoryWithSeparator, repetitionWithSeparator } = collectMethods(currRule);
				alternation.forEach((currProd) => {
					const prodIdx = currProd.idx === 0 ? "" : currProd.idx;
					this.TRACE_INIT(`${getProductionDslName(currProd)}${prodIdx}`, () => {
						const laFunc = this.lookaheadStrategy.buildLookaheadForAlternation({
							prodOccurrence: currProd.idx,
							rule: currRule,
							maxLookahead: currProd.maxLookahead || this.maxLookahead,
							hasPredicates: currProd.hasPredicates,
							dynamicTokensEnabled: this.dynamicTokensEnabled
						});
						const key = getKeyForAutomaticLookahead(this.fullRuleNameToShort[currRule.name], 256, currProd.idx);
						this.setLaFuncCache(key, laFunc);
					});
				});
				repetition.forEach((currProd) => {
					this.computeLookaheadFunc(currRule, currProd.idx, 768, "Repetition", currProd.maxLookahead, getProductionDslName(currProd));
				});
				option.forEach((currProd) => {
					this.computeLookaheadFunc(currRule, currProd.idx, 512, "Option", currProd.maxLookahead, getProductionDslName(currProd));
				});
				repetitionMandatory.forEach((currProd) => {
					this.computeLookaheadFunc(currRule, currProd.idx, AT_LEAST_ONE_IDX, "RepetitionMandatory", currProd.maxLookahead, getProductionDslName(currProd));
				});
				repetitionMandatoryWithSeparator.forEach((currProd) => {
					this.computeLookaheadFunc(currRule, currProd.idx, AT_LEAST_ONE_SEP_IDX, "RepetitionMandatoryWithSeparator", currProd.maxLookahead, getProductionDslName(currProd));
				});
				repetitionWithSeparator.forEach((currProd) => {
					this.computeLookaheadFunc(currRule, currProd.idx, MANY_SEP_IDX, "RepetitionWithSeparator", currProd.maxLookahead, getProductionDslName(currProd));
				});
			});
		});
	}
	computeLookaheadFunc(rule, prodOccurrence, prodKey, prodType, prodMaxLookahead, dslMethodName) {
		this.TRACE_INIT(`${dslMethodName}${prodOccurrence === 0 ? "" : prodOccurrence}`, () => {
			const laFunc = this.lookaheadStrategy.buildLookaheadForOptional({
				prodOccurrence,
				rule,
				maxLookahead: prodMaxLookahead || this.maxLookahead,
				dynamicTokensEnabled: this.dynamicTokensEnabled,
				prodType
			});
			const key = getKeyForAutomaticLookahead(this.fullRuleNameToShort[rule.name], prodKey, prodOccurrence);
			this.setLaFuncCache(key, laFunc);
		});
	}
	getKeyForAutomaticLookahead(dslMethodIdx, occurrence) {
		return getKeyForAutomaticLookahead(this.currRuleShortName, dslMethodIdx, occurrence);
	}
	getLaFuncFromCache(key) {
		return this.lookAheadFuncsCache.get(key);
	}
	/* istanbul ignore next */
	setLaFuncCache(key, value) {
		this.lookAheadFuncsCache.set(key, value);
	}
};
var DslMethodsCollectorVisitor = class extends GAstVisitor {
	constructor() {
		super(...arguments);
		this.dslMethods = {
			option: [],
			alternation: [],
			repetition: [],
			repetitionWithSeparator: [],
			repetitionMandatory: [],
			repetitionMandatoryWithSeparator: []
		};
	}
	reset() {
		this.dslMethods = {
			option: [],
			alternation: [],
			repetition: [],
			repetitionWithSeparator: [],
			repetitionMandatory: [],
			repetitionMandatoryWithSeparator: []
		};
	}
	visitOption(option) {
		this.dslMethods.option.push(option);
	}
	visitRepetitionWithSeparator(manySep) {
		this.dslMethods.repetitionWithSeparator.push(manySep);
	}
	visitRepetitionMandatory(atLeastOne) {
		this.dslMethods.repetitionMandatory.push(atLeastOne);
	}
	visitRepetitionMandatoryWithSeparator(atLeastOneSep) {
		this.dslMethods.repetitionMandatoryWithSeparator.push(atLeastOneSep);
	}
	visitRepetition(many) {
		this.dslMethods.repetition.push(many);
	}
	visitAlternation(or) {
		this.dslMethods.alternation.push(or);
	}
};
var collectorVisitor = new DslMethodsCollectorVisitor();
function collectMethods(rule) {
	collectorVisitor.reset();
	rule.accept(collectorVisitor);
	const dslMethods = collectorVisitor.dslMethods;
	collectorVisitor.reset();
	return dslMethods;
}
/**
* This nodeLocation tracking is not efficient and should only be used
* when error recovery is enabled or the Token Vector contains virtual Tokens
* (e.g, Python Indent/Outdent)
* As it executes the calculation for every single terminal/nonTerminal
* and does not rely on the fact the token vector is **sorted**
*/
function setNodeLocationOnlyOffset(currNodeLocation, newLocationInfo) {
	if (isNaN(currNodeLocation.startOffset) === true) {
		currNodeLocation.startOffset = newLocationInfo.startOffset;
		currNodeLocation.endOffset = newLocationInfo.endOffset;
	} else if (currNodeLocation.endOffset < newLocationInfo.endOffset === true) currNodeLocation.endOffset = newLocationInfo.endOffset;
}
/**
* This nodeLocation tracking is not efficient and should only be used
* when error recovery is enabled or the Token Vector contains virtual Tokens
* (e.g, Python Indent/Outdent)
* As it executes the calculation for every single terminal/nonTerminal
* and does not rely on the fact the token vector is **sorted**
*/
function setNodeLocationFull(currNodeLocation, newLocationInfo) {
	if (isNaN(currNodeLocation.startOffset) === true) {
		currNodeLocation.startOffset = newLocationInfo.startOffset;
		currNodeLocation.startColumn = newLocationInfo.startColumn;
		currNodeLocation.startLine = newLocationInfo.startLine;
		currNodeLocation.endOffset = newLocationInfo.endOffset;
		currNodeLocation.endColumn = newLocationInfo.endColumn;
		currNodeLocation.endLine = newLocationInfo.endLine;
	} else if (currNodeLocation.endOffset < newLocationInfo.endOffset === true) {
		currNodeLocation.endOffset = newLocationInfo.endOffset;
		currNodeLocation.endColumn = newLocationInfo.endColumn;
		currNodeLocation.endLine = newLocationInfo.endLine;
	}
}
function addTerminalToCst(node, token, tokenTypeName) {
	if (node.children[tokenTypeName] === void 0) node.children[tokenTypeName] = [token];
	else node.children[tokenTypeName].push(token);
}
function addNoneTerminalToCst(node, ruleName, ruleResult) {
	if (node.children[ruleName] === void 0) node.children[ruleName] = [ruleResult];
	else node.children[ruleName].push(ruleResult);
}
var NAME = "name";
function defineNameProp(obj, nameValue) {
	Object.defineProperty(obj, NAME, {
		enumerable: false,
		configurable: true,
		writable: false,
		value: nameValue
	});
}
function defaultVisit(ctx, param) {
	const childrenNames = Object.keys(ctx);
	const childrenNamesLength = childrenNames.length;
	for (let i = 0; i < childrenNamesLength; i++) {
		const currChildArray = ctx[childrenNames[i]];
		const currChildArrayLength = currChildArray.length;
		for (let j = 0; j < currChildArrayLength; j++) {
			const currChild = currChildArray[j];
			if (currChild.tokenTypeIdx === void 0) this[currChild.name](currChild.children, param);
		}
	}
}
function createBaseSemanticVisitorConstructor(grammarName, ruleNames) {
	const derivedConstructor = function() {};
	defineNameProp(derivedConstructor, grammarName + "BaseSemantics");
	derivedConstructor.prototype = {
		visit: function(cstNode, param) {
			if (Array.isArray(cstNode)) cstNode = cstNode[0];
			if (cstNode === void 0) return;
			return this[cstNode.name](cstNode.children, param);
		},
		validateVisitor: function() {
			const semanticDefinitionErrors = validateVisitor(this, ruleNames);
			if (semanticDefinitionErrors.length !== 0) {
				const errorMessages = semanticDefinitionErrors.map((currDefError) => currDefError.msg);
				throw Error(`Errors Detected in CST Visitor <${this.constructor.name}>:\n\t${errorMessages.join("\n\n").replace(/\n/g, "\n	")}`);
			}
		}
	};
	derivedConstructor.prototype.constructor = derivedConstructor;
	derivedConstructor._RULE_NAMES = ruleNames;
	return derivedConstructor;
}
function createBaseVisitorConstructorWithDefaults(grammarName, ruleNames, baseConstructor) {
	const derivedConstructor = function() {};
	defineNameProp(derivedConstructor, grammarName + "BaseSemanticsWithDefaults");
	const withDefaultsProto = Object.create(baseConstructor.prototype);
	ruleNames.forEach((ruleName) => {
		withDefaultsProto[ruleName] = defaultVisit;
	});
	derivedConstructor.prototype = withDefaultsProto;
	derivedConstructor.prototype.constructor = derivedConstructor;
	return derivedConstructor;
}
var CstVisitorDefinitionError;
(function(CstVisitorDefinitionError) {
	CstVisitorDefinitionError[CstVisitorDefinitionError["REDUNDANT_METHOD"] = 0] = "REDUNDANT_METHOD";
	CstVisitorDefinitionError[CstVisitorDefinitionError["MISSING_METHOD"] = 1] = "MISSING_METHOD";
})(CstVisitorDefinitionError || (CstVisitorDefinitionError = {}));
function validateVisitor(visitorInstance, ruleNames) {
	return validateMissingCstMethods(visitorInstance, ruleNames);
}
function validateMissingCstMethods(visitorInstance, ruleNames) {
	return ruleNames.filter((currRuleName) => {
		return typeof visitorInstance[currRuleName] === "function" === false;
	}).map((currRuleName) => {
		return {
			msg: `Missing visitor method: <${currRuleName}> on ${visitorInstance.constructor.name} CST Visitor.`,
			type: CstVisitorDefinitionError.MISSING_METHOD,
			methodName: currRuleName
		};
	}).filter(Boolean);
}
/**
* This trait is responsible for the CST building logic.
*/
var TreeBuilder = class {
	initTreeBuilder(config) {
		this.CST_STACK = [];
		this.outputCst = config.outputCst;
		this.nodeLocationTracking = Object.hasOwn(config, "nodeLocationTracking") ? config.nodeLocationTracking : DEFAULT_PARSER_CONFIG.nodeLocationTracking;
		if (!this.outputCst) {
			this.cstInvocationStateUpdate = () => {};
			this.cstFinallyStateUpdate = () => {};
			this.cstPostTerminal = () => {};
			this.cstPostNonTerminal = () => {};
			this.cstPostRule = () => {};
		} else if (/full/i.test(this.nodeLocationTracking)) if (this.recoveryEnabled) {
			this.setNodeLocationFromToken = setNodeLocationFull;
			this.setNodeLocationFromNode = setNodeLocationFull;
			this.cstPostRule = () => {};
			this.setInitialNodeLocation = this.setInitialNodeLocationFullRecovery;
		} else {
			this.setNodeLocationFromToken = () => {};
			this.setNodeLocationFromNode = () => {};
			this.cstPostRule = this.cstPostRuleFull;
			this.setInitialNodeLocation = this.setInitialNodeLocationFullRegular;
		}
		else if (/onlyOffset/i.test(this.nodeLocationTracking)) if (this.recoveryEnabled) {
			this.setNodeLocationFromToken = setNodeLocationOnlyOffset;
			this.setNodeLocationFromNode = setNodeLocationOnlyOffset;
			this.cstPostRule = () => {};
			this.setInitialNodeLocation = this.setInitialNodeLocationOnlyOffsetRecovery;
		} else {
			this.setNodeLocationFromToken = () => {};
			this.setNodeLocationFromNode = () => {};
			this.cstPostRule = this.cstPostRuleOnlyOffset;
			this.setInitialNodeLocation = this.setInitialNodeLocationOnlyOffsetRegular;
		}
		else if (/none/i.test(this.nodeLocationTracking)) {
			this.setNodeLocationFromToken = () => {};
			this.setNodeLocationFromNode = () => {};
			this.cstPostRule = () => {};
			this.setInitialNodeLocation = () => {};
		} else throw Error(`Invalid <nodeLocationTracking> config option: "${config.nodeLocationTracking}"`);
	}
	setInitialNodeLocationOnlyOffsetRecovery(cstNode) {
		cstNode.location = {
			startOffset: NaN,
			endOffset: NaN
		};
	}
	setInitialNodeLocationOnlyOffsetRegular(cstNode) {
		cstNode.location = {
			startOffset: this.LA_FAST(1).startOffset,
			endOffset: NaN
		};
	}
	setInitialNodeLocationFullRecovery(cstNode) {
		cstNode.location = {
			startOffset: NaN,
			startLine: NaN,
			startColumn: NaN,
			endOffset: NaN,
			endLine: NaN,
			endColumn: NaN
		};
	}
	/**
	*  @see setInitialNodeLocationOnlyOffsetRegular for explanation why this work
	
	* @param cstNode
	*/
	setInitialNodeLocationFullRegular(cstNode) {
		const nextToken = this.LA_FAST(1);
		cstNode.location = {
			startOffset: nextToken.startOffset,
			startLine: nextToken.startLine,
			startColumn: nextToken.startColumn,
			endOffset: NaN,
			endLine: NaN,
			endColumn: NaN
		};
	}
	cstInvocationStateUpdate(fullRuleName) {
		const cstNode = {
			name: fullRuleName,
			children: Object.create(null)
		};
		this.setInitialNodeLocation(cstNode);
		this.CST_STACK.push(cstNode);
	}
	cstFinallyStateUpdate() {
		this.CST_STACK.pop();
	}
	cstPostRuleFull(ruleCstNode) {
		const prevToken = this.LA(0);
		const loc = ruleCstNode.location;
		if (loc.startOffset <= prevToken.startOffset === true) {
			loc.endOffset = prevToken.endOffset;
			loc.endLine = prevToken.endLine;
			loc.endColumn = prevToken.endColumn;
		} else {
			loc.startOffset = NaN;
			loc.startLine = NaN;
			loc.startColumn = NaN;
		}
	}
	cstPostRuleOnlyOffset(ruleCstNode) {
		const prevToken = this.LA(0);
		const loc = ruleCstNode.location;
		if (loc.startOffset <= prevToken.startOffset === true) loc.endOffset = prevToken.endOffset;
		else loc.startOffset = NaN;
	}
	cstPostTerminal(key, consumedToken) {
		const rootCst = this.CST_STACK[this.CST_STACK.length - 1];
		addTerminalToCst(rootCst, consumedToken, key);
		this.setNodeLocationFromToken(rootCst.location, consumedToken);
	}
	cstPostNonTerminal(ruleCstResult, ruleName) {
		const preCstNode = this.CST_STACK[this.CST_STACK.length - 1];
		addNoneTerminalToCst(preCstNode, ruleName, ruleCstResult);
		this.setNodeLocationFromNode(preCstNode.location, ruleCstResult.location);
	}
	getBaseCstVisitorConstructor() {
		if (this.baseCstVisitorConstructor === void 0) {
			const newBaseCstVisitorConstructor = createBaseSemanticVisitorConstructor(this.className, Object.keys(this.gastProductionsCache));
			this.baseCstVisitorConstructor = newBaseCstVisitorConstructor;
			return newBaseCstVisitorConstructor;
		}
		return this.baseCstVisitorConstructor;
	}
	getBaseCstVisitorConstructorWithDefaults() {
		if (this.baseCstVisitorWithDefaultsConstructor === void 0) {
			const newConstructor = createBaseVisitorConstructorWithDefaults(this.className, Object.keys(this.gastProductionsCache), this.getBaseCstVisitorConstructor());
			this.baseCstVisitorWithDefaultsConstructor = newConstructor;
			return newConstructor;
		}
		return this.baseCstVisitorWithDefaultsConstructor;
	}
	getPreviousExplicitRuleShortName() {
		return this.RULE_STACK[this.RULE_STACK_IDX - 1];
	}
	getLastExplicitRuleOccurrenceIndex() {
		return this.RULE_OCCURRENCE_STACK[this.RULE_OCCURRENCE_STACK_IDX];
	}
};
/**
* Trait responsible abstracting over the interaction with Lexer output (Token vector).
*
* This could be generalized to support other kinds of lexers, e.g.
* - Just in Time Lexing / Lexer-Less parsing.
* - Streaming Lexer.
*/
var LexerAdapter = class {
	initLexerAdapter() {
		this.tokVector = [];
		this.tokVectorLength = 0;
		this.currIdx = -1;
	}
	set input(newInput) {
		if (this.selfAnalysisDone !== true) throw Error(`Missing <performSelfAnalysis> invocation at the end of the Parser's constructor.`);
		this.reset();
		this.tokVector = newInput;
		this.tokVectorLength = newInput.length;
	}
	get input() {
		return this.tokVector;
	}
	SKIP_TOKEN() {
		if (this.currIdx <= this.tokVectorLength - 2) {
			this.consumeToken();
			return this.LA_FAST(1);
		} else return END_OF_FILE;
	}
	LA_FAST(howMuch) {
		const soughtIdx = this.currIdx + howMuch;
		return this.tokVector[soughtIdx];
	}
	LA(howMuch) {
		const soughtIdx = this.currIdx + howMuch;
		if (soughtIdx < 0 || this.tokVectorLength <= soughtIdx) return END_OF_FILE;
		else return this.tokVector[soughtIdx];
	}
	consumeToken() {
		this.currIdx++;
	}
	exportLexerState() {
		return this.currIdx;
	}
	importLexerState(newState) {
		this.currIdx = newState;
	}
	resetLexerState() {
		this.currIdx = -1;
	}
	moveToTerminatedState() {
		this.currIdx = this.tokVectorLength - 1;
	}
	getLexerPosition() {
		return this.exportLexerState();
	}
};
/**
* This trait is responsible for implementing the public API
* for defining Chevrotain parsers, i.e:
* - CONSUME
* - RULE
* - OPTION
* - ...
*/
var RecognizerApi = class {
	ACTION(impl) {
		return impl.call(this);
	}
	consume(idx, tokType, options) {
		return this.consumeInternal(tokType, idx, options);
	}
	subrule(idx, ruleToCall, options) {
		return this.subruleInternal(ruleToCall, idx, options);
	}
	option(idx, actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, idx);
	}
	or(idx, altsOrOpts) {
		return this.orInternal(altsOrOpts, idx);
	}
	many(idx, actionORMethodDef) {
		return this.manyInternal(idx, actionORMethodDef);
	}
	atLeastOne(idx, actionORMethodDef) {
		return this.atLeastOneInternal(idx, actionORMethodDef);
	}
	CONSUME(tokType, options) {
		return this.consumeInternal(tokType, 0, options);
	}
	CONSUME1(tokType, options) {
		return this.consumeInternal(tokType, 1, options);
	}
	CONSUME2(tokType, options) {
		return this.consumeInternal(tokType, 2, options);
	}
	CONSUME3(tokType, options) {
		return this.consumeInternal(tokType, 3, options);
	}
	CONSUME4(tokType, options) {
		return this.consumeInternal(tokType, 4, options);
	}
	CONSUME5(tokType, options) {
		return this.consumeInternal(tokType, 5, options);
	}
	CONSUME6(tokType, options) {
		return this.consumeInternal(tokType, 6, options);
	}
	CONSUME7(tokType, options) {
		return this.consumeInternal(tokType, 7, options);
	}
	CONSUME8(tokType, options) {
		return this.consumeInternal(tokType, 8, options);
	}
	CONSUME9(tokType, options) {
		return this.consumeInternal(tokType, 9, options);
	}
	SUBRULE(ruleToCall, options) {
		return this.subruleInternal(ruleToCall, 0, options);
	}
	SUBRULE1(ruleToCall, options) {
		return this.subruleInternal(ruleToCall, 1, options);
	}
	SUBRULE2(ruleToCall, options) {
		return this.subruleInternal(ruleToCall, 2, options);
	}
	SUBRULE3(ruleToCall, options) {
		return this.subruleInternal(ruleToCall, 3, options);
	}
	SUBRULE4(ruleToCall, options) {
		return this.subruleInternal(ruleToCall, 4, options);
	}
	SUBRULE5(ruleToCall, options) {
		return this.subruleInternal(ruleToCall, 5, options);
	}
	SUBRULE6(ruleToCall, options) {
		return this.subruleInternal(ruleToCall, 6, options);
	}
	SUBRULE7(ruleToCall, options) {
		return this.subruleInternal(ruleToCall, 7, options);
	}
	SUBRULE8(ruleToCall, options) {
		return this.subruleInternal(ruleToCall, 8, options);
	}
	SUBRULE9(ruleToCall, options) {
		return this.subruleInternal(ruleToCall, 9, options);
	}
	OPTION(actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, 0);
	}
	OPTION1(actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, 1);
	}
	OPTION2(actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, 2);
	}
	OPTION3(actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, 3);
	}
	OPTION4(actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, 4);
	}
	OPTION5(actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, 5);
	}
	OPTION6(actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, 6);
	}
	OPTION7(actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, 7);
	}
	OPTION8(actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, 8);
	}
	OPTION9(actionORMethodDef) {
		return this.optionInternal(actionORMethodDef, 9);
	}
	OR(altsOrOpts) {
		return this.orInternal(altsOrOpts, 0);
	}
	OR1(altsOrOpts) {
		return this.orInternal(altsOrOpts, 1);
	}
	OR2(altsOrOpts) {
		return this.orInternal(altsOrOpts, 2);
	}
	OR3(altsOrOpts) {
		return this.orInternal(altsOrOpts, 3);
	}
	OR4(altsOrOpts) {
		return this.orInternal(altsOrOpts, 4);
	}
	OR5(altsOrOpts) {
		return this.orInternal(altsOrOpts, 5);
	}
	OR6(altsOrOpts) {
		return this.orInternal(altsOrOpts, 6);
	}
	OR7(altsOrOpts) {
		return this.orInternal(altsOrOpts, 7);
	}
	OR8(altsOrOpts) {
		return this.orInternal(altsOrOpts, 8);
	}
	OR9(altsOrOpts) {
		return this.orInternal(altsOrOpts, 9);
	}
	MANY(actionORMethodDef) {
		this.manyInternal(0, actionORMethodDef);
	}
	MANY1(actionORMethodDef) {
		this.manyInternal(1, actionORMethodDef);
	}
	MANY2(actionORMethodDef) {
		this.manyInternal(2, actionORMethodDef);
	}
	MANY3(actionORMethodDef) {
		this.manyInternal(3, actionORMethodDef);
	}
	MANY4(actionORMethodDef) {
		this.manyInternal(4, actionORMethodDef);
	}
	MANY5(actionORMethodDef) {
		this.manyInternal(5, actionORMethodDef);
	}
	MANY6(actionORMethodDef) {
		this.manyInternal(6, actionORMethodDef);
	}
	MANY7(actionORMethodDef) {
		this.manyInternal(7, actionORMethodDef);
	}
	MANY8(actionORMethodDef) {
		this.manyInternal(8, actionORMethodDef);
	}
	MANY9(actionORMethodDef) {
		this.manyInternal(9, actionORMethodDef);
	}
	MANY_SEP(options) {
		this.manySepFirstInternal(0, options);
	}
	MANY_SEP1(options) {
		this.manySepFirstInternal(1, options);
	}
	MANY_SEP2(options) {
		this.manySepFirstInternal(2, options);
	}
	MANY_SEP3(options) {
		this.manySepFirstInternal(3, options);
	}
	MANY_SEP4(options) {
		this.manySepFirstInternal(4, options);
	}
	MANY_SEP5(options) {
		this.manySepFirstInternal(5, options);
	}
	MANY_SEP6(options) {
		this.manySepFirstInternal(6, options);
	}
	MANY_SEP7(options) {
		this.manySepFirstInternal(7, options);
	}
	MANY_SEP8(options) {
		this.manySepFirstInternal(8, options);
	}
	MANY_SEP9(options) {
		this.manySepFirstInternal(9, options);
	}
	AT_LEAST_ONE(actionORMethodDef) {
		this.atLeastOneInternal(0, actionORMethodDef);
	}
	AT_LEAST_ONE1(actionORMethodDef) {
		return this.atLeastOneInternal(1, actionORMethodDef);
	}
	AT_LEAST_ONE2(actionORMethodDef) {
		this.atLeastOneInternal(2, actionORMethodDef);
	}
	AT_LEAST_ONE3(actionORMethodDef) {
		this.atLeastOneInternal(3, actionORMethodDef);
	}
	AT_LEAST_ONE4(actionORMethodDef) {
		this.atLeastOneInternal(4, actionORMethodDef);
	}
	AT_LEAST_ONE5(actionORMethodDef) {
		this.atLeastOneInternal(5, actionORMethodDef);
	}
	AT_LEAST_ONE6(actionORMethodDef) {
		this.atLeastOneInternal(6, actionORMethodDef);
	}
	AT_LEAST_ONE7(actionORMethodDef) {
		this.atLeastOneInternal(7, actionORMethodDef);
	}
	AT_LEAST_ONE8(actionORMethodDef) {
		this.atLeastOneInternal(8, actionORMethodDef);
	}
	AT_LEAST_ONE9(actionORMethodDef) {
		this.atLeastOneInternal(9, actionORMethodDef);
	}
	AT_LEAST_ONE_SEP(options) {
		this.atLeastOneSepFirstInternal(0, options);
	}
	AT_LEAST_ONE_SEP1(options) {
		this.atLeastOneSepFirstInternal(1, options);
	}
	AT_LEAST_ONE_SEP2(options) {
		this.atLeastOneSepFirstInternal(2, options);
	}
	AT_LEAST_ONE_SEP3(options) {
		this.atLeastOneSepFirstInternal(3, options);
	}
	AT_LEAST_ONE_SEP4(options) {
		this.atLeastOneSepFirstInternal(4, options);
	}
	AT_LEAST_ONE_SEP5(options) {
		this.atLeastOneSepFirstInternal(5, options);
	}
	AT_LEAST_ONE_SEP6(options) {
		this.atLeastOneSepFirstInternal(6, options);
	}
	AT_LEAST_ONE_SEP7(options) {
		this.atLeastOneSepFirstInternal(7, options);
	}
	AT_LEAST_ONE_SEP8(options) {
		this.atLeastOneSepFirstInternal(8, options);
	}
	AT_LEAST_ONE_SEP9(options) {
		this.atLeastOneSepFirstInternal(9, options);
	}
	RULE(name, implementation, config = DEFAULT_RULE_CONFIG) {
		if (this.definedRulesNames.includes(name)) {
			const error = {
				message: defaultGrammarValidatorErrorProvider.buildDuplicateRuleNameError({
					topLevelRule: name,
					grammarName: this.className
				}),
				type: ParserDefinitionErrorType.DUPLICATE_RULE_NAME,
				ruleName: name
			};
			this.definitionErrors.push(error);
		}
		this.definedRulesNames.push(name);
		const ruleImplementation = this.defineRule(name, implementation, config);
		this[name] = ruleImplementation;
		return ruleImplementation;
	}
	OVERRIDE_RULE(name, impl, config = DEFAULT_RULE_CONFIG) {
		const ruleErrors = validateRuleIsOverridden(name, this.definedRulesNames, this.className);
		this.definitionErrors = this.definitionErrors.concat(ruleErrors);
		const ruleImplementation = this.defineRule(name, impl, config);
		this[name] = ruleImplementation;
		return ruleImplementation;
	}
	BACKTRACK(grammarRule, args) {
		var _a;
		const ruleToCall = (_a = grammarRule.coreRule) !== null && _a !== void 0 ? _a : grammarRule;
		return function() {
			this.isBackTrackingStack.push(1);
			const orgState = this.saveRecogState();
			try {
				ruleToCall.apply(this, args);
				return true;
			} catch (e) {
				if (isRecognitionException(e)) return false;
				else throw e;
			} finally {
				this.reloadRecogState(orgState);
				this.isBackTrackingStack.pop();
			}
		};
	}
	getGAstProductions() {
		return this.gastProductionsCache;
	}
	getSerializedGastProductions() {
		return serializeGrammar(Object.values(this.gastProductionsCache));
	}
};
/**
* This trait is responsible for the runtime parsing engine
* Used by the official API (recognizer_api.ts)
*/
var RecognizerEngine = class {
	initRecognizerEngine(tokenVocabulary, config) {
		this.className = this.constructor.name;
		this.shortRuleNameToFull = {};
		this.fullRuleNameToShort = {};
		this.ruleShortNameIdx = 256;
		this.tokenMatcher = tokenStructuredMatcherNoCategories;
		this.subruleIdx = 0;
		this.currRuleShortName = 0;
		this.definedRulesNames = [];
		this.tokensMap = {};
		this.isBackTrackingStack = [];
		this.RULE_STACK = [];
		this.RULE_STACK_IDX = -1;
		this.RULE_OCCURRENCE_STACK = [];
		this.RULE_OCCURRENCE_STACK_IDX = -1;
		this.gastProductionsCache = {};
		if (Object.hasOwn(config, "serializedGrammar")) throw Error("The Parser's configuration can no longer contain a <serializedGrammar> property.\n	See: https://chevrotain.io/docs/changes/BREAKING_CHANGES.html#_6-0-0\n	For Further details.");
		if (Array.isArray(tokenVocabulary)) {
			if (tokenVocabulary.length === 0) throw Error("A Token Vocabulary cannot be empty.\n	Note that the first argument for the parser constructor\n	is no longer a Token vector (since v4.0).");
			if (typeof tokenVocabulary[0].startOffset === "number") throw Error("The Parser constructor no longer accepts a token vector as the first argument.\n	See: https://chevrotain.io/docs/changes/BREAKING_CHANGES.html#_4-0-0\n	For Further details.");
		}
		if (Array.isArray(tokenVocabulary)) this.tokensMap = tokenVocabulary.reduce((acc, tokType) => {
			acc[tokType.name] = tokType;
			return acc;
		}, {});
		else if (Object.hasOwn(tokenVocabulary, "modes") && Object.values(tokenVocabulary.modes).flat().every(isTokenType)) {
			const allTokenTypes = Object.values(tokenVocabulary.modes).flat();
			const uniqueTokens = [...new Set(allTokenTypes)];
			this.tokensMap = uniqueTokens.reduce((acc, tokType) => {
				acc[tokType.name] = tokType;
				return acc;
			}, {});
		} else if (typeof tokenVocabulary === "object" && tokenVocabulary !== null) this.tokensMap = Object.assign({}, tokenVocabulary);
		else throw new Error("<tokensDictionary> argument must be An Array of Token constructors, A dictionary of Token constructors or an IMultiModeLexerDefinition");
		this.tokensMap["EOF"] = EOF;
		const noTokenCategoriesUsed = (Object.hasOwn(tokenVocabulary, "modes") ? Object.values(tokenVocabulary.modes).flat() : Object.values(tokenVocabulary)).every((tokenConstructor) => {
			var _a;
			return ((_a = tokenConstructor.categoryMatches) === null || _a === void 0 ? void 0 : _a.length) == 0;
		});
		this.tokenMatcher = noTokenCategoriesUsed ? tokenStructuredMatcherNoCategories : tokenStructuredMatcher;
		augmentTokenTypes(Object.values(this.tokensMap));
	}
	defineRule(ruleName, impl, config) {
		if (this.selfAnalysisDone) throw Error(`Grammar rule <${ruleName}> may not be defined after the 'performSelfAnalysis' method has been called'\nMake sure that all grammar rule definitions are done before 'performSelfAnalysis' is called.`);
		const resyncEnabled = Object.hasOwn(config, "resyncEnabled") ? config.resyncEnabled : DEFAULT_RULE_CONFIG.resyncEnabled;
		const recoveryValueFunc = Object.hasOwn(config, "recoveryValueFunc") ? config.recoveryValueFunc : DEFAULT_RULE_CONFIG.recoveryValueFunc;
		const shortName = this.ruleShortNameIdx << 12;
		this.ruleShortNameIdx++;
		this.shortRuleNameToFull[shortName] = ruleName;
		this.fullRuleNameToShort[ruleName] = shortName;
		let coreRuleFunction;
		if (this.outputCst === true) coreRuleFunction = function invokeRuleWithTry(...args) {
			try {
				this.ruleInvocationStateUpdate(shortName, ruleName, this.subruleIdx);
				impl.apply(this, args);
				const cst = this.CST_STACK[this.CST_STACK.length - 1];
				this.cstPostRule(cst);
				return cst;
			} catch (e) {
				return this.invokeRuleCatch(e, resyncEnabled, recoveryValueFunc);
			} finally {
				this.ruleFinallyStateUpdate();
			}
		};
		else coreRuleFunction = function invokeRuleWithTryCst(...args) {
			try {
				this.ruleInvocationStateUpdate(shortName, ruleName, this.subruleIdx);
				return impl.apply(this, args);
			} catch (e) {
				return this.invokeRuleCatch(e, resyncEnabled, recoveryValueFunc);
			} finally {
				this.ruleFinallyStateUpdate();
			}
		};
		return Object.assign(function rootRule(...args) {
			this.onBeforeParse(ruleName);
			try {
				return coreRuleFunction.apply(this, args);
			} finally {
				this.onAfterParse(ruleName);
			}
		}, {
			ruleName,
			originalGrammarAction: impl,
			coreRule: coreRuleFunction
		});
	}
	invokeRuleCatch(e, resyncEnabledConfig, recoveryValueFunc) {
		const isFirstInvokedRule = this.RULE_STACK_IDX === 0;
		const reSyncEnabled = resyncEnabledConfig && !this.isBackTracking() && this.recoveryEnabled;
		if (isRecognitionException(e)) {
			const recogError = e;
			if (reSyncEnabled) {
				const reSyncTokType = this.findReSyncTokenType();
				if (this.isInCurrentRuleReSyncSet(reSyncTokType)) {
					recogError.resyncedTokens = this.reSyncTo(reSyncTokType);
					if (this.outputCst) {
						const partialCstResult = this.CST_STACK[this.CST_STACK.length - 1];
						partialCstResult.recoveredNode = true;
						return partialCstResult;
					} else return recoveryValueFunc(e);
				} else {
					if (this.outputCst) {
						const partialCstResult = this.CST_STACK[this.CST_STACK.length - 1];
						partialCstResult.recoveredNode = true;
						recogError.partialCstResult = partialCstResult;
					}
					throw recogError;
				}
			} else if (isFirstInvokedRule) {
				this.moveToTerminatedState();
				return recoveryValueFunc(e);
			} else throw recogError;
		} else throw e;
	}
	optionInternal(actionORMethodDef, occurrence) {
		const key = this.getKeyForAutomaticLookahead(512, occurrence);
		return this.optionInternalLogic(actionORMethodDef, occurrence, key);
	}
	optionInternalLogic(actionORMethodDef, occurrence, key) {
		let lookAheadFunc = this.getLaFuncFromCache(key);
		let action;
		if (typeof actionORMethodDef !== "function") {
			action = actionORMethodDef.DEF;
			const predicate = actionORMethodDef.GATE;
			if (predicate !== void 0) {
				const orgLookaheadFunction = lookAheadFunc;
				lookAheadFunc = () => {
					return predicate.call(this) && orgLookaheadFunction.call(this);
				};
			}
		} else action = actionORMethodDef;
		if (lookAheadFunc.call(this) === true) return action.call(this);
	}
	atLeastOneInternal(prodOccurrence, actionORMethodDef) {
		const laKey = this.getKeyForAutomaticLookahead(AT_LEAST_ONE_IDX, prodOccurrence);
		return this.atLeastOneInternalLogic(prodOccurrence, actionORMethodDef, laKey);
	}
	atLeastOneInternalLogic(prodOccurrence, actionORMethodDef, key) {
		let lookAheadFunc = this.getLaFuncFromCache(key);
		let action;
		if (typeof actionORMethodDef !== "function") {
			action = actionORMethodDef.DEF;
			const predicate = actionORMethodDef.GATE;
			if (predicate !== void 0) {
				const orgLookaheadFunction = lookAheadFunc;
				lookAheadFunc = () => {
					return predicate.call(this) && orgLookaheadFunction.call(this);
				};
			}
		} else action = actionORMethodDef;
		if (lookAheadFunc.call(this) === true) {
			let notStuck = this.doSingleRepetition(action);
			while (lookAheadFunc.call(this) === true && notStuck === true) notStuck = this.doSingleRepetition(action);
		} else throw this.raiseEarlyExitException(prodOccurrence, PROD_TYPE.REPETITION_MANDATORY, actionORMethodDef.ERR_MSG);
		this.attemptInRepetitionRecovery(this.atLeastOneInternal, [prodOccurrence, actionORMethodDef], lookAheadFunc, AT_LEAST_ONE_IDX, prodOccurrence, NextTerminalAfterAtLeastOneWalker);
	}
	atLeastOneSepFirstInternal(prodOccurrence, options) {
		const laKey = this.getKeyForAutomaticLookahead(AT_LEAST_ONE_SEP_IDX, prodOccurrence);
		this.atLeastOneSepFirstInternalLogic(prodOccurrence, options, laKey);
	}
	atLeastOneSepFirstInternalLogic(prodOccurrence, options, key) {
		const action = options.DEF;
		const separator = options.SEP;
		if (this.getLaFuncFromCache(key).call(this) === true) {
			action.call(this);
			const separatorLookAheadFunc = () => {
				return this.tokenMatcher(this.LA_FAST(1), separator);
			};
			while (this.tokenMatcher(this.LA_FAST(1), separator) === true) {
				this.CONSUME(separator);
				action.call(this);
			}
			this.attemptInRepetitionRecovery(this.repetitionSepSecondInternal, [
				prodOccurrence,
				separator,
				separatorLookAheadFunc,
				action,
				NextTerminalAfterAtLeastOneSepWalker
			], separatorLookAheadFunc, AT_LEAST_ONE_SEP_IDX, prodOccurrence, NextTerminalAfterAtLeastOneSepWalker);
		} else throw this.raiseEarlyExitException(prodOccurrence, PROD_TYPE.REPETITION_MANDATORY_WITH_SEPARATOR, options.ERR_MSG);
	}
	manyInternal(prodOccurrence, actionORMethodDef) {
		const laKey = this.getKeyForAutomaticLookahead(768, prodOccurrence);
		return this.manyInternalLogic(prodOccurrence, actionORMethodDef, laKey);
	}
	manyInternalLogic(prodOccurrence, actionORMethodDef, key) {
		let lookaheadFunction = this.getLaFuncFromCache(key);
		let action;
		if (typeof actionORMethodDef !== "function") {
			action = actionORMethodDef.DEF;
			const predicate = actionORMethodDef.GATE;
			if (predicate !== void 0) {
				const orgLookaheadFunction = lookaheadFunction;
				lookaheadFunction = () => {
					return predicate.call(this) && orgLookaheadFunction.call(this);
				};
			}
		} else action = actionORMethodDef;
		let notStuck = true;
		while (lookaheadFunction.call(this) === true && notStuck === true) notStuck = this.doSingleRepetition(action);
		this.attemptInRepetitionRecovery(this.manyInternal, [prodOccurrence, actionORMethodDef], lookaheadFunction, 768, prodOccurrence, NextTerminalAfterManyWalker, notStuck);
	}
	manySepFirstInternal(prodOccurrence, options) {
		const laKey = this.getKeyForAutomaticLookahead(MANY_SEP_IDX, prodOccurrence);
		this.manySepFirstInternalLogic(prodOccurrence, options, laKey);
	}
	manySepFirstInternalLogic(prodOccurrence, options, key) {
		const action = options.DEF;
		const separator = options.SEP;
		if (this.getLaFuncFromCache(key).call(this) === true) {
			action.call(this);
			const separatorLookAheadFunc = () => {
				return this.tokenMatcher(this.LA_FAST(1), separator);
			};
			while (this.tokenMatcher(this.LA_FAST(1), separator) === true) {
				this.CONSUME(separator);
				action.call(this);
			}
			this.attemptInRepetitionRecovery(this.repetitionSepSecondInternal, [
				prodOccurrence,
				separator,
				separatorLookAheadFunc,
				action,
				NextTerminalAfterManySepWalker
			], separatorLookAheadFunc, MANY_SEP_IDX, prodOccurrence, NextTerminalAfterManySepWalker);
		}
	}
	repetitionSepSecondInternal(prodOccurrence, separator, separatorLookAheadFunc, action, nextTerminalAfterWalker) {
		while (separatorLookAheadFunc()) {
			this.CONSUME(separator);
			action.call(this);
		}
		/* istanbul ignore else */
		this.attemptInRepetitionRecovery(this.repetitionSepSecondInternal, [
			prodOccurrence,
			separator,
			separatorLookAheadFunc,
			action,
			nextTerminalAfterWalker
		], separatorLookAheadFunc, AT_LEAST_ONE_SEP_IDX, prodOccurrence, nextTerminalAfterWalker);
	}
	doSingleRepetition(action) {
		const beforeIteration = this.getLexerPosition();
		action.call(this);
		return this.getLexerPosition() > beforeIteration;
	}
	orInternal(altsOrOpts, occurrence) {
		const laKey = this.getKeyForAutomaticLookahead(256, occurrence);
		const alts = Array.isArray(altsOrOpts) ? altsOrOpts : altsOrOpts.DEF;
		const altIdxToTake = this.getLaFuncFromCache(laKey).call(this, alts);
		if (altIdxToTake !== void 0) return alts[altIdxToTake].ALT.call(this);
		this.raiseNoAltException(occurrence, altsOrOpts.ERR_MSG);
	}
	ruleFinallyStateUpdate() {
		this.RULE_STACK_IDX--;
		this.RULE_OCCURRENCE_STACK_IDX--;
		if (this.RULE_STACK_IDX >= 0) this.currRuleShortName = this.RULE_STACK[this.RULE_STACK_IDX];
		this.cstFinallyStateUpdate();
	}
	subruleInternal(ruleToCall, idx, options) {
		let ruleResult;
		try {
			const args = options !== void 0 ? options.ARGS : void 0;
			this.subruleIdx = idx;
			ruleResult = ruleToCall.coreRule.apply(this, args);
			this.cstPostNonTerminal(ruleResult, options !== void 0 && options.LABEL !== void 0 ? options.LABEL : ruleToCall.ruleName);
			return ruleResult;
		} catch (e) {
			throw this.subruleInternalError(e, options, ruleToCall.ruleName);
		}
	}
	subruleInternalError(e, options, ruleName) {
		if (isRecognitionException(e) && e.partialCstResult !== void 0) {
			this.cstPostNonTerminal(e.partialCstResult, options !== void 0 && options.LABEL !== void 0 ? options.LABEL : ruleName);
			delete e.partialCstResult;
		}
		throw e;
	}
	consumeInternal(tokType, idx, options) {
		let consumedToken;
		try {
			const nextToken = this.LA_FAST(1);
			if (this.tokenMatcher(nextToken, tokType) === true) {
				this.consumeToken();
				consumedToken = nextToken;
			} else this.consumeInternalError(tokType, nextToken, options);
		} catch (eFromConsumption) {
			consumedToken = this.consumeInternalRecovery(tokType, idx, eFromConsumption);
		}
		this.cstPostTerminal(options !== void 0 && options.LABEL !== void 0 ? options.LABEL : tokType.name, consumedToken);
		return consumedToken;
	}
	consumeInternalError(tokType, nextToken, options) {
		let msg;
		const previousToken = this.LA(0);
		if (options !== void 0 && options.ERR_MSG) msg = options.ERR_MSG;
		else msg = this.errorMessageProvider.buildMismatchTokenMessage({
			expected: tokType,
			actual: nextToken,
			previous: previousToken,
			ruleName: this.getCurrRuleFullName()
		});
		throw this.SAVE_ERROR(new MismatchedTokenException(msg, nextToken, previousToken));
	}
	consumeInternalRecovery(tokType, idx, eFromConsumption) {
		if (this.recoveryEnabled && eFromConsumption.name === "MismatchedTokenException" && !this.isBackTracking()) {
			const follows = this.getFollowsForInRuleRecovery(tokType, idx);
			try {
				return this.tryInRuleRecovery(tokType, follows);
			} catch (eFromInRuleRecovery) {
				if (eFromInRuleRecovery.name === "InRuleRecoveryException") throw eFromConsumption;
				else throw eFromInRuleRecovery;
			}
		} else throw eFromConsumption;
	}
	saveRecogState() {
		const savedErrors = this.errors;
		const savedRuleStack = this.RULE_STACK.slice(0, this.RULE_STACK_IDX + 1);
		return {
			errors: savedErrors,
			lexerState: this.exportLexerState(),
			RULE_STACK: savedRuleStack,
			CST_STACK: this.CST_STACK
		};
	}
	reloadRecogState(newState) {
		this.errors = newState.errors;
		this.importLexerState(newState.lexerState);
		const saved = newState.RULE_STACK;
		for (let i = 0; i < saved.length; i++) this.RULE_STACK[i] = saved[i];
		this.RULE_STACK_IDX = saved.length - 1;
		if (this.RULE_STACK_IDX >= 0) this.currRuleShortName = this.RULE_STACK[this.RULE_STACK_IDX];
	}
	ruleInvocationStateUpdate(shortName, fullName, idxInCallingRule) {
		this.RULE_OCCURRENCE_STACK[++this.RULE_OCCURRENCE_STACK_IDX] = idxInCallingRule;
		this.RULE_STACK[++this.RULE_STACK_IDX] = shortName;
		this.currRuleShortName = shortName;
		this.cstInvocationStateUpdate(fullName);
	}
	isBackTracking() {
		return this.isBackTrackingStack.length !== 0;
	}
	getCurrRuleFullName() {
		const shortName = this.currRuleShortName;
		return this.shortRuleNameToFull[shortName];
	}
	shortRuleNameToFullName(shortName) {
		return this.shortRuleNameToFull[shortName];
	}
	isAtEndOfInput() {
		return this.tokenMatcher(this.LA(1), EOF);
	}
	reset() {
		this.resetLexerState();
		this.subruleIdx = 0;
		this.currRuleShortName = 0;
		this.isBackTrackingStack = [];
		this.errors = [];
		this.RULE_STACK_IDX = -1;
		this.RULE_OCCURRENCE_STACK_IDX = -1;
		this.CST_STACK = [];
	}
	/**
	* Hook called before the root-level parsing rule is invoked.
	* This is only called when a rule is invoked directly by the consumer
	* (e.g., `parser.json()`), not when invoked as a sub-rule via SUBRULE.
	*
	* Override this method to perform actions before parsing begins.
	* The default implementation is a no-op.
	*
	* @param ruleName - The name of the root rule being invoked.
	*/
	onBeforeParse(ruleName) {
		for (let i = 0; i < this.maxLookahead + 1; i++) this.tokVector.push(END_OF_FILE);
	}
	/**
	* Hook called after the root-level parsing rule has completed (or thrown).
	* This is only called when a rule is invoked directly by the consumer
	* (e.g., `parser.json()`), not when invoked as a sub-rule via SUBRULE.
	*
	* This hook is called in a `finally` block, so it executes regardless of
	* whether parsing succeeded or threw an error.
	*
	* Override this method to perform actions after parsing completes.
	* The default implementation is a no-op.
	*
	* @param ruleName - The name of the root rule that was invoked.
	*/
	onAfterParse(ruleName) {
		if (this.isAtEndOfInput() === false) {
			const firstRedundantTok = this.LA(1);
			const errMsg = this.errorMessageProvider.buildNotAllInputParsedMessage({
				firstRedundant: firstRedundantTok,
				ruleName: this.getCurrRuleFullName()
			});
			this.SAVE_ERROR(new NotAllInputParsedException(errMsg, firstRedundantTok));
		}
		while (this.tokVector.at(-1) === END_OF_FILE) this.tokVector.pop();
	}
};
/**
* Trait responsible for runtime parsing errors.
*/
var ErrorHandler = class {
	initErrorHandler(config) {
		this._errors = [];
		this.errorMessageProvider = Object.hasOwn(config, "errorMessageProvider") ? config.errorMessageProvider : DEFAULT_PARSER_CONFIG.errorMessageProvider;
	}
	SAVE_ERROR(error) {
		if (isRecognitionException(error)) {
			error.context = {
				ruleStack: this.getHumanReadableRuleStack(),
				ruleOccurrenceStack: this.RULE_OCCURRENCE_STACK.slice(0, this.RULE_OCCURRENCE_STACK_IDX + 1)
			};
			this._errors.push(error);
			return error;
		} else throw Error("Trying to save an Error which is not a RecognitionException");
	}
	get errors() {
		return [...this._errors];
	}
	set errors(newErrors) {
		this._errors = newErrors;
	}
	raiseEarlyExitException(occurrence, prodType, userDefinedErrMsg) {
		const ruleName = this.getCurrRuleFullName();
		const ruleGrammar = this.getGAstProductions()[ruleName];
		const insideProdPaths = getLookaheadPathsForOptionalProd(occurrence, ruleGrammar, prodType, this.maxLookahead)[0];
		const actualTokens = [];
		for (let i = 1; i <= this.maxLookahead; i++) actualTokens.push(this.LA(i));
		const msg = this.errorMessageProvider.buildEarlyExitMessage({
			expectedIterationPaths: insideProdPaths,
			actual: actualTokens,
			previous: this.LA(0),
			customUserDescription: userDefinedErrMsg,
			ruleName
		});
		throw this.SAVE_ERROR(new EarlyExitException(msg, this.LA(1), this.LA(0)));
	}
	raiseNoAltException(occurrence, errMsgTypes) {
		const ruleName = this.getCurrRuleFullName();
		const ruleGrammar = this.getGAstProductions()[ruleName];
		const lookAheadPathsPerAlternative = getLookaheadPathsForOr(occurrence, ruleGrammar, this.maxLookahead);
		const actualTokens = [];
		for (let i = 1; i <= this.maxLookahead; i++) actualTokens.push(this.LA(i));
		const previousToken = this.LA(0);
		const errMsg = this.errorMessageProvider.buildNoViableAltMessage({
			expectedPathsPerAlt: lookAheadPathsPerAlternative,
			actual: actualTokens,
			previous: previousToken,
			customUserDescription: errMsgTypes,
			ruleName: this.getCurrRuleFullName()
		});
		throw this.SAVE_ERROR(new NoViableAltException(errMsg, this.LA(1), previousToken));
	}
};
var RECORDING_NULL_OBJECT = { description: "This Object indicates the Parser is during Recording Phase" };
Object.freeze(RECORDING_NULL_OBJECT);
var HANDLE_SEPARATOR = true;
var MAX_METHOD_IDX = Math.pow(2, 8) - 1;
var RFT = createToken({
	name: "RECORDING_PHASE_TOKEN",
	pattern: Lexer.NA
});
augmentTokenTypes([RFT]);
var RECORDING_PHASE_TOKEN = createTokenInstance(RFT, "This IToken indicates the Parser is in Recording Phase\n	See: https://chevrotain.io/docs/guide/internals.html#grammar-recording for details", -1, -1, -1, -1, -1, -1);
Object.freeze(RECORDING_PHASE_TOKEN);
var RECORDING_PHASE_CSTNODE = {
	name: "This CSTNode indicates the Parser is in Recording Phase\n	See: https://chevrotain.io/docs/guide/internals.html#grammar-recording for details",
	children: {}
};
/**
* This trait handles the creation of the GAST structure for Chevrotain Grammars
*/
var GastRecorder = class {
	initGastRecorder(config) {
		this.recordingProdStack = [];
		this.RECORDING_PHASE = false;
	}
	enableRecording() {
		this.RECORDING_PHASE = true;
		this.TRACE_INIT("Enable Recording", () => {
			/**
			* Warning Dark Voodoo Magic upcoming!
			* We are "replacing" the public parsing DSL methods API
			* With **new** alternative implementations on the Parser **instance**
			*
			* So far this is the only way I've found to avoid performance regressions during parsing time.
			* - Approx 30% performance regression was measured on Chrome 75 Canary when attempting to replace the "internal"
			*   implementations directly instead.
			*/
			for (let i = 0; i < 10; i++) {
				const idx = i > 0 ? i : "";
				this[`CONSUME${idx}`] = function(arg1, arg2) {
					return this.consumeInternalRecord(arg1, i, arg2);
				};
				this[`SUBRULE${idx}`] = function(arg1, arg2) {
					return this.subruleInternalRecord(arg1, i, arg2);
				};
				this[`OPTION${idx}`] = function(arg1) {
					return this.optionInternalRecord(arg1, i);
				};
				this[`OR${idx}`] = function(arg1) {
					return this.orInternalRecord(arg1, i);
				};
				this[`MANY${idx}`] = function(arg1) {
					this.manyInternalRecord(i, arg1);
				};
				this[`MANY_SEP${idx}`] = function(arg1) {
					this.manySepFirstInternalRecord(i, arg1);
				};
				this[`AT_LEAST_ONE${idx}`] = function(arg1) {
					this.atLeastOneInternalRecord(i, arg1);
				};
				this[`AT_LEAST_ONE_SEP${idx}`] = function(arg1) {
					this.atLeastOneSepFirstInternalRecord(i, arg1);
				};
			}
			this[`consume`] = function(idx, arg1, arg2) {
				return this.consumeInternalRecord(arg1, idx, arg2);
			};
			this[`subrule`] = function(idx, arg1, arg2) {
				return this.subruleInternalRecord(arg1, idx, arg2);
			};
			this[`option`] = function(idx, arg1) {
				return this.optionInternalRecord(arg1, idx);
			};
			this[`or`] = function(idx, arg1) {
				return this.orInternalRecord(arg1, idx);
			};
			this[`many`] = function(idx, arg1) {
				this.manyInternalRecord(idx, arg1);
			};
			this[`atLeastOne`] = function(idx, arg1) {
				this.atLeastOneInternalRecord(idx, arg1);
			};
			this.ACTION = this.ACTION_RECORD;
			this.BACKTRACK = this.BACKTRACK_RECORD;
			this.LA = this.LA_RECORD;
		});
	}
	disableRecording() {
		this.RECORDING_PHASE = false;
		this.TRACE_INIT("Deleting Recording methods", () => {
			const that = this;
			for (let i = 0; i < 10; i++) {
				const idx = i > 0 ? i : "";
				delete that[`CONSUME${idx}`];
				delete that[`SUBRULE${idx}`];
				delete that[`OPTION${idx}`];
				delete that[`OR${idx}`];
				delete that[`MANY${idx}`];
				delete that[`MANY_SEP${idx}`];
				delete that[`AT_LEAST_ONE${idx}`];
				delete that[`AT_LEAST_ONE_SEP${idx}`];
			}
			delete that[`consume`];
			delete that[`subrule`];
			delete that[`option`];
			delete that[`or`];
			delete that[`many`];
			delete that[`atLeastOne`];
			delete that.ACTION;
			delete that.BACKTRACK;
			delete that.LA;
		});
	}
	ACTION_RECORD(impl) {}
	BACKTRACK_RECORD(grammarRule, args) {
		return () => true;
	}
	LA_RECORD(howMuch) {
		return END_OF_FILE;
	}
	topLevelRuleRecord(name, def) {
		try {
			const newTopLevelRule = new Rule({
				definition: [],
				name
			});
			newTopLevelRule.name = name;
			this.recordingProdStack.push(newTopLevelRule);
			def.call(this);
			this.recordingProdStack.pop();
			return newTopLevelRule;
		} catch (originalError) {
			if (originalError.KNOWN_RECORDER_ERROR !== true) try {
				originalError.message = originalError.message + "\n	 This error was thrown during the \"grammar recording phase\" For more info see:\n	https://chevrotain.io/docs/guide/internals.html#grammar-recording";
			} catch (mutabilityError) {
				throw originalError;
			}
			throw originalError;
		}
	}
	optionInternalRecord(actionORMethodDef, occurrence) {
		return recordProd.call(this, Option, actionORMethodDef, occurrence);
	}
	atLeastOneInternalRecord(occurrence, actionORMethodDef) {
		recordProd.call(this, RepetitionMandatory, actionORMethodDef, occurrence);
	}
	atLeastOneSepFirstInternalRecord(occurrence, options) {
		recordProd.call(this, RepetitionMandatoryWithSeparator, options, occurrence, HANDLE_SEPARATOR);
	}
	manyInternalRecord(occurrence, actionORMethodDef) {
		recordProd.call(this, Repetition, actionORMethodDef, occurrence);
	}
	manySepFirstInternalRecord(occurrence, options) {
		recordProd.call(this, RepetitionWithSeparator, options, occurrence, HANDLE_SEPARATOR);
	}
	orInternalRecord(altsOrOpts, occurrence) {
		return recordOrProd.call(this, altsOrOpts, occurrence);
	}
	subruleInternalRecord(ruleToCall, occurrence, options) {
		assertMethodIdxIsValid(occurrence);
		if (!ruleToCall || !Object.hasOwn(ruleToCall, "ruleName")) {
			const error = /* @__PURE__ */ new Error(`<SUBRULE${getIdxSuffix(occurrence)}> argument is invalid expecting a Parser method reference but got: <${JSON.stringify(ruleToCall)}>\n inside top level rule: <${this.recordingProdStack[0].name}>`);
			error.KNOWN_RECORDER_ERROR = true;
			throw error;
		}
		const prevProd = this.recordingProdStack.at(-1);
		const ruleName = ruleToCall.ruleName;
		const newNoneTerminal = new NonTerminal({
			idx: occurrence,
			nonTerminalName: ruleName,
			label: options === null || options === void 0 ? void 0 : options.LABEL,
			referencedRule: void 0
		});
		prevProd.definition.push(newNoneTerminal);
		return this.outputCst ? RECORDING_PHASE_CSTNODE : RECORDING_NULL_OBJECT;
	}
	consumeInternalRecord(tokType, occurrence, options) {
		assertMethodIdxIsValid(occurrence);
		if (!hasShortKeyProperty(tokType)) {
			const error = /* @__PURE__ */ new Error(`<CONSUME${getIdxSuffix(occurrence)}> argument is invalid expecting a TokenType reference but got: <${JSON.stringify(tokType)}>\n inside top level rule: <${this.recordingProdStack[0].name}>`);
			error.KNOWN_RECORDER_ERROR = true;
			throw error;
		}
		const prevProd = this.recordingProdStack.at(-1);
		const newNoneTerminal = new Terminal({
			idx: occurrence,
			terminalType: tokType,
			label: options === null || options === void 0 ? void 0 : options.LABEL
		});
		prevProd.definition.push(newNoneTerminal);
		return RECORDING_PHASE_TOKEN;
	}
};
function recordProd(prodConstructor, mainProdArg, occurrence, handleSep = false) {
	assertMethodIdxIsValid(occurrence);
	const prevProd = this.recordingProdStack.at(-1);
	const grammarAction = typeof mainProdArg === "function" ? mainProdArg : mainProdArg.DEF;
	const newProd = new prodConstructor({
		definition: [],
		idx: occurrence
	});
	if (handleSep) newProd.separator = mainProdArg.SEP;
	if (Object.hasOwn(mainProdArg, "MAX_LOOKAHEAD")) newProd.maxLookahead = mainProdArg.MAX_LOOKAHEAD;
	this.recordingProdStack.push(newProd);
	grammarAction.call(this);
	prevProd.definition.push(newProd);
	this.recordingProdStack.pop();
	return RECORDING_NULL_OBJECT;
}
function recordOrProd(mainProdArg, occurrence) {
	assertMethodIdxIsValid(occurrence);
	const prevProd = this.recordingProdStack.at(-1);
	const hasOptions = Array.isArray(mainProdArg) === false;
	const alts = hasOptions === false ? mainProdArg : mainProdArg.DEF;
	const newOrProd = new Alternation({
		definition: [],
		idx: occurrence,
		ignoreAmbiguities: hasOptions && mainProdArg.IGNORE_AMBIGUITIES === true
	});
	if (Object.hasOwn(mainProdArg, "MAX_LOOKAHEAD")) newOrProd.maxLookahead = mainProdArg.MAX_LOOKAHEAD;
	newOrProd.hasPredicates = alts.some((currAlt) => typeof currAlt.GATE === "function");
	prevProd.definition.push(newOrProd);
	alts.forEach((currAlt) => {
		const currAltFlat = new Alternative({ definition: [] });
		newOrProd.definition.push(currAltFlat);
		if (Object.hasOwn(currAlt, "IGNORE_AMBIGUITIES")) currAltFlat.ignoreAmbiguities = currAlt.IGNORE_AMBIGUITIES;
		else if (Object.hasOwn(currAlt, "GATE")) currAltFlat.ignoreAmbiguities = true;
		this.recordingProdStack.push(currAltFlat);
		currAlt.ALT.call(this);
		this.recordingProdStack.pop();
	});
	return RECORDING_NULL_OBJECT;
}
function getIdxSuffix(idx) {
	return idx === 0 ? "" : `${idx}`;
}
function assertMethodIdxIsValid(idx) {
	if (idx < 0 || idx > MAX_METHOD_IDX) {
		const error = /* @__PURE__ */ new Error(`Invalid DSL Method idx value: <${idx}>\n\tIdx value must be a none negative value smaller than ${MAX_METHOD_IDX + 1}`);
		error.KNOWN_RECORDER_ERROR = true;
		throw error;
	}
}
/**
* Trait responsible for runtime parsing errors.
*/
var PerformanceTracer = class {
	initPerformanceTracer(config) {
		if (Object.hasOwn(config, "traceInitPerf")) {
			const userTraceInitPerf = config.traceInitPerf;
			const traceIsNumber = typeof userTraceInitPerf === "number";
			this.traceInitMaxIdent = traceIsNumber ? userTraceInitPerf : Infinity;
			this.traceInitPerf = traceIsNumber ? userTraceInitPerf > 0 : userTraceInitPerf;
		} else {
			this.traceInitMaxIdent = 0;
			this.traceInitPerf = DEFAULT_PARSER_CONFIG.traceInitPerf;
		}
		this.traceInitIndent = -1;
	}
	TRACE_INIT(phaseDesc, phaseImpl) {
		if (this.traceInitPerf === true) {
			this.traceInitIndent++;
			const indent = new Array(this.traceInitIndent + 1).join("	");
			if (this.traceInitIndent < this.traceInitMaxIdent) console.log(`${indent}--> <${phaseDesc}>`);
			const { time, value } = timer(phaseImpl);
			/* istanbul ignore next - Difficult to reproduce specific performance behavior (>10ms) in tests */
			const traceMethod = time > 10 ? console.warn : console.log;
			if (this.traceInitIndent < this.traceInitMaxIdent) traceMethod(`${indent}<-- <${phaseDesc}> time: ${time}ms`);
			this.traceInitIndent--;
			return value;
		} else return phaseImpl();
	}
};
function applyMixins(derivedCtor, baseCtors) {
	baseCtors.forEach((baseCtor) => {
		const baseProto = baseCtor.prototype;
		Object.getOwnPropertyNames(baseProto).forEach((propName) => {
			if (propName === "constructor") return;
			const basePropDescriptor = Object.getOwnPropertyDescriptor(baseProto, propName);
			if (basePropDescriptor && (basePropDescriptor.get || basePropDescriptor.set)) Object.defineProperty(derivedCtor.prototype, propName, basePropDescriptor);
			else derivedCtor.prototype[propName] = baseCtor.prototype[propName];
		});
	});
}
var END_OF_FILE = createTokenInstance(EOF, "", NaN, NaN, NaN, NaN, NaN, NaN);
Object.freeze(END_OF_FILE);
var DEFAULT_PARSER_CONFIG = Object.freeze({
	recoveryEnabled: false,
	maxLookahead: 3,
	dynamicTokensEnabled: false,
	outputCst: true,
	errorMessageProvider: defaultParserErrorProvider,
	nodeLocationTracking: "none",
	traceInitPerf: false,
	skipValidations: false
});
var DEFAULT_RULE_CONFIG = Object.freeze({
	recoveryValueFunc: () => void 0,
	resyncEnabled: true
});
var ParserDefinitionErrorType;
(function(ParserDefinitionErrorType) {
	ParserDefinitionErrorType[ParserDefinitionErrorType["INVALID_RULE_NAME"] = 0] = "INVALID_RULE_NAME";
	ParserDefinitionErrorType[ParserDefinitionErrorType["DUPLICATE_RULE_NAME"] = 1] = "DUPLICATE_RULE_NAME";
	ParserDefinitionErrorType[ParserDefinitionErrorType["INVALID_RULE_OVERRIDE"] = 2] = "INVALID_RULE_OVERRIDE";
	ParserDefinitionErrorType[ParserDefinitionErrorType["DUPLICATE_PRODUCTIONS"] = 3] = "DUPLICATE_PRODUCTIONS";
	ParserDefinitionErrorType[ParserDefinitionErrorType["UNRESOLVED_SUBRULE_REF"] = 4] = "UNRESOLVED_SUBRULE_REF";
	ParserDefinitionErrorType[ParserDefinitionErrorType["LEFT_RECURSION"] = 5] = "LEFT_RECURSION";
	ParserDefinitionErrorType[ParserDefinitionErrorType["NONE_LAST_EMPTY_ALT"] = 6] = "NONE_LAST_EMPTY_ALT";
	ParserDefinitionErrorType[ParserDefinitionErrorType["AMBIGUOUS_ALTS"] = 7] = "AMBIGUOUS_ALTS";
	ParserDefinitionErrorType[ParserDefinitionErrorType["CONFLICT_TOKENS_RULES_NAMESPACE"] = 8] = "CONFLICT_TOKENS_RULES_NAMESPACE";
	ParserDefinitionErrorType[ParserDefinitionErrorType["INVALID_TOKEN_NAME"] = 9] = "INVALID_TOKEN_NAME";
	ParserDefinitionErrorType[ParserDefinitionErrorType["NO_NON_EMPTY_LOOKAHEAD"] = 10] = "NO_NON_EMPTY_LOOKAHEAD";
	ParserDefinitionErrorType[ParserDefinitionErrorType["AMBIGUOUS_PREFIX_ALTS"] = 11] = "AMBIGUOUS_PREFIX_ALTS";
	ParserDefinitionErrorType[ParserDefinitionErrorType["TOO_MANY_ALTS"] = 12] = "TOO_MANY_ALTS";
	ParserDefinitionErrorType[ParserDefinitionErrorType["CUSTOM_LOOKAHEAD_VALIDATION"] = 13] = "CUSTOM_LOOKAHEAD_VALIDATION";
})(ParserDefinitionErrorType || (ParserDefinitionErrorType = {}));
function EMPTY_ALT(value = void 0) {
	return function() {
		return value;
	};
}
var Parser = class Parser {
	/**
	*  @deprecated use the **instance** method with the same name instead
	*/
	static performSelfAnalysis(parserInstance) {
		throw Error("The **static** `performSelfAnalysis` method has been deprecated.	\nUse the **instance** method with the same name instead.");
	}
	performSelfAnalysis() {
		this.TRACE_INIT("performSelfAnalysis", () => {
			let defErrorsMsgs;
			this.selfAnalysisDone = true;
			const className = this.className;
			this.TRACE_INIT("toFastProps", () => {
				toFastProperties(this);
			});
			this.TRACE_INIT("Grammar Recording", () => {
				try {
					this.enableRecording();
					this.definedRulesNames.forEach((currRuleName) => {
						const originalGrammarAction = this[currRuleName]["originalGrammarAction"];
						let recordedRuleGast;
						this.TRACE_INIT(`${currRuleName} Rule`, () => {
							recordedRuleGast = this.topLevelRuleRecord(currRuleName, originalGrammarAction);
						});
						this.gastProductionsCache[currRuleName] = recordedRuleGast;
					});
				} finally {
					this.disableRecording();
				}
			});
			let resolverErrors = [];
			this.TRACE_INIT("Grammar Resolving", () => {
				resolverErrors = resolveGrammar({ rules: Object.values(this.gastProductionsCache) });
				this.definitionErrors = this.definitionErrors.concat(resolverErrors);
			});
			this.TRACE_INIT("Grammar Validations", () => {
				if (resolverErrors.length === 0 && this.skipValidations === false) {
					const validationErrors = validateGrammar({
						rules: Object.values(this.gastProductionsCache),
						tokenTypes: Object.values(this.tokensMap),
						errMsgProvider: defaultGrammarValidatorErrorProvider,
						grammarName: className
					});
					const lookaheadValidationErrors = validateLookahead({
						lookaheadStrategy: this.lookaheadStrategy,
						rules: Object.values(this.gastProductionsCache),
						tokenTypes: Object.values(this.tokensMap),
						grammarName: className
					});
					this.definitionErrors = this.definitionErrors.concat(validationErrors, lookaheadValidationErrors);
				}
			});
			if (this.definitionErrors.length === 0) {
				if (this.recoveryEnabled) this.TRACE_INIT("computeAllProdsFollows", () => {
					const allFollows = computeAllProdsFollows(Object.values(this.gastProductionsCache));
					this.resyncFollows = allFollows;
				});
				this.TRACE_INIT("ComputeLookaheadFunctions", () => {
					var _a, _b;
					(_b = (_a = this.lookaheadStrategy).initialize) === null || _b === void 0 || _b.call(_a, { rules: Object.values(this.gastProductionsCache) });
					this.preComputeLookaheadFunctions(Object.values(this.gastProductionsCache));
				});
			}
			if (!Parser.DEFER_DEFINITION_ERRORS_HANDLING && this.definitionErrors.length !== 0) {
				defErrorsMsgs = this.definitionErrors.map((defError) => defError.message);
				throw new Error(`Parser Definition Errors detected:\n ${defErrorsMsgs.join("\n-------------------------------\n")}`);
			}
		});
	}
	constructor(tokenVocabulary, config) {
		this.definitionErrors = [];
		this.selfAnalysisDone = false;
		const that = this;
		that.initErrorHandler(config);
		that.initLexerAdapter();
		that.initLooksAhead(config);
		that.initRecognizerEngine(tokenVocabulary, config);
		that.initRecoverable(config);
		that.initTreeBuilder(config);
		that.initGastRecorder(config);
		that.initPerformanceTracer(config);
		if (Object.hasOwn(config, "ignoredIssues")) throw new Error("The <ignoredIssues> IParserConfig property has been deprecated.\n	Please use the <IGNORE_AMBIGUITIES> flag on the relevant DSL method instead.\n	See: https://chevrotain.io/docs/guide/resolving_grammar_errors.html#IGNORING_AMBIGUITIES\n	For further details.");
		this.skipValidations = Object.hasOwn(config, "skipValidations") ? config.skipValidations : DEFAULT_PARSER_CONFIG.skipValidations;
	}
};
Parser.DEFER_DEFINITION_ERRORS_HANDLING = false;
applyMixins(Parser, [
	Recoverable,
	LooksAhead,
	TreeBuilder,
	LexerAdapter,
	RecognizerEngine,
	RecognizerApi,
	ErrorHandler,
	GastRecorder,
	PerformanceTracer
]);
var EmbeddedActionsParser = class extends Parser {
	constructor(tokenVocabulary, config = DEFAULT_PARSER_CONFIG) {
		const configClone = Object.assign({}, config);
		configClone.outputCst = false;
		super(tokenVocabulary, configClone);
	}
};
export { RepetitionWithSeparator as _, defaultParserErrorProvider as a, RegExpParser as b, tokenMatcher as c, Alternation as d, NonTerminal as f, RepetitionMandatoryWithSeparator as g, RepetitionMandatory as h, getLookaheadPaths as i, Lexer as l, Repetition as m, EmbeddedActionsParser as n, EOF as o, Option as p, LLkLookaheadStrategy as r, tokenLabel as s, EMPTY_ALT as t, defaultLexerErrorProvider as u, Terminal as v, BaseRegExpVisitor as y };
