// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

class XpathEditor {
    static get _goodAttributes() {
        return ['class', 'text', 'name', 'myaction', 'accessiblename', 'text.key', 'accessiblename.key']
    }

    constructor(element) {
        this.element = element
        this.editor = element.getElementsByTagName("xpathEditor")[0]
        this.xpathTextField = this.editor.getElementsByTagName("input")[0]
        this.xpathLabel = this.editor.getElementsByTagName("label")[0]
        this.editor.setAttribute("class", this.editor.getAttribute("class").replace("hidden", ""))
        this.generator = new SmartXpathLocatorsGenerator()
    }

    generatePath() {
        const xpaths = this.generator.generate(this.element).sort(
            function (a, b) {
                if (a.priority === b.priority) {
                    return a.xpath.length - b.xpath.length
                }
                return a.priority - b.priority
            })
        console.log("..............")
        console.log("Unique Locators for " + this.element.getAttribute("class"))
        xpaths.map(locator => console.log(locator.xpath))
        console.log("..............")
        const xpath = xpaths[0]
        if (xpath) {
            this.xpathTextField.value = xpath.xpath
        } else {
            this.xpathTextField.value = `//${this.element.tagName.toLowerCase()}${this.generator._formatAttributes(this.element)}`;
        }
        this.checkXpath()
    }

    checkXpath() {
        try {
            const xpath = this.xpathTextField.value
            const elementsFoundByXpath = this._countElementByXpath(xpath)

            if (elementsFoundByXpath !== 1) {
                this.xpathTextField.setAttribute("class", "badXpath")
                this.xpathLabel.textContent = `${elementsFoundByXpath} matches`
            } else {
                const result = document.evaluate(xpath, document, null, XPathResult.ANY_TYPE, null).iterateNext();
                if (result === this.element) {
                    this.xpathTextField.setAttribute("class", "goodXpath")
                    this.xpathLabel.textContent = `1 match!`
                } else {
                    this.xpathTextField.setAttribute("class", "badXpath")
                    this.xpathLabel.textContent = `matched wrong element`
                }
            }
        } catch (e) {
            this.xpathTextField.setAttribute("class", "invalidXpath")
            this.xpathLabel.textContent = `invalid xpath`
        }
    }

    _countElementByXpath(xpath) {
        return document.evaluate(`count(${xpath})`, document, null, XPathResult.ANY_TYPE, null).numberValue;
    }
}

class PrioritizedXpath {
    constructor(xpath, priority) {
        this.xpath = xpath
        this.priority = priority
    }
}


class SmartXpathLocatorsGenerator {
    constructor() {
        this.generators = [
            // class
            (element) => {
                if (element.getAttribute("class")) {
                    return [new PrioritizedXpath(`//div[@class='${element.getAttribute("class")}']`, 2)]
                }
            },
            // title.key
            (element) => {
                if (element.getAttribute("title.key")) {
                    const keys = element.getAttribute("title.key")
                    if (keys.includes(" ")) {
                        return keys.split(" ").map((key => new PrioritizedXpath(`//*[contains(@title.key, '${key}')]`, 1)))
                    }
                    return [new PrioritizedXpath(`//*[@title.key='${element.getAttribute("title.key")}']`, 1)]
                }
            },
            // visible text
            (element) => {
                if (element.getAttribute("visible_text")) {
                    return element.getAttribute("visible_text").split("||")
                        .map((it) => new PrioritizedXpath(`//div[contains(@visible_text, '${it.trim()}')]`, 4))
                }
            },
            // visible text key
            (element) => {
                if (element.getAttribute("visible_text_keys")) {
                    return element.getAttribute("visible_text_keys").split("||")
                        .flatMap(keys => keys.split(" "))
                        .map((it) => new PrioritizedXpath(`//div[contains(@visible_text_keys, '${it.trim()}')]`, 3))
                }
            },
            //all attributes
            (element) => {
                return Array.prototype.slice.call(element.attributes)
                    .filter((a) => a.nodeValue.length > 0)
                    .flatMap((a) => {
                        let priority = 3
                        if (a.nodeValue.length > 30) priority = 4
                        if (a.nodeName.endsWith(".key")) {
                            priority = 2
                            if (a.nodeValue.includes(" ")) {
                                return a.nodeValue.split(" ").map(key => new PrioritizedXpath(`//${element.tagName.toLowerCase()}[contains(@${a.nodeName}, '${key}')]`, priority))
                            }
                        }
                        return [new PrioritizedXpath(`//${element.tagName.toLowerCase()}[@${a.nodeName}='${a.nodeValue}']`, priority)]
                    })
            },
            // combination of good attributes
            (element) => {
                return [new PrioritizedXpath(`//${element.tagName.toLowerCase()}${this._formatAttributes(element)}`, 3)]
            },
            (element) => {
                const result = []
                Array.prototype.slice.call(element.attributes).forEach((a) => {
                    const attrName = a.nodeName
                    a.nodeValue.split(" ")
                        .filter((v) => v.length > 0)
                        .forEach((v) => {
                            result.push(new PrioritizedXpath(`//${element.tagName.toLowerCase()}[contains(@${attrName}, '${v}')]`, 4))
                        })
                })
                return result
            }
        ]
    }

