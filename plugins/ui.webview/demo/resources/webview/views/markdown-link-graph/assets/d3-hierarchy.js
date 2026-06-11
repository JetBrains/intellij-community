//#region node_modules/d3-hierarchy/src/hierarchy/count.js
function count(node) {
	var sum = 0, children = node.children, i = children && children.length;
	if (!i) sum = 1;
	else while (--i >= 0) sum += children[i].value;
	node.value = sum;
}
function count_default() {
	return this.eachAfter(count);
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/each.js
function each_default(callback, that) {
	let index = -1;
	for (const node of this) callback.call(that, node, ++index, this);
	return this;
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/eachBefore.js
function eachBefore_default(callback, that) {
	var node = this, nodes = [node], children, i, index = -1;
	while (node = nodes.pop()) {
		callback.call(that, node, ++index, this);
		if (children = node.children) for (i = children.length - 1; i >= 0; --i) nodes.push(children[i]);
	}
	return this;
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/eachAfter.js
function eachAfter_default(callback, that) {
	var node = this, nodes = [node], next = [], children, i, n, index = -1;
	while (node = nodes.pop()) {
		next.push(node);
		if (children = node.children) for (i = 0, n = children.length; i < n; ++i) nodes.push(children[i]);
	}
	while (node = next.pop()) callback.call(that, node, ++index, this);
	return this;
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/find.js
function find_default(callback, that) {
	let index = -1;
	for (const node of this) if (callback.call(that, node, ++index, this)) return node;
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/sum.js
function sum_default(value) {
	return this.eachAfter(function(node) {
		var sum = +value(node.data) || 0, children = node.children, i = children && children.length;
		while (--i >= 0) sum += children[i].value;
		node.value = sum;
	});
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/sort.js
function sort_default(compare) {
	return this.eachBefore(function(node) {
		if (node.children) node.children.sort(compare);
	});
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/path.js
function path_default(end) {
	var start = this, ancestor = leastCommonAncestor(start, end), nodes = [start];
	while (start !== ancestor) {
		start = start.parent;
		nodes.push(start);
	}
	var k = nodes.length;
	while (end !== ancestor) {
		nodes.splice(k, 0, end);
		end = end.parent;
	}
	return nodes;
}
function leastCommonAncestor(a, b) {
	if (a === b) return a;
	var aNodes = a.ancestors(), bNodes = b.ancestors(), c = null;
	a = aNodes.pop();
	b = bNodes.pop();
	while (a === b) {
		c = a;
		a = aNodes.pop();
		b = bNodes.pop();
	}
	return c;
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/ancestors.js
function ancestors_default() {
	var node = this, nodes = [node];
	while (node = node.parent) nodes.push(node);
	return nodes;
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/descendants.js
function descendants_default() {
	return Array.from(this);
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/leaves.js
function leaves_default() {
	var leaves = [];
	this.eachBefore(function(node) {
		if (!node.children) leaves.push(node);
	});
	return leaves;
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/links.js
function links_default() {
	var root = this, links = [];
	root.each(function(node) {
		if (node !== root) links.push({
			source: node.parent,
			target: node
		});
	});
	return links;
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/iterator.js
function* iterator_default() {
	var node = this, current, next = [node], children, i, n;
	do {
		current = next.reverse(), next = [];
		while (node = current.pop()) {
			yield node;
			if (children = node.children) for (i = 0, n = children.length; i < n; ++i) next.push(children[i]);
		}
	} while (next.length);
}
//#endregion
//#region node_modules/d3-hierarchy/src/hierarchy/index.js
function hierarchy(data, children) {
	if (data instanceof Map) {
		data = [void 0, data];
		if (children === void 0) children = mapChildren;
	} else if (children === void 0) children = objectChildren;
	var root = new Node(data), node, nodes = [root], child, childs, i, n;
	while (node = nodes.pop()) if ((childs = children(node.data)) && (n = (childs = Array.from(childs)).length)) {
		node.children = childs;
		for (i = n - 1; i >= 0; --i) {
			nodes.push(child = childs[i] = new Node(childs[i]));
			child.parent = node;
			child.depth = node.depth + 1;
		}
	}
	return root.eachBefore(computeHeight);
}
function node_copy() {
	return hierarchy(this).eachBefore(copyData);
}
function objectChildren(d) {
	return d.children;
}
function mapChildren(d) {
	return Array.isArray(d) ? d[1] : null;
}
function copyData(node) {
	if (node.data.value !== void 0) node.value = node.data.value;
	node.data = node.data.data;
}
function computeHeight(node) {
	var height = 0;
	do
		node.height = height;
	while ((node = node.parent) && node.height < ++height);
}
function Node(data) {
	this.data = data;
	this.depth = this.height = 0;
	this.parent = null;
}
Node.prototype = hierarchy.prototype = {
	constructor: Node,
	count: count_default,
	each: each_default,
	eachAfter: eachAfter_default,
	eachBefore: eachBefore_default,
	find: find_default,
	sum: sum_default,
	sort: sort_default,
	path: path_default,
	ancestors: ancestors_default,
	descendants: descendants_default,
	leaves: leaves_default,
	links: links_default,
	copy: node_copy,
	[Symbol.iterator]: iterator_default
};
//#endregion
//#region node_modules/d3-hierarchy/src/accessors.js
function required(f) {
	if (typeof f !== "function") throw new Error();
	return f;
}
//#endregion
//#region node_modules/d3-hierarchy/src/constant.js
function constantZero() {
	return 0;
}
function constant_default(x) {
	return function() {
		return x;
	};
}
//#endregion
//#region node_modules/d3-hierarchy/src/treemap/round.js
function round_default(node) {
	node.x0 = Math.round(node.x0);
	node.y0 = Math.round(node.y0);
	node.x1 = Math.round(node.x1);
	node.y1 = Math.round(node.y1);
}
//#endregion
//#region node_modules/d3-hierarchy/src/treemap/dice.js
function dice_default(parent, x0, y0, x1, y1) {
	var nodes = parent.children, node, i = -1, n = nodes.length, k = parent.value && (x1 - x0) / parent.value;
	while (++i < n) {
		node = nodes[i], node.y0 = y0, node.y1 = y1;
		node.x0 = x0, node.x1 = x0 += node.value * k;
	}
}
//#endregion
//#region node_modules/d3-hierarchy/src/treemap/slice.js
function slice_default(parent, x0, y0, x1, y1) {
	var nodes = parent.children, node, i = -1, n = nodes.length, k = parent.value && (y1 - y0) / parent.value;
	while (++i < n) {
		node = nodes[i], node.x0 = x0, node.x1 = x1;
		node.y0 = y0, node.y1 = y0 += node.value * k;
	}
}
//#endregion
//#region node_modules/d3-hierarchy/src/treemap/squarify.js
var phi = (1 + Math.sqrt(5)) / 2;
function squarifyRatio(ratio, parent, x0, y0, x1, y1) {
	var rows = [], nodes = parent.children, row, nodeValue, i0 = 0, i1 = 0, n = nodes.length, dx, dy, value = parent.value, sumValue, minValue, maxValue, newRatio, minRatio, alpha, beta;
	while (i0 < n) {
		dx = x1 - x0, dy = y1 - y0;
		do
			sumValue = nodes[i1++].value;
		while (!sumValue && i1 < n);
		minValue = maxValue = sumValue;
		alpha = Math.max(dy / dx, dx / dy) / (value * ratio);
		beta = sumValue * sumValue * alpha;
		minRatio = Math.max(maxValue / beta, beta / minValue);
		for (; i1 < n; ++i1) {
			sumValue += nodeValue = nodes[i1].value;
			if (nodeValue < minValue) minValue = nodeValue;
			if (nodeValue > maxValue) maxValue = nodeValue;
			beta = sumValue * sumValue * alpha;
			newRatio = Math.max(maxValue / beta, beta / minValue);
			if (newRatio > minRatio) {
				sumValue -= nodeValue;
				break;
			}
			minRatio = newRatio;
		}
		rows.push(row = {
			value: sumValue,
			dice: dx < dy,
			children: nodes.slice(i0, i1)
		});
		if (row.dice) dice_default(row, x0, y0, x1, value ? y0 += dy * sumValue / value : y1);
		else slice_default(row, x0, y0, value ? x0 += dx * sumValue / value : x1, y1);
		value -= sumValue, i0 = i1;
	}
	return rows;
}
var squarify_default = (function custom(ratio) {
	function squarify(parent, x0, y0, x1, y1) {
		squarifyRatio(ratio, parent, x0, y0, x1, y1);
	}
	squarify.ratio = function(x) {
		return custom((x = +x) > 1 ? x : 1);
	};
	return squarify;
})(phi);
//#endregion
//#region node_modules/d3-hierarchy/src/treemap/index.js
function treemap_default() {
	var tile = squarify_default, round = false, dx = 1, dy = 1, paddingStack = [0], paddingInner = constantZero, paddingTop = constantZero, paddingRight = constantZero, paddingBottom = constantZero, paddingLeft = constantZero;
	function treemap(root) {
		root.x0 = root.y0 = 0;
		root.x1 = dx;
		root.y1 = dy;
		root.eachBefore(positionNode);
		paddingStack = [0];
		if (round) root.eachBefore(round_default);
		return root;
	}
	function positionNode(node) {
		var p = paddingStack[node.depth], x0 = node.x0 + p, y0 = node.y0 + p, x1 = node.x1 - p, y1 = node.y1 - p;
		if (x1 < x0) x0 = x1 = (x0 + x1) / 2;
		if (y1 < y0) y0 = y1 = (y0 + y1) / 2;
		node.x0 = x0;
		node.y0 = y0;
		node.x1 = x1;
		node.y1 = y1;
		if (node.children) {
			p = paddingStack[node.depth + 1] = paddingInner(node) / 2;
			x0 += paddingLeft(node) - p;
			y0 += paddingTop(node) - p;
			x1 -= paddingRight(node) - p;
			y1 -= paddingBottom(node) - p;
			if (x1 < x0) x0 = x1 = (x0 + x1) / 2;
			if (y1 < y0) y0 = y1 = (y0 + y1) / 2;
			tile(node, x0, y0, x1, y1);
		}
	}
	treemap.round = function(x) {
		return arguments.length ? (round = !!x, treemap) : round;
	};
	treemap.size = function(x) {
		return arguments.length ? (dx = +x[0], dy = +x[1], treemap) : [dx, dy];
	};
	treemap.tile = function(x) {
		return arguments.length ? (tile = required(x), treemap) : tile;
	};
	treemap.padding = function(x) {
		return arguments.length ? treemap.paddingInner(x).paddingOuter(x) : treemap.paddingInner();
	};
	treemap.paddingInner = function(x) {
		return arguments.length ? (paddingInner = typeof x === "function" ? x : constant_default(+x), treemap) : paddingInner;
	};
	treemap.paddingOuter = function(x) {
		return arguments.length ? treemap.paddingTop(x).paddingRight(x).paddingBottom(x).paddingLeft(x) : treemap.paddingTop();
	};
	treemap.paddingTop = function(x) {
		return arguments.length ? (paddingTop = typeof x === "function" ? x : constant_default(+x), treemap) : paddingTop;
	};
	treemap.paddingRight = function(x) {
		return arguments.length ? (paddingRight = typeof x === "function" ? x : constant_default(+x), treemap) : paddingRight;
	};
	treemap.paddingBottom = function(x) {
		return arguments.length ? (paddingBottom = typeof x === "function" ? x : constant_default(+x), treemap) : paddingBottom;
	};
	treemap.paddingLeft = function(x) {
		return arguments.length ? (paddingLeft = typeof x === "function" ? x : constant_default(+x), treemap) : paddingLeft;
	};
	return treemap;
}
//#endregion
export { hierarchy as n, treemap_default as t };
