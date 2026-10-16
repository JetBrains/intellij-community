// The grammar of a hand-laid diagram (ADR 0203). An author places each node on a grid of columns
// and rows, and declares the edges and the numbered steps. Nothing is auto-laid: the renderer only
// converts the grid to pixels (src/graph/diagramLayout.ts).
//
// The kernel shares it among scripts/diagramFiles.ts (node: reads the YAML files), scripts/harvestDocs.ts (node:
// resolves the references), and the markdown renderer of a site (browser: parses the ```diagram fence), so this file
// imports nothing from node. The node kinds and the planned values come from the site config (siteConfig.ts).

import { fenceEntries } from './fence.ts'

/** A node kind of the site config (`diagram.kinds`). */
export type DiagramNodeKind = string

export const DIAGRAM_SIDES = ['top', 'right', 'bottom', 'left'] as const

export type DiagramSide = typeof DIAGRAM_SIDES[number]

/** The lane colors; each one is a `.layer-*` class of styles/canvas.css. */
export const DIAGRAM_COLORS = ['indigo', 'sky', 'blue', 'green', 'amber', 'red', 'neutral'] as const

export type DiagramColor = typeof DIAGRAM_COLORS[number]

/** A planned value of the site config (`diagram.planned`): the phase that builds a planned part, or an open decision. */
export type DiagramPlanned = string

/** The node kinds and the planned values that a site accepts (`diagramGrammar` of siteConfig.ts). */
export interface DiagramGrammar {
  kinds: readonly string[]
  planned: readonly string[]
}

/** A Bazel label: an optional repository, a package path, and an optional target name. */
const BAZEL_LABEL = /^(@[a-z_]+)?\/\/[^:]*(:[A-Za-z0-9_.+-]+)?$/

/** The limits that keep a diagram readable. The review checklist of ADR 0203 names the rest. */
export const DIAGRAM_LIMITS = {
  columns: 6,
  groups: 6,
  nodes: 12,
  edges: 30,
  steps: 9,
  titleLength: 60,
  columnTitleLength: 32,
  nodeTitleLength: 40,
  nodeTextLength: 90,
  labelLength: 28,
  stepTitleLength: 24,
  captionLength: 140,
  minColumnWidth: 200,
  maxColumnWidth: 360,
  defaultColumnWidth: 260,
} as const

export interface DiagramCell {
  col: string
  row: number
}

export interface DiagramColumn {
  id: string
  title: string
  /** A module whose name shows under the lane title. */
  module: string | null
  color: DiagramColor
  width: number
}

export interface DiagramGroup {
  id: string
  title: string
  color: DiagramColor
  from: DiagramCell
  to: DiagramCell
}

export interface DiagramNode {
  id: string
  title: string
  /** One line under the title, inline markdown. */
  text: string | null
  kind: DiagramNodeKind
  col: string
  row: number
  /** Columns the node covers, to the right of `col`. */
  span: number
  /** A Kotlin type that the harvest resolves to a declaration. */
  symbol: string | null
  /** A path under the content root of the site, for a node whose code is not a type. */
  source: string | null
  line: number | null
  /** A Bazel label, for a node that is a build target. The key is absent when the node names no target. */
  target?: string
  module: string | null
  spec: string | null
  adr: string | null
  concept: string | null
  planned: DiagramPlanned | null
  dashed: boolean
  /** The card text, markdown. */
  detail: string | null
}

export interface DiagramEdge {
  /** `<from>-><to>`, the key a step uses. */
  id: string
  from: string
  to: string
  fromSide: DiagramSide | null
  toSide: DiagramSide | null
  label: string | null
  dashed: boolean
}

export interface DiagramStep {
  title: string
  caption: string
  nodes: string[]
  edges: string[]
  /** The bound `@flowStep` as `<flow-id>/<step-id>`, or null. The harvest resolves it (scripts/diagramFlowSteps.ts). */
  flowStep: string | null
}

export interface AirDiagram {
  id: string
  title: string
  summary: string
  columns: DiagramColumn[]
  groups: DiagramGroup[]
  nodes: DiagramNode[]
  edges: DiagramEdge[]
  steps: DiagramStep[]
}