    _collect(element) {
        return this.generators
            .flatMap((generator) => generator(element))
            .filter((x) => x)
    }

    generate(element) {
        const allXpaths = this._collect(element)
        let xpaths = allXpaths.filter((xpath) => this._isGoodAndUnique(xpath, element))
        if (xpaths.length === 0) {
            const uniqueChildrenXpath = this._findUniqueChildren(element)
            if (uniqueChildrenXpath) {
                xpaths = allXpaths
                    .map((x) => new PrioritizedXpath(x.xpath + `[.${uniqueChildrenXpath.xpath}]`, x.priority))
                    .filter((xpath) => this._isGoodAndUnique(xpath, element))
            }
        }
        if (xpaths.length === 0) {
            const uniqueParentXpath = this._findUniqueParent(element)
            if (uniqueParentXpath) {
                xpaths = allXpaths
                    .map((x) => new PrioritizedXpath(uniqueParentXpath.xpath + x.xpath, x.priority))
                    .filter((xpath) => this._isGoodAndUnique(xpath, element))
                xpaths = xpaths.flatMap((x) => this._generateMiddleSkippedPaths(x))
                    .filter((xpath) => this._isGoodAndUnique(xpath, element))
            }
        }
        return xpaths
    }

    _findUniqueParent(element) {
        let parent = element.parentNode
        while (parent) {
            const uniqueParent = this.generate(parent)
            if (uniqueParent.length > 0) {
                return uniqueParent.sort(
                    function (a, b) {
                        if (a.priority === b.priority) {
                            return a.xpath.length - b.xpath.length
                        }
                        return a.priority - b.priority
                    }
                )[0]
            }
            parent = parent.parentNode
        }
    }

    _findUniqueChildren(element) {
        return Array.from(element.getElementsByTagName('div'))
            .flatMap((el) => this._collect(el).filter((x) => this._isGoodAndUnique(x, el)))
            .sort(
                function (a, b) {
                    if (a.priority === b.priority) {
                        return a.xpath.length - b.xpath.length
                    }
                    return a.priority - b.priority
                })[0]
    }

    _generateMiddleSkippedPaths(x) {
        const parts = x.xpath.split("//").filter((it) => it.length > 0)

        const result = []
        for (let n = 0; n < Math.pow(2, parts.length - 2); n++) {
            let middle = []
            for (let p = 0; p < parts.length - 2; p++) {
                if ((n >> p) % 2 === 1) {
                    middle.push(parts[p + 1])
                }
            }
            let collectedXpath = "//" + parts[0]
            if (middle.length > 0) {
                collectedXpath = collectedXpath + "//"
            }
            collectedXpath = collectedXpath + middle.join("//")
            collectedXpath = collectedXpath + "//" + parts[parts.length - 1]
            result.push(new PrioritizedXpath(collectedXpath, x.priority))
        }
        return result
    }

    _isGoodAndUnique(x, element) {
        if (/\r|\n/.exec(x.xpath)) {
            return false
        }
        if (/[a-zA-Z;$0-9]{2,30}@[a-z0-9A-Z]{3,20}/.exec(x.xpath)) {
            return false
        }
        if (/@onclick/.exec(x.xpath)) {
            return false
        }
        if (/[=,][ ]?'[a-zA-Z$.]{0,200}[0-9]{1,5}'/.exec(x.xpath)) {
            return false
        }
        if (/[=,][ ]?'[^a-z^A-Z]{1,20}'/.exec(x.xpath)) {
            return false
        }
        try {
            return document.evaluate("count(" + x.xpath + ")", document, null, XPathResult.ANY_TYPE, null).numberValue === 1
                && document.evaluate(x.xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue === element
        } catch (e) {
            return false
        }
    }

    _formatAttributes(element) {
        const attributes = element.attributes

        if (attributes.length === 0) {
            return ""
        }
        if (attributes.length === 1) {
            return `[@${attributes[0].name}='${attributes[0].value}']`
        }
        let result = ""
        for (let i = 0; i < attributes.length; i++) {
            if (XpathEditor._goodAttributes.includes(attributes[i].name) && attributes[i].value.length > 1 && !/[']/.exec(attributes[i].value)) {
                if (result.length === 0) {
                    result = `[@${attributes[i].name}='${attributes[i].value}'`
                } else {
                    result = `${result} and @${attributes[i].name}='${attributes[i].value}'`
                }
            }
        }
        if (result.length > 0) {
            result = `${result}]`
        }
        return result
    }
}
