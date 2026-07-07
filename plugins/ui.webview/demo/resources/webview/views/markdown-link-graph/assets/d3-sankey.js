import { n as min, r as max, t as sum } from "./d3-array.js";
import { t as path } from "./d3-path.js";
function targetDepth(d) {
	return d.target.depth;
}
function left(node) {
	return node.depth;
}
function right(node, n) {
	return n - 1 - node.height;
}
function justify(node, n) {
	return node.sourceLinks.length ? node.depth : n - 1;
}
function center(node) {
	return node.targetLinks.length ? node.depth : node.sourceLinks.length ? min(node.sourceLinks, targetDepth) - 1 : 0;
}
function constant(x) {
	return function() {
		return x;
	};
}
function ascendingSourceBreadth(a, b) {
	return ascendingBreadth(a.source, b.source) || a.index - b.index;
}
function ascendingTargetBreadth(a, b) {
	return ascendingBreadth(a.target, b.target) || a.index - b.index;
}
function ascendingBreadth(a, b) {
	return a.y0 - b.y0;
}
function value(d) {
	return d.value;
}
function defaultId(d) {
	return d.index;
}
function defaultNodes(graph) {
	return graph.nodes;
}
function defaultLinks(graph) {
	return graph.links;
}
function find(nodeById, id) {
	const node = nodeById.get(id);
	if (!node) throw new Error("missing: " + id);
	return node;
}
function computeLinkBreadths({ nodes }) {
	for (const node of nodes) {
		let y0 = node.y0;
		let y1 = y0;
		for (const link of node.sourceLinks) {
			link.y0 = y0 + link.width / 2;
			y0 += link.width;
		}
		for (const link of node.targetLinks) {
			link.y1 = y1 + link.width / 2;
			y1 += link.width;
		}
	}
}
function Sankey() {
	let x0 = 0, y0 = 0, x1 = 1, y1 = 1;
	let dx = 24;
	let dy = 8, py;
	let id = defaultId;
	let align = justify;
	let sort;
	let linkSort;
	let nodes = defaultNodes;
	let links = defaultLinks;
	let iterations = 6;
	function sankey() {
		const graph = {
			nodes: nodes.apply(null, arguments),
			links: links.apply(null, arguments)
		};
		computeNodeLinks(graph);
		computeNodeValues(graph);
		computeNodeDepths(graph);
		computeNodeHeights(graph);
		computeNodeBreadths(graph);
		computeLinkBreadths(graph);
		return graph;
	}
	sankey.update = function(graph) {
		computeLinkBreadths(graph);
		return graph;
	};
	sankey.nodeId = function(_) {
		return arguments.length ? (id = typeof _ === "function" ? _ : constant(_), sankey) : id;
	};
	sankey.nodeAlign = function(_) {
		return arguments.length ? (align = typeof _ === "function" ? _ : constant(_), sankey) : align;
	};
	sankey.nodeSort = function(_) {
		return arguments.length ? (sort = _, sankey) : sort;
	};
	sankey.nodeWidth = function(_) {
		return arguments.length ? (dx = +_, sankey) : dx;
	};
	sankey.nodePadding = function(_) {
		return arguments.length ? (dy = py = +_, sankey) : dy;
	};
	sankey.nodes = function(_) {
		return arguments.length ? (nodes = typeof _ === "function" ? _ : constant(_), sankey) : nodes;
	};
	sankey.links = function(_) {
		return arguments.length ? (links = typeof _ === "function" ? _ : constant(_), sankey) : links;
	};
	sankey.linkSort = function(_) {
		return arguments.length ? (linkSort = _, sankey) : linkSort;
	};
	sankey.size = function(_) {
		return arguments.length ? (x0 = y0 = 0, x1 = +_[0], y1 = +_[1], sankey) : [x1 - x0, y1 - y0];
	};
	sankey.extent = function(_) {
		return arguments.length ? (x0 = +_[0][0], x1 = +_[1][0], y0 = +_[0][1], y1 = +_[1][1], sankey) : [[x0, y0], [x1, y1]];
	};
	sankey.iterations = function(_) {
		return arguments.length ? (iterations = +_, sankey) : iterations;
	};
	function computeNodeLinks({ nodes, links }) {
		for (const [i, node] of nodes.entries()) {
			node.index = i;
			node.sourceLinks = [];
			node.targetLinks = [];
		}
		const nodeById = new Map(nodes.map((d, i) => [id(d, i, nodes), d]));
		for (const [i, link] of links.entries()) {
			link.index = i;
			let { source, target } = link;
			if (typeof source !== "object") source = link.source = find(nodeById, source);
			if (typeof target !== "object") target = link.target = find(nodeById, target);
			source.sourceLinks.push(link);
			target.targetLinks.push(link);
		}
		if (linkSort != null) for (const { sourceLinks, targetLinks } of nodes) {
			sourceLinks.sort(linkSort);
			targetLinks.sort(linkSort);
		}
	}
	function computeNodeValues({ nodes }) {
		for (const node of nodes) node.value = node.fixedValue === void 0 ? Math.max(sum(node.sourceLinks, value), sum(node.targetLinks, value)) : node.fixedValue;
	}
	function computeNodeDepths({ nodes }) {
		const n = nodes.length;
		let current = new Set(nodes);
		let next = /* @__PURE__ */ new Set();
		let x = 0;
		while (current.size) {
			for (const node of current) {
				node.depth = x;
				for (const { target } of node.sourceLinks) next.add(target);
			}
			if (++x > n) throw new Error("circular link");
			current = next;
			next = /* @__PURE__ */ new Set();
		}
	}
	function computeNodeHeights({ nodes }) {
		const n = nodes.length;
		let current = new Set(nodes);
		let next = /* @__PURE__ */ new Set();
		let x = 0;
		while (current.size) {
			for (const node of current) {
				node.height = x;
				for (const { source } of node.targetLinks) next.add(source);
			}
			if (++x > n) throw new Error("circular link");
			current = next;
			next = /* @__PURE__ */ new Set();
		}
	}
	function computeNodeLayers({ nodes }) {
		const x = max(nodes, (d) => d.depth) + 1;
		const kx = (x1 - x0 - dx) / (x - 1);
		const columns = new Array(x);
		for (const node of nodes) {
			const i = Math.max(0, Math.min(x - 1, Math.floor(align.call(null, node, x))));
			node.layer = i;
			node.x0 = x0 + i * kx;
			node.x1 = node.x0 + dx;
			if (columns[i]) columns[i].push(node);
			else columns[i] = [node];
		}
		if (sort) for (const column of columns) column.sort(sort);
		return columns;
	}
	function initializeNodeBreadths(columns) {
		const ky = min(columns, (c) => (y1 - y0 - (c.length - 1) * py) / sum(c, value));
		for (const nodes of columns) {
			let y = y0;
			for (const node of nodes) {
				node.y0 = y;
				node.y1 = y + node.value * ky;
				y = node.y1 + py;
				for (const link of node.sourceLinks) link.width = link.value * ky;
			}
			y = (y1 - y + py) / (nodes.length + 1);
			for (let i = 0; i < nodes.length; ++i) {
				const node = nodes[i];
				node.y0 += y * (i + 1);
				node.y1 += y * (i + 1);
			}
			reorderLinks(nodes);
		}
	}
	function computeNodeBreadths(graph) {
		const columns = computeNodeLayers(graph);
		py = Math.min(dy, (y1 - y0) / (max(columns, (c) => c.length) - 1));
		initializeNodeBreadths(columns);
		for (let i = 0; i < iterations; ++i) {
			const alpha = Math.pow(.99, i);
			const beta = Math.max(1 - alpha, (i + 1) / iterations);
			relaxRightToLeft(columns, alpha, beta);
			relaxLeftToRight(columns, alpha, beta);
		}
	}
	function relaxLeftToRight(columns, alpha, beta) {
		for (let i = 1, n = columns.length; i < n; ++i) {
			const column = columns[i];
			for (const target of column) {
				let y = 0;
				let w = 0;
				for (const { source, value } of target.targetLinks) {
					let v = value * (target.layer - source.layer);
					y += targetTop(source, target) * v;
					w += v;
				}
				if (!(w > 0)) continue;
				let dy = (y / w - target.y0) * alpha;
				target.y0 += dy;
				target.y1 += dy;
				reorderNodeLinks(target);
			}
			if (sort === void 0) column.sort(ascendingBreadth);
			resolveCollisions(column, beta);
		}
	}
	function relaxRightToLeft(columns, alpha, beta) {
		for (let i = columns.length - 2; i >= 0; --i) {
			const column = columns[i];
			for (const source of column) {
				let y = 0;
				let w = 0;
				for (const { target, value } of source.sourceLinks) {
					let v = value * (target.layer - source.layer);
					y += sourceTop(source, target) * v;
					w += v;
				}
				if (!(w > 0)) continue;
				let dy = (y / w - source.y0) * alpha;
				source.y0 += dy;
				source.y1 += dy;
				reorderNodeLinks(source);
			}
			if (sort === void 0) column.sort(ascendingBreadth);
			resolveCollisions(column, beta);
		}
	}
	function resolveCollisions(nodes, alpha) {
		const i = nodes.length >> 1;
		const subject = nodes[i];
		resolveCollisionsBottomToTop(nodes, subject.y0 - py, i - 1, alpha);
		resolveCollisionsTopToBottom(nodes, subject.y1 + py, i + 1, alpha);
		resolveCollisionsBottomToTop(nodes, y1, nodes.length - 1, alpha);
		resolveCollisionsTopToBottom(nodes, y0, 0, alpha);
	}
	function resolveCollisionsTopToBottom(nodes, y, i, alpha) {
		for (; i < nodes.length; ++i) {
			const node = nodes[i];
			const dy = (y - node.y0) * alpha;
			if (dy > 1e-6) node.y0 += dy, node.y1 += dy;
			y = node.y1 + py;
		}
	}
	function resolveCollisionsBottomToTop(nodes, y, i, alpha) {
		for (; i >= 0; --i) {
			const node = nodes[i];
			const dy = (node.y1 - y) * alpha;
			if (dy > 1e-6) node.y0 -= dy, node.y1 -= dy;
			y = node.y0 - py;
		}
	}
	function reorderNodeLinks({ sourceLinks, targetLinks }) {
		if (linkSort === void 0) {
			for (const { source: { sourceLinks } } of targetLinks) sourceLinks.sort(ascendingTargetBreadth);
			for (const { target: { targetLinks } } of sourceLinks) targetLinks.sort(ascendingSourceBreadth);
		}
	}
	function reorderLinks(nodes) {
		if (linkSort === void 0) for (const { sourceLinks, targetLinks } of nodes) {
			sourceLinks.sort(ascendingTargetBreadth);
			targetLinks.sort(ascendingSourceBreadth);
		}
	}
	function targetTop(source, target) {
		let y = source.y0 - (source.sourceLinks.length - 1) * py / 2;
		for (const { target: node, width } of source.sourceLinks) {
			if (node === target) break;
			y += width + py;
		}
		for (const { source: node, width } of target.targetLinks) {
			if (node === source) break;
			y -= width;
		}
		return y;
	}
	function sourceTop(source, target) {
		let y = target.y0 - (target.targetLinks.length - 1) * py / 2;
		for (const { source: node, width } of target.targetLinks) {
			if (node === source) break;
			y += width + py;
		}
		for (const { target: node, width } of source.sourceLinks) {
			if (node === target) break;
			y -= width;
		}
		return y;
	}
	return sankey;
}
function constant_default(x) {
	return function constant() {
		return x;
	};
}
function x(p) {
	return p[0];
}
function y(p) {
	return p[1];
}
var slice = Array.prototype.slice;
function linkSource(d) {
	return d.source;
}
function linkTarget(d) {
	return d.target;
}
function link(curve) {
	var source = linkSource, target = linkTarget, x$1 = x, y$1 = y, context = null;
	function link() {
		var buffer, argv = slice.call(arguments), s = source.apply(this, argv), t = target.apply(this, argv);
		if (!context) context = buffer = path();
		curve(context, +x$1.apply(this, (argv[0] = s, argv)), +y$1.apply(this, argv), +x$1.apply(this, (argv[0] = t, argv)), +y$1.apply(this, argv));
		if (buffer) return context = null, buffer + "" || null;
	}
	link.source = function(_) {
		return arguments.length ? (source = _, link) : source;
	};
	link.target = function(_) {
		return arguments.length ? (target = _, link) : target;
	};
	link.x = function(_) {
		return arguments.length ? (x$1 = typeof _ === "function" ? _ : constant_default(+_), link) : x$1;
	};
	link.y = function(_) {
		return arguments.length ? (y$1 = typeof _ === "function" ? _ : constant_default(+_), link) : y$1;
	};
	link.context = function(_) {
		return arguments.length ? (context = _ == null ? null : _, link) : context;
	};
	return link;
}
function curveHorizontal(context, x0, y0, x1, y1) {
	context.moveTo(x0, y0);
	context.bezierCurveTo(x0 = (x0 + x1) / 2, y0, x0, y1, x1, y1);
}
function linkHorizontal() {
	return link(curveHorizontal);
}
function horizontalSource(d) {
	return [d.source.x1, d.y0];
}
function horizontalTarget(d) {
	return [d.target.x0, d.y1];
}
function sankeyLinkHorizontal_default() {
	return linkHorizontal().source(horizontalSource).target(horizontalTarget);
}
export { left as a, justify as i, Sankey as n, right as o, center as r, sankeyLinkHorizontal_default as t };