/** A diagram with the references that the harvest resolved. The browser reads this shape. */
export interface HarvestedDiagram extends Omit<AirDiagram, 'nodes' | 'steps'> {
  nodes: HarvestedDiagramNode[]
  steps: HarvestedDiagramStep[]
}

export interface HarvestedDiagramNode extends DiagramNode {
  /** The declaration of `symbol`, or null. */
  symbolRef: { file: string, line: number } | null
  /** The `source` file with its `line`, or null. */
  sourceRef: { file: string, line: number } | null
  /** The `BUILD.bazel` line of `target`. The key is absent when the node names no target. */
  targetRef?: { file: string, line: number }
  /** The title of the ADR, when `adr` is set. */
  adrTitle: string | null
  /** The path of the ADR under the content root, when `adr` is set. */
  adrPath: string | null
}

/** The flow step that a diagram step binds to, as the harvest resolved it. */
export interface DiagramFlowRef {
  flow: string
  step: string
  kind: 'story' | 'implementation'
  /** The `@flowStep` tag in the Kotlin source, or null. */
  source: { file: string, line: number } | null
}

export interface HarvestedDiagramStep extends DiagramStep {
  /** The resolved `flowStep`, or null when the step binds to no flow step. */
  flowRef: DiagramFlowRef | null
}

export const DIAGRAM_FILE_SUFFIX = '.diagram.yaml'

const ID_PATTERN = /^[a-z0-9][a-z0-9-]*$/

/** `<flow-id>/<step-id>`, the key of a step in the coverage ledger (src/coverageLedger.ts). */
const FLOW_STEP_PATTERN = /^([a-z0-9][a-z0-9-]*)\/([a-z0-9][a-z0-9-]*)$/

/** Splits a `flowStep` value into the flow id and the step id, or gives null when the text does not match. */
export function parseFlowStepRef(text: string): { flow: string, step: string } | null {
  const match = FLOW_STEP_PATTERN.exec(text)
  return match ? { flow: match[1], step: match[2] } : null
}

type Raw = Record<string, unknown>

class Errors {
  readonly list: string[] = []
  private readonly prefix: string

  constructor(prefix: string) {
    this.prefix = prefix
  }

  add(message: string): void {
    this.list.push(`${this.prefix}: ${message}`)
  }

  throwIfAny(): void {
    if (this.list.length > 0) {
      throw new Error(this.list.join('\n'))
    }
  }
}

const STEP_KEYS = new Set(['title', 'caption', 'nodes', 'edges', 'flowStep'])

