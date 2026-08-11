import { a as __toCommonJS, i as __reExport, n as __esmMin, o as __toESM, r as __exportAll, t as __commonJSMin } from "./rolldown-runtime.js";
import { t as LLStarLookaheadStrategy } from "./chevrotain-allstar.js";
import { a as defaultParserErrorProvider, b as RegExpParser, l as Lexer, n as EmbeddedActionsParser, o as EOF, r as LLkLookaheadStrategy, t as EMPTY_ALT, u as defaultLexerErrorProvider, y as BaseRegExpVisitor } from "./chevrotain.js";
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
function isAstNode(obj) {
	return typeof obj === "object" && obj !== null && typeof obj.$type === "string";
}
function isReference(obj) {
	return typeof obj === "object" && obj !== null && typeof obj.$refText === "string" && "ref" in obj;
}
function isMultiReference(obj) {
	return typeof obj === "object" && obj !== null && typeof obj.$refText === "string" && "items" in obj;
}
function isAstNodeDescription(obj) {
	return typeof obj === "object" && obj !== null && typeof obj.name === "string" && typeof obj.type === "string" && typeof obj.path === "string";
}
function isLinkingError(obj) {
	return typeof obj === "object" && obj !== null && typeof obj.info === "object" && typeof obj.message === "string";
}
/**
* An abstract implementation of the {@link AstReflection} interface.
* Serves to cache subtype computation results to improve performance throughout different parts of Langium.
*/
var AbstractAstReflection = class {
	constructor() {
		this.subtypes = {};
		this.allSubtypes = {};
	}
	getAllTypes() {
		return Object.keys(this.types);
	}
	getReferenceType(refInfo) {
		const metaData = this.types[refInfo.container.$type];
		if (!metaData) throw new Error(`Type ${refInfo.container.$type || "undefined"} not found.`);
		const referenceType = metaData.properties[refInfo.property]?.referenceType;
		if (!referenceType) throw new Error(`Property ${refInfo.property || "undefined"} of type ${refInfo.container.$type} is not a reference.`);
		return referenceType;
	}
	getTypeMetaData(type) {
		const result = this.types[type];
		if (!result) return {
			name: type,
			properties: {},
			superTypes: []
		};
		return result;
	}
	isInstance(node, type) {
		return isAstNode(node) && this.isSubtype(node.$type, type);
	}
	isSubtype(subtype, supertype) {
		if (subtype === supertype) return true;
		let nested = this.subtypes[subtype];
		if (!nested) nested = this.subtypes[subtype] = {};
		const existing = nested[supertype];
		if (existing !== void 0) return existing;
		else {
			const metaData = this.types[subtype];
			const result = metaData ? metaData.superTypes.some((s) => this.isSubtype(s, supertype)) : false;
			nested[supertype] = result;
			return result;
		}
	}
	getAllSubTypes(type) {
		const existing = this.allSubtypes[type];
		if (existing) return existing;
		else {
			const allTypes = this.getAllTypes();
			const types = [];
			for (const possibleSubType of allTypes) if (this.isSubtype(possibleSubType, type)) types.push(possibleSubType);
			this.allSubtypes[type] = types;
			return types;
		}
	}
};
function isCompositeCstNode(node) {
	return typeof node === "object" && node !== null && Array.isArray(node.content);
}
function isLeafCstNode(node) {
	return typeof node === "object" && node !== null && typeof node.tokenType === "object";
}
function isRootCstNode(node) {
	return isCompositeCstNode(node) && typeof node.fullText === "string";
}
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* The default implementation of `Stream` works with two input functions:
*  - The first function creates the initial state of an iteration.
*  - The second function gets the current state as argument and returns an `IteratorResult`.
*/
var StreamImpl = class StreamImpl {
	constructor(startFn, nextFn) {
		this.startFn = startFn;
		this.nextFn = nextFn;
	}
	iterator() {
		const iterator = {
			state: this.startFn(),
			next: () => this.nextFn(iterator.state),
			[Symbol.iterator]: () => iterator
		};
		return iterator;
	}
	[Symbol.iterator]() {
		return this.iterator();
	}
	isEmpty() {
		const iterator = this.iterator();
		return Boolean(iterator.next().done);
	}
	count() {
		const iterator = this.iterator();
		let count = 0;
		let next = iterator.next();
		while (!next.done) {
			count++;
			next = iterator.next();
		}
		return count;
	}
	toArray() {
		const result = [];
		const iterator = this.iterator();
		let next;
		do {
			next = iterator.next();
			if (next.value !== void 0) result.push(next.value);
		} while (!next.done);
		return result;
	}
	toSet() {
		return new Set(this);
	}
	toMap(keyFn, valueFn) {
		const entryStream = this.map((element) => [keyFn ? keyFn(element) : element, valueFn ? valueFn(element) : element]);
		return new Map(entryStream);
	}
	toString() {
		return this.join();
	}
	concat(other) {
		return new StreamImpl(() => ({
			first: this.startFn(),
			firstDone: false,
			iterator: other[Symbol.iterator]()
		}), (state) => {
			let result;
			if (!state.firstDone) {
				do {
					result = this.nextFn(state.first);
					if (!result.done) return result;
				} while (!result.done);
				state.firstDone = true;
			}
			do {
				result = state.iterator.next();
				if (!result.done) return result;
			} while (!result.done);
			return DONE_RESULT;
		});
	}
	join(separator = ",") {
		const iterator = this.iterator();
		let value = "";
		let result;
		let addSeparator = false;
		do {
			result = iterator.next();
			if (!result.done) {
				if (addSeparator) value += separator;
				value += toString(result.value);
			}
			addSeparator = true;
		} while (!result.done);
		return value;
	}
	indexOf(searchElement, fromIndex = 0) {
		const iterator = this.iterator();
		let index = 0;
		let next = iterator.next();
		while (!next.done) {
			if (index >= fromIndex && next.value === searchElement) return index;
			next = iterator.next();
			index++;
		}
		return -1;
	}
	every(predicate) {
		const iterator = this.iterator();
		let next = iterator.next();
		while (!next.done) {
			if (!predicate(next.value)) return false;
			next = iterator.next();
		}
		return true;
	}
	some(predicate) {
		const iterator = this.iterator();
		let next = iterator.next();
		while (!next.done) {
			if (predicate(next.value)) return true;
			next = iterator.next();
		}
		return false;
	}
	forEach(callbackfn) {
		const iterator = this.iterator();
		let index = 0;
		let next = iterator.next();
		while (!next.done) {
			callbackfn(next.value, index);
			next = iterator.next();
			index++;
		}
	}
	map(callbackfn) {
		return new StreamImpl(this.startFn, (state) => {
			const { done, value } = this.nextFn(state);
			if (done) return DONE_RESULT;
			else return {
				done: false,
				value: callbackfn(value)
			};
		});
	}
	filter(predicate) {
		return new StreamImpl(this.startFn, (state) => {
			let result;
			do {
				result = this.nextFn(state);
				if (!result.done && predicate(result.value)) return result;
			} while (!result.done);
			return DONE_RESULT;
		});
	}
	nonNullable() {
		return this.filter((e) => e !== void 0 && e !== null);
	}
	reduce(callbackfn, initialValue) {
		const iterator = this.iterator();
		let previousValue = initialValue;
		let next = iterator.next();
		while (!next.done) {
			if (previousValue === void 0) previousValue = next.value;
			else previousValue = callbackfn(previousValue, next.value);
			next = iterator.next();
		}
		return previousValue;
	}
	reduceRight(callbackfn, initialValue) {
		return this.recursiveReduce(this.iterator(), callbackfn, initialValue);
	}
	recursiveReduce(iterator, callbackfn, initialValue) {
		const next = iterator.next();
		if (next.done) return initialValue;
		const previousValue = this.recursiveReduce(iterator, callbackfn, initialValue);
		if (previousValue === void 0) return next.value;
		return callbackfn(previousValue, next.value);
	}
	find(predicate) {
		const iterator = this.iterator();
		let next = iterator.next();
		while (!next.done) {
			if (predicate(next.value)) return next.value;
			next = iterator.next();
		}
	}
	findIndex(predicate) {
		const iterator = this.iterator();
		let index = 0;
		let next = iterator.next();
		while (!next.done) {
			if (predicate(next.value)) return index;
			next = iterator.next();
			index++;
		}
		return -1;
	}
	includes(searchElement) {
		const iterator = this.iterator();
		let next = iterator.next();
		while (!next.done) {
			if (next.value === searchElement) return true;
			next = iterator.next();
		}
		return false;
	}
	flatMap(callbackfn) {
		return new StreamImpl(() => ({ this: this.startFn() }), (state) => {
			do {
				if (state.iterator) {
					const next = state.iterator.next();
					if (next.done) state.iterator = void 0;
					else return next;
				}
				const { done, value } = this.nextFn(state.this);
				if (!done) {
					const mapped = callbackfn(value);
					if (isIterable(mapped)) state.iterator = mapped[Symbol.iterator]();
					else return {
						done: false,
						value: mapped
					};
				}
			} while (state.iterator);
			return DONE_RESULT;
		});
	}
	flat(depth) {
		if (depth === void 0) depth = 1;
		if (depth <= 0) return this;
		const stream = depth > 1 ? this.flat(depth - 1) : this;
		return new StreamImpl(() => ({ this: stream.startFn() }), (state) => {
			do {
				if (state.iterator) {
					const next = state.iterator.next();
					if (next.done) state.iterator = void 0;
					else return next;
				}
				const { done, value } = stream.nextFn(state.this);
				if (!done) if (isIterable(value)) state.iterator = value[Symbol.iterator]();
				else return {
					done: false,
					value
				};
			} while (state.iterator);
			return DONE_RESULT;
		});
	}
	head() {
		const result = this.iterator().next();
		if (result.done) return;
		return result.value;
	}
	tail(skipCount = 1) {
		return new StreamImpl(() => {
			const state = this.startFn();
			for (let i = 0; i < skipCount; i++) if (this.nextFn(state).done) return state;
			return state;
		}, this.nextFn);
	}
	limit(maxSize) {
		return new StreamImpl(() => ({
			size: 0,
			state: this.startFn()
		}), (state) => {
			state.size++;
			if (state.size > maxSize) return DONE_RESULT;
			return this.nextFn(state.state);
		});
	}
	distinct(by) {
		return new StreamImpl(() => ({
			set: /* @__PURE__ */ new Set(),
			internalState: this.startFn()
		}), (state) => {
			let result;
			do {
				result = this.nextFn(state.internalState);
				if (!result.done) {
					const value = by ? by(result.value) : result.value;
					if (!state.set.has(value)) {
						state.set.add(value);
						return result;
					}
				}
			} while (!result.done);
			return DONE_RESULT;
		});
	}
	exclude(other, key) {
		const otherKeySet = /* @__PURE__ */ new Set();
		for (const item of other) {
			const value = key ? key(item) : item;
			otherKeySet.add(value);
		}
		return this.filter((e) => {
			const ownKey = key ? key(e) : e;
			return !otherKeySet.has(ownKey);
		});
	}
};
function toString(item) {
	if (typeof item === "string") return item;
	if (typeof item === "undefined") return "undefined";
	if (typeof item.toString === "function") return item.toString();
	return Object.prototype.toString.call(item);
}
function isIterable(obj) {
	return !!obj && typeof obj[Symbol.iterator] === "function";
}
/**
* An empty stream of any type.
*/
var EMPTY_STREAM = new StreamImpl(() => void 0, () => DONE_RESULT);
/**
* Use this `IteratorResult` when implementing a `StreamImpl` to indicate that there are no more elements in the stream.
*/
var DONE_RESULT = Object.freeze({
	done: true,
	value: void 0
});
/**
* Create a stream from one or more iterables or array-likes.
*/
function stream(...collections) {
	if (collections.length === 1) {
		const collection = collections[0];
		if (collection instanceof StreamImpl) return collection;
		if (isIterable(collection)) return new StreamImpl(() => collection[Symbol.iterator](), (iterator) => iterator.next());
		if (typeof collection.length === "number") return new StreamImpl(() => ({ index: 0 }), (state) => {
			if (state.index < collection.length) return {
				done: false,
				value: collection[state.index++]
			};
			else return DONE_RESULT;
		});
	}
	if (collections.length > 1) return new StreamImpl(() => ({
		collIndex: 0,
		arrIndex: 0
	}), (state) => {
		do {
			if (state.iterator) {
				const next = state.iterator.next();
				if (!next.done) return next;
				state.iterator = void 0;
			}
			if (state.array) {
				if (state.arrIndex < state.array.length) return {
					done: false,
					value: state.array[state.arrIndex++]
				};
				state.array = void 0;
				state.arrIndex = 0;
			}
			if (state.collIndex < collections.length) {
				const collection = collections[state.collIndex++];
				if (isIterable(collection)) state.iterator = collection[Symbol.iterator]();
				else if (collection && typeof collection.length === "number") state.array = collection;
			}
		} while (state.iterator || state.array || state.collIndex < collections.length);
		return DONE_RESULT;
	});
	return EMPTY_STREAM;
}
/**
* The default implementation of `TreeStream` takes a root element and a function that computes the
* children of its argument. Whether the root node included in the stream is controlled with the
* `includeRoot` option, which defaults to `false`.
*/
var TreeStreamImpl = class extends StreamImpl {
	constructor(root, children, options) {
		super(() => ({
			iterators: options?.includeRoot ? [[root][Symbol.iterator]()] : [children(root)[Symbol.iterator]()],
			pruned: false
		}), (state) => {
			if (state.pruned) {
				state.iterators.pop();
				state.pruned = false;
			}
			while (state.iterators.length > 0) {
				const next = state.iterators[state.iterators.length - 1].next();
				if (next.done) state.iterators.pop();
				else {
					state.iterators.push(children(next.value)[Symbol.iterator]());
					return next;
				}
			}
			return DONE_RESULT;
		});
	}
	iterator() {
		const iterator = {
			state: this.startFn(),
			next: () => this.nextFn(iterator.state),
			prune: () => {
				iterator.state.pruned = true;
			},
			[Symbol.iterator]: () => iterator
		};
		return iterator;
	}
};
/**
* A set of utility functions that reduce a stream to a single value.
*/
var Reduction;
(function(Reduction) {
	/**
	* Compute the sum of a number stream.
	*/
	function sum(stream) {
		return stream.reduce((a, b) => a + b, 0);
	}
	Reduction.sum = sum;
	/**
	* Compute the product of a number stream.
	*/
	function product(stream) {
		return stream.reduce((a, b) => a * b, 0);
	}
	Reduction.product = product;
	/**
	* Compute the minimum of a number stream. Returns `undefined` if the stream is empty.
	*/
	function min(stream) {
		return stream.reduce((a, b) => Math.min(a, b));
	}
	Reduction.min = min;
	/**
	* Compute the maximum of a number stream. Returns `undefined` if the stream is empty.
	*/
	function max(stream) {
		return stream.reduce((a, b) => Math.max(a, b));
	}
	Reduction.max = max;
})(Reduction || (Reduction = {}));
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* Link the `$container` and other related properties of every AST node that is directly contained
* in the given `node`.
*/
function linkContentToContainer(node, options = {}) {
	for (const [name, value] of Object.entries(node)) if (!name.startsWith("$")) {
		if (Array.isArray(value)) value.forEach((item, index) => {
			if (isAstNode(item)) {
				item.$container = node;
				item.$containerProperty = name;
				item.$containerIndex = index;
				if (options.deep) linkContentToContainer(item, options);
			}
		});
		else if (isAstNode(value)) {
			value.$container = node;
			value.$containerProperty = name;
			if (options.deep) linkContentToContainer(value, options);
		}
	}
}
/**
* Walk along the hierarchy of containers from the given AST node to the root and return the first
* node that matches the type predicate. If the start node itself matches, it is returned.
* If no container matches, `undefined` is returned.
*/
function getContainerOfType(node, typePredicate) {
	let item = node;
	while (item) {
		if (typePredicate(item)) return item;
		item = item.$container;
	}
}
/**
* Retrieve the document in which the given AST node is contained. A reference to the document is
* usually held by the root node of the AST.
*
* @throws an error if the node is not contained in a document.
*/
function getDocument(node) {
	const result = findRootNode(node).$document;
	if (!result) throw new Error("AST node has no document.");
	return result;
}
/**
* Returns the root node of the given AST node by following the `$container` references.
*/
function findRootNode(node) {
	while (node.$container) node = node.$container;
	return node;
}
/**
* Returns all AST nodes that are referenced by the given reference or multi-reference.
*/
function getReferenceNodes(reference) {
	if (isReference(reference)) return reference.ref ? [reference.ref] : [];
	else if (isMultiReference(reference)) return reference.items.map((item) => item.ref);
	return [];
}
/**
* Create a stream of all AST nodes that are directly contained in the given node. This includes
* single-valued as well as multi-valued (array) properties.
*/
function streamContents(node, options) {
	if (!node) throw new Error("Node must be an AstNode.");
	const range = options?.range;
	return new StreamImpl(() => ({
		keys: Object.keys(node),
		keyIndex: 0,
		arrayIndex: 0
	}), (state) => {
		while (state.keyIndex < state.keys.length) {
			const property = state.keys[state.keyIndex];
			if (!property.startsWith("$")) {
				const value = node[property];
				if (isAstNode(value)) {
					state.keyIndex++;
					if (isAstNodeInRange(value, range)) return {
						done: false,
						value
					};
				} else if (Array.isArray(value)) {
					while (state.arrayIndex < value.length) {
						const element = value[state.arrayIndex++];
						if (isAstNode(element) && isAstNodeInRange(element, range)) return {
							done: false,
							value: element
						};
					}
					state.arrayIndex = 0;
				}
			}
			state.keyIndex++;
		}
		return DONE_RESULT;
	});
}
/**
* Create a stream of all AST nodes that are directly and indirectly contained in the given root node.
* This does not include the root node itself.
*/
function streamAllContents(root, options) {
	if (!root) throw new Error("Root node must be an AstNode.");
	return new TreeStreamImpl(root, (node) => streamContents(node, options));
}
/**
* Create a stream of all AST nodes that are directly and indirectly contained in the given root node,
* including the root node itself.
*/
function streamAst(root, options) {
	if (!root) throw new Error("Root node must be an AstNode.");
	else if (options?.range && !isAstNodeInRange(root, options.range)) return new TreeStreamImpl(root, () => []);
	return new TreeStreamImpl(root, (node) => streamContents(node, options), { includeRoot: true });
}
function isAstNodeInRange(astNode, range) {
	if (!range) return true;
	const nodeRange = astNode.$cstNode?.range;
	if (!nodeRange) return false;
	return inRange(nodeRange, range);
}
/**
* Create a stream of all cross-references that are held by the given AST node. This includes
* single-valued as well as multi-valued (array) properties.
*/
function streamReferences(node) {
	return new StreamImpl(() => ({
		keys: Object.keys(node),
		keyIndex: 0,
		arrayIndex: 0
	}), (state) => {
		while (state.keyIndex < state.keys.length) {
			const property = state.keys[state.keyIndex];
			if (!property.startsWith("$")) {
				const value = node[property];
				if (isReference(value) || isMultiReference(value)) {
					state.keyIndex++;
					return {
						done: false,
						value: {
							reference: value,
							container: node,
							property
						}
					};
				} else if (Array.isArray(value)) {
					while (state.arrayIndex < value.length) {
						const index = state.arrayIndex++;
						const element = value[index];
						if (isReference(element) || isMultiReference(value)) return {
							done: false,
							value: {
								reference: element,
								container: node,
								property,
								index
							}
						};
					}
					state.arrayIndex = 0;
				}
			}
			state.keyIndex++;
		}
		return DONE_RESULT;
	});
}
/**
* Assigns all mandatory AST properties to the specified node.
*
* @param reflection Reflection object used to gather mandatory properties for the node.
* @param node Specified node is modified in place and properties are directly assigned.
*/
function assignMandatoryProperties(reflection, node) {
	const typeMetaData = reflection.getTypeMetaData(node.$type);
	const genericNode = node;
	for (const property of Object.values(typeMetaData.properties)) if (property.defaultValue !== void 0 && genericNode[property.name] === void 0) genericNode[property.name] = copyDefaultValue(property.defaultValue);
}
function copyDefaultValue(propertyType) {
	if (Array.isArray(propertyType)) return [...propertyType.map(copyDefaultValue)];
	else return propertyType;
}
/******************************************************************************
* This file was generated by langium-cli 4.2.1.
* DO NOT EDIT MANUALLY!
******************************************************************************/
var AbstractElement = {
	$type: "AbstractElement",
	cardinality: "cardinality"
};
function isAbstractElement(item) {
	return reflection.isInstance(item, AbstractElement.$type);
}
var AbstractParserRule = { $type: "AbstractParserRule" };
function isAbstractParserRule(item) {
	return reflection.isInstance(item, AbstractParserRule.$type);
}
var AbstractRule = { $type: "AbstractRule" };
var AbstractType = { $type: "AbstractType" };
var Action = {
	$type: "Action",
	cardinality: "cardinality",
	feature: "feature",
	inferredType: "inferredType",
	operator: "operator",
	type: "type"
};
function isAction(item) {
	return reflection.isInstance(item, Action.$type);
}
var Alternatives = {
	$type: "Alternatives",
	cardinality: "cardinality",
	elements: "elements"
};
function isAlternatives(item) {
	return reflection.isInstance(item, Alternatives.$type);
}
var ArrayLiteral = {
	$type: "ArrayLiteral",
	elements: "elements"
};
var ArrayType = {
	$type: "ArrayType",
	elementType: "elementType"
};
var Assignment = {
	$type: "Assignment",
	cardinality: "cardinality",
	feature: "feature",
	operator: "operator",
	predicate: "predicate",
	terminal: "terminal"
};
function isAssignment(item) {
	return reflection.isInstance(item, Assignment.$type);
}
var BooleanLiteral = {
	$type: "BooleanLiteral",
	true: "true"
};
function isBooleanLiteral(item) {
	return reflection.isInstance(item, BooleanLiteral.$type);
}
var CharacterRange = {
	$type: "CharacterRange",
	cardinality: "cardinality",
	left: "left",
	lookahead: "lookahead",
	parenthesized: "parenthesized",
	right: "right"
};
function isCharacterRange(item) {
	return reflection.isInstance(item, CharacterRange.$type);
}
var Condition = { $type: "Condition" };
var Conjunction = {
	$type: "Conjunction",
	left: "left",
	right: "right"
};
function isConjunction(item) {
	return reflection.isInstance(item, Conjunction.$type);
}
var CrossReference = {
	$type: "CrossReference",
	cardinality: "cardinality",
	deprecatedSyntax: "deprecatedSyntax",
	isMulti: "isMulti",
	terminal: "terminal",
	type: "type"
};
function isCrossReference(item) {
	return reflection.isInstance(item, CrossReference.$type);
}
var Disjunction = {
	$type: "Disjunction",
	left: "left",
	right: "right"
};
function isDisjunction(item) {
	return reflection.isInstance(item, Disjunction.$type);
}
var EndOfFile = {
	$type: "EndOfFile",
	cardinality: "cardinality"
};
function isEndOfFile(item) {
	return reflection.isInstance(item, EndOfFile.$type);
}
var Grammar = {
	$type: "Grammar",
	imports: "imports",
	interfaces: "interfaces",
	isDeclared: "isDeclared",
	name: "name",
	rules: "rules",
	types: "types"
};
var GrammarImport = {
	$type: "GrammarImport",
	path: "path"
};
var Group = {
	$type: "Group",
	cardinality: "cardinality",
	elements: "elements",
	guardCondition: "guardCondition",
	predicate: "predicate"
};
function isGroup(item) {
	return reflection.isInstance(item, Group.$type);
}
var InferredType = {
	$type: "InferredType",
	name: "name"
};
function isInferredType(item) {
	return reflection.isInstance(item, InferredType.$type);
}
var InfixRule = {
	$type: "InfixRule",
	call: "call",
	dataType: "dataType",
	inferredType: "inferredType",
	name: "name",
	operators: "operators",
	parameters: "parameters",
	returnType: "returnType"
};
function isInfixRule(item) {
	return reflection.isInstance(item, InfixRule.$type);
}
var InfixRuleOperatorList = {
	$type: "InfixRuleOperatorList",
	associativity: "associativity",
	operators: "operators"
};
var InfixRuleOperators = {
	$type: "InfixRuleOperators",
	precedences: "precedences"
};
var Interface = {
	$type: "Interface",
	attributes: "attributes",
	name: "name",
	superTypes: "superTypes"
};
function isInterface(item) {
	return reflection.isInstance(item, Interface.$type);
}
var Keyword = {
	$type: "Keyword",
	cardinality: "cardinality",
	predicate: "predicate",
	value: "value"
};
function isKeyword(item) {
	return reflection.isInstance(item, Keyword.$type);
}
var NamedArgument = {
	$type: "NamedArgument",
	calledByName: "calledByName",
	parameter: "parameter",
	value: "value"
};
var NegatedToken = {
	$type: "NegatedToken",
	cardinality: "cardinality",
	lookahead: "lookahead",
	parenthesized: "parenthesized",
	terminal: "terminal"
};
function isNegatedToken(item) {
	return reflection.isInstance(item, NegatedToken.$type);
}
var Negation = {
	$type: "Negation",
	value: "value"
};
function isNegation(item) {
	return reflection.isInstance(item, Negation.$type);
}
var NumberLiteral = {
	$type: "NumberLiteral",
	value: "value"
};
var Parameter = {
	$type: "Parameter",
	name: "name"
};
var ParameterReference = {
	$type: "ParameterReference",
	parameter: "parameter"
};
function isParameterReference(item) {
	return reflection.isInstance(item, ParameterReference.$type);
}
var ParserRule = {
	$type: "ParserRule",
	dataType: "dataType",
	definition: "definition",
	entry: "entry",
	fragment: "fragment",
	inferredType: "inferredType",
	name: "name",
	parameters: "parameters",
	returnType: "returnType"
};
function isParserRule(item) {
	return reflection.isInstance(item, ParserRule.$type);
}
var ReferenceType = {
	$type: "ReferenceType",
	isMulti: "isMulti",
	referenceType: "referenceType"
};
var RegexToken = {
	$type: "RegexToken",
	cardinality: "cardinality",
	lookahead: "lookahead",
	parenthesized: "parenthesized",
	regex: "regex"
};
function isRegexToken(item) {
	return reflection.isInstance(item, RegexToken.$type);
}
var ReturnType = {
	$type: "ReturnType",
	name: "name"
};
function isReturnType(item) {
	return reflection.isInstance(item, ReturnType.$type);
}
var RuleCall = {
	$type: "RuleCall",
	arguments: "arguments",
	cardinality: "cardinality",
	predicate: "predicate",
	rule: "rule"
};
function isRuleCall(item) {
	return reflection.isInstance(item, RuleCall.$type);
}
var SimpleType = {
	$type: "SimpleType",
	primitiveType: "primitiveType",
	stringType: "stringType",
	typeRef: "typeRef"
};
function isSimpleType(item) {
	return reflection.isInstance(item, SimpleType.$type);
}
var StringLiteral = {
	$type: "StringLiteral",
	value: "value"
};
var TerminalAlternatives = {
	$type: "TerminalAlternatives",
	cardinality: "cardinality",
	elements: "elements",
	lookahead: "lookahead",
	parenthesized: "parenthesized"
};
function isTerminalAlternatives(item) {
	return reflection.isInstance(item, TerminalAlternatives.$type);
}
var TerminalElement = {
	$type: "TerminalElement",
	cardinality: "cardinality",
	lookahead: "lookahead",
	parenthesized: "parenthesized"
};
var TerminalGroup = {
	$type: "TerminalGroup",
	cardinality: "cardinality",
	elements: "elements",
	lookahead: "lookahead",
	parenthesized: "parenthesized"
};
function isTerminalGroup(item) {
	return reflection.isInstance(item, TerminalGroup.$type);
}
var TerminalRule = {
	$type: "TerminalRule",
	definition: "definition",
	fragment: "fragment",
	hidden: "hidden",
	name: "name",
	type: "type"
};
function isTerminalRule(item) {
	return reflection.isInstance(item, TerminalRule.$type);
}
var TerminalRuleCall = {
	$type: "TerminalRuleCall",
	cardinality: "cardinality",
	lookahead: "lookahead",
	parenthesized: "parenthesized",
	rule: "rule"
};
function isTerminalRuleCall(item) {
	return reflection.isInstance(item, TerminalRuleCall.$type);
}
var Type = {
	$type: "Type",
	name: "name",
	type: "type"
};
function isType(item) {
	return reflection.isInstance(item, Type.$type);
}
var TypeAttribute = {
	$type: "TypeAttribute",
	defaultValue: "defaultValue",
	isOptional: "isOptional",
	name: "name",
	type: "type"
};
var TypeDefinition = { $type: "TypeDefinition" };
var UnionType = {
	$type: "UnionType",
	types: "types"
};
var UnorderedGroup = {
	$type: "UnorderedGroup",
	cardinality: "cardinality",
	elements: "elements"
};
function isUnorderedGroup(item) {
	return reflection.isInstance(item, UnorderedGroup.$type);
}
var UntilToken = {
	$type: "UntilToken",
	cardinality: "cardinality",
	lookahead: "lookahead",
	parenthesized: "parenthesized",
	terminal: "terminal"
};
function isUntilToken(item) {
	return reflection.isInstance(item, UntilToken.$type);
}
var ValueLiteral = { $type: "ValueLiteral" };
var Wildcard = {
	$type: "Wildcard",
	cardinality: "cardinality",
	lookahead: "lookahead",
	parenthesized: "parenthesized"
};
function isWildcard(item) {
	return reflection.isInstance(item, Wildcard.$type);
}
var LangiumGrammarAstReflection = class extends AbstractAstReflection {
	constructor() {
		super(...arguments);
		this.types = {
			AbstractElement: {
				name: AbstractElement.$type,
				properties: { cardinality: { name: AbstractElement.cardinality } },
				superTypes: []
			},
			AbstractParserRule: {
				name: AbstractParserRule.$type,
				properties: {},
				superTypes: [AbstractRule.$type, AbstractType.$type]
			},
			AbstractRule: {
				name: AbstractRule.$type,
				properties: {},
				superTypes: []
			},
			AbstractType: {
				name: AbstractType.$type,
				properties: {},
				superTypes: []
			},
			Action: {
				name: Action.$type,
				properties: {
					cardinality: { name: Action.cardinality },
					feature: { name: Action.feature },
					inferredType: { name: Action.inferredType },
					operator: { name: Action.operator },
					type: {
						name: Action.type,
						referenceType: AbstractType.$type
					}
				},
				superTypes: [AbstractElement.$type]
			},
			Alternatives: {
				name: Alternatives.$type,
				properties: {
					cardinality: { name: Alternatives.cardinality },
					elements: {
						name: Alternatives.elements,
						defaultValue: []
					}
				},
				superTypes: [AbstractElement.$type]
			},
			ArrayLiteral: {
				name: ArrayLiteral.$type,
				properties: { elements: {
					name: ArrayLiteral.elements,
					defaultValue: []
				} },
				superTypes: [ValueLiteral.$type]
			},
			ArrayType: {
				name: ArrayType.$type,
				properties: { elementType: { name: ArrayType.elementType } },
				superTypes: [TypeDefinition.$type]
			},
			Assignment: {
				name: Assignment.$type,
				properties: {
					cardinality: { name: Assignment.cardinality },
					feature: { name: Assignment.feature },
					operator: { name: Assignment.operator },
					predicate: { name: Assignment.predicate },
					terminal: { name: Assignment.terminal }
				},
				superTypes: [AbstractElement.$type]
			},
			BooleanLiteral: {
				name: BooleanLiteral.$type,
				properties: { true: {
					name: BooleanLiteral.true,
					defaultValue: false
				} },
				superTypes: [Condition.$type, ValueLiteral.$type]
			},
			CharacterRange: {
				name: CharacterRange.$type,
				properties: {
					cardinality: { name: CharacterRange.cardinality },
					left: { name: CharacterRange.left },
					lookahead: { name: CharacterRange.lookahead },
					parenthesized: {
						name: CharacterRange.parenthesized,
						defaultValue: false
					},
					right: { name: CharacterRange.right }
				},
				superTypes: [TerminalElement.$type]
			},
			Condition: {
				name: Condition.$type,
				properties: {},
				superTypes: []
			},
			Conjunction: {
				name: Conjunction.$type,
				properties: {
					left: { name: Conjunction.left },
					right: { name: Conjunction.right }
				},
				superTypes: [Condition.$type]
			},
			CrossReference: {
				name: CrossReference.$type,
				properties: {
					cardinality: { name: CrossReference.cardinality },
					deprecatedSyntax: {
						name: CrossReference.deprecatedSyntax,
						defaultValue: false
					},
					isMulti: {
						name: CrossReference.isMulti,
						defaultValue: false
					},
					terminal: { name: CrossReference.terminal },
					type: {
						name: CrossReference.type,
						referenceType: AbstractType.$type
					}
				},
				superTypes: [AbstractElement.$type]
			},
			Disjunction: {
				name: Disjunction.$type,
				properties: {
					left: { name: Disjunction.left },
					right: { name: Disjunction.right }
				},
				superTypes: [Condition.$type]
			},
			EndOfFile: {
				name: EndOfFile.$type,
				properties: { cardinality: { name: EndOfFile.cardinality } },
				superTypes: [AbstractElement.$type]
			},
			Grammar: {
				name: Grammar.$type,
				properties: {
					imports: {
						name: Grammar.imports,
						defaultValue: []
					},
					interfaces: {
						name: Grammar.interfaces,
						defaultValue: []
					},
					isDeclared: {
						name: Grammar.isDeclared,
						defaultValue: false
					},
					name: { name: Grammar.name },
					rules: {
						name: Grammar.rules,
						defaultValue: []
					},
					types: {
						name: Grammar.types,
						defaultValue: []
					}
				},
				superTypes: []
			},
			GrammarImport: {
				name: GrammarImport.$type,
				properties: { path: { name: GrammarImport.path } },
				superTypes: []
			},
			Group: {
				name: Group.$type,
				properties: {
					cardinality: { name: Group.cardinality },
					elements: {
						name: Group.elements,
						defaultValue: []
					},
					guardCondition: { name: Group.guardCondition },
					predicate: { name: Group.predicate }
				},
				superTypes: [AbstractElement.$type]
			},
			InferredType: {
				name: InferredType.$type,
				properties: { name: { name: InferredType.name } },
				superTypes: [AbstractType.$type]
			},
			InfixRule: {
				name: InfixRule.$type,
				properties: {
					call: { name: InfixRule.call },
					dataType: { name: InfixRule.dataType },
					inferredType: { name: InfixRule.inferredType },
					name: { name: InfixRule.name },
					operators: { name: InfixRule.operators },
					parameters: {
						name: InfixRule.parameters,
						defaultValue: []
					},
					returnType: {
						name: InfixRule.returnType,
						referenceType: AbstractType.$type
					}
				},
				superTypes: [AbstractParserRule.$type]
			},
			InfixRuleOperatorList: {
				name: InfixRuleOperatorList.$type,
				properties: {
					associativity: { name: InfixRuleOperatorList.associativity },
					operators: {
						name: InfixRuleOperatorList.operators,
						defaultValue: []
					}
				},
				superTypes: []
			},
			InfixRuleOperators: {
				name: InfixRuleOperators.$type,
				properties: { precedences: {
					name: InfixRuleOperators.precedences,
					defaultValue: []
				} },
				superTypes: []
			},
			Interface: {
				name: Interface.$type,
				properties: {
					attributes: {
						name: Interface.attributes,
						defaultValue: []
					},
					name: { name: Interface.name },
					superTypes: {
						name: Interface.superTypes,
						defaultValue: [],
						referenceType: AbstractType.$type
					}
				},
				superTypes: [AbstractType.$type]
			},
			Keyword: {
				name: Keyword.$type,
				properties: {
					cardinality: { name: Keyword.cardinality },
					predicate: { name: Keyword.predicate },
					value: { name: Keyword.value }
				},
				superTypes: [AbstractElement.$type]
			},
			NamedArgument: {
				name: NamedArgument.$type,
				properties: {
					calledByName: {
						name: NamedArgument.calledByName,
						defaultValue: false
					},
					parameter: {
						name: NamedArgument.parameter,
						referenceType: Parameter.$type
					},
					value: { name: NamedArgument.value }
				},
				superTypes: []
			},
			NegatedToken: {
				name: NegatedToken.$type,
				properties: {
					cardinality: { name: NegatedToken.cardinality },
					lookahead: { name: NegatedToken.lookahead },
					parenthesized: {
						name: NegatedToken.parenthesized,
						defaultValue: false
					},
					terminal: { name: NegatedToken.terminal }
				},
				superTypes: [TerminalElement.$type]
			},
			Negation: {
				name: Negation.$type,
				properties: { value: { name: Negation.value } },
				superTypes: [Condition.$type]
			},
			NumberLiteral: {
				name: NumberLiteral.$type,
				properties: { value: { name: NumberLiteral.value } },
				superTypes: [ValueLiteral.$type]
			},
			Parameter: {
				name: Parameter.$type,
				properties: { name: { name: Parameter.name } },
				superTypes: []
			},
			ParameterReference: {
				name: ParameterReference.$type,
				properties: { parameter: {
					name: ParameterReference.parameter,
					referenceType: Parameter.$type
				} },
				superTypes: [Condition.$type]
			},
			ParserRule: {
				name: ParserRule.$type,
				properties: {
					dataType: { name: ParserRule.dataType },
					definition: { name: ParserRule.definition },
					entry: {
						name: ParserRule.entry,
						defaultValue: false
					},
					fragment: {
						name: ParserRule.fragment,
						defaultValue: false
					},
					inferredType: { name: ParserRule.inferredType },
					name: { name: ParserRule.name },
					parameters: {
						name: ParserRule.parameters,
						defaultValue: []
					},
					returnType: {
						name: ParserRule.returnType,
						referenceType: AbstractType.$type
					}
				},
				superTypes: [AbstractParserRule.$type]
			},
			ReferenceType: {
				name: ReferenceType.$type,
				properties: {
					isMulti: {
						name: ReferenceType.isMulti,
						defaultValue: false
					},
					referenceType: { name: ReferenceType.referenceType }
				},
				superTypes: [TypeDefinition.$type]
			},
			RegexToken: {
				name: RegexToken.$type,
				properties: {
					cardinality: { name: RegexToken.cardinality },
					lookahead: { name: RegexToken.lookahead },
					parenthesized: {
						name: RegexToken.parenthesized,
						defaultValue: false
					},
					regex: { name: RegexToken.regex }
				},
				superTypes: [TerminalElement.$type]
			},
			ReturnType: {
				name: ReturnType.$type,
				properties: { name: { name: ReturnType.name } },
				superTypes: []
			},
			RuleCall: {
				name: RuleCall.$type,
				properties: {
					arguments: {
						name: RuleCall.arguments,
						defaultValue: []
					},
					cardinality: { name: RuleCall.cardinality },
					predicate: { name: RuleCall.predicate },
					rule: {
						name: RuleCall.rule,
						referenceType: AbstractRule.$type
					}
				},
				superTypes: [AbstractElement.$type]
			},
			SimpleType: {
				name: SimpleType.$type,
				properties: {
					primitiveType: { name: SimpleType.primitiveType },
					stringType: { name: SimpleType.stringType },
					typeRef: {
						name: SimpleType.typeRef,
						referenceType: AbstractType.$type
					}
				},
				superTypes: [TypeDefinition.$type]
			},
			StringLiteral: {
				name: StringLiteral.$type,
				properties: { value: { name: StringLiteral.value } },
				superTypes: [ValueLiteral.$type]
			},
			TerminalAlternatives: {
				name: TerminalAlternatives.$type,
				properties: {
					cardinality: { name: TerminalAlternatives.cardinality },
					elements: {
						name: TerminalAlternatives.elements,
						defaultValue: []
					},
					lookahead: { name: TerminalAlternatives.lookahead },
					parenthesized: {
						name: TerminalAlternatives.parenthesized,
						defaultValue: false
					}
				},
				superTypes: [TerminalElement.$type]
			},
			TerminalElement: {
				name: TerminalElement.$type,
				properties: {
					cardinality: { name: TerminalElement.cardinality },
					lookahead: { name: TerminalElement.lookahead },
					parenthesized: {
						name: TerminalElement.parenthesized,
						defaultValue: false
					}
				},
				superTypes: [AbstractElement.$type]
			},
			TerminalGroup: {
				name: TerminalGroup.$type,
				properties: {
					cardinality: { name: TerminalGroup.cardinality },
					elements: {
						name: TerminalGroup.elements,
						defaultValue: []
					},
					lookahead: { name: TerminalGroup.lookahead },
					parenthesized: {
						name: TerminalGroup.parenthesized,
						defaultValue: false
					}
				},
				superTypes: [TerminalElement.$type]
			},
			TerminalRule: {
				name: TerminalRule.$type,
				properties: {
					definition: { name: TerminalRule.definition },
					fragment: {
						name: TerminalRule.fragment,
						defaultValue: false
					},
					hidden: {
						name: TerminalRule.hidden,
						defaultValue: false
					},
					name: { name: TerminalRule.name },
					type: { name: TerminalRule.type }
				},
				superTypes: [AbstractRule.$type]
			},
			TerminalRuleCall: {
				name: TerminalRuleCall.$type,
				properties: {
					cardinality: { name: TerminalRuleCall.cardinality },
					lookahead: { name: TerminalRuleCall.lookahead },
					parenthesized: {
						name: TerminalRuleCall.parenthesized,
						defaultValue: false
					},
					rule: {
						name: TerminalRuleCall.rule,
						referenceType: TerminalRule.$type
					}
				},
				superTypes: [TerminalElement.$type]
			},
			Type: {
				name: Type.$type,
				properties: {
					name: { name: Type.name },
					type: { name: Type.type }
				},
				superTypes: [AbstractType.$type]
			},
			TypeAttribute: {
				name: TypeAttribute.$type,
				properties: {
					defaultValue: { name: TypeAttribute.defaultValue },
					isOptional: {
						name: TypeAttribute.isOptional,
						defaultValue: false
					},
					name: { name: TypeAttribute.name },
					type: { name: TypeAttribute.type }
				},
				superTypes: []
			},
			TypeDefinition: {
				name: TypeDefinition.$type,
				properties: {},
				superTypes: []
			},
			UnionType: {
				name: UnionType.$type,
				properties: { types: {
					name: UnionType.types,
					defaultValue: []
				} },
				superTypes: [TypeDefinition.$type]
			},
			UnorderedGroup: {
				name: UnorderedGroup.$type,
				properties: {
					cardinality: { name: UnorderedGroup.cardinality },
					elements: {
						name: UnorderedGroup.elements,
						defaultValue: []
					}
				},
				superTypes: [AbstractElement.$type]
			},
			UntilToken: {
				name: UntilToken.$type,
				properties: {
					cardinality: { name: UntilToken.cardinality },
					lookahead: { name: UntilToken.lookahead },
					parenthesized: {
						name: UntilToken.parenthesized,
						defaultValue: false
					},
					terminal: { name: UntilToken.terminal }
				},
				superTypes: [TerminalElement.$type]
			},
			ValueLiteral: {
				name: ValueLiteral.$type,
				properties: {},
				superTypes: []
			},
			Wildcard: {
				name: Wildcard.$type,
				properties: {
					cardinality: { name: Wildcard.cardinality },
					lookahead: { name: Wildcard.lookahead },
					parenthesized: {
						name: Wildcard.parenthesized,
						defaultValue: false
					}
				},
				superTypes: [TerminalElement.$type]
			}
		};
	}
};
var reflection = new LangiumGrammarAstReflection();
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* Create a stream of all CST nodes that are directly and indirectly contained in the given root node,
* including the root node itself.
*/
function streamCst(node) {
	return new TreeStreamImpl(node, (element) => {
		if (isCompositeCstNode(element)) return element.content;
		else return [];
	}, { includeRoot: true });
}
/**
* Determines whether the specified cst node is a child of the specified parent node.
*/
function isChildNode(child, parent) {
	while (child.container) {
		child = child.container;
		if (child === parent) return true;
	}
	return false;
}
function tokenToRange(token) {
	return {
		start: {
			character: token.startColumn - 1,
			line: token.startLine - 1
		},
		end: {
			character: token.endColumn,
			line: token.endLine - 1
		}
	};
}
function toDocumentSegment(node) {
	if (!node) return;
	const { offset, end, range } = node;
	return {
		range,
		offset,
		end,
		length: end - offset
	};
}
var RangeComparison;
(function(RangeComparison) {
	RangeComparison[RangeComparison["Before"] = 0] = "Before";
	RangeComparison[RangeComparison["After"] = 1] = "After";
	RangeComparison[RangeComparison["OverlapFront"] = 2] = "OverlapFront";
	RangeComparison[RangeComparison["OverlapBack"] = 3] = "OverlapBack";
	RangeComparison[RangeComparison["Inside"] = 4] = "Inside";
	RangeComparison[RangeComparison["Outside"] = 5] = "Outside";
})(RangeComparison || (RangeComparison = {}));
function compareRange(range, to) {
	if (range.end.line < to.start.line || range.end.line === to.start.line && range.end.character <= to.start.character) return RangeComparison.Before;
	else if (range.start.line > to.end.line || range.start.line === to.end.line && range.start.character >= to.end.character) return RangeComparison.After;
	const startInside = range.start.line > to.start.line || range.start.line === to.start.line && range.start.character >= to.start.character;
	const endInside = range.end.line < to.end.line || range.end.line === to.end.line && range.end.character <= to.end.character;
	if (startInside && endInside) return RangeComparison.Inside;
	else if (startInside) return RangeComparison.OverlapBack;
	else if (endInside) return RangeComparison.OverlapFront;
	else return RangeComparison.Outside;
}
function inRange(range, to) {
	return compareRange(range, to) > RangeComparison.After;
}
var DefaultNameRegexp = /^[\w\p{L}]$/u;
function findCommentNode(cstNode, commentNames) {
	if (cstNode) {
		const previous = getPreviousNode(cstNode, true);
		if (previous && isCommentNode(previous, commentNames)) return previous;
		if (isRootCstNode(cstNode)) {
			const endIndex = cstNode.content.findIndex((e) => !e.hidden);
			for (let i = endIndex - 1; i >= 0; i--) {
				const child = cstNode.content[i];
				if (isCommentNode(child, commentNames)) return child;
			}
		}
	}
}
function isCommentNode(cstNode, commentNames) {
	return isLeafCstNode(cstNode) && commentNames.includes(cstNode.tokenType.name);
}
function getPreviousNode(node, hidden = true) {
	while (node.container) {
		const parent = node.container;
		let index = parent.content.indexOf(node);
		while (index > 0) {
			index--;
			const previous = parent.content[index];
			if (hidden || !previous.hidden) return previous;
		}
		node = parent;
	}
}
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var ErrorWithLocation = class extends Error {
	constructor(node, message) {
		super(node ? `${message} at ${node.range.start.line}:${node.range.start.character}` : message);
	}
};
function assertUnreachable(_, message = "Error: Got unexpected value.") {
	throw new Error(message);
}
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var NEWLINE_REGEXP = /\r?\n/gm;
var regexpParser = new RegExpParser();
/**
* This class is in charge of heuristically identifying start/end tokens of terminals.
*
* The way this works is by doing the following:
* 1. Traverse the regular expression in the "start state"
* 2. Add any encountered sets/single characters to the "start regexp"
* 3. Once we encounter any variable-length content (i.e. with quantifiers such as +/?/*), we enter the "end state"
* 4. In the end state, any sets/single characters are added to an "end stack".
* 5. If we re-encounter any variable-length content we reset the end stack
* 6. We continue visiting the regex until the end, reseting the end stack and rebuilding it as necessary
*
* After traversing a regular expression the `startRegexp/endRegexp` properties allow access to the stored start/end of the terminal
*/
var TerminalRegExpVisitor = class extends BaseRegExpVisitor {
	constructor() {
		super(...arguments);
		this.isStarting = true;
		this.endRegexpStack = [];
		this.multiline = false;
	}
	get endRegex() {
		return this.endRegexpStack.join("");
	}
	reset(regex) {
		this.multiline = false;
		this.regex = regex;
		this.startRegexp = "";
		this.isStarting = true;
		this.endRegexpStack = [];
	}
	visitGroup(node) {
		if (node.quantifier) {
			this.isStarting = false;
			this.endRegexpStack = [];
		}
	}
	visitCharacter(node) {
		const char = String.fromCharCode(node.value);
		if (!this.multiline && char === "\n") this.multiline = true;
		if (node.quantifier) {
			this.isStarting = false;
			this.endRegexpStack = [];
		} else {
			const escapedChar = escapeRegExp(char);
			this.endRegexpStack.push(escapedChar);
			if (this.isStarting) this.startRegexp += escapedChar;
		}
	}
	visitSet(node) {
		if (!this.multiline) {
			const set = this.regex.substring(node.loc.begin, node.loc.end);
			const regex = new RegExp(set);
			this.multiline = Boolean("\n".match(regex));
		}
		if (node.quantifier) {
			this.isStarting = false;
			this.endRegexpStack = [];
		} else {
			const set = this.regex.substring(node.loc.begin, node.loc.end);
			this.endRegexpStack.push(set);
			if (this.isStarting) this.startRegexp += set;
		}
	}
	visitChildren(node) {
		if (node.type === "Group") {
			if (node.quantifier) return;
		}
		super.visitChildren(node);
	}
};
var visitor = new TerminalRegExpVisitor();
function isMultilineComment(regexp) {
	try {
		if (typeof regexp === "string") regexp = new RegExp(regexp);
		regexp = regexp.toString();
		visitor.reset(regexp);
		visitor.visit(regexpParser.pattern(regexp));
		return visitor.multiline;
	} catch {
		return false;
	}
}
/**
* A set of all characters that are considered whitespace by the '\s' RegExp character class.
* Taken from [MDN](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_expressions/Character_classes).
*/
var whitespaceCharacters = "\f\n\r	\v \xA0            \u2028\u2029  　﻿".split("");
function isWhitespace(value) {
	const regexp = typeof value === "string" ? new RegExp(value) : value;
	return whitespaceCharacters.some((ws) => regexp.test(ws));
}
function escapeRegExp(value) {
	return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
/**
* Determines whether the given input has a partial match with the specified regex.
* @param regex The regex to partially match against
* @param input The input string
* @returns Whether any match exists.
*/
function partialMatches(regex, input) {
	const partial = partialRegExp(regex);
	const match = input.match(partial);
	return !!match && match[0].length > 0;
}
/**
* Builds a partial regex from the input regex. A partial regex is able to match incomplete input strings. E.g.
* a partial regex constructed from `/ab/` is able to match the string `a` without needing a following `b` character. However it won't match `b` alone.
* @param regex The input regex to be converted.
* @returns A partial regex constructed from the input regex.
*/
function partialRegExp(regex) {
	if (typeof regex === "string") regex = new RegExp(regex);
	const re = regex, source = regex.source;
	let i = 0;
	function process() {
		let result = "", tmp;
		function appendRaw(nbChars) {
			result += source.substr(i, nbChars);
			i += nbChars;
		}
		function appendOptional(nbChars) {
			result += "(?:" + source.substr(i, nbChars) + "|$)";
			i += nbChars;
		}
		while (i < source.length) switch (source[i]) {
			case "\\":
				switch (source[i + 1]) {
					case "c":
						appendOptional(3);
						break;
					case "x":
						appendOptional(4);
						break;
					case "u":
						if (re.unicode) if (source[i + 2] === "{") appendOptional(source.indexOf("}", i) - i + 1);
						else appendOptional(6);
						else appendOptional(2);
						break;
					case "p":
					case "P":
						if (re.unicode) appendOptional(source.indexOf("}", i) - i + 1);
						else appendOptional(2);
						break;
					case "k":
						appendOptional(source.indexOf(">", i) - i + 1);
						break;
					default:
						appendOptional(2);
						break;
				}
				break;
			case "[":
				tmp = /\[(?:\\.|.)*?\]/g;
				tmp.lastIndex = i;
				tmp = tmp.exec(source) || [];
				appendOptional(tmp[0].length);
				break;
			case "|":
			case "^":
			case "$":
			case "*":
			case "+":
			case "?":
				appendRaw(1);
				break;
			case "{":
				tmp = /\{\d+,?\d*\}/g;
				tmp.lastIndex = i;
				tmp = tmp.exec(source);
				if (tmp) appendRaw(tmp[0].length);
				else appendOptional(1);
				break;
			case "(":
				if (source[i + 1] === "?") switch (source[i + 2]) {
					case ":":
						result += "(?:";
						i += 3;
						result += process() + "|$)";
						break;
					case "=":
						result += "(?=";
						i += 3;
						result += process() + ")";
						break;
					case "!":
						tmp = i;
						i += 3;
						process();
						result += source.substr(tmp, i - tmp);
						break;
					case "<":
						switch (source[i + 3]) {
							case "=":
							case "!":
								tmp = i;
								i += 4;
								process();
								result += source.substr(tmp, i - tmp);
								break;
							default:
								appendRaw(source.indexOf(">", i) - i + 1);
								result += process() + "|$)";
								break;
						}
						break;
				}
				else {
					appendRaw(1);
					result += process() + "|$)";
				}
				break;
			case ")":
				++i;
				return result;
			default:
				appendOptional(1);
				break;
		}
		return result;
	}
	return new RegExp(process(), regex.flags);
}
/******************************************************************************
* Copyright 2021-2022 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* Returns the entry rule of the given grammar, if any. If the grammar file does not contain an entry rule,
* the result is `undefined`.
*/
function getEntryRule(grammar) {
	return grammar.rules.find((e) => isParserRule(e) && e.entry);
}
/**
* Returns all hidden terminal rules of the given grammar, if any.
*/
function getHiddenRules(grammar) {
	return grammar.rules.filter((e) => isTerminalRule(e) && e.hidden);
}
/**
* Returns all rules that can be reached from the topmost rules of the specified grammar (entry and hidden terminal rules).
*
* @param grammar The grammar that contains all rules
* @param allTerminals Whether or not to include terminals that are referenced only by other terminals
* @returns A list of referenced parser and terminal rules. If the grammar contains no entry rule,
*      this function returns all rules of the specified grammar.
*/
function getAllReachableRules(grammar, allTerminals) {
	const ruleNames = /* @__PURE__ */ new Set();
	const entryRule = getEntryRule(grammar);
	if (!entryRule) return new Set(grammar.rules);
	const topMostRules = [entryRule].concat(getHiddenRules(grammar));
	for (const rule of topMostRules) ruleDfs(rule, ruleNames, allTerminals);
	const rules = /* @__PURE__ */ new Set();
	for (const rule of grammar.rules) if (ruleNames.has(rule.name) || isTerminalRule(rule) && rule.hidden) rules.add(rule);
	return rules;
}
function ruleDfs(rule, visitedSet, allTerminals) {
	visitedSet.add(rule.name);
	streamAllContents(rule).forEach((node) => {
		if (isRuleCall(node) || allTerminals && isTerminalRuleCall(node)) {
			const refRule = node.rule.ref;
			if (refRule && !visitedSet.has(refRule.name)) ruleDfs(refRule, visitedSet, allTerminals);
		}
	});
}
/**
* Determines the grammar expression used to parse a cross-reference (usually a reference to a terminal rule).
* A cross-reference can declare this expression explicitly in the form `[Type : Terminal]`, but if `Terminal`
* is omitted, this function attempts to infer it from the name of the referenced `Type` (using `findNameAssignment`).
*
* Returns the grammar expression used to parse the given cross-reference, or `undefined` if it is not declared
* and cannot be inferred.
*/
function getCrossReferenceTerminal(crossRef) {
	if (crossRef.terminal) return crossRef.terminal;
	else if (crossRef.type.ref) return findNameAssignment(crossRef.type.ref)?.terminal;
}
/**
* Determines whether the given terminal rule represents a comment. This is true if the rule is marked
* as `hidden` and it does not match white space. This means every hidden token (i.e. excluded from the AST)
* that contains visible characters is considered a comment.
*/
function isCommentTerminal(terminalRule) {
	return terminalRule.hidden && !isWhitespace(terminalRegex(terminalRule));
}
/**
* Find all CST nodes within the given node that contribute to the specified property.
*
* @param node A CST node in which to look for property assignments. If this is undefined, the result is an empty array.
* @param property A property name of the constructed AST node. If this is undefined, the result is an empty array.
*/
function findNodesForProperty(node, property) {
	if (!node || !property) return [];
	return findNodesForPropertyInternal(node, property, node.astNode, true);
}
/**
* Find a single CST node within the given node that contributes to the specified property.
*
* @param node A CST node in which to look for property assignments. If this is undefined, the result is `undefined`.
* @param property A property name of the constructed AST node. If this is undefined, the result is `undefined`.
* @param index If no index is specified or the index is less than zero, the first found node is returned. If the
*        specified index exceeds the number of assignments to the property, the last found node is returned. Otherwise,
*        the node with the specified index is returned.
*/
function findNodeForProperty(node, property, index) {
	if (!node || !property) return;
	const nodes = findNodesForPropertyInternal(node, property, node.astNode, true);
	if (nodes.length === 0) return;
	if (index !== void 0) index = Math.max(0, Math.min(index, nodes.length - 1));
	else index = 0;
	return nodes[index];
}
function findNodesForPropertyInternal(node, property, element, first) {
	if (!first) {
		const nodeFeature = getContainerOfType(node.grammarSource, isAssignment);
		if (nodeFeature && nodeFeature.feature === property) return [node];
	}
	if (isCompositeCstNode(node) && node.astNode === element) return node.content.flatMap((e) => findNodesForPropertyInternal(e, property, element, false));
	return [];
}
/**
* Find a single CST node within the given node that corresponds to the specified keyword.
*
* @param node A CST node in which to look for keywords. If this is undefined, the result is `undefined`.
* @param keyword A keyword as specified in the grammar.
* @param index If no index is specified or the index is less than zero, the first found node is returned. If the
*        specified index exceeds the number of keyword occurrences, the last found node is returned. Otherwise,
*        the node with the specified index is returned.
*/
function findNodeForKeyword(node, keyword, index) {
	if (!node) return;
	const nodes = findNodesForKeywordInternal(node, keyword, node?.astNode);
	if (nodes.length === 0) return;
	if (index !== void 0) index = Math.max(0, Math.min(index, nodes.length - 1));
	else index = 0;
	return nodes[index];
}
function findNodesForKeywordInternal(node, keyword, element) {
	if (node.astNode !== element) return [];
	if (isKeyword(node.grammarSource) && node.grammarSource.value === keyword) return [node];
	const treeIterator = streamCst(node).iterator();
	let result;
	const keywordNodes = [];
	do {
		result = treeIterator.next();
		if (!result.done) {
			const childNode = result.value;
			if (childNode.astNode === element) {
				if (isKeyword(childNode.grammarSource) && childNode.grammarSource.value === keyword) keywordNodes.push(childNode);
			} else treeIterator.prune();
		}
	} while (!result.done);
	return keywordNodes;
}
/**
* If the given CST node was parsed in the context of a property assignment, the respective `Assignment` grammar
* node is returned. If no assignment is found, the result is `undefined`.
*
* @param cstNode A CST node for which to find a property assignment.
*/
function findAssignment(cstNode) {
	const astNode = cstNode.astNode;
	while (astNode === cstNode.container?.astNode) {
		const assignment = getContainerOfType(cstNode.grammarSource, isAssignment);
		if (assignment) return assignment;
		cstNode = cstNode.container;
	}
}
/**
* Find an assignment to the `name` property for the given grammar type. This requires the `type` to be inferred
* from a parser rule, and that rule must contain an assignment to the `name` property. In all other cases,
* this function returns `undefined`.
*/
function findNameAssignment(type) {
	let startNode = type;
	if (isInferredType(startNode)) if (isAction(startNode.$container)) startNode = startNode.$container.$container;
	else if (isAbstractParserRule(startNode.$container)) startNode = startNode.$container;
	else assertUnreachable(startNode.$container);
	return findNameAssignmentInternal(type, startNode, /* @__PURE__ */ new Map());
}
function findNameAssignmentInternal(type, startNode, cache) {
	function go(node, refType) {
		let childAssignment = void 0;
		if (!getContainerOfType(node, isAssignment)) childAssignment = findNameAssignmentInternal(refType, refType, cache);
		cache.set(type, childAssignment);
		return childAssignment;
	}
	if (cache.has(type)) return cache.get(type);
	cache.set(type, void 0);
	for (const node of streamAllContents(startNode)) if (isAssignment(node) && node.feature.toLowerCase() === "name") {
		cache.set(type, node);
		return node;
	} else if (isRuleCall(node) && isParserRule(node.rule.ref)) return go(node, node.rule.ref);
	else if (isSimpleType(node) && node.typeRef?.ref) return go(node, node.typeRef.ref);
}
/**
* Determines whether the given parser rule is a _data type rule_, meaning that it has a
* primitive return type like `number`, `boolean`, etc.
*/
function isDataTypeRule(rule) {
	return isDataTypeRuleInternal(rule, /* @__PURE__ */ new Set());
}
function isDataTypeRuleInternal(rule, visited) {
	if (visited.has(rule)) return true;
	else visited.add(rule);
	for (const node of streamAllContents(rule)) if (isRuleCall(node)) {
		if (!node.rule.ref) return false;
		if (isParserRule(node.rule.ref) && !isDataTypeRuleInternal(node.rule.ref, visited)) return false;
		if (isInfixRule(node.rule.ref)) return false;
	} else if (isAssignment(node)) return false;
	else if (isAction(node)) return false;
	return Boolean(rule.definition);
}
function getExplicitRuleType(rule) {
	if (isTerminalRule(rule)) return;
	if (rule.inferredType) return rule.inferredType.name;
	else if (rule.dataType) return rule.dataType;
	else if (rule.returnType) {
		const refType = rule.returnType.ref;
		if (refType) return refType.name;
	}
}
function getTypeName(type) {
	if (isAbstractParserRule(type)) return isParserRule(type) && isDataTypeRule(type) ? type.name : getExplicitRuleType(type) ?? type.name;
	else if (isInterface(type) || isType(type) || isReturnType(type)) return type.name;
	else if (isAction(type)) {
		const actionType = getActionType(type);
		if (actionType) return actionType;
	} else if (isInferredType(type)) return type.name;
	throw new Error("Cannot get name of Unknown Type");
}
function getActionType(action) {
	if (action.inferredType) return action.inferredType.name;
	else if (action.type?.ref) return getTypeName(action.type.ref);
}
/**
* This function is used at runtime to get the actual type of the values produced by the given rule at runtime.
* For data type rules, the name of the declared return type of the rule is returned (if any),
* e.g. "INT_value returns number: MY_INT;" returns "number".
* @param rule the given rule
* @returns the name of the type of the produced values of the rule at runtime
*/
function getRuleType(rule) {
	if (isTerminalRule(rule)) return rule.type?.name ?? "string";
	else return getExplicitRuleType(rule) ?? rule.name;
}
function terminalRegex(terminalRule) {
	const flags = {
		s: false,
		i: false,
		u: false
	};
	const source = abstractElementToRegex(terminalRule.definition, flags);
	const flagText = Object.entries(flags).filter(([, value]) => value).map(([name]) => name).join("");
	return new RegExp(source, flagText);
}
var WILDCARD = /[\s\S]/.source;
function abstractElementToRegex(element, flags) {
	if (isTerminalAlternatives(element)) return terminalAlternativesToRegex(element);
	else if (isTerminalGroup(element)) return terminalGroupToRegex(element);
	else if (isCharacterRange(element)) return characterRangeToRegex(element);
	else if (isTerminalRuleCall(element)) {
		const rule = element.rule.ref;
		if (!rule) throw new Error("Missing rule reference.");
		return withCardinality(abstractElementToRegex(rule.definition), {
			cardinality: element.cardinality,
			lookahead: element.lookahead,
			parenthesized: element.parenthesized
		});
	} else if (isNegatedToken(element)) return negateTokenToRegex(element);
	else if (isUntilToken(element)) return untilTokenToRegex(element);
	else if (isRegexToken(element)) {
		const lastSlash = element.regex.lastIndexOf("/");
		const source = element.regex.substring(1, lastSlash);
		const regexFlags = element.regex.substring(lastSlash + 1);
		if (flags) {
			flags.i = regexFlags.includes("i");
			flags.s = regexFlags.includes("s");
			flags.u = regexFlags.includes("u");
		}
		return withCardinality(source, {
			cardinality: element.cardinality,
			lookahead: element.lookahead,
			parenthesized: element.parenthesized,
			wrap: false
		});
	} else if (isWildcard(element)) return withCardinality(WILDCARD, {
		cardinality: element.cardinality,
		lookahead: element.lookahead,
		parenthesized: element.parenthesized
	});
	else throw new Error(`Invalid terminal element: ${element?.$type}, ${element?.$cstNode?.text}`);
}
function terminalAlternativesToRegex(alternatives) {
	return withCardinality(alternatives.elements.map((e) => abstractElementToRegex(e)).join("|"), {
		cardinality: alternatives.cardinality,
		lookahead: alternatives.lookahead,
		parenthesized: alternatives.parenthesized,
		wrap: false
	});
}
function terminalGroupToRegex(group) {
	return withCardinality(group.elements.map((e) => abstractElementToRegex(e)).join(""), {
		cardinality: group.cardinality,
		lookahead: group.lookahead,
		parenthesized: group.parenthesized,
		wrap: false
	});
}
function untilTokenToRegex(until) {
	return withCardinality(`${WILDCARD}*?${abstractElementToRegex(until.terminal)}`, {
		cardinality: until.cardinality,
		lookahead: until.lookahead,
		parenthesized: until.parenthesized
	});
}
function negateTokenToRegex(negate) {
	return withCardinality(`(?!${abstractElementToRegex(negate.terminal)})${WILDCARD}*?`, {
		cardinality: negate.cardinality,
		lookahead: negate.lookahead,
		parenthesized: negate.parenthesized
	});
}
function characterRangeToRegex(range) {
	if (range.right) return withCardinality(`[${keywordToRegex(range.left)}-${keywordToRegex(range.right)}]`, {
		cardinality: range.cardinality,
		lookahead: range.lookahead,
		parenthesized: range.parenthesized,
		wrap: false
	});
	return withCardinality(keywordToRegex(range.left), {
		cardinality: range.cardinality,
		lookahead: range.lookahead,
		parenthesized: range.parenthesized,
		wrap: false
	});
}
function keywordToRegex(keyword) {
	return escapeRegExp(keyword.value);
}
function withCardinality(regex, options) {
	if (options.parenthesized || options.lookahead || options.wrap !== false) regex = `(${options.lookahead ?? (options.parenthesized ? "" : "?:")}${regex})`;
	if (options.cardinality) return `${regex}${options.cardinality}`;
	return regex;
}
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* Create the default grammar configuration (used by `createDefaultModule`). This can be overridden in a
* language-specific module.
*/
function createGrammarConfig(services) {
	const rules = [];
	const grammar = services.Grammar;
	for (const rule of grammar.rules) if (isTerminalRule(rule) && isCommentTerminal(rule) && isMultilineComment(terminalRegex(rule))) rules.push(rule.name);
	return {
		multilineCommentRules: rules,
		nameRegexp: DefaultNameRegexp
	};
}
var main_exports = /* @__PURE__ */ __exportAll({
	AnnotatedTextEdit: () => AnnotatedTextEdit,
	ChangeAnnotation: () => ChangeAnnotation,
	ChangeAnnotationIdentifier: () => ChangeAnnotationIdentifier,
	CodeAction: () => CodeAction,
	CodeActionContext: () => CodeActionContext,
	CodeActionKind: () => CodeActionKind,
	CodeActionTriggerKind: () => CodeActionTriggerKind,
	CodeDescription: () => CodeDescription,
	CodeLens: () => CodeLens,
	Color: () => Color,
	ColorInformation: () => ColorInformation,
	ColorPresentation: () => ColorPresentation,
	Command: () => Command,
	CompletionItem: () => CompletionItem,
	CompletionItemKind: () => CompletionItemKind,
	CompletionItemLabelDetails: () => CompletionItemLabelDetails,
	CompletionItemTag: () => CompletionItemTag,
	CompletionList: () => CompletionList,
	CreateFile: () => CreateFile,
	DeleteFile: () => DeleteFile,
	Diagnostic: () => Diagnostic,
	DiagnosticRelatedInformation: () => DiagnosticRelatedInformation,
	DiagnosticSeverity: () => DiagnosticSeverity,
	DiagnosticTag: () => DiagnosticTag,
	DocumentHighlight: () => DocumentHighlight,
	DocumentHighlightKind: () => DocumentHighlightKind,
	DocumentLink: () => DocumentLink,
	DocumentSymbol: () => DocumentSymbol,
	DocumentUri: () => DocumentUri,
	EOL: () => EOL,
	FoldingRange: () => FoldingRange,
	FoldingRangeKind: () => FoldingRangeKind,
	FormattingOptions: () => FormattingOptions,
	Hover: () => Hover,
	InlayHint: () => InlayHint,
	InlayHintKind: () => InlayHintKind,
	InlayHintLabelPart: () => InlayHintLabelPart,
	InlineCompletionContext: () => InlineCompletionContext,
	InlineCompletionItem: () => InlineCompletionItem,
	InlineCompletionList: () => InlineCompletionList,
	InlineCompletionTriggerKind: () => InlineCompletionTriggerKind,
	InlineValueContext: () => InlineValueContext,
	InlineValueEvaluatableExpression: () => InlineValueEvaluatableExpression,
	InlineValueText: () => InlineValueText,
	InlineValueVariableLookup: () => InlineValueVariableLookup,
	InsertReplaceEdit: () => InsertReplaceEdit,
	InsertTextFormat: () => InsertTextFormat,
	InsertTextMode: () => InsertTextMode,
	Location: () => Location,
	LocationLink: () => LocationLink,
	MarkedString: () => MarkedString,
	MarkupContent: () => MarkupContent,
	MarkupKind: () => MarkupKind,
	OptionalVersionedTextDocumentIdentifier: () => OptionalVersionedTextDocumentIdentifier,
	ParameterInformation: () => ParameterInformation,
	Position: () => Position,
	Range: () => Range,
	RenameFile: () => RenameFile,
	SelectedCompletionInfo: () => SelectedCompletionInfo,
	SelectionRange: () => SelectionRange,
	SemanticTokenModifiers: () => SemanticTokenModifiers,
	SemanticTokenTypes: () => SemanticTokenTypes,
	SemanticTokens: () => SemanticTokens,
	SignatureInformation: () => SignatureInformation,
	StringValue: () => StringValue,
	SymbolInformation: () => SymbolInformation,
	SymbolKind: () => SymbolKind,
	SymbolTag: () => SymbolTag,
	TextDocument: () => TextDocument$1,
	TextDocumentEdit: () => TextDocumentEdit,
	TextDocumentIdentifier: () => TextDocumentIdentifier,
	TextDocumentItem: () => TextDocumentItem,
	TextEdit: () => TextEdit,
	URI: () => URI$1,
	VersionedTextDocumentIdentifier: () => VersionedTextDocumentIdentifier,
	WorkspaceChange: () => WorkspaceChange,
	WorkspaceEdit: () => WorkspaceEdit,
	WorkspaceFolder: () => WorkspaceFolder,
	WorkspaceSymbol: () => WorkspaceSymbol,
	integer: () => integer,
	uinteger: () => uinteger
});
var DocumentUri, URI$1, integer, uinteger, Position, Range, Location, LocationLink, Color, ColorInformation, ColorPresentation, FoldingRangeKind, FoldingRange, DiagnosticRelatedInformation, DiagnosticSeverity, DiagnosticTag, CodeDescription, Diagnostic, Command, TextEdit, ChangeAnnotation, ChangeAnnotationIdentifier, AnnotatedTextEdit, TextDocumentEdit, CreateFile, RenameFile, DeleteFile, WorkspaceEdit, TextEditChangeImpl, ChangeAnnotations, WorkspaceChange, TextDocumentIdentifier, VersionedTextDocumentIdentifier, OptionalVersionedTextDocumentIdentifier, TextDocumentItem, MarkupKind, MarkupContent, CompletionItemKind, InsertTextFormat, CompletionItemTag, InsertReplaceEdit, InsertTextMode, CompletionItemLabelDetails, CompletionItem, CompletionList, MarkedString, Hover, ParameterInformation, SignatureInformation, DocumentHighlightKind, DocumentHighlight, SymbolKind, SymbolTag, SymbolInformation, WorkspaceSymbol, DocumentSymbol, CodeActionKind, CodeActionTriggerKind, CodeActionContext, CodeAction, CodeLens, FormattingOptions, DocumentLink, SelectionRange, SemanticTokenTypes, SemanticTokenModifiers, SemanticTokens, InlineValueText, InlineValueVariableLookup, InlineValueEvaluatableExpression, InlineValueContext, InlayHintKind, InlayHintLabelPart, InlayHint, StringValue, InlineCompletionItem, InlineCompletionList, InlineCompletionTriggerKind, SelectedCompletionInfo, InlineCompletionContext, WorkspaceFolder, EOL, TextDocument$1, FullTextDocument$1, Is;
var init_main = __esmMin((() => {
	(function(DocumentUri) {
		function is(value) {
			return typeof value === "string";
		}
		DocumentUri.is = is;
	})(DocumentUri || (DocumentUri = {}));
	(function(URI) {
		function is(value) {
			return typeof value === "string";
		}
		URI.is = is;
	})(URI$1 || (URI$1 = {}));
	(function(integer) {
		integer.MIN_VALUE = -2147483648;
		integer.MAX_VALUE = 2147483647;
		function is(value) {
			return typeof value === "number" && integer.MIN_VALUE <= value && value <= integer.MAX_VALUE;
		}
		integer.is = is;
	})(integer || (integer = {}));
	(function(uinteger) {
		uinteger.MIN_VALUE = 0;
		uinteger.MAX_VALUE = 2147483647;
		function is(value) {
			return typeof value === "number" && uinteger.MIN_VALUE <= value && value <= uinteger.MAX_VALUE;
		}
		uinteger.is = is;
	})(uinteger || (uinteger = {}));
	(function(Position) {
		/**
		* Creates a new Position literal from the given line and character.
		* @param line The position's line.
		* @param character The position's character.
		*/
		function create(line, character) {
			if (line === Number.MAX_VALUE) line = uinteger.MAX_VALUE;
			if (character === Number.MAX_VALUE) character = uinteger.MAX_VALUE;
			return {
				line,
				character
			};
		}
		Position.create = create;
		/**
		* Checks whether the given literal conforms to the {@link Position} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.objectLiteral(candidate) && Is.uinteger(candidate.line) && Is.uinteger(candidate.character);
		}
		Position.is = is;
	})(Position || (Position = {}));
	(function(Range) {
		function create(one, two, three, four) {
			if (Is.uinteger(one) && Is.uinteger(two) && Is.uinteger(three) && Is.uinteger(four)) return {
				start: Position.create(one, two),
				end: Position.create(three, four)
			};
			else if (Position.is(one) && Position.is(two)) return {
				start: one,
				end: two
			};
			else throw new Error(`Range#create called with invalid arguments[${one}, ${two}, ${three}, ${four}]`);
		}
		Range.create = create;
		/**
		* Checks whether the given literal conforms to the {@link Range} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.objectLiteral(candidate) && Position.is(candidate.start) && Position.is(candidate.end);
		}
		Range.is = is;
	})(Range || (Range = {}));
	(function(Location) {
		/**
		* Creates a Location literal.
		* @param uri The location's uri.
		* @param range The location's range.
		*/
		function create(uri, range) {
			return {
				uri,
				range
			};
		}
		Location.create = create;
		/**
		* Checks whether the given literal conforms to the {@link Location} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.objectLiteral(candidate) && Range.is(candidate.range) && (Is.string(candidate.uri) || Is.undefined(candidate.uri));
		}
		Location.is = is;
	})(Location || (Location = {}));
	(function(LocationLink) {
		/**
		* Creates a LocationLink literal.
		* @param targetUri The definition's uri.
		* @param targetRange The full range of the definition.
		* @param targetSelectionRange The span of the symbol definition at the target.
		* @param originSelectionRange The span of the symbol being defined in the originating source file.
		*/
		function create(targetUri, targetRange, targetSelectionRange, originSelectionRange) {
			return {
				targetUri,
				targetRange,
				targetSelectionRange,
				originSelectionRange
			};
		}
		LocationLink.create = create;
		/**
		* Checks whether the given literal conforms to the {@link LocationLink} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.objectLiteral(candidate) && Range.is(candidate.targetRange) && Is.string(candidate.targetUri) && Range.is(candidate.targetSelectionRange) && (Range.is(candidate.originSelectionRange) || Is.undefined(candidate.originSelectionRange));
		}
		LocationLink.is = is;
	})(LocationLink || (LocationLink = {}));
	(function(Color) {
		/**
		* Creates a new Color literal.
		*/
		function create(red, green, blue, alpha) {
			return {
				red,
				green,
				blue,
				alpha
			};
		}
		Color.create = create;
		/**
		* Checks whether the given literal conforms to the {@link Color} interface.
		*/
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && Is.numberRange(candidate.red, 0, 1) && Is.numberRange(candidate.green, 0, 1) && Is.numberRange(candidate.blue, 0, 1) && Is.numberRange(candidate.alpha, 0, 1);
		}
		Color.is = is;
	})(Color || (Color = {}));
	(function(ColorInformation) {
		/**
		* Creates a new ColorInformation literal.
		*/
		function create(range, color) {
			return {
				range,
				color
			};
		}
		ColorInformation.create = create;
		/**
		* Checks whether the given literal conforms to the {@link ColorInformation} interface.
		*/
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && Range.is(candidate.range) && Color.is(candidate.color);
		}
		ColorInformation.is = is;
	})(ColorInformation || (ColorInformation = {}));
	(function(ColorPresentation) {
		/**
		* Creates a new ColorInformation literal.
		*/
		function create(label, textEdit, additionalTextEdits) {
			return {
				label,
				textEdit,
				additionalTextEdits
			};
		}
		ColorPresentation.create = create;
		/**
		* Checks whether the given literal conforms to the {@link ColorInformation} interface.
		*/
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && Is.string(candidate.label) && (Is.undefined(candidate.textEdit) || TextEdit.is(candidate)) && (Is.undefined(candidate.additionalTextEdits) || Is.typedArray(candidate.additionalTextEdits, TextEdit.is));
		}
		ColorPresentation.is = is;
	})(ColorPresentation || (ColorPresentation = {}));
	(function(FoldingRangeKind) {
		/**
		* Folding range for a comment
		*/
		FoldingRangeKind.Comment = "comment";
		/**
		* Folding range for an import or include
		*/
		FoldingRangeKind.Imports = "imports";
		/**
		* Folding range for a region (e.g. `#region`)
		*/
		FoldingRangeKind.Region = "region";
	})(FoldingRangeKind || (FoldingRangeKind = {}));
	(function(FoldingRange) {
		/**
		* Creates a new FoldingRange literal.
		*/
		function create(startLine, endLine, startCharacter, endCharacter, kind, collapsedText) {
			const result = {
				startLine,
				endLine
			};
			if (Is.defined(startCharacter)) result.startCharacter = startCharacter;
			if (Is.defined(endCharacter)) result.endCharacter = endCharacter;
			if (Is.defined(kind)) result.kind = kind;
			if (Is.defined(collapsedText)) result.collapsedText = collapsedText;
			return result;
		}
		FoldingRange.create = create;
		/**
		* Checks whether the given literal conforms to the {@link FoldingRange} interface.
		*/
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && Is.uinteger(candidate.startLine) && Is.uinteger(candidate.startLine) && (Is.undefined(candidate.startCharacter) || Is.uinteger(candidate.startCharacter)) && (Is.undefined(candidate.endCharacter) || Is.uinteger(candidate.endCharacter)) && (Is.undefined(candidate.kind) || Is.string(candidate.kind));
		}
		FoldingRange.is = is;
	})(FoldingRange || (FoldingRange = {}));
	(function(DiagnosticRelatedInformation) {
		/**
		* Creates a new DiagnosticRelatedInformation literal.
		*/
		function create(location, message) {
			return {
				location,
				message
			};
		}
		DiagnosticRelatedInformation.create = create;
		/**
		* Checks whether the given literal conforms to the {@link DiagnosticRelatedInformation} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Location.is(candidate.location) && Is.string(candidate.message);
		}
		DiagnosticRelatedInformation.is = is;
	})(DiagnosticRelatedInformation || (DiagnosticRelatedInformation = {}));
	(function(DiagnosticSeverity) {
		/**
		* Reports an error.
		*/
		DiagnosticSeverity.Error = 1;
		/**
		* Reports a warning.
		*/
		DiagnosticSeverity.Warning = 2;
		/**
		* Reports an information.
		*/
		DiagnosticSeverity.Information = 3;
		/**
		* Reports a hint.
		*/
		DiagnosticSeverity.Hint = 4;
	})(DiagnosticSeverity || (DiagnosticSeverity = {}));
	(function(DiagnosticTag) {
		/**
		* Unused or unnecessary code.
		*
		* Clients are allowed to render diagnostics with this tag faded out instead of having
		* an error squiggle.
		*/
		DiagnosticTag.Unnecessary = 1;
		/**
		* Deprecated or obsolete code.
		*
		* Clients are allowed to rendered diagnostics with this tag strike through.
		*/
		DiagnosticTag.Deprecated = 2;
	})(DiagnosticTag || (DiagnosticTag = {}));
	(function(CodeDescription) {
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && Is.string(candidate.href);
		}
		CodeDescription.is = is;
	})(CodeDescription || (CodeDescription = {}));
	(function(Diagnostic) {
		/**
		* Creates a new Diagnostic literal.
		*/
		function create(range, message, severity, code, source, relatedInformation) {
			let result = {
				range,
				message
			};
			if (Is.defined(severity)) result.severity = severity;
			if (Is.defined(code)) result.code = code;
			if (Is.defined(source)) result.source = source;
			if (Is.defined(relatedInformation)) result.relatedInformation = relatedInformation;
			return result;
		}
		Diagnostic.create = create;
		/**
		* Checks whether the given literal conforms to the {@link Diagnostic} interface.
		*/
		function is(value) {
			var _a;
			let candidate = value;
			return Is.defined(candidate) && Range.is(candidate.range) && Is.string(candidate.message) && (Is.number(candidate.severity) || Is.undefined(candidate.severity)) && (Is.integer(candidate.code) || Is.string(candidate.code) || Is.undefined(candidate.code)) && (Is.undefined(candidate.codeDescription) || Is.string((_a = candidate.codeDescription) === null || _a === void 0 ? void 0 : _a.href)) && (Is.string(candidate.source) || Is.undefined(candidate.source)) && (Is.undefined(candidate.relatedInformation) || Is.typedArray(candidate.relatedInformation, DiagnosticRelatedInformation.is));
		}
		Diagnostic.is = is;
	})(Diagnostic || (Diagnostic = {}));
	(function(Command) {
		/**
		* Creates a new Command literal.
		*/
		function create(title, command, ...args) {
			let result = {
				title,
				command
			};
			if (Is.defined(args) && args.length > 0) result.arguments = args;
			return result;
		}
		Command.create = create;
		/**
		* Checks whether the given literal conforms to the {@link Command} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Is.string(candidate.title) && Is.string(candidate.command);
		}
		Command.is = is;
	})(Command || (Command = {}));
	(function(TextEdit) {
		/**
		* Creates a replace text edit.
		* @param range The range of text to be replaced.
		* @param newText The new text.
		*/
		function replace(range, newText) {
			return {
				range,
				newText
			};
		}
		TextEdit.replace = replace;
		/**
		* Creates an insert text edit.
		* @param position The position to insert the text at.
		* @param newText The text to be inserted.
		*/
		function insert(position, newText) {
			return {
				range: {
					start: position,
					end: position
				},
				newText
			};
		}
		TextEdit.insert = insert;
		/**
		* Creates a delete text edit.
		* @param range The range of text to be deleted.
		*/
		function del(range) {
			return {
				range,
				newText: ""
			};
		}
		TextEdit.del = del;
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && Is.string(candidate.newText) && Range.is(candidate.range);
		}
		TextEdit.is = is;
	})(TextEdit || (TextEdit = {}));
	(function(ChangeAnnotation) {
		function create(label, needsConfirmation, description) {
			const result = { label };
			if (needsConfirmation !== void 0) result.needsConfirmation = needsConfirmation;
			if (description !== void 0) result.description = description;
			return result;
		}
		ChangeAnnotation.create = create;
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && Is.string(candidate.label) && (Is.boolean(candidate.needsConfirmation) || candidate.needsConfirmation === void 0) && (Is.string(candidate.description) || candidate.description === void 0);
		}
		ChangeAnnotation.is = is;
	})(ChangeAnnotation || (ChangeAnnotation = {}));
	(function(ChangeAnnotationIdentifier) {
		function is(value) {
			const candidate = value;
			return Is.string(candidate);
		}
		ChangeAnnotationIdentifier.is = is;
	})(ChangeAnnotationIdentifier || (ChangeAnnotationIdentifier = {}));
	(function(AnnotatedTextEdit) {
		/**
		* Creates an annotated replace text edit.
		*
		* @param range The range of text to be replaced.
		* @param newText The new text.
		* @param annotation The annotation.
		*/
		function replace(range, newText, annotation) {
			return {
				range,
				newText,
				annotationId: annotation
			};
		}
		AnnotatedTextEdit.replace = replace;
		/**
		* Creates an annotated insert text edit.
		*
		* @param position The position to insert the text at.
		* @param newText The text to be inserted.
		* @param annotation The annotation.
		*/
		function insert(position, newText, annotation) {
			return {
				range: {
					start: position,
					end: position
				},
				newText,
				annotationId: annotation
			};
		}
		AnnotatedTextEdit.insert = insert;
		/**
		* Creates an annotated delete text edit.
		*
		* @param range The range of text to be deleted.
		* @param annotation The annotation.
		*/
		function del(range, annotation) {
			return {
				range,
				newText: "",
				annotationId: annotation
			};
		}
		AnnotatedTextEdit.del = del;
		function is(value) {
			const candidate = value;
			return TextEdit.is(candidate) && (ChangeAnnotation.is(candidate.annotationId) || ChangeAnnotationIdentifier.is(candidate.annotationId));
		}
		AnnotatedTextEdit.is = is;
	})(AnnotatedTextEdit || (AnnotatedTextEdit = {}));
	(function(TextDocumentEdit) {
		/**
		* Creates a new `TextDocumentEdit`
		*/
		function create(textDocument, edits) {
			return {
				textDocument,
				edits
			};
		}
		TextDocumentEdit.create = create;
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && OptionalVersionedTextDocumentIdentifier.is(candidate.textDocument) && Array.isArray(candidate.edits);
		}
		TextDocumentEdit.is = is;
	})(TextDocumentEdit || (TextDocumentEdit = {}));
	(function(CreateFile) {
		function create(uri, options, annotation) {
			let result = {
				kind: "create",
				uri
			};
			if (options !== void 0 && (options.overwrite !== void 0 || options.ignoreIfExists !== void 0)) result.options = options;
			if (annotation !== void 0) result.annotationId = annotation;
			return result;
		}
		CreateFile.create = create;
		function is(value) {
			let candidate = value;
			return candidate && candidate.kind === "create" && Is.string(candidate.uri) && (candidate.options === void 0 || (candidate.options.overwrite === void 0 || Is.boolean(candidate.options.overwrite)) && (candidate.options.ignoreIfExists === void 0 || Is.boolean(candidate.options.ignoreIfExists))) && (candidate.annotationId === void 0 || ChangeAnnotationIdentifier.is(candidate.annotationId));
		}
		CreateFile.is = is;
	})(CreateFile || (CreateFile = {}));
	(function(RenameFile) {
		function create(oldUri, newUri, options, annotation) {
			let result = {
				kind: "rename",
				oldUri,
				newUri
			};
			if (options !== void 0 && (options.overwrite !== void 0 || options.ignoreIfExists !== void 0)) result.options = options;
			if (annotation !== void 0) result.annotationId = annotation;
			return result;
		}
		RenameFile.create = create;
		function is(value) {
			let candidate = value;
			return candidate && candidate.kind === "rename" && Is.string(candidate.oldUri) && Is.string(candidate.newUri) && (candidate.options === void 0 || (candidate.options.overwrite === void 0 || Is.boolean(candidate.options.overwrite)) && (candidate.options.ignoreIfExists === void 0 || Is.boolean(candidate.options.ignoreIfExists))) && (candidate.annotationId === void 0 || ChangeAnnotationIdentifier.is(candidate.annotationId));
		}
		RenameFile.is = is;
	})(RenameFile || (RenameFile = {}));
	(function(DeleteFile) {
		function create(uri, options, annotation) {
			let result = {
				kind: "delete",
				uri
			};
			if (options !== void 0 && (options.recursive !== void 0 || options.ignoreIfNotExists !== void 0)) result.options = options;
			if (annotation !== void 0) result.annotationId = annotation;
			return result;
		}
		DeleteFile.create = create;
		function is(value) {
			let candidate = value;
			return candidate && candidate.kind === "delete" && Is.string(candidate.uri) && (candidate.options === void 0 || (candidate.options.recursive === void 0 || Is.boolean(candidate.options.recursive)) && (candidate.options.ignoreIfNotExists === void 0 || Is.boolean(candidate.options.ignoreIfNotExists))) && (candidate.annotationId === void 0 || ChangeAnnotationIdentifier.is(candidate.annotationId));
		}
		DeleteFile.is = is;
	})(DeleteFile || (DeleteFile = {}));
	(function(WorkspaceEdit) {
		function is(value) {
			let candidate = value;
			return candidate && (candidate.changes !== void 0 || candidate.documentChanges !== void 0) && (candidate.documentChanges === void 0 || candidate.documentChanges.every((change) => {
				if (Is.string(change.kind)) return CreateFile.is(change) || RenameFile.is(change) || DeleteFile.is(change);
				else return TextDocumentEdit.is(change);
			}));
		}
		WorkspaceEdit.is = is;
	})(WorkspaceEdit || (WorkspaceEdit = {}));
	TextEditChangeImpl = class {
		constructor(edits, changeAnnotations) {
			this.edits = edits;
			this.changeAnnotations = changeAnnotations;
		}
		insert(position, newText, annotation) {
			let edit;
			let id;
			if (annotation === void 0) edit = TextEdit.insert(position, newText);
			else if (ChangeAnnotationIdentifier.is(annotation)) {
				id = annotation;
				edit = AnnotatedTextEdit.insert(position, newText, annotation);
			} else {
				this.assertChangeAnnotations(this.changeAnnotations);
				id = this.changeAnnotations.manage(annotation);
				edit = AnnotatedTextEdit.insert(position, newText, id);
			}
			this.edits.push(edit);
			if (id !== void 0) return id;
		}
		replace(range, newText, annotation) {
			let edit;
			let id;
			if (annotation === void 0) edit = TextEdit.replace(range, newText);
			else if (ChangeAnnotationIdentifier.is(annotation)) {
				id = annotation;
				edit = AnnotatedTextEdit.replace(range, newText, annotation);
			} else {
				this.assertChangeAnnotations(this.changeAnnotations);
				id = this.changeAnnotations.manage(annotation);
				edit = AnnotatedTextEdit.replace(range, newText, id);
			}
			this.edits.push(edit);
			if (id !== void 0) return id;
		}
		delete(range, annotation) {
			let edit;
			let id;
			if (annotation === void 0) edit = TextEdit.del(range);
			else if (ChangeAnnotationIdentifier.is(annotation)) {
				id = annotation;
				edit = AnnotatedTextEdit.del(range, annotation);
			} else {
				this.assertChangeAnnotations(this.changeAnnotations);
				id = this.changeAnnotations.manage(annotation);
				edit = AnnotatedTextEdit.del(range, id);
			}
			this.edits.push(edit);
			if (id !== void 0) return id;
		}
		add(edit) {
			this.edits.push(edit);
		}
		all() {
			return this.edits;
		}
		clear() {
			this.edits.splice(0, this.edits.length);
		}
		assertChangeAnnotations(value) {
			if (value === void 0) throw new Error(`Text edit change is not configured to manage change annotations.`);
		}
	};
	ChangeAnnotations = class {
		constructor(annotations) {
			this._annotations = annotations === void 0 ? Object.create(null) : annotations;
			this._counter = 0;
			this._size = 0;
		}
		all() {
			return this._annotations;
		}
		get size() {
			return this._size;
		}
		manage(idOrAnnotation, annotation) {
			let id;
			if (ChangeAnnotationIdentifier.is(idOrAnnotation)) id = idOrAnnotation;
			else {
				id = this.nextId();
				annotation = idOrAnnotation;
			}
			if (this._annotations[id] !== void 0) throw new Error(`Id ${id} is already in use.`);
			if (annotation === void 0) throw new Error(`No annotation provided for id ${id}`);
			this._annotations[id] = annotation;
			this._size++;
			return id;
		}
		nextId() {
			this._counter++;
			return this._counter.toString();
		}
	};
	WorkspaceChange = class {
		constructor(workspaceEdit) {
			this._textEditChanges = Object.create(null);
			if (workspaceEdit !== void 0) {
				this._workspaceEdit = workspaceEdit;
				if (workspaceEdit.documentChanges) {
					this._changeAnnotations = new ChangeAnnotations(workspaceEdit.changeAnnotations);
					workspaceEdit.changeAnnotations = this._changeAnnotations.all();
					workspaceEdit.documentChanges.forEach((change) => {
						if (TextDocumentEdit.is(change)) {
							const textEditChange = new TextEditChangeImpl(change.edits, this._changeAnnotations);
							this._textEditChanges[change.textDocument.uri] = textEditChange;
						}
					});
				} else if (workspaceEdit.changes) Object.keys(workspaceEdit.changes).forEach((key) => {
					const textEditChange = new TextEditChangeImpl(workspaceEdit.changes[key]);
					this._textEditChanges[key] = textEditChange;
				});
			} else this._workspaceEdit = {};
		}
		/**
		* Returns the underlying {@link WorkspaceEdit} literal
		* use to be returned from a workspace edit operation like rename.
		*/
		get edit() {
			this.initDocumentChanges();
			if (this._changeAnnotations !== void 0) if (this._changeAnnotations.size === 0) this._workspaceEdit.changeAnnotations = void 0;
			else this._workspaceEdit.changeAnnotations = this._changeAnnotations.all();
			return this._workspaceEdit;
		}
		getTextEditChange(key) {
			if (OptionalVersionedTextDocumentIdentifier.is(key)) {
				this.initDocumentChanges();
				if (this._workspaceEdit.documentChanges === void 0) throw new Error("Workspace edit is not configured for document changes.");
				const textDocument = {
					uri: key.uri,
					version: key.version
				};
				let result = this._textEditChanges[textDocument.uri];
				if (!result) {
					const edits = [];
					const textDocumentEdit = {
						textDocument,
						edits
					};
					this._workspaceEdit.documentChanges.push(textDocumentEdit);
					result = new TextEditChangeImpl(edits, this._changeAnnotations);
					this._textEditChanges[textDocument.uri] = result;
				}
				return result;
			} else {
				this.initChanges();
				if (this._workspaceEdit.changes === void 0) throw new Error("Workspace edit is not configured for normal text edit changes.");
				let result = this._textEditChanges[key];
				if (!result) {
					let edits = [];
					this._workspaceEdit.changes[key] = edits;
					result = new TextEditChangeImpl(edits);
					this._textEditChanges[key] = result;
				}
				return result;
			}
		}
		initDocumentChanges() {
			if (this._workspaceEdit.documentChanges === void 0 && this._workspaceEdit.changes === void 0) {
				this._changeAnnotations = new ChangeAnnotations();
				this._workspaceEdit.documentChanges = [];
				this._workspaceEdit.changeAnnotations = this._changeAnnotations.all();
			}
		}
		initChanges() {
			if (this._workspaceEdit.documentChanges === void 0 && this._workspaceEdit.changes === void 0) this._workspaceEdit.changes = Object.create(null);
		}
		createFile(uri, optionsOrAnnotation, options) {
			this.initDocumentChanges();
			if (this._workspaceEdit.documentChanges === void 0) throw new Error("Workspace edit is not configured for document changes.");
			let annotation;
			if (ChangeAnnotation.is(optionsOrAnnotation) || ChangeAnnotationIdentifier.is(optionsOrAnnotation)) annotation = optionsOrAnnotation;
			else options = optionsOrAnnotation;
			let operation;
			let id;
			if (annotation === void 0) operation = CreateFile.create(uri, options);
			else {
				id = ChangeAnnotationIdentifier.is(annotation) ? annotation : this._changeAnnotations.manage(annotation);
				operation = CreateFile.create(uri, options, id);
			}
			this._workspaceEdit.documentChanges.push(operation);
			if (id !== void 0) return id;
		}
		renameFile(oldUri, newUri, optionsOrAnnotation, options) {
			this.initDocumentChanges();
			if (this._workspaceEdit.documentChanges === void 0) throw new Error("Workspace edit is not configured for document changes.");
			let annotation;
			if (ChangeAnnotation.is(optionsOrAnnotation) || ChangeAnnotationIdentifier.is(optionsOrAnnotation)) annotation = optionsOrAnnotation;
			else options = optionsOrAnnotation;
			let operation;
			let id;
			if (annotation === void 0) operation = RenameFile.create(oldUri, newUri, options);
			else {
				id = ChangeAnnotationIdentifier.is(annotation) ? annotation : this._changeAnnotations.manage(annotation);
				operation = RenameFile.create(oldUri, newUri, options, id);
			}
			this._workspaceEdit.documentChanges.push(operation);
			if (id !== void 0) return id;
		}
		deleteFile(uri, optionsOrAnnotation, options) {
			this.initDocumentChanges();
			if (this._workspaceEdit.documentChanges === void 0) throw new Error("Workspace edit is not configured for document changes.");
			let annotation;
			if (ChangeAnnotation.is(optionsOrAnnotation) || ChangeAnnotationIdentifier.is(optionsOrAnnotation)) annotation = optionsOrAnnotation;
			else options = optionsOrAnnotation;
			let operation;
			let id;
			if (annotation === void 0) operation = DeleteFile.create(uri, options);
			else {
				id = ChangeAnnotationIdentifier.is(annotation) ? annotation : this._changeAnnotations.manage(annotation);
				operation = DeleteFile.create(uri, options, id);
			}
			this._workspaceEdit.documentChanges.push(operation);
			if (id !== void 0) return id;
		}
	};
	(function(TextDocumentIdentifier) {
		/**
		* Creates a new TextDocumentIdentifier literal.
		* @param uri The document's uri.
		*/
		function create(uri) {
			return { uri };
		}
		TextDocumentIdentifier.create = create;
		/**
		* Checks whether the given literal conforms to the {@link TextDocumentIdentifier} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Is.string(candidate.uri);
		}
		TextDocumentIdentifier.is = is;
	})(TextDocumentIdentifier || (TextDocumentIdentifier = {}));
	(function(VersionedTextDocumentIdentifier) {
		/**
		* Creates a new VersionedTextDocumentIdentifier literal.
		* @param uri The document's uri.
		* @param version The document's version.
		*/
		function create(uri, version) {
			return {
				uri,
				version
			};
		}
		VersionedTextDocumentIdentifier.create = create;
		/**
		* Checks whether the given literal conforms to the {@link VersionedTextDocumentIdentifier} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Is.string(candidate.uri) && Is.integer(candidate.version);
		}
		VersionedTextDocumentIdentifier.is = is;
	})(VersionedTextDocumentIdentifier || (VersionedTextDocumentIdentifier = {}));
	(function(OptionalVersionedTextDocumentIdentifier) {
		/**
		* Creates a new OptionalVersionedTextDocumentIdentifier literal.
		* @param uri The document's uri.
		* @param version The document's version.
		*/
		function create(uri, version) {
			return {
				uri,
				version
			};
		}
		OptionalVersionedTextDocumentIdentifier.create = create;
		/**
		* Checks whether the given literal conforms to the {@link OptionalVersionedTextDocumentIdentifier} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Is.string(candidate.uri) && (candidate.version === null || Is.integer(candidate.version));
		}
		OptionalVersionedTextDocumentIdentifier.is = is;
	})(OptionalVersionedTextDocumentIdentifier || (OptionalVersionedTextDocumentIdentifier = {}));
	(function(TextDocumentItem) {
		/**
		* Creates a new TextDocumentItem literal.
		* @param uri The document's uri.
		* @param languageId The document's language identifier.
		* @param version The document's version number.
		* @param text The document's text.
		*/
		function create(uri, languageId, version, text) {
			return {
				uri,
				languageId,
				version,
				text
			};
		}
		TextDocumentItem.create = create;
		/**
		* Checks whether the given literal conforms to the {@link TextDocumentItem} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Is.string(candidate.uri) && Is.string(candidate.languageId) && Is.integer(candidate.version) && Is.string(candidate.text);
		}
		TextDocumentItem.is = is;
	})(TextDocumentItem || (TextDocumentItem = {}));
	(function(MarkupKind) {
		/**
		* Plain text is supported as a content format
		*/
		MarkupKind.PlainText = "plaintext";
		/**
		* Markdown is supported as a content format
		*/
		MarkupKind.Markdown = "markdown";
		/**
		* Checks whether the given value is a value of the {@link MarkupKind} type.
		*/
		function is(value) {
			const candidate = value;
			return candidate === MarkupKind.PlainText || candidate === MarkupKind.Markdown;
		}
		MarkupKind.is = is;
	})(MarkupKind || (MarkupKind = {}));
	(function(MarkupContent) {
		/**
		* Checks whether the given value conforms to the {@link MarkupContent} interface.
		*/
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(value) && MarkupKind.is(candidate.kind) && Is.string(candidate.value);
		}
		MarkupContent.is = is;
	})(MarkupContent || (MarkupContent = {}));
	(function(CompletionItemKind) {
		CompletionItemKind.Text = 1;
		CompletionItemKind.Method = 2;
		CompletionItemKind.Function = 3;
		CompletionItemKind.Constructor = 4;
		CompletionItemKind.Field = 5;
		CompletionItemKind.Variable = 6;
		CompletionItemKind.Class = 7;
		CompletionItemKind.Interface = 8;
		CompletionItemKind.Module = 9;
		CompletionItemKind.Property = 10;
		CompletionItemKind.Unit = 11;
		CompletionItemKind.Value = 12;
		CompletionItemKind.Enum = 13;
		CompletionItemKind.Keyword = 14;
		CompletionItemKind.Snippet = 15;
		CompletionItemKind.Color = 16;
		CompletionItemKind.File = 17;
		CompletionItemKind.Reference = 18;
		CompletionItemKind.Folder = 19;
		CompletionItemKind.EnumMember = 20;
		CompletionItemKind.Constant = 21;
		CompletionItemKind.Struct = 22;
		CompletionItemKind.Event = 23;
		CompletionItemKind.Operator = 24;
		CompletionItemKind.TypeParameter = 25;
	})(CompletionItemKind || (CompletionItemKind = {}));
	(function(InsertTextFormat) {
		/**
		* The primary text to be inserted is treated as a plain string.
		*/
		InsertTextFormat.PlainText = 1;
		/**
		* The primary text to be inserted is treated as a snippet.
		*
		* A snippet can define tab stops and placeholders with `$1`, `$2`
		* and `${3:foo}`. `$0` defines the final tab stop, it defaults to
		* the end of the snippet. Placeholders with equal identifiers are linked,
		* that is typing in one will update others too.
		*
		* See also: https://microsoft.github.io/language-server-protocol/specifications/specification-current/#snippet_syntax
		*/
		InsertTextFormat.Snippet = 2;
	})(InsertTextFormat || (InsertTextFormat = {}));
	(function(CompletionItemTag) {
		/**
		* Render a completion as obsolete, usually using a strike-out.
		*/
		CompletionItemTag.Deprecated = 1;
	})(CompletionItemTag || (CompletionItemTag = {}));
	(function(InsertReplaceEdit) {
		/**
		* Creates a new insert / replace edit
		*/
		function create(newText, insert, replace) {
			return {
				newText,
				insert,
				replace
			};
		}
		InsertReplaceEdit.create = create;
		/**
		* Checks whether the given literal conforms to the {@link InsertReplaceEdit} interface.
		*/
		function is(value) {
			const candidate = value;
			return candidate && Is.string(candidate.newText) && Range.is(candidate.insert) && Range.is(candidate.replace);
		}
		InsertReplaceEdit.is = is;
	})(InsertReplaceEdit || (InsertReplaceEdit = {}));
	(function(InsertTextMode) {
		/**
		* The insertion or replace strings is taken as it is. If the
		* value is multi line the lines below the cursor will be
		* inserted using the indentation defined in the string value.
		* The client will not apply any kind of adjustments to the
		* string.
		*/
		InsertTextMode.asIs = 1;
		/**
		* The editor adjusts leading whitespace of new lines so that
		* they match the indentation up to the cursor of the line for
		* which the item is accepted.
		*
		* Consider a line like this: <2tabs><cursor><3tabs>foo. Accepting a
		* multi line completion item is indented using 2 tabs and all
		* following lines inserted will be indented using 2 tabs as well.
		*/
		InsertTextMode.adjustIndentation = 2;
	})(InsertTextMode || (InsertTextMode = {}));
	(function(CompletionItemLabelDetails) {
		function is(value) {
			const candidate = value;
			return candidate && (Is.string(candidate.detail) || candidate.detail === void 0) && (Is.string(candidate.description) || candidate.description === void 0);
		}
		CompletionItemLabelDetails.is = is;
	})(CompletionItemLabelDetails || (CompletionItemLabelDetails = {}));
	(function(CompletionItem) {
		/**
		* Create a completion item and seed it with a label.
		* @param label The completion item's label
		*/
		function create(label) {
			return { label };
		}
		CompletionItem.create = create;
	})(CompletionItem || (CompletionItem = {}));
	(function(CompletionList) {
		/**
		* Creates a new completion list.
		*
		* @param items The completion items.
		* @param isIncomplete The list is not complete.
		*/
		function create(items, isIncomplete) {
			return {
				items: items ? items : [],
				isIncomplete: !!isIncomplete
			};
		}
		CompletionList.create = create;
	})(CompletionList || (CompletionList = {}));
	(function(MarkedString) {
		/**
		* Creates a marked string from plain text.
		*
		* @param plainText The plain text.
		*/
		function fromPlainText(plainText) {
			return plainText.replace(/[\\`*_{}[\]()#+\-.!]/g, "\\$&");
		}
		MarkedString.fromPlainText = fromPlainText;
		/**
		* Checks whether the given value conforms to the {@link MarkedString} type.
		*/
		function is(value) {
			const candidate = value;
			return Is.string(candidate) || Is.objectLiteral(candidate) && Is.string(candidate.language) && Is.string(candidate.value);
		}
		MarkedString.is = is;
	})(MarkedString || (MarkedString = {}));
	(function(Hover) {
		/**
		* Checks whether the given value conforms to the {@link Hover} interface.
		*/
		function is(value) {
			let candidate = value;
			return !!candidate && Is.objectLiteral(candidate) && (MarkupContent.is(candidate.contents) || MarkedString.is(candidate.contents) || Is.typedArray(candidate.contents, MarkedString.is)) && (value.range === void 0 || Range.is(value.range));
		}
		Hover.is = is;
	})(Hover || (Hover = {}));
	(function(ParameterInformation) {
		/**
		* Creates a new parameter information literal.
		*
		* @param label A label string.
		* @param documentation A doc string.
		*/
		function create(label, documentation) {
			return documentation ? {
				label,
				documentation
			} : { label };
		}
		ParameterInformation.create = create;
	})(ParameterInformation || (ParameterInformation = {}));
	(function(SignatureInformation) {
		function create(label, documentation, ...parameters) {
			let result = { label };
			if (Is.defined(documentation)) result.documentation = documentation;
			if (Is.defined(parameters)) result.parameters = parameters;
			else result.parameters = [];
			return result;
		}
		SignatureInformation.create = create;
	})(SignatureInformation || (SignatureInformation = {}));
	(function(DocumentHighlightKind) {
		/**
		* A textual occurrence.
		*/
		DocumentHighlightKind.Text = 1;
		/**
		* Read-access of a symbol, like reading a variable.
		*/
		DocumentHighlightKind.Read = 2;
		/**
		* Write-access of a symbol, like writing to a variable.
		*/
		DocumentHighlightKind.Write = 3;
	})(DocumentHighlightKind || (DocumentHighlightKind = {}));
	(function(DocumentHighlight) {
		/**
		* Create a DocumentHighlight object.
		* @param range The range the highlight applies to.
		* @param kind The highlight kind
		*/
		function create(range, kind) {
			let result = { range };
			if (Is.number(kind)) result.kind = kind;
			return result;
		}
		DocumentHighlight.create = create;
	})(DocumentHighlight || (DocumentHighlight = {}));
	(function(SymbolKind) {
		SymbolKind.File = 1;
		SymbolKind.Module = 2;
		SymbolKind.Namespace = 3;
		SymbolKind.Package = 4;
		SymbolKind.Class = 5;
		SymbolKind.Method = 6;
		SymbolKind.Property = 7;
		SymbolKind.Field = 8;
		SymbolKind.Constructor = 9;
		SymbolKind.Enum = 10;
		SymbolKind.Interface = 11;
		SymbolKind.Function = 12;
		SymbolKind.Variable = 13;
		SymbolKind.Constant = 14;
		SymbolKind.String = 15;
		SymbolKind.Number = 16;
		SymbolKind.Boolean = 17;
		SymbolKind.Array = 18;
		SymbolKind.Object = 19;
		SymbolKind.Key = 20;
		SymbolKind.Null = 21;
		SymbolKind.EnumMember = 22;
		SymbolKind.Struct = 23;
		SymbolKind.Event = 24;
		SymbolKind.Operator = 25;
		SymbolKind.TypeParameter = 26;
	})(SymbolKind || (SymbolKind = {}));
	(function(SymbolTag) {
		/**
		* Render a symbol as obsolete, usually using a strike-out.
		*/
		SymbolTag.Deprecated = 1;
	})(SymbolTag || (SymbolTag = {}));
	(function(SymbolInformation) {
		/**
		* Creates a new symbol information literal.
		*
		* @param name The name of the symbol.
		* @param kind The kind of the symbol.
		* @param range The range of the location of the symbol.
		* @param uri The resource of the location of symbol.
		* @param containerName The name of the symbol containing the symbol.
		*/
		function create(name, kind, range, uri, containerName) {
			let result = {
				name,
				kind,
				location: {
					uri,
					range
				}
			};
			if (containerName) result.containerName = containerName;
			return result;
		}
		SymbolInformation.create = create;
	})(SymbolInformation || (SymbolInformation = {}));
	(function(WorkspaceSymbol) {
		/**
		* Create a new workspace symbol.
		*
		* @param name The name of the symbol.
		* @param kind The kind of the symbol.
		* @param uri The resource of the location of the symbol.
		* @param range An options range of the location.
		* @returns A WorkspaceSymbol.
		*/
		function create(name, kind, uri, range) {
			return range !== void 0 ? {
				name,
				kind,
				location: {
					uri,
					range
				}
			} : {
				name,
				kind,
				location: { uri }
			};
		}
		WorkspaceSymbol.create = create;
	})(WorkspaceSymbol || (WorkspaceSymbol = {}));
	(function(DocumentSymbol) {
		/**
		* Creates a new symbol information literal.
		*
		* @param name The name of the symbol.
		* @param detail The detail of the symbol.
		* @param kind The kind of the symbol.
		* @param range The range of the symbol.
		* @param selectionRange The selectionRange of the symbol.
		* @param children Children of the symbol.
		*/
		function create(name, detail, kind, range, selectionRange, children) {
			let result = {
				name,
				detail,
				kind,
				range,
				selectionRange
			};
			if (children !== void 0) result.children = children;
			return result;
		}
		DocumentSymbol.create = create;
		/**
		* Checks whether the given literal conforms to the {@link DocumentSymbol} interface.
		*/
		function is(value) {
			let candidate = value;
			return candidate && Is.string(candidate.name) && Is.number(candidate.kind) && Range.is(candidate.range) && Range.is(candidate.selectionRange) && (candidate.detail === void 0 || Is.string(candidate.detail)) && (candidate.deprecated === void 0 || Is.boolean(candidate.deprecated)) && (candidate.children === void 0 || Array.isArray(candidate.children)) && (candidate.tags === void 0 || Array.isArray(candidate.tags));
		}
		DocumentSymbol.is = is;
	})(DocumentSymbol || (DocumentSymbol = {}));
	(function(CodeActionKind) {
		/**
		* Empty kind.
		*/
		CodeActionKind.Empty = "";
		/**
		* Base kind for quickfix actions: 'quickfix'
		*/
		CodeActionKind.QuickFix = "quickfix";
		/**
		* Base kind for refactoring actions: 'refactor'
		*/
		CodeActionKind.Refactor = "refactor";
		/**
		* Base kind for refactoring extraction actions: 'refactor.extract'
		*
		* Example extract actions:
		*
		* - Extract method
		* - Extract function
		* - Extract variable
		* - Extract interface from class
		* - ...
		*/
		CodeActionKind.RefactorExtract = "refactor.extract";
		/**
		* Base kind for refactoring inline actions: 'refactor.inline'
		*
		* Example inline actions:
		*
		* - Inline function
		* - Inline variable
		* - Inline constant
		* - ...
		*/
		CodeActionKind.RefactorInline = "refactor.inline";
		/**
		* Base kind for refactoring rewrite actions: 'refactor.rewrite'
		*
		* Example rewrite actions:
		*
		* - Convert JavaScript function to class
		* - Add or remove parameter
		* - Encapsulate field
		* - Make method static
		* - Move method to base class
		* - ...
		*/
		CodeActionKind.RefactorRewrite = "refactor.rewrite";
		/**
		* Base kind for source actions: `source`
		*
		* Source code actions apply to the entire file.
		*/
		CodeActionKind.Source = "source";
		/**
		* Base kind for an organize imports source action: `source.organizeImports`
		*/
		CodeActionKind.SourceOrganizeImports = "source.organizeImports";
		/**
		* Base kind for auto-fix source actions: `source.fixAll`.
		*
		* Fix all actions automatically fix errors that have a clear fix that do not require user input.
		* They should not suppress errors or perform unsafe fixes such as generating new types or classes.
		*
		* @since 3.15.0
		*/
		CodeActionKind.SourceFixAll = "source.fixAll";
	})(CodeActionKind || (CodeActionKind = {}));
	(function(CodeActionTriggerKind) {
		/**
		* Code actions were explicitly requested by the user or by an extension.
		*/
		CodeActionTriggerKind.Invoked = 1;
		/**
		* Code actions were requested automatically.
		*
		* This typically happens when current selection in a file changes, but can
		* also be triggered when file content changes.
		*/
		CodeActionTriggerKind.Automatic = 2;
	})(CodeActionTriggerKind || (CodeActionTriggerKind = {}));
	(function(CodeActionContext) {
		/**
		* Creates a new CodeActionContext literal.
		*/
		function create(diagnostics, only, triggerKind) {
			let result = { diagnostics };
			if (only !== void 0 && only !== null) result.only = only;
			if (triggerKind !== void 0 && triggerKind !== null) result.triggerKind = triggerKind;
			return result;
		}
		CodeActionContext.create = create;
		/**
		* Checks whether the given literal conforms to the {@link CodeActionContext} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Is.typedArray(candidate.diagnostics, Diagnostic.is) && (candidate.only === void 0 || Is.typedArray(candidate.only, Is.string)) && (candidate.triggerKind === void 0 || candidate.triggerKind === CodeActionTriggerKind.Invoked || candidate.triggerKind === CodeActionTriggerKind.Automatic);
		}
		CodeActionContext.is = is;
	})(CodeActionContext || (CodeActionContext = {}));
	(function(CodeAction) {
		function create(title, kindOrCommandOrEdit, kind) {
			let result = { title };
			let checkKind = true;
			if (typeof kindOrCommandOrEdit === "string") {
				checkKind = false;
				result.kind = kindOrCommandOrEdit;
			} else if (Command.is(kindOrCommandOrEdit)) result.command = kindOrCommandOrEdit;
			else result.edit = kindOrCommandOrEdit;
			if (checkKind && kind !== void 0) result.kind = kind;
			return result;
		}
		CodeAction.create = create;
		function is(value) {
			let candidate = value;
			return candidate && Is.string(candidate.title) && (candidate.diagnostics === void 0 || Is.typedArray(candidate.diagnostics, Diagnostic.is)) && (candidate.kind === void 0 || Is.string(candidate.kind)) && (candidate.edit !== void 0 || candidate.command !== void 0) && (candidate.command === void 0 || Command.is(candidate.command)) && (candidate.isPreferred === void 0 || Is.boolean(candidate.isPreferred)) && (candidate.edit === void 0 || WorkspaceEdit.is(candidate.edit));
		}
		CodeAction.is = is;
	})(CodeAction || (CodeAction = {}));
	(function(CodeLens) {
		/**
		* Creates a new CodeLens literal.
		*/
		function create(range, data) {
			let result = { range };
			if (Is.defined(data)) result.data = data;
			return result;
		}
		CodeLens.create = create;
		/**
		* Checks whether the given literal conforms to the {@link CodeLens} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Range.is(candidate.range) && (Is.undefined(candidate.command) || Command.is(candidate.command));
		}
		CodeLens.is = is;
	})(CodeLens || (CodeLens = {}));
	(function(FormattingOptions) {
		/**
		* Creates a new FormattingOptions literal.
		*/
		function create(tabSize, insertSpaces) {
			return {
				tabSize,
				insertSpaces
			};
		}
		FormattingOptions.create = create;
		/**
		* Checks whether the given literal conforms to the {@link FormattingOptions} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Is.uinteger(candidate.tabSize) && Is.boolean(candidate.insertSpaces);
		}
		FormattingOptions.is = is;
	})(FormattingOptions || (FormattingOptions = {}));
	(function(DocumentLink) {
		/**
		* Creates a new DocumentLink literal.
		*/
		function create(range, target, data) {
			return {
				range,
				target,
				data
			};
		}
		DocumentLink.create = create;
		/**
		* Checks whether the given literal conforms to the {@link DocumentLink} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Range.is(candidate.range) && (Is.undefined(candidate.target) || Is.string(candidate.target));
		}
		DocumentLink.is = is;
	})(DocumentLink || (DocumentLink = {}));
	(function(SelectionRange) {
		/**
		* Creates a new SelectionRange
		* @param range the range.
		* @param parent an optional parent.
		*/
		function create(range, parent) {
			return {
				range,
				parent
			};
		}
		SelectionRange.create = create;
		function is(value) {
			let candidate = value;
			return Is.objectLiteral(candidate) && Range.is(candidate.range) && (candidate.parent === void 0 || SelectionRange.is(candidate.parent));
		}
		SelectionRange.is = is;
	})(SelectionRange || (SelectionRange = {}));
	(function(SemanticTokenTypes) {
		SemanticTokenTypes["namespace"] = "namespace";
		/**
		* Represents a generic type. Acts as a fallback for types which can't be mapped to
		* a specific type like class or enum.
		*/
		SemanticTokenTypes["type"] = "type";
		SemanticTokenTypes["class"] = "class";
		SemanticTokenTypes["enum"] = "enum";
		SemanticTokenTypes["interface"] = "interface";
		SemanticTokenTypes["struct"] = "struct";
		SemanticTokenTypes["typeParameter"] = "typeParameter";
		SemanticTokenTypes["parameter"] = "parameter";
		SemanticTokenTypes["variable"] = "variable";
		SemanticTokenTypes["property"] = "property";
		SemanticTokenTypes["enumMember"] = "enumMember";
		SemanticTokenTypes["event"] = "event";
		SemanticTokenTypes["function"] = "function";
		SemanticTokenTypes["method"] = "method";
		SemanticTokenTypes["macro"] = "macro";
		SemanticTokenTypes["keyword"] = "keyword";
		SemanticTokenTypes["modifier"] = "modifier";
		SemanticTokenTypes["comment"] = "comment";
		SemanticTokenTypes["string"] = "string";
		SemanticTokenTypes["number"] = "number";
		SemanticTokenTypes["regexp"] = "regexp";
		SemanticTokenTypes["operator"] = "operator";
		/**
		* @since 3.17.0
		*/
		SemanticTokenTypes["decorator"] = "decorator";
	})(SemanticTokenTypes || (SemanticTokenTypes = {}));
	(function(SemanticTokenModifiers) {
		SemanticTokenModifiers["declaration"] = "declaration";
		SemanticTokenModifiers["definition"] = "definition";
		SemanticTokenModifiers["readonly"] = "readonly";
		SemanticTokenModifiers["static"] = "static";
		SemanticTokenModifiers["deprecated"] = "deprecated";
		SemanticTokenModifiers["abstract"] = "abstract";
		SemanticTokenModifiers["async"] = "async";
		SemanticTokenModifiers["modification"] = "modification";
		SemanticTokenModifiers["documentation"] = "documentation";
		SemanticTokenModifiers["defaultLibrary"] = "defaultLibrary";
	})(SemanticTokenModifiers || (SemanticTokenModifiers = {}));
	(function(SemanticTokens) {
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && (candidate.resultId === void 0 || typeof candidate.resultId === "string") && Array.isArray(candidate.data) && (candidate.data.length === 0 || typeof candidate.data[0] === "number");
		}
		SemanticTokens.is = is;
	})(SemanticTokens || (SemanticTokens = {}));
	(function(InlineValueText) {
		/**
		* Creates a new InlineValueText literal.
		*/
		function create(range, text) {
			return {
				range,
				text
			};
		}
		InlineValueText.create = create;
		function is(value) {
			const candidate = value;
			return candidate !== void 0 && candidate !== null && Range.is(candidate.range) && Is.string(candidate.text);
		}
		InlineValueText.is = is;
	})(InlineValueText || (InlineValueText = {}));
	(function(InlineValueVariableLookup) {
		/**
		* Creates a new InlineValueText literal.
		*/
		function create(range, variableName, caseSensitiveLookup) {
			return {
				range,
				variableName,
				caseSensitiveLookup
			};
		}
		InlineValueVariableLookup.create = create;
		function is(value) {
			const candidate = value;
			return candidate !== void 0 && candidate !== null && Range.is(candidate.range) && Is.boolean(candidate.caseSensitiveLookup) && (Is.string(candidate.variableName) || candidate.variableName === void 0);
		}
		InlineValueVariableLookup.is = is;
	})(InlineValueVariableLookup || (InlineValueVariableLookup = {}));
	(function(InlineValueEvaluatableExpression) {
		/**
		* Creates a new InlineValueEvaluatableExpression literal.
		*/
		function create(range, expression) {
			return {
				range,
				expression
			};
		}
		InlineValueEvaluatableExpression.create = create;
		function is(value) {
			const candidate = value;
			return candidate !== void 0 && candidate !== null && Range.is(candidate.range) && (Is.string(candidate.expression) || candidate.expression === void 0);
		}
		InlineValueEvaluatableExpression.is = is;
	})(InlineValueEvaluatableExpression || (InlineValueEvaluatableExpression = {}));
	(function(InlineValueContext) {
		/**
		* Creates a new InlineValueContext literal.
		*/
		function create(frameId, stoppedLocation) {
			return {
				frameId,
				stoppedLocation
			};
		}
		InlineValueContext.create = create;
		/**
		* Checks whether the given literal conforms to the {@link InlineValueContext} interface.
		*/
		function is(value) {
			const candidate = value;
			return Is.defined(candidate) && Range.is(value.stoppedLocation);
		}
		InlineValueContext.is = is;
	})(InlineValueContext || (InlineValueContext = {}));
	(function(InlayHintKind) {
		/**
		* An inlay hint that for a type annotation.
		*/
		InlayHintKind.Type = 1;
		/**
		* An inlay hint that is for a parameter.
		*/
		InlayHintKind.Parameter = 2;
		function is(value) {
			return value === 1 || value === 2;
		}
		InlayHintKind.is = is;
	})(InlayHintKind || (InlayHintKind = {}));
	(function(InlayHintLabelPart) {
		function create(value) {
			return { value };
		}
		InlayHintLabelPart.create = create;
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && (candidate.tooltip === void 0 || Is.string(candidate.tooltip) || MarkupContent.is(candidate.tooltip)) && (candidate.location === void 0 || Location.is(candidate.location)) && (candidate.command === void 0 || Command.is(candidate.command));
		}
		InlayHintLabelPart.is = is;
	})(InlayHintLabelPart || (InlayHintLabelPart = {}));
	(function(InlayHint) {
		function create(position, label, kind) {
			const result = {
				position,
				label
			};
			if (kind !== void 0) result.kind = kind;
			return result;
		}
		InlayHint.create = create;
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && Position.is(candidate.position) && (Is.string(candidate.label) || Is.typedArray(candidate.label, InlayHintLabelPart.is)) && (candidate.kind === void 0 || InlayHintKind.is(candidate.kind)) && candidate.textEdits === void 0 || Is.typedArray(candidate.textEdits, TextEdit.is) && (candidate.tooltip === void 0 || Is.string(candidate.tooltip) || MarkupContent.is(candidate.tooltip)) && (candidate.paddingLeft === void 0 || Is.boolean(candidate.paddingLeft)) && (candidate.paddingRight === void 0 || Is.boolean(candidate.paddingRight));
		}
		InlayHint.is = is;
	})(InlayHint || (InlayHint = {}));
	(function(StringValue) {
		function createSnippet(value) {
			return {
				kind: "snippet",
				value
			};
		}
		StringValue.createSnippet = createSnippet;
	})(StringValue || (StringValue = {}));
	(function(InlineCompletionItem) {
		function create(insertText, filterText, range, command) {
			return {
				insertText,
				filterText,
				range,
				command
			};
		}
		InlineCompletionItem.create = create;
	})(InlineCompletionItem || (InlineCompletionItem = {}));
	(function(InlineCompletionList) {
		function create(items) {
			return { items };
		}
		InlineCompletionList.create = create;
	})(InlineCompletionList || (InlineCompletionList = {}));
	(function(InlineCompletionTriggerKind) {
		/**
		* Completion was triggered explicitly by a user gesture.
		*/
		InlineCompletionTriggerKind.Invoked = 0;
		/**
		* Completion was triggered automatically while editing.
		*/
		InlineCompletionTriggerKind.Automatic = 1;
	})(InlineCompletionTriggerKind || (InlineCompletionTriggerKind = {}));
	(function(SelectedCompletionInfo) {
		function create(range, text) {
			return {
				range,
				text
			};
		}
		SelectedCompletionInfo.create = create;
	})(SelectedCompletionInfo || (SelectedCompletionInfo = {}));
	(function(InlineCompletionContext) {
		function create(triggerKind, selectedCompletionInfo) {
			return {
				triggerKind,
				selectedCompletionInfo
			};
		}
		InlineCompletionContext.create = create;
	})(InlineCompletionContext || (InlineCompletionContext = {}));
	(function(WorkspaceFolder) {
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && URI$1.is(candidate.uri) && Is.string(candidate.name);
		}
		WorkspaceFolder.is = is;
	})(WorkspaceFolder || (WorkspaceFolder = {}));
	EOL = [
		"\n",
		"\r\n",
		"\r"
	];
	(function(TextDocument) {
		/**
		* Creates a new ITextDocument literal from the given uri and content.
		* @param uri The document's uri.
		* @param languageId The document's language Id.
		* @param version The document's version.
		* @param content The document's content.
		*/
		function create(uri, languageId, version, content) {
			return new FullTextDocument$1(uri, languageId, version, content);
		}
		TextDocument.create = create;
		/**
		* Checks whether the given literal conforms to the {@link ITextDocument} interface.
		*/
		function is(value) {
			let candidate = value;
			return Is.defined(candidate) && Is.string(candidate.uri) && (Is.undefined(candidate.languageId) || Is.string(candidate.languageId)) && Is.uinteger(candidate.lineCount) && Is.func(candidate.getText) && Is.func(candidate.positionAt) && Is.func(candidate.offsetAt) ? true : false;
		}
		TextDocument.is = is;
		function applyEdits(document, edits) {
			let text = document.getText();
			let sortedEdits = mergeSort(edits, (a, b) => {
				let diff = a.range.start.line - b.range.start.line;
				if (diff === 0) return a.range.start.character - b.range.start.character;
				return diff;
			});
			let lastModifiedOffset = text.length;
			for (let i = sortedEdits.length - 1; i >= 0; i--) {
				let e = sortedEdits[i];
				let startOffset = document.offsetAt(e.range.start);
				let endOffset = document.offsetAt(e.range.end);
				if (endOffset <= lastModifiedOffset) text = text.substring(0, startOffset) + e.newText + text.substring(endOffset, text.length);
				else throw new Error("Overlapping edit");
				lastModifiedOffset = startOffset;
			}
			return text;
		}
		TextDocument.applyEdits = applyEdits;
		function mergeSort(data, compare) {
			if (data.length <= 1) return data;
			const p = data.length / 2 | 0;
			const left = data.slice(0, p);
			const right = data.slice(p);
			mergeSort(left, compare);
			mergeSort(right, compare);
			let leftIdx = 0;
			let rightIdx = 0;
			let i = 0;
			while (leftIdx < left.length && rightIdx < right.length) if (compare(left[leftIdx], right[rightIdx]) <= 0) data[i++] = left[leftIdx++];
			else data[i++] = right[rightIdx++];
			while (leftIdx < left.length) data[i++] = left[leftIdx++];
			while (rightIdx < right.length) data[i++] = right[rightIdx++];
			return data;
		}
	})(TextDocument$1 || (TextDocument$1 = {}));
	FullTextDocument$1 = class {
		constructor(uri, languageId, version, content) {
			this._uri = uri;
			this._languageId = languageId;
			this._version = version;
			this._content = content;
			this._lineOffsets = void 0;
		}
		get uri() {
			return this._uri;
		}
		get languageId() {
			return this._languageId;
		}
		get version() {
			return this._version;
		}
		getText(range) {
			if (range) {
				let start = this.offsetAt(range.start);
				let end = this.offsetAt(range.end);
				return this._content.substring(start, end);
			}
			return this._content;
		}
		update(event, version) {
			this._content = event.text;
			this._version = version;
			this._lineOffsets = void 0;
		}
		getLineOffsets() {
			if (this._lineOffsets === void 0) {
				let lineOffsets = [];
				let text = this._content;
				let isLineStart = true;
				for (let i = 0; i < text.length; i++) {
					if (isLineStart) {
						lineOffsets.push(i);
						isLineStart = false;
					}
					let ch = text.charAt(i);
					isLineStart = ch === "\r" || ch === "\n";
					if (ch === "\r" && i + 1 < text.length && text.charAt(i + 1) === "\n") i++;
				}
				if (isLineStart && text.length > 0) lineOffsets.push(text.length);
				this._lineOffsets = lineOffsets;
			}
			return this._lineOffsets;
		}
		positionAt(offset) {
			offset = Math.max(Math.min(offset, this._content.length), 0);
			let lineOffsets = this.getLineOffsets();
			let low = 0, high = lineOffsets.length;
			if (high === 0) return Position.create(0, offset);
			while (low < high) {
				let mid = Math.floor((low + high) / 2);
				if (lineOffsets[mid] > offset) high = mid;
				else low = mid + 1;
			}
			let line = low - 1;
			return Position.create(line, offset - lineOffsets[line]);
		}
		offsetAt(position) {
			let lineOffsets = this.getLineOffsets();
			if (position.line >= lineOffsets.length) return this._content.length;
			else if (position.line < 0) return 0;
			let lineOffset = lineOffsets[position.line];
			let nextLineOffset = position.line + 1 < lineOffsets.length ? lineOffsets[position.line + 1] : this._content.length;
			return Math.max(Math.min(lineOffset + position.character, nextLineOffset), lineOffset);
		}
		get lineCount() {
			return this.getLineOffsets().length;
		}
	};
	(function(Is) {
		const toString = Object.prototype.toString;
		function defined(value) {
			return typeof value !== "undefined";
		}
		Is.defined = defined;
		function undefined(value) {
			return typeof value === "undefined";
		}
		Is.undefined = undefined;
		function boolean(value) {
			return value === true || value === false;
		}
		Is.boolean = boolean;
		function string(value) {
			return toString.call(value) === "[object String]";
		}
		Is.string = string;
		function number(value) {
			return toString.call(value) === "[object Number]";
		}
		Is.number = number;
		function numberRange(value, min, max) {
			return toString.call(value) === "[object Number]" && min <= value && value <= max;
		}
		Is.numberRange = numberRange;
		function integer(value) {
			return toString.call(value) === "[object Number]" && -2147483648 <= value && value <= 2147483647;
		}
		Is.integer = integer;
		function uinteger(value) {
			return toString.call(value) === "[object Number]" && 0 <= value && value <= 2147483647;
		}
		Is.uinteger = uinteger;
		function func(value) {
			return toString.call(value) === "[object Function]";
		}
		Is.func = func;
		function objectLiteral(value) {
			return value !== null && typeof value === "object";
		}
		Is.objectLiteral = objectLiteral;
		function typedArray(value, check) {
			return Array.isArray(value) && value.every(check);
		}
		Is.typedArray = typedArray;
	})(Is || (Is = {}));
}));
init_main();
var CstNodeBuilder = class {
	constructor() {
		this.nodeStack = [];
	}
	get current() {
		return this.nodeStack[this.nodeStack.length - 1] ?? this.rootNode;
	}
	buildRootNode(input) {
		this.rootNode = new RootCstNodeImpl(input);
		this.rootNode.root = this.rootNode;
		this.nodeStack = [this.rootNode];
		return this.rootNode;
	}
	buildCompositeNode(feature) {
		const compositeNode = new CompositeCstNodeImpl();
		compositeNode.grammarSource = feature;
		compositeNode.root = this.rootNode;
		this.current.content.push(compositeNode);
		this.nodeStack.push(compositeNode);
		return compositeNode;
	}
	buildLeafNode(token, feature) {
		const leafNode = new LeafCstNodeImpl(token.startOffset, token.image.length, tokenToRange(token), token.tokenType, !feature);
		leafNode.grammarSource = feature;
		leafNode.root = this.rootNode;
		this.current.content.push(leafNode);
		return leafNode;
	}
	removeNode(node) {
		const parent = node.container;
		if (parent) {
			const index = parent.content.indexOf(node);
			if (index >= 0) parent.content.splice(index, 1);
		}
	}
	addHiddenNodes(tokens) {
		const nodes = [];
		for (const token of tokens) {
			const leafNode = new LeafCstNodeImpl(token.startOffset, token.image.length, tokenToRange(token), token.tokenType, true);
			leafNode.root = this.rootNode;
			nodes.push(leafNode);
		}
		let current = this.current;
		let added = false;
		if (current.content.length > 0) {
			current.content.push(...nodes);
			return;
		}
		while (current.container) {
			const index = current.container.content.indexOf(current);
			if (index > 0) {
				current.container.content.splice(index, 0, ...nodes);
				added = true;
				break;
			}
			current = current.container;
		}
		if (!added) this.rootNode.content.unshift(...nodes);
	}
	construct(item) {
		const current = this.current;
		if (typeof item.$type === "string" && !item.$infixName) this.current.astNode = item;
		item.$cstNode = current;
		const node = this.nodeStack.pop();
		if (node?.content.length === 0) this.removeNode(node);
	}
};
var AbstractCstNode = class {
	get hidden() {
		return false;
	}
	get astNode() {
		const node = typeof this._astNode?.$type === "string" ? this._astNode : this.container?.astNode;
		if (!node) throw new Error("This node has no associated AST element");
		return node;
	}
	set astNode(value) {
		this._astNode = value;
	}
	get text() {
		return this.root.fullText.substring(this.offset, this.end);
	}
};
var LeafCstNodeImpl = class extends AbstractCstNode {
	get offset() {
		return this._offset;
	}
	get length() {
		return this._length;
	}
	get end() {
		return this._offset + this._length;
	}
	get hidden() {
		return this._hidden;
	}
	get tokenType() {
		return this._tokenType;
	}
	get range() {
		return this._range;
	}
	constructor(offset, length, range, tokenType, hidden = false) {
		super();
		this._hidden = hidden;
		this._offset = offset;
		this._tokenType = tokenType;
		this._length = length;
		this._range = range;
	}
};
var CompositeCstNodeImpl = class extends AbstractCstNode {
	constructor() {
		super(...arguments);
		this.content = new CstNodeContainer(this);
	}
	get offset() {
		return this.firstNonHiddenNode?.offset ?? 0;
	}
	get length() {
		return this.end - this.offset;
	}
	get end() {
		return this.lastNonHiddenNode?.end ?? 0;
	}
	get range() {
		const firstNode = this.firstNonHiddenNode;
		const lastNode = this.lastNonHiddenNode;
		if (firstNode && lastNode) {
			if (this._rangeCache === void 0) {
				const { range: firstRange } = firstNode;
				const { range: lastRange } = lastNode;
				this._rangeCache = {
					start: firstRange.start,
					end: lastRange.end.line < firstRange.start.line ? firstRange.start : lastRange.end
				};
			}
			return this._rangeCache;
		} else return {
			start: Position.create(0, 0),
			end: Position.create(0, 0)
		};
	}
	get firstNonHiddenNode() {
		for (const child of this.content) if (!child.hidden) return child;
		return this.content[0];
	}
	get lastNonHiddenNode() {
		for (let i = this.content.length - 1; i >= 0; i--) {
			const child = this.content[i];
			if (!child.hidden) return child;
		}
		return this.content[this.content.length - 1];
	}
};
var CstNodeContainer = class CstNodeContainer extends Array {
	constructor(parent) {
		super();
		this.parent = parent;
		Object.setPrototypeOf(this, CstNodeContainer.prototype);
	}
	push(...items) {
		this.addParents(items);
		return super.push(...items);
	}
	unshift(...items) {
		this.addParents(items);
		return super.unshift(...items);
	}
	splice(start, count, ...items) {
		this.addParents(items);
		return super.splice(start, count, ...items);
	}
	addParents(items) {
		for (const item of items) item.container = this.parent;
	}
};
var RootCstNodeImpl = class extends CompositeCstNodeImpl {
	get text() {
		return this._text.substring(this.offset, this.end);
	}
	get fullText() {
		return this._text;
	}
	constructor(input) {
		super();
		this._text = "";
		this._text = input ?? "";
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DatatypeSymbol = Symbol("Datatype");
function isDataTypeNode(node) {
	return node.$type === DatatypeSymbol;
}
var ruleSuffix = "​";
var withRuleSuffix = (name) => name.endsWith(ruleSuffix) ? name : name + ruleSuffix;
var AbstractLangiumParser = class {
	constructor(services, incomplete) {
		this._unorderedGroups = /* @__PURE__ */ new Map();
		this.allRules = /* @__PURE__ */ new Map();
		this.lexer = services.parser.Lexer;
		const tokens = this.lexer.definition;
		const production = services.LanguageMetaData.mode === "production";
		if (services.shared.profilers.LangiumProfiler?.isActive("parsing")) this.wrapper = new ProfilerWrapper(tokens, {
			...services.parser.ParserConfig,
			skipValidations: production,
			errorMessageProvider: services.parser.ParserErrorMessageProvider
		}, incomplete, services.shared.profilers.LangiumProfiler.createTask("parsing", services.LanguageMetaData.languageId));
		else this.wrapper = new ChevrotainWrapper(tokens, {
			...services.parser.ParserConfig,
			skipValidations: production,
			errorMessageProvider: services.parser.ParserErrorMessageProvider
		}, incomplete);
	}
	alternatives(idx, choices) {
		this.wrapper.wrapOr(idx, choices);
	}
	optional(idx, callback) {
		this.wrapper.wrapOption(idx, callback);
	}
	many(idx, callback) {
		this.wrapper.wrapMany(idx, callback);
	}
	atLeastOne(idx, callback) {
		this.wrapper.wrapAtLeastOne(idx, callback);
	}
	getRule(name) {
		return this.allRules.get(name);
	}
	isRecording() {
		return this.wrapper.IS_RECORDING;
	}
	get unorderedGroups() {
		return this._unorderedGroups;
	}
	getRuleStack() {
		return this.wrapper.RULE_STACK;
	}
	finalize() {
		this.wrapper.wrapSelfAnalysis();
	}
};
var LangiumParser = class extends AbstractLangiumParser {
	get current() {
		return this.stack[this.stack.length - 1];
	}
	constructor(services) {
		super(services, false);
		this.nodeBuilder = new CstNodeBuilder();
		this.stack = [];
		this.assignmentMap = /* @__PURE__ */ new Map();
		this.operatorPrecedence = /* @__PURE__ */ new Map();
		this.linker = services.references.Linker;
		this.converter = services.parser.ValueConverter;
		this.astReflection = services.shared.AstReflection;
	}
	rule(rule, impl) {
		const type = this.computeRuleType(rule);
		let infixName = void 0;
		if (isInfixRule(rule)) {
			infixName = rule.name;
			this.registerPrecedenceMap(rule);
		}
		const ruleMethod = this.wrapper.DEFINE_RULE(withRuleSuffix(rule.name), this.startImplementation(type, infixName, impl).bind(this));
		this.allRules.set(rule.name, ruleMethod);
		if (isParserRule(rule) && rule.entry) this.mainRule = ruleMethod;
		return ruleMethod;
	}
	registerPrecedenceMap(rule) {
		const name = rule.name;
		const map = /* @__PURE__ */ new Map();
		for (let i = 0; i < rule.operators.precedences.length; i++) {
			const precedence = rule.operators.precedences[i];
			for (const keyword of precedence.operators) map.set(keyword.value, {
				precedence: i,
				rightAssoc: precedence.associativity === "right"
			});
		}
		this.operatorPrecedence.set(name, map);
	}
	computeRuleType(rule) {
		if (isInfixRule(rule)) return getTypeName(rule);
		else if (rule.fragment) return;
		else if (isDataTypeRule(rule)) return DatatypeSymbol;
		else return getTypeName(rule);
	}
	parse(input, options = {}) {
		this.nodeBuilder.buildRootNode(input);
		const lexerResult = this.lexerResult = this.lexer.tokenize(input);
		this.wrapper.input = lexerResult.tokens;
		const ruleMethod = options.rule ? this.allRules.get(options.rule) : this.mainRule;
		if (!ruleMethod) throw new Error(options.rule ? `No rule found with name '${options.rule}'` : "No main rule available.");
		const result = this.doParse(ruleMethod);
		this.nodeBuilder.addHiddenNodes(lexerResult.hidden);
		this.unorderedGroups.clear();
		this.lexerResult = void 0;
		linkContentToContainer(result, { deep: true });
		return {
			value: result,
			lexerErrors: lexerResult.errors,
			lexerReport: lexerResult.report,
			parserErrors: this.wrapper.errors
		};
	}
	doParse(rule) {
		let result = this.wrapper.rule(rule);
		if (this.stack.length > 0) result = this.construct();
		if (result === void 0) throw new Error("No result from parser");
		else if (this.stack.length > 0) throw new Error("Parser stack is not empty after parsing");
		return result;
	}
	startImplementation($type, infixName, implementation) {
		return (args) => {
			const createNode = !this.isRecording() && $type !== void 0;
			if (createNode) {
				const node = { $type };
				this.stack.push(node);
				if ($type === DatatypeSymbol) node.value = "";
				else if (infixName !== void 0) node.$infixName = infixName;
			}
			implementation(args);
			return createNode ? this.construct() : void 0;
		};
	}
	extractHiddenTokens(token) {
		const hiddenTokens = this.lexerResult.hidden;
		if (!hiddenTokens.length) return [];
		const offset = token.startOffset;
		for (let i = 0; i < hiddenTokens.length; i++) if (hiddenTokens[i].startOffset > offset) return hiddenTokens.splice(0, i);
		return hiddenTokens.splice(0, hiddenTokens.length);
	}
	consume(idx, tokenType, feature) {
		const token = this.wrapper.wrapConsume(idx, tokenType);
		if (!this.isRecording() && this.isValidToken(token)) {
			const hiddenTokens = this.extractHiddenTokens(token);
			this.nodeBuilder.addHiddenNodes(hiddenTokens);
			const leafNode = this.nodeBuilder.buildLeafNode(token, feature);
			const { assignment, crossRef } = this.getAssignment(feature);
			const current = this.current;
			if (assignment) {
				const convertedValue = isKeyword(feature) ? token.image : this.converter.convert(token.image, leafNode);
				this.assign(assignment.operator, assignment.feature, convertedValue, leafNode, crossRef);
			} else if (isDataTypeNode(current)) {
				let text = token.image;
				if (!isKeyword(feature)) text = this.converter.convert(text, leafNode).toString();
				current.value += text;
			}
		}
	}
	/**
	* Most consumed parser tokens are valid. However there are two cases in which they are not valid:
	*
	* 1. They were inserted during error recovery by the parser. These tokens don't really exist and should not be further processed
	* 2. They contain invalid token ranges. This might include the special EOF token, or other tokens produced by invalid token builders.
	*/
	isValidToken(token) {
		return !token.isInsertedInRecovery && !isNaN(token.startOffset) && typeof token.endOffset === "number" && !isNaN(token.endOffset);
	}
	subrule(idx, rule, fragment, feature, args) {
		let cstNode;
		if (!this.isRecording() && !fragment) cstNode = this.nodeBuilder.buildCompositeNode(feature);
		let result;
		try {
			result = this.wrapper.wrapSubrule(idx, rule, args);
		} finally {
			if (!this.isRecording()) {
				if (result === void 0 && !fragment) result = this.construct();
				if (result !== void 0 && cstNode && cstNode.length > 0) this.performSubruleAssignment(result, feature, cstNode);
			}
		}
	}
	performSubruleAssignment(result, feature, cstNode) {
		const { assignment, crossRef } = this.getAssignment(feature);
		if (assignment) this.assign(assignment.operator, assignment.feature, result, cstNode, crossRef);
		else if (!assignment) {
			const current = this.current;
			if (isDataTypeNode(current)) current.value += result.toString();
			else if (typeof result === "object" && result) {
				const newItem = this.assignWithoutOverride(result, current);
				this.stack.pop();
				this.stack.push(newItem);
			}
		}
	}
	action($type, action) {
		if (!this.isRecording()) {
			let last = this.current;
			if (action.feature && action.operator) {
				last = this.construct();
				this.nodeBuilder.removeNode(last.$cstNode);
				this.nodeBuilder.buildCompositeNode(action).content.push(last.$cstNode);
				const newItem = { $type };
				this.stack.push(newItem);
				this.assign(action.operator, action.feature, last, last.$cstNode);
			} else last.$type = $type;
		}
	}
	construct() {
		if (this.isRecording()) return;
		const obj = this.stack.pop();
		this.nodeBuilder.construct(obj);
		if ("$infixName" in obj) return this.constructInfix(obj, this.operatorPrecedence.get(obj.$infixName));
		else if (isDataTypeNode(obj)) return this.converter.convert(obj.value, obj.$cstNode);
		else assignMandatoryProperties(this.astReflection, obj);
		return obj;
	}
	constructInfix(obj, precedence) {
		const parts = obj.parts;
		if (!Array.isArray(parts) || parts.length === 0) return;
		const operators = obj.operators;
		if (!Array.isArray(operators) || parts.length < 2) return parts[0];
		let lowestPrecedenceIdx = 0;
		let lowestPrecedenceValue = -1;
		for (let i = 0; i < operators.length; i++) {
			const operator = operators[i];
			const opPrecedence = precedence.get(operator) ?? {
				precedence: Infinity,
				rightAssoc: false
			};
			if (opPrecedence.precedence > lowestPrecedenceValue) {
				lowestPrecedenceValue = opPrecedence.precedence;
				lowestPrecedenceIdx = i;
			} else if (opPrecedence.precedence === lowestPrecedenceValue) {
				if (!opPrecedence.rightAssoc) lowestPrecedenceIdx = i;
			}
		}
		const leftOperators = operators.slice(0, lowestPrecedenceIdx);
		const rightOperators = operators.slice(lowestPrecedenceIdx + 1);
		const leftParts = parts.slice(0, lowestPrecedenceIdx + 1);
		const rightParts = parts.slice(lowestPrecedenceIdx + 1);
		const leftInfix = {
			$infixName: obj.$infixName,
			$type: obj.$type,
			$cstNode: obj.$cstNode,
			parts: leftParts,
			operators: leftOperators
		};
		const rightInfix = {
			$infixName: obj.$infixName,
			$type: obj.$type,
			$cstNode: obj.$cstNode,
			parts: rightParts,
			operators: rightOperators
		};
		const leftTree = this.constructInfix(leftInfix, precedence);
		const rightTree = this.constructInfix(rightInfix, precedence);
		return {
			$type: obj.$type,
			$cstNode: obj.$cstNode,
			left: leftTree,
			operator: operators[lowestPrecedenceIdx],
			right: rightTree
		};
	}
	getAssignment(feature) {
		if (!this.assignmentMap.has(feature)) {
			const assignment = getContainerOfType(feature, isAssignment);
			this.assignmentMap.set(feature, {
				assignment,
				crossRef: assignment && isCrossReference(assignment.terminal) ? assignment.terminal.isMulti ? "multi" : "single" : void 0
			});
		}
		return this.assignmentMap.get(feature);
	}
	assign(operator, feature, value, cstNode, crossRef) {
		const obj = this.current;
		let item;
		if (crossRef === "single" && typeof value === "string") item = this.linker.buildReference(obj, feature, cstNode, value);
		else if (crossRef === "multi" && typeof value === "string") item = this.linker.buildMultiReference(obj, feature, cstNode, value);
		else item = value;
		switch (operator) {
			case "=":
				obj[feature] = item;
				break;
			case "?=":
				obj[feature] = true;
				break;
			case "+=":
				if (!Array.isArray(obj[feature])) obj[feature] = [];
				obj[feature].push(item);
		}
	}
	assignWithoutOverride(target, source) {
		for (const [name, existingValue] of Object.entries(source)) {
			const newValue = target[name];
			if (newValue === void 0) target[name] = existingValue;
			else if (Array.isArray(newValue) && Array.isArray(existingValue)) {
				existingValue.push(...newValue);
				target[name] = existingValue;
			}
		}
		const targetCstNode = target.$cstNode;
		if (targetCstNode) {
			targetCstNode.astNode = void 0;
			target.$cstNode = void 0;
		}
		return target;
	}
	get definitionErrors() {
		return this.wrapper.definitionErrors;
	}
};
var AbstractParserErrorMessageProvider = class {
	buildMismatchTokenMessage(options) {
		return defaultParserErrorProvider.buildMismatchTokenMessage(options);
	}
	buildNotAllInputParsedMessage(options) {
		return defaultParserErrorProvider.buildNotAllInputParsedMessage(options);
	}
	buildNoViableAltMessage(options) {
		return defaultParserErrorProvider.buildNoViableAltMessage(options);
	}
	buildEarlyExitMessage(options) {
		return defaultParserErrorProvider.buildEarlyExitMessage(options);
	}
};
var LangiumParserErrorMessageProvider = class extends AbstractParserErrorMessageProvider {
	buildMismatchTokenMessage({ expected, actual }) {
		return `Expecting ${expected.LABEL ? "`" + expected.LABEL + "`" : expected.name.endsWith(":KW") ? `keyword '${expected.name.substring(0, expected.name.length - 3)}'` : `token of type '${expected.name}'`} but found \`${actual.image}\`.`;
	}
	buildNotAllInputParsedMessage({ firstRedundant }) {
		return `Expecting end of file but found \`${firstRedundant.image}\`.`;
	}
};
var LangiumCompletionParser = class extends AbstractLangiumParser {
	constructor(services) {
		super(services, true);
		this.tokens = [];
		this.elementStack = [];
		this.lastElementStack = [];
		this.nextTokenIndex = 0;
		this.stackSize = 0;
	}
	action() {}
	construct() {}
	parse(input) {
		this.resetState();
		const tokens = this.lexer.tokenize(input, { mode: "partial" });
		this.tokens = tokens.tokens;
		this.wrapper.input = [...this.tokens];
		this.mainRule.call(this.wrapper, {});
		this.unorderedGroups.clear();
		return {
			tokens: this.tokens,
			elementStack: [...this.lastElementStack],
			tokenIndex: this.nextTokenIndex
		};
	}
	rule(rule, impl) {
		const ruleMethod = this.wrapper.DEFINE_RULE(withRuleSuffix(rule.name), this.startImplementation(impl).bind(this));
		this.allRules.set(rule.name, ruleMethod);
		if (rule.entry) this.mainRule = ruleMethod;
		return ruleMethod;
	}
	resetState() {
		this.elementStack = [];
		this.lastElementStack = [];
		this.nextTokenIndex = 0;
		this.stackSize = 0;
	}
	startImplementation(implementation) {
		return (args) => {
			const size = this.keepStackSize();
			try {
				implementation(args);
			} finally {
				this.resetStackSize(size);
			}
		};
	}
	removeUnexpectedElements() {
		this.elementStack.splice(this.stackSize);
	}
	keepStackSize() {
		const size = this.elementStack.length;
		this.stackSize = size;
		return size;
	}
	resetStackSize(size) {
		this.removeUnexpectedElements();
		this.stackSize = size;
	}
	consume(idx, tokenType, feature) {
		this.wrapper.wrapConsume(idx, tokenType);
		if (!this.isRecording()) {
			this.lastElementStack = [...this.elementStack, feature];
			this.nextTokenIndex = this.currIdx + 1;
		}
	}
	subrule(idx, rule, fragment, feature, args) {
		this.before(feature);
		this.wrapper.wrapSubrule(idx, rule, args);
		this.after(feature);
	}
	before(element) {
		if (!this.isRecording()) this.elementStack.push(element);
	}
	after(element) {
		if (!this.isRecording()) {
			const index = this.elementStack.lastIndexOf(element);
			if (index >= 0) this.elementStack.splice(index);
		}
	}
	get currIdx() {
		return this.wrapper.currIdx;
	}
};
var defaultConfig = {
	recoveryEnabled: true,
	nodeLocationTracking: "full",
	skipValidations: true,
	errorMessageProvider: new LangiumParserErrorMessageProvider()
};
/**
* This class wraps the embedded actions parser of chevrotain and exposes protected methods.
* This way, we can build the `LangiumParser` as a composition.
*/
var ChevrotainWrapper = class extends EmbeddedActionsParser {
	constructor(tokens, config, incomplete) {
		const useDefaultLookahead = config && "maxLookahead" in config;
		super(tokens, {
			...defaultConfig,
			lookaheadStrategy: useDefaultLookahead ? new LLkLookaheadStrategy({ maxLookahead: config.maxLookahead }) : new LLStarLookaheadStrategy({
				logging: config.skipValidations ? () => {} : void 0,
				incomplete
			}),
			...config
		});
	}
	get IS_RECORDING() {
		return this.RECORDING_PHASE;
	}
	DEFINE_RULE(name, impl, config) {
		return this.RULE(name, impl, config);
	}
	wrapSelfAnalysis() {
		this.performSelfAnalysis();
	}
	wrapConsume(idx, tokenType) {
		return this.consume(idx, tokenType, void 0);
	}
	wrapSubrule(idx, rule, args) {
		return this.subrule(idx, rule, { ARGS: [args] });
	}
	wrapOr(idx, choices) {
		this.or(idx, choices);
	}
	wrapOption(idx, callback) {
		this.option(idx, callback);
	}
	wrapMany(idx, callback) {
		this.many(idx, callback);
	}
	wrapAtLeastOne(idx, callback) {
		this.atLeastOne(idx, callback);
	}
	rule(rule) {
		return rule.call(this, {});
	}
};
var ProfilerWrapper = class extends ChevrotainWrapper {
	constructor(tokens, config, incomplete, task) {
		super(tokens, config, incomplete);
		this.task = task;
	}
	rule(rule) {
		this.task.start();
		this.task.startSubTask(this.ruleName(rule));
		try {
			return super.rule(rule);
		} finally {
			this.task.stopSubTask(this.ruleName(rule));
			this.task.stop();
		}
	}
	ruleName(rule) {
		return rule.ruleName;
	}
	subrule(idx, ruleToCall, options) {
		this.task.startSubTask(this.ruleName(ruleToCall));
		try {
			return super.subrule(idx, ruleToCall, options);
		} finally {
			this.task.stopSubTask(this.ruleName(ruleToCall));
		}
	}
};
/******************************************************************************
* Copyright 2022 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
function createParser(grammar, parser, tokens) {
	buildRules({
		parser,
		tokens,
		ruleNames: /* @__PURE__ */ new Map()
	}, grammar);
	return parser;
}
function buildRules(parserContext, grammar) {
	const reachable = getAllReachableRules(grammar, false);
	const parserRules = stream(grammar.rules).filter(isParserRule).filter((rule) => reachable.has(rule));
	for (const rule of parserRules) {
		const ctx = {
			...parserContext,
			consume: 1,
			optional: 1,
			subrule: 1,
			many: 1,
			or: 1
		};
		parserContext.parser.rule(rule, buildElement(ctx, rule.definition));
	}
	const infixRules = stream(grammar.rules).filter(isInfixRule).filter((rule) => reachable.has(rule));
	for (const rule of infixRules) parserContext.parser.rule(rule, buildInfixRule(parserContext, rule));
}
function buildInfixRule(ctx, rule) {
	const expressionRule = rule.call.rule.ref;
	if (!expressionRule) throw new Error("Could not resolve reference to infix operator rule: " + rule.call.rule.$refText);
	if (isTerminalRule(expressionRule)) throw new Error("Cannot use terminal rule in infix expression");
	const allKeywords = rule.operators.precedences.flatMap((e) => e.operators);
	const outerGroup = {
		$type: "Group",
		elements: []
	};
	const part1Assignment = {
		$container: outerGroup,
		$type: "Assignment",
		feature: "parts",
		operator: "+=",
		terminal: rule.call
	};
	const innerGroup = {
		$container: outerGroup,
		$type: "Group",
		elements: [],
		cardinality: "*"
	};
	outerGroup.elements.push(part1Assignment, innerGroup);
	const operatorAssignment = {
		$container: innerGroup,
		$type: "Assignment",
		feature: "operators",
		operator: "+=",
		terminal: {
			$type: "Alternatives",
			elements: allKeywords
		}
	};
	const part2Assignment = {
		...part1Assignment,
		$container: innerGroup
	};
	innerGroup.elements.push(operatorAssignment, part2Assignment);
	const orAlts = allKeywords.map((e) => ctx.tokens[e.value]).map((token, index) => ({ ALT: () => ctx.parser.consume(index, token, operatorAssignment) }));
	let subrule;
	return (args) => {
		subrule ?? (subrule = getRule(ctx, expressionRule));
		ctx.parser.subrule(0, subrule, false, part1Assignment, args);
		ctx.parser.many(0, { DEF: () => {
			ctx.parser.alternatives(0, orAlts);
			ctx.parser.subrule(1, subrule, false, part2Assignment, args);
		} });
	};
}
function buildElement(ctx, element, ignoreGuard = false) {
	let method;
	if (isKeyword(element)) method = buildKeyword(ctx, element);
	else if (isAction(element)) method = buildAction(ctx, element);
	else if (isAssignment(element)) method = buildElement(ctx, element.terminal);
	else if (isCrossReference(element)) method = buildCrossReference(ctx, element);
	else if (isRuleCall(element)) method = buildRuleCall(ctx, element);
	else if (isAlternatives(element)) method = buildAlternatives(ctx, element);
	else if (isUnorderedGroup(element)) method = buildUnorderedGroup(ctx, element);
	else if (isGroup(element)) method = buildGroup(ctx, element);
	else if (isEndOfFile(element)) {
		const idx = ctx.consume++;
		method = () => ctx.parser.consume(idx, EOF, element);
	} else throw new ErrorWithLocation(element.$cstNode, `Unexpected element type: ${element.$type}`);
	return wrap(ctx, ignoreGuard ? void 0 : getGuardCondition(element), method, element.cardinality);
}
function buildAction(ctx, action) {
	const actionType = getTypeName(action);
	return () => ctx.parser.action(actionType, action);
}
function buildRuleCall(ctx, ruleCall) {
	const rule = ruleCall.rule.ref;
	if (isAbstractParserRule(rule)) {
		const idx = ctx.subrule++;
		const fragment = isParserRule(rule) && rule.fragment;
		const predicate = ruleCall.arguments.length > 0 ? buildRuleCallPredicate(rule, ruleCall.arguments) : () => ({});
		let subrule;
		return (args) => {
			subrule ?? (subrule = getRule(ctx, rule));
			ctx.parser.subrule(idx, subrule, fragment, ruleCall, predicate(args));
		};
	} else if (isTerminalRule(rule)) {
		const idx = ctx.consume++;
		const method = getToken(ctx, rule.name);
		return () => ctx.parser.consume(idx, method, ruleCall);
	} else if (!rule) throw new ErrorWithLocation(ruleCall.$cstNode, `Undefined rule: ${ruleCall.rule.$refText}`);
	else assertUnreachable(rule);
}
function buildRuleCallPredicate(rule, namedArgs) {
	if (namedArgs.some((arg) => arg.calledByName)) {
		const namedPredicates = namedArgs.map((arg) => ({
			parameterName: arg.parameter?.ref?.name,
			predicate: buildPredicate(arg.value)
		}));
		return (args) => {
			const ruleArgs = {};
			for (const { parameterName, predicate } of namedPredicates) if (parameterName) ruleArgs[parameterName] = predicate(args);
			return ruleArgs;
		};
	} else {
		const predicates = namedArgs.map((arg) => buildPredicate(arg.value));
		return (args) => {
			const ruleArgs = {};
			for (let i = 0; i < predicates.length; i++) if (i < rule.parameters.length) {
				const parameterName = rule.parameters[i].name;
				const predicate = predicates[i];
				ruleArgs[parameterName] = predicate(args);
			}
			return ruleArgs;
		};
	}
}
function buildPredicate(condition) {
	if (isDisjunction(condition)) {
		const left = buildPredicate(condition.left);
		const right = buildPredicate(condition.right);
		return (args) => left(args) || right(args);
	} else if (isConjunction(condition)) {
		const left = buildPredicate(condition.left);
		const right = buildPredicate(condition.right);
		return (args) => left(args) && right(args);
	} else if (isNegation(condition)) {
		const value = buildPredicate(condition.value);
		return (args) => !value(args);
	} else if (isParameterReference(condition)) {
		const name = condition.parameter.ref.name;
		return (args) => args !== void 0 && args[name] === true;
	} else if (isBooleanLiteral(condition)) {
		const value = Boolean(condition.true);
		return () => value;
	}
	assertUnreachable(condition);
}
function buildAlternatives(ctx, alternatives) {
	if (alternatives.elements.length === 1) return buildElement(ctx, alternatives.elements[0]);
	else {
		const methods = [];
		for (const element of alternatives.elements) {
			const predicatedMethod = { ALT: buildElement(ctx, element, true) };
			const guard = getGuardCondition(element);
			if (guard) predicatedMethod.GATE = buildPredicate(guard);
			methods.push(predicatedMethod);
		}
		const idx = ctx.or++;
		return (args) => ctx.parser.alternatives(idx, methods.map((method) => {
			const alt = { ALT: () => method.ALT(args) };
			const gate = method.GATE;
			if (gate) alt.GATE = () => gate(args);
			return alt;
		}));
	}
}
function buildUnorderedGroup(ctx, group) {
	if (group.elements.length === 1) return buildElement(ctx, group.elements[0]);
	const methods = [];
	for (const element of group.elements) {
		const predicatedMethod = { ALT: buildElement(ctx, element, true) };
		const guard = getGuardCondition(element);
		if (guard) predicatedMethod.GATE = buildPredicate(guard);
		methods.push(predicatedMethod);
	}
	const orIdx = ctx.or++;
	const idFunc = (groupIdx, lParser) => {
		return `uGroup_${groupIdx}_${lParser.getRuleStack().join("-")}`;
	};
	const alternatives = (args) => ctx.parser.alternatives(orIdx, methods.map((method, idx) => {
		const alt = { ALT: () => true };
		const parser = ctx.parser;
		alt.ALT = () => {
			method.ALT(args);
			if (!parser.isRecording()) {
				const key = idFunc(orIdx, parser);
				if (!parser.unorderedGroups.get(key)) parser.unorderedGroups.set(key, []);
				const groupState = parser.unorderedGroups.get(key);
				if (typeof groupState?.[idx] === "undefined") groupState[idx] = true;
			}
		};
		const gate = method.GATE;
		if (gate) alt.GATE = () => gate(args);
		else alt.GATE = () => {
			return !parser.unorderedGroups.get(idFunc(orIdx, parser))?.[idx];
		};
		return alt;
	}));
	const wrapped = wrap(ctx, getGuardCondition(group), alternatives, "*");
	return (args) => {
		wrapped(args);
		if (!ctx.parser.isRecording()) ctx.parser.unorderedGroups.delete(idFunc(orIdx, ctx.parser));
	};
}
function buildGroup(ctx, group) {
	const methods = group.elements.map((e) => buildElement(ctx, e));
	return (args) => methods.forEach((method) => method(args));
}
function getGuardCondition(element) {
	if (isGroup(element)) return element.guardCondition;
}
function buildCrossReference(ctx, crossRef, terminal = crossRef.terminal) {
	if (!terminal) {
		if (!crossRef.type.ref) throw new Error("Could not resolve reference to type: " + crossRef.type.$refText);
		const assignTerminal = findNameAssignment(crossRef.type.ref)?.terminal;
		if (!assignTerminal) throw new Error("Could not find name assignment for type: " + getTypeName(crossRef.type.ref));
		return buildCrossReference(ctx, crossRef, assignTerminal);
	} else if (isRuleCall(terminal) && isParserRule(terminal.rule.ref)) {
		const rule = terminal.rule.ref;
		const idx = ctx.subrule++;
		let subrule;
		return (args) => {
			subrule ?? (subrule = getRule(ctx, rule));
			ctx.parser.subrule(idx, subrule, false, crossRef, args);
		};
	} else if (isRuleCall(terminal) && isTerminalRule(terminal.rule.ref)) {
		const idx = ctx.consume++;
		const terminalRule = getToken(ctx, terminal.rule.ref.name);
		return () => ctx.parser.consume(idx, terminalRule, crossRef);
	} else if (isKeyword(terminal)) {
		const idx = ctx.consume++;
		const keyword = getToken(ctx, terminal.value);
		return () => ctx.parser.consume(idx, keyword, crossRef);
	} else throw new Error("Could not build cross reference parser");
}
function buildKeyword(ctx, keyword) {
	const idx = ctx.consume++;
	const token = ctx.tokens[keyword.value];
	if (!token) throw new Error("Could not find token for keyword: " + keyword.value);
	return () => ctx.parser.consume(idx, token, keyword);
}
function wrap(ctx, guard, method, cardinality) {
	const gate = guard && buildPredicate(guard);
	if (!cardinality) if (gate) {
		const idx = ctx.or++;
		return (args) => ctx.parser.alternatives(idx, [{
			ALT: () => method(args),
			GATE: () => gate(args)
		}, {
			ALT: EMPTY_ALT(),
			GATE: () => !gate(args)
		}]);
	} else return method;
	if (cardinality === "*") {
		const idx = ctx.many++;
		return (args) => ctx.parser.many(idx, {
			DEF: () => method(args),
			GATE: gate ? () => gate(args) : void 0
		});
	} else if (cardinality === "+") {
		const idx = ctx.many++;
		if (gate) {
			const orIdx = ctx.or++;
			return (args) => ctx.parser.alternatives(orIdx, [{
				ALT: () => ctx.parser.atLeastOne(idx, { DEF: () => method(args) }),
				GATE: () => gate(args)
			}, {
				ALT: EMPTY_ALT(),
				GATE: () => !gate(args)
			}]);
		} else return (args) => ctx.parser.atLeastOne(idx, { DEF: () => method(args) });
	} else if (cardinality === "?") {
		const idx = ctx.optional++;
		return (args) => ctx.parser.optional(idx, {
			DEF: () => method(args),
			GATE: gate ? () => gate(args) : void 0
		});
	} else assertUnreachable(cardinality);
}
function getRule(ctx, element) {
	const name = getRuleName(ctx, element);
	const rule = ctx.parser.getRule(name);
	if (!rule) throw new Error(`Rule "${name}" not found."`);
	return rule;
}
function getRuleName(ctx, element) {
	if (isAbstractParserRule(element)) return element.name;
	else if (ctx.ruleNames.has(element)) return ctx.ruleNames.get(element);
	else {
		let item = element;
		let parent = item.$container;
		let ruleName = element.$type;
		while (!isParserRule(parent)) {
			if (isGroup(parent) || isAlternatives(parent) || isUnorderedGroup(parent)) ruleName = parent.elements.indexOf(item).toString() + ":" + ruleName;
			item = parent;
			parent = parent.$container;
		}
		ruleName = parent.name + ":" + ruleName;
		ctx.ruleNames.set(element, ruleName);
		return ruleName;
	}
}
function getToken(ctx, name) {
	const token = ctx.tokens[name];
	if (!token) throw new Error(`Token "${name}" not found."`);
	return token;
}
/******************************************************************************
* Copyright 2022 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
function createCompletionParser(services) {
	const grammar = services.Grammar;
	const lexer = services.parser.Lexer;
	const parser = new LangiumCompletionParser(services);
	createParser(grammar, parser, lexer.definition);
	parser.finalize();
	return parser;
}
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* Create and finalize a Langium parser. The parser rules are derived from the grammar, which is
* available at `services.Grammar`.
*/
function createLangiumParser(services) {
	const parser = prepareLangiumParser(services);
	parser.finalize();
	return parser;
}
/**
* Create a Langium parser without finalizing it. This is used to extract more detailed error
* information when the parser is initially validated.
*/
function prepareLangiumParser(services) {
	const grammar = services.Grammar;
	const lexer = services.parser.Lexer;
	return createParser(grammar, new LangiumParser(services), lexer.definition);
}
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultTokenBuilder = class {
	constructor() {
		/**
		* The list of diagnostics stored during the lexing process of a single text.
		*/
		this.diagnostics = [];
	}
	buildTokens(grammar, options) {
		const reachableRules = stream(getAllReachableRules(grammar, false));
		const terminalTokens = this.buildTerminalTokens(reachableRules);
		const tokens = this.buildKeywordTokens(reachableRules, terminalTokens, options);
		tokens.push(...terminalTokens);
		return tokens;
	}
	flushLexingReport(text) {
		return { diagnostics: this.popDiagnostics() };
	}
	popDiagnostics() {
		const diagnostics = [...this.diagnostics];
		this.diagnostics = [];
		return diagnostics;
	}
	buildTerminalTokens(rules) {
		return rules.filter(isTerminalRule).filter((e) => !e.fragment).map((terminal) => this.buildTerminalToken(terminal)).toArray();
	}
	buildTerminalToken(terminal) {
		const regex = terminalRegex(terminal);
		const pattern = this.requiresCustomPattern(regex) ? this.regexPatternFunction(regex) : regex;
		const tokenType = {
			name: terminal.name,
			PATTERN: pattern
		};
		if (typeof pattern === "function") tokenType.LINE_BREAKS = true;
		if (terminal.hidden) tokenType.GROUP = isWhitespace(regex) ? Lexer.SKIPPED : "hidden";
		return tokenType;
	}
	requiresCustomPattern(regex) {
		if (regex.flags.includes("u") || regex.flags.includes("s")) return true;
		else return false;
	}
	regexPatternFunction(regex) {
		const stickyRegex = new RegExp(regex, regex.flags + "y");
		return (text, offset) => {
			stickyRegex.lastIndex = offset;
			return stickyRegex.exec(text);
		};
	}
	buildKeywordTokens(rules, terminalTokens, options) {
		return rules.filter(isAbstractParserRule).flatMap((rule) => streamAllContents(rule).filter(isKeyword)).distinct((e) => e.value).toArray().sort((a, b) => b.value.length - a.value.length).map((keyword) => this.buildKeywordToken(keyword, terminalTokens, Boolean(options?.caseInsensitive)));
	}
	buildKeywordToken(keyword, terminalTokens, caseInsensitive) {
		const keywordPattern = this.buildKeywordPattern(keyword, caseInsensitive);
		const tokenType = {
			name: keyword.value,
			PATTERN: keywordPattern,
			LONGER_ALT: this.findLongerAlt(keyword, terminalTokens)
		};
		if (typeof keywordPattern === "function") tokenType.LINE_BREAKS = true;
		return tokenType;
	}
	buildKeywordPattern(keyword, caseInsensitive) {
		return caseInsensitive ? new RegExp(escapeRegExp(keyword.value), "i") : keyword.value;
	}
	findLongerAlt(keyword, terminalTokens) {
		return terminalTokens.reduce((longerAlts, token) => {
			const pattern = token?.PATTERN;
			if (pattern?.source && partialMatches("^" + pattern.source + "$", keyword.value)) longerAlts.push(token);
			return longerAlts;
		}, []);
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultValueConverter = class {
	convert(input, cstNode) {
		let feature = cstNode.grammarSource;
		if (isCrossReference(feature)) feature = getCrossReferenceTerminal(feature);
		if (isRuleCall(feature)) {
			const rule = feature.rule.ref;
			if (!rule) throw new Error("This cst node was not parsed by a rule.");
			return this.runConverter(rule, input, cstNode);
		}
		return input;
	}
	runConverter(rule, input, cstNode) {
		switch (rule.name.toUpperCase()) {
			case "INT": return ValueConverter.convertInt(input);
			case "STRING": return ValueConverter.convertString(input);
			case "ID": return ValueConverter.convertID(input);
		}
		switch (getRuleType(rule)?.toLowerCase()) {
			case "number": return ValueConverter.convertNumber(input);
			case "boolean": return ValueConverter.convertBoolean(input);
			case "bigint": return ValueConverter.convertBigint(input);
			case "date": return ValueConverter.convertDate(input);
			default: return input;
		}
	}
};
var ValueConverter;
(function(ValueConverter) {
	function convertString(input) {
		let result = "";
		for (let i = 1; i < input.length - 1; i++) {
			const c = input.charAt(i);
			if (c === "\\") {
				const c1 = input.charAt(++i);
				result += convertEscapeCharacter(c1);
			} else result += c;
		}
		return result;
	}
	ValueConverter.convertString = convertString;
	function convertEscapeCharacter(char) {
		switch (char) {
			case "b": return "\b";
			case "f": return "\f";
			case "n": return "\n";
			case "r": return "\r";
			case "t": return "	";
			case "v": return "\v";
			case "0": return "\0";
			default: return char;
		}
	}
	function convertID(input) {
		if (input.charAt(0) === "^") return input.substring(1);
		else return input;
	}
	ValueConverter.convertID = convertID;
	function convertInt(input) {
		return parseInt(input);
	}
	ValueConverter.convertInt = convertInt;
	function convertBigint(input) {
		return BigInt(input);
	}
	ValueConverter.convertBigint = convertBigint;
	function convertDate(input) {
		return new Date(input);
	}
	ValueConverter.convertDate = convertDate;
	function convertNumber(input) {
		return Number(input);
	}
	ValueConverter.convertNumber = convertNumber;
	function convertBoolean(input) {
		return input.toLowerCase() === "true";
	}
	ValueConverter.convertBoolean = convertBoolean;
})(ValueConverter || (ValueConverter = {}));
var require_ral = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	var _ral;
	function RAL() {
		if (_ral === void 0) throw new Error(`No runtime abstraction layer installed`);
		return _ral;
	}
	(function(RAL) {
		function install(ral) {
			if (ral === void 0) throw new Error(`No runtime abstraction layer provided`);
			_ral = ral;
		}
		RAL.install = install;
	})(RAL || (RAL = {}));
	exports.default = RAL;
}));
var require_is$1 = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.stringArray = exports.array = exports.func = exports.error = exports.number = exports.string = exports.boolean = void 0;
	function boolean(value) {
		return value === true || value === false;
	}
	exports.boolean = boolean;
	function string(value) {
		return typeof value === "string" || value instanceof String;
	}
	exports.string = string;
	function number(value) {
		return typeof value === "number" || value instanceof Number;
	}
	exports.number = number;
	function error(value) {
		return value instanceof Error;
	}
	exports.error = error;
	function func(value) {
		return typeof value === "function";
	}
	exports.func = func;
	function array(value) {
		return Array.isArray(value);
	}
	exports.array = array;
	function stringArray(value) {
		return array(value) && value.every((elem) => string(elem));
	}
	exports.stringArray = stringArray;
}));
var require_events = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.Emitter = exports.Event = void 0;
	var ral_1 = require_ral();
	var Event;
	(function(Event) {
		const _disposable = { dispose() {} };
		Event.None = function() {
			return _disposable;
		};
	})(Event || (exports.Event = Event = {}));
	var CallbackList = class {
		add(callback, context = null, bucket) {
			if (!this._callbacks) {
				this._callbacks = [];
				this._contexts = [];
			}
			this._callbacks.push(callback);
			this._contexts.push(context);
			if (Array.isArray(bucket)) bucket.push({ dispose: () => this.remove(callback, context) });
		}
		remove(callback, context = null) {
			if (!this._callbacks) return;
			let foundCallbackWithDifferentContext = false;
			for (let i = 0, len = this._callbacks.length; i < len; i++) if (this._callbacks[i] === callback) if (this._contexts[i] === context) {
				this._callbacks.splice(i, 1);
				this._contexts.splice(i, 1);
				return;
			} else foundCallbackWithDifferentContext = true;
			if (foundCallbackWithDifferentContext) throw new Error("When adding a listener with a context, you should remove it with the same context");
		}
		invoke(...args) {
			if (!this._callbacks) return [];
			const ret = [], callbacks = this._callbacks.slice(0), contexts = this._contexts.slice(0);
			for (let i = 0, len = callbacks.length; i < len; i++) try {
				ret.push(callbacks[i].apply(contexts[i], args));
			} catch (e) {
				(0, ral_1.default)().console.error(e);
			}
			return ret;
		}
		isEmpty() {
			return !this._callbacks || this._callbacks.length === 0;
		}
		dispose() {
			this._callbacks = void 0;
			this._contexts = void 0;
		}
	};
	var Emitter = class Emitter {
		constructor(_options) {
			this._options = _options;
		}
		/**
		* For the public to allow to subscribe
		* to events from this Emitter
		*/
		get event() {
			if (!this._event) this._event = (listener, thisArgs, disposables) => {
				if (!this._callbacks) this._callbacks = new CallbackList();
				if (this._options && this._options.onFirstListenerAdd && this._callbacks.isEmpty()) this._options.onFirstListenerAdd(this);
				this._callbacks.add(listener, thisArgs);
				const result = { dispose: () => {
					if (!this._callbacks) return;
					this._callbacks.remove(listener, thisArgs);
					result.dispose = Emitter._noop;
					if (this._options && this._options.onLastListenerRemove && this._callbacks.isEmpty()) this._options.onLastListenerRemove(this);
				} };
				if (Array.isArray(disposables)) disposables.push(result);
				return result;
			};
			return this._event;
		}
		/**
		* To be kept private to fire an event to
		* subscribers
		*/
		fire(event) {
			if (this._callbacks) this._callbacks.invoke.call(this._callbacks, event);
		}
		dispose() {
			if (this._callbacks) {
				this._callbacks.dispose();
				this._callbacks = void 0;
			}
		}
	};
	exports.Emitter = Emitter;
	Emitter._noop = function() {};
}));
var require_cancellation = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.CancellationTokenSource = exports.CancellationToken = void 0;
	var ral_1 = require_ral();
	var Is = require_is$1();
	var events_1 = require_events();
	var CancellationToken;
	(function(CancellationToken) {
		CancellationToken.None = Object.freeze({
			isCancellationRequested: false,
			onCancellationRequested: events_1.Event.None
		});
		CancellationToken.Cancelled = Object.freeze({
			isCancellationRequested: true,
			onCancellationRequested: events_1.Event.None
		});
		function is(value) {
			const candidate = value;
			return candidate && (candidate === CancellationToken.None || candidate === CancellationToken.Cancelled || Is.boolean(candidate.isCancellationRequested) && !!candidate.onCancellationRequested);
		}
		CancellationToken.is = is;
	})(CancellationToken || (exports.CancellationToken = CancellationToken = {}));
	var shortcutEvent = Object.freeze(function(callback, context) {
		const handle = (0, ral_1.default)().timer.setTimeout(callback.bind(context), 0);
		return { dispose() {
			handle.dispose();
		} };
	});
	var MutableToken = class {
		constructor() {
			this._isCancelled = false;
		}
		cancel() {
			if (!this._isCancelled) {
				this._isCancelled = true;
				if (this._emitter) {
					this._emitter.fire(void 0);
					this.dispose();
				}
			}
		}
		get isCancellationRequested() {
			return this._isCancelled;
		}
		get onCancellationRequested() {
			if (this._isCancelled) return shortcutEvent;
			if (!this._emitter) this._emitter = new events_1.Emitter();
			return this._emitter.event;
		}
		dispose() {
			if (this._emitter) {
				this._emitter.dispose();
				this._emitter = void 0;
			}
		}
	};
	var CancellationTokenSource = class {
		get token() {
			if (!this._token) this._token = new MutableToken();
			return this._token;
		}
		cancel() {
			if (!this._token) this._token = CancellationToken.Cancelled;
			else this._token.cancel();
		}
		dispose() {
			if (!this._token) this._token = CancellationToken.None;
			else if (this._token instanceof MutableToken) this._token.dispose();
		}
	};
	exports.CancellationTokenSource = CancellationTokenSource;
}));
var cancellation_exports = /* @__PURE__ */ __exportAll({});
__reExport(cancellation_exports, /* @__PURE__ */ __toESM(require_cancellation(), 1));
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* Delays the execution of the current code to the next tick of the event loop.
* Don't call this method directly in a tight loop to prevent too many promises from being created.
*/
function delayNextTick() {
	return new Promise((resolve) => {
		if (typeof setImmediate === "undefined") setTimeout(resolve, 0);
		else setImmediate(resolve);
	});
}
var lastTick = 0;
var globalInterruptionPeriod = 10;
/**
* Reset the global interruption period and create a cancellation token source.
*/
function startCancelableOperation() {
	lastTick = performance.now();
	return new cancellation_exports.CancellationTokenSource();
}
/**
* This symbol may be thrown in an asynchronous context by any Langium service that receives
* a `CancellationToken`. This means that the promise returned by such a service is rejected with
* this symbol as rejection reason.
*/
var OperationCancelled = Symbol("OperationCancelled");
/**
* Use this in a `catch` block to check whether the thrown object indicates that the operation
* has been cancelled.
*/
function isOperationCancelled(err) {
	return err === OperationCancelled;
}
/**
* This function does two things:
*  1. Check the elapsed time since the last call to this function or to `startCancelableOperation`. If the predefined
*     period (configured with `setInterruptionPeriod`) is exceeded, execution is delayed with `delayNextTick`.
*  2. If the predefined period is not met yet or execution is resumed after an interruption, the given cancellation
*     token is checked, and if cancellation is requested, `OperationCanceled` is thrown.
*
* All services in Langium that receive a `CancellationToken` may potentially call this function, so the
* `CancellationToken` must be caught (with an `async` try-catch block or a `catch` callback attached to
* the promise) to avoid that event being exposed as an error.
*/
async function interruptAndCheck(token) {
	if (token === cancellation_exports.CancellationToken.None) return;
	const current = performance.now();
	if (current - lastTick >= globalInterruptionPeriod) {
		lastTick = current;
		await delayNextTick();
		lastTick = performance.now();
	}
	if (token.isCancellationRequested) throw OperationCancelled;
}
/**
* Simple implementation of the deferred pattern.
* An object that exposes a promise and functions to resolve and reject it.
*/
var Deferred = class {
	constructor() {
		this.promise = new Promise((resolve, reject) => {
			this.resolve = (arg) => {
				resolve(arg);
				return this;
			};
			this.reject = (err) => {
				reject(err);
				return this;
			};
		});
	}
};
var FullTextDocument = class FullTextDocument {
	constructor(uri, languageId, version, content) {
		this._uri = uri;
		this._languageId = languageId;
		this._version = version;
		this._content = content;
		this._lineOffsets = void 0;
	}
	get uri() {
		return this._uri;
	}
	get languageId() {
		return this._languageId;
	}
	get version() {
		return this._version;
	}
	getText(range) {
		if (range) {
			const start = this.offsetAt(range.start);
			const end = this.offsetAt(range.end);
			return this._content.substring(start, end);
		}
		return this._content;
	}
	update(changes, version) {
		for (const change of changes) if (FullTextDocument.isIncremental(change)) {
			const range = getWellformedRange(change.range);
			const startOffset = this.offsetAt(range.start);
			const endOffset = this.offsetAt(range.end);
			this._content = this._content.substring(0, startOffset) + change.text + this._content.substring(endOffset, this._content.length);
			const startLine = Math.max(range.start.line, 0);
			const endLine = Math.max(range.end.line, 0);
			let lineOffsets = this._lineOffsets;
			const addedLineOffsets = computeLineOffsets(change.text, false, startOffset);
			if (endLine - startLine === addedLineOffsets.length) for (let i = 0, len = addedLineOffsets.length; i < len; i++) lineOffsets[i + startLine + 1] = addedLineOffsets[i];
			else if (addedLineOffsets.length < 1e4) lineOffsets.splice(startLine + 1, endLine - startLine, ...addedLineOffsets);
			else this._lineOffsets = lineOffsets = lineOffsets.slice(0, startLine + 1).concat(addedLineOffsets, lineOffsets.slice(endLine + 1));
			const diff = change.text.length - (endOffset - startOffset);
			if (diff !== 0) for (let i = startLine + 1 + addedLineOffsets.length, len = lineOffsets.length; i < len; i++) lineOffsets[i] = lineOffsets[i] + diff;
		} else if (FullTextDocument.isFull(change)) {
			this._content = change.text;
			this._lineOffsets = void 0;
		} else throw new Error("Unknown change event received");
		this._version = version;
	}
	getLineOffsets() {
		if (this._lineOffsets === void 0) this._lineOffsets = computeLineOffsets(this._content, true);
		return this._lineOffsets;
	}
	positionAt(offset) {
		offset = Math.max(Math.min(offset, this._content.length), 0);
		const lineOffsets = this.getLineOffsets();
		let low = 0, high = lineOffsets.length;
		if (high === 0) return {
			line: 0,
			character: offset
		};
		while (low < high) {
			const mid = Math.floor((low + high) / 2);
			if (lineOffsets[mid] > offset) high = mid;
			else low = mid + 1;
		}
		const line = low - 1;
		offset = this.ensureBeforeEOL(offset, lineOffsets[line]);
		return {
			line,
			character: offset - lineOffsets[line]
		};
	}
	offsetAt(position) {
		const lineOffsets = this.getLineOffsets();
		if (position.line >= lineOffsets.length) return this._content.length;
		else if (position.line < 0) return 0;
		const lineOffset = lineOffsets[position.line];
		if (position.character <= 0) return lineOffset;
		const nextLineOffset = position.line + 1 < lineOffsets.length ? lineOffsets[position.line + 1] : this._content.length;
		const offset = Math.min(lineOffset + position.character, nextLineOffset);
		return this.ensureBeforeEOL(offset, lineOffset);
	}
	ensureBeforeEOL(offset, lineOffset) {
		while (offset > lineOffset && isEOL(this._content.charCodeAt(offset - 1))) offset--;
		return offset;
	}
	get lineCount() {
		return this.getLineOffsets().length;
	}
	static isIncremental(event) {
		const candidate = event;
		return candidate !== void 0 && candidate !== null && typeof candidate.text === "string" && candidate.range !== void 0 && (candidate.rangeLength === void 0 || typeof candidate.rangeLength === "number");
	}
	static isFull(event) {
		const candidate = event;
		return candidate !== void 0 && candidate !== null && typeof candidate.text === "string" && candidate.range === void 0 && candidate.rangeLength === void 0;
	}
};
var TextDocument;
(function(TextDocument) {
	/**
	* Creates a new text document.
	*
	* @param uri The document's uri.
	* @param languageId  The document's language Id.
	* @param version The document's initial version number.
	* @param content The document's content.
	*/
	function create(uri, languageId, version, content) {
		return new FullTextDocument(uri, languageId, version, content);
	}
	TextDocument.create = create;
	/**
	* Updates a TextDocument by modifying its content.
	*
	* @param document the document to update. Only documents created by TextDocument.create are valid inputs.
	* @param changes the changes to apply to the document.
	* @param version the changes version for the document.
	* @returns The updated TextDocument. Note: That's the same document instance passed in as first parameter.
	*
	*/
	function update(document, changes, version) {
		if (document instanceof FullTextDocument) {
			document.update(changes, version);
			return document;
		} else throw new Error("TextDocument.update: document must be created by TextDocument.create");
	}
	TextDocument.update = update;
	function applyEdits(document, edits) {
		const text = document.getText();
		const sortedEdits = mergeSort(edits.map(getWellformedEdit), (a, b) => {
			const diff = a.range.start.line - b.range.start.line;
			if (diff === 0) return a.range.start.character - b.range.start.character;
			return diff;
		});
		let lastModifiedOffset = 0;
		const spans = [];
		for (const e of sortedEdits) {
			const startOffset = document.offsetAt(e.range.start);
			if (startOffset < lastModifiedOffset) throw new Error("Overlapping edit");
			else if (startOffset > lastModifiedOffset) spans.push(text.substring(lastModifiedOffset, startOffset));
			if (e.newText.length) spans.push(e.newText);
			lastModifiedOffset = document.offsetAt(e.range.end);
		}
		spans.push(text.substr(lastModifiedOffset));
		return spans.join("");
	}
	TextDocument.applyEdits = applyEdits;
})(TextDocument || (TextDocument = {}));
function mergeSort(data, compare) {
	if (data.length <= 1) return data;
	const p = data.length / 2 | 0;
	const left = data.slice(0, p);
	const right = data.slice(p);
	mergeSort(left, compare);
	mergeSort(right, compare);
	let leftIdx = 0;
	let rightIdx = 0;
	let i = 0;
	while (leftIdx < left.length && rightIdx < right.length) if (compare(left[leftIdx], right[rightIdx]) <= 0) data[i++] = left[leftIdx++];
	else data[i++] = right[rightIdx++];
	while (leftIdx < left.length) data[i++] = left[leftIdx++];
	while (rightIdx < right.length) data[i++] = right[rightIdx++];
	return data;
}
function computeLineOffsets(text, isAtLineStart, textOffset = 0) {
	const result = isAtLineStart ? [textOffset] : [];
	for (let i = 0; i < text.length; i++) {
		const ch = text.charCodeAt(i);
		if (isEOL(ch)) {
			if (ch === 13 && i + 1 < text.length && text.charCodeAt(i + 1) === 10) i++;
			result.push(textOffset + i + 1);
		}
	}
	return result;
}
function isEOL(char) {
	return char === 13 || char === 10;
}
function getWellformedRange(range) {
	const start = range.start;
	const end = range.end;
	if (start.line > end.line || start.line === end.line && start.character > end.character) return {
		start: end,
		end: start
	};
	return range;
}
function getWellformedEdit(textEdit) {
	const range = getWellformedRange(textEdit.range);
	if (range !== textEdit.range) return {
		newText: textEdit.newText,
		range
	};
	return textEdit;
}
var LIB;
(() => {
	"use strict";
	var t = { 975: (t) => {
		function e(t) {
			if ("string" != typeof t) throw new TypeError("Path must be a string. Received " + JSON.stringify(t));
		}
		function r(t, e) {
			for (var r, n = "", i = 0, o = -1, s = 0, h = 0; h <= t.length; ++h) {
				if (h < t.length) r = t.charCodeAt(h);
				else {
					if (47 === r) break;
					r = 47;
				}
				if (47 === r) {
					if (o === h - 1 || 1 === s);
					else if (o !== h - 1 && 2 === s) {
						if (n.length < 2 || 2 !== i || 46 !== n.charCodeAt(n.length - 1) || 46 !== n.charCodeAt(n.length - 2)) {
							if (n.length > 2) {
								var a = n.lastIndexOf("/");
								if (a !== n.length - 1) {
									-1 === a ? (n = "", i = 0) : i = (n = n.slice(0, a)).length - 1 - n.lastIndexOf("/"), o = h, s = 0;
									continue;
								}
							} else if (2 === n.length || 1 === n.length) {
								n = "", i = 0, o = h, s = 0;
								continue;
							}
						}
						e && (n.length > 0 ? n += "/.." : n = "..", i = 2);
					} else n.length > 0 ? n += "/" + t.slice(o + 1, h) : n = t.slice(o + 1, h), i = h - o - 1;
					o = h, s = 0;
				} else 46 === r && -1 !== s ? ++s : s = -1;
			}
			return n;
		}
		var n = {
			resolve: function() {
				for (var t, n = "", i = !1, o = arguments.length - 1; o >= -1 && !i; o--) {
					var s;
					o >= 0 ? s = arguments[o] : (void 0 === t && (t = process.cwd()), s = t), e(s), 0 !== s.length && (n = s + "/" + n, i = 47 === s.charCodeAt(0));
				}
				return n = r(n, !i), i ? n.length > 0 ? "/" + n : "/" : n.length > 0 ? n : ".";
			},
			normalize: function(t) {
				if (e(t), 0 === t.length) return ".";
				var n = 47 === t.charCodeAt(0), i = 47 === t.charCodeAt(t.length - 1);
				return 0 !== (t = r(t, !n)).length || n || (t = "."), t.length > 0 && i && (t += "/"), n ? "/" + t : t;
			},
			isAbsolute: function(t) {
				return e(t), t.length > 0 && 47 === t.charCodeAt(0);
			},
			join: function() {
				if (0 === arguments.length) return ".";
				for (var t, r = 0; r < arguments.length; ++r) {
					var i = arguments[r];
					e(i), i.length > 0 && (void 0 === t ? t = i : t += "/" + i);
				}
				return void 0 === t ? "." : n.normalize(t);
			},
			relative: function(t, r) {
				if (e(t), e(r), t === r) return "";
				if ((t = n.resolve(t)) === (r = n.resolve(r))) return "";
				for (var i = 1; i < t.length && 47 === t.charCodeAt(i); ++i);
				for (var o = t.length, s = o - i, h = 1; h < r.length && 47 === r.charCodeAt(h); ++h);
				for (var a = r.length - h, c = s < a ? s : a, f = -1, u = 0; u <= c; ++u) {
					if (u === c) {
						if (a > c) {
							if (47 === r.charCodeAt(h + u)) return r.slice(h + u + 1);
							if (0 === u) return r.slice(h + u);
						} else s > c && (47 === t.charCodeAt(i + u) ? f = u : 0 === u && (f = 0));
						break;
					}
					var l = t.charCodeAt(i + u);
					if (l !== r.charCodeAt(h + u)) break;
					47 === l && (f = u);
				}
				var g = "";
				for (u = i + f + 1; u <= o; ++u) u !== o && 47 !== t.charCodeAt(u) || (0 === g.length ? g += ".." : g += "/..");
				return g.length > 0 ? g + r.slice(h + f) : (h += f, 47 === r.charCodeAt(h) && ++h, r.slice(h));
			},
			_makeLong: function(t) {
				return t;
			},
			dirname: function(t) {
				if (e(t), 0 === t.length) return ".";
				for (var r = t.charCodeAt(0), n = 47 === r, i = -1, o = !0, s = t.length - 1; s >= 1; --s) if (47 === (r = t.charCodeAt(s))) {
					if (!o) {
						i = s;
						break;
					}
				} else o = !1;
				return -1 === i ? n ? "/" : "." : n && 1 === i ? "//" : t.slice(0, i);
			},
			basename: function(t, r) {
				if (void 0 !== r && "string" != typeof r) throw new TypeError("\"ext\" argument must be a string");
				e(t);
				var n, i = 0, o = -1, s = !0;
				if (void 0 !== r && r.length > 0 && r.length <= t.length) {
					if (r.length === t.length && r === t) return "";
					var h = r.length - 1, a = -1;
					for (n = t.length - 1; n >= 0; --n) {
						var c = t.charCodeAt(n);
						if (47 === c) {
							if (!s) {
								i = n + 1;
								break;
							}
						} else -1 === a && (s = !1, a = n + 1), h >= 0 && (c === r.charCodeAt(h) ? -1 == --h && (o = n) : (h = -1, o = a));
					}
					return i === o ? o = a : -1 === o && (o = t.length), t.slice(i, o);
				}
				for (n = t.length - 1; n >= 0; --n) if (47 === t.charCodeAt(n)) {
					if (!s) {
						i = n + 1;
						break;
					}
				} else -1 === o && (s = !1, o = n + 1);
				return -1 === o ? "" : t.slice(i, o);
			},
			extname: function(t) {
				e(t);
				for (var r = -1, n = 0, i = -1, o = !0, s = 0, h = t.length - 1; h >= 0; --h) {
					var a = t.charCodeAt(h);
					if (47 !== a) -1 === i && (o = !1, i = h + 1), 46 === a ? -1 === r ? r = h : 1 !== s && (s = 1) : -1 !== r && (s = -1);
					else if (!o) {
						n = h + 1;
						break;
					}
				}
				return -1 === r || -1 === i || 0 === s || 1 === s && r === i - 1 && r === n + 1 ? "" : t.slice(r, i);
			},
			format: function(t) {
				if (null === t || "object" != typeof t) throw new TypeError("The \"pathObject\" argument must be of type Object. Received type " + typeof t);
				return function(t, e) {
					var r = e.dir || e.root, n = e.base || (e.name || "") + (e.ext || "");
					return r ? r === e.root ? r + n : r + "/" + n : n;
				}(0, t);
			},
			parse: function(t) {
				e(t);
				var r = {
					root: "",
					dir: "",
					base: "",
					ext: "",
					name: ""
				};
				if (0 === t.length) return r;
				var n, i = t.charCodeAt(0), o = 47 === i;
				o ? (r.root = "/", n = 1) : n = 0;
				for (var s = -1, h = 0, a = -1, c = !0, f = t.length - 1, u = 0; f >= n; --f) if (47 !== (i = t.charCodeAt(f))) -1 === a && (c = !1, a = f + 1), 46 === i ? -1 === s ? s = f : 1 !== u && (u = 1) : -1 !== s && (u = -1);
				else if (!c) {
					h = f + 1;
					break;
				}
				return -1 === s || -1 === a || 0 === u || 1 === u && s === a - 1 && s === h + 1 ? -1 !== a && (r.base = r.name = 0 === h && o ? t.slice(1, a) : t.slice(h, a)) : (0 === h && o ? (r.name = t.slice(1, s), r.base = t.slice(1, a)) : (r.name = t.slice(h, s), r.base = t.slice(h, a)), r.ext = t.slice(s, a)), h > 0 ? r.dir = t.slice(0, h - 1) : o && (r.dir = "/"), r;
			},
			sep: "/",
			delimiter: ":",
			win32: null,
			posix: null
		};
		n.posix = n, t.exports = n;
	} }, e = {};
	function r(n) {
		var i = e[n];
		if (void 0 !== i) return i.exports;
		var o = e[n] = { exports: {} };
		return t[n](o, o.exports, r), o.exports;
	}
	r.d = (t, e) => {
		for (var n in e) r.o(e, n) && !r.o(t, n) && Object.defineProperty(t, n, {
			enumerable: !0,
			get: e[n]
		});
	}, r.o = (t, e) => Object.prototype.hasOwnProperty.call(t, e), r.r = (t) => {
		"undefined" != typeof Symbol && Symbol.toStringTag && Object.defineProperty(t, Symbol.toStringTag, { value: "Module" }), Object.defineProperty(t, "__esModule", { value: !0 });
	};
	var n = {};
	let i;
	if (r.r(n), r.d(n, {
		URI: () => l,
		Utils: () => I
	}), "object" == typeof process) i = "win32" === process.platform;
	else if ("object" == typeof navigator) i = navigator.userAgent.indexOf("Windows") >= 0;
	const o = /^\w[\w\d+.-]*$/, s = /^\//, h = /^\/\//;
	function a(t, e) {
		if (!t.scheme && e) throw new Error(`[UriError]: Scheme is missing: {scheme: "", authority: "${t.authority}", path: "${t.path}", query: "${t.query}", fragment: "${t.fragment}"}`);
		if (t.scheme && !o.test(t.scheme)) throw new Error("[UriError]: Scheme contains illegal characters.");
		if (t.path) {
			if (t.authority) {
				if (!s.test(t.path)) throw new Error("[UriError]: If a URI contains an authority component, then the path component must either be empty or begin with a slash (\"/\") character");
			} else if (h.test(t.path)) throw new Error("[UriError]: If a URI does not contain an authority component, then the path cannot begin with two slash characters (\"//\")");
		}
	}
	const c = "", f = "/", u = /^(([^:/?#]+?):)?(\/\/([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?/;
	class l {
		static isUri(t) {
			return t instanceof l || !!t && "string" == typeof t.authority && "string" == typeof t.fragment && "string" == typeof t.path && "string" == typeof t.query && "string" == typeof t.scheme && "string" == typeof t.fsPath && "function" == typeof t.with && "function" == typeof t.toString;
		}
		scheme;
		authority;
		path;
		query;
		fragment;
		constructor(t, e, r, n, i, o = !1) {
			"object" == typeof t ? (this.scheme = t.scheme || c, this.authority = t.authority || c, this.path = t.path || c, this.query = t.query || c, this.fragment = t.fragment || c) : (this.scheme = function(t, e) {
				return t || e ? t : "file";
			}(t, o), this.authority = e || c, this.path = function(t, e) {
				switch (t) {
					case "https":
					case "http":
					case "file": e ? e[0] !== f && (e = f + e) : e = f;
				}
				return e;
			}(this.scheme, r || c), this.query = n || c, this.fragment = i || c, a(this, o));
		}
		get fsPath() {
			return v(this, !1);
		}
		with(t) {
			if (!t) return this;
			let { scheme: e, authority: r, path: n, query: i, fragment: o } = t;
			return void 0 === e ? e = this.scheme : null === e && (e = c), void 0 === r ? r = this.authority : null === r && (r = c), void 0 === n ? n = this.path : null === n && (n = c), void 0 === i ? i = this.query : null === i && (i = c), void 0 === o ? o = this.fragment : null === o && (o = c), e === this.scheme && r === this.authority && n === this.path && i === this.query && o === this.fragment ? this : new d(e, r, n, i, o);
		}
		static parse(t, e = !1) {
			const r = u.exec(t);
			return r ? new d(r[2] || c, w(r[4] || c), w(r[5] || c), w(r[7] || c), w(r[9] || c), e) : new d(c, c, c, c, c);
		}
		static file(t) {
			let e = c;
			if (i && (t = t.replace(/\\/g, f)), t[0] === f && t[1] === f) {
				const r = t.indexOf(f, 2);
				-1 === r ? (e = t.substring(2), t = f) : (e = t.substring(2, r), t = t.substring(r) || f);
			}
			return new d("file", e, t, c, c);
		}
		static from(t) {
			const e = new d(t.scheme, t.authority, t.path, t.query, t.fragment);
			return a(e, !0), e;
		}
		toString(t = !1) {
			return b(this, t);
		}
		toJSON() {
			return this;
		}
		static revive(t) {
			if (t) {
				if (t instanceof l) return t;
				{
					const e = new d(t);
					return e._formatted = t.external, e._fsPath = t._sep === g ? t.fsPath : null, e;
				}
			}
			return t;
		}
	}
	const g = i ? 1 : void 0;
	class d extends l {
		_formatted = null;
		_fsPath = null;
		get fsPath() {
			return this._fsPath || (this._fsPath = v(this, !1)), this._fsPath;
		}
		toString(t = !1) {
			return t ? b(this, !0) : (this._formatted || (this._formatted = b(this, !1)), this._formatted);
		}
		toJSON() {
			const t = { $mid: 1 };
			return this._fsPath && (t.fsPath = this._fsPath, t._sep = g), this._formatted && (t.external = this._formatted), this.path && (t.path = this.path), this.scheme && (t.scheme = this.scheme), this.authority && (t.authority = this.authority), this.query && (t.query = this.query), this.fragment && (t.fragment = this.fragment), t;
		}
	}
	const p = {
		58: "%3A",
		47: "%2F",
		63: "%3F",
		35: "%23",
		91: "%5B",
		93: "%5D",
		64: "%40",
		33: "%21",
		36: "%24",
		38: "%26",
		39: "%27",
		40: "%28",
		41: "%29",
		42: "%2A",
		43: "%2B",
		44: "%2C",
		59: "%3B",
		61: "%3D",
		32: "%20"
	};
	function m(t, e, r) {
		let n, i = -1;
		for (let o = 0; o < t.length; o++) {
			const s = t.charCodeAt(o);
			if (s >= 97 && s <= 122 || s >= 65 && s <= 90 || s >= 48 && s <= 57 || 45 === s || 46 === s || 95 === s || 126 === s || e && 47 === s || r && 91 === s || r && 93 === s || r && 58 === s) -1 !== i && (n += encodeURIComponent(t.substring(i, o)), i = -1), void 0 !== n && (n += t.charAt(o));
			else {
				void 0 === n && (n = t.substr(0, o));
				const e = p[s];
				void 0 !== e ? (-1 !== i && (n += encodeURIComponent(t.substring(i, o)), i = -1), n += e) : -1 === i && (i = o);
			}
		}
		return -1 !== i && (n += encodeURIComponent(t.substring(i))), void 0 !== n ? n : t;
	}
	function y(t) {
		let e;
		for (let r = 0; r < t.length; r++) {
			const n = t.charCodeAt(r);
			35 === n || 63 === n ? (void 0 === e && (e = t.substr(0, r)), e += p[n]) : void 0 !== e && (e += t[r]);
		}
		return void 0 !== e ? e : t;
	}
	function v(t, e) {
		let r;
		return r = t.authority && t.path.length > 1 && "file" === t.scheme ? `//${t.authority}${t.path}` : 47 === t.path.charCodeAt(0) && (t.path.charCodeAt(1) >= 65 && t.path.charCodeAt(1) <= 90 || t.path.charCodeAt(1) >= 97 && t.path.charCodeAt(1) <= 122) && 58 === t.path.charCodeAt(2) ? e ? t.path.substr(1) : t.path[1].toLowerCase() + t.path.substr(2) : t.path, i && (r = r.replace(/\//g, "\\")), r;
	}
	function b(t, e) {
		const r = e ? y : m;
		let n = "", { scheme: i, authority: o, path: s, query: h, fragment: a } = t;
		if (i && (n += i, n += ":"), (o || "file" === i) && (n += f, n += f), o) {
			let t = o.indexOf("@");
			if (-1 !== t) {
				const e = o.substr(0, t);
				o = o.substr(t + 1), t = e.lastIndexOf(":"), -1 === t ? n += r(e, !1, !1) : (n += r(e.substr(0, t), !1, !1), n += ":", n += r(e.substr(t + 1), !1, !0)), n += "@";
			}
			o = o.toLowerCase(), t = o.lastIndexOf(":"), -1 === t ? n += r(o, !1, !0) : (n += r(o.substr(0, t), !1, !0), n += o.substr(t));
		}
		if (s) {
			if (s.length >= 3 && 47 === s.charCodeAt(0) && 58 === s.charCodeAt(2)) {
				const t = s.charCodeAt(1);
				t >= 65 && t <= 90 && (s = `/${String.fromCharCode(t + 32)}:${s.substr(3)}`);
			} else if (s.length >= 2 && 58 === s.charCodeAt(1)) {
				const t = s.charCodeAt(0);
				t >= 65 && t <= 90 && (s = `${String.fromCharCode(t + 32)}:${s.substr(2)}`);
			}
			n += r(s, !0, !1);
		}
		return h && (n += "?", n += r(h, !1, !1)), a && (n += "#", n += e ? a : m(a, !1, !1)), n;
	}
	function C(t) {
		try {
			return decodeURIComponent(t);
		} catch {
			return t.length > 3 ? t.substr(0, 3) + C(t.substr(3)) : t;
		}
	}
	const A = /(%[0-9A-Za-z][0-9A-Za-z])+/g;
	function w(t) {
		return t.match(A) ? t.replace(A, ((t) => C(t))) : t;
	}
	var x = r(975);
	const P = x.posix || x, _ = "/";
	var I;
	(function(t) {
		t.joinPath = function(t, ...e) {
			return t.with({ path: P.join(t.path, ...e) });
		}, t.resolvePath = function(t, ...e) {
			let r = t.path, n = !1;
			r[0] !== _ && (r = _ + r, n = !0);
			let i = P.resolve(r, ...e);
			return n && i[0] === _ && !t.authority && (i = i.substring(1)), t.with({ path: i });
		}, t.dirname = function(t) {
			if (0 === t.path.length || t.path === _) return t;
			let e = P.dirname(t.path);
			return 1 === e.length && 46 === e.charCodeAt(0) && (e = ""), t.with({ path: e });
		}, t.basename = function(t) {
			return P.basename(t.path);
		}, t.extname = function(t) {
			return P.extname(t.path);
		};
	})(I || (I = {})), LIB = n;
})();
var { URI, Utils } = LIB;
/******************************************************************************
* Copyright 2022 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var UriUtils;
(function(UriUtils) {
	UriUtils.basename = Utils.basename;
	UriUtils.dirname = Utils.dirname;
	UriUtils.extname = Utils.extname;
	UriUtils.joinPath = Utils.joinPath;
	UriUtils.resolvePath = Utils.resolvePath;
	const isWindows = typeof process === "object" && process?.platform === "win32";
	function equals(a, b) {
		return a?.toString() === b?.toString();
	}
	UriUtils.equals = equals;
	function relative(from, to) {
		const fromPath = typeof from === "string" ? URI.parse(from).path : from.path;
		const toPath = typeof to === "string" ? URI.parse(to).path : to.path;
		const fromParts = fromPath.split("/").filter((e) => e.length > 0);
		const toParts = toPath.split("/").filter((e) => e.length > 0);
		if (isWindows) {
			const upperCaseDriveLetter = /^[A-Z]:$/;
			if (fromParts[0] && upperCaseDriveLetter.test(fromParts[0])) fromParts[0] = fromParts[0].toLowerCase();
			if (toParts[0] && upperCaseDriveLetter.test(toParts[0])) toParts[0] = toParts[0].toLowerCase();
			if (fromParts[0] !== toParts[0]) return toPath.substring(1);
		}
		let i = 0;
		for (; i < fromParts.length; i++) if (fromParts[i] !== toParts[i]) break;
		return "../".repeat(fromParts.length - i) + toParts.slice(i).join("/");
	}
	UriUtils.relative = relative;
	function normalize(uri) {
		return URI.parse(uri.toString()).toString();
	}
	UriUtils.normalize = normalize;
	function contains(parent, child) {
		let parentPath = typeof parent === "string" ? parent : parent.path;
		let childPath = typeof child === "string" ? child : child.path;
		if (childPath.charAt(childPath.length - 1) === "/") childPath = childPath.slice(0, -1);
		if (parentPath.charAt(parentPath.length - 1) === "/") parentPath = parentPath.slice(0, -1);
		if (childPath === parentPath) return true;
		if (childPath.length < parentPath.length) return false;
		if (childPath.charAt(parentPath.length) !== "/") return false;
		return childPath.startsWith(parentPath);
	}
	UriUtils.contains = contains;
})(UriUtils || (UriUtils = {}));
/**
* A trie structure for URIs. It allows to insert, delete and find elements by their URI.
* More specifically, it allows to efficiently find all elements that are children of a given URI.
*
* Unlike a regular trie, this implementation uses the name of the URI segments as keys.
*
* @see {@link https://en.wikipedia.org/wiki/Trie}
*/
var UriTrie = class {
	constructor() {
		this.root = {
			name: "",
			children: /* @__PURE__ */ new Map()
		};
	}
	normalizeUri(uri) {
		return UriUtils.normalize(uri);
	}
	clear() {
		this.root.children.clear();
	}
	insert(uri, element) {
		const node = this.getNode(this.normalizeUri(uri), true);
		node.element = element;
	}
	delete(uri) {
		const nodeToDelete = this.getNode(this.normalizeUri(uri), false);
		if (nodeToDelete?.parent) nodeToDelete.parent.children.delete(nodeToDelete.name);
	}
	has(uri) {
		return this.getNode(this.normalizeUri(uri), false)?.element !== void 0;
	}
	hasNode(uri) {
		return this.getNode(this.normalizeUri(uri), false) !== void 0;
	}
	find(uri) {
		return this.getNode(this.normalizeUri(uri), false)?.element;
	}
	findNode(uri) {
		const uriString = this.normalizeUri(uri);
		const node = this.getNode(uriString, false);
		if (!node) return;
		return {
			name: node.name,
			uri: UriUtils.joinPath(URI.parse(uriString), node.name).toString(),
			element: node.element
		};
	}
	findChildren(uri) {
		const uriString = this.normalizeUri(uri);
		const node = this.getNode(uriString, false);
		if (!node) return [];
		return Array.from(node.children.values()).map((child) => ({
			name: child.name,
			uri: UriUtils.joinPath(URI.parse(uriString), child.name).toString(),
			element: child.element
		}));
	}
	all() {
		return this.collectValues(this.root);
	}
	findAll(prefix) {
		const node = this.getNode(UriUtils.normalize(prefix), false);
		if (!node) return [];
		return this.collectValues(node);
	}
	getNode(uri, create) {
		const parts = uri.split("/");
		if (uri.charAt(uri.length - 1) === "/") parts.pop();
		let current = this.root;
		for (const part of parts) {
			let child = current.children.get(part);
			if (!child) if (create) {
				child = {
					name: part,
					children: /* @__PURE__ */ new Map(),
					parent: current
				};
				current.children.set(part, child);
			} else return;
			current = child;
		}
		return current;
	}
	collectValues(node) {
		const result = [];
		if (node.element) result.push(node.element);
		for (const child of node.children.values()) result.push(...this.collectValues(child));
		return result;
	}
};
/**
* A document is subject to several phases that are run in predefined order. Any state value implies that
* smaller state values are finished as well.
*/
var DocumentState;
(function(DocumentState) {
	/**
	* The text content has changed and needs to be parsed again. The AST held by this outdated
	* document instance is no longer valid.
	*/
	DocumentState[DocumentState["Changed"] = 0] = "Changed";
	/**
	* An AST has been created from the text content. The document structure can be traversed,
	* but cross-references cannot be resolved yet. If necessary, the structure can be manipulated
	* at this stage as a preprocessing step.
	*/
	DocumentState[DocumentState["Parsed"] = 1] = "Parsed";
	/**
	* The `IndexManager` service has processed AST nodes of this document. This means the
	* exported symbols are available in the global scope and can be resolved from other documents.
	*/
	DocumentState[DocumentState["IndexedContent"] = 2] = "IndexedContent";
	/**
	* The `ScopeComputation` service has processed this document. This means the document's locally accessible
	* symbols are captured in a `DocumentSymbols` table and can be looked up by the `ScopeProvider` service.
	* Once a document has reached this state, you may follow every reference - it will lazily
	* resolve its `ref` property and yield either the target AST node or `undefined` in case
	* the target is not in scope.
	*/
	DocumentState[DocumentState["ComputedScopes"] = 3] = "ComputedScopes";
	/**
	* The `Linker` service has processed this document. All outgoing references have been
	* resolved or marked as erroneous.
	*/
	DocumentState[DocumentState["Linked"] = 4] = "Linked";
	/**
	* The `IndexManager` service has processed AST node references of this document. This is
	* necessary to determine which documents are affected by a change in one of the workspace
	* documents.
	*/
	DocumentState[DocumentState["IndexedReferences"] = 5] = "IndexedReferences";
	/**
	* The `DocumentValidator` service has processed this document. The language server listens
	* to the results of this phase and sends diagnostics to the client.
	*/
	DocumentState[DocumentState["Validated"] = 6] = "Validated";
})(DocumentState || (DocumentState = {}));
var DefaultLangiumDocumentFactory = class {
	constructor(services) {
		this.serviceRegistry = services.ServiceRegistry;
		this.textDocuments = services.workspace.TextDocuments;
		this.fileSystemProvider = services.workspace.FileSystemProvider;
	}
	async fromUri(uri, cancellationToken = cancellation_exports.CancellationToken.None) {
		const content = await this.fileSystemProvider.readFile(uri);
		return this.createAsync(uri, content, cancellationToken);
	}
	fromTextDocument(textDocument, uri, token) {
		uri = uri ?? URI.parse(textDocument.uri);
		if (cancellation_exports.CancellationToken.is(token)) return this.createAsync(uri, textDocument, token);
		else return this.create(uri, textDocument, token);
	}
	fromString(text, uri, token) {
		if (cancellation_exports.CancellationToken.is(token)) return this.createAsync(uri, text, token);
		else return this.create(uri, text, token);
	}
	fromModel(model, uri) {
		return this.create(uri, { $model: model });
	}
	create(uri, content, options) {
		if (typeof content === "string") {
			const parseResult = this.parse(uri, content, options);
			return this.createLangiumDocument(parseResult, uri, void 0, content);
		} else if ("$model" in content) {
			const parseResult = {
				value: content.$model,
				parserErrors: [],
				lexerErrors: []
			};
			return this.createLangiumDocument(parseResult, uri);
		} else {
			const parseResult = this.parse(uri, content.getText(), options);
			return this.createLangiumDocument(parseResult, uri, content);
		}
	}
	async createAsync(uri, content, cancelToken) {
		if (typeof content === "string") {
			const parseResult = await this.parseAsync(uri, content, cancelToken);
			return this.createLangiumDocument(parseResult, uri, void 0, content);
		} else {
			const parseResult = await this.parseAsync(uri, content.getText(), cancelToken);
			return this.createLangiumDocument(parseResult, uri, content);
		}
	}
	/**
	* Create a LangiumDocument from a given parse result.
	*
	* A TextDocument is created on demand if it is not provided as argument here. Usually this
	* should not be necessary because the main purpose of the TextDocument is to convert between
	* text ranges and offsets, which is done solely in LSP request handling.
	*
	* With the introduction of {@link update} below this method is supposed to be mainly called
	* during workspace initialization and on addition/recognition of new files, while changes in
	* existing documents are processed via {@link update}.
	*/
	createLangiumDocument(parseResult, uri, textDocument, text) {
		let document;
		if (textDocument) document = {
			parseResult,
			uri,
			state: DocumentState.Parsed,
			references: [],
			textDocument
		};
		else {
			const textDocumentGetter = this.createTextDocumentGetter(uri, text);
			document = {
				parseResult,
				uri,
				state: DocumentState.Parsed,
				references: [],
				get textDocument() {
					return textDocumentGetter();
				}
			};
		}
		parseResult.value.$document = document;
		return document;
	}
	async update(document, cancellationToken) {
		const oldText = document.parseResult.value.$cstNode?.root.fullText;
		const textDocument = this.textDocuments?.get(document.uri.toString());
		const text = textDocument ? textDocument.getText() : await this.fileSystemProvider.readFile(document.uri);
		if (textDocument) Object.defineProperty(document, "textDocument", { value: textDocument });
		else {
			const textDocumentGetter = this.createTextDocumentGetter(document.uri, text);
			Object.defineProperty(document, "textDocument", { get: textDocumentGetter });
		}
		if (oldText !== text) {
			document.parseResult = await this.parseAsync(document.uri, text, cancellationToken);
			document.parseResult.value.$document = document;
		}
		document.state = DocumentState.Parsed;
		return document;
	}
	parse(uri, text, options) {
		return this.serviceRegistry.getServices(uri).parser.LangiumParser.parse(text, options);
	}
	parseAsync(uri, text, cancellationToken) {
		return this.serviceRegistry.getServices(uri).parser.AsyncParser.parse(text, cancellationToken);
	}
	createTextDocumentGetter(uri, text) {
		const serviceRegistry = this.serviceRegistry;
		let textDoc = void 0;
		return () => {
			return textDoc ?? (textDoc = TextDocument.create(uri.toString(), serviceRegistry.getServices(uri).LanguageMetaData.languageId, 0, text ?? ""));
		};
	}
};
var DefaultLangiumDocuments = class {
	constructor(services) {
		this.documentTrie = new UriTrie();
		this.services = services;
		this.langiumDocumentFactory = services.workspace.LangiumDocumentFactory;
		this.documentBuilder = () => services.workspace.DocumentBuilder;
	}
	get all() {
		return stream(this.documentTrie.all());
	}
	addDocument(document) {
		const uriString = document.uri.toString();
		if (this.documentTrie.has(uriString)) throw new Error(`A document with the URI '${uriString}' is already present.`);
		this.documentTrie.insert(uriString, document);
	}
	getDocument(uri) {
		const uriString = uri.toString();
		return this.documentTrie.find(uriString);
	}
	getDocuments(folder) {
		const uriString = folder.toString();
		return this.documentTrie.findAll(uriString);
	}
	async getOrCreateDocument(uri, cancellationToken) {
		let document = this.getDocument(uri);
		if (document) return document;
		document = await this.langiumDocumentFactory.fromUri(uri, cancellationToken);
		this.addDocument(document);
		return document;
	}
	createDocument(uri, text, cancellationToken) {
		if (cancellationToken) return this.langiumDocumentFactory.fromString(text, uri, cancellationToken).then((document) => {
			this.addDocument(document);
			return document;
		});
		else {
			const document = this.langiumDocumentFactory.fromString(text, uri);
			this.addDocument(document);
			return document;
		}
	}
	hasDocument(uri) {
		return this.documentTrie.has(uri.toString());
	}
	/**
	* @deprecated Since 4.2 use `DocumentBuilder.resetToState(DocumentState.Changed)` instead
	* TODO remove this for the next major release
	*/
	invalidateDocument(uri) {
		const uriString = uri.toString();
		const langiumDoc = this.documentTrie.find(uriString);
		if (langiumDoc) this.documentBuilder().resetToState(langiumDoc, DocumentState.Changed);
		return langiumDoc;
	}
	deleteDocument(uri) {
		const uriString = uri.toString();
		const langiumDoc = this.documentTrie.find(uriString);
		if (langiumDoc) {
			langiumDoc.state = DocumentState.Changed;
			this.documentTrie.delete(uriString);
		}
		return langiumDoc;
	}
	deleteDocuments(folder) {
		const uriString = folder.toString();
		const langiumDocs = this.documentTrie.findAll(uriString);
		for (const langiumDoc of langiumDocs) langiumDoc.state = DocumentState.Changed;
		this.documentTrie.delete(uriString);
		return langiumDocs;
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var RefResolving = Symbol("RefResolving");
var DefaultLinker = class {
	constructor(services) {
		this.reflection = services.shared.AstReflection;
		this.langiumDocuments = () => services.shared.workspace.LangiumDocuments;
		this.scopeProvider = services.references.ScopeProvider;
		this.astNodeLocator = services.workspace.AstNodeLocator;
		this.profiler = services.shared.profilers.LangiumProfiler;
		this.languageId = services.LanguageMetaData.languageId;
	}
	async link(document, cancelToken = cancellation_exports.CancellationToken.None) {
		if (this.profiler?.isActive("linking")) {
			const task = this.profiler.createTask("linking", this.languageId);
			task.start();
			try {
				for (const node of streamAst(document.parseResult.value)) {
					await interruptAndCheck(cancelToken);
					streamReferences(node).forEach((ref) => {
						const name = `${node.$type}:${ref.property}`;
						task.startSubTask(name);
						try {
							this.doLink(ref, document);
						} finally {
							task.stopSubTask(name);
						}
					});
				}
			} finally {
				task.stop();
			}
		} else for (const node of streamAst(document.parseResult.value)) {
			await interruptAndCheck(cancelToken);
			streamReferences(node).forEach((ref) => this.doLink(ref, document));
		}
	}
	doLink(refInfo, document) {
		const ref = refInfo.reference;
		if ("_ref" in ref && ref._ref === void 0) {
			ref._ref = RefResolving;
			try {
				const description = this.getCandidate(refInfo);
				if (isLinkingError(description)) ref._ref = description;
				else {
					ref._nodeDescription = description;
					ref._ref = this.loadAstNode(description) ?? this.createLinkingError(refInfo, description);
				}
			} catch (err) {
				console.error(`An error occurred while resolving reference to '${ref.$refText}':`, err);
				const errorMessage = err.message ?? String(err);
				ref._ref = {
					info: refInfo,
					message: `An error occurred while resolving reference to '${ref.$refText}': ${errorMessage}`
				};
			}
			document.references.push(ref);
		} else if ("_items" in ref && ref._items === void 0) {
			ref._items = RefResolving;
			try {
				const descriptions = this.getCandidates(refInfo);
				const items = [];
				if (isLinkingError(descriptions)) ref._linkingError = descriptions;
				else for (const description of descriptions) {
					const linkedNode = this.loadAstNode(description);
					if (linkedNode) items.push({
						ref: linkedNode,
						$nodeDescription: description
					});
				}
				ref._items = items;
			} catch (err) {
				ref._linkingError = {
					info: refInfo,
					message: `An error occurred while resolving reference to '${ref.$refText}': ${err}`
				};
				ref._items = [];
			}
			document.references.push(ref);
		}
	}
	unlink(document) {
		for (const ref of document.references) if ("_ref" in ref) {
			ref._ref = void 0;
			delete ref._nodeDescription;
		} else if ("_items" in ref) {
			ref._items = void 0;
			delete ref._linkingError;
		}
		document.references = [];
	}
	getCandidate(refInfo) {
		return this.scopeProvider.getScope(refInfo).getElement(refInfo.reference.$refText) ?? this.createLinkingError(refInfo);
	}
	getCandidates(refInfo) {
		const descriptions = this.scopeProvider.getScope(refInfo).getElements(refInfo.reference.$refText).distinct((desc) => `${desc.documentUri}#${desc.path}`).toArray();
		return descriptions.length > 0 ? descriptions : this.createLinkingError(refInfo);
	}
	buildReference(node, property, refNode, refText) {
		const linker = this;
		const reference = {
			$refNode: refNode,
			$refText: refText,
			_ref: void 0,
			get ref() {
				if (isAstNode(this._ref)) return this._ref;
				else if (isAstNodeDescription(this._nodeDescription)) {
					const linkedNode = linker.loadAstNode(this._nodeDescription);
					this._ref = linkedNode ?? linker.createLinkingError({
						reference,
						container: node,
						property
					}, this._nodeDescription);
				} else if (this._ref === void 0) {
					this._ref = RefResolving;
					const document = findRootNode(node).$document;
					const refData = linker.getLinkedNode({
						reference,
						container: node,
						property
					});
					if (refData.error && document && document.state < DocumentState.ComputedScopes) {
						this._ref = void 0;
						return;
					}
					this._ref = refData.node ?? refData.error;
					this._nodeDescription = refData.descr;
					document?.references.push(this);
				} else if (this._ref === RefResolving) linker.throwCyclicReferenceError(node, property, refText);
				return isAstNode(this._ref) ? this._ref : void 0;
			},
			get $nodeDescription() {
				return this._nodeDescription;
			},
			get error() {
				return isLinkingError(this._ref) ? this._ref : void 0;
			}
		};
		return reference;
	}
	buildMultiReference(node, property, refNode, refText) {
		const linker = this;
		const reference = {
			$refNode: refNode,
			$refText: refText,
			_items: void 0,
			get items() {
				if (Array.isArray(this._items)) return this._items;
				else if (this._items === void 0) {
					this._items = RefResolving;
					const document = findRootNode(node).$document;
					const descriptions = linker.getCandidates({
						reference,
						container: node,
						property
					});
					const items = [];
					if (isLinkingError(descriptions)) this._linkingError = descriptions;
					else for (const description of descriptions) {
						const linkedNode = linker.loadAstNode(description);
						if (linkedNode) items.push({
							ref: linkedNode,
							$nodeDescription: description
						});
					}
					this._items = items;
					document?.references.push(this);
				} else if (this._items === RefResolving) linker.throwCyclicReferenceError(node, property, refText);
				return Array.isArray(this._items) ? this._items : [];
			},
			get error() {
				if (this._linkingError) return this._linkingError;
				if (this.items.length > 0) return;
				else return this._linkingError = linker.createLinkingError({
					reference,
					container: node,
					property
				});
			}
		};
		return reference;
	}
	throwCyclicReferenceError(node, property, refText) {
		throw new Error(`Cyclic reference resolution detected: ${this.astNodeLocator.getAstNodePath(node)}/${property} (symbol '${refText}')`);
	}
	getLinkedNode(refInfo) {
		try {
			const description = this.getCandidate(refInfo);
			if (isLinkingError(description)) return { error: description };
			const linkedNode = this.loadAstNode(description);
			if (linkedNode) return {
				node: linkedNode,
				descr: description
			};
			else return {
				descr: description,
				error: this.createLinkingError(refInfo, description)
			};
		} catch (err) {
			console.error(`An error occurred while resolving reference to '${refInfo.reference.$refText}':`, err);
			const errorMessage = err.message ?? String(err);
			return { error: {
				info: refInfo,
				message: `An error occurred while resolving reference to '${refInfo.reference.$refText}': ${errorMessage}`
			} };
		}
	}
	loadAstNode(nodeDescription) {
		if (nodeDescription.node) return nodeDescription.node;
		const doc = this.langiumDocuments().getDocument(nodeDescription.documentUri);
		if (!doc) return;
		return this.astNodeLocator.getAstNode(doc.parseResult.value, nodeDescription.path);
	}
	createLinkingError(refInfo, targetDescription) {
		const document = findRootNode(refInfo.container).$document;
		if (document && document.state < DocumentState.ComputedScopes) console.warn(`Attempted reference resolution before document reached ComputedScopes state (${document.uri}).`);
		return {
			info: refInfo,
			message: `Could not resolve reference to ${this.reflection.getReferenceType(refInfo)} named '${refInfo.reference.$refText}'.`,
			targetDescription
		};
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
function isNamed(node) {
	return typeof node.name === "string";
}
var DefaultNameProvider = class {
	getName(node) {
		if (isNamed(node)) return node.name;
	}
	getNameNode(node) {
		return findNodeForProperty(node.$cstNode, "name");
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultReferences = class {
	constructor(services) {
		this.nameProvider = services.references.NameProvider;
		this.index = services.shared.workspace.IndexManager;
		this.nodeLocator = services.workspace.AstNodeLocator;
		this.documents = services.shared.workspace.LangiumDocuments;
		this.hasMultiReference = streamAst(services.Grammar).some((node) => isCrossReference(node) && node.isMulti);
	}
	findDeclarations(sourceCstNode) {
		if (sourceCstNode) {
			const assignment = findAssignment(sourceCstNode);
			const nodeElem = sourceCstNode.astNode;
			if (assignment && nodeElem) {
				const reference = nodeElem[assignment.feature];
				if (isReference(reference) || isMultiReference(reference)) return getReferenceNodes(reference);
				else if (Array.isArray(reference)) {
					for (const ref of reference) if ((isReference(ref) || isMultiReference(ref)) && ref.$refNode && ref.$refNode.offset <= sourceCstNode.offset && ref.$refNode.end >= sourceCstNode.end) return getReferenceNodes(ref);
				}
			}
			if (nodeElem) {
				const nameNode = this.nameProvider.getNameNode(nodeElem);
				if (nameNode && (nameNode === sourceCstNode || isChildNode(sourceCstNode, nameNode))) return this.getSelfNodes(nodeElem);
			}
		}
		return [];
	}
	/**
	* Returns all self-references for the specified node.
	* Since the node can be part of a multi-reference, this method returns all nodes that are part of the same multi-reference.
	*/
	getSelfNodes(node) {
		if (!this.hasMultiReference) return [node];
		else {
			const references = this.index.findAllReferences(node, this.nodeLocator.getAstNodePath(node));
			const headNode = this.getNodeFromReferenceDescription(references.head());
			if (headNode) {
				for (const ref of streamReferences(headNode)) if (isMultiReference(ref.reference) && ref.reference.items.some((item) => item.ref === node)) return ref.reference.items.map((item) => item.ref);
			}
			return [node];
		}
	}
	getNodeFromReferenceDescription(ref) {
		if (!ref) return;
		const doc = this.documents.getDocument(ref.sourceUri);
		if (doc) return this.nodeLocator.getAstNode(doc.parseResult.value, ref.sourcePath);
	}
	findDeclarationNodes(sourceCstNode) {
		const astNodes = this.findDeclarations(sourceCstNode);
		const cstNodes = [];
		for (const astNode of astNodes) {
			const cstNode = this.nameProvider.getNameNode(astNode) ?? astNode.$cstNode;
			if (cstNode) cstNodes.push(cstNode);
		}
		return cstNodes;
	}
	findReferences(targetNode, options) {
		const refs = [];
		if (options.includeDeclaration) refs.push(...this.getSelfReferences(targetNode));
		let indexReferences = this.index.findAllReferences(targetNode, this.nodeLocator.getAstNodePath(targetNode));
		if (options.documentUri) indexReferences = indexReferences.filter((ref) => UriUtils.equals(ref.sourceUri, options.documentUri));
		refs.push(...indexReferences);
		return stream(refs);
	}
	getSelfReferences(targetNode) {
		const selfNodes = this.getSelfNodes(targetNode);
		const references = [];
		for (const selfNode of selfNodes) {
			const nameNode = this.nameProvider.getNameNode(selfNode);
			if (nameNode) {
				const doc = getDocument(selfNode);
				const path = this.nodeLocator.getAstNodePath(selfNode);
				references.push({
					sourceUri: doc.uri,
					sourcePath: path,
					targetUri: doc.uri,
					targetPath: path,
					segment: toDocumentSegment(nameNode),
					local: true
				});
			}
		}
		return references;
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* A multimap is a variation of a Map that has potentially multiple values for every key.
*/
var MultiMap = class {
	constructor(elements) {
		this.map = /* @__PURE__ */ new Map();
		if (elements) for (const [key, value] of elements) this.add(key, value);
	}
	/**
	* The total number of values in the multimap.
	*/
	get size() {
		return Reduction.sum(stream(this.map.values()).map((a) => a.length));
	}
	/**
	* Clear all entries in the multimap.
	*/
	clear() {
		this.map.clear();
	}
	/**
	* Operates differently depending on whether a `value` is given:
	*  * With a value, this method deletes the specific key / value pair from the multimap.
	*  * Without a value, all values associated with the given key are deleted.
	*
	* @returns `true` if a value existed and has been removed, or `false` if the specified
	*     key / value does not exist.
	*/
	delete(key, value) {
		if (value === void 0) return this.map.delete(key);
		else {
			const values = this.map.get(key);
			if (values) {
				const index = values.indexOf(value);
				if (index >= 0) {
					if (values.length === 1) this.map.delete(key);
					else values.splice(index, 1);
					return true;
				}
			}
			return false;
		}
	}
	/**
	* Returns an array of all values associated with the given key. If no value exists,
	* an empty array is returned.
	*
	* _Note:_ The returned array is assumed not to be modified. Use the `set` method to add a
	* value and `delete` to remove a value from the multimap.
	*/
	get(key) {
		return this.map.get(key) ?? [];
	}
	/**
	* Returns a stream of all values associated with the given key. If no value exists,
	* {@link EMPTY_STREAM} is returned.
	*/
	getStream(key) {
		const values = this.map.get(key);
		return values ? stream(values) : EMPTY_STREAM;
	}
	/**
	* Operates differently depending on whether a `value` is given:
	*  * With a value, this method returns `true` if the specific key / value pair is present in the multimap.
	*  * Without a value, this method returns `true` if the given key is present in the multimap.
	*/
	has(key, value) {
		if (value === void 0) return this.map.has(key);
		else {
			const values = this.map.get(key);
			if (values) return values.indexOf(value) >= 0;
			return false;
		}
	}
	/**
	* Add the given key / value pair to the multimap.
	*/
	add(key, value) {
		if (this.map.has(key)) this.map.get(key).push(value);
		else this.map.set(key, [value]);
		return this;
	}
	/**
	* Add the given set of key / value pairs to the multimap.
	*/
	addAll(key, values) {
		if (this.map.has(key)) this.map.get(key).push(...values);
		else this.map.set(key, Array.from(values));
		return this;
	}
	/**
	* Invokes the given callback function for every key / value pair in the multimap.
	*/
	forEach(callbackfn) {
		this.map.forEach((array, key) => array.forEach((value) => callbackfn(value, key, this)));
	}
	/**
	* Returns an iterator of key, value pairs for every entry in the map.
	*/
	[Symbol.iterator]() {
		return this.entries().iterator();
	}
	/**
	* Returns a stream of key, value pairs for every entry in the map.
	*/
	entries() {
		return stream(this.map.entries()).flatMap(([key, array]) => array.map((value) => [key, value]));
	}
	/**
	* Returns a stream of keys in the map.
	*/
	keys() {
		return stream(this.map.keys());
	}
	/**
	* Returns a stream of values in the map.
	*/
	values() {
		return stream(this.map.values()).flat();
	}
	/**
	* Returns a stream of key, value set pairs for every key in the map.
	*/
	entriesGroupedByKey() {
		return stream(this.map.entries());
	}
};
var BiMap = class {
	get size() {
		return this.map.size;
	}
	constructor(elements) {
		this.map = /* @__PURE__ */ new Map();
		this.inverse = /* @__PURE__ */ new Map();
		if (elements) for (const [key, value] of elements) this.set(key, value);
	}
	clear() {
		this.map.clear();
		this.inverse.clear();
	}
	set(key, value) {
		this.map.set(key, value);
		this.inverse.set(value, key);
		return this;
	}
	get(key) {
		return this.map.get(key);
	}
	getKey(value) {
		return this.inverse.get(value);
	}
	delete(key) {
		const value = this.map.get(key);
		if (value !== void 0) {
			this.map.delete(key);
			this.inverse.delete(value);
			return true;
		}
		return false;
	}
};
/******************************************************************************
* Copyright 2021-2022 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* The default scope computation creates and collects descriptions of the AST nodes to be exported into the
* _global_ scope from the given document. By default those are the document's root AST node and its directly
* contained child nodes.
*
* Besides, it gathers all AST nodes that have a name (according to the `NameProvider` service) and that are to be
* included in the local scope of their particular container nodes. They are collected in a `DocumentSymbols` table.
* As a result, for every cross-reference in the AST, target elements from the same level (siblings) and further up
* towards the root (parents and siblings of parents) are visible.
* Elements being nested inside lower levels (children, children of siblings and parents' siblings)
* are _invisible_ by default, but that can be changed by customizing this service.
*/
var DefaultScopeComputation = class {
	constructor(services) {
		this.nameProvider = services.references.NameProvider;
		this.descriptions = services.workspace.AstNodeDescriptionProvider;
	}
	async collectExportedSymbols(document, cancelToken = cancellation_exports.CancellationToken.None) {
		return this.collectExportedSymbolsForNode(document.parseResult.value, document, void 0, cancelToken);
	}
	/**
	* Creates {@link AstNodeDescription AstNodeDescriptions} for the given {@link AstNode parentNode} and its children.
	* The list of children to be considered is determined by the function parameter {@link children}.
	* By default only the direct children of {@link parentNode} are visited, nested nodes are not exported.
	*
	* @param parentNode AST node to be exported, i.e., of which an {@link AstNodeDescription} shall be added to the returned list.
	* @param document The document containing the AST node to be exported.
	* @param children A function called with {@link parentNode} as single argument and returning an {@link Iterable} supplying the children to be visited, which must be directly or transitively contained in {@link parentNode}.
	* @param cancelToken Indicates when to cancel the current operation.
	* @throws `OperationCancelled` if a user action occurs during execution.
	* @returns A list of {@link AstNodeDescription AstNodeDescriptions} to be published to index.
	*/
	async collectExportedSymbolsForNode(parentNode, document, children = streamContents, cancelToken = cancellation_exports.CancellationToken.None) {
		const exports = [];
		this.addExportedSymbol(parentNode, exports, document);
		for (const node of children(parentNode)) {
			await interruptAndCheck(cancelToken);
			this.addExportedSymbol(node, exports, document);
		}
		return exports;
	}
	/**
	* Adds a single node to the list of exports if it has a name. Override this method to change how
	* symbols are exported, e.g. by modifying their exported name.
	*/
	addExportedSymbol(node, exports, document) {
		const name = this.nameProvider.getName(node);
		if (name) exports.push(this.descriptions.createDescription(node, name, document));
	}
	async collectLocalSymbols(document, cancelToken = cancellation_exports.CancellationToken.None) {
		const rootNode = document.parseResult.value;
		const symbols = new MultiMap();
		for (const node of streamAllContents(rootNode)) {
			await interruptAndCheck(cancelToken);
			this.addLocalSymbol(node, document, symbols);
		}
		return symbols;
	}
	/**
	* Adds a single node to the local symbols of its containing document if it has a name.
	* The default implementation makes the node visible in the subtree of its container if it does have a container.
	* Override this method to change this, e.g. by increasing the visibility to a higher level in the AST.
	*/
	addLocalSymbol(node, document, symbols) {
		const container = node.$container;
		if (container) {
			const name = this.nameProvider.getName(node);
			if (name) symbols.add(container, this.descriptions.createDescription(node, name, document));
		}
	}
};
/******************************************************************************
* Copyright 2023 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* The default scope implementation is based on a `Stream`. It has an optional _outer scope_ describing
* the next level of elements, which are queried when a target element is not found in the stream provided
* to this scope.
*/
var StreamScope = class {
	constructor(elements, outerScope, options) {
		this.elements = elements;
		this.outerScope = outerScope;
		this.caseInsensitive = options?.caseInsensitive ?? false;
		this.concatOuterScope = options?.concatOuterScope ?? true;
	}
	getAllElements() {
		if (this.outerScope) return this.elements.concat(this.outerScope.getAllElements());
		else return this.elements;
	}
	getElement(name) {
		const lowerCaseName = this.caseInsensitive ? name.toLowerCase() : name;
		const local = this.caseInsensitive ? this.elements.find((e) => e.name.toLowerCase() === lowerCaseName) : this.elements.find((e) => e.name === name);
		if (local) return local;
		if (this.outerScope) return this.outerScope.getElement(name);
	}
	getElements(name) {
		const lowerCaseName = this.caseInsensitive ? name.toLowerCase() : name;
		const local = this.caseInsensitive ? this.elements.filter((e) => e.name.toLowerCase() === lowerCaseName) : this.elements.filter((e) => e.name === name);
		if ((this.concatOuterScope || local.isEmpty()) && this.outerScope) return local.concat(this.outerScope.getElements(name));
		else return local;
	}
};
var MultiMapScope = class {
	constructor(elements, outerScope, options) {
		this.elements = new MultiMap();
		this.caseInsensitive = options?.caseInsensitive ?? false;
		this.concatOuterScope = options?.concatOuterScope ?? true;
		for (const element of elements) {
			const name = this.caseInsensitive ? element.name.toLowerCase() : element.name;
			this.elements.add(name, element);
		}
		this.outerScope = outerScope;
	}
	getElement(name) {
		const localName = this.caseInsensitive ? name.toLowerCase() : name;
		const local = this.elements.get(localName)[0];
		if (local) return local;
		if (this.outerScope) return this.outerScope.getElement(name);
	}
	getElements(name) {
		const localName = this.caseInsensitive ? name.toLowerCase() : name;
		const local = this.elements.get(localName);
		if ((this.concatOuterScope || local.length === 0) && this.outerScope) return stream(local).concat(this.outerScope.getElements(name));
		else return stream(local);
	}
	getAllElements() {
		let elementStream = stream(this.elements.values());
		if (this.outerScope) elementStream = elementStream.concat(this.outerScope.getAllElements());
		return elementStream;
	}
};
/******************************************************************************
* Copyright 2023 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DisposableCache = class {
	constructor() {
		this.toDispose = [];
		this.isDisposed = false;
	}
	onDispose(disposable) {
		this.toDispose.push(disposable);
	}
	dispose() {
		this.throwIfDisposed();
		this.clear();
		this.isDisposed = true;
		this.toDispose.forEach((disposable) => disposable.dispose());
	}
	throwIfDisposed() {
		if (this.isDisposed) throw new Error("This cache has already been disposed");
	}
};
var SimpleCache = class extends DisposableCache {
	constructor() {
		super(...arguments);
		this.cache = /* @__PURE__ */ new Map();
	}
	has(key) {
		this.throwIfDisposed();
		return this.cache.has(key);
	}
	set(key, value) {
		this.throwIfDisposed();
		this.cache.set(key, value);
	}
	get(key, provider) {
		this.throwIfDisposed();
		if (this.cache.has(key)) return this.cache.get(key);
		else if (provider) {
			const value = provider();
			this.cache.set(key, value);
			return value;
		} else return;
	}
	delete(key) {
		this.throwIfDisposed();
		return this.cache.delete(key);
	}
	clear() {
		this.throwIfDisposed();
		this.cache.clear();
	}
};
var ContextCache = class extends DisposableCache {
	constructor(converter) {
		super();
		this.cache = /* @__PURE__ */ new Map();
		this.converter = converter ?? ((value) => value);
	}
	has(contextKey, key) {
		this.throwIfDisposed();
		return this.cacheForContext(contextKey).has(key);
	}
	set(contextKey, key, value) {
		this.throwIfDisposed();
		this.cacheForContext(contextKey).set(key, value);
	}
	get(contextKey, key, provider) {
		this.throwIfDisposed();
		const contextCache = this.cacheForContext(contextKey);
		if (contextCache.has(key)) return contextCache.get(key);
		else if (provider) {
			const value = provider();
			contextCache.set(key, value);
			return value;
		} else return;
	}
	delete(contextKey, key) {
		this.throwIfDisposed();
		return this.cacheForContext(contextKey).delete(key);
	}
	clear(contextKey) {
		this.throwIfDisposed();
		if (contextKey) {
			const mapKey = this.converter(contextKey);
			this.cache.delete(mapKey);
		} else this.cache.clear();
	}
	cacheForContext(contextKey) {
		const mapKey = this.converter(contextKey);
		let documentCache = this.cache.get(mapKey);
		if (!documentCache) {
			documentCache = /* @__PURE__ */ new Map();
			this.cache.set(mapKey, documentCache);
		}
		return documentCache;
	}
};
/**
* Every key/value pair in this cache is scoped to the whole workspace.
* If any document in the workspace is added, changed or deleted, the whole cache is evicted.
*/
var WorkspaceCache = class extends SimpleCache {
	/**
	* Creates a new workspace cache.
	*
	* @param sharedServices Service container instance to hook into document lifecycle events.
	* @param state Optional document state on which the cache should evict.
	* If not provided, the cache will evict on `DocumentBuilder#onUpdate`.
	* *Deleted* documents are considered in both cases.
	*/
	constructor(sharedServices, state) {
		super();
		if (state) {
			this.toDispose.push(sharedServices.workspace.DocumentBuilder.onBuildPhase(state, () => {
				this.clear();
			}));
			this.toDispose.push(sharedServices.workspace.DocumentBuilder.onUpdate((_changed, deleted) => {
				if (deleted.length > 0) this.clear();
			}));
		} else this.toDispose.push(sharedServices.workspace.DocumentBuilder.onUpdate(() => {
			this.clear();
		}));
	}
};
/******************************************************************************
* Copyright 2021-2022 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultScopeProvider = class {
	constructor(services) {
		this.reflection = services.shared.AstReflection;
		this.nameProvider = services.references.NameProvider;
		this.descriptions = services.workspace.AstNodeDescriptionProvider;
		this.indexManager = services.shared.workspace.IndexManager;
		this.globalScopeCache = new WorkspaceCache(services.shared);
	}
	getScope(context) {
		const scopes = [];
		const referenceType = this.reflection.getReferenceType(context);
		const localSymbols = getDocument(context.container).localSymbols;
		if (localSymbols) {
			let currentNode = context.container;
			do {
				if (localSymbols.has(currentNode)) scopes.push(localSymbols.getStream(currentNode).filter((desc) => this.reflection.isSubtype(desc.type, referenceType)));
				currentNode = currentNode.$container;
			} while (currentNode);
		}
		let result = this.getGlobalScope(referenceType, context);
		for (let i = scopes.length - 1; i >= 0; i--) result = this.createScope(scopes[i], result);
		return result;
	}
	/**
	* Create a scope for the given collection of AST node descriptions.
	*/
	createScope(elements, outerScope, options) {
		return new StreamScope(stream(elements), outerScope, options);
	}
	/**
	* Create a scope for the given collection of AST nodes, which need to be transformed into respective
	* descriptions first. This is done using the `NameProvider` and `AstNodeDescriptionProvider` services.
	*/
	createScopeForNodes(elements, outerScope, options) {
		return new StreamScope(stream(elements).map((e) => {
			const name = this.nameProvider.getName(e);
			if (name) return this.descriptions.createDescription(e, name);
		}).nonNullable(), outerScope, options);
	}
	/**
	* Create a global scope filtered for the given reference type.
	*/
	getGlobalScope(referenceType, _context) {
		return this.globalScopeCache.get(referenceType, () => new MultiMapScope(this.indexManager.allElements(referenceType)));
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
function isAstNodeWithComment(node) {
	return typeof node.$comment === "string";
}
function isIntermediateReference(obj) {
	return typeof obj === "object" && !!obj && ("$ref" in obj || "$error" in obj);
}
var DefaultJsonSerializer = class {
	constructor(services) {
		/** The set of AstNode properties to be ignored by the serializer. */
		this.ignoreProperties = new Set([
			"$container",
			"$containerProperty",
			"$containerIndex",
			"$document",
			"$cstNode"
		]);
		this.langiumDocuments = services.shared.workspace.LangiumDocuments;
		this.astNodeLocator = services.workspace.AstNodeLocator;
		this.nameProvider = services.references.NameProvider;
		this.commentProvider = services.documentation.CommentProvider;
	}
	serialize(node, options) {
		const serializeOptions = options ?? {};
		const specificReplacer = options?.replacer;
		const defaultReplacer = (key, value) => this.replacer(key, value, serializeOptions);
		const replacer = specificReplacer ? (key, value) => specificReplacer(key, value, defaultReplacer) : defaultReplacer;
		try {
			this.currentDocument = getDocument(node);
			return JSON.stringify(node, replacer, options?.space);
		} finally {
			this.currentDocument = void 0;
		}
	}
	deserialize(content, options) {
		const deserializeOptions = options ?? {};
		const root = JSON.parse(content);
		this.linkNode(root, root, deserializeOptions);
		return root;
	}
	replacer(key, value, { refText, sourceText, textRegions, comments, uriConverter }) {
		if (this.ignoreProperties.has(key)) return;
		else if (isReference(value)) {
			const refValue = value.ref;
			const $refText = refText ? value.$refText : void 0;
			if (refValue) {
				const targetDocument = getDocument(refValue);
				let targetUri = "";
				if (this.currentDocument && this.currentDocument !== targetDocument) if (uriConverter) targetUri = uriConverter(targetDocument.uri, refValue);
				else targetUri = targetDocument.uri.toString();
				const targetPath = this.astNodeLocator.getAstNodePath(refValue);
				return {
					$ref: `${targetUri}#${targetPath}`,
					$refText
				};
			} else return {
				$error: value.error?.message ?? "Could not resolve reference",
				$refText
			};
		} else if (isMultiReference(value)) {
			const $refText = refText ? value.$refText : void 0;
			const $refs = [];
			for (const item of value.items) {
				const refValue = item.ref;
				const targetDocument = getDocument(item.ref);
				let targetUri = "";
				if (this.currentDocument && this.currentDocument !== targetDocument) if (uriConverter) targetUri = uriConverter(targetDocument.uri, refValue);
				else targetUri = targetDocument.uri.toString();
				const targetPath = this.astNodeLocator.getAstNodePath(refValue);
				$refs.push(`${targetUri}#${targetPath}`);
			}
			return {
				$refs,
				$refText
			};
		} else if (isAstNode(value)) {
			let astNode = void 0;
			if (textRegions) {
				astNode = this.addAstNodeRegionWithAssignmentsTo({ ...value });
				if ((!key || value.$document) && astNode?.$textRegion) astNode.$textRegion.documentURI = this.currentDocument?.uri.toString();
			}
			if (sourceText && !key) {
				astNode ?? (astNode = { ...value });
				astNode.$sourceText = value.$cstNode?.text;
			}
			if (comments) {
				astNode ?? (astNode = { ...value });
				const comment = this.commentProvider.getComment(value);
				if (comment) astNode.$comment = comment.replace(/\r/g, "");
			}
			return astNode ?? value;
		} else return value;
	}
	addAstNodeRegionWithAssignmentsTo(node) {
		const createDocumentSegment = (cstNode) => ({
			offset: cstNode.offset,
			end: cstNode.end,
			length: cstNode.length,
			range: cstNode.range
		});
		if (node.$cstNode) {
			const textRegion = node.$textRegion = createDocumentSegment(node.$cstNode);
			const assignments = textRegion.assignments = {};
			Object.keys(node).filter((key) => !key.startsWith("$")).forEach((key) => {
				const propertyAssignments = findNodesForProperty(node.$cstNode, key).map(createDocumentSegment);
				if (propertyAssignments.length !== 0) assignments[key] = propertyAssignments;
			});
			return node;
		}
	}
	linkNode(node, root, options, container, containerProperty, containerIndex) {
		for (const [propertyName, item] of Object.entries(node)) if (Array.isArray(item)) for (let index = 0; index < item.length; index++) {
			const element = item[index];
			if (isIntermediateReference(element)) item[index] = this.reviveReference(node, propertyName, root, element, options);
			else if (isAstNode(element)) this.linkNode(element, root, options, node, propertyName, index);
		}
		else if (isIntermediateReference(item)) node[propertyName] = this.reviveReference(node, propertyName, root, item, options);
		else if (isAstNode(item)) this.linkNode(item, root, options, node, propertyName);
		const mutable = node;
		mutable.$container = container;
		mutable.$containerProperty = containerProperty;
		mutable.$containerIndex = containerIndex;
	}
	reviveReference(container, property, root, reference, options) {
		let refText = reference.$refText;
		let error = reference.$error;
		let ref;
		if (reference.$ref) {
			const refNode = this.getRefNode(root, reference.$ref, options.uriConverter);
			if (isAstNode(refNode)) {
				if (!refText) refText = this.nameProvider.getName(refNode);
				return {
					$refText: refText ?? "",
					ref: refNode
				};
			} else error = refNode;
		} else if (reference.$refs) {
			const refs = [];
			for (const refUri of reference.$refs) {
				const refNode = this.getRefNode(root, refUri, options.uriConverter);
				if (isAstNode(refNode)) refs.push({ ref: refNode });
			}
			if (refs.length === 0) {
				ref = {
					$refText: refText ?? "",
					items: refs
				};
				error ?? (error = "Could not resolve multi-reference");
			} else return {
				$refText: refText ?? "",
				items: refs
			};
		}
		if (error) {
			ref ?? (ref = {
				$refText: refText ?? "",
				ref: void 0
			});
			ref.error = {
				info: {
					container,
					property,
					reference: ref
				},
				message: error
			};
			return ref;
		} else return;
	}
	getRefNode(root, uri, uriConverter) {
		try {
			const fragmentIndex = uri.indexOf("#");
			if (fragmentIndex === 0) {
				const node = this.astNodeLocator.getAstNode(root, uri.substring(1));
				if (!node) return "Could not resolve path: " + uri;
				return node;
			}
			if (fragmentIndex < 0) {
				const documentUri = uriConverter ? uriConverter(uri) : URI.parse(uri);
				const document = this.langiumDocuments.getDocument(documentUri);
				if (!document) return "Could not find document for URI: " + uri;
				return document.parseResult.value;
			}
			const documentUri = uriConverter ? uriConverter(uri.substring(0, fragmentIndex)) : URI.parse(uri.substring(0, fragmentIndex));
			const document = this.langiumDocuments.getDocument(documentUri);
			if (!document) return "Could not find document for URI: " + uri;
			if (fragmentIndex === uri.length - 1) return document.parseResult.value;
			const node = this.astNodeLocator.getAstNode(document.parseResult.value, uri.substring(fragmentIndex + 1));
			if (!node) return "Could not resolve URI: " + uri;
			return node;
		} catch (err) {
			return String(err);
		}
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* Generic registry for Langium services, but capable of being used with extending service sets as well (such as the lsp-complete LangiumCoreServices set)
*/
var DefaultServiceRegistry = class {
	/**
	* @deprecated Since 3.1.0. Use the new `fileExtensionMap` (or `languageIdMap`) property instead.
	*/
	get map() {
		return this.fileExtensionMap;
	}
	constructor(services) {
		this.languageIdMap = /* @__PURE__ */ new Map();
		this.fileExtensionMap = /* @__PURE__ */ new Map();
		this.fileNameMap = /* @__PURE__ */ new Map();
		this.textDocuments = services?.workspace.TextDocuments;
	}
	register(language) {
		const data = language.LanguageMetaData;
		for (const ext of data.fileExtensions) {
			if (this.fileExtensionMap.has(ext)) console.warn(`The file extension ${ext} is used by multiple languages. It is now assigned to '${data.languageId}'.`);
			this.fileExtensionMap.set(ext, language);
		}
		if (data.fileNames) for (const name of data.fileNames) {
			if (this.fileNameMap.has(name)) console.warn(`The file name ${name} is used by multiple languages. It is now assigned to '${data.languageId}'.`);
			this.fileNameMap.set(name, language);
		}
		this.languageIdMap.set(data.languageId, language);
	}
	getServices(uri) {
		if (this.languageIdMap.size === 0) throw new Error("The service registry is empty. Use `register` to register the services of a language.");
		const languageId = this.textDocuments?.get(uri)?.languageId;
		if (languageId !== void 0) {
			const services = this.languageIdMap.get(languageId);
			if (services) return services;
		}
		const ext = UriUtils.extname(uri);
		const name = UriUtils.basename(uri);
		const services = this.fileNameMap.get(name) ?? this.fileExtensionMap.get(ext);
		if (!services) if (languageId) throw new Error(`The service registry contains no services for the extension '${ext}' for language '${languageId}'.`);
		else throw new Error(`The service registry contains no services for the extension '${ext}'.`);
		return services;
	}
	hasServices(uri) {
		try {
			this.getServices(uri);
			return true;
		} catch {
			return false;
		}
	}
	get all() {
		return Array.from(this.languageIdMap.values());
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* Create DiagnosticData for a given diagnostic code. The result can be put into the `data` field of a DiagnosticInfo.
*/
function diagnosticData(code) {
	return { code };
}
var ValidationCategory;
(function(ValidationCategory) {
	ValidationCategory.defaults = [
		"fast",
		"slow",
		"built-in"
	];
	/**
	* @deprecated since 4.2 Use `ValidationCategory.defaults` instead,
	* since "all" does not include user-defined, custom validation categories.
	*/
	ValidationCategory.all = ValidationCategory.defaults;
})(ValidationCategory || (ValidationCategory = {}));
/**
* Manages a set of `ValidationCheck`s to be applied when documents are validated.
*/
var ValidationRegistry = class {
	constructor(services) {
		this.entries = new MultiMap();
		this.knownCategories = new Set(ValidationCategory.defaults);
		this.entriesBefore = [];
		this.entriesAfter = [];
		this.reflection = services.shared.AstReflection;
	}
	/**
	* Register a set of validation checks. Each value in the record can be either a single validation check (i.e. a function)
	* or an array of validation checks.
	*
	* @param checksRecord Set of validation checks to register.
	* @param thisObj Optional object to be used as `this` when calling the validation check functions.
	* @param category Optional category for the validation checks (defaults to `'fast'`).
	*/
	register(checksRecord, thisObj = this, category = "fast") {
		if (category === "built-in") throw new Error("The 'built-in' category is reserved for lexer, parser, and linker errors.");
		this.knownCategories.add(category);
		for (const [type, ch] of Object.entries(checksRecord)) {
			const callbacks = ch;
			if (Array.isArray(callbacks)) for (const check of callbacks) {
				const entry = {
					check: this.wrapValidationException(check, thisObj),
					category
				};
				this.addEntry(type, entry);
			}
			else if (typeof callbacks === "function") {
				const entry = {
					check: this.wrapValidationException(callbacks, thisObj),
					category
				};
				this.addEntry(type, entry);
			} else assertUnreachable(callbacks);
		}
	}
	wrapValidationException(check, thisObj) {
		return async (node, accept, cancelToken) => {
			await this.handleException(() => check.call(thisObj, node, accept, cancelToken), "An error occurred during validation", accept, node);
		};
	}
	async handleException(functionality, messageContext, accept, node) {
		try {
			await functionality();
		} catch (err) {
			if (isOperationCancelled(err)) throw err;
			console.error(`${messageContext}:`, err);
			if (err instanceof Error && err.stack) console.error(err.stack);
			accept("error", `${messageContext}: ${err instanceof Error ? err.message : String(err)}`, { node });
		}
	}
	addEntry(type, entry) {
		if (type === "AstNode") {
			this.entries.add("AstNode", entry);
			return;
		}
		for (const subtype of this.reflection.getAllSubTypes(type)) this.entries.add(subtype, entry);
	}
	getChecks(type, categories) {
		let checks = stream(this.entries.get(type)).concat(this.entries.get("AstNode"));
		if (categories) checks = checks.filter((entry) => categories.includes(entry.category));
		return checks.map((entry) => entry.check);
	}
	/**
	* Register logic which will be executed once before validating all the nodes of an AST/Langium document.
	* This helps to prepare or initialize some information which are required or reusable for the following checks on the AstNodes.
	*
	* As an example, for validating unique fully-qualified names of nodes in the AST,
	* here the map for mapping names to nodes could be established.
	* During the usual checks on the nodes, they are put into this map with their name.
	*
	* Note that this approach makes validations stateful, which is relevant e.g. when cancelling the validation.
	* Therefore it is recommended to clear stored information
	* _before_ validating an AST to validate each AST unaffected from other ASTs
	* AND _after_ validating the AST to free memory by information which are no longer used.
	*
	* @param checkBefore a set-up function which will be called once before actually validating an AST
	* @param thisObj Optional object to be used as `this` when calling the validation check functions.
	*/
	registerBeforeDocument(checkBefore, thisObj = this) {
		this.entriesBefore.push(this.wrapPreparationException(checkBefore, "An error occurred during set-up of the validation", thisObj));
	}
	/**
	* Register logic which will be executed once after validating all the nodes of an AST/Langium document.
	* This helps to finally evaluate information which are collected during the checks on the AstNodes.
	*
	* As an example, for validating unique fully-qualified names of nodes in the AST,
	* here the map with all the collected nodes and their names is checked
	* and validation hints are created for all nodes with the same name.
	*
	* Note that this approach makes validations stateful, which is relevant e.g. when cancelling the validation.
	* Therefore it is recommended to clear stored information
	* _before_ validating an AST to validate each AST unaffected from other ASTs
	* AND _after_ validating the AST to free memory by information which are no longer used.
	*
	* @param checkBefore a set-up function which will be called once before actually validating an AST
	* @param thisObj Optional object to be used as `this` when calling the validation check functions.
	*/
	registerAfterDocument(checkAfter, thisObj = this) {
		this.entriesAfter.push(this.wrapPreparationException(checkAfter, "An error occurred during tear-down of the validation", thisObj));
	}
	wrapPreparationException(check, messageContext, thisObj) {
		return async (rootNode, accept, categories, cancelToken) => {
			await this.handleException(() => check.call(thisObj, rootNode, accept, categories, cancelToken), messageContext, accept, rootNode);
		};
	}
	get checksBefore() {
		return this.entriesBefore;
	}
	get checksAfter() {
		return this.entriesAfter;
	}
	getAllValidationCategories(_document) {
		return this.knownCategories;
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var VALIDATE_EACH_NODE = Object.freeze({
	validateNode: true,
	validateChildren: true
});
var DefaultDocumentValidator = class {
	constructor(services) {
		this.validationRegistry = services.validation.ValidationRegistry;
		this.metadata = services.LanguageMetaData;
		this.profiler = services.shared.profilers.LangiumProfiler;
		this.languageId = services.LanguageMetaData.languageId;
	}
	async validateDocument(document, options = {}, cancelToken = cancellation_exports.CancellationToken.None) {
		const parseResult = document.parseResult;
		const diagnostics = [];
		await interruptAndCheck(cancelToken);
		if (!options.categories || options.categories.includes("built-in")) {
			this.processLexingErrors(parseResult, diagnostics, options);
			if (options.stopAfterLexingErrors && diagnostics.some((d) => d.data?.code === DocumentValidator.LexingError)) return diagnostics;
			this.processParsingErrors(parseResult, diagnostics, options);
			if (options.stopAfterParsingErrors && diagnostics.some((d) => d.data?.code === DocumentValidator.ParsingError)) return diagnostics;
			this.processLinkingErrors(document, diagnostics, options);
			if (options.stopAfterLinkingErrors && diagnostics.some((d) => d.data?.code === DocumentValidator.LinkingError)) return diagnostics;
		}
		try {
			diagnostics.push(...await this.validateAst(parseResult.value, options, cancelToken));
		} catch (err) {
			if (isOperationCancelled(err)) throw err;
			console.error("An error occurred during validation:", err);
		}
		await interruptAndCheck(cancelToken);
		return diagnostics;
	}
	processLexingErrors(parseResult, diagnostics, _options) {
		const lexerDiagnostics = [...parseResult.lexerErrors, ...parseResult.lexerReport?.diagnostics ?? []];
		for (const lexerDiagnostic of lexerDiagnostics) {
			const severity = lexerDiagnostic.severity ?? "error";
			const diagnostic = {
				severity: toDiagnosticSeverity(severity),
				range: {
					start: {
						line: lexerDiagnostic.line - 1,
						character: lexerDiagnostic.column - 1
					},
					end: {
						line: lexerDiagnostic.line - 1,
						character: lexerDiagnostic.column + lexerDiagnostic.length - 1
					}
				},
				message: lexerDiagnostic.message,
				data: toDiagnosticData(severity),
				source: this.getSource()
			};
			diagnostics.push(diagnostic);
		}
	}
	processParsingErrors(parseResult, diagnostics, _options) {
		for (const parserError of parseResult.parserErrors) {
			let range = void 0;
			if (isNaN(parserError.token.startOffset)) {
				if ("previousToken" in parserError) {
					const token = parserError.previousToken;
					if (!isNaN(token.startOffset)) {
						const position = {
							line: token.endLine - 1,
							character: token.endColumn
						};
						range = {
							start: position,
							end: position
						};
					} else {
						const position = {
							line: 0,
							character: 0
						};
						range = {
							start: position,
							end: position
						};
					}
				}
			} else range = tokenToRange(parserError.token);
			if (range) {
				const diagnostic = {
					severity: toDiagnosticSeverity("error"),
					range,
					message: parserError.message,
					data: diagnosticData(DocumentValidator.ParsingError),
					source: this.getSource()
				};
				diagnostics.push(diagnostic);
			}
		}
	}
	processLinkingErrors(document, diagnostics, _options) {
		for (const reference of document.references) {
			const linkingError = reference.error;
			if (linkingError) {
				const info = {
					node: linkingError.info.container,
					range: reference.$refNode?.range,
					property: linkingError.info.property,
					index: linkingError.info.index,
					data: {
						code: DocumentValidator.LinkingError,
						containerType: linkingError.info.container.$type,
						property: linkingError.info.property,
						refText: linkingError.info.reference.$refText
					}
				};
				diagnostics.push(this.toDiagnostic("error", linkingError.message, info));
			}
		}
	}
	async validateAst(rootNode, options, cancelToken = cancellation_exports.CancellationToken.None) {
		const validationItems = [];
		const acceptor = (severity, message, info) => {
			validationItems.push(this.toDiagnostic(severity, message, info));
		};
		await this.validateAstBefore(rootNode, options, acceptor, cancelToken);
		await this.validateAstNodes(rootNode, options, acceptor, cancelToken);
		await this.validateAstAfter(rootNode, options, acceptor, cancelToken);
		return validationItems;
	}
	async validateAstBefore(rootNode, options, acceptor, cancelToken = cancellation_exports.CancellationToken.None) {
		const checksBefore = this.validationRegistry.checksBefore;
		for (const checkBefore of checksBefore) {
			await interruptAndCheck(cancelToken);
			await checkBefore(rootNode, acceptor, options.categories ?? [], cancelToken);
		}
	}
	async validateAstNodes(rootNode, options, acceptor, cancelToken = cancellation_exports.CancellationToken.None) {
		if (this.profiler?.isActive("validating")) {
			const task = this.profiler.createTask("validating", this.languageId);
			task.start();
			try {
				const nodes = streamAst(rootNode).iterator();
				for (const node of nodes) {
					task.startSubTask(node.$type);
					const nodeOptions = this.validateSingleNodeOptions(node, options);
					if (nodeOptions.validateNode) try {
						const checks = this.validationRegistry.getChecks(node.$type, options.categories);
						for (const check of checks) await check(node, acceptor, cancelToken);
					} finally {
						task.stopSubTask(node.$type);
					}
					if (!nodeOptions.validateChildren) nodes.prune();
				}
			} finally {
				task.stop();
			}
		} else {
			const nodes = streamAst(rootNode).iterator();
			for (const node of nodes) {
				await interruptAndCheck(cancelToken);
				const nodeOptions = this.validateSingleNodeOptions(node, options);
				if (nodeOptions.validateNode) {
					const checks = this.validationRegistry.getChecks(node.$type, options.categories);
					for (const check of checks) await check(node, acceptor, cancelToken);
				}
				if (!nodeOptions.validateChildren) nodes.prune();
			}
		}
	}
	validateSingleNodeOptions(_node, _options) {
		return VALIDATE_EACH_NODE;
	}
	async validateAstAfter(rootNode, options, acceptor, cancelToken = cancellation_exports.CancellationToken.None) {
		const checksAfter = this.validationRegistry.checksAfter;
		for (const checkAfter of checksAfter) {
			await interruptAndCheck(cancelToken);
			await checkAfter(rootNode, acceptor, options.categories ?? [], cancelToken);
		}
	}
	toDiagnostic(severity, message, info) {
		return {
			message,
			range: getDiagnosticRange(info),
			severity: toDiagnosticSeverity(severity),
			code: info.code,
			codeDescription: info.codeDescription,
			tags: info.tags,
			relatedInformation: info.relatedInformation,
			data: info.data,
			source: this.getSource()
		};
	}
	getSource() {
		return this.metadata.languageId;
	}
};
function getDiagnosticRange(info) {
	if (info.range) return info.range;
	let cstNode;
	if (typeof info.property === "string") cstNode = findNodeForProperty(info.node.$cstNode, info.property, info.index);
	else if (typeof info.keyword === "string") cstNode = findNodeForKeyword(info.node.$cstNode, info.keyword, info.index);
	cstNode ?? (cstNode = info.node.$cstNode);
	if (!cstNode) return {
		start: {
			line: 0,
			character: 0
		},
		end: {
			line: 0,
			character: 0
		}
	};
	return cstNode.range;
}
/**
* Transforms the diagnostic severity from the {@link LexingDiagnosticSeverity} format to LSP's `DiagnosticSeverity` format.
*
* @param severity The lexing diagnostic severity
* @returns Diagnostic severity according to `vscode-languageserver-types/lib/esm/main.js#DiagnosticSeverity`
*/
function toDiagnosticSeverity(severity) {
	switch (severity) {
		case "error": return 1;
		case "warning": return 2;
		case "info": return 3;
		case "hint": return 4;
		default: throw new Error("Invalid diagnostic severity: " + severity);
	}
}
function toDiagnosticData(severity) {
	switch (severity) {
		case "error": return diagnosticData(DocumentValidator.LexingError);
		case "warning": return diagnosticData(DocumentValidator.LexingWarning);
		case "info": return diagnosticData(DocumentValidator.LexingInfo);
		case "hint": return diagnosticData(DocumentValidator.LexingHint);
		default: throw new Error("Invalid diagnostic severity: " + severity);
	}
}
var DocumentValidator;
(function(DocumentValidator) {
	DocumentValidator.LexingError = "lexing-error";
	DocumentValidator.LexingWarning = "lexing-warning";
	DocumentValidator.LexingInfo = "lexing-info";
	DocumentValidator.LexingHint = "lexing-hint";
	DocumentValidator.ParsingError = "parsing-error";
	DocumentValidator.LinkingError = "linking-error";
})(DocumentValidator || (DocumentValidator = {}));
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultAstNodeDescriptionProvider = class {
	constructor(services) {
		this.astNodeLocator = services.workspace.AstNodeLocator;
		this.nameProvider = services.references.NameProvider;
	}
	createDescription(node, name, document) {
		const doc = document ?? getDocument(node);
		name ?? (name = this.nameProvider.getName(node));
		const path = this.astNodeLocator.getAstNodePath(node);
		if (!name) throw new Error(`Node at path ${path} has no name.`);
		let nameNodeSegment;
		const nameSegmentGetter = () => nameNodeSegment ?? (nameNodeSegment = toDocumentSegment(this.nameProvider.getNameNode(node) ?? node.$cstNode));
		return {
			node,
			name,
			get nameSegment() {
				return nameSegmentGetter();
			},
			selectionSegment: toDocumentSegment(node.$cstNode),
			type: node.$type,
			documentUri: doc.uri,
			path
		};
	}
};
var DefaultReferenceDescriptionProvider = class {
	constructor(services) {
		this.nodeLocator = services.workspace.AstNodeLocator;
	}
	async createDescriptions(document, cancelToken = cancellation_exports.CancellationToken.None) {
		const descr = [];
		const rootNode = document.parseResult.value;
		for (const astNode of streamAst(rootNode)) {
			await interruptAndCheck(cancelToken);
			streamReferences(astNode).forEach((refInfo) => {
				if (!refInfo.reference.error) descr.push(...this.createInfoDescriptions(refInfo));
			});
		}
		return descr;
	}
	createInfoDescriptions(refInfo) {
		const reference = refInfo.reference;
		if (reference.error || !reference.$refNode) return [];
		let items = [];
		if (isReference(reference) && reference.$nodeDescription) items = [reference.$nodeDescription];
		else if (isMultiReference(reference)) items = reference.items.map((e) => e.$nodeDescription).filter((e) => e !== void 0);
		const sourceUri = getDocument(refInfo.container).uri;
		const sourcePath = this.nodeLocator.getAstNodePath(refInfo.container);
		const descriptions = [];
		const segment = toDocumentSegment(reference.$refNode);
		for (const item of items) descriptions.push({
			sourceUri,
			sourcePath,
			targetUri: item.documentUri,
			targetPath: item.path,
			segment,
			local: UriUtils.equals(item.documentUri, sourceUri)
		});
		return descriptions;
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultAstNodeLocator = class {
	constructor() {
		this.segmentSeparator = "/";
		this.indexSeparator = "@";
	}
	getAstNodePath(node) {
		if (node.$container) {
			const containerPath = this.getAstNodePath(node.$container);
			const newSegment = this.getPathSegment(node);
			return containerPath + this.segmentSeparator + newSegment;
		}
		return "";
	}
	getPathSegment({ $containerProperty, $containerIndex }) {
		if (!$containerProperty) throw new Error("Missing '$containerProperty' in AST node.");
		if ($containerIndex !== void 0) return $containerProperty + this.indexSeparator + $containerIndex;
		return $containerProperty;
	}
	getAstNode(node, path) {
		return path.split(this.segmentSeparator).reduce((previousValue, currentValue) => {
			if (!previousValue || currentValue.length === 0) return previousValue;
			const propertyIndex = currentValue.indexOf(this.indexSeparator);
			if (propertyIndex > 0) {
				const property = currentValue.substring(0, propertyIndex);
				const arrayIndex = parseInt(currentValue.substring(propertyIndex + 1));
				return previousValue[property]?.[arrayIndex];
			}
			return previousValue[currentValue];
		}, node);
	}
};
var event_exports = /* @__PURE__ */ __exportAll({});
__reExport(event_exports, /* @__PURE__ */ __toESM(require_events(), 1));
/******************************************************************************
* Copyright 2022 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* Base configuration provider for building up other configuration providers
*/
var DefaultConfigurationProvider = class {
	constructor(services) {
		this._ready = new Deferred();
		this.onConfigurationSectionUpdateEmitter = new event_exports.Emitter();
		this.settings = {};
		this.workspaceConfig = false;
		this.serviceRegistry = services.ServiceRegistry;
	}
	get ready() {
		return this._ready.promise;
	}
	initialize(params) {
		this.workspaceConfig = params.capabilities.workspace?.configuration ?? false;
	}
	async initialized(params) {
		if (this.workspaceConfig) {
			if (params.register) {
				const languages = this.serviceRegistry.all;
				params.register({ section: languages.map((lang) => this.toSectionName(lang.LanguageMetaData.languageId)) });
			}
			if (params.fetchConfiguration) {
				const configToUpdate = this.serviceRegistry.all.map((lang) => ({ section: this.toSectionName(lang.LanguageMetaData.languageId) }));
				const configs = await params.fetchConfiguration(configToUpdate);
				configToUpdate.forEach((conf, idx) => {
					this.updateSectionConfiguration(conf.section, configs[idx]);
				});
			}
		}
		this._ready.resolve();
	}
	/**
	*  Updates the cached configurations using the `change` notification parameters.
	*
	* @param change The parameters of a change configuration notification.
	* `settings` property of the change object could be expressed as `Record<string, Record<string, any>>`
	*/
	updateConfiguration(change) {
		if (typeof change.settings !== "object" || change.settings === null) return;
		Object.entries(change.settings).forEach(([section, configuration]) => {
			this.updateSectionConfiguration(section, configuration);
			this.onConfigurationSectionUpdateEmitter.fire({
				section,
				configuration
			});
		});
	}
	updateSectionConfiguration(section, configuration) {
		this.settings[section] = configuration;
	}
	/**
	* Returns a configuration value stored for the given language.
	*
	* @param language The language id
	* @param configuration Configuration name
	*/
	async getConfiguration(language, configuration) {
		await this.ready;
		const sectionName = this.toSectionName(language);
		if (this.settings[sectionName]) return this.settings[sectionName][configuration];
	}
	toSectionName(languageId) {
		return `${languageId}`;
	}
	get onConfigurationSectionUpdate() {
		return this.onConfigurationSectionUpdateEmitter.event;
	}
};
var require_messages$1 = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.Message = exports.NotificationType9 = exports.NotificationType8 = exports.NotificationType7 = exports.NotificationType6 = exports.NotificationType5 = exports.NotificationType4 = exports.NotificationType3 = exports.NotificationType2 = exports.NotificationType1 = exports.NotificationType0 = exports.NotificationType = exports.RequestType9 = exports.RequestType8 = exports.RequestType7 = exports.RequestType6 = exports.RequestType5 = exports.RequestType4 = exports.RequestType3 = exports.RequestType2 = exports.RequestType1 = exports.RequestType = exports.RequestType0 = exports.AbstractMessageSignature = exports.ParameterStructures = exports.ResponseError = exports.ErrorCodes = void 0;
	var is = require_is$1();
	/**
	* Predefined error codes.
	*/
	var ErrorCodes;
	(function(ErrorCodes) {
		ErrorCodes.ParseError = -32700;
		ErrorCodes.InvalidRequest = -32600;
		ErrorCodes.MethodNotFound = -32601;
		ErrorCodes.InvalidParams = -32602;
		ErrorCodes.InternalError = -32603;
		/**
		* This is the start range of JSON RPC reserved error codes.
		* It doesn't denote a real error code. No application error codes should
		* be defined between the start and end range. For backwards
		* compatibility the `ServerNotInitialized` and the `UnknownErrorCode`
		* are left in the range.
		*
		* @since 3.16.0
		*/
		ErrorCodes.jsonrpcReservedErrorRangeStart = -32099;
		/** @deprecated use  jsonrpcReservedErrorRangeStart */
		ErrorCodes.serverErrorStart = -32099;
		/**
		* An error occurred when write a message to the transport layer.
		*/
		ErrorCodes.MessageWriteError = -32099;
		/**
		* An error occurred when reading a message from the transport layer.
		*/
		ErrorCodes.MessageReadError = -32098;
		/**
		* The connection got disposed or lost and all pending responses got
		* rejected.
		*/
		ErrorCodes.PendingResponseRejected = -32097;
		/**
		* The connection is inactive and a use of it failed.
		*/
		ErrorCodes.ConnectionInactive = -32096;
		/**
		* Error code indicating that a server received a notification or
		* request before the server has received the `initialize` request.
		*/
		ErrorCodes.ServerNotInitialized = -32002;
		ErrorCodes.UnknownErrorCode = -32001;
		/**
		* This is the end range of JSON RPC reserved error codes.
		* It doesn't denote a real error code.
		*
		* @since 3.16.0
		*/
		ErrorCodes.jsonrpcReservedErrorRangeEnd = -32e3;
		/** @deprecated use  jsonrpcReservedErrorRangeEnd */
		ErrorCodes.serverErrorEnd = -32e3;
	})(ErrorCodes || (exports.ErrorCodes = ErrorCodes = {}));
	exports.ResponseError = class ResponseError extends Error {
		constructor(code, message, data) {
			super(message);
			this.code = is.number(code) ? code : ErrorCodes.UnknownErrorCode;
			this.data = data;
			Object.setPrototypeOf(this, ResponseError.prototype);
		}
		toJson() {
			const result = {
				code: this.code,
				message: this.message
			};
			if (this.data !== void 0) result.data = this.data;
			return result;
		}
	};
	var ParameterStructures = class ParameterStructures {
		constructor(kind) {
			this.kind = kind;
		}
		static is(value) {
			return value === ParameterStructures.auto || value === ParameterStructures.byName || value === ParameterStructures.byPosition;
		}
		toString() {
			return this.kind;
		}
	};
	exports.ParameterStructures = ParameterStructures;
	/**
	* The parameter structure is automatically inferred on the number of parameters
	* and the parameter type in case of a single param.
	*/
	ParameterStructures.auto = new ParameterStructures("auto");
	/**
	* Forces `byPosition` parameter structure. This is useful if you have a single
	* parameter which has a literal type.
	*/
	ParameterStructures.byPosition = new ParameterStructures("byPosition");
	/**
	* Forces `byName` parameter structure. This is only useful when having a single
	* parameter. The library will report errors if used with a different number of
	* parameters.
	*/
	ParameterStructures.byName = new ParameterStructures("byName");
	/**
	* An abstract implementation of a MessageType.
	*/
	var AbstractMessageSignature = class {
		constructor(method, numberOfParams) {
			this.method = method;
			this.numberOfParams = numberOfParams;
		}
		get parameterStructures() {
			return ParameterStructures.auto;
		}
	};
	exports.AbstractMessageSignature = AbstractMessageSignature;
	/**
	* Classes to type request response pairs
	*/
	var RequestType0 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 0);
		}
	};
	exports.RequestType0 = RequestType0;
	var RequestType = class extends AbstractMessageSignature {
		constructor(method, _parameterStructures = ParameterStructures.auto) {
			super(method, 1);
			this._parameterStructures = _parameterStructures;
		}
		get parameterStructures() {
			return this._parameterStructures;
		}
	};
	exports.RequestType = RequestType;
	var RequestType1 = class extends AbstractMessageSignature {
		constructor(method, _parameterStructures = ParameterStructures.auto) {
			super(method, 1);
			this._parameterStructures = _parameterStructures;
		}
		get parameterStructures() {
			return this._parameterStructures;
		}
	};
	exports.RequestType1 = RequestType1;
	var RequestType2 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 2);
		}
	};
	exports.RequestType2 = RequestType2;
	var RequestType3 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 3);
		}
	};
	exports.RequestType3 = RequestType3;
	var RequestType4 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 4);
		}
	};
	exports.RequestType4 = RequestType4;
	var RequestType5 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 5);
		}
	};
	exports.RequestType5 = RequestType5;
	var RequestType6 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 6);
		}
	};
	exports.RequestType6 = RequestType6;
	var RequestType7 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 7);
		}
	};
	exports.RequestType7 = RequestType7;
	var RequestType8 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 8);
		}
	};
	exports.RequestType8 = RequestType8;
	var RequestType9 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 9);
		}
	};
	exports.RequestType9 = RequestType9;
	var NotificationType = class extends AbstractMessageSignature {
		constructor(method, _parameterStructures = ParameterStructures.auto) {
			super(method, 1);
			this._parameterStructures = _parameterStructures;
		}
		get parameterStructures() {
			return this._parameterStructures;
		}
	};
	exports.NotificationType = NotificationType;
	var NotificationType0 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 0);
		}
	};
	exports.NotificationType0 = NotificationType0;
	var NotificationType1 = class extends AbstractMessageSignature {
		constructor(method, _parameterStructures = ParameterStructures.auto) {
			super(method, 1);
			this._parameterStructures = _parameterStructures;
		}
		get parameterStructures() {
			return this._parameterStructures;
		}
	};
	exports.NotificationType1 = NotificationType1;
	var NotificationType2 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 2);
		}
	};
	exports.NotificationType2 = NotificationType2;
	var NotificationType3 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 3);
		}
	};
	exports.NotificationType3 = NotificationType3;
	var NotificationType4 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 4);
		}
	};
	exports.NotificationType4 = NotificationType4;
	var NotificationType5 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 5);
		}
	};
	exports.NotificationType5 = NotificationType5;
	var NotificationType6 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 6);
		}
	};
	exports.NotificationType6 = NotificationType6;
	var NotificationType7 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 7);
		}
	};
	exports.NotificationType7 = NotificationType7;
	var NotificationType8 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 8);
		}
	};
	exports.NotificationType8 = NotificationType8;
	var NotificationType9 = class extends AbstractMessageSignature {
		constructor(method) {
			super(method, 9);
		}
	};
	exports.NotificationType9 = NotificationType9;
	var Message;
	(function(Message) {
		/**
		* Tests if the given message is a request message
		*/
		function isRequest(message) {
			const candidate = message;
			return candidate && is.string(candidate.method) && (is.string(candidate.id) || is.number(candidate.id));
		}
		Message.isRequest = isRequest;
		/**
		* Tests if the given message is a notification message
		*/
		function isNotification(message) {
			const candidate = message;
			return candidate && is.string(candidate.method) && message.id === void 0;
		}
		Message.isNotification = isNotification;
		/**
		* Tests if the given message is a response message
		*/
		function isResponse(message) {
			const candidate = message;
			return candidate && (candidate.result !== void 0 || !!candidate.error) && (is.string(candidate.id) || is.number(candidate.id) || candidate.id === null);
		}
		Message.isResponse = isResponse;
	})(Message || (exports.Message = Message = {}));
}));
var require_linkedMap = /* @__PURE__ */ __commonJSMin(((exports) => {
	var _a;
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.LRUCache = exports.LinkedMap = exports.Touch = void 0;
	var Touch;
	(function(Touch) {
		Touch.None = 0;
		Touch.First = 1;
		Touch.AsOld = Touch.First;
		Touch.Last = 2;
		Touch.AsNew = Touch.Last;
	})(Touch || (exports.Touch = Touch = {}));
	var LinkedMap = class {
		constructor() {
			this[_a] = "LinkedMap";
			this._map = /* @__PURE__ */ new Map();
			this._head = void 0;
			this._tail = void 0;
			this._size = 0;
			this._state = 0;
		}
		clear() {
			this._map.clear();
			this._head = void 0;
			this._tail = void 0;
			this._size = 0;
			this._state++;
		}
		isEmpty() {
			return !this._head && !this._tail;
		}
		get size() {
			return this._size;
		}
		get first() {
			return this._head?.value;
		}
		get last() {
			return this._tail?.value;
		}
		has(key) {
			return this._map.has(key);
		}
		get(key, touch = Touch.None) {
			const item = this._map.get(key);
			if (!item) return;
			if (touch !== Touch.None) this.touch(item, touch);
			return item.value;
		}
		set(key, value, touch = Touch.None) {
			let item = this._map.get(key);
			if (item) {
				item.value = value;
				if (touch !== Touch.None) this.touch(item, touch);
			} else {
				item = {
					key,
					value,
					next: void 0,
					previous: void 0
				};
				switch (touch) {
					case Touch.None:
						this.addItemLast(item);
						break;
					case Touch.First:
						this.addItemFirst(item);
						break;
					case Touch.Last:
						this.addItemLast(item);
						break;
					default:
						this.addItemLast(item);
						break;
				}
				this._map.set(key, item);
				this._size++;
			}
			return this;
		}
		delete(key) {
			return !!this.remove(key);
		}
		remove(key) {
			const item = this._map.get(key);
			if (!item) return;
			this._map.delete(key);
			this.removeItem(item);
			this._size--;
			return item.value;
		}
		shift() {
			if (!this._head && !this._tail) return;
			if (!this._head || !this._tail) throw new Error("Invalid list");
			const item = this._head;
			this._map.delete(item.key);
			this.removeItem(item);
			this._size--;
			return item.value;
		}
		forEach(callbackfn, thisArg) {
			const state = this._state;
			let current = this._head;
			while (current) {
				if (thisArg) callbackfn.bind(thisArg)(current.value, current.key, this);
				else callbackfn(current.value, current.key, this);
				if (this._state !== state) throw new Error(`LinkedMap got modified during iteration.`);
				current = current.next;
			}
		}
		keys() {
			const state = this._state;
			let current = this._head;
			const iterator = {
				[Symbol.iterator]: () => {
					return iterator;
				},
				next: () => {
					if (this._state !== state) throw new Error(`LinkedMap got modified during iteration.`);
					if (current) {
						const result = {
							value: current.key,
							done: false
						};
						current = current.next;
						return result;
					} else return {
						value: void 0,
						done: true
					};
				}
			};
			return iterator;
		}
		values() {
			const state = this._state;
			let current = this._head;
			const iterator = {
				[Symbol.iterator]: () => {
					return iterator;
				},
				next: () => {
					if (this._state !== state) throw new Error(`LinkedMap got modified during iteration.`);
					if (current) {
						const result = {
							value: current.value,
							done: false
						};
						current = current.next;
						return result;
					} else return {
						value: void 0,
						done: true
					};
				}
			};
			return iterator;
		}
		entries() {
			const state = this._state;
			let current = this._head;
			const iterator = {
				[Symbol.iterator]: () => {
					return iterator;
				},
				next: () => {
					if (this._state !== state) throw new Error(`LinkedMap got modified during iteration.`);
					if (current) {
						const result = {
							value: [current.key, current.value],
							done: false
						};
						current = current.next;
						return result;
					} else return {
						value: void 0,
						done: true
					};
				}
			};
			return iterator;
		}
		[(_a = Symbol.toStringTag, Symbol.iterator)]() {
			return this.entries();
		}
		trimOld(newSize) {
			if (newSize >= this.size) return;
			if (newSize === 0) {
				this.clear();
				return;
			}
			let current = this._head;
			let currentSize = this.size;
			while (current && currentSize > newSize) {
				this._map.delete(current.key);
				current = current.next;
				currentSize--;
			}
			this._head = current;
			this._size = currentSize;
			if (current) current.previous = void 0;
			this._state++;
		}
		addItemFirst(item) {
			if (!this._head && !this._tail) this._tail = item;
			else if (!this._head) throw new Error("Invalid list");
			else {
				item.next = this._head;
				this._head.previous = item;
			}
			this._head = item;
			this._state++;
		}
		addItemLast(item) {
			if (!this._head && !this._tail) this._head = item;
			else if (!this._tail) throw new Error("Invalid list");
			else {
				item.previous = this._tail;
				this._tail.next = item;
			}
			this._tail = item;
			this._state++;
		}
		removeItem(item) {
			if (item === this._head && item === this._tail) {
				this._head = void 0;
				this._tail = void 0;
			} else if (item === this._head) {
				if (!item.next) throw new Error("Invalid list");
				item.next.previous = void 0;
				this._head = item.next;
			} else if (item === this._tail) {
				if (!item.previous) throw new Error("Invalid list");
				item.previous.next = void 0;
				this._tail = item.previous;
			} else {
				const next = item.next;
				const previous = item.previous;
				if (!next || !previous) throw new Error("Invalid list");
				next.previous = previous;
				previous.next = next;
			}
			item.next = void 0;
			item.previous = void 0;
			this._state++;
		}
		touch(item, touch) {
			if (!this._head || !this._tail) throw new Error("Invalid list");
			if (touch !== Touch.First && touch !== Touch.Last) return;
			if (touch === Touch.First) {
				if (item === this._head) return;
				const next = item.next;
				const previous = item.previous;
				if (item === this._tail) {
					previous.next = void 0;
					this._tail = previous;
				} else {
					next.previous = previous;
					previous.next = next;
				}
				item.previous = void 0;
				item.next = this._head;
				this._head.previous = item;
				this._head = item;
				this._state++;
			} else if (touch === Touch.Last) {
				if (item === this._tail) return;
				const next = item.next;
				const previous = item.previous;
				if (item === this._head) {
					next.previous = void 0;
					this._head = next;
				} else {
					next.previous = previous;
					previous.next = next;
				}
				item.next = void 0;
				item.previous = this._tail;
				this._tail.next = item;
				this._tail = item;
				this._state++;
			}
		}
		toJSON() {
			const data = [];
			this.forEach((value, key) => {
				data.push([key, value]);
			});
			return data;
		}
		fromJSON(data) {
			this.clear();
			for (const [key, value] of data) this.set(key, value);
		}
	};
	exports.LinkedMap = LinkedMap;
	var LRUCache = class extends LinkedMap {
		constructor(limit, ratio = 1) {
			super();
			this._limit = limit;
			this._ratio = Math.min(Math.max(0, ratio), 1);
		}
		get limit() {
			return this._limit;
		}
		set limit(limit) {
			this._limit = limit;
			this.checkTrim();
		}
		get ratio() {
			return this._ratio;
		}
		set ratio(ratio) {
			this._ratio = Math.min(Math.max(0, ratio), 1);
			this.checkTrim();
		}
		get(key, touch = Touch.AsNew) {
			return super.get(key, touch);
		}
		peek(key) {
			return super.get(key, Touch.None);
		}
		set(key, value) {
			super.set(key, value, Touch.Last);
			this.checkTrim();
			return this;
		}
		checkTrim() {
			if (this.size > this._limit) this.trimOld(Math.round(this._limit * this._ratio));
		}
	};
	exports.LRUCache = LRUCache;
}));
var require_disposable = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.Disposable = void 0;
	var Disposable;
	(function(Disposable) {
		function create(func) {
			return { dispose: func };
		}
		Disposable.create = create;
	})(Disposable || (exports.Disposable = Disposable = {}));
}));
var require_sharedArrayCancellation = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.SharedArrayReceiverStrategy = exports.SharedArraySenderStrategy = void 0;
	var cancellation_1 = require_cancellation();
	var CancellationState;
	(function(CancellationState) {
		CancellationState.Continue = 0;
		CancellationState.Cancelled = 1;
	})(CancellationState || (CancellationState = {}));
	var SharedArraySenderStrategy = class {
		constructor() {
			this.buffers = /* @__PURE__ */ new Map();
		}
		enableCancellation(request) {
			if (request.id === null) return;
			const buffer = new SharedArrayBuffer(4);
			const data = new Int32Array(buffer, 0, 1);
			data[0] = CancellationState.Continue;
			this.buffers.set(request.id, buffer);
			request.$cancellationData = buffer;
		}
		async sendCancellation(_conn, id) {
			const buffer = this.buffers.get(id);
			if (buffer === void 0) return;
			const data = new Int32Array(buffer, 0, 1);
			Atomics.store(data, 0, CancellationState.Cancelled);
		}
		cleanup(id) {
			this.buffers.delete(id);
		}
		dispose() {
			this.buffers.clear();
		}
	};
	exports.SharedArraySenderStrategy = SharedArraySenderStrategy;
	var SharedArrayBufferCancellationToken = class {
		constructor(buffer) {
			this.data = new Int32Array(buffer, 0, 1);
		}
		get isCancellationRequested() {
			return Atomics.load(this.data, 0) === CancellationState.Cancelled;
		}
		get onCancellationRequested() {
			throw new Error(`Cancellation over SharedArrayBuffer doesn't support cancellation events`);
		}
	};
	var SharedArrayBufferCancellationTokenSource = class {
		constructor(buffer) {
			this.token = new SharedArrayBufferCancellationToken(buffer);
		}
		cancel() {}
		dispose() {}
	};
	var SharedArrayReceiverStrategy = class {
		constructor() {
			this.kind = "request";
		}
		createCancellationTokenSource(request) {
			const buffer = request.$cancellationData;
			if (buffer === void 0) return new cancellation_1.CancellationTokenSource();
			return new SharedArrayBufferCancellationTokenSource(buffer);
		}
	};
	exports.SharedArrayReceiverStrategy = SharedArrayReceiverStrategy;
}));
var require_semaphore = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.Semaphore = void 0;
	var ral_1 = require_ral();
	var Semaphore = class {
		constructor(capacity = 1) {
			if (capacity <= 0) throw new Error("Capacity must be greater than 0");
			this._capacity = capacity;
			this._active = 0;
			this._waiting = [];
		}
		lock(thunk) {
			return new Promise((resolve, reject) => {
				this._waiting.push({
					thunk,
					resolve,
					reject
				});
				this.runNext();
			});
		}
		get active() {
			return this._active;
		}
		runNext() {
			if (this._waiting.length === 0 || this._active === this._capacity) return;
			(0, ral_1.default)().timer.setImmediate(() => this.doRunNext());
		}
		doRunNext() {
			if (this._waiting.length === 0 || this._active === this._capacity) return;
			const next = this._waiting.shift();
			this._active++;
			if (this._active > this._capacity) throw new Error(`To many thunks active`);
			try {
				const result = next.thunk();
				if (result instanceof Promise) result.then((value) => {
					this._active--;
					next.resolve(value);
					this.runNext();
				}, (err) => {
					this._active--;
					next.reject(err);
					this.runNext();
				});
				else {
					this._active--;
					next.resolve(result);
					this.runNext();
				}
			} catch (err) {
				this._active--;
				next.reject(err);
				this.runNext();
			}
		}
	};
	exports.Semaphore = Semaphore;
}));
var require_messageReader = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.ReadableStreamMessageReader = exports.AbstractMessageReader = exports.MessageReader = void 0;
	var ral_1 = require_ral();
	var Is = require_is$1();
	var events_1 = require_events();
	var semaphore_1 = require_semaphore();
	var MessageReader;
	(function(MessageReader) {
		function is(value) {
			let candidate = value;
			return candidate && Is.func(candidate.listen) && Is.func(candidate.dispose) && Is.func(candidate.onError) && Is.func(candidate.onClose) && Is.func(candidate.onPartialMessage);
		}
		MessageReader.is = is;
	})(MessageReader || (exports.MessageReader = MessageReader = {}));
	var AbstractMessageReader = class {
		constructor() {
			this.errorEmitter = new events_1.Emitter();
			this.closeEmitter = new events_1.Emitter();
			this.partialMessageEmitter = new events_1.Emitter();
		}
		dispose() {
			this.errorEmitter.dispose();
			this.closeEmitter.dispose();
		}
		get onError() {
			return this.errorEmitter.event;
		}
		fireError(error) {
			this.errorEmitter.fire(this.asError(error));
		}
		get onClose() {
			return this.closeEmitter.event;
		}
		fireClose() {
			this.closeEmitter.fire(void 0);
		}
		get onPartialMessage() {
			return this.partialMessageEmitter.event;
		}
		firePartialMessage(info) {
			this.partialMessageEmitter.fire(info);
		}
		asError(error) {
			if (error instanceof Error) return error;
			else return /* @__PURE__ */ new Error(`Reader received error. Reason: ${Is.string(error.message) ? error.message : "unknown"}`);
		}
	};
	exports.AbstractMessageReader = AbstractMessageReader;
	var ResolvedMessageReaderOptions;
	(function(ResolvedMessageReaderOptions) {
		function fromOptions(options) {
			let charset;
			let contentDecoder;
			const contentDecoders = /* @__PURE__ */ new Map();
			let contentTypeDecoder;
			const contentTypeDecoders = /* @__PURE__ */ new Map();
			if (options === void 0 || typeof options === "string") charset = options ?? "utf-8";
			else {
				charset = options.charset ?? "utf-8";
				if (options.contentDecoder !== void 0) {
					contentDecoder = options.contentDecoder;
					contentDecoders.set(contentDecoder.name, contentDecoder);
				}
				if (options.contentDecoders !== void 0) for (const decoder of options.contentDecoders) contentDecoders.set(decoder.name, decoder);
				if (options.contentTypeDecoder !== void 0) {
					contentTypeDecoder = options.contentTypeDecoder;
					contentTypeDecoders.set(contentTypeDecoder.name, contentTypeDecoder);
				}
				if (options.contentTypeDecoders !== void 0) for (const decoder of options.contentTypeDecoders) contentTypeDecoders.set(decoder.name, decoder);
			}
			if (contentTypeDecoder === void 0) {
				contentTypeDecoder = (0, ral_1.default)().applicationJson.decoder;
				contentTypeDecoders.set(contentTypeDecoder.name, contentTypeDecoder);
			}
			return {
				charset,
				contentDecoder,
				contentDecoders,
				contentTypeDecoder,
				contentTypeDecoders
			};
		}
		ResolvedMessageReaderOptions.fromOptions = fromOptions;
	})(ResolvedMessageReaderOptions || (ResolvedMessageReaderOptions = {}));
	var ReadableStreamMessageReader = class extends AbstractMessageReader {
		constructor(readable, options) {
			super();
			this.readable = readable;
			this.options = ResolvedMessageReaderOptions.fromOptions(options);
			this.buffer = (0, ral_1.default)().messageBuffer.create(this.options.charset);
			this._partialMessageTimeout = 1e4;
			this.nextMessageLength = -1;
			this.messageToken = 0;
			this.readSemaphore = new semaphore_1.Semaphore(1);
		}
		set partialMessageTimeout(timeout) {
			this._partialMessageTimeout = timeout;
		}
		get partialMessageTimeout() {
			return this._partialMessageTimeout;
		}
		listen(callback) {
			this.nextMessageLength = -1;
			this.messageToken = 0;
			this.partialMessageTimer = void 0;
			this.callback = callback;
			const result = this.readable.onData((data) => {
				this.onData(data);
			});
			this.readable.onError((error) => this.fireError(error));
			this.readable.onClose(() => this.fireClose());
			return result;
		}
		onData(data) {
			try {
				this.buffer.append(data);
				while (true) {
					if (this.nextMessageLength === -1) {
						const headers = this.buffer.tryReadHeaders(true);
						if (!headers) return;
						const contentLength = headers.get("content-length");
						if (!contentLength) {
							this.fireError(/* @__PURE__ */ new Error(`Header must provide a Content-Length property.\n${JSON.stringify(Object.fromEntries(headers))}`));
							return;
						}
						const length = parseInt(contentLength);
						if (isNaN(length)) {
							this.fireError(/* @__PURE__ */ new Error(`Content-Length value must be a number. Got ${contentLength}`));
							return;
						}
						this.nextMessageLength = length;
					}
					const body = this.buffer.tryReadBody(this.nextMessageLength);
					if (body === void 0) {
						/** We haven't received the full message yet. */
						this.setPartialMessageTimer();
						return;
					}
					this.clearPartialMessageTimer();
					this.nextMessageLength = -1;
					this.readSemaphore.lock(async () => {
						const bytes = this.options.contentDecoder !== void 0 ? await this.options.contentDecoder.decode(body) : body;
						const message = await this.options.contentTypeDecoder.decode(bytes, this.options);
						this.callback(message);
					}).catch((error) => {
						this.fireError(error);
					});
				}
			} catch (error) {
				this.fireError(error);
			}
		}
		clearPartialMessageTimer() {
			if (this.partialMessageTimer) {
				this.partialMessageTimer.dispose();
				this.partialMessageTimer = void 0;
			}
		}
		setPartialMessageTimer() {
			this.clearPartialMessageTimer();
			if (this._partialMessageTimeout <= 0) return;
			this.partialMessageTimer = (0, ral_1.default)().timer.setTimeout((token, timeout) => {
				this.partialMessageTimer = void 0;
				if (token === this.messageToken) {
					this.firePartialMessage({
						messageToken: token,
						waitingTime: timeout
					});
					this.setPartialMessageTimer();
				}
			}, this._partialMessageTimeout, this.messageToken, this._partialMessageTimeout);
		}
	};
	exports.ReadableStreamMessageReader = ReadableStreamMessageReader;
}));
var require_messageWriter = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.WriteableStreamMessageWriter = exports.AbstractMessageWriter = exports.MessageWriter = void 0;
	var ral_1 = require_ral();
	var Is = require_is$1();
	var semaphore_1 = require_semaphore();
	var events_1 = require_events();
	var ContentLength = "Content-Length: ";
	var CRLF = "\r\n";
	var MessageWriter;
	(function(MessageWriter) {
		function is(value) {
			let candidate = value;
			return candidate && Is.func(candidate.dispose) && Is.func(candidate.onClose) && Is.func(candidate.onError) && Is.func(candidate.write);
		}
		MessageWriter.is = is;
	})(MessageWriter || (exports.MessageWriter = MessageWriter = {}));
	var AbstractMessageWriter = class {
		constructor() {
			this.errorEmitter = new events_1.Emitter();
			this.closeEmitter = new events_1.Emitter();
		}
		dispose() {
			this.errorEmitter.dispose();
			this.closeEmitter.dispose();
		}
		get onError() {
			return this.errorEmitter.event;
		}
		fireError(error, message, count) {
			this.errorEmitter.fire([
				this.asError(error),
				message,
				count
			]);
		}
		get onClose() {
			return this.closeEmitter.event;
		}
		fireClose() {
			this.closeEmitter.fire(void 0);
		}
		asError(error) {
			if (error instanceof Error) return error;
			else return /* @__PURE__ */ new Error(`Writer received error. Reason: ${Is.string(error.message) ? error.message : "unknown"}`);
		}
	};
	exports.AbstractMessageWriter = AbstractMessageWriter;
	var ResolvedMessageWriterOptions;
	(function(ResolvedMessageWriterOptions) {
		function fromOptions(options) {
			if (options === void 0 || typeof options === "string") return {
				charset: options ?? "utf-8",
				contentTypeEncoder: (0, ral_1.default)().applicationJson.encoder
			};
			else return {
				charset: options.charset ?? "utf-8",
				contentEncoder: options.contentEncoder,
				contentTypeEncoder: options.contentTypeEncoder ?? (0, ral_1.default)().applicationJson.encoder
			};
		}
		ResolvedMessageWriterOptions.fromOptions = fromOptions;
	})(ResolvedMessageWriterOptions || (ResolvedMessageWriterOptions = {}));
	var WriteableStreamMessageWriter = class extends AbstractMessageWriter {
		constructor(writable, options) {
			super();
			this.writable = writable;
			this.options = ResolvedMessageWriterOptions.fromOptions(options);
			this.errorCount = 0;
			this.writeSemaphore = new semaphore_1.Semaphore(1);
			this.writable.onError((error) => this.fireError(error));
			this.writable.onClose(() => this.fireClose());
		}
		async write(msg) {
			return this.writeSemaphore.lock(async () => {
				return this.options.contentTypeEncoder.encode(msg, this.options).then((buffer) => {
					if (this.options.contentEncoder !== void 0) return this.options.contentEncoder.encode(buffer);
					else return buffer;
				}).then((buffer) => {
					const headers = [];
					headers.push(ContentLength, buffer.byteLength.toString(), CRLF);
					headers.push(CRLF);
					return this.doWrite(msg, headers, buffer);
				}, (error) => {
					this.fireError(error);
					throw error;
				});
			});
		}
		async doWrite(msg, headers, data) {
			try {
				await this.writable.write(headers.join(""), "ascii");
				return this.writable.write(data);
			} catch (error) {
				this.handleError(error, msg);
				return Promise.reject(error);
			}
		}
		handleError(error, msg) {
			this.errorCount++;
			this.fireError(error, msg, this.errorCount);
		}
		end() {
			this.writable.end();
		}
	};
	exports.WriteableStreamMessageWriter = WriteableStreamMessageWriter;
}));
var require_messageBuffer = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.AbstractMessageBuffer = void 0;
	var CR = 13;
	var LF = 10;
	var CRLF = "\r\n";
	var AbstractMessageBuffer = class {
		constructor(encoding = "utf-8") {
			this._encoding = encoding;
			this._chunks = [];
			this._totalLength = 0;
		}
		get encoding() {
			return this._encoding;
		}
		append(chunk) {
			const toAppend = typeof chunk === "string" ? this.fromString(chunk, this._encoding) : chunk;
			this._chunks.push(toAppend);
			this._totalLength += toAppend.byteLength;
		}
		tryReadHeaders(lowerCaseKeys = false) {
			if (this._chunks.length === 0) return;
			let state = 0;
			let chunkIndex = 0;
			let offset = 0;
			let chunkBytesRead = 0;
			row: while (chunkIndex < this._chunks.length) {
				const chunk = this._chunks[chunkIndex];
				offset = 0;
				column: while (offset < chunk.length) {
					switch (chunk[offset]) {
						case CR:
							switch (state) {
								case 0:
									state = 1;
									break;
								case 2:
									state = 3;
									break;
								default: state = 0;
							}
							break;
						case LF:
							switch (state) {
								case 1:
									state = 2;
									break;
								case 3:
									state = 4;
									offset++;
									break row;
								default: state = 0;
							}
							break;
						default: state = 0;
					}
					offset++;
				}
				chunkBytesRead += chunk.byteLength;
				chunkIndex++;
			}
			if (state !== 4) return;
			const buffer = this._read(chunkBytesRead + offset);
			const result = /* @__PURE__ */ new Map();
			const headers = this.toString(buffer, "ascii").split(CRLF);
			if (headers.length < 2) return result;
			for (let i = 0; i < headers.length - 2; i++) {
				const header = headers[i];
				const index = header.indexOf(":");
				if (index === -1) throw new Error(`Message header must separate key and value using ':'\n${header}`);
				const key = header.substr(0, index);
				const value = header.substr(index + 1).trim();
				result.set(lowerCaseKeys ? key.toLowerCase() : key, value);
			}
			return result;
		}
		tryReadBody(length) {
			if (this._totalLength < length) return;
			return this._read(length);
		}
		get numberOfBytes() {
			return this._totalLength;
		}
		_read(byteCount) {
			if (byteCount === 0) return this.emptyBuffer();
			if (byteCount > this._totalLength) throw new Error(`Cannot read so many bytes!`);
			if (this._chunks[0].byteLength === byteCount) {
				const chunk = this._chunks[0];
				this._chunks.shift();
				this._totalLength -= byteCount;
				return this.asNative(chunk);
			}
			if (this._chunks[0].byteLength > byteCount) {
				const chunk = this._chunks[0];
				const result = this.asNative(chunk, byteCount);
				this._chunks[0] = chunk.slice(byteCount);
				this._totalLength -= byteCount;
				return result;
			}
			const result = this.allocNative(byteCount);
			let resultOffset = 0;
			let chunkIndex = 0;
			while (byteCount > 0) {
				const chunk = this._chunks[chunkIndex];
				if (chunk.byteLength > byteCount) {
					const chunkPart = chunk.slice(0, byteCount);
					result.set(chunkPart, resultOffset);
					resultOffset += byteCount;
					this._chunks[chunkIndex] = chunk.slice(byteCount);
					this._totalLength -= byteCount;
					byteCount -= byteCount;
				} else {
					result.set(chunk, resultOffset);
					resultOffset += chunk.byteLength;
					this._chunks.shift();
					this._totalLength -= chunk.byteLength;
					byteCount -= chunk.byteLength;
				}
			}
			return result;
		}
	};
	exports.AbstractMessageBuffer = AbstractMessageBuffer;
}));
var require_connection$1 = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.createMessageConnection = exports.ConnectionOptions = exports.MessageStrategy = exports.CancellationStrategy = exports.CancellationSenderStrategy = exports.CancellationReceiverStrategy = exports.RequestCancellationReceiverStrategy = exports.IdCancellationReceiverStrategy = exports.ConnectionStrategy = exports.ConnectionError = exports.ConnectionErrors = exports.LogTraceNotification = exports.SetTraceNotification = exports.TraceFormat = exports.TraceValues = exports.Trace = exports.NullLogger = exports.ProgressType = exports.ProgressToken = void 0;
	var ral_1 = require_ral();
	var Is = require_is$1();
	var messages_1 = require_messages$1();
	var linkedMap_1 = require_linkedMap();
	var events_1 = require_events();
	var cancellation_1 = require_cancellation();
	var CancelNotification;
	(function(CancelNotification) {
		CancelNotification.type = new messages_1.NotificationType("$/cancelRequest");
	})(CancelNotification || (CancelNotification = {}));
	var ProgressToken;
	(function(ProgressToken) {
		function is(value) {
			return typeof value === "string" || typeof value === "number";
		}
		ProgressToken.is = is;
	})(ProgressToken || (exports.ProgressToken = ProgressToken = {}));
	var ProgressNotification;
	(function(ProgressNotification) {
		ProgressNotification.type = new messages_1.NotificationType("$/progress");
	})(ProgressNotification || (ProgressNotification = {}));
	var ProgressType = class {
		constructor() {}
	};
	exports.ProgressType = ProgressType;
	var StarRequestHandler;
	(function(StarRequestHandler) {
		function is(value) {
			return Is.func(value);
		}
		StarRequestHandler.is = is;
	})(StarRequestHandler || (StarRequestHandler = {}));
	exports.NullLogger = Object.freeze({
		error: () => {},
		warn: () => {},
		info: () => {},
		log: () => {}
	});
	var Trace;
	(function(Trace) {
		Trace[Trace["Off"] = 0] = "Off";
		Trace[Trace["Messages"] = 1] = "Messages";
		Trace[Trace["Compact"] = 2] = "Compact";
		Trace[Trace["Verbose"] = 3] = "Verbose";
	})(Trace || (exports.Trace = Trace = {}));
	var TraceValues;
	(function(TraceValues) {
		/**
		* Turn tracing off.
		*/
		TraceValues.Off = "off";
		/**
		* Trace messages only.
		*/
		TraceValues.Messages = "messages";
		/**
		* Compact message tracing.
		*/
		TraceValues.Compact = "compact";
		/**
		* Verbose message tracing.
		*/
		TraceValues.Verbose = "verbose";
	})(TraceValues || (exports.TraceValues = TraceValues = {}));
	(function(Trace) {
		function fromString(value) {
			if (!Is.string(value)) return Trace.Off;
			value = value.toLowerCase();
			switch (value) {
				case "off": return Trace.Off;
				case "messages": return Trace.Messages;
				case "compact": return Trace.Compact;
				case "verbose": return Trace.Verbose;
				default: return Trace.Off;
			}
		}
		Trace.fromString = fromString;
		function toString(value) {
			switch (value) {
				case Trace.Off: return "off";
				case Trace.Messages: return "messages";
				case Trace.Compact: return "compact";
				case Trace.Verbose: return "verbose";
				default: return "off";
			}
		}
		Trace.toString = toString;
	})(Trace || (exports.Trace = Trace = {}));
	var TraceFormat;
	(function(TraceFormat) {
		TraceFormat["Text"] = "text";
		TraceFormat["JSON"] = "json";
	})(TraceFormat || (exports.TraceFormat = TraceFormat = {}));
	(function(TraceFormat) {
		function fromString(value) {
			if (!Is.string(value)) return TraceFormat.Text;
			value = value.toLowerCase();
			if (value === "json") return TraceFormat.JSON;
			else return TraceFormat.Text;
		}
		TraceFormat.fromString = fromString;
	})(TraceFormat || (exports.TraceFormat = TraceFormat = {}));
	var SetTraceNotification;
	(function(SetTraceNotification) {
		SetTraceNotification.type = new messages_1.NotificationType("$/setTrace");
	})(SetTraceNotification || (exports.SetTraceNotification = SetTraceNotification = {}));
	var LogTraceNotification;
	(function(LogTraceNotification) {
		LogTraceNotification.type = new messages_1.NotificationType("$/logTrace");
	})(LogTraceNotification || (exports.LogTraceNotification = LogTraceNotification = {}));
	var ConnectionErrors;
	(function(ConnectionErrors) {
		/**
		* The connection is closed.
		*/
		ConnectionErrors[ConnectionErrors["Closed"] = 1] = "Closed";
		/**
		* The connection got disposed.
		*/
		ConnectionErrors[ConnectionErrors["Disposed"] = 2] = "Disposed";
		/**
		* The connection is already in listening mode.
		*/
		ConnectionErrors[ConnectionErrors["AlreadyListening"] = 3] = "AlreadyListening";
	})(ConnectionErrors || (exports.ConnectionErrors = ConnectionErrors = {}));
	var ConnectionError = class ConnectionError extends Error {
		constructor(code, message) {
			super(message);
			this.code = code;
			Object.setPrototypeOf(this, ConnectionError.prototype);
		}
	};
	exports.ConnectionError = ConnectionError;
	var ConnectionStrategy;
	(function(ConnectionStrategy) {
		function is(value) {
			const candidate = value;
			return candidate && Is.func(candidate.cancelUndispatched);
		}
		ConnectionStrategy.is = is;
	})(ConnectionStrategy || (exports.ConnectionStrategy = ConnectionStrategy = {}));
	var IdCancellationReceiverStrategy;
	(function(IdCancellationReceiverStrategy) {
		function is(value) {
			const candidate = value;
			return candidate && (candidate.kind === void 0 || candidate.kind === "id") && Is.func(candidate.createCancellationTokenSource) && (candidate.dispose === void 0 || Is.func(candidate.dispose));
		}
		IdCancellationReceiverStrategy.is = is;
	})(IdCancellationReceiverStrategy || (exports.IdCancellationReceiverStrategy = IdCancellationReceiverStrategy = {}));
	var RequestCancellationReceiverStrategy;
	(function(RequestCancellationReceiverStrategy) {
		function is(value) {
			const candidate = value;
			return candidate && candidate.kind === "request" && Is.func(candidate.createCancellationTokenSource) && (candidate.dispose === void 0 || Is.func(candidate.dispose));
		}
		RequestCancellationReceiverStrategy.is = is;
	})(RequestCancellationReceiverStrategy || (exports.RequestCancellationReceiverStrategy = RequestCancellationReceiverStrategy = {}));
	var CancellationReceiverStrategy;
	(function(CancellationReceiverStrategy) {
		CancellationReceiverStrategy.Message = Object.freeze({ createCancellationTokenSource(_) {
			return new cancellation_1.CancellationTokenSource();
		} });
		function is(value) {
			return IdCancellationReceiverStrategy.is(value) || RequestCancellationReceiverStrategy.is(value);
		}
		CancellationReceiverStrategy.is = is;
	})(CancellationReceiverStrategy || (exports.CancellationReceiverStrategy = CancellationReceiverStrategy = {}));
	var CancellationSenderStrategy;
	(function(CancellationSenderStrategy) {
		CancellationSenderStrategy.Message = Object.freeze({
			sendCancellation(conn, id) {
				return conn.sendNotification(CancelNotification.type, { id });
			},
			cleanup(_) {}
		});
		function is(value) {
			const candidate = value;
			return candidate && Is.func(candidate.sendCancellation) && Is.func(candidate.cleanup);
		}
		CancellationSenderStrategy.is = is;
	})(CancellationSenderStrategy || (exports.CancellationSenderStrategy = CancellationSenderStrategy = {}));
	var CancellationStrategy;
	(function(CancellationStrategy) {
		CancellationStrategy.Message = Object.freeze({
			receiver: CancellationReceiverStrategy.Message,
			sender: CancellationSenderStrategy.Message
		});
		function is(value) {
			const candidate = value;
			return candidate && CancellationReceiverStrategy.is(candidate.receiver) && CancellationSenderStrategy.is(candidate.sender);
		}
		CancellationStrategy.is = is;
	})(CancellationStrategy || (exports.CancellationStrategy = CancellationStrategy = {}));
	var MessageStrategy;
	(function(MessageStrategy) {
		function is(value) {
			const candidate = value;
			return candidate && Is.func(candidate.handleMessage);
		}
		MessageStrategy.is = is;
	})(MessageStrategy || (exports.MessageStrategy = MessageStrategy = {}));
	var ConnectionOptions;
	(function(ConnectionOptions) {
		function is(value) {
			const candidate = value;
			return candidate && (CancellationStrategy.is(candidate.cancellationStrategy) || ConnectionStrategy.is(candidate.connectionStrategy) || MessageStrategy.is(candidate.messageStrategy));
		}
		ConnectionOptions.is = is;
	})(ConnectionOptions || (exports.ConnectionOptions = ConnectionOptions = {}));
	var ConnectionState;
	(function(ConnectionState) {
		ConnectionState[ConnectionState["New"] = 1] = "New";
		ConnectionState[ConnectionState["Listening"] = 2] = "Listening";
		ConnectionState[ConnectionState["Closed"] = 3] = "Closed";
		ConnectionState[ConnectionState["Disposed"] = 4] = "Disposed";
	})(ConnectionState || (ConnectionState = {}));
	function createMessageConnection(messageReader, messageWriter, _logger, options) {
		const logger = _logger !== void 0 ? _logger : exports.NullLogger;
		let sequenceNumber = 0;
		let notificationSequenceNumber = 0;
		let unknownResponseSequenceNumber = 0;
		const version = "2.0";
		let starRequestHandler = void 0;
		const requestHandlers = /* @__PURE__ */ new Map();
		let starNotificationHandler = void 0;
		const notificationHandlers = /* @__PURE__ */ new Map();
		const progressHandlers = /* @__PURE__ */ new Map();
		let timer;
		let messageQueue = new linkedMap_1.LinkedMap();
		let responsePromises = /* @__PURE__ */ new Map();
		let knownCanceledRequests = /* @__PURE__ */ new Set();
		let requestTokens = /* @__PURE__ */ new Map();
		let trace = Trace.Off;
		let traceFormat = TraceFormat.Text;
		let tracer;
		let state = ConnectionState.New;
		const errorEmitter = new events_1.Emitter();
		const closeEmitter = new events_1.Emitter();
		const unhandledNotificationEmitter = new events_1.Emitter();
		const unhandledProgressEmitter = new events_1.Emitter();
		const disposeEmitter = new events_1.Emitter();
		const cancellationStrategy = options && options.cancellationStrategy ? options.cancellationStrategy : CancellationStrategy.Message;
		function createRequestQueueKey(id) {
			if (id === null) throw new Error(`Can't send requests with id null since the response can't be correlated.`);
			return "req-" + id.toString();
		}
		function createResponseQueueKey(id) {
			if (id === null) return "res-unknown-" + (++unknownResponseSequenceNumber).toString();
			else return "res-" + id.toString();
		}
		function createNotificationQueueKey() {
			return "not-" + (++notificationSequenceNumber).toString();
		}
		function addMessageToQueue(queue, message) {
			if (messages_1.Message.isRequest(message)) queue.set(createRequestQueueKey(message.id), message);
			else if (messages_1.Message.isResponse(message)) queue.set(createResponseQueueKey(message.id), message);
			else queue.set(createNotificationQueueKey(), message);
		}
		function cancelUndispatched(_message) {}
		function isListening() {
			return state === ConnectionState.Listening;
		}
		function isClosed() {
			return state === ConnectionState.Closed;
		}
		function isDisposed() {
			return state === ConnectionState.Disposed;
		}
		function closeHandler() {
			if (state === ConnectionState.New || state === ConnectionState.Listening) {
				state = ConnectionState.Closed;
				closeEmitter.fire(void 0);
			}
		}
		function readErrorHandler(error) {
			errorEmitter.fire([
				error,
				void 0,
				void 0
			]);
		}
		function writeErrorHandler(data) {
			errorEmitter.fire(data);
		}
		messageReader.onClose(closeHandler);
		messageReader.onError(readErrorHandler);
		messageWriter.onClose(closeHandler);
		messageWriter.onError(writeErrorHandler);
		function triggerMessageQueue() {
			if (timer || messageQueue.size === 0) return;
			timer = (0, ral_1.default)().timer.setImmediate(() => {
				timer = void 0;
				processMessageQueue();
			});
		}
		function handleMessage(message) {
			if (messages_1.Message.isRequest(message)) handleRequest(message);
			else if (messages_1.Message.isNotification(message)) handleNotification(message);
			else if (messages_1.Message.isResponse(message)) handleResponse(message);
			else handleInvalidMessage(message);
		}
		function processMessageQueue() {
			if (messageQueue.size === 0) return;
			const message = messageQueue.shift();
			try {
				const messageStrategy = options?.messageStrategy;
				if (MessageStrategy.is(messageStrategy)) messageStrategy.handleMessage(message, handleMessage);
				else handleMessage(message);
			} finally {
				triggerMessageQueue();
			}
		}
		const callback = (message) => {
			try {
				if (messages_1.Message.isNotification(message) && message.method === CancelNotification.type.method) {
					const cancelId = message.params.id;
					const key = createRequestQueueKey(cancelId);
					const toCancel = messageQueue.get(key);
					if (messages_1.Message.isRequest(toCancel)) {
						const strategy = options?.connectionStrategy;
						const response = strategy && strategy.cancelUndispatched ? strategy.cancelUndispatched(toCancel, cancelUndispatched) : void 0;
						if (response && (response.error !== void 0 || response.result !== void 0)) {
							messageQueue.delete(key);
							requestTokens.delete(cancelId);
							response.id = toCancel.id;
							traceSendingResponse(response, message.method, Date.now());
							messageWriter.write(response).catch(() => logger.error(`Sending response for canceled message failed.`));
							return;
						}
					}
					const cancellationToken = requestTokens.get(cancelId);
					if (cancellationToken !== void 0) {
						cancellationToken.cancel();
						traceReceivedNotification(message);
						return;
					} else knownCanceledRequests.add(cancelId);
				}
				addMessageToQueue(messageQueue, message);
			} finally {
				triggerMessageQueue();
			}
		};
		function handleRequest(requestMessage) {
			if (isDisposed()) return;
			function reply(resultOrError, method, startTime) {
				const message = {
					jsonrpc: version,
					id: requestMessage.id
				};
				if (resultOrError instanceof messages_1.ResponseError) message.error = resultOrError.toJson();
				else message.result = resultOrError === void 0 ? null : resultOrError;
				traceSendingResponse(message, method, startTime);
				messageWriter.write(message).catch(() => logger.error(`Sending response failed.`));
			}
			function replyError(error, method, startTime) {
				const message = {
					jsonrpc: version,
					id: requestMessage.id,
					error: error.toJson()
				};
				traceSendingResponse(message, method, startTime);
				messageWriter.write(message).catch(() => logger.error(`Sending response failed.`));
			}
			function replySuccess(result, method, startTime) {
				if (result === void 0) result = null;
				const message = {
					jsonrpc: version,
					id: requestMessage.id,
					result
				};
				traceSendingResponse(message, method, startTime);
				messageWriter.write(message).catch(() => logger.error(`Sending response failed.`));
			}
			traceReceivedRequest(requestMessage);
			const element = requestHandlers.get(requestMessage.method);
			let type;
			let requestHandler;
			if (element) {
				type = element.type;
				requestHandler = element.handler;
			}
			const startTime = Date.now();
			if (requestHandler || starRequestHandler) {
				const tokenKey = requestMessage.id ?? String(Date.now());
				const cancellationSource = IdCancellationReceiverStrategy.is(cancellationStrategy.receiver) ? cancellationStrategy.receiver.createCancellationTokenSource(tokenKey) : cancellationStrategy.receiver.createCancellationTokenSource(requestMessage);
				if (requestMessage.id !== null && knownCanceledRequests.has(requestMessage.id)) cancellationSource.cancel();
				if (requestMessage.id !== null) requestTokens.set(tokenKey, cancellationSource);
				try {
					let handlerResult;
					if (requestHandler) if (requestMessage.params === void 0) {
						if (type !== void 0 && type.numberOfParams !== 0) {
							replyError(new messages_1.ResponseError(messages_1.ErrorCodes.InvalidParams, `Request ${requestMessage.method} defines ${type.numberOfParams} params but received none.`), requestMessage.method, startTime);
							return;
						}
						handlerResult = requestHandler(cancellationSource.token);
					} else if (Array.isArray(requestMessage.params)) {
						if (type !== void 0 && type.parameterStructures === messages_1.ParameterStructures.byName) {
							replyError(new messages_1.ResponseError(messages_1.ErrorCodes.InvalidParams, `Request ${requestMessage.method} defines parameters by name but received parameters by position`), requestMessage.method, startTime);
							return;
						}
						handlerResult = requestHandler(...requestMessage.params, cancellationSource.token);
					} else {
						if (type !== void 0 && type.parameterStructures === messages_1.ParameterStructures.byPosition) {
							replyError(new messages_1.ResponseError(messages_1.ErrorCodes.InvalidParams, `Request ${requestMessage.method} defines parameters by position but received parameters by name`), requestMessage.method, startTime);
							return;
						}
						handlerResult = requestHandler(requestMessage.params, cancellationSource.token);
					}
					else if (starRequestHandler) handlerResult = starRequestHandler(requestMessage.method, requestMessage.params, cancellationSource.token);
					const promise = handlerResult;
					if (!handlerResult) {
						requestTokens.delete(tokenKey);
						replySuccess(handlerResult, requestMessage.method, startTime);
					} else if (promise.then) promise.then((resultOrError) => {
						requestTokens.delete(tokenKey);
						reply(resultOrError, requestMessage.method, startTime);
					}, (error) => {
						requestTokens.delete(tokenKey);
						if (error instanceof messages_1.ResponseError) replyError(error, requestMessage.method, startTime);
						else if (error && Is.string(error.message)) replyError(new messages_1.ResponseError(messages_1.ErrorCodes.InternalError, `Request ${requestMessage.method} failed with message: ${error.message}`), requestMessage.method, startTime);
						else replyError(new messages_1.ResponseError(messages_1.ErrorCodes.InternalError, `Request ${requestMessage.method} failed unexpectedly without providing any details.`), requestMessage.method, startTime);
					});
					else {
						requestTokens.delete(tokenKey);
						reply(handlerResult, requestMessage.method, startTime);
					}
				} catch (error) {
					requestTokens.delete(tokenKey);
					if (error instanceof messages_1.ResponseError) reply(error, requestMessage.method, startTime);
					else if (error && Is.string(error.message)) replyError(new messages_1.ResponseError(messages_1.ErrorCodes.InternalError, `Request ${requestMessage.method} failed with message: ${error.message}`), requestMessage.method, startTime);
					else replyError(new messages_1.ResponseError(messages_1.ErrorCodes.InternalError, `Request ${requestMessage.method} failed unexpectedly without providing any details.`), requestMessage.method, startTime);
				}
			} else replyError(new messages_1.ResponseError(messages_1.ErrorCodes.MethodNotFound, `Unhandled method ${requestMessage.method}`), requestMessage.method, startTime);
		}
		function handleResponse(responseMessage) {
			if (isDisposed()) return;
			if (responseMessage.id === null) if (responseMessage.error) logger.error(`Received response message without id: Error is: \n${JSON.stringify(responseMessage.error, void 0, 4)}`);
			else logger.error(`Received response message without id. No further error information provided.`);
			else {
				const key = responseMessage.id;
				const responsePromise = responsePromises.get(key);
				traceReceivedResponse(responseMessage, responsePromise);
				if (responsePromise !== void 0) {
					responsePromises.delete(key);
					try {
						if (responseMessage.error) {
							const error = responseMessage.error;
							responsePromise.reject(new messages_1.ResponseError(error.code, error.message, error.data));
						} else if (responseMessage.result !== void 0) responsePromise.resolve(responseMessage.result);
						else throw new Error("Should never happen.");
					} catch (error) {
						if (error.message) logger.error(`Response handler '${responsePromise.method}' failed with message: ${error.message}`);
						else logger.error(`Response handler '${responsePromise.method}' failed unexpectedly.`);
					}
				}
			}
		}
		function handleNotification(message) {
			if (isDisposed()) return;
			let type = void 0;
			let notificationHandler;
			if (message.method === CancelNotification.type.method) {
				const cancelId = message.params.id;
				knownCanceledRequests.delete(cancelId);
				traceReceivedNotification(message);
				return;
			} else {
				const element = notificationHandlers.get(message.method);
				if (element) {
					notificationHandler = element.handler;
					type = element.type;
				}
			}
			if (notificationHandler || starNotificationHandler) try {
				traceReceivedNotification(message);
				if (notificationHandler) if (message.params === void 0) {
					if (type !== void 0) {
						if (type.numberOfParams !== 0 && type.parameterStructures !== messages_1.ParameterStructures.byName) logger.error(`Notification ${message.method} defines ${type.numberOfParams} params but received none.`);
					}
					notificationHandler();
				} else if (Array.isArray(message.params)) {
					const params = message.params;
					if (message.method === ProgressNotification.type.method && params.length === 2 && ProgressToken.is(params[0])) notificationHandler({
						token: params[0],
						value: params[1]
					});
					else {
						if (type !== void 0) {
							if (type.parameterStructures === messages_1.ParameterStructures.byName) logger.error(`Notification ${message.method} defines parameters by name but received parameters by position`);
							if (type.numberOfParams !== message.params.length) logger.error(`Notification ${message.method} defines ${type.numberOfParams} params but received ${params.length} arguments`);
						}
						notificationHandler(...params);
					}
				} else {
					if (type !== void 0 && type.parameterStructures === messages_1.ParameterStructures.byPosition) logger.error(`Notification ${message.method} defines parameters by position but received parameters by name`);
					notificationHandler(message.params);
				}
				else if (starNotificationHandler) starNotificationHandler(message.method, message.params);
			} catch (error) {
				if (error.message) logger.error(`Notification handler '${message.method}' failed with message: ${error.message}`);
				else logger.error(`Notification handler '${message.method}' failed unexpectedly.`);
			}
			else unhandledNotificationEmitter.fire(message);
		}
		function handleInvalidMessage(message) {
			if (!message) {
				logger.error("Received empty message.");
				return;
			}
			logger.error(`Received message which is neither a response nor a notification message:\n${JSON.stringify(message, null, 4)}`);
			const responseMessage = message;
			if (Is.string(responseMessage.id) || Is.number(responseMessage.id)) {
				const key = responseMessage.id;
				const responseHandler = responsePromises.get(key);
				if (responseHandler) responseHandler.reject(/* @__PURE__ */ new Error("The received response has neither a result nor an error property."));
			}
		}
		function stringifyTrace(params) {
			if (params === void 0 || params === null) return;
			switch (trace) {
				case Trace.Verbose: return JSON.stringify(params, null, 4);
				case Trace.Compact: return JSON.stringify(params);
				default: return;
			}
		}
		function traceSendingRequest(message) {
			if (trace === Trace.Off || !tracer) return;
			if (traceFormat === TraceFormat.Text) {
				let data = void 0;
				if ((trace === Trace.Verbose || trace === Trace.Compact) && message.params) data = `Params: ${stringifyTrace(message.params)}\n\n`;
				tracer.log(`Sending request '${message.method} - (${message.id})'.`, data);
			} else logLSPMessage("send-request", message);
		}
		function traceSendingNotification(message) {
			if (trace === Trace.Off || !tracer) return;
			if (traceFormat === TraceFormat.Text) {
				let data = void 0;
				if (trace === Trace.Verbose || trace === Trace.Compact) if (message.params) data = `Params: ${stringifyTrace(message.params)}\n\n`;
				else data = "No parameters provided.\n\n";
				tracer.log(`Sending notification '${message.method}'.`, data);
			} else logLSPMessage("send-notification", message);
		}
		function traceSendingResponse(message, method, startTime) {
			if (trace === Trace.Off || !tracer) return;
			if (traceFormat === TraceFormat.Text) {
				let data = void 0;
				if (trace === Trace.Verbose || trace === Trace.Compact) {
					if (message.error && message.error.data) data = `Error data: ${stringifyTrace(message.error.data)}\n\n`;
					else if (message.result) data = `Result: ${stringifyTrace(message.result)}\n\n`;
					else if (message.error === void 0) data = "No result returned.\n\n";
				}
				tracer.log(`Sending response '${method} - (${message.id})'. Processing request took ${Date.now() - startTime}ms`, data);
			} else logLSPMessage("send-response", message);
		}
		function traceReceivedRequest(message) {
			if (trace === Trace.Off || !tracer) return;
			if (traceFormat === TraceFormat.Text) {
				let data = void 0;
				if ((trace === Trace.Verbose || trace === Trace.Compact) && message.params) data = `Params: ${stringifyTrace(message.params)}\n\n`;
				tracer.log(`Received request '${message.method} - (${message.id})'.`, data);
			} else logLSPMessage("receive-request", message);
		}
		function traceReceivedNotification(message) {
			if (trace === Trace.Off || !tracer || message.method === LogTraceNotification.type.method) return;
			if (traceFormat === TraceFormat.Text) {
				let data = void 0;
				if (trace === Trace.Verbose || trace === Trace.Compact) if (message.params) data = `Params: ${stringifyTrace(message.params)}\n\n`;
				else data = "No parameters provided.\n\n";
				tracer.log(`Received notification '${message.method}'.`, data);
			} else logLSPMessage("receive-notification", message);
		}
		function traceReceivedResponse(message, responsePromise) {
			if (trace === Trace.Off || !tracer) return;
			if (traceFormat === TraceFormat.Text) {
				let data = void 0;
				if (trace === Trace.Verbose || trace === Trace.Compact) {
					if (message.error && message.error.data) data = `Error data: ${stringifyTrace(message.error.data)}\n\n`;
					else if (message.result) data = `Result: ${stringifyTrace(message.result)}\n\n`;
					else if (message.error === void 0) data = "No result returned.\n\n";
				}
				if (responsePromise) {
					const error = message.error ? ` Request failed: ${message.error.message} (${message.error.code}).` : "";
					tracer.log(`Received response '${responsePromise.method} - (${message.id})' in ${Date.now() - responsePromise.timerStart}ms.${error}`, data);
				} else tracer.log(`Received response ${message.id} without active response promise.`, data);
			} else logLSPMessage("receive-response", message);
		}
		function logLSPMessage(type, message) {
			if (!tracer || trace === Trace.Off) return;
			const lspMessage = {
				isLSPMessage: true,
				type,
				message,
				timestamp: Date.now()
			};
			tracer.log(lspMessage);
		}
		function throwIfClosedOrDisposed() {
			if (isClosed()) throw new ConnectionError(ConnectionErrors.Closed, "Connection is closed.");
			if (isDisposed()) throw new ConnectionError(ConnectionErrors.Disposed, "Connection is disposed.");
		}
		function throwIfListening() {
			if (isListening()) throw new ConnectionError(ConnectionErrors.AlreadyListening, "Connection is already listening");
		}
		function throwIfNotListening() {
			if (!isListening()) throw new Error("Call listen() first.");
		}
		function undefinedToNull(param) {
			if (param === void 0) return null;
			else return param;
		}
		function nullToUndefined(param) {
			if (param === null) return;
			else return param;
		}
		function isNamedParam(param) {
			return param !== void 0 && param !== null && !Array.isArray(param) && typeof param === "object";
		}
		function computeSingleParam(parameterStructures, param) {
			switch (parameterStructures) {
				case messages_1.ParameterStructures.auto: if (isNamedParam(param)) return nullToUndefined(param);
				else return [undefinedToNull(param)];
				case messages_1.ParameterStructures.byName:
					if (!isNamedParam(param)) throw new Error(`Received parameters by name but param is not an object literal.`);
					return nullToUndefined(param);
				case messages_1.ParameterStructures.byPosition: return [undefinedToNull(param)];
				default: throw new Error(`Unknown parameter structure ${parameterStructures.toString()}`);
			}
		}
		function computeMessageParams(type, params) {
			let result;
			const numberOfParams = type.numberOfParams;
			switch (numberOfParams) {
				case 0:
					result = void 0;
					break;
				case 1:
					result = computeSingleParam(type.parameterStructures, params[0]);
					break;
				default:
					result = [];
					for (let i = 0; i < params.length && i < numberOfParams; i++) result.push(undefinedToNull(params[i]));
					if (params.length < numberOfParams) for (let i = params.length; i < numberOfParams; i++) result.push(null);
					break;
			}
			return result;
		}
		const connection = {
			sendNotification: (type, ...args) => {
				throwIfClosedOrDisposed();
				let method;
				let messageParams;
				if (Is.string(type)) {
					method = type;
					const first = args[0];
					let paramStart = 0;
					let parameterStructures = messages_1.ParameterStructures.auto;
					if (messages_1.ParameterStructures.is(first)) {
						paramStart = 1;
						parameterStructures = first;
					}
					let paramEnd = args.length;
					const numberOfParams = paramEnd - paramStart;
					switch (numberOfParams) {
						case 0:
							messageParams = void 0;
							break;
						case 1:
							messageParams = computeSingleParam(parameterStructures, args[paramStart]);
							break;
						default:
							if (parameterStructures === messages_1.ParameterStructures.byName) throw new Error(`Received ${numberOfParams} parameters for 'by Name' notification parameter structure.`);
							messageParams = args.slice(paramStart, paramEnd).map((value) => undefinedToNull(value));
							break;
					}
				} else {
					const params = args;
					method = type.method;
					messageParams = computeMessageParams(type, params);
				}
				const notificationMessage = {
					jsonrpc: version,
					method,
					params: messageParams
				};
				traceSendingNotification(notificationMessage);
				return messageWriter.write(notificationMessage).catch((error) => {
					logger.error(`Sending notification failed.`);
					throw error;
				});
			},
			onNotification: (type, handler) => {
				throwIfClosedOrDisposed();
				let method;
				if (Is.func(type)) starNotificationHandler = type;
				else if (handler) if (Is.string(type)) {
					method = type;
					notificationHandlers.set(type, {
						type: void 0,
						handler
					});
				} else {
					method = type.method;
					notificationHandlers.set(type.method, {
						type,
						handler
					});
				}
				return { dispose: () => {
					if (method !== void 0) notificationHandlers.delete(method);
					else starNotificationHandler = void 0;
				} };
			},
			onProgress: (_type, token, handler) => {
				if (progressHandlers.has(token)) throw new Error(`Progress handler for token ${token} already registered`);
				progressHandlers.set(token, handler);
				return { dispose: () => {
					progressHandlers.delete(token);
				} };
			},
			sendProgress: (_type, token, value) => {
				return connection.sendNotification(ProgressNotification.type, {
					token,
					value
				});
			},
			onUnhandledProgress: unhandledProgressEmitter.event,
			sendRequest: (type, ...args) => {
				throwIfClosedOrDisposed();
				throwIfNotListening();
				let method;
				let messageParams;
				let token = void 0;
				if (Is.string(type)) {
					method = type;
					const first = args[0];
					const last = args[args.length - 1];
					let paramStart = 0;
					let parameterStructures = messages_1.ParameterStructures.auto;
					if (messages_1.ParameterStructures.is(first)) {
						paramStart = 1;
						parameterStructures = first;
					}
					let paramEnd = args.length;
					if (cancellation_1.CancellationToken.is(last)) {
						paramEnd = paramEnd - 1;
						token = last;
					}
					const numberOfParams = paramEnd - paramStart;
					switch (numberOfParams) {
						case 0:
							messageParams = void 0;
							break;
						case 1:
							messageParams = computeSingleParam(parameterStructures, args[paramStart]);
							break;
						default:
							if (parameterStructures === messages_1.ParameterStructures.byName) throw new Error(`Received ${numberOfParams} parameters for 'by Name' request parameter structure.`);
							messageParams = args.slice(paramStart, paramEnd).map((value) => undefinedToNull(value));
							break;
					}
				} else {
					const params = args;
					method = type.method;
					messageParams = computeMessageParams(type, params);
					const numberOfParams = type.numberOfParams;
					token = cancellation_1.CancellationToken.is(params[numberOfParams]) ? params[numberOfParams] : void 0;
				}
				const id = sequenceNumber++;
				let disposable;
				if (token) disposable = token.onCancellationRequested(() => {
					const p = cancellationStrategy.sender.sendCancellation(connection, id);
					if (p === void 0) {
						logger.log(`Received no promise from cancellation strategy when cancelling id ${id}`);
						return Promise.resolve();
					} else return p.catch(() => {
						logger.log(`Sending cancellation messages for id ${id} failed`);
					});
				});
				const requestMessage = {
					jsonrpc: version,
					id,
					method,
					params: messageParams
				};
				traceSendingRequest(requestMessage);
				if (typeof cancellationStrategy.sender.enableCancellation === "function") cancellationStrategy.sender.enableCancellation(requestMessage);
				return new Promise(async (resolve, reject) => {
					const resolveWithCleanup = (r) => {
						resolve(r);
						cancellationStrategy.sender.cleanup(id);
						disposable?.dispose();
					};
					const rejectWithCleanup = (r) => {
						reject(r);
						cancellationStrategy.sender.cleanup(id);
						disposable?.dispose();
					};
					const responsePromise = {
						method,
						timerStart: Date.now(),
						resolve: resolveWithCleanup,
						reject: rejectWithCleanup
					};
					try {
						await messageWriter.write(requestMessage);
						responsePromises.set(id, responsePromise);
					} catch (error) {
						logger.error(`Sending request failed.`);
						responsePromise.reject(new messages_1.ResponseError(messages_1.ErrorCodes.MessageWriteError, error.message ? error.message : "Unknown reason"));
						throw error;
					}
				});
			},
			onRequest: (type, handler) => {
				throwIfClosedOrDisposed();
				let method = null;
				if (StarRequestHandler.is(type)) {
					method = void 0;
					starRequestHandler = type;
				} else if (Is.string(type)) {
					method = null;
					if (handler !== void 0) {
						method = type;
						requestHandlers.set(type, {
							handler,
							type: void 0
						});
					}
				} else if (handler !== void 0) {
					method = type.method;
					requestHandlers.set(type.method, {
						type,
						handler
					});
				}
				return { dispose: () => {
					if (method === null) return;
					if (method !== void 0) requestHandlers.delete(method);
					else starRequestHandler = void 0;
				} };
			},
			hasPendingResponse: () => {
				return responsePromises.size > 0;
			},
			trace: async (_value, _tracer, sendNotificationOrTraceOptions) => {
				let _sendNotification = false;
				let _traceFormat = TraceFormat.Text;
				if (sendNotificationOrTraceOptions !== void 0) if (Is.boolean(sendNotificationOrTraceOptions)) _sendNotification = sendNotificationOrTraceOptions;
				else {
					_sendNotification = sendNotificationOrTraceOptions.sendNotification || false;
					_traceFormat = sendNotificationOrTraceOptions.traceFormat || TraceFormat.Text;
				}
				trace = _value;
				traceFormat = _traceFormat;
				if (trace === Trace.Off) tracer = void 0;
				else tracer = _tracer;
				if (_sendNotification && !isClosed() && !isDisposed()) await connection.sendNotification(SetTraceNotification.type, { value: Trace.toString(_value) });
			},
			onError: errorEmitter.event,
			onClose: closeEmitter.event,
			onUnhandledNotification: unhandledNotificationEmitter.event,
			onDispose: disposeEmitter.event,
			end: () => {
				messageWriter.end();
			},
			dispose: () => {
				if (isDisposed()) return;
				state = ConnectionState.Disposed;
				disposeEmitter.fire(void 0);
				const error = new messages_1.ResponseError(messages_1.ErrorCodes.PendingResponseRejected, "Pending response rejected since connection got disposed");
				for (const promise of responsePromises.values()) promise.reject(error);
				responsePromises = /* @__PURE__ */ new Map();
				requestTokens = /* @__PURE__ */ new Map();
				knownCanceledRequests = /* @__PURE__ */ new Set();
				messageQueue = new linkedMap_1.LinkedMap();
				if (Is.func(messageWriter.dispose)) messageWriter.dispose();
				if (Is.func(messageReader.dispose)) messageReader.dispose();
			},
			listen: () => {
				throwIfClosedOrDisposed();
				throwIfListening();
				state = ConnectionState.Listening;
				messageReader.listen(callback);
			},
			inspect: () => {
				(0, ral_1.default)().console.log("inspect");
			}
		};
		connection.onNotification(LogTraceNotification.type, (params) => {
			if (trace === Trace.Off || !tracer) return;
			const verbose = trace === Trace.Verbose || trace === Trace.Compact;
			tracer.log(params.message, verbose ? params.verbose : void 0);
		});
		connection.onNotification(ProgressNotification.type, (params) => {
			const handler = progressHandlers.get(params.token);
			if (handler) handler(params.value);
			else unhandledProgressEmitter.fire(params);
		});
		return connection;
	}
	exports.createMessageConnection = createMessageConnection;
}));
var require_api$1 = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.ProgressType = exports.ProgressToken = exports.createMessageConnection = exports.NullLogger = exports.ConnectionOptions = exports.ConnectionStrategy = exports.AbstractMessageBuffer = exports.WriteableStreamMessageWriter = exports.AbstractMessageWriter = exports.MessageWriter = exports.ReadableStreamMessageReader = exports.AbstractMessageReader = exports.MessageReader = exports.SharedArrayReceiverStrategy = exports.SharedArraySenderStrategy = exports.CancellationToken = exports.CancellationTokenSource = exports.Emitter = exports.Event = exports.Disposable = exports.LRUCache = exports.Touch = exports.LinkedMap = exports.ParameterStructures = exports.NotificationType9 = exports.NotificationType8 = exports.NotificationType7 = exports.NotificationType6 = exports.NotificationType5 = exports.NotificationType4 = exports.NotificationType3 = exports.NotificationType2 = exports.NotificationType1 = exports.NotificationType0 = exports.NotificationType = exports.ErrorCodes = exports.ResponseError = exports.RequestType9 = exports.RequestType8 = exports.RequestType7 = exports.RequestType6 = exports.RequestType5 = exports.RequestType4 = exports.RequestType3 = exports.RequestType2 = exports.RequestType1 = exports.RequestType0 = exports.RequestType = exports.Message = exports.RAL = void 0;
	exports.MessageStrategy = exports.CancellationStrategy = exports.CancellationSenderStrategy = exports.CancellationReceiverStrategy = exports.ConnectionError = exports.ConnectionErrors = exports.LogTraceNotification = exports.SetTraceNotification = exports.TraceFormat = exports.TraceValues = exports.Trace = void 0;
	var messages_1 = require_messages$1();
	Object.defineProperty(exports, "Message", {
		enumerable: true,
		get: function() {
			return messages_1.Message;
		}
	});
	Object.defineProperty(exports, "RequestType", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType;
		}
	});
	Object.defineProperty(exports, "RequestType0", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType0;
		}
	});
	Object.defineProperty(exports, "RequestType1", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType1;
		}
	});
	Object.defineProperty(exports, "RequestType2", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType2;
		}
	});
	Object.defineProperty(exports, "RequestType3", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType3;
		}
	});
	Object.defineProperty(exports, "RequestType4", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType4;
		}
	});
	Object.defineProperty(exports, "RequestType5", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType5;
		}
	});
	Object.defineProperty(exports, "RequestType6", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType6;
		}
	});
	Object.defineProperty(exports, "RequestType7", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType7;
		}
	});
	Object.defineProperty(exports, "RequestType8", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType8;
		}
	});
	Object.defineProperty(exports, "RequestType9", {
		enumerable: true,
		get: function() {
			return messages_1.RequestType9;
		}
	});
	Object.defineProperty(exports, "ResponseError", {
		enumerable: true,
		get: function() {
			return messages_1.ResponseError;
		}
	});
	Object.defineProperty(exports, "ErrorCodes", {
		enumerable: true,
		get: function() {
			return messages_1.ErrorCodes;
		}
	});
	Object.defineProperty(exports, "NotificationType", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType;
		}
	});
	Object.defineProperty(exports, "NotificationType0", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType0;
		}
	});
	Object.defineProperty(exports, "NotificationType1", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType1;
		}
	});
	Object.defineProperty(exports, "NotificationType2", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType2;
		}
	});
	Object.defineProperty(exports, "NotificationType3", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType3;
		}
	});
	Object.defineProperty(exports, "NotificationType4", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType4;
		}
	});
	Object.defineProperty(exports, "NotificationType5", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType5;
		}
	});
	Object.defineProperty(exports, "NotificationType6", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType6;
		}
	});
	Object.defineProperty(exports, "NotificationType7", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType7;
		}
	});
	Object.defineProperty(exports, "NotificationType8", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType8;
		}
	});
	Object.defineProperty(exports, "NotificationType9", {
		enumerable: true,
		get: function() {
			return messages_1.NotificationType9;
		}
	});
	Object.defineProperty(exports, "ParameterStructures", {
		enumerable: true,
		get: function() {
			return messages_1.ParameterStructures;
		}
	});
	var linkedMap_1 = require_linkedMap();
	Object.defineProperty(exports, "LinkedMap", {
		enumerable: true,
		get: function() {
			return linkedMap_1.LinkedMap;
		}
	});
	Object.defineProperty(exports, "LRUCache", {
		enumerable: true,
		get: function() {
			return linkedMap_1.LRUCache;
		}
	});
	Object.defineProperty(exports, "Touch", {
		enumerable: true,
		get: function() {
			return linkedMap_1.Touch;
		}
	});
	var disposable_1 = require_disposable();
	Object.defineProperty(exports, "Disposable", {
		enumerable: true,
		get: function() {
			return disposable_1.Disposable;
		}
	});
	var events_1 = require_events();
	Object.defineProperty(exports, "Event", {
		enumerable: true,
		get: function() {
			return events_1.Event;
		}
	});
	Object.defineProperty(exports, "Emitter", {
		enumerable: true,
		get: function() {
			return events_1.Emitter;
		}
	});
	var cancellation_1 = require_cancellation();
	Object.defineProperty(exports, "CancellationTokenSource", {
		enumerable: true,
		get: function() {
			return cancellation_1.CancellationTokenSource;
		}
	});
	Object.defineProperty(exports, "CancellationToken", {
		enumerable: true,
		get: function() {
			return cancellation_1.CancellationToken;
		}
	});
	var sharedArrayCancellation_1 = require_sharedArrayCancellation();
	Object.defineProperty(exports, "SharedArraySenderStrategy", {
		enumerable: true,
		get: function() {
			return sharedArrayCancellation_1.SharedArraySenderStrategy;
		}
	});
	Object.defineProperty(exports, "SharedArrayReceiverStrategy", {
		enumerable: true,
		get: function() {
			return sharedArrayCancellation_1.SharedArrayReceiverStrategy;
		}
	});
	var messageReader_1 = require_messageReader();
	Object.defineProperty(exports, "MessageReader", {
		enumerable: true,
		get: function() {
			return messageReader_1.MessageReader;
		}
	});
	Object.defineProperty(exports, "AbstractMessageReader", {
		enumerable: true,
		get: function() {
			return messageReader_1.AbstractMessageReader;
		}
	});
	Object.defineProperty(exports, "ReadableStreamMessageReader", {
		enumerable: true,
		get: function() {
			return messageReader_1.ReadableStreamMessageReader;
		}
	});
	var messageWriter_1 = require_messageWriter();
	Object.defineProperty(exports, "MessageWriter", {
		enumerable: true,
		get: function() {
			return messageWriter_1.MessageWriter;
		}
	});
	Object.defineProperty(exports, "AbstractMessageWriter", {
		enumerable: true,
		get: function() {
			return messageWriter_1.AbstractMessageWriter;
		}
	});
	Object.defineProperty(exports, "WriteableStreamMessageWriter", {
		enumerable: true,
		get: function() {
			return messageWriter_1.WriteableStreamMessageWriter;
		}
	});
	var messageBuffer_1 = require_messageBuffer();
	Object.defineProperty(exports, "AbstractMessageBuffer", {
		enumerable: true,
		get: function() {
			return messageBuffer_1.AbstractMessageBuffer;
		}
	});
	var connection_1 = require_connection$1();
	Object.defineProperty(exports, "ConnectionStrategy", {
		enumerable: true,
		get: function() {
			return connection_1.ConnectionStrategy;
		}
	});
	Object.defineProperty(exports, "ConnectionOptions", {
		enumerable: true,
		get: function() {
			return connection_1.ConnectionOptions;
		}
	});
	Object.defineProperty(exports, "NullLogger", {
		enumerable: true,
		get: function() {
			return connection_1.NullLogger;
		}
	});
	Object.defineProperty(exports, "createMessageConnection", {
		enumerable: true,
		get: function() {
			return connection_1.createMessageConnection;
		}
	});
	Object.defineProperty(exports, "ProgressToken", {
		enumerable: true,
		get: function() {
			return connection_1.ProgressToken;
		}
	});
	Object.defineProperty(exports, "ProgressType", {
		enumerable: true,
		get: function() {
			return connection_1.ProgressType;
		}
	});
	Object.defineProperty(exports, "Trace", {
		enumerable: true,
		get: function() {
			return connection_1.Trace;
		}
	});
	Object.defineProperty(exports, "TraceValues", {
		enumerable: true,
		get: function() {
			return connection_1.TraceValues;
		}
	});
	Object.defineProperty(exports, "TraceFormat", {
		enumerable: true,
		get: function() {
			return connection_1.TraceFormat;
		}
	});
	Object.defineProperty(exports, "SetTraceNotification", {
		enumerable: true,
		get: function() {
			return connection_1.SetTraceNotification;
		}
	});
	Object.defineProperty(exports, "LogTraceNotification", {
		enumerable: true,
		get: function() {
			return connection_1.LogTraceNotification;
		}
	});
	Object.defineProperty(exports, "ConnectionErrors", {
		enumerable: true,
		get: function() {
			return connection_1.ConnectionErrors;
		}
	});
	Object.defineProperty(exports, "ConnectionError", {
		enumerable: true,
		get: function() {
			return connection_1.ConnectionError;
		}
	});
	Object.defineProperty(exports, "CancellationReceiverStrategy", {
		enumerable: true,
		get: function() {
			return connection_1.CancellationReceiverStrategy;
		}
	});
	Object.defineProperty(exports, "CancellationSenderStrategy", {
		enumerable: true,
		get: function() {
			return connection_1.CancellationSenderStrategy;
		}
	});
	Object.defineProperty(exports, "CancellationStrategy", {
		enumerable: true,
		get: function() {
			return connection_1.CancellationStrategy;
		}
	});
	Object.defineProperty(exports, "MessageStrategy", {
		enumerable: true,
		get: function() {
			return connection_1.MessageStrategy;
		}
	});
	exports.RAL = require_ral().default;
}));
var require_ril = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	var api_1 = require_api$1();
	var MessageBuffer = class MessageBuffer extends api_1.AbstractMessageBuffer {
		constructor(encoding = "utf-8") {
			super(encoding);
			this.asciiDecoder = new TextDecoder("ascii");
		}
		emptyBuffer() {
			return MessageBuffer.emptyBuffer;
		}
		fromString(value, _encoding) {
			return new TextEncoder().encode(value);
		}
		toString(value, encoding) {
			if (encoding === "ascii") return this.asciiDecoder.decode(value);
			else return new TextDecoder(encoding).decode(value);
		}
		asNative(buffer, length) {
			if (length === void 0) return buffer;
			else return buffer.slice(0, length);
		}
		allocNative(length) {
			return new Uint8Array(length);
		}
	};
	MessageBuffer.emptyBuffer = new Uint8Array(0);
	var ReadableStreamWrapper = class {
		constructor(socket) {
			this.socket = socket;
			this._onData = new api_1.Emitter();
			this._messageListener = (event) => {
				event.data.arrayBuffer().then((buffer) => {
					this._onData.fire(new Uint8Array(buffer));
				}, () => {
					(0, api_1.RAL)().console.error(`Converting blob to array buffer failed.`);
				});
			};
			this.socket.addEventListener("message", this._messageListener);
		}
		onClose(listener) {
			this.socket.addEventListener("close", listener);
			return api_1.Disposable.create(() => this.socket.removeEventListener("close", listener));
		}
		onError(listener) {
			this.socket.addEventListener("error", listener);
			return api_1.Disposable.create(() => this.socket.removeEventListener("error", listener));
		}
		onEnd(listener) {
			this.socket.addEventListener("end", listener);
			return api_1.Disposable.create(() => this.socket.removeEventListener("end", listener));
		}
		onData(listener) {
			return this._onData.event(listener);
		}
	};
	var WritableStreamWrapper = class {
		constructor(socket) {
			this.socket = socket;
		}
		onClose(listener) {
			this.socket.addEventListener("close", listener);
			return api_1.Disposable.create(() => this.socket.removeEventListener("close", listener));
		}
		onError(listener) {
			this.socket.addEventListener("error", listener);
			return api_1.Disposable.create(() => this.socket.removeEventListener("error", listener));
		}
		onEnd(listener) {
			this.socket.addEventListener("end", listener);
			return api_1.Disposable.create(() => this.socket.removeEventListener("end", listener));
		}
		write(data, encoding) {
			if (typeof data === "string") {
				if (encoding !== void 0 && encoding !== "utf-8") throw new Error(`In a Browser environments only utf-8 text encoding is supported. But got encoding: ${encoding}`);
				this.socket.send(data);
			} else this.socket.send(data);
			return Promise.resolve();
		}
		end() {
			this.socket.close();
		}
	};
	var _textEncoder = new TextEncoder();
	var _ril = Object.freeze({
		messageBuffer: Object.freeze({ create: (encoding) => new MessageBuffer(encoding) }),
		applicationJson: Object.freeze({
			encoder: Object.freeze({
				name: "application/json",
				encode: (msg, options) => {
					if (options.charset !== "utf-8") throw new Error(`In a Browser environments only utf-8 text encoding is supported. But got encoding: ${options.charset}`);
					return Promise.resolve(_textEncoder.encode(JSON.stringify(msg, void 0, 0)));
				}
			}),
			decoder: Object.freeze({
				name: "application/json",
				decode: (buffer, options) => {
					if (!(buffer instanceof Uint8Array)) throw new Error(`In a Browser environments only Uint8Arrays are supported.`);
					return Promise.resolve(JSON.parse(new TextDecoder(options.charset).decode(buffer)));
				}
			})
		}),
		stream: Object.freeze({
			asReadableStream: (socket) => new ReadableStreamWrapper(socket),
			asWritableStream: (socket) => new WritableStreamWrapper(socket)
		}),
		console,
		timer: Object.freeze({
			setTimeout(callback, ms, ...args) {
				const handle = setTimeout(callback, ms, ...args);
				return { dispose: () => clearTimeout(handle) };
			},
			setImmediate(callback, ...args) {
				const handle = setTimeout(callback, 0, ...args);
				return { dispose: () => clearTimeout(handle) };
			},
			setInterval(callback, ms, ...args) {
				const handle = setInterval(callback, ms, ...args);
				return { dispose: () => clearInterval(handle) };
			}
		})
	});
	function RIL() {
		return _ril;
	}
	(function(RIL) {
		function install() {
			api_1.RAL.install(_ril);
		}
		RIL.install = install;
	})(RIL || (RIL = {}));
	exports.default = RIL;
}));
var require_main$1 = /* @__PURE__ */ __commonJSMin(((exports) => {
	var __createBinding = exports && exports.__createBinding || (Object.create ? (function(o, m, k, k2) {
		if (k2 === void 0) k2 = k;
		var desc = Object.getOwnPropertyDescriptor(m, k);
		if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) desc = {
			enumerable: true,
			get: function() {
				return m[k];
			}
		};
		Object.defineProperty(o, k2, desc);
	}) : (function(o, m, k, k2) {
		if (k2 === void 0) k2 = k;
		o[k2] = m[k];
	}));
	var __exportStar = exports && exports.__exportStar || function(m, exports$3) {
		for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports$3, p)) __createBinding(exports$3, m, p);
	};
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.createMessageConnection = exports.BrowserMessageWriter = exports.BrowserMessageReader = void 0;
	require_ril().default.install();
	var api_1 = require_api$1();
	__exportStar(require_api$1(), exports);
	var BrowserMessageReader = class extends api_1.AbstractMessageReader {
		constructor(port) {
			super();
			this._onData = new api_1.Emitter();
			this._messageListener = (event) => {
				this._onData.fire(event.data);
			};
			port.addEventListener("error", (event) => this.fireError(event));
			port.onmessage = this._messageListener;
		}
		listen(callback) {
			return this._onData.event(callback);
		}
	};
	exports.BrowserMessageReader = BrowserMessageReader;
	var BrowserMessageWriter = class extends api_1.AbstractMessageWriter {
		constructor(port) {
			super();
			this.port = port;
			this.errorCount = 0;
			port.addEventListener("error", (event) => this.fireError(event));
		}
		write(msg) {
			try {
				this.port.postMessage(msg);
				return Promise.resolve();
			} catch (error) {
				this.handleError(error, msg);
				return Promise.reject(error);
			}
		}
		handleError(error, msg) {
			this.errorCount++;
			this.fireError(error, msg, this.errorCount);
		}
		end() {}
	};
	exports.BrowserMessageWriter = BrowserMessageWriter;
	function createMessageConnection(reader, writer, logger, options) {
		if (logger === void 0) logger = api_1.NullLogger;
		if (api_1.ConnectionStrategy.is(options)) options = { connectionStrategy: options };
		return (0, api_1.createMessageConnection)(reader, writer, logger, options);
	}
	exports.createMessageConnection = createMessageConnection;
}));
var require_browser = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	module.exports = require_main$1();
}));
var require_messages = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.ProtocolNotificationType = exports.ProtocolNotificationType0 = exports.ProtocolRequestType = exports.ProtocolRequestType0 = exports.RegistrationType = exports.MessageDirection = void 0;
	var vscode_jsonrpc_1 = require_main$1();
	var MessageDirection;
	(function(MessageDirection) {
		MessageDirection["clientToServer"] = "clientToServer";
		MessageDirection["serverToClient"] = "serverToClient";
		MessageDirection["both"] = "both";
	})(MessageDirection || (exports.MessageDirection = MessageDirection = {}));
	var RegistrationType = class {
		constructor(method) {
			this.method = method;
		}
	};
	exports.RegistrationType = RegistrationType;
	var ProtocolRequestType0 = class extends vscode_jsonrpc_1.RequestType0 {
		constructor(method) {
			super(method);
		}
	};
	exports.ProtocolRequestType0 = ProtocolRequestType0;
	var ProtocolRequestType = class extends vscode_jsonrpc_1.RequestType {
		constructor(method) {
			super(method, vscode_jsonrpc_1.ParameterStructures.byName);
		}
	};
	exports.ProtocolRequestType = ProtocolRequestType;
	var ProtocolNotificationType0 = class extends vscode_jsonrpc_1.NotificationType0 {
		constructor(method) {
			super(method);
		}
	};
	exports.ProtocolNotificationType0 = ProtocolNotificationType0;
	var ProtocolNotificationType = class extends vscode_jsonrpc_1.NotificationType {
		constructor(method) {
			super(method, vscode_jsonrpc_1.ParameterStructures.byName);
		}
	};
	exports.ProtocolNotificationType = ProtocolNotificationType;
}));
var require_is = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.objectLiteral = exports.typedArray = exports.stringArray = exports.array = exports.func = exports.error = exports.number = exports.string = exports.boolean = void 0;
	function boolean(value) {
		return value === true || value === false;
	}
	exports.boolean = boolean;
	function string(value) {
		return typeof value === "string" || value instanceof String;
	}
	exports.string = string;
	function number(value) {
		return typeof value === "number" || value instanceof Number;
	}
	exports.number = number;
	function error(value) {
		return value instanceof Error;
	}
	exports.error = error;
	function func(value) {
		return typeof value === "function";
	}
	exports.func = func;
	function array(value) {
		return Array.isArray(value);
	}
	exports.array = array;
	function stringArray(value) {
		return array(value) && value.every((elem) => string(elem));
	}
	exports.stringArray = stringArray;
	function typedArray(value, check) {
		return Array.isArray(value) && value.every(check);
	}
	exports.typedArray = typedArray;
	function objectLiteral(value) {
		return value !== null && typeof value === "object";
	}
	exports.objectLiteral = objectLiteral;
}));
var require_protocol_implementation = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.ImplementationRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to resolve the implementation locations of a symbol at a given text
	* document position. The request's parameter is of type {@link TextDocumentPositionParams}
	* the response is of type {@link Definition} or a Thenable that resolves to such.
	*/
	var ImplementationRequest;
	(function(ImplementationRequest) {
		ImplementationRequest.method = "textDocument/implementation";
		ImplementationRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		ImplementationRequest.type = new messages_1.ProtocolRequestType(ImplementationRequest.method);
	})(ImplementationRequest || (exports.ImplementationRequest = ImplementationRequest = {}));
}));
var require_protocol_typeDefinition = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.TypeDefinitionRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to resolve the type definition locations of a symbol at a given text
	* document position. The request's parameter is of type {@link TextDocumentPositionParams}
	* the response is of type {@link Definition} or a Thenable that resolves to such.
	*/
	var TypeDefinitionRequest;
	(function(TypeDefinitionRequest) {
		TypeDefinitionRequest.method = "textDocument/typeDefinition";
		TypeDefinitionRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		TypeDefinitionRequest.type = new messages_1.ProtocolRequestType(TypeDefinitionRequest.method);
	})(TypeDefinitionRequest || (exports.TypeDefinitionRequest = TypeDefinitionRequest = {}));
}));
var require_protocol_workspaceFolder = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.DidChangeWorkspaceFoldersNotification = exports.WorkspaceFoldersRequest = void 0;
	var messages_1 = require_messages();
	/**
	* The `workspace/workspaceFolders` is sent from the server to the client to fetch the open workspace folders.
	*/
	var WorkspaceFoldersRequest;
	(function(WorkspaceFoldersRequest) {
		WorkspaceFoldersRequest.method = "workspace/workspaceFolders";
		WorkspaceFoldersRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		WorkspaceFoldersRequest.type = new messages_1.ProtocolRequestType0(WorkspaceFoldersRequest.method);
	})(WorkspaceFoldersRequest || (exports.WorkspaceFoldersRequest = WorkspaceFoldersRequest = {}));
	/**
	* The `workspace/didChangeWorkspaceFolders` notification is sent from the client to the server when the workspace
	* folder configuration changes.
	*/
	var DidChangeWorkspaceFoldersNotification;
	(function(DidChangeWorkspaceFoldersNotification) {
		DidChangeWorkspaceFoldersNotification.method = "workspace/didChangeWorkspaceFolders";
		DidChangeWorkspaceFoldersNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidChangeWorkspaceFoldersNotification.type = new messages_1.ProtocolNotificationType(DidChangeWorkspaceFoldersNotification.method);
	})(DidChangeWorkspaceFoldersNotification || (exports.DidChangeWorkspaceFoldersNotification = DidChangeWorkspaceFoldersNotification = {}));
}));
var require_protocol_configuration = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.ConfigurationRequest = void 0;
	var messages_1 = require_messages();
	/**
	* The 'workspace/configuration' request is sent from the server to the client to fetch a certain
	* configuration setting.
	*
	* This pull model replaces the old push model were the client signaled configuration change via an
	* event. If the server still needs to react to configuration changes (since the server caches the
	* result of `workspace/configuration` requests) the server should register for an empty configuration
	* change event and empty the cache if such an event is received.
	*/
	var ConfigurationRequest;
	(function(ConfigurationRequest) {
		ConfigurationRequest.method = "workspace/configuration";
		ConfigurationRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		ConfigurationRequest.type = new messages_1.ProtocolRequestType(ConfigurationRequest.method);
	})(ConfigurationRequest || (exports.ConfigurationRequest = ConfigurationRequest = {}));
}));
var require_protocol_colorProvider = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.ColorPresentationRequest = exports.DocumentColorRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to list all color symbols found in a given text document. The request's
	* parameter is of type {@link DocumentColorParams} the
	* response is of type {@link ColorInformation ColorInformation[]} or a Thenable
	* that resolves to such.
	*/
	var DocumentColorRequest;
	(function(DocumentColorRequest) {
		DocumentColorRequest.method = "textDocument/documentColor";
		DocumentColorRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DocumentColorRequest.type = new messages_1.ProtocolRequestType(DocumentColorRequest.method);
	})(DocumentColorRequest || (exports.DocumentColorRequest = DocumentColorRequest = {}));
	/**
	* A request to list all presentation for a color. The request's
	* parameter is of type {@link ColorPresentationParams} the
	* response is of type {@link ColorInformation ColorInformation[]} or a Thenable
	* that resolves to such.
	*/
	var ColorPresentationRequest;
	(function(ColorPresentationRequest) {
		ColorPresentationRequest.method = "textDocument/colorPresentation";
		ColorPresentationRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		ColorPresentationRequest.type = new messages_1.ProtocolRequestType(ColorPresentationRequest.method);
	})(ColorPresentationRequest || (exports.ColorPresentationRequest = ColorPresentationRequest = {}));
}));
var require_protocol_foldingRange = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.FoldingRangeRefreshRequest = exports.FoldingRangeRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to provide folding ranges in a document. The request's
	* parameter is of type {@link FoldingRangeParams}, the
	* response is of type {@link FoldingRangeList} or a Thenable
	* that resolves to such.
	*/
	var FoldingRangeRequest;
	(function(FoldingRangeRequest) {
		FoldingRangeRequest.method = "textDocument/foldingRange";
		FoldingRangeRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		FoldingRangeRequest.type = new messages_1.ProtocolRequestType(FoldingRangeRequest.method);
	})(FoldingRangeRequest || (exports.FoldingRangeRequest = FoldingRangeRequest = {}));
	/**
	* @since 3.18.0
	* @proposed
	*/
	var FoldingRangeRefreshRequest;
	(function(FoldingRangeRefreshRequest) {
		FoldingRangeRefreshRequest.method = `workspace/foldingRange/refresh`;
		FoldingRangeRefreshRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		FoldingRangeRefreshRequest.type = new messages_1.ProtocolRequestType0(FoldingRangeRefreshRequest.method);
	})(FoldingRangeRefreshRequest || (exports.FoldingRangeRefreshRequest = FoldingRangeRefreshRequest = {}));
}));
var require_protocol_declaration = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.DeclarationRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to resolve the type definition locations of a symbol at a given text
	* document position. The request's parameter is of type {@link TextDocumentPositionParams}
	* the response is of type {@link Declaration} or a typed array of {@link DeclarationLink}
	* or a Thenable that resolves to such.
	*/
	var DeclarationRequest;
	(function(DeclarationRequest) {
		DeclarationRequest.method = "textDocument/declaration";
		DeclarationRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DeclarationRequest.type = new messages_1.ProtocolRequestType(DeclarationRequest.method);
	})(DeclarationRequest || (exports.DeclarationRequest = DeclarationRequest = {}));
}));
var require_protocol_selectionRange = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.SelectionRangeRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to provide selection ranges in a document. The request's
	* parameter is of type {@link SelectionRangeParams}, the
	* response is of type {@link SelectionRange SelectionRange[]} or a Thenable
	* that resolves to such.
	*/
	var SelectionRangeRequest;
	(function(SelectionRangeRequest) {
		SelectionRangeRequest.method = "textDocument/selectionRange";
		SelectionRangeRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		SelectionRangeRequest.type = new messages_1.ProtocolRequestType(SelectionRangeRequest.method);
	})(SelectionRangeRequest || (exports.SelectionRangeRequest = SelectionRangeRequest = {}));
}));
var require_protocol_progress = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.WorkDoneProgressCancelNotification = exports.WorkDoneProgressCreateRequest = exports.WorkDoneProgress = void 0;
	var vscode_jsonrpc_1 = require_main$1();
	var messages_1 = require_messages();
	var WorkDoneProgress;
	(function(WorkDoneProgress) {
		WorkDoneProgress.type = new vscode_jsonrpc_1.ProgressType();
		function is(value) {
			return value === WorkDoneProgress.type;
		}
		WorkDoneProgress.is = is;
	})(WorkDoneProgress || (exports.WorkDoneProgress = WorkDoneProgress = {}));
	/**
	* The `window/workDoneProgress/create` request is sent from the server to the client to initiate progress
	* reporting from the server.
	*/
	var WorkDoneProgressCreateRequest;
	(function(WorkDoneProgressCreateRequest) {
		WorkDoneProgressCreateRequest.method = "window/workDoneProgress/create";
		WorkDoneProgressCreateRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		WorkDoneProgressCreateRequest.type = new messages_1.ProtocolRequestType(WorkDoneProgressCreateRequest.method);
	})(WorkDoneProgressCreateRequest || (exports.WorkDoneProgressCreateRequest = WorkDoneProgressCreateRequest = {}));
	/**
	* The `window/workDoneProgress/cancel` notification is sent from  the client to the server to cancel a progress
	* initiated on the server side.
	*/
	var WorkDoneProgressCancelNotification;
	(function(WorkDoneProgressCancelNotification) {
		WorkDoneProgressCancelNotification.method = "window/workDoneProgress/cancel";
		WorkDoneProgressCancelNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		WorkDoneProgressCancelNotification.type = new messages_1.ProtocolNotificationType(WorkDoneProgressCancelNotification.method);
	})(WorkDoneProgressCancelNotification || (exports.WorkDoneProgressCancelNotification = WorkDoneProgressCancelNotification = {}));
}));
var require_protocol_callHierarchy = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.CallHierarchyOutgoingCallsRequest = exports.CallHierarchyIncomingCallsRequest = exports.CallHierarchyPrepareRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to result a `CallHierarchyItem` in a document at a given position.
	* Can be used as an input to an incoming or outgoing call hierarchy.
	*
	* @since 3.16.0
	*/
	var CallHierarchyPrepareRequest;
	(function(CallHierarchyPrepareRequest) {
		CallHierarchyPrepareRequest.method = "textDocument/prepareCallHierarchy";
		CallHierarchyPrepareRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		CallHierarchyPrepareRequest.type = new messages_1.ProtocolRequestType(CallHierarchyPrepareRequest.method);
	})(CallHierarchyPrepareRequest || (exports.CallHierarchyPrepareRequest = CallHierarchyPrepareRequest = {}));
	/**
	* A request to resolve the incoming calls for a given `CallHierarchyItem`.
	*
	* @since 3.16.0
	*/
	var CallHierarchyIncomingCallsRequest;
	(function(CallHierarchyIncomingCallsRequest) {
		CallHierarchyIncomingCallsRequest.method = "callHierarchy/incomingCalls";
		CallHierarchyIncomingCallsRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		CallHierarchyIncomingCallsRequest.type = new messages_1.ProtocolRequestType(CallHierarchyIncomingCallsRequest.method);
	})(CallHierarchyIncomingCallsRequest || (exports.CallHierarchyIncomingCallsRequest = CallHierarchyIncomingCallsRequest = {}));
	/**
	* A request to resolve the outgoing calls for a given `CallHierarchyItem`.
	*
	* @since 3.16.0
	*/
	var CallHierarchyOutgoingCallsRequest;
	(function(CallHierarchyOutgoingCallsRequest) {
		CallHierarchyOutgoingCallsRequest.method = "callHierarchy/outgoingCalls";
		CallHierarchyOutgoingCallsRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		CallHierarchyOutgoingCallsRequest.type = new messages_1.ProtocolRequestType(CallHierarchyOutgoingCallsRequest.method);
	})(CallHierarchyOutgoingCallsRequest || (exports.CallHierarchyOutgoingCallsRequest = CallHierarchyOutgoingCallsRequest = {}));
}));
var require_protocol_semanticTokens = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.SemanticTokensRefreshRequest = exports.SemanticTokensRangeRequest = exports.SemanticTokensDeltaRequest = exports.SemanticTokensRequest = exports.SemanticTokensRegistrationType = exports.TokenFormat = void 0;
	var messages_1 = require_messages();
	var TokenFormat;
	(function(TokenFormat) {
		TokenFormat.Relative = "relative";
	})(TokenFormat || (exports.TokenFormat = TokenFormat = {}));
	var SemanticTokensRegistrationType;
	(function(SemanticTokensRegistrationType) {
		SemanticTokensRegistrationType.method = "textDocument/semanticTokens";
		SemanticTokensRegistrationType.type = new messages_1.RegistrationType(SemanticTokensRegistrationType.method);
	})(SemanticTokensRegistrationType || (exports.SemanticTokensRegistrationType = SemanticTokensRegistrationType = {}));
	/**
	* @since 3.16.0
	*/
	var SemanticTokensRequest;
	(function(SemanticTokensRequest) {
		SemanticTokensRequest.method = "textDocument/semanticTokens/full";
		SemanticTokensRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		SemanticTokensRequest.type = new messages_1.ProtocolRequestType(SemanticTokensRequest.method);
		SemanticTokensRequest.registrationMethod = SemanticTokensRegistrationType.method;
	})(SemanticTokensRequest || (exports.SemanticTokensRequest = SemanticTokensRequest = {}));
	/**
	* @since 3.16.0
	*/
	var SemanticTokensDeltaRequest;
	(function(SemanticTokensDeltaRequest) {
		SemanticTokensDeltaRequest.method = "textDocument/semanticTokens/full/delta";
		SemanticTokensDeltaRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		SemanticTokensDeltaRequest.type = new messages_1.ProtocolRequestType(SemanticTokensDeltaRequest.method);
		SemanticTokensDeltaRequest.registrationMethod = SemanticTokensRegistrationType.method;
	})(SemanticTokensDeltaRequest || (exports.SemanticTokensDeltaRequest = SemanticTokensDeltaRequest = {}));
	/**
	* @since 3.16.0
	*/
	var SemanticTokensRangeRequest;
	(function(SemanticTokensRangeRequest) {
		SemanticTokensRangeRequest.method = "textDocument/semanticTokens/range";
		SemanticTokensRangeRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		SemanticTokensRangeRequest.type = new messages_1.ProtocolRequestType(SemanticTokensRangeRequest.method);
		SemanticTokensRangeRequest.registrationMethod = SemanticTokensRegistrationType.method;
	})(SemanticTokensRangeRequest || (exports.SemanticTokensRangeRequest = SemanticTokensRangeRequest = {}));
	/**
	* @since 3.16.0
	*/
	var SemanticTokensRefreshRequest;
	(function(SemanticTokensRefreshRequest) {
		SemanticTokensRefreshRequest.method = `workspace/semanticTokens/refresh`;
		SemanticTokensRefreshRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		SemanticTokensRefreshRequest.type = new messages_1.ProtocolRequestType0(SemanticTokensRefreshRequest.method);
	})(SemanticTokensRefreshRequest || (exports.SemanticTokensRefreshRequest = SemanticTokensRefreshRequest = {}));
}));
var require_protocol_showDocument = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.ShowDocumentRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to show a document. This request might open an
	* external program depending on the value of the URI to open.
	* For example a request to open `https://code.visualstudio.com/`
	* will very likely open the URI in a WEB browser.
	*
	* @since 3.16.0
	*/
	var ShowDocumentRequest;
	(function(ShowDocumentRequest) {
		ShowDocumentRequest.method = "window/showDocument";
		ShowDocumentRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		ShowDocumentRequest.type = new messages_1.ProtocolRequestType(ShowDocumentRequest.method);
	})(ShowDocumentRequest || (exports.ShowDocumentRequest = ShowDocumentRequest = {}));
}));
var require_protocol_linkedEditingRange = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.LinkedEditingRangeRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to provide ranges that can be edited together.
	*
	* @since 3.16.0
	*/
	var LinkedEditingRangeRequest;
	(function(LinkedEditingRangeRequest) {
		LinkedEditingRangeRequest.method = "textDocument/linkedEditingRange";
		LinkedEditingRangeRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		LinkedEditingRangeRequest.type = new messages_1.ProtocolRequestType(LinkedEditingRangeRequest.method);
	})(LinkedEditingRangeRequest || (exports.LinkedEditingRangeRequest = LinkedEditingRangeRequest = {}));
}));
var require_protocol_fileOperations = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.WillDeleteFilesRequest = exports.DidDeleteFilesNotification = exports.DidRenameFilesNotification = exports.WillRenameFilesRequest = exports.DidCreateFilesNotification = exports.WillCreateFilesRequest = exports.FileOperationPatternKind = void 0;
	var messages_1 = require_messages();
	/**
	* A pattern kind describing if a glob pattern matches a file a folder or
	* both.
	*
	* @since 3.16.0
	*/
	var FileOperationPatternKind;
	(function(FileOperationPatternKind) {
		/**
		* The pattern matches a file only.
		*/
		FileOperationPatternKind.file = "file";
		/**
		* The pattern matches a folder only.
		*/
		FileOperationPatternKind.folder = "folder";
	})(FileOperationPatternKind || (exports.FileOperationPatternKind = FileOperationPatternKind = {}));
	/**
	* The will create files request is sent from the client to the server before files are actually
	* created as long as the creation is triggered from within the client.
	*
	* The request can return a `WorkspaceEdit` which will be applied to workspace before the
	* files are created. Hence the `WorkspaceEdit` can not manipulate the content of the file
	* to be created.
	*
	* @since 3.16.0
	*/
	var WillCreateFilesRequest;
	(function(WillCreateFilesRequest) {
		WillCreateFilesRequest.method = "workspace/willCreateFiles";
		WillCreateFilesRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		WillCreateFilesRequest.type = new messages_1.ProtocolRequestType(WillCreateFilesRequest.method);
	})(WillCreateFilesRequest || (exports.WillCreateFilesRequest = WillCreateFilesRequest = {}));
	/**
	* The did create files notification is sent from the client to the server when
	* files were created from within the client.
	*
	* @since 3.16.0
	*/
	var DidCreateFilesNotification;
	(function(DidCreateFilesNotification) {
		DidCreateFilesNotification.method = "workspace/didCreateFiles";
		DidCreateFilesNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidCreateFilesNotification.type = new messages_1.ProtocolNotificationType(DidCreateFilesNotification.method);
	})(DidCreateFilesNotification || (exports.DidCreateFilesNotification = DidCreateFilesNotification = {}));
	/**
	* The will rename files request is sent from the client to the server before files are actually
	* renamed as long as the rename is triggered from within the client.
	*
	* @since 3.16.0
	*/
	var WillRenameFilesRequest;
	(function(WillRenameFilesRequest) {
		WillRenameFilesRequest.method = "workspace/willRenameFiles";
		WillRenameFilesRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		WillRenameFilesRequest.type = new messages_1.ProtocolRequestType(WillRenameFilesRequest.method);
	})(WillRenameFilesRequest || (exports.WillRenameFilesRequest = WillRenameFilesRequest = {}));
	/**
	* The did rename files notification is sent from the client to the server when
	* files were renamed from within the client.
	*
	* @since 3.16.0
	*/
	var DidRenameFilesNotification;
	(function(DidRenameFilesNotification) {
		DidRenameFilesNotification.method = "workspace/didRenameFiles";
		DidRenameFilesNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidRenameFilesNotification.type = new messages_1.ProtocolNotificationType(DidRenameFilesNotification.method);
	})(DidRenameFilesNotification || (exports.DidRenameFilesNotification = DidRenameFilesNotification = {}));
	/**
	* The will delete files request is sent from the client to the server before files are actually
	* deleted as long as the deletion is triggered from within the client.
	*
	* @since 3.16.0
	*/
	var DidDeleteFilesNotification;
	(function(DidDeleteFilesNotification) {
		DidDeleteFilesNotification.method = "workspace/didDeleteFiles";
		DidDeleteFilesNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidDeleteFilesNotification.type = new messages_1.ProtocolNotificationType(DidDeleteFilesNotification.method);
	})(DidDeleteFilesNotification || (exports.DidDeleteFilesNotification = DidDeleteFilesNotification = {}));
	/**
	* The did delete files notification is sent from the client to the server when
	* files were deleted from within the client.
	*
	* @since 3.16.0
	*/
	var WillDeleteFilesRequest;
	(function(WillDeleteFilesRequest) {
		WillDeleteFilesRequest.method = "workspace/willDeleteFiles";
		WillDeleteFilesRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		WillDeleteFilesRequest.type = new messages_1.ProtocolRequestType(WillDeleteFilesRequest.method);
	})(WillDeleteFilesRequest || (exports.WillDeleteFilesRequest = WillDeleteFilesRequest = {}));
}));
var require_protocol_moniker = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.MonikerRequest = exports.MonikerKind = exports.UniquenessLevel = void 0;
	var messages_1 = require_messages();
	/**
	* Moniker uniqueness level to define scope of the moniker.
	*
	* @since 3.16.0
	*/
	var UniquenessLevel;
	(function(UniquenessLevel) {
		/**
		* The moniker is only unique inside a document
		*/
		UniquenessLevel.document = "document";
		/**
		* The moniker is unique inside a project for which a dump got created
		*/
		UniquenessLevel.project = "project";
		/**
		* The moniker is unique inside the group to which a project belongs
		*/
		UniquenessLevel.group = "group";
		/**
		* The moniker is unique inside the moniker scheme.
		*/
		UniquenessLevel.scheme = "scheme";
		/**
		* The moniker is globally unique
		*/
		UniquenessLevel.global = "global";
	})(UniquenessLevel || (exports.UniquenessLevel = UniquenessLevel = {}));
	/**
	* The moniker kind.
	*
	* @since 3.16.0
	*/
	var MonikerKind;
	(function(MonikerKind) {
		/**
		* The moniker represent a symbol that is imported into a project
		*/
		MonikerKind.$import = "import";
		/**
		* The moniker represents a symbol that is exported from a project
		*/
		MonikerKind.$export = "export";
		/**
		* The moniker represents a symbol that is local to a project (e.g. a local
		* variable of a function, a class not visible outside the project, ...)
		*/
		MonikerKind.local = "local";
	})(MonikerKind || (exports.MonikerKind = MonikerKind = {}));
	/**
	* A request to get the moniker of a symbol at a given text document position.
	* The request parameter is of type {@link TextDocumentPositionParams}.
	* The response is of type {@link Moniker Moniker[]} or `null`.
	*/
	var MonikerRequest;
	(function(MonikerRequest) {
		MonikerRequest.method = "textDocument/moniker";
		MonikerRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		MonikerRequest.type = new messages_1.ProtocolRequestType(MonikerRequest.method);
	})(MonikerRequest || (exports.MonikerRequest = MonikerRequest = {}));
}));
var require_protocol_typeHierarchy = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.TypeHierarchySubtypesRequest = exports.TypeHierarchySupertypesRequest = exports.TypeHierarchyPrepareRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to result a `TypeHierarchyItem` in a document at a given position.
	* Can be used as an input to a subtypes or supertypes type hierarchy.
	*
	* @since 3.17.0
	*/
	var TypeHierarchyPrepareRequest;
	(function(TypeHierarchyPrepareRequest) {
		TypeHierarchyPrepareRequest.method = "textDocument/prepareTypeHierarchy";
		TypeHierarchyPrepareRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		TypeHierarchyPrepareRequest.type = new messages_1.ProtocolRequestType(TypeHierarchyPrepareRequest.method);
	})(TypeHierarchyPrepareRequest || (exports.TypeHierarchyPrepareRequest = TypeHierarchyPrepareRequest = {}));
	/**
	* A request to resolve the supertypes for a given `TypeHierarchyItem`.
	*
	* @since 3.17.0
	*/
	var TypeHierarchySupertypesRequest;
	(function(TypeHierarchySupertypesRequest) {
		TypeHierarchySupertypesRequest.method = "typeHierarchy/supertypes";
		TypeHierarchySupertypesRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		TypeHierarchySupertypesRequest.type = new messages_1.ProtocolRequestType(TypeHierarchySupertypesRequest.method);
	})(TypeHierarchySupertypesRequest || (exports.TypeHierarchySupertypesRequest = TypeHierarchySupertypesRequest = {}));
	/**
	* A request to resolve the subtypes for a given `TypeHierarchyItem`.
	*
	* @since 3.17.0
	*/
	var TypeHierarchySubtypesRequest;
	(function(TypeHierarchySubtypesRequest) {
		TypeHierarchySubtypesRequest.method = "typeHierarchy/subtypes";
		TypeHierarchySubtypesRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		TypeHierarchySubtypesRequest.type = new messages_1.ProtocolRequestType(TypeHierarchySubtypesRequest.method);
	})(TypeHierarchySubtypesRequest || (exports.TypeHierarchySubtypesRequest = TypeHierarchySubtypesRequest = {}));
}));
var require_protocol_inlineValue = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.InlineValueRefreshRequest = exports.InlineValueRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to provide inline values in a document. The request's parameter is of
	* type {@link InlineValueParams}, the response is of type
	* {@link InlineValue InlineValue[]} or a Thenable that resolves to such.
	*
	* @since 3.17.0
	*/
	var InlineValueRequest;
	(function(InlineValueRequest) {
		InlineValueRequest.method = "textDocument/inlineValue";
		InlineValueRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		InlineValueRequest.type = new messages_1.ProtocolRequestType(InlineValueRequest.method);
	})(InlineValueRequest || (exports.InlineValueRequest = InlineValueRequest = {}));
	/**
	* @since 3.17.0
	*/
	var InlineValueRefreshRequest;
	(function(InlineValueRefreshRequest) {
		InlineValueRefreshRequest.method = `workspace/inlineValue/refresh`;
		InlineValueRefreshRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		InlineValueRefreshRequest.type = new messages_1.ProtocolRequestType0(InlineValueRefreshRequest.method);
	})(InlineValueRefreshRequest || (exports.InlineValueRefreshRequest = InlineValueRefreshRequest = {}));
}));
var require_protocol_inlayHint = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.InlayHintRefreshRequest = exports.InlayHintResolveRequest = exports.InlayHintRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to provide inlay hints in a document. The request's parameter is of
	* type {@link InlayHintsParams}, the response is of type
	* {@link InlayHint InlayHint[]} or a Thenable that resolves to such.
	*
	* @since 3.17.0
	*/
	var InlayHintRequest;
	(function(InlayHintRequest) {
		InlayHintRequest.method = "textDocument/inlayHint";
		InlayHintRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		InlayHintRequest.type = new messages_1.ProtocolRequestType(InlayHintRequest.method);
	})(InlayHintRequest || (exports.InlayHintRequest = InlayHintRequest = {}));
	/**
	* A request to resolve additional properties for an inlay hint.
	* The request's parameter is of type {@link InlayHint}, the response is
	* of type {@link InlayHint} or a Thenable that resolves to such.
	*
	* @since 3.17.0
	*/
	var InlayHintResolveRequest;
	(function(InlayHintResolveRequest) {
		InlayHintResolveRequest.method = "inlayHint/resolve";
		InlayHintResolveRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		InlayHintResolveRequest.type = new messages_1.ProtocolRequestType(InlayHintResolveRequest.method);
	})(InlayHintResolveRequest || (exports.InlayHintResolveRequest = InlayHintResolveRequest = {}));
	/**
	* @since 3.17.0
	*/
	var InlayHintRefreshRequest;
	(function(InlayHintRefreshRequest) {
		InlayHintRefreshRequest.method = `workspace/inlayHint/refresh`;
		InlayHintRefreshRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		InlayHintRefreshRequest.type = new messages_1.ProtocolRequestType0(InlayHintRefreshRequest.method);
	})(InlayHintRefreshRequest || (exports.InlayHintRefreshRequest = InlayHintRefreshRequest = {}));
}));
var require_protocol_diagnostic = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.DiagnosticRefreshRequest = exports.WorkspaceDiagnosticRequest = exports.DocumentDiagnosticRequest = exports.DocumentDiagnosticReportKind = exports.DiagnosticServerCancellationData = void 0;
	var vscode_jsonrpc_1 = require_main$1();
	var Is = require_is();
	var messages_1 = require_messages();
	/**
	* @since 3.17.0
	*/
	var DiagnosticServerCancellationData;
	(function(DiagnosticServerCancellationData) {
		function is(value) {
			const candidate = value;
			return candidate && Is.boolean(candidate.retriggerRequest);
		}
		DiagnosticServerCancellationData.is = is;
	})(DiagnosticServerCancellationData || (exports.DiagnosticServerCancellationData = DiagnosticServerCancellationData = {}));
	/**
	* The document diagnostic report kinds.
	*
	* @since 3.17.0
	*/
	var DocumentDiagnosticReportKind;
	(function(DocumentDiagnosticReportKind) {
		/**
		* A diagnostic report with a full
		* set of problems.
		*/
		DocumentDiagnosticReportKind.Full = "full";
		/**
		* A report indicating that the last
		* returned report is still accurate.
		*/
		DocumentDiagnosticReportKind.Unchanged = "unchanged";
	})(DocumentDiagnosticReportKind || (exports.DocumentDiagnosticReportKind = DocumentDiagnosticReportKind = {}));
	/**
	* The document diagnostic request definition.
	*
	* @since 3.17.0
	*/
	var DocumentDiagnosticRequest;
	(function(DocumentDiagnosticRequest) {
		DocumentDiagnosticRequest.method = "textDocument/diagnostic";
		DocumentDiagnosticRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DocumentDiagnosticRequest.type = new messages_1.ProtocolRequestType(DocumentDiagnosticRequest.method);
		DocumentDiagnosticRequest.partialResult = new vscode_jsonrpc_1.ProgressType();
	})(DocumentDiagnosticRequest || (exports.DocumentDiagnosticRequest = DocumentDiagnosticRequest = {}));
	/**
	* The workspace diagnostic request definition.
	*
	* @since 3.17.0
	*/
	var WorkspaceDiagnosticRequest;
	(function(WorkspaceDiagnosticRequest) {
		WorkspaceDiagnosticRequest.method = "workspace/diagnostic";
		WorkspaceDiagnosticRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		WorkspaceDiagnosticRequest.type = new messages_1.ProtocolRequestType(WorkspaceDiagnosticRequest.method);
		WorkspaceDiagnosticRequest.partialResult = new vscode_jsonrpc_1.ProgressType();
	})(WorkspaceDiagnosticRequest || (exports.WorkspaceDiagnosticRequest = WorkspaceDiagnosticRequest = {}));
	/**
	* The diagnostic refresh request definition.
	*
	* @since 3.17.0
	*/
	var DiagnosticRefreshRequest;
	(function(DiagnosticRefreshRequest) {
		DiagnosticRefreshRequest.method = `workspace/diagnostic/refresh`;
		DiagnosticRefreshRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		DiagnosticRefreshRequest.type = new messages_1.ProtocolRequestType0(DiagnosticRefreshRequest.method);
	})(DiagnosticRefreshRequest || (exports.DiagnosticRefreshRequest = DiagnosticRefreshRequest = {}));
}));
var require_protocol_notebook = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.DidCloseNotebookDocumentNotification = exports.DidSaveNotebookDocumentNotification = exports.DidChangeNotebookDocumentNotification = exports.NotebookCellArrayChange = exports.DidOpenNotebookDocumentNotification = exports.NotebookDocumentSyncRegistrationType = exports.NotebookDocument = exports.NotebookCell = exports.ExecutionSummary = exports.NotebookCellKind = void 0;
	var vscode_languageserver_types_1 = (init_main(), __toCommonJS(main_exports));
	var Is = require_is();
	var messages_1 = require_messages();
	/**
	* A notebook cell kind.
	*
	* @since 3.17.0
	*/
	var NotebookCellKind;
	(function(NotebookCellKind) {
		/**
		* A markup-cell is formatted source that is used for display.
		*/
		NotebookCellKind.Markup = 1;
		/**
		* A code-cell is source code.
		*/
		NotebookCellKind.Code = 2;
		function is(value) {
			return value === 1 || value === 2;
		}
		NotebookCellKind.is = is;
	})(NotebookCellKind || (exports.NotebookCellKind = NotebookCellKind = {}));
	var ExecutionSummary;
	(function(ExecutionSummary) {
		function create(executionOrder, success) {
			const result = { executionOrder };
			if (success === true || success === false) result.success = success;
			return result;
		}
		ExecutionSummary.create = create;
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && vscode_languageserver_types_1.uinteger.is(candidate.executionOrder) && (candidate.success === void 0 || Is.boolean(candidate.success));
		}
		ExecutionSummary.is = is;
		function equals(one, other) {
			if (one === other) return true;
			if (one === null || one === void 0 || other === null || other === void 0) return false;
			return one.executionOrder === other.executionOrder && one.success === other.success;
		}
		ExecutionSummary.equals = equals;
	})(ExecutionSummary || (exports.ExecutionSummary = ExecutionSummary = {}));
	var NotebookCell;
	(function(NotebookCell) {
		function create(kind, document) {
			return {
				kind,
				document
			};
		}
		NotebookCell.create = create;
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && NotebookCellKind.is(candidate.kind) && vscode_languageserver_types_1.DocumentUri.is(candidate.document) && (candidate.metadata === void 0 || Is.objectLiteral(candidate.metadata));
		}
		NotebookCell.is = is;
		function diff(one, two) {
			const result = /* @__PURE__ */ new Set();
			if (one.document !== two.document) result.add("document");
			if (one.kind !== two.kind) result.add("kind");
			if (one.executionSummary !== two.executionSummary) result.add("executionSummary");
			if ((one.metadata !== void 0 || two.metadata !== void 0) && !equalsMetadata(one.metadata, two.metadata)) result.add("metadata");
			if ((one.executionSummary !== void 0 || two.executionSummary !== void 0) && !ExecutionSummary.equals(one.executionSummary, two.executionSummary)) result.add("executionSummary");
			return result;
		}
		NotebookCell.diff = diff;
		function equalsMetadata(one, other) {
			if (one === other) return true;
			if (one === null || one === void 0 || other === null || other === void 0) return false;
			if (typeof one !== typeof other) return false;
			if (typeof one !== "object") return false;
			const oneArray = Array.isArray(one);
			const otherArray = Array.isArray(other);
			if (oneArray !== otherArray) return false;
			if (oneArray && otherArray) {
				if (one.length !== other.length) return false;
				for (let i = 0; i < one.length; i++) if (!equalsMetadata(one[i], other[i])) return false;
			}
			if (Is.objectLiteral(one) && Is.objectLiteral(other)) {
				const oneKeys = Object.keys(one);
				const otherKeys = Object.keys(other);
				if (oneKeys.length !== otherKeys.length) return false;
				oneKeys.sort();
				otherKeys.sort();
				if (!equalsMetadata(oneKeys, otherKeys)) return false;
				for (let i = 0; i < oneKeys.length; i++) {
					const prop = oneKeys[i];
					if (!equalsMetadata(one[prop], other[prop])) return false;
				}
			}
			return true;
		}
	})(NotebookCell || (exports.NotebookCell = NotebookCell = {}));
	var NotebookDocument;
	(function(NotebookDocument) {
		function create(uri, notebookType, version, cells) {
			return {
				uri,
				notebookType,
				version,
				cells
			};
		}
		NotebookDocument.create = create;
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && Is.string(candidate.uri) && vscode_languageserver_types_1.integer.is(candidate.version) && Is.typedArray(candidate.cells, NotebookCell.is);
		}
		NotebookDocument.is = is;
	})(NotebookDocument || (exports.NotebookDocument = NotebookDocument = {}));
	var NotebookDocumentSyncRegistrationType;
	(function(NotebookDocumentSyncRegistrationType) {
		NotebookDocumentSyncRegistrationType.method = "notebookDocument/sync";
		NotebookDocumentSyncRegistrationType.messageDirection = messages_1.MessageDirection.clientToServer;
		NotebookDocumentSyncRegistrationType.type = new messages_1.RegistrationType(NotebookDocumentSyncRegistrationType.method);
	})(NotebookDocumentSyncRegistrationType || (exports.NotebookDocumentSyncRegistrationType = NotebookDocumentSyncRegistrationType = {}));
	/**
	* A notification sent when a notebook opens.
	*
	* @since 3.17.0
	*/
	var DidOpenNotebookDocumentNotification;
	(function(DidOpenNotebookDocumentNotification) {
		DidOpenNotebookDocumentNotification.method = "notebookDocument/didOpen";
		DidOpenNotebookDocumentNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidOpenNotebookDocumentNotification.type = new messages_1.ProtocolNotificationType(DidOpenNotebookDocumentNotification.method);
		DidOpenNotebookDocumentNotification.registrationMethod = NotebookDocumentSyncRegistrationType.method;
	})(DidOpenNotebookDocumentNotification || (exports.DidOpenNotebookDocumentNotification = DidOpenNotebookDocumentNotification = {}));
	var NotebookCellArrayChange;
	(function(NotebookCellArrayChange) {
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && vscode_languageserver_types_1.uinteger.is(candidate.start) && vscode_languageserver_types_1.uinteger.is(candidate.deleteCount) && (candidate.cells === void 0 || Is.typedArray(candidate.cells, NotebookCell.is));
		}
		NotebookCellArrayChange.is = is;
		function create(start, deleteCount, cells) {
			const result = {
				start,
				deleteCount
			};
			if (cells !== void 0) result.cells = cells;
			return result;
		}
		NotebookCellArrayChange.create = create;
	})(NotebookCellArrayChange || (exports.NotebookCellArrayChange = NotebookCellArrayChange = {}));
	var DidChangeNotebookDocumentNotification;
	(function(DidChangeNotebookDocumentNotification) {
		DidChangeNotebookDocumentNotification.method = "notebookDocument/didChange";
		DidChangeNotebookDocumentNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidChangeNotebookDocumentNotification.type = new messages_1.ProtocolNotificationType(DidChangeNotebookDocumentNotification.method);
		DidChangeNotebookDocumentNotification.registrationMethod = NotebookDocumentSyncRegistrationType.method;
	})(DidChangeNotebookDocumentNotification || (exports.DidChangeNotebookDocumentNotification = DidChangeNotebookDocumentNotification = {}));
	/**
	* A notification sent when a notebook document is saved.
	*
	* @since 3.17.0
	*/
	var DidSaveNotebookDocumentNotification;
	(function(DidSaveNotebookDocumentNotification) {
		DidSaveNotebookDocumentNotification.method = "notebookDocument/didSave";
		DidSaveNotebookDocumentNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidSaveNotebookDocumentNotification.type = new messages_1.ProtocolNotificationType(DidSaveNotebookDocumentNotification.method);
		DidSaveNotebookDocumentNotification.registrationMethod = NotebookDocumentSyncRegistrationType.method;
	})(DidSaveNotebookDocumentNotification || (exports.DidSaveNotebookDocumentNotification = DidSaveNotebookDocumentNotification = {}));
	/**
	* A notification sent when a notebook closes.
	*
	* @since 3.17.0
	*/
	var DidCloseNotebookDocumentNotification;
	(function(DidCloseNotebookDocumentNotification) {
		DidCloseNotebookDocumentNotification.method = "notebookDocument/didClose";
		DidCloseNotebookDocumentNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidCloseNotebookDocumentNotification.type = new messages_1.ProtocolNotificationType(DidCloseNotebookDocumentNotification.method);
		DidCloseNotebookDocumentNotification.registrationMethod = NotebookDocumentSyncRegistrationType.method;
	})(DidCloseNotebookDocumentNotification || (exports.DidCloseNotebookDocumentNotification = DidCloseNotebookDocumentNotification = {}));
}));
var require_protocol_inlineCompletion = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.InlineCompletionRequest = void 0;
	var messages_1 = require_messages();
	/**
	* A request to provide inline completions in a document. The request's parameter is of
	* type {@link InlineCompletionParams}, the response is of type
	* {@link InlineCompletion InlineCompletion[]} or a Thenable that resolves to such.
	*
	* @since 3.18.0
	* @proposed
	*/
	var InlineCompletionRequest;
	(function(InlineCompletionRequest) {
		InlineCompletionRequest.method = "textDocument/inlineCompletion";
		InlineCompletionRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		InlineCompletionRequest.type = new messages_1.ProtocolRequestType(InlineCompletionRequest.method);
	})(InlineCompletionRequest || (exports.InlineCompletionRequest = InlineCompletionRequest = {}));
}));
var require_protocol = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.WorkspaceSymbolRequest = exports.CodeActionResolveRequest = exports.CodeActionRequest = exports.DocumentSymbolRequest = exports.DocumentHighlightRequest = exports.ReferencesRequest = exports.DefinitionRequest = exports.SignatureHelpRequest = exports.SignatureHelpTriggerKind = exports.HoverRequest = exports.CompletionResolveRequest = exports.CompletionRequest = exports.CompletionTriggerKind = exports.PublishDiagnosticsNotification = exports.WatchKind = exports.RelativePattern = exports.FileChangeType = exports.DidChangeWatchedFilesNotification = exports.WillSaveTextDocumentWaitUntilRequest = exports.WillSaveTextDocumentNotification = exports.TextDocumentSaveReason = exports.DidSaveTextDocumentNotification = exports.DidCloseTextDocumentNotification = exports.DidChangeTextDocumentNotification = exports.TextDocumentContentChangeEvent = exports.DidOpenTextDocumentNotification = exports.TextDocumentSyncKind = exports.TelemetryEventNotification = exports.LogMessageNotification = exports.ShowMessageRequest = exports.ShowMessageNotification = exports.MessageType = exports.DidChangeConfigurationNotification = exports.ExitNotification = exports.ShutdownRequest = exports.InitializedNotification = exports.InitializeErrorCodes = exports.InitializeRequest = exports.WorkDoneProgressOptions = exports.TextDocumentRegistrationOptions = exports.StaticRegistrationOptions = exports.PositionEncodingKind = exports.FailureHandlingKind = exports.ResourceOperationKind = exports.UnregistrationRequest = exports.RegistrationRequest = exports.DocumentSelector = exports.NotebookCellTextDocumentFilter = exports.NotebookDocumentFilter = exports.TextDocumentFilter = void 0;
	exports.MonikerRequest = exports.MonikerKind = exports.UniquenessLevel = exports.WillDeleteFilesRequest = exports.DidDeleteFilesNotification = exports.WillRenameFilesRequest = exports.DidRenameFilesNotification = exports.WillCreateFilesRequest = exports.DidCreateFilesNotification = exports.FileOperationPatternKind = exports.LinkedEditingRangeRequest = exports.ShowDocumentRequest = exports.SemanticTokensRegistrationType = exports.SemanticTokensRefreshRequest = exports.SemanticTokensRangeRequest = exports.SemanticTokensDeltaRequest = exports.SemanticTokensRequest = exports.TokenFormat = exports.CallHierarchyPrepareRequest = exports.CallHierarchyOutgoingCallsRequest = exports.CallHierarchyIncomingCallsRequest = exports.WorkDoneProgressCancelNotification = exports.WorkDoneProgressCreateRequest = exports.WorkDoneProgress = exports.SelectionRangeRequest = exports.DeclarationRequest = exports.FoldingRangeRefreshRequest = exports.FoldingRangeRequest = exports.ColorPresentationRequest = exports.DocumentColorRequest = exports.ConfigurationRequest = exports.DidChangeWorkspaceFoldersNotification = exports.WorkspaceFoldersRequest = exports.TypeDefinitionRequest = exports.ImplementationRequest = exports.ApplyWorkspaceEditRequest = exports.ExecuteCommandRequest = exports.PrepareRenameRequest = exports.RenameRequest = exports.PrepareSupportDefaultBehavior = exports.DocumentOnTypeFormattingRequest = exports.DocumentRangesFormattingRequest = exports.DocumentRangeFormattingRequest = exports.DocumentFormattingRequest = exports.DocumentLinkResolveRequest = exports.DocumentLinkRequest = exports.CodeLensRefreshRequest = exports.CodeLensResolveRequest = exports.CodeLensRequest = exports.WorkspaceSymbolResolveRequest = void 0;
	exports.InlineCompletionRequest = exports.DidCloseNotebookDocumentNotification = exports.DidSaveNotebookDocumentNotification = exports.DidChangeNotebookDocumentNotification = exports.NotebookCellArrayChange = exports.DidOpenNotebookDocumentNotification = exports.NotebookDocumentSyncRegistrationType = exports.NotebookDocument = exports.NotebookCell = exports.ExecutionSummary = exports.NotebookCellKind = exports.DiagnosticRefreshRequest = exports.WorkspaceDiagnosticRequest = exports.DocumentDiagnosticRequest = exports.DocumentDiagnosticReportKind = exports.DiagnosticServerCancellationData = exports.InlayHintRefreshRequest = exports.InlayHintResolveRequest = exports.InlayHintRequest = exports.InlineValueRefreshRequest = exports.InlineValueRequest = exports.TypeHierarchySupertypesRequest = exports.TypeHierarchySubtypesRequest = exports.TypeHierarchyPrepareRequest = void 0;
	var messages_1 = require_messages();
	var vscode_languageserver_types_1 = (init_main(), __toCommonJS(main_exports));
	var Is = require_is();
	var protocol_implementation_1 = require_protocol_implementation();
	Object.defineProperty(exports, "ImplementationRequest", {
		enumerable: true,
		get: function() {
			return protocol_implementation_1.ImplementationRequest;
		}
	});
	var protocol_typeDefinition_1 = require_protocol_typeDefinition();
	Object.defineProperty(exports, "TypeDefinitionRequest", {
		enumerable: true,
		get: function() {
			return protocol_typeDefinition_1.TypeDefinitionRequest;
		}
	});
	var protocol_workspaceFolder_1 = require_protocol_workspaceFolder();
	Object.defineProperty(exports, "WorkspaceFoldersRequest", {
		enumerable: true,
		get: function() {
			return protocol_workspaceFolder_1.WorkspaceFoldersRequest;
		}
	});
	Object.defineProperty(exports, "DidChangeWorkspaceFoldersNotification", {
		enumerable: true,
		get: function() {
			return protocol_workspaceFolder_1.DidChangeWorkspaceFoldersNotification;
		}
	});
	var protocol_configuration_1 = require_protocol_configuration();
	Object.defineProperty(exports, "ConfigurationRequest", {
		enumerable: true,
		get: function() {
			return protocol_configuration_1.ConfigurationRequest;
		}
	});
	var protocol_colorProvider_1 = require_protocol_colorProvider();
	Object.defineProperty(exports, "DocumentColorRequest", {
		enumerable: true,
		get: function() {
			return protocol_colorProvider_1.DocumentColorRequest;
		}
	});
	Object.defineProperty(exports, "ColorPresentationRequest", {
		enumerable: true,
		get: function() {
			return protocol_colorProvider_1.ColorPresentationRequest;
		}
	});
	var protocol_foldingRange_1 = require_protocol_foldingRange();
	Object.defineProperty(exports, "FoldingRangeRequest", {
		enumerable: true,
		get: function() {
			return protocol_foldingRange_1.FoldingRangeRequest;
		}
	});
	Object.defineProperty(exports, "FoldingRangeRefreshRequest", {
		enumerable: true,
		get: function() {
			return protocol_foldingRange_1.FoldingRangeRefreshRequest;
		}
	});
	var protocol_declaration_1 = require_protocol_declaration();
	Object.defineProperty(exports, "DeclarationRequest", {
		enumerable: true,
		get: function() {
			return protocol_declaration_1.DeclarationRequest;
		}
	});
	var protocol_selectionRange_1 = require_protocol_selectionRange();
	Object.defineProperty(exports, "SelectionRangeRequest", {
		enumerable: true,
		get: function() {
			return protocol_selectionRange_1.SelectionRangeRequest;
		}
	});
	var protocol_progress_1 = require_protocol_progress();
	Object.defineProperty(exports, "WorkDoneProgress", {
		enumerable: true,
		get: function() {
			return protocol_progress_1.WorkDoneProgress;
		}
	});
	Object.defineProperty(exports, "WorkDoneProgressCreateRequest", {
		enumerable: true,
		get: function() {
			return protocol_progress_1.WorkDoneProgressCreateRequest;
		}
	});
	Object.defineProperty(exports, "WorkDoneProgressCancelNotification", {
		enumerable: true,
		get: function() {
			return protocol_progress_1.WorkDoneProgressCancelNotification;
		}
	});
	var protocol_callHierarchy_1 = require_protocol_callHierarchy();
	Object.defineProperty(exports, "CallHierarchyIncomingCallsRequest", {
		enumerable: true,
		get: function() {
			return protocol_callHierarchy_1.CallHierarchyIncomingCallsRequest;
		}
	});
	Object.defineProperty(exports, "CallHierarchyOutgoingCallsRequest", {
		enumerable: true,
		get: function() {
			return protocol_callHierarchy_1.CallHierarchyOutgoingCallsRequest;
		}
	});
	Object.defineProperty(exports, "CallHierarchyPrepareRequest", {
		enumerable: true,
		get: function() {
			return protocol_callHierarchy_1.CallHierarchyPrepareRequest;
		}
	});
	var protocol_semanticTokens_1 = require_protocol_semanticTokens();
	Object.defineProperty(exports, "TokenFormat", {
		enumerable: true,
		get: function() {
			return protocol_semanticTokens_1.TokenFormat;
		}
	});
	Object.defineProperty(exports, "SemanticTokensRequest", {
		enumerable: true,
		get: function() {
			return protocol_semanticTokens_1.SemanticTokensRequest;
		}
	});
	Object.defineProperty(exports, "SemanticTokensDeltaRequest", {
		enumerable: true,
		get: function() {
			return protocol_semanticTokens_1.SemanticTokensDeltaRequest;
		}
	});
	Object.defineProperty(exports, "SemanticTokensRangeRequest", {
		enumerable: true,
		get: function() {
			return protocol_semanticTokens_1.SemanticTokensRangeRequest;
		}
	});
	Object.defineProperty(exports, "SemanticTokensRefreshRequest", {
		enumerable: true,
		get: function() {
			return protocol_semanticTokens_1.SemanticTokensRefreshRequest;
		}
	});
	Object.defineProperty(exports, "SemanticTokensRegistrationType", {
		enumerable: true,
		get: function() {
			return protocol_semanticTokens_1.SemanticTokensRegistrationType;
		}
	});
	var protocol_showDocument_1 = require_protocol_showDocument();
	Object.defineProperty(exports, "ShowDocumentRequest", {
		enumerable: true,
		get: function() {
			return protocol_showDocument_1.ShowDocumentRequest;
		}
	});
	var protocol_linkedEditingRange_1 = require_protocol_linkedEditingRange();
	Object.defineProperty(exports, "LinkedEditingRangeRequest", {
		enumerable: true,
		get: function() {
			return protocol_linkedEditingRange_1.LinkedEditingRangeRequest;
		}
	});
	var protocol_fileOperations_1 = require_protocol_fileOperations();
	Object.defineProperty(exports, "FileOperationPatternKind", {
		enumerable: true,
		get: function() {
			return protocol_fileOperations_1.FileOperationPatternKind;
		}
	});
	Object.defineProperty(exports, "DidCreateFilesNotification", {
		enumerable: true,
		get: function() {
			return protocol_fileOperations_1.DidCreateFilesNotification;
		}
	});
	Object.defineProperty(exports, "WillCreateFilesRequest", {
		enumerable: true,
		get: function() {
			return protocol_fileOperations_1.WillCreateFilesRequest;
		}
	});
	Object.defineProperty(exports, "DidRenameFilesNotification", {
		enumerable: true,
		get: function() {
			return protocol_fileOperations_1.DidRenameFilesNotification;
		}
	});
	Object.defineProperty(exports, "WillRenameFilesRequest", {
		enumerable: true,
		get: function() {
			return protocol_fileOperations_1.WillRenameFilesRequest;
		}
	});
	Object.defineProperty(exports, "DidDeleteFilesNotification", {
		enumerable: true,
		get: function() {
			return protocol_fileOperations_1.DidDeleteFilesNotification;
		}
	});
	Object.defineProperty(exports, "WillDeleteFilesRequest", {
		enumerable: true,
		get: function() {
			return protocol_fileOperations_1.WillDeleteFilesRequest;
		}
	});
	var protocol_moniker_1 = require_protocol_moniker();
	Object.defineProperty(exports, "UniquenessLevel", {
		enumerable: true,
		get: function() {
			return protocol_moniker_1.UniquenessLevel;
		}
	});
	Object.defineProperty(exports, "MonikerKind", {
		enumerable: true,
		get: function() {
			return protocol_moniker_1.MonikerKind;
		}
	});
	Object.defineProperty(exports, "MonikerRequest", {
		enumerable: true,
		get: function() {
			return protocol_moniker_1.MonikerRequest;
		}
	});
	var protocol_typeHierarchy_1 = require_protocol_typeHierarchy();
	Object.defineProperty(exports, "TypeHierarchyPrepareRequest", {
		enumerable: true,
		get: function() {
			return protocol_typeHierarchy_1.TypeHierarchyPrepareRequest;
		}
	});
	Object.defineProperty(exports, "TypeHierarchySubtypesRequest", {
		enumerable: true,
		get: function() {
			return protocol_typeHierarchy_1.TypeHierarchySubtypesRequest;
		}
	});
	Object.defineProperty(exports, "TypeHierarchySupertypesRequest", {
		enumerable: true,
		get: function() {
			return protocol_typeHierarchy_1.TypeHierarchySupertypesRequest;
		}
	});
	var protocol_inlineValue_1 = require_protocol_inlineValue();
	Object.defineProperty(exports, "InlineValueRequest", {
		enumerable: true,
		get: function() {
			return protocol_inlineValue_1.InlineValueRequest;
		}
	});
	Object.defineProperty(exports, "InlineValueRefreshRequest", {
		enumerable: true,
		get: function() {
			return protocol_inlineValue_1.InlineValueRefreshRequest;
		}
	});
	var protocol_inlayHint_1 = require_protocol_inlayHint();
	Object.defineProperty(exports, "InlayHintRequest", {
		enumerable: true,
		get: function() {
			return protocol_inlayHint_1.InlayHintRequest;
		}
	});
	Object.defineProperty(exports, "InlayHintResolveRequest", {
		enumerable: true,
		get: function() {
			return protocol_inlayHint_1.InlayHintResolveRequest;
		}
	});
	Object.defineProperty(exports, "InlayHintRefreshRequest", {
		enumerable: true,
		get: function() {
			return protocol_inlayHint_1.InlayHintRefreshRequest;
		}
	});
	var protocol_diagnostic_1 = require_protocol_diagnostic();
	Object.defineProperty(exports, "DiagnosticServerCancellationData", {
		enumerable: true,
		get: function() {
			return protocol_diagnostic_1.DiagnosticServerCancellationData;
		}
	});
	Object.defineProperty(exports, "DocumentDiagnosticReportKind", {
		enumerable: true,
		get: function() {
			return protocol_diagnostic_1.DocumentDiagnosticReportKind;
		}
	});
	Object.defineProperty(exports, "DocumentDiagnosticRequest", {
		enumerable: true,
		get: function() {
			return protocol_diagnostic_1.DocumentDiagnosticRequest;
		}
	});
	Object.defineProperty(exports, "WorkspaceDiagnosticRequest", {
		enumerable: true,
		get: function() {
			return protocol_diagnostic_1.WorkspaceDiagnosticRequest;
		}
	});
	Object.defineProperty(exports, "DiagnosticRefreshRequest", {
		enumerable: true,
		get: function() {
			return protocol_diagnostic_1.DiagnosticRefreshRequest;
		}
	});
	var protocol_notebook_1 = require_protocol_notebook();
	Object.defineProperty(exports, "NotebookCellKind", {
		enumerable: true,
		get: function() {
			return protocol_notebook_1.NotebookCellKind;
		}
	});
	Object.defineProperty(exports, "ExecutionSummary", {
		enumerable: true,
		get: function() {
			return protocol_notebook_1.ExecutionSummary;
		}
	});
	Object.defineProperty(exports, "NotebookCell", {
		enumerable: true,
		get: function() {
			return protocol_notebook_1.NotebookCell;
		}
	});
	Object.defineProperty(exports, "NotebookDocument", {
		enumerable: true,
		get: function() {
			return protocol_notebook_1.NotebookDocument;
		}
	});
	Object.defineProperty(exports, "NotebookDocumentSyncRegistrationType", {
		enumerable: true,
		get: function() {
			return protocol_notebook_1.NotebookDocumentSyncRegistrationType;
		}
	});
	Object.defineProperty(exports, "DidOpenNotebookDocumentNotification", {
		enumerable: true,
		get: function() {
			return protocol_notebook_1.DidOpenNotebookDocumentNotification;
		}
	});
	Object.defineProperty(exports, "NotebookCellArrayChange", {
		enumerable: true,
		get: function() {
			return protocol_notebook_1.NotebookCellArrayChange;
		}
	});
	Object.defineProperty(exports, "DidChangeNotebookDocumentNotification", {
		enumerable: true,
		get: function() {
			return protocol_notebook_1.DidChangeNotebookDocumentNotification;
		}
	});
	Object.defineProperty(exports, "DidSaveNotebookDocumentNotification", {
		enumerable: true,
		get: function() {
			return protocol_notebook_1.DidSaveNotebookDocumentNotification;
		}
	});
	Object.defineProperty(exports, "DidCloseNotebookDocumentNotification", {
		enumerable: true,
		get: function() {
			return protocol_notebook_1.DidCloseNotebookDocumentNotification;
		}
	});
	var protocol_inlineCompletion_1 = require_protocol_inlineCompletion();
	Object.defineProperty(exports, "InlineCompletionRequest", {
		enumerable: true,
		get: function() {
			return protocol_inlineCompletion_1.InlineCompletionRequest;
		}
	});
	/**
	* The TextDocumentFilter namespace provides helper functions to work with
	* {@link TextDocumentFilter} literals.
	*
	* @since 3.17.0
	*/
	var TextDocumentFilter;
	(function(TextDocumentFilter) {
		function is(value) {
			const candidate = value;
			return Is.string(candidate) || Is.string(candidate.language) || Is.string(candidate.scheme) || Is.string(candidate.pattern);
		}
		TextDocumentFilter.is = is;
	})(TextDocumentFilter || (exports.TextDocumentFilter = TextDocumentFilter = {}));
	/**
	* The NotebookDocumentFilter namespace provides helper functions to work with
	* {@link NotebookDocumentFilter} literals.
	*
	* @since 3.17.0
	*/
	var NotebookDocumentFilter;
	(function(NotebookDocumentFilter) {
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && (Is.string(candidate.notebookType) || Is.string(candidate.scheme) || Is.string(candidate.pattern));
		}
		NotebookDocumentFilter.is = is;
	})(NotebookDocumentFilter || (exports.NotebookDocumentFilter = NotebookDocumentFilter = {}));
	/**
	* The NotebookCellTextDocumentFilter namespace provides helper functions to work with
	* {@link NotebookCellTextDocumentFilter} literals.
	*
	* @since 3.17.0
	*/
	var NotebookCellTextDocumentFilter;
	(function(NotebookCellTextDocumentFilter) {
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && (Is.string(candidate.notebook) || NotebookDocumentFilter.is(candidate.notebook)) && (candidate.language === void 0 || Is.string(candidate.language));
		}
		NotebookCellTextDocumentFilter.is = is;
	})(NotebookCellTextDocumentFilter || (exports.NotebookCellTextDocumentFilter = NotebookCellTextDocumentFilter = {}));
	/**
	* The DocumentSelector namespace provides helper functions to work with
	* {@link DocumentSelector}s.
	*/
	var DocumentSelector;
	(function(DocumentSelector) {
		function is(value) {
			if (!Array.isArray(value)) return false;
			for (let elem of value) if (!Is.string(elem) && !TextDocumentFilter.is(elem) && !NotebookCellTextDocumentFilter.is(elem)) return false;
			return true;
		}
		DocumentSelector.is = is;
	})(DocumentSelector || (exports.DocumentSelector = DocumentSelector = {}));
	/**
	* The `client/registerCapability` request is sent from the server to the client to register a new capability
	* handler on the client side.
	*/
	var RegistrationRequest;
	(function(RegistrationRequest) {
		RegistrationRequest.method = "client/registerCapability";
		RegistrationRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		RegistrationRequest.type = new messages_1.ProtocolRequestType(RegistrationRequest.method);
	})(RegistrationRequest || (exports.RegistrationRequest = RegistrationRequest = {}));
	/**
	* The `client/unregisterCapability` request is sent from the server to the client to unregister a previously registered capability
	* handler on the client side.
	*/
	var UnregistrationRequest;
	(function(UnregistrationRequest) {
		UnregistrationRequest.method = "client/unregisterCapability";
		UnregistrationRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		UnregistrationRequest.type = new messages_1.ProtocolRequestType(UnregistrationRequest.method);
	})(UnregistrationRequest || (exports.UnregistrationRequest = UnregistrationRequest = {}));
	var ResourceOperationKind;
	(function(ResourceOperationKind) {
		/**
		* Supports creating new files and folders.
		*/
		ResourceOperationKind.Create = "create";
		/**
		* Supports renaming existing files and folders.
		*/
		ResourceOperationKind.Rename = "rename";
		/**
		* Supports deleting existing files and folders.
		*/
		ResourceOperationKind.Delete = "delete";
	})(ResourceOperationKind || (exports.ResourceOperationKind = ResourceOperationKind = {}));
	var FailureHandlingKind;
	(function(FailureHandlingKind) {
		/**
		* Applying the workspace change is simply aborted if one of the changes provided
		* fails. All operations executed before the failing operation stay executed.
		*/
		FailureHandlingKind.Abort = "abort";
		/**
		* All operations are executed transactional. That means they either all
		* succeed or no changes at all are applied to the workspace.
		*/
		FailureHandlingKind.Transactional = "transactional";
		/**
		* If the workspace edit contains only textual file changes they are executed transactional.
		* If resource changes (create, rename or delete file) are part of the change the failure
		* handling strategy is abort.
		*/
		FailureHandlingKind.TextOnlyTransactional = "textOnlyTransactional";
		/**
		* The client tries to undo the operations already executed. But there is no
		* guarantee that this is succeeding.
		*/
		FailureHandlingKind.Undo = "undo";
	})(FailureHandlingKind || (exports.FailureHandlingKind = FailureHandlingKind = {}));
	/**
	* A set of predefined position encoding kinds.
	*
	* @since 3.17.0
	*/
	var PositionEncodingKind;
	(function(PositionEncodingKind) {
		/**
		* Character offsets count UTF-8 code units (e.g. bytes).
		*/
		PositionEncodingKind.UTF8 = "utf-8";
		/**
		* Character offsets count UTF-16 code units.
		*
		* This is the default and must always be supported
		* by servers
		*/
		PositionEncodingKind.UTF16 = "utf-16";
		/**
		* Character offsets count UTF-32 code units.
		*
		* Implementation note: these are the same as Unicode codepoints,
		* so this `PositionEncodingKind` may also be used for an
		* encoding-agnostic representation of character offsets.
		*/
		PositionEncodingKind.UTF32 = "utf-32";
	})(PositionEncodingKind || (exports.PositionEncodingKind = PositionEncodingKind = {}));
	/**
	* The StaticRegistrationOptions namespace provides helper functions to work with
	* {@link StaticRegistrationOptions} literals.
	*/
	var StaticRegistrationOptions;
	(function(StaticRegistrationOptions) {
		function hasId(value) {
			const candidate = value;
			return candidate && Is.string(candidate.id) && candidate.id.length > 0;
		}
		StaticRegistrationOptions.hasId = hasId;
	})(StaticRegistrationOptions || (exports.StaticRegistrationOptions = StaticRegistrationOptions = {}));
	/**
	* The TextDocumentRegistrationOptions namespace provides helper functions to work with
	* {@link TextDocumentRegistrationOptions} literals.
	*/
	var TextDocumentRegistrationOptions;
	(function(TextDocumentRegistrationOptions) {
		function is(value) {
			const candidate = value;
			return candidate && (candidate.documentSelector === null || DocumentSelector.is(candidate.documentSelector));
		}
		TextDocumentRegistrationOptions.is = is;
	})(TextDocumentRegistrationOptions || (exports.TextDocumentRegistrationOptions = TextDocumentRegistrationOptions = {}));
	/**
	* The WorkDoneProgressOptions namespace provides helper functions to work with
	* {@link WorkDoneProgressOptions} literals.
	*/
	var WorkDoneProgressOptions;
	(function(WorkDoneProgressOptions) {
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && (candidate.workDoneProgress === void 0 || Is.boolean(candidate.workDoneProgress));
		}
		WorkDoneProgressOptions.is = is;
		function hasWorkDoneProgress(value) {
			const candidate = value;
			return candidate && Is.boolean(candidate.workDoneProgress);
		}
		WorkDoneProgressOptions.hasWorkDoneProgress = hasWorkDoneProgress;
	})(WorkDoneProgressOptions || (exports.WorkDoneProgressOptions = WorkDoneProgressOptions = {}));
	/**
	* The initialize request is sent from the client to the server.
	* It is sent once as the request after starting up the server.
	* The requests parameter is of type {@link InitializeParams}
	* the response if of type {@link InitializeResult} of a Thenable that
	* resolves to such.
	*/
	var InitializeRequest;
	(function(InitializeRequest) {
		InitializeRequest.method = "initialize";
		InitializeRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		InitializeRequest.type = new messages_1.ProtocolRequestType(InitializeRequest.method);
	})(InitializeRequest || (exports.InitializeRequest = InitializeRequest = {}));
	/**
	* Known error codes for an `InitializeErrorCodes`;
	*/
	var InitializeErrorCodes;
	(function(InitializeErrorCodes) {
		/**
		* If the protocol version provided by the client can't be handled by the server.
		*
		* @deprecated This initialize error got replaced by client capabilities. There is
		* no version handshake in version 3.0x
		*/
		InitializeErrorCodes.unknownProtocolVersion = 1;
	})(InitializeErrorCodes || (exports.InitializeErrorCodes = InitializeErrorCodes = {}));
	/**
	* The initialized notification is sent from the client to the
	* server after the client is fully initialized and the server
	* is allowed to send requests from the server to the client.
	*/
	var InitializedNotification;
	(function(InitializedNotification) {
		InitializedNotification.method = "initialized";
		InitializedNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		InitializedNotification.type = new messages_1.ProtocolNotificationType(InitializedNotification.method);
	})(InitializedNotification || (exports.InitializedNotification = InitializedNotification = {}));
	/**
	* A shutdown request is sent from the client to the server.
	* It is sent once when the client decides to shutdown the
	* server. The only notification that is sent after a shutdown request
	* is the exit event.
	*/
	var ShutdownRequest;
	(function(ShutdownRequest) {
		ShutdownRequest.method = "shutdown";
		ShutdownRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		ShutdownRequest.type = new messages_1.ProtocolRequestType0(ShutdownRequest.method);
	})(ShutdownRequest || (exports.ShutdownRequest = ShutdownRequest = {}));
	/**
	* The exit event is sent from the client to the server to
	* ask the server to exit its process.
	*/
	var ExitNotification;
	(function(ExitNotification) {
		ExitNotification.method = "exit";
		ExitNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		ExitNotification.type = new messages_1.ProtocolNotificationType0(ExitNotification.method);
	})(ExitNotification || (exports.ExitNotification = ExitNotification = {}));
	/**
	* The configuration change notification is sent from the client to the server
	* when the client's configuration has changed. The notification contains
	* the changed configuration as defined by the language client.
	*/
	var DidChangeConfigurationNotification;
	(function(DidChangeConfigurationNotification) {
		DidChangeConfigurationNotification.method = "workspace/didChangeConfiguration";
		DidChangeConfigurationNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidChangeConfigurationNotification.type = new messages_1.ProtocolNotificationType(DidChangeConfigurationNotification.method);
	})(DidChangeConfigurationNotification || (exports.DidChangeConfigurationNotification = DidChangeConfigurationNotification = {}));
	/**
	* The message type
	*/
	var MessageType;
	(function(MessageType) {
		/**
		* An error message.
		*/
		MessageType.Error = 1;
		/**
		* A warning message.
		*/
		MessageType.Warning = 2;
		/**
		* An information message.
		*/
		MessageType.Info = 3;
		/**
		* A log message.
		*/
		MessageType.Log = 4;
		/**
		* A debug message.
		*
		* @since 3.18.0
		*/
		MessageType.Debug = 5;
	})(MessageType || (exports.MessageType = MessageType = {}));
	/**
	* The show message notification is sent from a server to a client to ask
	* the client to display a particular message in the user interface.
	*/
	var ShowMessageNotification;
	(function(ShowMessageNotification) {
		ShowMessageNotification.method = "window/showMessage";
		ShowMessageNotification.messageDirection = messages_1.MessageDirection.serverToClient;
		ShowMessageNotification.type = new messages_1.ProtocolNotificationType(ShowMessageNotification.method);
	})(ShowMessageNotification || (exports.ShowMessageNotification = ShowMessageNotification = {}));
	/**
	* The show message request is sent from the server to the client to show a message
	* and a set of options actions to the user.
	*/
	var ShowMessageRequest;
	(function(ShowMessageRequest) {
		ShowMessageRequest.method = "window/showMessageRequest";
		ShowMessageRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		ShowMessageRequest.type = new messages_1.ProtocolRequestType(ShowMessageRequest.method);
	})(ShowMessageRequest || (exports.ShowMessageRequest = ShowMessageRequest = {}));
	/**
	* The log message notification is sent from the server to the client to ask
	* the client to log a particular message.
	*/
	var LogMessageNotification;
	(function(LogMessageNotification) {
		LogMessageNotification.method = "window/logMessage";
		LogMessageNotification.messageDirection = messages_1.MessageDirection.serverToClient;
		LogMessageNotification.type = new messages_1.ProtocolNotificationType(LogMessageNotification.method);
	})(LogMessageNotification || (exports.LogMessageNotification = LogMessageNotification = {}));
	/**
	* The telemetry event notification is sent from the server to the client to ask
	* the client to log telemetry data.
	*/
	var TelemetryEventNotification;
	(function(TelemetryEventNotification) {
		TelemetryEventNotification.method = "telemetry/event";
		TelemetryEventNotification.messageDirection = messages_1.MessageDirection.serverToClient;
		TelemetryEventNotification.type = new messages_1.ProtocolNotificationType(TelemetryEventNotification.method);
	})(TelemetryEventNotification || (exports.TelemetryEventNotification = TelemetryEventNotification = {}));
	/**
	* Defines how the host (editor) should sync
	* document changes to the language server.
	*/
	var TextDocumentSyncKind;
	(function(TextDocumentSyncKind) {
		/**
		* Documents should not be synced at all.
		*/
		TextDocumentSyncKind.None = 0;
		/**
		* Documents are synced by always sending the full content
		* of the document.
		*/
		TextDocumentSyncKind.Full = 1;
		/**
		* Documents are synced by sending the full content on open.
		* After that only incremental updates to the document are
		* send.
		*/
		TextDocumentSyncKind.Incremental = 2;
	})(TextDocumentSyncKind || (exports.TextDocumentSyncKind = TextDocumentSyncKind = {}));
	/**
	* The document open notification is sent from the client to the server to signal
	* newly opened text documents. The document's truth is now managed by the client
	* and the server must not try to read the document's truth using the document's
	* uri. Open in this sense means it is managed by the client. It doesn't necessarily
	* mean that its content is presented in an editor. An open notification must not
	* be sent more than once without a corresponding close notification send before.
	* This means open and close notification must be balanced and the max open count
	* is one.
	*/
	var DidOpenTextDocumentNotification;
	(function(DidOpenTextDocumentNotification) {
		DidOpenTextDocumentNotification.method = "textDocument/didOpen";
		DidOpenTextDocumentNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidOpenTextDocumentNotification.type = new messages_1.ProtocolNotificationType(DidOpenTextDocumentNotification.method);
	})(DidOpenTextDocumentNotification || (exports.DidOpenTextDocumentNotification = DidOpenTextDocumentNotification = {}));
	var TextDocumentContentChangeEvent;
	(function(TextDocumentContentChangeEvent) {
		/**
		* Checks whether the information describes a delta event.
		*/
		function isIncremental(event) {
			let candidate = event;
			return candidate !== void 0 && candidate !== null && typeof candidate.text === "string" && candidate.range !== void 0 && (candidate.rangeLength === void 0 || typeof candidate.rangeLength === "number");
		}
		TextDocumentContentChangeEvent.isIncremental = isIncremental;
		/**
		* Checks whether the information describes a full replacement event.
		*/
		function isFull(event) {
			let candidate = event;
			return candidate !== void 0 && candidate !== null && typeof candidate.text === "string" && candidate.range === void 0 && candidate.rangeLength === void 0;
		}
		TextDocumentContentChangeEvent.isFull = isFull;
	})(TextDocumentContentChangeEvent || (exports.TextDocumentContentChangeEvent = TextDocumentContentChangeEvent = {}));
	/**
	* The document change notification is sent from the client to the server to signal
	* changes to a text document.
	*/
	var DidChangeTextDocumentNotification;
	(function(DidChangeTextDocumentNotification) {
		DidChangeTextDocumentNotification.method = "textDocument/didChange";
		DidChangeTextDocumentNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidChangeTextDocumentNotification.type = new messages_1.ProtocolNotificationType(DidChangeTextDocumentNotification.method);
	})(DidChangeTextDocumentNotification || (exports.DidChangeTextDocumentNotification = DidChangeTextDocumentNotification = {}));
	/**
	* The document close notification is sent from the client to the server when
	* the document got closed in the client. The document's truth now exists where
	* the document's uri points to (e.g. if the document's uri is a file uri the
	* truth now exists on disk). As with the open notification the close notification
	* is about managing the document's content. Receiving a close notification
	* doesn't mean that the document was open in an editor before. A close
	* notification requires a previous open notification to be sent.
	*/
	var DidCloseTextDocumentNotification;
	(function(DidCloseTextDocumentNotification) {
		DidCloseTextDocumentNotification.method = "textDocument/didClose";
		DidCloseTextDocumentNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidCloseTextDocumentNotification.type = new messages_1.ProtocolNotificationType(DidCloseTextDocumentNotification.method);
	})(DidCloseTextDocumentNotification || (exports.DidCloseTextDocumentNotification = DidCloseTextDocumentNotification = {}));
	/**
	* The document save notification is sent from the client to the server when
	* the document got saved in the client.
	*/
	var DidSaveTextDocumentNotification;
	(function(DidSaveTextDocumentNotification) {
		DidSaveTextDocumentNotification.method = "textDocument/didSave";
		DidSaveTextDocumentNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidSaveTextDocumentNotification.type = new messages_1.ProtocolNotificationType(DidSaveTextDocumentNotification.method);
	})(DidSaveTextDocumentNotification || (exports.DidSaveTextDocumentNotification = DidSaveTextDocumentNotification = {}));
	/**
	* Represents reasons why a text document is saved.
	*/
	var TextDocumentSaveReason;
	(function(TextDocumentSaveReason) {
		/**
		* Manually triggered, e.g. by the user pressing save, by starting debugging,
		* or by an API call.
		*/
		TextDocumentSaveReason.Manual = 1;
		/**
		* Automatic after a delay.
		*/
		TextDocumentSaveReason.AfterDelay = 2;
		/**
		* When the editor lost focus.
		*/
		TextDocumentSaveReason.FocusOut = 3;
	})(TextDocumentSaveReason || (exports.TextDocumentSaveReason = TextDocumentSaveReason = {}));
	/**
	* A document will save notification is sent from the client to the server before
	* the document is actually saved.
	*/
	var WillSaveTextDocumentNotification;
	(function(WillSaveTextDocumentNotification) {
		WillSaveTextDocumentNotification.method = "textDocument/willSave";
		WillSaveTextDocumentNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		WillSaveTextDocumentNotification.type = new messages_1.ProtocolNotificationType(WillSaveTextDocumentNotification.method);
	})(WillSaveTextDocumentNotification || (exports.WillSaveTextDocumentNotification = WillSaveTextDocumentNotification = {}));
	/**
	* A document will save request is sent from the client to the server before
	* the document is actually saved. The request can return an array of TextEdits
	* which will be applied to the text document before it is saved. Please note that
	* clients might drop results if computing the text edits took too long or if a
	* server constantly fails on this request. This is done to keep the save fast and
	* reliable.
	*/
	var WillSaveTextDocumentWaitUntilRequest;
	(function(WillSaveTextDocumentWaitUntilRequest) {
		WillSaveTextDocumentWaitUntilRequest.method = "textDocument/willSaveWaitUntil";
		WillSaveTextDocumentWaitUntilRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		WillSaveTextDocumentWaitUntilRequest.type = new messages_1.ProtocolRequestType(WillSaveTextDocumentWaitUntilRequest.method);
	})(WillSaveTextDocumentWaitUntilRequest || (exports.WillSaveTextDocumentWaitUntilRequest = WillSaveTextDocumentWaitUntilRequest = {}));
	/**
	* The watched files notification is sent from the client to the server when
	* the client detects changes to file watched by the language client.
	*/
	var DidChangeWatchedFilesNotification;
	(function(DidChangeWatchedFilesNotification) {
		DidChangeWatchedFilesNotification.method = "workspace/didChangeWatchedFiles";
		DidChangeWatchedFilesNotification.messageDirection = messages_1.MessageDirection.clientToServer;
		DidChangeWatchedFilesNotification.type = new messages_1.ProtocolNotificationType(DidChangeWatchedFilesNotification.method);
	})(DidChangeWatchedFilesNotification || (exports.DidChangeWatchedFilesNotification = DidChangeWatchedFilesNotification = {}));
	/**
	* The file event type
	*/
	var FileChangeType;
	(function(FileChangeType) {
		/**
		* The file got created.
		*/
		FileChangeType.Created = 1;
		/**
		* The file got changed.
		*/
		FileChangeType.Changed = 2;
		/**
		* The file got deleted.
		*/
		FileChangeType.Deleted = 3;
	})(FileChangeType || (exports.FileChangeType = FileChangeType = {}));
	var RelativePattern;
	(function(RelativePattern) {
		function is(value) {
			const candidate = value;
			return Is.objectLiteral(candidate) && (vscode_languageserver_types_1.URI.is(candidate.baseUri) || vscode_languageserver_types_1.WorkspaceFolder.is(candidate.baseUri)) && Is.string(candidate.pattern);
		}
		RelativePattern.is = is;
	})(RelativePattern || (exports.RelativePattern = RelativePattern = {}));
	var WatchKind;
	(function(WatchKind) {
		/**
		* Interested in create events.
		*/
		WatchKind.Create = 1;
		/**
		* Interested in change events
		*/
		WatchKind.Change = 2;
		/**
		* Interested in delete events
		*/
		WatchKind.Delete = 4;
	})(WatchKind || (exports.WatchKind = WatchKind = {}));
	/**
	* Diagnostics notification are sent from the server to the client to signal
	* results of validation runs.
	*/
	var PublishDiagnosticsNotification;
	(function(PublishDiagnosticsNotification) {
		PublishDiagnosticsNotification.method = "textDocument/publishDiagnostics";
		PublishDiagnosticsNotification.messageDirection = messages_1.MessageDirection.serverToClient;
		PublishDiagnosticsNotification.type = new messages_1.ProtocolNotificationType(PublishDiagnosticsNotification.method);
	})(PublishDiagnosticsNotification || (exports.PublishDiagnosticsNotification = PublishDiagnosticsNotification = {}));
	/**
	* How a completion was triggered
	*/
	var CompletionTriggerKind;
	(function(CompletionTriggerKind) {
		/**
		* Completion was triggered by typing an identifier (24x7 code
		* complete), manual invocation (e.g Ctrl+Space) or via API.
		*/
		CompletionTriggerKind.Invoked = 1;
		/**
		* Completion was triggered by a trigger character specified by
		* the `triggerCharacters` properties of the `CompletionRegistrationOptions`.
		*/
		CompletionTriggerKind.TriggerCharacter = 2;
		/**
		* Completion was re-triggered as current completion list is incomplete
		*/
		CompletionTriggerKind.TriggerForIncompleteCompletions = 3;
	})(CompletionTriggerKind || (exports.CompletionTriggerKind = CompletionTriggerKind = {}));
	/**
	* Request to request completion at a given text document position. The request's
	* parameter is of type {@link TextDocumentPosition} the response
	* is of type {@link CompletionItem CompletionItem[]} or {@link CompletionList}
	* or a Thenable that resolves to such.
	*
	* The request can delay the computation of the {@link CompletionItem.detail `detail`}
	* and {@link CompletionItem.documentation `documentation`} properties to the `completionItem/resolve`
	* request. However, properties that are needed for the initial sorting and filtering, like `sortText`,
	* `filterText`, `insertText`, and `textEdit`, must not be changed during resolve.
	*/
	var CompletionRequest;
	(function(CompletionRequest) {
		CompletionRequest.method = "textDocument/completion";
		CompletionRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		CompletionRequest.type = new messages_1.ProtocolRequestType(CompletionRequest.method);
	})(CompletionRequest || (exports.CompletionRequest = CompletionRequest = {}));
	/**
	* Request to resolve additional information for a given completion item.The request's
	* parameter is of type {@link CompletionItem} the response
	* is of type {@link CompletionItem} or a Thenable that resolves to such.
	*/
	var CompletionResolveRequest;
	(function(CompletionResolveRequest) {
		CompletionResolveRequest.method = "completionItem/resolve";
		CompletionResolveRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		CompletionResolveRequest.type = new messages_1.ProtocolRequestType(CompletionResolveRequest.method);
	})(CompletionResolveRequest || (exports.CompletionResolveRequest = CompletionResolveRequest = {}));
	/**
	* Request to request hover information at a given text document position. The request's
	* parameter is of type {@link TextDocumentPosition} the response is of
	* type {@link Hover} or a Thenable that resolves to such.
	*/
	var HoverRequest;
	(function(HoverRequest) {
		HoverRequest.method = "textDocument/hover";
		HoverRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		HoverRequest.type = new messages_1.ProtocolRequestType(HoverRequest.method);
	})(HoverRequest || (exports.HoverRequest = HoverRequest = {}));
	/**
	* How a signature help was triggered.
	*
	* @since 3.15.0
	*/
	var SignatureHelpTriggerKind;
	(function(SignatureHelpTriggerKind) {
		/**
		* Signature help was invoked manually by the user or by a command.
		*/
		SignatureHelpTriggerKind.Invoked = 1;
		/**
		* Signature help was triggered by a trigger character.
		*/
		SignatureHelpTriggerKind.TriggerCharacter = 2;
		/**
		* Signature help was triggered by the cursor moving or by the document content changing.
		*/
		SignatureHelpTriggerKind.ContentChange = 3;
	})(SignatureHelpTriggerKind || (exports.SignatureHelpTriggerKind = SignatureHelpTriggerKind = {}));
	var SignatureHelpRequest;
	(function(SignatureHelpRequest) {
		SignatureHelpRequest.method = "textDocument/signatureHelp";
		SignatureHelpRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		SignatureHelpRequest.type = new messages_1.ProtocolRequestType(SignatureHelpRequest.method);
	})(SignatureHelpRequest || (exports.SignatureHelpRequest = SignatureHelpRequest = {}));
	/**
	* A request to resolve the definition location of a symbol at a given text
	* document position. The request's parameter is of type {@link TextDocumentPosition}
	* the response is of either type {@link Definition} or a typed array of
	* {@link DefinitionLink} or a Thenable that resolves to such.
	*/
	var DefinitionRequest;
	(function(DefinitionRequest) {
		DefinitionRequest.method = "textDocument/definition";
		DefinitionRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DefinitionRequest.type = new messages_1.ProtocolRequestType(DefinitionRequest.method);
	})(DefinitionRequest || (exports.DefinitionRequest = DefinitionRequest = {}));
	/**
	* A request to resolve project-wide references for the symbol denoted
	* by the given text document position. The request's parameter is of
	* type {@link ReferenceParams} the response is of type
	* {@link Location Location[]} or a Thenable that resolves to such.
	*/
	var ReferencesRequest;
	(function(ReferencesRequest) {
		ReferencesRequest.method = "textDocument/references";
		ReferencesRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		ReferencesRequest.type = new messages_1.ProtocolRequestType(ReferencesRequest.method);
	})(ReferencesRequest || (exports.ReferencesRequest = ReferencesRequest = {}));
	/**
	* Request to resolve a {@link DocumentHighlight} for a given
	* text document position. The request's parameter is of type {@link TextDocumentPosition}
	* the request response is an array of type {@link DocumentHighlight}
	* or a Thenable that resolves to such.
	*/
	var DocumentHighlightRequest;
	(function(DocumentHighlightRequest) {
		DocumentHighlightRequest.method = "textDocument/documentHighlight";
		DocumentHighlightRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DocumentHighlightRequest.type = new messages_1.ProtocolRequestType(DocumentHighlightRequest.method);
	})(DocumentHighlightRequest || (exports.DocumentHighlightRequest = DocumentHighlightRequest = {}));
	/**
	* A request to list all symbols found in a given text document. The request's
	* parameter is of type {@link TextDocumentIdentifier} the
	* response is of type {@link SymbolInformation SymbolInformation[]} or a Thenable
	* that resolves to such.
	*/
	var DocumentSymbolRequest;
	(function(DocumentSymbolRequest) {
		DocumentSymbolRequest.method = "textDocument/documentSymbol";
		DocumentSymbolRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DocumentSymbolRequest.type = new messages_1.ProtocolRequestType(DocumentSymbolRequest.method);
	})(DocumentSymbolRequest || (exports.DocumentSymbolRequest = DocumentSymbolRequest = {}));
	/**
	* A request to provide commands for the given text document and range.
	*/
	var CodeActionRequest;
	(function(CodeActionRequest) {
		CodeActionRequest.method = "textDocument/codeAction";
		CodeActionRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		CodeActionRequest.type = new messages_1.ProtocolRequestType(CodeActionRequest.method);
	})(CodeActionRequest || (exports.CodeActionRequest = CodeActionRequest = {}));
	/**
	* Request to resolve additional information for a given code action.The request's
	* parameter is of type {@link CodeAction} the response
	* is of type {@link CodeAction} or a Thenable that resolves to such.
	*/
	var CodeActionResolveRequest;
	(function(CodeActionResolveRequest) {
		CodeActionResolveRequest.method = "codeAction/resolve";
		CodeActionResolveRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		CodeActionResolveRequest.type = new messages_1.ProtocolRequestType(CodeActionResolveRequest.method);
	})(CodeActionResolveRequest || (exports.CodeActionResolveRequest = CodeActionResolveRequest = {}));
	/**
	* A request to list project-wide symbols matching the query string given
	* by the {@link WorkspaceSymbolParams}. The response is
	* of type {@link SymbolInformation SymbolInformation[]} or a Thenable that
	* resolves to such.
	*
	* @since 3.17.0 - support for WorkspaceSymbol in the returned data. Clients
	*  need to advertise support for WorkspaceSymbols via the client capability
	*  `workspace.symbol.resolveSupport`.
	*
	*/
	var WorkspaceSymbolRequest;
	(function(WorkspaceSymbolRequest) {
		WorkspaceSymbolRequest.method = "workspace/symbol";
		WorkspaceSymbolRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		WorkspaceSymbolRequest.type = new messages_1.ProtocolRequestType(WorkspaceSymbolRequest.method);
	})(WorkspaceSymbolRequest || (exports.WorkspaceSymbolRequest = WorkspaceSymbolRequest = {}));
	/**
	* A request to resolve the range inside the workspace
	* symbol's location.
	*
	* @since 3.17.0
	*/
	var WorkspaceSymbolResolveRequest;
	(function(WorkspaceSymbolResolveRequest) {
		WorkspaceSymbolResolveRequest.method = "workspaceSymbol/resolve";
		WorkspaceSymbolResolveRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		WorkspaceSymbolResolveRequest.type = new messages_1.ProtocolRequestType(WorkspaceSymbolResolveRequest.method);
	})(WorkspaceSymbolResolveRequest || (exports.WorkspaceSymbolResolveRequest = WorkspaceSymbolResolveRequest = {}));
	/**
	* A request to provide code lens for the given text document.
	*/
	var CodeLensRequest;
	(function(CodeLensRequest) {
		CodeLensRequest.method = "textDocument/codeLens";
		CodeLensRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		CodeLensRequest.type = new messages_1.ProtocolRequestType(CodeLensRequest.method);
	})(CodeLensRequest || (exports.CodeLensRequest = CodeLensRequest = {}));
	/**
	* A request to resolve a command for a given code lens.
	*/
	var CodeLensResolveRequest;
	(function(CodeLensResolveRequest) {
		CodeLensResolveRequest.method = "codeLens/resolve";
		CodeLensResolveRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		CodeLensResolveRequest.type = new messages_1.ProtocolRequestType(CodeLensResolveRequest.method);
	})(CodeLensResolveRequest || (exports.CodeLensResolveRequest = CodeLensResolveRequest = {}));
	/**
	* A request to refresh all code actions
	*
	* @since 3.16.0
	*/
	var CodeLensRefreshRequest;
	(function(CodeLensRefreshRequest) {
		CodeLensRefreshRequest.method = `workspace/codeLens/refresh`;
		CodeLensRefreshRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		CodeLensRefreshRequest.type = new messages_1.ProtocolRequestType0(CodeLensRefreshRequest.method);
	})(CodeLensRefreshRequest || (exports.CodeLensRefreshRequest = CodeLensRefreshRequest = {}));
	/**
	* A request to provide document links
	*/
	var DocumentLinkRequest;
	(function(DocumentLinkRequest) {
		DocumentLinkRequest.method = "textDocument/documentLink";
		DocumentLinkRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DocumentLinkRequest.type = new messages_1.ProtocolRequestType(DocumentLinkRequest.method);
	})(DocumentLinkRequest || (exports.DocumentLinkRequest = DocumentLinkRequest = {}));
	/**
	* Request to resolve additional information for a given document link. The request's
	* parameter is of type {@link DocumentLink} the response
	* is of type {@link DocumentLink} or a Thenable that resolves to such.
	*/
	var DocumentLinkResolveRequest;
	(function(DocumentLinkResolveRequest) {
		DocumentLinkResolveRequest.method = "documentLink/resolve";
		DocumentLinkResolveRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DocumentLinkResolveRequest.type = new messages_1.ProtocolRequestType(DocumentLinkResolveRequest.method);
	})(DocumentLinkResolveRequest || (exports.DocumentLinkResolveRequest = DocumentLinkResolveRequest = {}));
	/**
	* A request to format a whole document.
	*/
	var DocumentFormattingRequest;
	(function(DocumentFormattingRequest) {
		DocumentFormattingRequest.method = "textDocument/formatting";
		DocumentFormattingRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DocumentFormattingRequest.type = new messages_1.ProtocolRequestType(DocumentFormattingRequest.method);
	})(DocumentFormattingRequest || (exports.DocumentFormattingRequest = DocumentFormattingRequest = {}));
	/**
	* A request to format a range in a document.
	*/
	var DocumentRangeFormattingRequest;
	(function(DocumentRangeFormattingRequest) {
		DocumentRangeFormattingRequest.method = "textDocument/rangeFormatting";
		DocumentRangeFormattingRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DocumentRangeFormattingRequest.type = new messages_1.ProtocolRequestType(DocumentRangeFormattingRequest.method);
	})(DocumentRangeFormattingRequest || (exports.DocumentRangeFormattingRequest = DocumentRangeFormattingRequest = {}));
	/**
	* A request to format ranges in a document.
	*
	* @since 3.18.0
	* @proposed
	*/
	var DocumentRangesFormattingRequest;
	(function(DocumentRangesFormattingRequest) {
		DocumentRangesFormattingRequest.method = "textDocument/rangesFormatting";
		DocumentRangesFormattingRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DocumentRangesFormattingRequest.type = new messages_1.ProtocolRequestType(DocumentRangesFormattingRequest.method);
	})(DocumentRangesFormattingRequest || (exports.DocumentRangesFormattingRequest = DocumentRangesFormattingRequest = {}));
	/**
	* A request to format a document on type.
	*/
	var DocumentOnTypeFormattingRequest;
	(function(DocumentOnTypeFormattingRequest) {
		DocumentOnTypeFormattingRequest.method = "textDocument/onTypeFormatting";
		DocumentOnTypeFormattingRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		DocumentOnTypeFormattingRequest.type = new messages_1.ProtocolRequestType(DocumentOnTypeFormattingRequest.method);
	})(DocumentOnTypeFormattingRequest || (exports.DocumentOnTypeFormattingRequest = DocumentOnTypeFormattingRequest = {}));
	var PrepareSupportDefaultBehavior;
	(function(PrepareSupportDefaultBehavior) {
		/**
		* The client's default behavior is to select the identifier
		* according the to language's syntax rule.
		*/
		PrepareSupportDefaultBehavior.Identifier = 1;
	})(PrepareSupportDefaultBehavior || (exports.PrepareSupportDefaultBehavior = PrepareSupportDefaultBehavior = {}));
	/**
	* A request to rename a symbol.
	*/
	var RenameRequest;
	(function(RenameRequest) {
		RenameRequest.method = "textDocument/rename";
		RenameRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		RenameRequest.type = new messages_1.ProtocolRequestType(RenameRequest.method);
	})(RenameRequest || (exports.RenameRequest = RenameRequest = {}));
	/**
	* A request to test and perform the setup necessary for a rename.
	*
	* @since 3.16 - support for default behavior
	*/
	var PrepareRenameRequest;
	(function(PrepareRenameRequest) {
		PrepareRenameRequest.method = "textDocument/prepareRename";
		PrepareRenameRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		PrepareRenameRequest.type = new messages_1.ProtocolRequestType(PrepareRenameRequest.method);
	})(PrepareRenameRequest || (exports.PrepareRenameRequest = PrepareRenameRequest = {}));
	/**
	* A request send from the client to the server to execute a command. The request might return
	* a workspace edit which the client will apply to the workspace.
	*/
	var ExecuteCommandRequest;
	(function(ExecuteCommandRequest) {
		ExecuteCommandRequest.method = "workspace/executeCommand";
		ExecuteCommandRequest.messageDirection = messages_1.MessageDirection.clientToServer;
		ExecuteCommandRequest.type = new messages_1.ProtocolRequestType(ExecuteCommandRequest.method);
	})(ExecuteCommandRequest || (exports.ExecuteCommandRequest = ExecuteCommandRequest = {}));
	/**
	* A request sent from the server to the client to modified certain resources.
	*/
	var ApplyWorkspaceEditRequest;
	(function(ApplyWorkspaceEditRequest) {
		ApplyWorkspaceEditRequest.method = "workspace/applyEdit";
		ApplyWorkspaceEditRequest.messageDirection = messages_1.MessageDirection.serverToClient;
		ApplyWorkspaceEditRequest.type = new messages_1.ProtocolRequestType("workspace/applyEdit");
	})(ApplyWorkspaceEditRequest || (exports.ApplyWorkspaceEditRequest = ApplyWorkspaceEditRequest = {}));
}));
var require_connection = /* @__PURE__ */ __commonJSMin(((exports) => {
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.createProtocolConnection = void 0;
	var vscode_jsonrpc_1 = require_main$1();
	function createProtocolConnection(input, output, logger, options) {
		if (vscode_jsonrpc_1.ConnectionStrategy.is(options)) options = { connectionStrategy: options };
		return (0, vscode_jsonrpc_1.createMessageConnection)(input, output, logger, options);
	}
	exports.createProtocolConnection = createProtocolConnection;
}));
var require_api = /* @__PURE__ */ __commonJSMin(((exports) => {
	var __createBinding = exports && exports.__createBinding || (Object.create ? (function(o, m, k, k2) {
		if (k2 === void 0) k2 = k;
		var desc = Object.getOwnPropertyDescriptor(m, k);
		if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) desc = {
			enumerable: true,
			get: function() {
				return m[k];
			}
		};
		Object.defineProperty(o, k2, desc);
	}) : (function(o, m, k, k2) {
		if (k2 === void 0) k2 = k;
		o[k2] = m[k];
	}));
	var __exportStar = exports && exports.__exportStar || function(m, exports$2) {
		for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports$2, p)) __createBinding(exports$2, m, p);
	};
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.LSPErrorCodes = exports.createProtocolConnection = void 0;
	__exportStar(require_main$1(), exports);
	__exportStar((init_main(), __toCommonJS(main_exports)), exports);
	__exportStar(require_messages(), exports);
	__exportStar(require_protocol(), exports);
	var connection_1 = require_connection();
	Object.defineProperty(exports, "createProtocolConnection", {
		enumerable: true,
		get: function() {
			return connection_1.createProtocolConnection;
		}
	});
	var LSPErrorCodes;
	(function(LSPErrorCodes) {
		/**
		* This is the start range of LSP reserved error codes.
		* It doesn't denote a real error code.
		*
		* @since 3.16.0
		*/
		LSPErrorCodes.lspReservedErrorRangeStart = -32899;
		/**
		* A request failed but it was syntactically correct, e.g the
		* method name was known and the parameters were valid. The error
		* message should contain human readable information about why
		* the request failed.
		*
		* @since 3.17.0
		*/
		LSPErrorCodes.RequestFailed = -32803;
		/**
		* The server cancelled the request. This error code should
		* only be used for requests that explicitly support being
		* server cancellable.
		*
		* @since 3.17.0
		*/
		LSPErrorCodes.ServerCancelled = -32802;
		/**
		* The server detected that the content of a document got
		* modified outside normal conditions. A server should
		* NOT send this error code if it detects a content change
		* in it unprocessed messages. The result even computed
		* on an older state might still be useful for the client.
		*
		* If a client decides that a result is not of any use anymore
		* the client should cancel the request.
		*/
		LSPErrorCodes.ContentModified = -32801;
		/**
		* The client has canceled a request and a server as detected
		* the cancel.
		*/
		LSPErrorCodes.RequestCancelled = -32800;
		/**
		* This is the end range of LSP reserved error codes.
		* It doesn't denote a real error code.
		*
		* @since 3.16.0
		*/
		LSPErrorCodes.lspReservedErrorRangeEnd = -32800;
	})(LSPErrorCodes || (exports.LSPErrorCodes = LSPErrorCodes = {}));
}));
var import_main = (/* @__PURE__ */ __commonJSMin(((exports) => {
	var __createBinding = exports && exports.__createBinding || (Object.create ? (function(o, m, k, k2) {
		if (k2 === void 0) k2 = k;
		var desc = Object.getOwnPropertyDescriptor(m, k);
		if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) desc = {
			enumerable: true,
			get: function() {
				return m[k];
			}
		};
		Object.defineProperty(o, k2, desc);
	}) : (function(o, m, k, k2) {
		if (k2 === void 0) k2 = k;
		o[k2] = m[k];
	}));
	var __exportStar = exports && exports.__exportStar || function(m, exports$1) {
		for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports$1, p)) __createBinding(exports$1, m, p);
	};
	Object.defineProperty(exports, "__esModule", { value: true });
	exports.createProtocolConnection = void 0;
	var browser_1 = require_browser();
	__exportStar(require_browser(), exports);
	__exportStar(require_api(), exports);
	function createProtocolConnection(reader, writer, logger, options) {
		return (0, browser_1.createMessageConnection)(reader, writer, logger, options);
	}
	exports.createProtocolConnection = createProtocolConnection;
})))();
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var Disposable;
(function(Disposable) {
	function create(callback) {
		return { dispose: async () => await callback() };
	}
	Disposable.create = create;
})(Disposable || (Disposable = {}));
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultDocumentBuilder = class {
	constructor(services) {
		this.updateBuildOptions = { validation: { categories: ["built-in", "fast"] } };
		this.updateListeners = [];
		this.buildPhaseListeners = new MultiMap();
		this.documentPhaseListeners = new MultiMap();
		this.buildState = /* @__PURE__ */ new Map();
		this.documentBuildWaiters = /* @__PURE__ */ new Map();
		this.currentState = DocumentState.Changed;
		this.langiumDocuments = services.workspace.LangiumDocuments;
		this.langiumDocumentFactory = services.workspace.LangiumDocumentFactory;
		this.textDocuments = services.workspace.TextDocuments;
		this.indexManager = services.workspace.IndexManager;
		this.fileSystemProvider = services.workspace.FileSystemProvider;
		this.workspaceManager = () => services.workspace.WorkspaceManager;
		this.serviceRegistry = services.ServiceRegistry;
	}
	async build(documents, options = {}, cancelToken = cancellation_exports.CancellationToken.None) {
		for (const document of documents) {
			const key = document.uri.toString();
			if (document.state === DocumentState.Validated) {
				if (typeof options.validation === "boolean" && options.validation) this.resetToState(document, DocumentState.IndexedReferences);
				else if (typeof options.validation === "object") {
					const categories = this.findMissingValidationCategories(document, options);
					if (categories.length > 0) {
						this.buildState.set(key, {
							completed: false,
							options: { validation: { categories } },
							result: this.buildState.get(key)?.result
						});
						document.state = DocumentState.IndexedReferences;
					}
				}
			} else this.buildState.delete(key);
		}
		this.currentState = DocumentState.Changed;
		await this.emitUpdate(documents.map((e) => e.uri), []);
		await this.buildDocuments(documents, options, cancelToken);
	}
	async update(changed, deleted, cancelToken = cancellation_exports.CancellationToken.None) {
		this.currentState = DocumentState.Changed;
		const deletedUris = [];
		for (const deletedUri of deleted) {
			const deletedDocs = this.langiumDocuments.deleteDocuments(deletedUri);
			for (const doc of deletedDocs) {
				deletedUris.push(doc.uri);
				this.cleanUpDeleted(doc);
			}
		}
		const changedUris = (await Promise.all(changed.map((uri) => this.findChangedUris(uri)))).flat();
		for (const changedUri of changedUris) {
			let changedDocument = this.langiumDocuments.getDocument(changedUri);
			if (changedDocument === void 0) {
				changedDocument = this.langiumDocumentFactory.fromModel({ $type: "INVALID" }, changedUri);
				changedDocument.state = DocumentState.Changed;
				this.langiumDocuments.addDocument(changedDocument);
			}
			this.resetToState(changedDocument, DocumentState.Changed);
		}
		const allChangedUris = stream(changedUris).concat(deletedUris).map((uri) => uri.toString()).toSet();
		this.langiumDocuments.all.filter((doc) => !allChangedUris.has(doc.uri.toString()) && this.shouldRelink(doc, allChangedUris)).forEach((doc) => this.resetToState(doc, DocumentState.ComputedScopes));
		await this.emitUpdate(changedUris, deletedUris);
		await interruptAndCheck(cancelToken);
		const rebuildDocuments = this.sortDocuments(this.langiumDocuments.all.filter((doc) => doc.state < DocumentState.Validated || !this.buildState.get(doc.uri.toString())?.completed || this.resultsAreIncomplete(doc, this.updateBuildOptions)).toArray());
		await this.buildDocuments(rebuildDocuments, this.updateBuildOptions, cancelToken);
	}
	resultsAreIncomplete(document, options) {
		return this.findMissingValidationCategories(document, options).length >= 1;
	}
	findMissingValidationCategories(document, options) {
		const state = this.buildState.get(document.uri.toString());
		const allCategories = this.serviceRegistry.getServices(document.uri).validation.ValidationRegistry.getAllValidationCategories(document);
		const executedCategories = state?.result?.validationChecks ? new Set(state?.result?.validationChecks) : state?.completed ? allCategories : /* @__PURE__ */ new Set();
		return stream(options === void 0 || options.validation === true ? allCategories : typeof options.validation === "object" ? options.validation.categories ?? allCategories : []).filter((requested) => !executedCategories.has(requested)).toArray();
	}
	async findChangedUris(changed) {
		if (this.langiumDocuments.getDocument(changed) ?? this.textDocuments?.get(changed)) return [changed];
		try {
			const stat = await this.fileSystemProvider.stat(changed);
			if (stat.isDirectory) return await this.workspaceManager().searchFolder(changed);
			else if (this.workspaceManager().shouldIncludeEntry(stat)) return [changed];
		} catch {}
		return [];
	}
	async emitUpdate(changed, deleted) {
		await Promise.all(this.updateListeners.map((listener) => listener(changed, deleted)));
	}
	/**
	* Sort the given documents by priority. By default, documents with an open text document are prioritized.
	* This is useful to ensure that visible documents show their diagnostics before all other documents.
	*
	* This improves the responsiveness in large workspaces as users usually don't care about diagnostics
	* in files that are currently not opened in the editor.
	*/
	sortDocuments(documents) {
		let left = 0;
		let right = documents.length - 1;
		while (left < right) {
			while (left < documents.length && this.hasTextDocument(documents[left])) left++;
			while (right >= 0 && !this.hasTextDocument(documents[right])) right--;
			if (left < right) [documents[left], documents[right]] = [documents[right], documents[left]];
		}
		return documents;
	}
	hasTextDocument(doc) {
		return Boolean(this.textDocuments?.get(doc.uri));
	}
	/**
	* Check whether the given document should be relinked after changes were found in the given URIs.
	*/
	shouldRelink(document, changedUris) {
		if (document.references.some((ref) => ref.error !== void 0)) return true;
		return this.indexManager.isAffected(document, changedUris);
	}
	onUpdate(callback) {
		this.updateListeners.push(callback);
		return Disposable.create(() => {
			const index = this.updateListeners.indexOf(callback);
			if (index >= 0) this.updateListeners.splice(index, 1);
		});
	}
	resetToState(document, state) {
		switch (state) {
			case DocumentState.Changed:
			case DocumentState.Parsed: this.indexManager.removeContent(document.uri);
			case DocumentState.IndexedContent: document.localSymbols = void 0;
			case DocumentState.ComputedScopes: this.serviceRegistry.getServices(document.uri).references.Linker.unlink(document);
			case DocumentState.Linked: this.indexManager.removeReferences(document.uri);
			case DocumentState.IndexedReferences:
				document.diagnostics = void 0;
				this.buildState.delete(document.uri.toString());
			case DocumentState.Validated:
		}
		if (document.state > state) document.state = state;
	}
	cleanUpDeleted(document) {
		this.buildState.delete(document.uri.toString());
		this.indexManager.remove(document.uri);
		document.state = DocumentState.Changed;
	}
	/**
	* Build the given documents by stepping through all build phases. If a document's state indicates
	* that a certain build phase is already done, the phase is skipped for that document.
	*
	* @param documents The documents to build.
	* @param options the {@link BuildOptions} to use.
	* @param cancelToken A cancellation token that can be used to cancel the build.
	* @returns A promise that resolves when the build is done.
	*/
	async buildDocuments(documents, options, cancelToken) {
		this.prepareBuild(documents, options);
		await this.runCancelable(documents, DocumentState.Parsed, cancelToken, (doc) => this.langiumDocumentFactory.update(doc, cancelToken));
		await this.runCancelable(documents, DocumentState.IndexedContent, cancelToken, (doc) => this.indexManager.updateContent(doc, cancelToken));
		await this.runCancelable(documents, DocumentState.ComputedScopes, cancelToken, async (doc) => {
			doc.localSymbols = await this.serviceRegistry.getServices(doc.uri).references.ScopeComputation.collectLocalSymbols(doc, cancelToken);
		});
		const toBeLinked = documents.filter((doc) => this.shouldLink(doc));
		await this.runCancelable(toBeLinked, DocumentState.Linked, cancelToken, (doc) => {
			return this.serviceRegistry.getServices(doc.uri).references.Linker.link(doc, cancelToken);
		});
		await this.runCancelable(toBeLinked, DocumentState.IndexedReferences, cancelToken, (doc) => this.indexManager.updateReferences(doc, cancelToken));
		const toBeValidated = documents.filter((doc) => {
			if (this.shouldValidate(doc)) return true;
			else {
				this.markAsCompleted(doc);
				return false;
			}
		});
		await this.runCancelable(toBeValidated, DocumentState.Validated, cancelToken, async (doc) => {
			await this.validate(doc, cancelToken);
			this.markAsCompleted(doc);
		});
	}
	markAsCompleted(document) {
		const state = this.buildState.get(document.uri.toString());
		if (state) state.completed = true;
	}
	/**
	* Runs prior to beginning the build process to update the {@link DocumentBuildState} for each document
	*
	* @param documents collection of documents to be built
	* @param options the {@link BuildOptions} to use
	*/
	prepareBuild(documents, options) {
		for (const doc of documents) {
			const key = doc.uri.toString();
			const state = this.buildState.get(key);
			if (!state || state.completed) this.buildState.set(key, {
				completed: false,
				options,
				result: state?.result
			});
		}
	}
	/**
	* Runs a cancelable operation on a set of documents to bring them to a specified {@link DocumentState}.
	*
	* @param documents The array of documents to process.
	* @param targetState The target {@link DocumentState} to bring the documents to.
	* @param cancelToken A token that can be used to cancel the operation.
	* @param callback A function to be called for each document.
	* @returns A promise that resolves when all documents have been processed or the operation is canceled.
	* @throws Will throw `OperationCancelled` if the operation is canceled via a `CancellationToken`.
	*/
	async runCancelable(documents, targetState, cancelToken, callback) {
		for (const document of documents) if (document.state < targetState) {
			await interruptAndCheck(cancelToken);
			await callback(document);
			document.state = targetState;
			await this.notifyDocumentPhase(document, targetState, cancelToken);
		}
		const targetStateDocs = documents.filter((doc) => doc.state === targetState);
		await this.notifyBuildPhase(targetStateDocs, targetState, cancelToken);
		this.currentState = targetState;
	}
	onBuildPhase(targetState, callback) {
		this.buildPhaseListeners.add(targetState, callback);
		return Disposable.create(() => {
			this.buildPhaseListeners.delete(targetState, callback);
		});
	}
	onDocumentPhase(targetState, callback) {
		this.documentPhaseListeners.add(targetState, callback);
		return Disposable.create(() => {
			this.documentPhaseListeners.delete(targetState, callback);
		});
	}
	waitUntil(state, uriOrToken, cancelToken) {
		let uri = void 0;
		if (uriOrToken && "path" in uriOrToken) uri = uriOrToken;
		else cancelToken = uriOrToken;
		cancelToken ?? (cancelToken = cancellation_exports.CancellationToken.None);
		if (uri) return this.awaitDocumentState(state, uri, cancelToken);
		else return this.awaitBuilderState(state, cancelToken);
	}
	awaitDocumentState(state, uri, cancelToken) {
		const document = this.langiumDocuments.getDocument(uri);
		if (!document) return Promise.reject(new import_main.ResponseError(import_main.LSPErrorCodes.ServerCancelled, `No document found for URI: ${uri.toString()}`));
		else if (document.state >= state) return Promise.resolve(uri);
		else if (cancelToken.isCancellationRequested) return Promise.reject(OperationCancelled);
		else if (this.currentState >= state && state > document.state) return Promise.reject(new import_main.ResponseError(import_main.LSPErrorCodes.RequestFailed, `Document state of ${uri.toString()} is ${DocumentState[document.state]}, requiring ${DocumentState[state]}, but workspace state is already ${DocumentState[this.currentState]}. Returning undefined.`));
		return new Promise((resolve, reject) => {
			const buildDisposable = this.onDocumentPhase(state, (doc) => {
				if (UriUtils.equals(doc.uri, uri)) {
					buildDisposable.dispose();
					cancelDisposable.dispose();
					resolve(doc.uri);
				}
			});
			const cancelDisposable = cancelToken.onCancellationRequested(() => {
				buildDisposable.dispose();
				cancelDisposable.dispose();
				reject(OperationCancelled);
			});
		});
	}
	awaitBuilderState(state, cancelToken) {
		if (this.currentState >= state) return Promise.resolve();
		else if (cancelToken.isCancellationRequested) return Promise.reject(OperationCancelled);
		return new Promise((resolve, reject) => {
			const buildDisposable = this.onBuildPhase(state, () => {
				buildDisposable.dispose();
				cancelDisposable.dispose();
				resolve();
			});
			const cancelDisposable = cancelToken.onCancellationRequested(() => {
				buildDisposable.dispose();
				cancelDisposable.dispose();
				reject(OperationCancelled);
			});
		});
	}
	async notifyDocumentPhase(document, state, cancelToken) {
		const listenersCopy = this.documentPhaseListeners.get(state).slice();
		for (const listener of listenersCopy) try {
			await interruptAndCheck(cancelToken);
			await listener(document, cancelToken);
		} catch (err) {
			if (!isOperationCancelled(err)) throw err;
		}
	}
	async notifyBuildPhase(documents, state, cancelToken) {
		if (documents.length === 0) return;
		const listenersCopy = this.buildPhaseListeners.get(state).slice();
		for (const listener of listenersCopy) {
			await interruptAndCheck(cancelToken);
			await listener(documents, cancelToken);
		}
	}
	/**
	* Determine whether the given document should be linked during a build. The default
	* implementation checks the `eagerLinking` property of the build options. If it's set to `true`
	* or `undefined`, the document is included in the linking phase. This also affects the
	* references indexing phase, which depends on eager linking.
	*/
	shouldLink(document) {
		return this.getBuildOptions(document).eagerLinking ?? true;
	}
	/**
	* Determine whether the given document should be validated during a build. The default
	* implementation checks the `validation` property of the build options. If it's set to `true`
	* or a `ValidationOptions` object, the document is included in the validation phase.
	*/
	shouldValidate(document) {
		return Boolean(this.getBuildOptions(document).validation);
	}
	/**
	* Run validation checks on the given document and store the resulting diagnostics in the document.
	* If the document already contains diagnostics, the new ones are added to the list.
	*/
	async validate(document, cancelToken) {
		const validator = this.serviceRegistry.getServices(document.uri).validation.DocumentValidator;
		const options = this.getBuildOptions(document);
		const validationOptions = typeof options.validation === "object" ? { ...options.validation } : {};
		validationOptions.categories = this.findMissingValidationCategories(document, options);
		const diagnostics = await validator.validateDocument(document, validationOptions, cancelToken);
		if (document.diagnostics) document.diagnostics.push(...diagnostics);
		else document.diagnostics = diagnostics;
		const state = this.buildState.get(document.uri.toString());
		if (state) {
			state.result ?? (state.result = {});
			if (state.result.validationChecks) state.result.validationChecks = stream(state.result.validationChecks).concat(validationOptions.categories).distinct().toArray();
			else state.result.validationChecks = [...validationOptions.categories];
		}
	}
	getBuildOptions(document) {
		return this.buildState.get(document.uri.toString())?.options ?? {};
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultIndexManager = class {
	constructor(services) {
		/**
		* The symbol index stores all `AstNodeDescription` items exported by a document.
		* The key used in this map is the string representation of the specific document URI.
		*/
		this.symbolIndex = /* @__PURE__ */ new Map();
		/**
		* This is a cache for the `allElements()` method.
		* It caches the descriptions from `symbolIndex` grouped by types.
		*/
		this.symbolByTypeIndex = new ContextCache();
		/**
		* This index keeps track of all `ReferenceDescription` items exported by a document.
		* This is used to compute which elements are affected by a document change
		* and for finding references to an AST node.
		*/
		this.referenceIndex = /* @__PURE__ */ new Map();
		this.documents = services.workspace.LangiumDocuments;
		this.serviceRegistry = services.ServiceRegistry;
		this.astReflection = services.AstReflection;
	}
	findAllReferences(targetNode, astNodePath) {
		const targetDocUri = getDocument(targetNode).uri;
		const result = [];
		this.referenceIndex.forEach((docRefs) => {
			docRefs.forEach((refDescr) => {
				if (UriUtils.equals(refDescr.targetUri, targetDocUri) && refDescr.targetPath === astNodePath) result.push(refDescr);
			});
		});
		return stream(result);
	}
	allElements(nodeType, uris) {
		let documentUris = stream(this.symbolIndex.keys());
		if (uris) documentUris = documentUris.filter((uri) => !uris || uris.has(uri));
		return documentUris.map((uri) => this.getFileDescriptions(uri, nodeType)).flat();
	}
	getFileDescriptions(uri, nodeType) {
		if (!nodeType) return this.symbolIndex.get(uri) ?? [];
		return this.symbolByTypeIndex.get(uri, nodeType, () => {
			return (this.symbolIndex.get(uri) ?? []).filter((e) => this.astReflection.isSubtype(e.type, nodeType));
		});
	}
	remove(uri) {
		this.removeContent(uri);
		this.removeReferences(uri);
	}
	removeContent(uri) {
		const uriString = uri.toString();
		this.symbolIndex.delete(uriString);
		this.symbolByTypeIndex.clear(uriString);
	}
	removeReferences(uri) {
		const uriString = uri.toString();
		this.referenceIndex.delete(uriString);
	}
	async updateContent(document, cancelToken = cancellation_exports.CancellationToken.None) {
		const exports = await this.serviceRegistry.getServices(document.uri).references.ScopeComputation.collectExportedSymbols(document, cancelToken);
		const uri = document.uri.toString();
		this.symbolIndex.set(uri, exports);
		this.symbolByTypeIndex.clear(uri);
	}
	async updateReferences(document, cancelToken = cancellation_exports.CancellationToken.None) {
		const indexData = await this.serviceRegistry.getServices(document.uri).workspace.ReferenceDescriptionProvider.createDescriptions(document, cancelToken);
		this.referenceIndex.set(document.uri.toString(), indexData);
	}
	isAffected(document, changedUris) {
		const references = this.referenceIndex.get(document.uri.toString());
		if (!references) return false;
		return references.some((ref) => !ref.local && changedUris.has(ref.targetUri.toString()));
	}
};
/******************************************************************************
* Copyright 2022 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultWorkspaceManager = class {
	constructor(services) {
		this.initialBuildOptions = {};
		this._ready = new Deferred();
		this.serviceRegistry = services.ServiceRegistry;
		this.langiumDocuments = services.workspace.LangiumDocuments;
		this.documentBuilder = services.workspace.DocumentBuilder;
		this.fileSystemProvider = services.workspace.FileSystemProvider;
		this.mutex = services.workspace.WorkspaceLock;
	}
	get ready() {
		return this._ready.promise;
	}
	get workspaceFolders() {
		return this.folders;
	}
	initialize(params) {
		this.folders = params.workspaceFolders ?? void 0;
	}
	initialized(_params) {
		return this.mutex.write((token) => this.initializeWorkspace(this.folders ?? [], token));
	}
	async initializeWorkspace(folders, cancelToken = cancellation_exports.CancellationToken.None) {
		const documents = await this.performStartup(folders);
		await interruptAndCheck(cancelToken);
		await this.documentBuilder.build(documents, this.initialBuildOptions, cancelToken);
	}
	/**
	* Performs the uninterruptable startup sequence of the workspace manager.
	* This methods loads all documents in the workspace and other documents and returns them.
	*/
	async performStartup(folders) {
		const documents = [];
		const collector = (document) => {
			documents.push(document);
			if (!this.langiumDocuments.hasDocument(document.uri)) this.langiumDocuments.addDocument(document);
		};
		await this.loadAdditionalDocuments(folders, collector);
		const uris = [];
		await Promise.all(folders.map((wf) => this.getRootFolder(wf)).map(async (entry) => this.traverseFolder(entry, uris)));
		const uniqueUris = stream(uris).distinct((uri) => uri.toString()).filter((uri) => !this.langiumDocuments.hasDocument(uri));
		await this.loadWorkspaceDocuments(uniqueUris, collector);
		this._ready.resolve();
		return documents;
	}
	async loadWorkspaceDocuments(uris, collector) {
		await Promise.all(uris.map(async (uri) => {
			collector(await this.langiumDocuments.getOrCreateDocument(uri));
		}));
	}
	/**
	* Load all additional documents that shall be visible in the context of the given workspace
	* folders and add them to the collector. This can be used to include built-in libraries of
	* your language, which can be either loaded from provided files or constructed in memory.
	*/
	loadAdditionalDocuments(_folders, _collector) {
		return Promise.resolve();
	}
	/**
	* Determine the root folder of the source documents in the given workspace folder.
	* The default implementation returns the URI of the workspace folder, but you can override
	* this to return a subfolder like `src` instead.
	*/
	getRootFolder(workspaceFolder) {
		return URI.parse(workspaceFolder.uri);
	}
	/**
	* Traverse the file system folder identified by the given URI and its subfolders. All
	* contained files that match the file extensions are added to the `uris` array.
	*/
	async traverseFolder(folderPath, uris) {
		try {
			const content = await this.fileSystemProvider.readDirectory(folderPath);
			await Promise.all(content.map(async (entry) => {
				if (this.shouldIncludeEntry(entry)) {
					if (entry.isDirectory) await this.traverseFolder(entry.uri, uris);
					else if (entry.isFile) uris.push(entry.uri);
				}
			}));
		} catch (e) {
			console.error("Failure to read directory content of " + folderPath.toString(true), e);
		}
	}
	async searchFolder(uri) {
		const uris = [];
		await this.traverseFolder(uri, uris);
		return uris;
	}
	/**
	* Determine whether the given folder entry shall be included while indexing the workspace.
	*/
	shouldIncludeEntry(entry) {
		const name = UriUtils.basename(entry.uri);
		if (name.startsWith(".")) return false;
		if (entry.isDirectory) return name !== "node_modules" && name !== "out";
		else if (entry.isFile) return this.serviceRegistry.hasServices(entry.uri);
		return false;
	}
};
/******************************************************************************
* Copyright 2022 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultLexerErrorMessageProvider = class {
	buildUnexpectedCharactersMessage(fullText, startOffset, length, line, column) {
		return defaultLexerErrorProvider.buildUnexpectedCharactersMessage(fullText, startOffset, length, line, column);
	}
	buildUnableToPopLexerModeMessage(token) {
		return defaultLexerErrorProvider.buildUnableToPopLexerModeMessage(token);
	}
};
var DEFAULT_TOKENIZE_OPTIONS = { mode: "full" };
var DefaultLexer = class {
	constructor(services) {
		this.errorMessageProvider = services.parser.LexerErrorMessageProvider;
		this.tokenBuilder = services.parser.TokenBuilder;
		const tokens = this.tokenBuilder.buildTokens(services.Grammar, { caseInsensitive: services.LanguageMetaData.caseInsensitive });
		this.tokenTypes = this.toTokenTypeDictionary(tokens);
		const lexerTokens = isTokenTypeDictionary(tokens) ? Object.values(tokens) : tokens;
		const production = services.LanguageMetaData.mode === "production";
		this.chevrotainLexer = new Lexer(lexerTokens, {
			positionTracking: "full",
			skipValidations: production,
			errorMessageProvider: this.errorMessageProvider
		});
	}
	get definition() {
		return this.tokenTypes;
	}
	tokenize(text, _options = DEFAULT_TOKENIZE_OPTIONS) {
		const chevrotainResult = this.chevrotainLexer.tokenize(text);
		return {
			tokens: chevrotainResult.tokens,
			errors: chevrotainResult.errors,
			hidden: chevrotainResult.groups.hidden ?? [],
			report: this.tokenBuilder.flushLexingReport?.(text)
		};
	}
	toTokenTypeDictionary(buildTokens) {
		if (isTokenTypeDictionary(buildTokens)) return buildTokens;
		const tokens = isIMultiModeLexerDefinition(buildTokens) ? Object.values(buildTokens.modes).flat() : buildTokens;
		const res = {};
		tokens.forEach((token) => res[token.name] = token);
		return res;
	}
};
/**
* Returns a check whether the given TokenVocabulary is TokenType array
*/
function isTokenTypeArray(tokenVocabulary) {
	return Array.isArray(tokenVocabulary) && (tokenVocabulary.length === 0 || "name" in tokenVocabulary[0]);
}
/**
* Returns a check whether the given TokenVocabulary is IMultiModeLexerDefinition
*/
function isIMultiModeLexerDefinition(tokenVocabulary) {
	return tokenVocabulary && "modes" in tokenVocabulary && "defaultMode" in tokenVocabulary;
}
/**
* Returns a check whether the given TokenVocabulary is TokenTypeDictionary
*/
function isTokenTypeDictionary(tokenVocabulary) {
	return !isTokenTypeArray(tokenVocabulary) && !isIMultiModeLexerDefinition(tokenVocabulary);
}
init_main();
function parseJSDoc(node, start, options) {
	let opts;
	let position;
	if (typeof node === "string") {
		position = start;
		opts = options;
	} else {
		position = node.range.start;
		opts = start;
	}
	if (!position) position = Position.create(0, 0);
	const lines = getLines(node);
	const normalizedOptions = normalizeOptions(opts);
	return parseJSDocComment({
		index: 0,
		tokens: tokenize({
			lines,
			position,
			options: normalizedOptions
		}),
		position
	});
}
function isJSDoc(node, options) {
	const normalizedOptions = normalizeOptions(options);
	const lines = getLines(node);
	if (lines.length === 0) return false;
	const first = lines[0];
	const last = lines[lines.length - 1];
	const firstRegex = normalizedOptions.start;
	const lastRegex = normalizedOptions.end;
	return Boolean(firstRegex?.exec(first)) && Boolean(lastRegex?.exec(last));
}
function getLines(node) {
	let content = "";
	if (typeof node === "string") content = node;
	else content = node.text;
	return content.split(NEWLINE_REGEXP);
}
var tagRegex = /\s*(@([\p{L}][\p{L}\p{N}]*)?)/uy;
var inlineTagRegex = /\{(@[\p{L}][\p{L}\p{N}]*)(\s*)([^\r\n}]+)?\}/gu;
function tokenize(context) {
	const tokens = [];
	let currentLine = context.position.line;
	let currentCharacter = context.position.character;
	for (let i = 0; i < context.lines.length; i++) {
		const first = i === 0;
		const last = i === context.lines.length - 1;
		let line = context.lines[i];
		let index = 0;
		if (first && context.options.start) {
			const match = context.options.start?.exec(line);
			if (match) index = match.index + match[0].length;
		} else {
			const match = context.options.line?.exec(line);
			if (match) index = match.index + match[0].length;
		}
		if (last) {
			const match = context.options.end?.exec(line);
			if (match) line = line.substring(0, match.index);
		}
		line = line.substring(0, lastCharacter(line));
		if (skipWhitespace(line, index) >= line.length) {
			if (tokens.length > 0) {
				const position = Position.create(currentLine, currentCharacter);
				tokens.push({
					type: "break",
					content: "",
					range: Range.create(position, position)
				});
			}
		} else {
			tagRegex.lastIndex = index;
			const tagMatch = tagRegex.exec(line);
			if (tagMatch) {
				const fullMatch = tagMatch[0];
				const value = tagMatch[1];
				const start = Position.create(currentLine, currentCharacter + index);
				const end = Position.create(currentLine, currentCharacter + index + fullMatch.length);
				tokens.push({
					type: "tag",
					content: value,
					range: Range.create(start, end)
				});
				index += fullMatch.length;
				index = skipWhitespace(line, index);
			}
			if (index < line.length) {
				const rest = line.substring(index);
				const inlineTagMatches = Array.from(rest.matchAll(inlineTagRegex));
				tokens.push(...buildInlineTokens(inlineTagMatches, rest, currentLine, currentCharacter + index));
			}
		}
		currentLine++;
		currentCharacter = 0;
	}
	if (tokens.length > 0 && tokens[tokens.length - 1].type === "break") return tokens.slice(0, -1);
	return tokens;
}
function buildInlineTokens(tags, line, lineIndex, characterIndex) {
	const tokens = [];
	if (tags.length === 0) {
		const start = Position.create(lineIndex, characterIndex);
		const end = Position.create(lineIndex, characterIndex + line.length);
		tokens.push({
			type: "text",
			content: line,
			range: Range.create(start, end)
		});
	} else {
		let lastIndex = 0;
		for (const match of tags) {
			const matchIndex = match.index;
			const startContent = line.substring(lastIndex, matchIndex);
			if (startContent.length > 0) tokens.push({
				type: "text",
				content: line.substring(lastIndex, matchIndex),
				range: Range.create(Position.create(lineIndex, lastIndex + characterIndex), Position.create(lineIndex, matchIndex + characterIndex))
			});
			let offset = startContent.length + 1;
			const tagName = match[1];
			tokens.push({
				type: "inline-tag",
				content: tagName,
				range: Range.create(Position.create(lineIndex, lastIndex + offset + characterIndex), Position.create(lineIndex, lastIndex + offset + tagName.length + characterIndex))
			});
			offset += tagName.length;
			if (match.length === 4) {
				offset += match[2].length;
				const value = match[3];
				tokens.push({
					type: "text",
					content: value,
					range: Range.create(Position.create(lineIndex, lastIndex + offset + characterIndex), Position.create(lineIndex, lastIndex + offset + value.length + characterIndex))
				});
			} else tokens.push({
				type: "text",
				content: "",
				range: Range.create(Position.create(lineIndex, lastIndex + offset + characterIndex), Position.create(lineIndex, lastIndex + offset + characterIndex))
			});
			lastIndex = matchIndex + match[0].length;
		}
		const endContent = line.substring(lastIndex);
		if (endContent.length > 0) tokens.push({
			type: "text",
			content: endContent,
			range: Range.create(Position.create(lineIndex, lastIndex + characterIndex), Position.create(lineIndex, lastIndex + characterIndex + endContent.length))
		});
	}
	return tokens;
}
var nonWhitespaceRegex = /\S/;
var whitespaceEndRegex = /\s*$/;
function skipWhitespace(line, index) {
	const match = line.substring(index).match(nonWhitespaceRegex);
	if (match) return index + match.index;
	else return line.length;
}
function lastCharacter(line) {
	const match = line.match(whitespaceEndRegex);
	if (match && typeof match.index === "number") return match.index;
}
function parseJSDocComment(context) {
	const startPosition = Position.create(context.position.line, context.position.character);
	if (context.tokens.length === 0) return new JSDocCommentImpl([], Range.create(startPosition, startPosition));
	const elements = [];
	while (context.index < context.tokens.length) {
		const element = parseJSDocElement(context, elements[elements.length - 1]);
		if (element) elements.push(element);
	}
	const start = elements[0]?.range.start ?? startPosition;
	const end = elements[elements.length - 1]?.range.end ?? startPosition;
	return new JSDocCommentImpl(elements, Range.create(start, end));
}
function parseJSDocElement(context, last) {
	const next = context.tokens[context.index];
	if (next.type === "tag") return parseJSDocTag(context, false);
	else if (next.type === "text" || next.type === "inline-tag") return parseJSDocText(context);
	else {
		appendEmptyLine(next, last);
		context.index++;
		return;
	}
}
function appendEmptyLine(token, element) {
	if (element) {
		const line = new JSDocLineImpl("", token.range);
		if ("inlines" in element) element.inlines.push(line);
		else element.content.inlines.push(line);
	}
}
function parseJSDocText(context) {
	let token = context.tokens[context.index];
	const firstToken = token;
	let lastToken = token;
	const lines = [];
	while (token && token.type !== "break" && token.type !== "tag") {
		lines.push(parseJSDocInline(context));
		lastToken = token;
		token = context.tokens[context.index];
	}
	return new JSDocTextImpl(lines, Range.create(firstToken.range.start, lastToken.range.end));
}
function parseJSDocInline(context) {
	if (context.tokens[context.index].type === "inline-tag") return parseJSDocTag(context, true);
	else return parseJSDocLine(context);
}
function parseJSDocTag(context, inline) {
	const tagToken = context.tokens[context.index++];
	const name = tagToken.content.substring(1);
	if (context.tokens[context.index]?.type === "text") if (inline) {
		const docLine = parseJSDocLine(context);
		return new JSDocTagImpl(name, new JSDocTextImpl([docLine], docLine.range), inline, Range.create(tagToken.range.start, docLine.range.end));
	} else {
		const textDoc = parseJSDocText(context);
		return new JSDocTagImpl(name, textDoc, inline, Range.create(tagToken.range.start, textDoc.range.end));
	}
	else {
		const range = tagToken.range;
		return new JSDocTagImpl(name, new JSDocTextImpl([], range), inline, range);
	}
}
function parseJSDocLine(context) {
	const token = context.tokens[context.index++];
	return new JSDocLineImpl(token.content, token.range);
}
function normalizeOptions(options) {
	if (!options) return normalizeOptions({
		start: "/**",
		end: "*/",
		line: "*"
	});
	const { start, end, line } = options;
	return {
		start: normalizeOption(start, true),
		end: normalizeOption(end, false),
		line: normalizeOption(line, true)
	};
}
function normalizeOption(option, start) {
	if (typeof option === "string" || typeof option === "object") {
		const escaped = typeof option === "string" ? escapeRegExp(option) : option.source;
		if (start) return new RegExp(`^\\s*${escaped}`);
		else return new RegExp(`\\s*${escaped}\\s*$`);
	} else return option;
}
var JSDocCommentImpl = class {
	constructor(elements, range) {
		this.elements = elements;
		this.range = range;
	}
	getTag(name) {
		return this.getAllTags().find((e) => e.name === name);
	}
	getTags(name) {
		return this.getAllTags().filter((e) => e.name === name);
	}
	getAllTags() {
		return this.elements.filter((e) => "name" in e);
	}
	toString() {
		let value = "";
		for (const element of this.elements) if (value.length === 0) value = element.toString();
		else {
			const text = element.toString();
			value += fillNewlines(value) + text;
		}
		return value.trim();
	}
	toMarkdown(options) {
		let value = "";
		for (const element of this.elements) if (value.length === 0) value = element.toMarkdown(options);
		else {
			const text = element.toMarkdown(options);
			value += fillNewlines(value) + text;
		}
		return value.trim();
	}
};
var JSDocTagImpl = class {
	constructor(name, content, inline, range) {
		this.name = name;
		this.content = content;
		this.inline = inline;
		this.range = range;
	}
	toString() {
		let text = `@${this.name}`;
		const content = this.content.toString();
		if (this.content.inlines.length === 1) text = `${text} ${content}`;
		else if (this.content.inlines.length > 1) text = `${text}\n${content}`;
		if (this.inline) return `{${text}}`;
		else return text;
	}
	toMarkdown(options) {
		return options?.renderTag?.(this) ?? this.toMarkdownDefault(options);
	}
	toMarkdownDefault(options) {
		const content = this.content.toMarkdown(options);
		if (this.inline) {
			const rendered = renderInlineTag(this.name, content, options ?? {});
			if (typeof rendered === "string") return rendered;
		}
		let marker = "";
		if (options?.tag === "italic" || options?.tag === void 0) marker = "*";
		else if (options?.tag === "bold") marker = "**";
		else if (options?.tag === "bold-italic") marker = "***";
		let text = `${marker}@${this.name}${marker}`;
		if (this.content.inlines.length === 1) text = `${text} — ${content}`;
		else if (this.content.inlines.length > 1) text = `${text}\n${content}`;
		if (this.inline) return `{${text}}`;
		else return text;
	}
};
function renderInlineTag(tag, content, options) {
	if (tag === "linkplain" || tag === "linkcode" || tag === "link") {
		const index = content.indexOf(" ");
		let display = content;
		if (index > 0) {
			const displayStart = skipWhitespace(content, index);
			display = content.substring(displayStart);
			content = content.substring(0, index);
		}
		if (tag === "linkcode" || tag === "link" && options.link === "code") display = `\`${display}\``;
		return options.renderLink?.(content, display) ?? renderLinkDefault(content, display);
	}
}
function renderLinkDefault(content, display) {
	try {
		URI.parse(content, true);
		return `[${display}](${content})`;
	} catch {
		return content;
	}
}
var JSDocTextImpl = class {
	constructor(lines, range) {
		this.inlines = lines;
		this.range = range;
	}
	toString() {
		let text = "";
		for (let i = 0; i < this.inlines.length; i++) {
			const inline = this.inlines[i];
			const next = this.inlines[i + 1];
			text += inline.toString();
			if (next && next.range.start.line > inline.range.start.line) text += "\n";
		}
		return text;
	}
	toMarkdown(options) {
		let text = "";
		for (let i = 0; i < this.inlines.length; i++) {
			const inline = this.inlines[i];
			const next = this.inlines[i + 1];
			text += inline.toMarkdown(options);
			if (next && next.range.start.line > inline.range.start.line) text += "\n";
		}
		return text;
	}
};
var JSDocLineImpl = class {
	constructor(text, range) {
		this.text = text;
		this.range = range;
	}
	toString() {
		return this.text;
	}
	toMarkdown() {
		return this.text;
	}
};
function fillNewlines(text) {
	if (text.endsWith("\n")) return "\n";
	else return "\n\n";
}
/******************************************************************************
* Copyright 2023 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var JSDocDocumentationProvider = class {
	constructor(services) {
		this.indexManager = services.shared.workspace.IndexManager;
		this.commentProvider = services.documentation.CommentProvider;
	}
	getDocumentation(node) {
		const comment = this.commentProvider.getComment(node);
		if (comment && isJSDoc(comment)) return parseJSDoc(comment).toMarkdown({
			renderLink: (link, display) => {
				return this.documentationLinkRenderer(node, link, display);
			},
			renderTag: (tag) => {
				return this.documentationTagRenderer(node, tag);
			}
		});
	}
	documentationLinkRenderer(node, name, display) {
		const description = this.findNameInLocalSymbols(node, name) ?? this.findNameInGlobalScope(node, name);
		if (description && description.nameSegment) {
			const line = description.nameSegment.range.start.line + 1;
			const character = description.nameSegment.range.start.character + 1;
			return `[${display}](${description.documentUri.with({ fragment: `L${line},${character}` }).toString()})`;
		} else return;
	}
	documentationTagRenderer(_node, _tag) {}
	findNameInLocalSymbols(node, name) {
		const precomputed = getDocument(node).localSymbols;
		if (!precomputed) return;
		let currentNode = node;
		do {
			const description = precomputed.getStream(currentNode).find((e) => e.name === name);
			if (description) return description;
			currentNode = currentNode.$container;
		} while (currentNode);
	}
	findNameInGlobalScope(node, name) {
		return this.indexManager.allElements().find((e) => e.name === name);
	}
};
/******************************************************************************
* Copyright 2023 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultCommentProvider = class {
	constructor(services) {
		this.grammarConfig = () => services.parser.GrammarConfig;
	}
	getComment(node) {
		if (isAstNodeWithComment(node)) return node.$comment;
		return findCommentNode(node.$cstNode, this.grammarConfig().multilineCommentRules)?.text;
	}
};
/**
* Default implementation of the async parser which simply wraps the sync parser in a promise.
*
* @remarks
* A real implementation would create worker threads or web workers to offload the parsing work.
*/
var DefaultAsyncParser = class {
	constructor(services) {
		this.syncParser = services.parser.LangiumParser;
	}
	parse(text, _cancelToken) {
		return Promise.resolve(this.syncParser.parse(text));
	}
};
/******************************************************************************
* Copyright 2023 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultWorkspaceLock = class {
	constructor() {
		this.previousTokenSource = new cancellation_exports.CancellationTokenSource();
		this.writeQueue = [];
		this.readQueue = [];
		this.done = true;
	}
	write(action) {
		this.cancelWrite();
		const tokenSource = startCancelableOperation();
		this.previousTokenSource = tokenSource;
		return this.enqueue(this.writeQueue, action, tokenSource.token);
	}
	read(action) {
		return this.enqueue(this.readQueue, action);
	}
	enqueue(queue, action, cancellationToken = cancellation_exports.CancellationToken.None) {
		const deferred = new Deferred();
		const entry = {
			action,
			deferred,
			cancellationToken
		};
		queue.push(entry);
		this.performNextOperation();
		return deferred.promise;
	}
	async performNextOperation() {
		if (!this.done) return;
		const entries = [];
		if (this.writeQueue.length > 0) entries.push(this.writeQueue.shift());
		else if (this.readQueue.length > 0) entries.push(...this.readQueue.splice(0, this.readQueue.length));
		else return;
		this.done = false;
		await Promise.all(entries.map(async ({ action, deferred, cancellationToken }) => {
			try {
				const result = await Promise.resolve().then(() => action(cancellationToken));
				deferred.resolve(result);
			} catch (err) {
				if (isOperationCancelled(err)) deferred.resolve(void 0);
				else deferred.reject(err);
			}
		}));
		this.done = true;
		this.performNextOperation();
	}
	cancelWrite() {
		this.previousTokenSource.cancel();
	}
};
/******************************************************************************
* Copyright 2024 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var DefaultHydrator = class {
	constructor(services) {
		this.grammarElementIdMap = new BiMap();
		this.tokenTypeIdMap = new BiMap();
		this.grammar = services.Grammar;
		this.lexer = services.parser.Lexer;
		this.linker = services.references.Linker;
	}
	dehydrate(result) {
		return {
			lexerErrors: result.lexerErrors,
			lexerReport: result.lexerReport ? this.dehydrateLexerReport(result.lexerReport) : void 0,
			parserErrors: result.parserErrors.map((e) => ({
				...e,
				message: e.message
			})),
			value: this.dehydrateAstNode(result.value, this.createDehyrationContext(result.value))
		};
	}
	dehydrateLexerReport(lexerReport) {
		return lexerReport;
	}
	createDehyrationContext(node) {
		const astNodes = /* @__PURE__ */ new Map();
		const cstNodes = /* @__PURE__ */ new Map();
		for (const astNode of streamAst(node)) astNodes.set(astNode, {});
		if (node.$cstNode) for (const cstNode of streamCst(node.$cstNode)) cstNodes.set(cstNode, {});
		return {
			astNodes,
			cstNodes
		};
	}
	dehydrateAstNode(node, context) {
		const obj = context.astNodes.get(node);
		obj.$type = node.$type;
		obj.$containerIndex = node.$containerIndex;
		obj.$containerProperty = node.$containerProperty;
		if (node.$cstNode !== void 0) obj.$cstNode = this.dehydrateCstNode(node.$cstNode, context);
		for (const [name, value] of Object.entries(node)) {
			if (name.startsWith("$")) continue;
			if (Array.isArray(value)) {
				const arr = [];
				obj[name] = arr;
				for (const item of value) if (isAstNode(item)) arr.push(this.dehydrateAstNode(item, context));
				else if (isReference(item)) arr.push(this.dehydrateReference(item, context));
				else arr.push(item);
			} else if (isAstNode(value)) obj[name] = this.dehydrateAstNode(value, context);
			else if (isReference(value)) obj[name] = this.dehydrateReference(value, context);
			else if (value !== void 0) obj[name] = value;
		}
		return obj;
	}
	dehydrateReference(reference, context) {
		const obj = {};
		obj.$refText = reference.$refText;
		if (reference.$refNode) obj.$refNode = context.cstNodes.get(reference.$refNode);
		return obj;
	}
	dehydrateCstNode(node, context) {
		const cstNode = context.cstNodes.get(node);
		if (isRootCstNode(node)) cstNode.fullText = node.fullText;
		else cstNode.grammarSource = this.getGrammarElementId(node.grammarSource);
		cstNode.hidden = node.hidden;
		cstNode.astNode = context.astNodes.get(node.astNode);
		if (isCompositeCstNode(node)) cstNode.content = node.content.map((child) => this.dehydrateCstNode(child, context));
		else if (isLeafCstNode(node)) {
			cstNode.tokenType = node.tokenType.name;
			cstNode.offset = node.offset;
			cstNode.length = node.length;
			cstNode.startLine = node.range.start.line;
			cstNode.startColumn = node.range.start.character;
			cstNode.endLine = node.range.end.line;
			cstNode.endColumn = node.range.end.character;
		}
		return cstNode;
	}
	hydrate(result) {
		const node = result.value;
		const context = this.createHydrationContext(node);
		if ("$cstNode" in node) this.hydrateCstNode(node.$cstNode, context);
		return {
			lexerErrors: result.lexerErrors,
			lexerReport: result.lexerReport,
			parserErrors: result.parserErrors,
			value: this.hydrateAstNode(node, context)
		};
	}
	createHydrationContext(node) {
		const astNodes = /* @__PURE__ */ new Map();
		const cstNodes = /* @__PURE__ */ new Map();
		for (const astNode of streamAst(node)) astNodes.set(astNode, {});
		let root;
		if (node.$cstNode) for (const cstNode of streamCst(node.$cstNode)) {
			let cst;
			if ("fullText" in cstNode) {
				cst = new RootCstNodeImpl(cstNode.fullText);
				root = cst;
			} else if ("content" in cstNode) cst = new CompositeCstNodeImpl();
			else if ("tokenType" in cstNode) cst = this.hydrateCstLeafNode(cstNode);
			if (cst) {
				cstNodes.set(cstNode, cst);
				cst.root = root;
			}
		}
		return {
			astNodes,
			cstNodes
		};
	}
	hydrateAstNode(node, context) {
		const astNode = context.astNodes.get(node);
		astNode.$type = node.$type;
		astNode.$containerIndex = node.$containerIndex;
		astNode.$containerProperty = node.$containerProperty;
		if (node.$cstNode) astNode.$cstNode = context.cstNodes.get(node.$cstNode);
		for (const [name, value] of Object.entries(node)) {
			if (name.startsWith("$")) continue;
			if (Array.isArray(value)) {
				const arr = [];
				astNode[name] = arr;
				for (const item of value) if (isAstNode(item)) arr.push(this.setParent(this.hydrateAstNode(item, context), astNode));
				else if (isReference(item)) arr.push(this.hydrateReference(item, astNode, name, context));
				else arr.push(item);
			} else if (isAstNode(value)) astNode[name] = this.setParent(this.hydrateAstNode(value, context), astNode);
			else if (isReference(value)) astNode[name] = this.hydrateReference(value, astNode, name, context);
			else if (value !== void 0) astNode[name] = value;
		}
		return astNode;
	}
	setParent(node, parent) {
		node.$container = parent;
		return node;
	}
	hydrateReference(reference, node, name, context) {
		return this.linker.buildReference(node, name, context.cstNodes.get(reference.$refNode), reference.$refText);
	}
	hydrateCstNode(cstNode, context, num = 0) {
		const cstNodeObj = context.cstNodes.get(cstNode);
		if (typeof cstNode.grammarSource === "number") cstNodeObj.grammarSource = this.getGrammarElement(cstNode.grammarSource);
		cstNodeObj.astNode = context.astNodes.get(cstNode.astNode);
		if (isCompositeCstNode(cstNodeObj)) for (const child of cstNode.content) {
			const hydrated = this.hydrateCstNode(child, context, num++);
			cstNodeObj.content.push(hydrated);
		}
		return cstNodeObj;
	}
	hydrateCstLeafNode(cstNode) {
		const tokenType = this.getTokenType(cstNode.tokenType);
		const offset = cstNode.offset;
		const length = cstNode.length;
		const startLine = cstNode.startLine;
		const startColumn = cstNode.startColumn;
		const endLine = cstNode.endLine;
		const endColumn = cstNode.endColumn;
		const hidden = cstNode.hidden;
		return new LeafCstNodeImpl(offset, length, {
			start: {
				line: startLine,
				character: startColumn
			},
			end: {
				line: endLine,
				character: endColumn
			}
		}, tokenType, hidden);
	}
	getTokenType(name) {
		return this.lexer.definition[name];
	}
	getGrammarElementId(node) {
		if (!node) return;
		if (this.grammarElementIdMap.size === 0) this.createGrammarElementIdMap();
		return this.grammarElementIdMap.get(node);
	}
	getGrammarElement(id) {
		if (this.grammarElementIdMap.size === 0) this.createGrammarElementIdMap();
		return this.grammarElementIdMap.getKey(id);
	}
	createGrammarElementIdMap() {
		let id = 0;
		for (const element of streamAst(this.grammar)) if (isAbstractElement(element)) this.grammarElementIdMap.set(element, id++);
	}
};
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
/**
* Creates a dependency injection module configuring the default core services.
* This is a set of services that are dedicated to a specific language.
*/
function createDefaultCoreModule(context) {
	return {
		documentation: {
			CommentProvider: (services) => new DefaultCommentProvider(services),
			DocumentationProvider: (services) => new JSDocDocumentationProvider(services)
		},
		parser: {
			AsyncParser: (services) => new DefaultAsyncParser(services),
			GrammarConfig: (services) => createGrammarConfig(services),
			LangiumParser: (services) => createLangiumParser(services),
			CompletionParser: (services) => createCompletionParser(services),
			ValueConverter: () => new DefaultValueConverter(),
			TokenBuilder: () => new DefaultTokenBuilder(),
			Lexer: (services) => new DefaultLexer(services),
			ParserErrorMessageProvider: () => new LangiumParserErrorMessageProvider(),
			LexerErrorMessageProvider: () => new DefaultLexerErrorMessageProvider()
		},
		workspace: {
			AstNodeLocator: () => new DefaultAstNodeLocator(),
			AstNodeDescriptionProvider: (services) => new DefaultAstNodeDescriptionProvider(services),
			ReferenceDescriptionProvider: (services) => new DefaultReferenceDescriptionProvider(services)
		},
		references: {
			Linker: (services) => new DefaultLinker(services),
			NameProvider: () => new DefaultNameProvider(),
			ScopeProvider: (services) => new DefaultScopeProvider(services),
			ScopeComputation: (services) => new DefaultScopeComputation(services),
			References: (services) => new DefaultReferences(services)
		},
		serializer: {
			Hydrator: (services) => new DefaultHydrator(services),
			JsonSerializer: (services) => new DefaultJsonSerializer(services)
		},
		validation: {
			DocumentValidator: (services) => new DefaultDocumentValidator(services),
			ValidationRegistry: (services) => new ValidationRegistry(services)
		},
		shared: () => context.shared
	};
}
/**
* Creates a dependency injection module configuring the default shared core services.
* This is the set of services that are shared between multiple languages.
*/
function createDefaultSharedCoreModule(context) {
	return {
		ServiceRegistry: (services) => new DefaultServiceRegistry(services),
		workspace: {
			LangiumDocuments: (services) => new DefaultLangiumDocuments(services),
			LangiumDocumentFactory: (services) => new DefaultLangiumDocumentFactory(services),
			DocumentBuilder: (services) => new DefaultDocumentBuilder(services),
			IndexManager: (services) => new DefaultIndexManager(services),
			WorkspaceManager: (services) => new DefaultWorkspaceManager(services),
			FileSystemProvider: (services) => context.fileSystemProvider(services),
			WorkspaceLock: () => new DefaultWorkspaceLock(),
			ConfigurationProvider: (services) => new DefaultConfigurationProvider(services)
		},
		profilers: {}
	};
}
/******************************************************************************
* Copyright 2021 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var Module;
(function(Module) {
	/**
	* Merges two dependency injection modules into a new (third) one that is returned.
	* At that `m1` and `m2` stay unchanged. Therefore, `m1` is deep-copied first,
	* and m2 is merged onto the copy afterwards.
	*
	* Note that the leaf values of `m1` and `m2`, i.e. the service constructor functions,
	* cannot be copied generically, since they are functions. They are shared by the source and merged modules.
	*
	* @returns the merged module being a deep copy of `m1` with `m2` merged onto it.
	*/
	Module.merge = (m1, m2) => _merge(_merge({}, m1), m2);
})(Module || (Module = {}));
/**
* Given a set of modules, the inject function returns a lazily evaluated injector
* that injects dependencies into the requested service when it is requested the
* first time. Subsequent requests will return the same service.
*
* In the case of cyclic dependencies, an Error will be thrown. This can be fixed
* by injecting a provider `() => T` instead of a `T`.
*
* Please note that the arguments may be objects or arrays. However, the result will
* be an object. Using it with for..of will have no effect.
*
* @param module1 first Module
* @param module2 (optional) second Module
* @param module3 (optional) third Module
* @param module4 (optional) fourth Module
* @param module5 (optional) fifth Module
* @param module6 (optional) sixth Module
* @param module7 (optional) seventh Module
* @param module8 (optional) eighth Module
* @param module9 (optional) ninth Module
* @returns a new object of type I
*/
function inject(module1, module2, module3, module4, module5, module6, module7, module8, module9) {
	return _inject([
		module1,
		module2,
		module3,
		module4,
		module5,
		module6,
		module7,
		module8,
		module9
	].reduce(_merge, {}));
}
var isProxy = Symbol("isProxy");
/**
* Helper function that returns an injector by creating a proxy.
* Invariant: injector is of type I. If injector is undefined, then T = I.
*/
function _inject(module, injector) {
	const proxy = new Proxy({}, {
		deleteProperty: () => false,
		set: () => {
			throw new Error("Cannot set property on injected service container");
		},
		get: (obj, prop) => {
			if (prop === isProxy) return true;
			else return _resolve(obj, prop, module, injector || proxy);
		},
		getOwnPropertyDescriptor: (obj, prop) => (_resolve(obj, prop, module, injector || proxy), Object.getOwnPropertyDescriptor(obj, prop)),
		has: (_, prop) => prop in module,
		ownKeys: () => [...Object.getOwnPropertyNames(module)]
	});
	return proxy;
}
/**
* Internally used to tag a requested dependency, directly before calling the factory.
* This allows us to find cycles during instance creation.
*/
var __requested__ = Symbol();
/**
* Returns the value `obj[prop]`. If the value does not exist, yet, it is resolved from
* the module description. The result of service factories is cached. Groups are
* recursively proxied.
*
* @param obj an object holding all group proxies and services
* @param prop the key of a value within obj
* @param module an object containing groups and service factories
* @param injector the first level proxy that provides access to all values
* @returns the requested value `obj[prop]`
* @throws Error if a dependency cycle is detected
*/
function _resolve(obj, prop, module, injector) {
	if (prop in obj) {
		if (obj[prop] instanceof Error) throw new Error("Construction failure. Please make sure that your dependencies are constructable. Cause: " + obj[prop]);
		if (obj[prop] === __requested__) throw new Error("Cycle detected. Please make \"" + String(prop) + "\" lazy. Visit https://langium.org/docs/reference/configuration-services/#resolving-cyclic-dependencies");
		return obj[prop];
	} else if (prop in module) {
		const value = module[prop];
		obj[prop] = __requested__;
		try {
			obj[prop] = typeof value === "function" ? value(injector) : _inject(value, injector);
		} catch (error) {
			obj[prop] = error instanceof Error ? error : void 0;
			throw error;
		}
		return obj[prop];
	} else return;
}
/**
* Performs a deep-merge of two modules by writing source entries into the target module.
*
* @param target the module which is written
* @param source the module which is read
* @returns the target module
*/
function _merge(target, source) {
	if (source) {
		for (const [key, sourceValue] of Object.entries(source)) if (sourceValue !== void 0 && sourceValue !== null) if (typeof sourceValue === "object") {
			const targetValue = target[key];
			if (typeof targetValue === "object" && targetValue !== null) target[key] = _merge(targetValue, sourceValue);
			else target[key] = _merge({}, sourceValue);
		} else target[key] = sourceValue;
	}
	return target;
}
/******************************************************************************
* Copyright 2022 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var EmptyFileSystemProvider = class {
	stat(_uri) {
		throw new Error("No file system is available.");
	}
	statSync(_uri) {
		throw new Error("No file system is available.");
	}
	async exists() {
		return false;
	}
	existsSync() {
		return false;
	}
	readBinary() {
		throw new Error("No file system is available.");
	}
	readBinarySync() {
		throw new Error("No file system is available.");
	}
	readFile() {
		throw new Error("No file system is available.");
	}
	readFileSync() {
		throw new Error("No file system is available.");
	}
	async readDirectory() {
		return [];
	}
	readDirectorySync() {
		return [];
	}
};
var EmptyFileSystem = { fileSystemProvider: () => new EmptyFileSystemProvider() };
/******************************************************************************
* Copyright 2023 TypeFox GmbH
* This program and the accompanying materials are made available under the
* terms of the MIT License, which is available in the project root.
******************************************************************************/
var minimalGrammarModule = {
	Grammar: () => void 0,
	LanguageMetaData: () => ({
		caseInsensitive: false,
		fileExtensions: [".langium"],
		languageId: "langium"
	})
};
var minimalSharedGrammarModule = { AstReflection: () => new LangiumGrammarAstReflection() };
function createMinimalGrammarServices() {
	const shared = inject(createDefaultSharedCoreModule(EmptyFileSystem), minimalSharedGrammarModule);
	const grammar = inject(createDefaultCoreModule({ shared }), minimalGrammarModule);
	shared.ServiceRegistry.register(grammar);
	return grammar;
}
/**
* Load a Langium grammar for your language from a JSON string. This is used by several services,
* most notably the parser builder which interprets the grammar to create a parser.
*/
function loadGrammarFromJson(json) {
	const services = createMinimalGrammarServices();
	const astNode = services.serializer.JsonSerializer.deserialize(json);
	services.shared.workspace.LangiumDocumentFactory.fromModel(astNode, URI.parse(`memory:/${astNode.name ?? "grammar"}.langium`));
	return astNode;
}
export { createDefaultSharedCoreModule as a, AbstractAstReflection as c, createDefaultCoreModule as i, EmptyFileSystem as n, DefaultValueConverter as o, inject as r, DefaultTokenBuilder as s, loadGrammarFromJson as t };
