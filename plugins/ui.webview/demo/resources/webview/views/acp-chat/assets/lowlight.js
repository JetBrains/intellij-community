import "./devlop.js";
import { A as graphql, C as lua, D as javascript, E as json, F as cpp, I as c, L as bash, M as diff, N as css, O as java, P as csharp, R as arduino, S as makefile, T as kotlin, _ as phpTemplate, a as vbnet, b as objectivec, c as sql, d as rust, f as ruby, g as plaintext, h as python, i as wasm, j as go, k as ini, l as shell, m as pythonRepl, n as yaml, o as typescript, p as r, r as xml, s as swift, t as core_default, u as scss, v as php, w as less, x as markdown, y as perl } from "./highlight-js.js";
//#region node_modules/lowlight/lib/common.js
/**
* @import {LanguageFn} from 'highlight.js'
*/
/**
* Map of grammars.
*
* @type {Record<string, LanguageFn>}
*/
var grammars = {
	arduino,
	bash,
	c,
	cpp,
	csharp,
	css,
	diff,
	go,
	graphql,
	ini,
	java,
	javascript,
	json,
	kotlin,
	less,
	lua,
	makefile,
	markdown,
	objectivec,
	perl,
	php,
	"php-template": phpTemplate,
	plaintext,
	python,
	"python-repl": pythonRepl,
	r,
	ruby,
	rust,
	scss,
	shell,
	sql,
	swift,
	typescript,
	vbnet,
	wasm,
	xml,
	yaml
};
//#endregion
//#region node_modules/lowlight/lib/index.js
/**
* @import {ElementContent, Element, RootData, Root} from 'hast'
* @import {Emitter, HLJSOptions as HljsOptions, HighlightResult, LanguageFn} from 'highlight.js'
*/
/**
* @typedef {Object} ExtraOptions
*   Extra fields.
* @property {ReadonlyArray<string> | null | undefined} [subset]
*   List of allowed languages (default: all registered languages).
*
* @typedef Options
*   Configuration for `highlight`.
* @property {string | null | undefined} [prefix='hljs-']
*   Class prefix (default: `'hljs-'`).
*
* @typedef {Options & ExtraOptions} AutoOptions
*   Configuration for `highlightAuto`.
*/
/** @type {AutoOptions} */
var emptyOptions = {};
var defaultPrefix = "hljs-";
/**
* Create a `lowlight` instance.
*
* @param {Readonly<Record<string, LanguageFn>> | null | undefined} [grammars]
*   Grammars to add (optional).
* @returns
*   Lowlight.
*/
function createLowlight(grammars) {
	const high = core_default.newInstance();
	if (grammars) register(grammars);
	return {
		highlight,
		highlightAuto,
		listLanguages,
		register,
		registerAlias,
		registered
	};
	/**
	* Highlight `value` (code) as `language` (name).
	*
	* @example
	*   ```js
	*   import {common, createLowlight} from 'lowlight'
	*
	*   const lowlight = createLowlight(common)
	*
	*   console.log(lowlight.highlight('css', 'em { color: red }'))
	*   ```
	*
	*   Yields:
	*
	*   ```js
	*   {type: 'root', children: [Array], data: {language: 'css', relevance: 3}}
	*   ```
	*
	* @param {string} language
	*   Programming language name.
	* @param {string} value
	*   Code to highlight.
	* @param {Readonly<Options> | null | undefined} [options={}]
	*   Configuration (optional).
	* @returns {Root}
	*   Tree; with the following `data` fields: `language` (`string`), detected
	*   programming language name; `relevance` (`number`), how sure lowlight is
	*   that the given code is in the language.
	*/
	function highlight(language, value, options) {
		const settings = options || emptyOptions;
		const prefix = typeof settings.prefix === "string" ? settings.prefix : defaultPrefix;
		if (!high.getLanguage(language)) throw new Error("Unknown language: `" + language + "` is not registered");
		high.configure({
			__emitter: HastEmitter,
			classPrefix: prefix
		});
		const result = high.highlight(value, {
			ignoreIllegals: true,
			language
		});
		/* c8 ignore next 5 */
		if (result.errorRaised) throw new Error("Could not highlight with `Highlight.js`", { cause: result.errorRaised });
		const root = result._emitter.root;
		const data = root.data;
		data.language = result.language;
		data.relevance = result.relevance;
		return root;
	}
	/**
	* Highlight `value` (code) and guess its programming language.
	*
	* @example
	*   ```js
	*   import {common, createLowlight} from 'lowlight'
	*
	*   const lowlight = createLowlight(common)
	*
	*   console.log(lowlight.highlightAuto('"hello, " + name + "!"'))
	*   ```
	*
	*   Yields:
	*
	*   ```js
	*   {type: 'root', children: [Array], data: {language: 'arduino', relevance: 2}}
	*   ```
	*
	* @param {string} value
	*   Code to highlight.
	* @param {Readonly<AutoOptions> | null | undefined} [options={}]
	*   Configuration (optional).
	* @returns {Root}
	*   Tree; with the following `data` fields: `language` (`string`), detected
	*   programming language name; `relevance` (`number`), how sure lowlight is
	*   that the given code is in the language.
	*/
	function highlightAuto(value, options) {
		const subset = (options || emptyOptions).subset || listLanguages();
		let index = -1;
		let relevance = 0;
		/** @type {Root | undefined} */
		let result;
		while (++index < subset.length) {
			const name = subset[index];
			if (!high.getLanguage(name)) continue;
			const current = highlight(name, value, options);
			if (current.data && current.data.relevance !== void 0 && current.data.relevance > relevance) {
				relevance = current.data.relevance;
				result = current;
			}
		}
		return result || {
			type: "root",
			children: [],
			data: {
				language: void 0,
				relevance
			}
		};
	}
	/**
	* List registered languages.
	*
	* @example
	*   ```js
	*   import {createLowlight} from 'lowlight'
	*   import markdown from 'highlight.js/lib/languages/markdown'
	*
	*   const lowlight = createLowlight()
	*
	*   console.log(lowlight.listLanguages()) // => []
	*
	*   lowlight.register({markdown})
	*
	*   console.log(lowlight.listLanguages()) // => ['markdown']
	*   ```
	*
	* @returns {Array<string>}
	*   Names of registered language.
	*/
	function listLanguages() {
		return high.listLanguages();
	}
	/**
	* Register languages.
	*
	* @example
	*   ```js
	*   import {createLowlight} from 'lowlight'
	*   import xml from 'highlight.js/lib/languages/xml'
	*
	*   const lowlight = createLowlight()
	*
	*   lowlight.register({xml})
	*
	*   // Note: `html` is an alias for `xml`.
	*   console.log(lowlight.highlight('html', '<em>Emphasis</em>'))
	*   ```
	*
	*   Yields:
	*
	*   ```js
	*   {type: 'root', children: [Array], data: {language: 'html', relevance: 2}}
	*   ```
	*
	* @overload
	* @param {Readonly<Record<string, LanguageFn>>} grammars
	* @returns {undefined}
	*
	* @overload
	* @param {string} name
	* @param {LanguageFn} grammar
	* @returns {undefined}
	*
	* @param {Readonly<Record<string, LanguageFn>> | string} grammarsOrName
	*   Grammars or programming language name.
	* @param {LanguageFn | undefined} [grammar]
	*   Grammar, if with name.
	* @returns {undefined}
	*   Nothing.
	*/
	function register(grammarsOrName, grammar) {
		if (typeof grammarsOrName === "string") high.registerLanguage(grammarsOrName, grammar);
		else {
			/** @type {string} */
			let name;
			for (name in grammarsOrName) if (Object.hasOwn(grammarsOrName, name)) high.registerLanguage(name, grammarsOrName[name]);
		}
	}
	/**
	* Register aliases.
	*
	* @example
	*   ```js
	*   import {createLowlight} from 'lowlight'
	*   import markdown from 'highlight.js/lib/languages/markdown'
	*
	*   const lowlight = createLowlight()
	*
	*   lowlight.register({markdown})
	*
	*   // lowlight.highlight('mdown', '<em>Emphasis</em>')
	*   // ^ would throw: Error: Unknown language: `mdown` is not registered
	*
	*   lowlight.registerAlias({markdown: ['mdown', 'mkdn', 'mdwn', 'ron']})
	*   lowlight.highlight('mdown', '<em>Emphasis</em>')
	*   // ^ Works!
	*   ```
	*
	* @overload
	* @param {Readonly<Record<string, ReadonlyArray<string> | string>>} aliases
	* @returns {undefined}
	*
	* @overload
	* @param {string} language
	* @param {ReadonlyArray<string> | string} alias
	* @returns {undefined}
	*
	* @param {Readonly<Record<string, ReadonlyArray<string> | string>> | string} aliasesOrName
	*   Map of programming language names to one or more aliases, or programming
	*   language name.
	* @param {ReadonlyArray<string> | string | undefined} [alias]
	*   One or more aliases for the programming language, if with `name`.
	* @returns {undefined}
	*   Nothing.
	*/
	function registerAlias(aliasesOrName, alias) {
		if (typeof aliasesOrName === "string") high.registerAliases(typeof alias === "string" ? alias : [...alias], { languageName: aliasesOrName });
		else {
			/** @type {string} */
			let key;
			for (key in aliasesOrName) if (Object.hasOwn(aliasesOrName, key)) {
				const aliases = aliasesOrName[key];
				high.registerAliases(typeof aliases === "string" ? aliases : [...aliases], { languageName: key });
			}
		}
	}
	/**
	* Check whether an alias or name is registered.
	*
	* @example
	*   ```js
	*   import {createLowlight} from 'lowlight'
	*   import javascript from 'highlight.js/lib/languages/javascript'
	*
	*   const lowlight = createLowlight({javascript})
	*
	*   console.log(lowlight.registered('funkyscript')) // => `false`
	*
	*   lowlight.registerAlias({javascript: 'funkyscript'})
	*   console.log(lowlight.registered('funkyscript')) // => `true`
	*   ```
	*
	* @param {string} aliasOrName
	*   Name of a language or alias for one.
	* @returns {boolean}
	*   Whether `aliasOrName` is registered.
	*/
	function registered(aliasOrName) {
		return Boolean(high.getLanguage(aliasOrName));
	}
}
/** @type {Emitter} */
var HastEmitter = class {
	/**
	* @param {Readonly<HljsOptions>} options
	*   Configuration.
	* @returns
	*   Instance.
	*/
	constructor(options) {
		/** @type {HljsOptions} */
		this.options = options;
		/** @type {Root} */
		this.root = {
			type: "root",
			children: [],
			data: {
				language: void 0,
				relevance: 0
			}
		};
		/** @type {[Root, ...Array<Element>]} */
		this.stack = [this.root];
	}
	/**
	* @param {string} value
	*   Text to add.
	* @returns {undefined}
	*   Nothing.
	*
	*/
	addText(value) {
		if (value === "") return;
		const current = this.stack[this.stack.length - 1];
		const tail = current.children[current.children.length - 1];
		if (tail && tail.type === "text") tail.value += value;
		else current.children.push({
			type: "text",
			value
		});
	}
	/**
	*
	* @param {unknown} rawName
	*   Name to add.
	* @returns {undefined}
	*   Nothing.
	*/
	startScope(rawName) {
		this.openNode(String(rawName));
	}
	/**
	* @returns {undefined}
	*   Nothing.
	*/
	endScope() {
		this.closeNode();
	}
	/**
	* @param {HastEmitter} other
	*   Other emitter.
	* @param {string} name
	*   Name of the sublanguage.
	* @returns {undefined}
	*   Nothing.
	*/
	__addSublanguage(other, name) {
		const current = this.stack[this.stack.length - 1];
		const results = other.root.children;
		if (name) current.children.push({
			type: "element",
			tagName: "span",
			properties: { className: [name] },
			children: results
		});
		else current.children.push(...results);
	}
	/**
	* @param {string} name
	*   Name to add.
	* @returns {undefined}
	*   Nothing.
	*/
	openNode(name) {
		const self = this;
		const className = name.split(".").map(function(d, i) {
			return i ? d + "_".repeat(i) : self.options.classPrefix + d;
		});
		const current = this.stack[this.stack.length - 1];
		/** @type {Element} */
		const child = {
			type: "element",
			tagName: "span",
			properties: { className },
			children: []
		};
		current.children.push(child);
		this.stack.push(child);
	}
	/**
	* @returns {undefined}
	*   Nothing.
	*/
	closeNode() {
		this.stack.pop();
	}
	/**
	* @returns {undefined}
	*   Nothing.
	*/
	finalize() {}
	/**
	* @returns {string}
	*   Nothing.
	*/
	toHTML() {
		return "";
	}
};
//#endregion
export { grammars as n, createLowlight as t };