function isRecord(value: unknown): value is Raw {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function readString(raw: Raw, key: string, errors: Errors, where: string, maxLength?: number): string | null {
  const value = raw[key]
  if (value === undefined || value === null) {
    return null
  }
  if (typeof value !== 'string' || value.trim() === '') {
    errors.add(`${where}: '${key}' must be a non-empty string`)
    return null
  }
  const text = value.trim()
  if (maxLength !== undefined && text.length > maxLength) {
    errors.add(`${where}: '${key}' is ${text.length} characters, the limit is ${maxLength}`)
  }
  return text
}

function requireString(raw: Raw, key: string, errors: Errors, where: string, maxLength?: number): string {
  const text = readString(raw, key, errors, where, maxLength)
  if (text === null) {
    if (raw[key] === undefined || raw[key] === null) {
      errors.add(`${where}: '${key}' is required`)
    }
    return ''
  }
  return text
}

function readId(raw: Raw, errors: Errors, where: string): string {
  const id = requireString(raw, 'id', errors, where)
  if (id !== '' && !ID_PATTERN.test(id)) {
    errors.add(`${where}: id '${id}' must match ${ID_PATTERN}`)
  }
  return id
}

function readOneOf<T extends string>(raw: Raw, key: string, values: readonly T[], errors: Errors, where: string): T | null {
  const value = raw[key]
  if (value === undefined || value === null) {
    return null
  }
  if (typeof value !== 'string' || !(values as readonly string[]).includes(value)) {
    errors.add(`${where}: '${key}' must be one of ${values.join(', ')}`)
    return null
  }
  return value as T
}

function readInt(raw: Raw, key: string, errors: Errors, where: string, min: number, max: number): number | null {
  const value = raw[key]
  if (value === undefined || value === null) {
    return null
  }
  if (typeof value !== 'number' || !Number.isInteger(value) || value < min || value > max) {
    errors.add(`${where}: '${key}' must be an integer from ${min} to ${max}`)
    return null
  }
  return value
}

function readBoolean(raw: Raw, key: string, errors: Errors, where: string): boolean {
  const value = raw[key]
  if (value === undefined || value === null) {
    return false
  }
  if (typeof value !== 'boolean') {
    errors.add(`${where}: '${key}' must be true or false`)
    return false
  }
  return value
}

function readList(raw: Raw, key: string, errors: Errors, where: string, required: boolean): Raw[] {
  const value = raw[key]
  if (value === undefined || value === null) {
    if (required) {
      errors.add(`${where}: '${key}' is required`)
    }
    return []
  }
  if (!Array.isArray(value) || value.some(entry => !isRecord(entry))) {
    errors.add(`${where}: '${key}' must be a list of entries`)
    return []
  }
  return value as Raw[]
}

function readStringList(raw: Raw, key: string, errors: Errors, where: string): string[] {
  const value = raw[key]
  if (value === undefined || value === null) {
    return []
  }
  if (!Array.isArray(value) || value.some(entry => typeof entry !== 'string')) {
    errors.add(`${where}: '${key}' must be a list of strings`)
    return []
  }
  return (value as string[]).map(entry => entry.trim())
}

/** `col,row` into a cell; null with an error when the text does not parse. */
function parseCell(text: string, errors: Errors, where: string): DiagramCell | null {
  const match = /^([a-z0-9-]+)\s*,\s*(\d+)$/.exec(text.trim())
  if (!match || Number(match[2]) < 1) {
    errors.add(`${where}: expected 'col,row' with a row from 1, got '${text}'`)
    return null
  }
  return { col: match[1], row: Number(match[2]) }
}

/** `node` or `node.side` into its parts. */
function parseEndpoint(text: string, errors: Errors, where: string): { node: string, side: DiagramSide | null } | null {
  const match = /^([a-z0-9-]+)(?:\.(top|right|bottom|left))?$/.exec(text.trim())
  if (!match) {
    errors.add(`${where}: expected 'node' or 'node.side', got '${text}'`)
    return null
  }
  return { node: match[1], side: (match[2] as DiagramSide | undefined) ?? null }
}

export function edgeId(from: string, to: string): string {
  return `${from}->${to}`
}

/**
 * Validates a parsed YAML document and gives the typed diagram. Throws one error that lists every
 * finding, prefixed with the diagram id. `id` is the file name without `.diagram.yaml`. `grammar` holds the
 * node kinds and the planned values of the site.
 */
export function validateDiagram(raw: unknown, id: string, grammar: DiagramGrammar): AirDiagram {
  const errors = new Errors(id)
  if (!isRecord(raw)) {
    errors.add('the file must hold one mapping')
    errors.throwIfAny()
  }
  const root = raw as Raw
  const declaredId = requireString(root, 'id', errors, 'diagram')
  if (declaredId !== '' && declaredId !== id) {
    errors.add(`'id' is '${declaredId}', the file name says '${id}'`)
  }
  const title = requireString(root, 'title', errors, 'diagram', DIAGRAM_LIMITS.titleLength)
  const summary = requireString(root, 'summary', errors, 'diagram')
  const allIds = new Map<string, string>()
  const claim = (entryId: string, what: string): void => {
    if (entryId === '') {
      return
    }
    const owner = allIds.get(entryId)
    if (owner !== undefined) {
      errors.add(`${what} '${entryId}' repeats the id of ${owner}`)
    }
    else {
      allIds.set(entryId, what)
    }
  }

  const columns: DiagramColumn[] = []
  for (const [index, entry] of readList(root, 'columns', errors, 'diagram', true).entries()) {
    const where = `columns[${index}]`
    const columnId = readId(entry, errors, where)
    claim(columnId, `column`)
    columns.push({
      id: columnId,
      title: requireString(entry, 'title', errors, where, DIAGRAM_LIMITS.columnTitleLength),
      module: readString(entry, 'module', errors, where),
      color: readOneOf(entry, 'color', DIAGRAM_COLORS, errors, where) ?? 'neutral',
      width: readInt(entry, 'width', errors, where, DIAGRAM_LIMITS.minColumnWidth, DIAGRAM_LIMITS.maxColumnWidth) ?? DIAGRAM_LIMITS.defaultColumnWidth,
    })
  }
  if (columns.length === 0) {
    errors.add('at least one column is required')
  }
  if (columns.length > DIAGRAM_LIMITS.columns) {
    errors.add(`${columns.length} columns, the limit is ${DIAGRAM_LIMITS.columns}`)
  }
  const columnIndex = new Map(columns.map((column, index) => [column.id, index]))
  const requireColumn = (col: string, where: string): boolean => {
    if (!columnIndex.has(col)) {
      errors.add(`${where}: column '${col}' is not declared`)
      return false
    }
    return true
  }

  const groups: DiagramGroup[] = []
  for (const [index, entry] of readList(root, 'groups', errors, 'diagram', false).entries()) {
    const where = `groups[${index}]`
    const groupId = readId(entry, errors, where)
    claim(groupId, 'group')
    const cells = requireString(entry, 'cells', errors, where)
    const parts = cells.split('..')
    const from = parts.length === 2 ? parseCell(parts[0], errors, where) : null
    const to = parts.length === 2 ? parseCell(parts[1], errors, where) : null
    if (parts.length !== 2) {
      errors.add(`${where}: 'cells' must be 'col,row .. col,row'`)
    }
    if (from && to && requireColumn(from.col, where) && requireColumn(to.col, where)) {
      const fromIndex = columnIndex.get(from.col)!
      const toIndex = columnIndex.get(to.col)!
      if (fromIndex > toIndex || from.row > to.row) {
        errors.add(`${where}: 'cells' must name the top-left cell first`)
      }
      groups.push({
        id: groupId,
        title: requireString(entry, 'title', errors, where, DIAGRAM_LIMITS.columnTitleLength),
        color: readOneOf(entry, 'color', DIAGRAM_COLORS, errors, where) ?? 'neutral',
        from,
        to,
      })
    }
  }
  if (groups.length > DIAGRAM_LIMITS.groups) {
    errors.add(`${groups.length} groups, the limit is ${DIAGRAM_LIMITS.groups}`)
  }
  for (const [index, a] of groups.entries()) {
    for (const b of groups.slice(index + 1)) {
      const aCols = [columnIndex.get(a.from.col)!, columnIndex.get(a.to.col)!]
      const bCols = [columnIndex.get(b.from.col)!, columnIndex.get(b.to.col)!]
      const overlap = aCols[0] <= bCols[1] && bCols[0] <= aCols[1] && a.from.row <= b.to.row && b.from.row <= a.to.row
      const aInB = aCols[0] >= bCols[0] && aCols[1] <= bCols[1] && a.from.row >= b.from.row && a.to.row <= b.to.row
      const bInA = bCols[0] >= aCols[0] && bCols[1] <= aCols[1] && b.from.row >= a.from.row && b.to.row <= a.to.row
      if (overlap && !aInB && !bInA) {
        errors.add(`groups '${a.id}' and '${b.id}' overlap in part; a group is nested or disjoint`)
      }
    }
  }

  const nodes: DiagramNode[] = []
  const cellOwner = new Map<string, string>()
  for (const [index, entry] of readList(root, 'nodes', errors, 'diagram', true).entries()) {
    const where = `nodes[${index}]`
    const nodeId = readId(entry, errors, where)
    claim(nodeId, 'node')
    const at = parseCell(requireString(entry, 'at', errors, where), errors, where)
    const span = readInt(entry, 'span', errors, where, 1, DIAGRAM_LIMITS.columns) ?? 1
    const source = readString(entry, 'source', errors, where)
    const line = readInt(entry, 'line', errors, where, 1, 1_000_000)
    if (line !== null && source === null) {
      errors.add(`${where}: 'line' needs 'source'`)
    }
    const target = readString(entry, 'target', errors, where)
    if (target !== null && !BAZEL_LABEL.test(target)) {
      errors.add(`${where}: 'target' must be a Bazel label such as //pkg:name, got '${target}'`)
    }
    const kind = readOneOf(entry, 'kind', grammar.kinds, errors, where)
    if (kind === null && entry.kind === undefined) {
      errors.add(`${where}: 'kind' is required`)
    }
    if (at && requireColumn(at.col, where)) {
      const start = columnIndex.get(at.col)!
      if (start + span > columns.length) {
        errors.add(`${where}: span ${span} from column '${at.col}' leaves the grid`)
      }
      for (let offset = 0; offset < span && start + offset < columns.length; offset++) {
        const key = `${columns[start + offset].id},${at.row}`
        const owner = cellOwner.get(key)
        if (owner !== undefined) {
          errors.add(`${where}: cell ${key} is taken by node '${owner}'`)
        }
        else {
          cellOwner.set(key, nodeId)
        }
      }
    }
    nodes.push({
      id: nodeId,
      title: requireString(entry, 'title', errors, where, DIAGRAM_LIMITS.nodeTitleLength),
      text: readString(entry, 'text', errors, where, DIAGRAM_LIMITS.nodeTextLength),
      kind: kind ?? '',
      col: at?.col ?? '',
      row: at?.row ?? 1,
      span,
      symbol: readString(entry, 'symbol', errors, where),
      source,
      line,
      ...(target === null ? {} : { target }),
      module: readString(entry, 'module', errors, where),
      spec: readString(entry, 'spec', errors, where),
      adr: readString(entry, 'adr', errors, where),
      concept: readString(entry, 'concept', errors, where),
      planned: readOneOf(entry, 'planned', grammar.planned, errors, where),
      dashed: readBoolean(entry, 'dashed', errors, where),
      detail: readString(entry, 'detail', errors, where),
    })
  }
  if (nodes.length === 0) {
    errors.add('at least one node is required')
  }
  if (nodes.length > DIAGRAM_LIMITS.nodes) {
    errors.add(`${nodes.length} nodes, the limit is ${DIAGRAM_LIMITS.nodes}; split the diagram`)
  }
  const nodeIds = new Set(nodes.map(node => node.id))
  const requireNode = (nodeId: string, where: string): boolean => {
    if (!nodeIds.has(nodeId)) {
      errors.add(`${where}: node '${nodeId}' is not declared`)
      return false
    }
    return true
  }

  const edges: DiagramEdge[] = []
  const edgeIds = new Set<string>()
  for (const [index, entry] of readList(root, 'edges', errors, 'diagram', false).entries()) {
    const where = `edges[${index}]`
    const from = parseEndpoint(requireString(entry, 'from', errors, where), errors, where)
    const to = parseEndpoint(requireString(entry, 'to', errors, where), errors, where)
    if (!from || !to || !requireNode(from.node, where) || !requireNode(to.node, where)) {
      continue
    }
    if (from.node === to.node) {
      errors.add(`${where}: an edge cannot start and end on '${from.node}'`)
      continue
    }
    const id = edgeId(from.node, to.node)
    if (edgeIds.has(id)) {
      errors.add(`${where}: edge '${id}' is declared twice; join the labels with ';'`)
      continue
    }
    edgeIds.add(id)
    edges.push({
      id,
      from: from.node,
      to: to.node,
      fromSide: from.side,
      toSide: to.side,
      label: readString(entry, 'label', errors, where, DIAGRAM_LIMITS.labelLength),
      dashed: readBoolean(entry, 'dashed', errors, where),
    })
  }
  if (edges.length > DIAGRAM_LIMITS.edges) {
    errors.add(`${edges.length} edges, the limit is ${DIAGRAM_LIMITS.edges}`)
  }

  const steps: DiagramStep[] = []
  for (const [index, entry] of readList(root, 'steps', errors, 'diagram', true).entries()) {
    const where = `steps[${index + 1}]`
    const stepNodes = readStringList(entry, 'nodes', errors, where)
    if (stepNodes.length === 0) {
      errors.add(`${where}: a step highlights at least one node`)
    }
    for (const nodeId of stepNodes) {
      requireNode(nodeId, where)
    }
    const stepEdges = readStringList(entry, 'edges', errors, where)
    for (const edge of stepEdges) {
      if (!edgeIds.has(edge)) {
        errors.add(`${where}: edge '${edge}' is not declared; write it as 'from->to'`)
      }
    }
    const flowStep = readString(entry, 'flowStep', errors, where)
    if (flowStep !== null && parseFlowStepRef(flowStep) === null) {
      errors.add(`${where}: 'flowStep' must be '<flow-id>/<step-id>', got '${flowStep}'`)
    }
    for (const key of Object.keys(entry)) {
      if (!STEP_KEYS.has(key)) {
        errors.add(`${where}: unknown step key '${key}'`)
      }
    }
    steps.push({
      title: requireString(entry, 'title', errors, where, DIAGRAM_LIMITS.stepTitleLength),
      caption: requireString(entry, 'caption', errors, where, DIAGRAM_LIMITS.captionLength),
      nodes: stepNodes,
      edges: stepEdges,
      flowStep,
    })
  }
  if (steps.length === 0) {
    errors.add('at least one step is required')
  }
  if (steps.length > DIAGRAM_LIMITS.steps) {
    errors.add(`${steps.length} steps, the limit is ${DIAGRAM_LIMITS.steps}`)
  }

  const known = new Set(['id', 'title', 'summary', 'columns', 'groups', 'nodes', 'edges', 'steps'])
  for (const key of Object.keys(root)) {
    if (!known.has(key)) {
      errors.add(`unknown top-level key '${key}'`)
    }
  }
  errors.throwIfAny()
  return { id, title, summary, columns, groups, nodes, edges, steps }
}

// --- the fence -----------------------------------------------------------------------------------

export interface AirDiagramFence {
  id: string
  /** The step the embedded diagram opens at, or null for the overview. */
  step: number | null
  /** The canvas height in px, or null for the natural height of the layout. */
  height: number | null
  /** The caption, or null for the diagram summary. */
  title: string | null
}

export interface AirDiagramFenceParseResult {
  spec: AirDiagramFence | null
  errors: string[]
}

/** Parses the body of a ```diagram fence: line-based `key: value`, like the air-view fence. */
export function parseDiagramFence(text: string): AirDiagramFenceParseResult {
  const errors: string[] = []
  let id: string | null = null
  let step: number | null = null
  let height: number | null = null
  let title: string | null = null
  for (const { key, value } of fenceEntries(text, errors)) {
    switch (key) {
      case 'id':
        if (ID_PATTERN.test(value)) {
          id = value
        }
        else {
          errors.push(`invalid id '${value}'`)
        }
        break
      case 'step': {
        const parsed = Number.parseInt(value, 10)
        if (Number.isNaN(parsed) || parsed < 1 || parsed > DIAGRAM_LIMITS.steps) {
          errors.push(`invalid step '${value}' (expected 1–${DIAGRAM_LIMITS.steps})`)
        }
        else {
          step = parsed
        }
        break
      }
      case 'height': {
        const parsed = Number.parseInt(value, 10)
        if (Number.isNaN(parsed) || parsed < 240 || parsed > 900) {
          errors.push(`invalid height '${value}' (expected 240–900)`)
        }
        else {
          height = parsed
        }
        break
      }
      case 'title':
        title = value
        break
      default:
        errors.push(`unknown key '${key}' (expected id, step, height, or title)`)
    }
  }
  if (id === null) {
    errors.push(`missing 'id:'`)
  }
  return { spec: id === null ? null : { id, step, height, title }, errors }
}

/**
 * The diagram fences of a markdown source, with the 1-based line of each opening fence. `diagram` is the kernel name
 * of the fence. The Air docs use the older name `air-diagram`, and the kernel accepts it too.
 */
export function findDiagramFences(source: string): { line: number, body: string }[] {
  const fences: { line: number, body: string }[] = []
  for (const fence of source.matchAll(/^```(?:air-)?diagram[ \t]*\n([\s\S]*?)^```[ \t]*$/gm)) {
    fences.push({ line: source.slice(0, fence.index).split('\n').length, body: fence[1] })
  }
  return fences
}
