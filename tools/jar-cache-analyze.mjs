#!/usr/bin/env bun

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {readdir, readFile, stat, writeFile} from "node:fs/promises"
import {dirname, join, resolve} from "node:path"
import process from "node:process"
import {fileURLToPath} from "node:url"

const usageExitCode = 2
const commandExitCode = 3
const defaultMarkdownReportFileName = "jar-cache-report.md"

const metadataMagic = 0x4A434D31
const metadataSchemaVersion = 2
const metadataHeaderSizeBytes = 12
const sourceRecordFixedSizeBytes = 20
const maxNativeFileCount = 65_536
const maxNativeFilesBlobSizeBytes = 8 * 1024 * 1024

const entryNameSeparator = "__"
const metadataFileSuffix = ".meta"
const markFileSuffix = ".mark"
const versionsPerTargetLimit = 3
const defaultAnomalyTop = 20
const defaultParetoThresholdPct = 80
const defaultHeatmapBins = 10
const scoreWeightKeys = ["overflow", "reclaim", "integrity", "concentration"]
const defaultScoreWeights = {
  overflow: 35,
  reclaim: 35,
  integrity: 20,
  concentration: 10,
}

const ansi = {
  reset: "\u001b[0m",
  cyan: "\u001b[36m",
  dim: "\u001b[2m",
  green: "\u001b[32m",
  red: "\u001b[31m",
  yellow: "\u001b[33m",
}

class UsageError extends Error {
  constructor(message) {
    super(message)
    this.name = "UsageError"
  }
}

function isMainModule() {
  const scriptPath = process["argv"]?.[1]
  if (!scriptPath) {
    return false
  }
  return resolve(scriptPath) === fileURLToPath(import.meta.url)
}

function printUsage() {
  return [
    "Usage:",
    "  bun community/tools/jar-cache-analyze.mjs [options]",
    "",
    "Options:",
    "  --cache-dir <path>  Jar cache root (default: out/dev-run/jar-cache)",
    "  --top <n>           Top-N rows in hotspot tables (default: 20)",
    "  --bins <n>          Histogram bins for metadata age profile (default: 12)",
    "  --anomaly-top <n>   Max anomaly rows in report (default: 20)",
    "  --pareto-threshold <pct>  Pareto reclaim threshold in percent (default: 80)",
    "  --heatmap-bins <n>  Source-count heatmap bins (default: 10)",
    "  --score-weights <json>  Score weights JSON, e.g. {\"overflow\":35,\"reclaim\":35,\"integrity\":20,\"concentration\":10}",
    "  --md-file <path>    Write Markdown report to file (default: <cache-dir>/jar-cache-report.md)",
    "  --no-md-file        Disable default Markdown report file write",
    "  --json              Print JSON report to stdout",
    "  --json-file <path>  Write JSON report to file",
    "  --strict-meta       Fail on first malformed metadata file",
    "  --no-color          Disable ANSI colors",
    "  --help              Print this help",
  ].join("\n")
}

function detectColorSupport() {
  const env = process.env ?? {}
  if (Object.prototype.hasOwnProperty.call(env, "NO_COLOR")) {
    return false
  }
  const forceColor = env["FORCE_COLOR"]
  if (forceColor !== undefined) {
    return forceColor !== "0"
  }
  return Boolean(process.stdout?.["isTTY"])
}

function colorize(text, color, useColor) {
  if (!useColor) {
    return text
  }
  return `${color}${text}${ansi.reset}`
}

function formatNumber(value) {
  return Number(value).toLocaleString("en-US")
}

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return "n/a"
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ["KiB", "MiB", "GiB", "TiB", "PiB"]
  let value = bytes
  let unitIndex = -1
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex++
  }
  const rounded = value >= 10 ? value.toFixed(1) : value.toFixed(2)
  return `${rounded} ${units[unitIndex]}`
}

function formatPercent(value) {
  return `${(value * 100).toFixed(1)}%`
}

function formatDuration(ms) {
  if (!Number.isFinite(ms) || ms < 0) {
    return "n/a"
  }
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) {
    return `${seconds}s`
  }
  const minutes = Math.floor(seconds / 60)
  const remSeconds = seconds % 60
  if (minutes < 60) {
    return `${minutes}m ${remSeconds}s`
  }
  const hours = Math.floor(minutes / 60)
  const remMinutes = minutes % 60
  if (hours < 24) {
    return `${hours}h ${remMinutes}m`
  }
  const days = Math.floor(hours / 24)
  const remHours = hours % 24
  return `${days}d ${remHours}h`
}

function truncate(value, maxLength) {
  if (value.length <= maxLength) {
    return value
  }
  if (maxLength < 2) {
    return value.slice(0, maxLength)
  }
  return `${value.slice(0, maxLength - 1)}…`
}

function pad(text, length, align = "left") {
  if (text.length >= length) {
    return text
  }
  const padding = " ".repeat(length - text.length)
  return align === "right" ? `${padding}${text}` : `${text}${padding}`
}

function makeHistogram(values, bins) {
  if (values.length === 0) {
    return []
  }

  const min = Math.min(...values)
  const max = Math.max(...values)
  if (min === max) {
    return [{from: min, to: max, count: values.length}]
  }

  const width = (max - min) / bins
  const histogram = Array.from({length: bins}, (_, index) => ({
    from: min + index * width,
    to: min + (index + 1) * width,
    count: 0,
  }))

  for (const value of values) {
    const index = Math.min(Math.floor((value - min) / width), bins - 1)
    histogram[index].count++
  }

  return histogram
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

function safeDivide(numerator, denominator) {
  if (!Number.isFinite(denominator) || denominator <= 0) {
    return 0
  }
  return numerator / denominator
}

function quantile(sortedValues, q) {
  if (sortedValues.length === 0) {
    return 0
  }
  if (sortedValues.length === 1) {
    return sortedValues[0]
  }
  const clampedQ = clamp(q, 0, 1)
  const position = (sortedValues.length - 1) * clampedQ
  const lowerIndex = Math.floor(position)
  const upperIndex = Math.ceil(position)
  if (lowerIndex === upperIndex) {
    return sortedValues[lowerIndex]
  }
  const fraction = position - lowerIndex
  return sortedValues[lowerIndex] * (1 - fraction) + sortedValues[upperIndex] * fraction
}

function robustUpperFence(values) {
  if (values.length === 0) {
    return 0
  }
  const sorted = [...values].sort((a, b) => a - b)
  const q1 = quantile(sorted, 0.25)
  const q3 = quantile(sorted, 0.75)
  const iqr = Math.max(0, q3 - q1)
  const fallbackSpread = Math.max(1, q3 * 0.5)
  return q3 + (iqr > 0 ? 1.5 * iqr : fallbackSpread)
}

function normalizeWeights(weights) {
  const total = scoreWeightKeys.reduce((sum, key) => sum + weights[key], 0)
  const normalized = {}
  for (const key of scoreWeightKeys) {
    normalized[key] = total > 0 ? weights[key] / total : 0
  }
  return {
    normalized,
    total,
  }
}

function computePearsonCorrelation(pairs) {
  if (pairs.length < 2) {
    return 0
  }
  const meanX = pairs.reduce((sum, pair) => sum + pair.x, 0) / pairs.length
  const meanY = pairs.reduce((sum, pair) => sum + pair.y, 0) / pairs.length

  let numerator = 0
  let xVariance = 0
  let yVariance = 0
  for (const pair of pairs) {
    const dx = pair.x - meanX
    const dy = pair.y - meanY
    numerator += dx * dy
    xVariance += dx * dx
    yVariance += dy * dy
  }

  const denominator = Math.sqrt(xVariance * yVariance)
  if (denominator === 0) {
    return 0
  }
  return numerator / denominator
}

function bucketSourceInsights(entries, heatmapBins, topOutlierLimit) {
  const metadataPairs = entries
    .filter((entry) => entry.payload !== null && entry.metadataParse?.ok)
    .map((entry) => ({
      sourceCount: entry.metadataParse.sourceCount,
      payloadBytes: entry.payload.sizeBytes,
      reclaimableBytes: entry.isOverflowByVersionCap ? entry.payload.sizeBytes : 0,
      targetName: entry.targetName ?? "(invalid)",
      entryStem: entry.entryStem,
      shardName: entry.shardName,
    }))

  const coveragePct = safeDivide(metadataPairs.length, entries.filter((entry) => entry.payload !== null).length)
  if (metadataPairs.length === 0) {
    return {
      coveragePct,
      buckets: [],
      correlations: {
        sourceCountVsPayloadPearson: 0,
      },
      topOutliers: [],
    }
  }

  const sorted = [...metadataPairs].sort((a, b) => a.sourceCount - b.sourceCount)
  const bucketCount = Math.max(1, Math.min(heatmapBins, sorted.length))
  const buckets = []
  for (let index = 0; index < bucketCount; index++) {
    const from = Math.floor((index * sorted.length) / bucketCount)
    const to = Math.floor(((index + 1) * sorted.length) / bucketCount)
    const slice = sorted.slice(from, Math.max(from + 1, to))
    if (slice.length === 0) {
      continue
    }

    const payloadTotal = slice.reduce((sum, item) => sum + item.payloadBytes, 0)
    const reclaimableTotal = slice.reduce((sum, item) => sum + item.reclaimableBytes, 0)
    buckets.push({
      minSourceCount: slice[0].sourceCount,
      maxSourceCount: slice[slice.length - 1].sourceCount,
      entryCount: slice.length,
      payloadBytes: payloadTotal,
      avgPayloadBytes: payloadTotal / slice.length,
      reclaimableBytes: reclaimableTotal,
    })
  }

  const correlation = computePearsonCorrelation(metadataPairs.map((pair) => ({
    x: pair.sourceCount,
    y: pair.payloadBytes,
  })))

  const topOutliers = [...metadataPairs]
    .sort((a, b) => {
      const scoreA = a.sourceCount * Math.log2(a.payloadBytes + 1)
      const scoreB = b.sourceCount * Math.log2(b.payloadBytes + 1)
      if (scoreB !== scoreA) {
        return scoreB - scoreA
      }
      return b.payloadBytes - a.payloadBytes
    })
    .slice(0, topOutlierLimit)
    .map((item) => ({
      targetName: item.targetName,
      entryStem: item.entryStem,
      shardName: item.shardName,
      sourceCount: item.sourceCount,
      payloadBytes: item.payloadBytes,
      reclaimableBytes: item.reclaimableBytes,
    }))

  return {
    coveragePct,
    buckets,
    correlations: {
      sourceCountVsPayloadPearson: correlation,
    },
    topOutliers,
  }
}

function computePareto(targetsByReclaimableBytes, reclaimableTotal, thresholdPct) {
  const threshold = clamp(thresholdPct / 100, 0, 1)
  let cumulative = 0
  let targetsToThreshold = 0
  let shareAtThreshold = 0

  const cumulativeRows = targetsByReclaimableBytes.map((target, index) => {
    cumulative += target.reclaimableBytes
    const cumulativeShare = reclaimableTotal > 0 ? cumulative / reclaimableTotal : 0
    if (targetsToThreshold === 0 && cumulativeShare >= threshold) {
      targetsToThreshold = index + 1
      shareAtThreshold = cumulativeShare
    }
    return {
      targetName: target.targetName,
      reclaimableBytes: target.reclaimableBytes,
      cumulativeReclaimableBytes: cumulative,
      cumulativeShare,
    }
  })

  return {
    targetCoverageThresholdPct: thresholdPct,
    cumulativeRows,
    targetsToThreshold,
    reclaimableShareAtThreshold: shareAtThreshold,
  }
}

function buildAnomalies(targetRows, thresholds, anomalyTop) {
  const rows = []
  for (const target of targetRows) {
    const reasons = []
    const overflowRatio = safeDivide(target.overflow, target.versions)
    let severity = 0

    if (target.reclaimableBytes > 0 && target.reclaimableBytes >= thresholds.reclaimableBytes) {
      reasons.push("high_reclaimable_bytes")
      severity += safeDivide(target.reclaimableBytes, Math.max(1, thresholds.reclaimableBytes))
    }

    if (target.versions >= thresholds.versions) {
      reasons.push("high_version_churn")
      severity += safeDivide(target.versions, Math.max(1, thresholds.versions))
    }

    if (target.versions > versionsPerTargetLimit && overflowRatio >= thresholds.overflowRatio) {
      reasons.push("high_overflow_ratio")
      severity += safeDivide(overflowRatio, Math.max(0.01, thresholds.overflowRatio))
    }

    if (target.payloadBytes >= thresholds.payloadBytes) {
      reasons.push("large_payload_outlier")
      severity += safeDivide(target.payloadBytes, Math.max(1, thresholds.payloadBytes))
    }

    if (target.avgSourceCount >= thresholds.avgSourceCount && target.payloadBytes >= thresholds.payloadBytes * 0.5) {
      reasons.push("high_sourcecount_large_payload")
      severity += safeDivide(target.avgSourceCount, Math.max(1, thresholds.avgSourceCount))
    }

    if (reasons.length > 0) {
      rows.push({
        targetName: target.targetName,
        versions: target.versions,
        overflow: target.overflow,
        overflowRatio,
        payloadBytes: target.payloadBytes,
        reclaimableBytes: target.reclaimableBytes,
        avgSourceCount: target.avgSourceCount,
        maxSourceCount: target.maxSourceCount,
        reasons,
        severity,
      })
    }
  }

  rows.sort((a, b) => {
    if (b.severity !== a.severity) {
      return b.severity - a.severity
    }
    if (b.reclaimableBytes !== a.reclaimableBytes) {
      return b.reclaimableBytes - a.reclaimableBytes
    }
    return b.versions - a.versions
  })

  return rows.slice(0, anomalyTop)
}

function computeEfficiencyScore(metrics, weights) {
  const {normalized} = normalizeWeights(weights)

  const overflowPressure = clamp(metrics.overflowPressure, 0, 1)
  const reclaimPressure = clamp(metrics.reclaimPressure, 0, 1)
  const integrityPenalty = clamp(metrics.integrityPenalty, 0, 1)
  const concentrationPenalty = clamp(metrics.concentrationPenalty, 0, 1)

  const weightedPenalty =
    normalized.overflow * overflowPressure +
    normalized.reclaim * reclaimPressure +
    normalized.integrity * integrityPenalty +
    normalized.concentration * concentrationPenalty

  const score = Math.round(clamp((1 - weightedPenalty) * 100, 0, 100))
  return {
    score,
    components: {
      overflowPressure,
      reclaimPressure,
      integrityPenalty,
      concentrationPenalty,
    },
    penalties: {
      overflow: normalized.overflow * overflowPressure,
      reclaim: normalized.reclaim * reclaimPressure,
      integrity: normalized.integrity * integrityPenalty,
      concentration: normalized.concentration * concentrationPenalty,
      total: weightedPenalty,
    },
    weights: {
      raw: weights,
      normalized,
    },
  }
}

/**
 * @param {{label: string, value: number}[]} items
 * @param {{width?: number, maxLabelLength?: number, formatValue?: (value: number) => string}} options
 */
function renderBarChart(items, options = {}) {
  if (items.length === 0) {
    return ["  (no data)"]
  }

  const width = options.width ?? 40
  const maxLabelLength = options.maxLabelLength ?? 28
  const labelWidth = Math.min(
    maxLabelLength,
    Math.max(...items.map((item) => item.label.length)),
  )
  const maxValue = Math.max(...items.map((item) => item.value), 1)
  const formatValue = options.formatValue ?? ((value) => formatNumber(value))
  const lines = []

  for (const item of items) {
    const normalized = maxValue === 0 ? 0 : item.value / maxValue
    const barLength = Math.round(normalized * width)
    const bar = "#".repeat(barLength)
    const label = pad(truncate(item.label, labelWidth), labelWidth)
    lines.push(`  ${label} | ${pad(bar, width)} ${formatValue(item.value)}`)
  }

  return lines
}

function renderTable(columns, rows) {
  if (rows.length === 0) {
    return ["  (no data)"]
  }

  const widths = columns.map((column) => {
    let width = column.title.length
    for (const row of rows) {
      const value = String(row[column.key] ?? "")
      width = Math.max(width, value.length)
    }
    return Math.min(width, column.maxWidth ?? width)
  })

  const header = columns
    .map((column, index) => pad(truncate(column.title, widths[index]), widths[index], column.align ?? "left"))
    .join(" | ")

  const separator = widths.map((width) => "-".repeat(width)).join("-+-")

  const lines = [`  ${header}`, `  ${separator}`]
  for (const row of rows) {
    const formatted = columns.map((column, index) => {
      const value = String(row[column.key] ?? "")
      return pad(truncate(value, widths[index]), widths[index], column.align ?? "left")
    })
    lines.push(`  ${formatted.join(" | ")}`)
  }

  return lines
}

function escapeMarkdownCell(value) {
  return String(value)
    .replace(/\\/g, "\\\\")
    .replace(/\|/g, "\\|")
    .replace(/\r?\n/g, " ")
}

function renderMarkdownTable(columns, rows) {
  if (rows.length === 0) {
    return ["(no data)"]
  }

  const header = `| ${columns.map((column) => escapeMarkdownCell(column.title)).join(" | ")} |`
  const separator = `| ${columns.map((column) => {
    if (column.align === "right") {
      return "---:"
    }
    if (column.align === "center") {
      return ":---:"
    }
    return "---"
  }).join(" | ")} |`

  const body = rows.map((row) => `| ${columns.map((column) => escapeMarkdownCell(row[column.key] ?? "")).join(" | ")} |`)
  return [header, separator, ...body]
}

function parseArguments(argv) {
  let cacheDir = resolve(process.cwd(), "out/dev-run/jar-cache")
  let top = 20
  let bins = 12
  let anomalyTop = defaultAnomalyTop
  let paretoThresholdPct = defaultParetoThresholdPct
  let heatmapBins = defaultHeatmapBins
  let scoreWeights = defaultScoreWeights
  let writeMarkdownFile = true
  let markdownFile = null
  let json = false
  let jsonFile = null
  let strictMeta = false
  let useColor = detectColorSupport()

  for (let index = 0; index < argv.length; index++) {
    const arg = argv[index]
    if (arg === "--help") {
      return {help: true}
    }
    if (arg === "--cache-dir") {
      const value = argv[++index]
      if (!value) {
        throw new UsageError("Missing value for --cache-dir")
      }
      cacheDir = resolve(value)
      continue
    }
    if (arg === "--top") {
      const value = argv[++index]
      if (!value) {
        throw new UsageError("Missing value for --top")
      }
      top = parsePositiveInt(value, "--top")
      continue
    }
    if (arg === "--bins") {
      const value = argv[++index]
      if (!value) {
        throw new UsageError("Missing value for --bins")
      }
      bins = parsePositiveInt(value, "--bins")
      continue
    }
    if (arg === "--anomaly-top") {
      const value = argv[++index]
      if (!value) {
        throw new UsageError("Missing value for --anomaly-top")
      }
      anomalyTop = parsePositiveInt(value, "--anomaly-top")
      continue
    }
    if (arg === "--pareto-threshold") {
      const value = argv[++index]
      if (!value) {
        throw new UsageError("Missing value for --pareto-threshold")
      }
      paretoThresholdPct = parsePercentage(value, "--pareto-threshold")
      continue
    }
    if (arg === "--heatmap-bins") {
      const value = argv[++index]
      if (!value) {
        throw new UsageError("Missing value for --heatmap-bins")
      }
      heatmapBins = parsePositiveInt(value, "--heatmap-bins")
      continue
    }
    if (arg === "--score-weights") {
      const value = argv[++index]
      if (!value) {
        throw new UsageError("Missing value for --score-weights")
      }
      scoreWeights = parseScoreWeights(value)
      continue
    }
    if (arg === "--md-file") {
      const value = argv[++index]
      if (!value) {
        throw new UsageError("Missing value for --md-file")
      }
      markdownFile = resolve(value)
      continue
    }
    if (arg === "--no-md-file") {
      writeMarkdownFile = false
      continue
    }
    if (arg === "--json") {
      json = true
      continue
    }
    if (arg === "--json-file") {
      const value = argv[++index]
      if (!value) {
        throw new UsageError("Missing value for --json-file")
      }
      jsonFile = resolve(value)
      continue
    }
    if (arg === "--strict-meta") {
      strictMeta = true
      continue
    }
    if (arg === "--no-color") {
      useColor = false
      continue
    }
    if (arg.startsWith("--")) {
      throw new UsageError(`Unknown option: ${arg}`)
    }
    throw new UsageError(`Unexpected positional argument: ${arg}`)
  }

  return {
    help: false,
    cacheDir,
    top,
    bins,
    anomalyTop,
    paretoThresholdPct,
    heatmapBins,
    scoreWeights,
    writeMarkdownFile,
    markdownFile,
    json,
    jsonFile,
    strictMeta,
    useColor,
  }
}

function parsePositiveInt(value, optionName) {
  const parsed = Number.parseInt(value, 10)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new UsageError(`Expected a positive integer for ${optionName}, got: ${value}`)
  }
  return parsed
}

function parsePercentage(value, optionName) {
  const parsed = Number.parseFloat(value)
  if (!Number.isFinite(parsed) || parsed <= 0 || parsed > 100) {
    throw new UsageError(`Expected percentage in (0, 100] for ${optionName}, got: ${value}`)
  }
  return parsed
}

function parseScoreWeights(value) {
  let parsed
  try {
    parsed = JSON.parse(value)
  }
  catch (error) {
    throw new UsageError(`Expected valid JSON for --score-weights, got: ${value}`)
  }

  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new UsageError("--score-weights must be a JSON object")
  }

  const result = {}
  for (const key of scoreWeightKeys) {
    const raw = parsed[key]
    if (raw === undefined) {
      result[key] = defaultScoreWeights[key]
      continue
    }

    const numericValue = Number(raw)
    if (!Number.isFinite(numericValue) || numericValue < 0) {
      throw new UsageError(`Invalid score weight for '${key}': ${raw}`)
    }
    result[key] = numericValue
  }

  const total = scoreWeightKeys.reduce((sum, key) => sum + result[key], 0)
  if (total <= 0) {
    throw new UsageError("At least one score weight must be > 0")
  }

  return result
}

function parseEntryStem(stem) {
  const separatorIndex = stem.indexOf(entryNameSeparator)
  if (separatorIndex <= 0 || separatorIndex + entryNameSeparator.length >= stem.length) {
    return {
      key: null,
      targetName: null,
      stemValid: false,
    }
  }
  return {
    key: stem.slice(0, separatorIndex),
    targetName: stem.slice(separatorIndex + entryNameSeparator.length),
    stemValid: true,
  }
}

function isVersionDirectoryName(name) {
  return /^v\d+$/.test(name)
}

function extractVersionNumber(name) {
  return Number.parseInt(name.slice(1), 10)
}

async function fileExists(path) {
  try {
    await stat(path)
    return true
  }
  catch {
    return false
  }
}

async function detectVersionLayout(cacheDir) {
  const resolvedCacheDir = resolve(cacheDir)
  const cacheDirName = resolvedCacheDir.split(/[\\/]/).filter(Boolean).at(-1) ?? ""

  if (isVersionDirectoryName(cacheDirName)) {
    const entriesDir = join(resolvedCacheDir, "entries")
    if (!(await fileExists(entriesDir))) {
      throw new Error(`Version directory does not contain entries/: ${resolvedCacheDir}`)
    }
    return {
      cacheRootDir: dirname(resolvedCacheDir),
      versionDir: resolvedCacheDir,
      entriesDir,
    }
  }

  const entries = await readdir(resolvedCacheDir, {withFileTypes: true})
  const versionDirs = entries
    .filter((entry) => entry.isDirectory() && isVersionDirectoryName(entry.name))
    .map((entry) => entry.name)

  if (versionDirs.length === 0) {
    throw new Error(`No version directories (vN) found in cache root: ${resolvedCacheDir}`)
  }

  versionDirs.sort((a, b) => extractVersionNumber(b) - extractVersionNumber(a))
  const versionDir = join(resolvedCacheDir, versionDirs[0])
  const entriesDir = join(versionDir, "entries")
  if (!(await fileExists(entriesDir))) {
    throw new Error(`Detected version directory does not contain entries/: ${versionDir}`)
  }

  return {
    cacheRootDir: resolvedCacheDir,
    versionDir,
    entriesDir,
  }
}

function createEntry(shardName, stem) {
  const parsedStem = parseEntryStem(stem)
  return {
    shardName,
    entryStem: stem,
    key: parsedStem.key,
    targetName: parsedStem.targetName,
    stemValid: parsedStem.stemValid,
    payload: null,
    meta: null,
    mark: null,
    metadataParse: null,
  }
}

function setEntryFile(entry, kind, filePath, fileStats) {
  const fileData = {
    path: filePath,
    sizeBytes: fileStats.size,
    mtimeMs: fileStats.mtimeMs,
  }
  if (kind === "payload") {
    entry.payload = fileData
  }
  else if (kind === "meta") {
    entry.meta = fileData
  }
  else {
    entry.mark = fileData
  }
}

function classifyEntryHealth(entry) {
  const hasPayload = entry.payload !== null
  const hasMeta = entry.meta !== null
  const hasMark = entry.mark !== null

  if (hasPayload && hasMeta) {
    return "complete"
  }
  if (hasPayload && !hasMeta) {
    return "missing_meta"
  }
  if (!hasPayload && hasMeta) {
    return "missing_payload"
  }
  if (!hasPayload && !hasMeta && hasMark) {
    return "orphan_mark"
  }
  return "missing_both"
}

async function scanEntries(entriesDir) {
  const shardEntries = await readdir(entriesDir, {withFileTypes: true})
  const shardNames = shardEntries
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort()

  const entriesByKey = new Map()

  for (const shardName of shardNames) {
    const shardDir = join(entriesDir, shardName)
    const fileEntries = await readdir(shardDir, {withFileTypes: true})

    for (const fileEntry of fileEntries) {
      if (!fileEntry.isFile()) {
        continue
      }

      const fileName = fileEntry.name
      let kind = "payload"
      let stem = fileName

      if (fileName.endsWith(metadataFileSuffix)) {
        kind = "meta"
        stem = fileName.slice(0, -metadataFileSuffix.length)
      }
      else if (fileName.endsWith(markFileSuffix)) {
        kind = "mark"
        stem = fileName.slice(0, -markFileSuffix.length)
      }

      const entryKey = `${shardName}|${stem}`
      const entry = entriesByKey.get(entryKey) ?? createEntry(shardName, stem)
      const filePath = join(shardDir, fileName)
      const fileStats = await stat(filePath)
      setEntryFile(entry, kind, filePath, fileStats)
      entriesByKey.set(entryKey, entry)
    }
  }

  return {
    shardNames,
    entries: Array.from(entriesByKey.values()),
  }
}

function readInt32(buffer, offset, description) {
  if (offset + 4 > buffer.length) {
    throw new Error(`Metadata is too short while reading ${description}`)
  }
  return buffer.readInt32BE(offset)
}

function readBigInt64(buffer, offset, description) {
  if (offset + 8 > buffer.length) {
    throw new Error(`Metadata is too short while reading ${description}`)
  }
  return buffer.readBigInt64BE(offset)
}

export function parseMetadataBuffer(buffer) {
  if (buffer.length < metadataHeaderSizeBytes) {
    throw new Error("Metadata is too short")
  }

  let offset = 0
  const magic = readInt32(buffer, offset, "magic")
  offset += 4
  if (magic !== metadataMagic) {
    throw new Error(`Unknown metadata magic: ${magic}`)
  }

  const schemaVersion = readInt32(buffer, offset, "schema version")
  offset += 4
  if (schemaVersion !== metadataSchemaVersion) {
    throw new Error(`Unsupported metadata schema version: ${schemaVersion}`)
  }

  const sourceCount = readInt32(buffer, offset, "source count")
  offset += 4
  if (sourceCount < 0) {
    throw new Error(`Negative source count: ${sourceCount}`)
  }

  let remainingBytes = buffer.length - metadataHeaderSizeBytes
  const maxSourceCountBySize = Math.floor(remainingBytes / sourceRecordFixedSizeBytes)
  if (sourceCount > maxSourceCountBySize) {
    throw new Error(`Source count is too large for metadata size: ${sourceCount}`)
  }

  let nativeFileCountTotal = 0
  let nativeBlobBytesTotal = 0

  for (let index = 0; index < sourceCount; index++) {
    if (remainingBytes < sourceRecordFixedSizeBytes) {
      throw new Error("Not enough bytes for source metadata")
    }

    const size = readInt32(buffer, offset, "source size")
    offset += 4
    if (size < 0) {
      throw new Error(`Negative source size: ${size}`)
    }

    // Hash is read for structural validation and offset progress.
    readBigInt64(buffer, offset, "source hash")
    offset += 8

    const nativeFileCount = readInt32(buffer, offset, "native file count")
    offset += 4
    if (nativeFileCount < 0) {
      throw new Error(`Negative native file count: ${nativeFileCount}`)
    }
    if (nativeFileCount > maxNativeFileCount) {
      throw new Error(`Too many native files: ${nativeFileCount}`)
    }

    const nativeFilesBlobSize = readInt32(buffer, offset, "native files blob size")
    offset += 4
    if (nativeFilesBlobSize < 0) {
      throw new Error(`Negative native files blob size: ${nativeFilesBlobSize}`)
    }
    if (nativeFilesBlobSize > maxNativeFilesBlobSizeBytes) {
      throw new Error(`Native files blob is too large: ${nativeFilesBlobSize}`)
    }

    remainingBytes -= sourceRecordFixedSizeBytes
    if (remainingBytes < nativeFilesBlobSize) {
      throw new Error("Not enough bytes for native files blob")
    }

    offset += nativeFilesBlobSize
    remainingBytes -= nativeFilesBlobSize

    nativeFileCountTotal += nativeFileCount
    nativeBlobBytesTotal += nativeFilesBlobSize
  }

  if (remainingBytes !== 0) {
    throw new Error(`Unexpected bytes left in metadata: ${remainingBytes}`)
  }

  return {
    schemaVersion,
    sourceCount,
    nativeFileCountTotal,
    nativeBlobBytesTotal,
  }
}

async function parseMetadataFile(metadataPath) {
  try {
    const metadataBytes = await readFile(metadataPath)
    const parsed = parseMetadataBuffer(metadataBytes)
    return {
      ok: true,
      ...parsed,
    }
  }
  catch (error) {
    return {
      ok: false,
      error: error instanceof Error ? error.message : String(error),
    }
  }
}

async function withConcurrency(items, concurrency, worker) {
  if (items.length === 0) {
    return
  }
  let nextIndex = 0
  const workerCount = Math.min(concurrency, items.length)
  const workers = Array.from({length: workerCount}, async () => {
    while (true) {
      const currentIndex = nextIndex
      nextIndex++
      if (currentIndex >= items.length) {
        return
      }
      await worker(items[currentIndex], currentIndex)
    }
  })
  await Promise.all(workers)
}

async function parseMetadataForEntries(entries, options) {
  const entriesWithMetadata = entries.filter((entry) => entry.meta !== null)
  await withConcurrency(entriesWithMetadata, 24, async (entry) => {
    const result = await parseMetadataFile(entry.meta.path)
    entry.metadataParse = result
    if (!result.ok && options.strictMeta) {
      throw new Error(`Invalid metadata at ${entry.meta.path}: ${result.error}`)
    }
  })

  for (const entry of entries) {
    if (entry.meta === null) {
      entry.metadataParse = {ok: false, error: "missing metadata"}
    }
  }
}

async function readMarkers(versionDir) {
  const cleanupMarkerPath = join(versionDir, ".last.cleanup.marker")
  const scanCursorPath = join(versionDir, ".cleanup.scan.cursor")

  let cleanupMarker = null
  let scanCursor = null

  if (await fileExists(cleanupMarkerPath)) {
    const markerStat = await stat(cleanupMarkerPath)
    cleanupMarker = {
      path: cleanupMarkerPath,
      mtimeMs: markerStat.mtimeMs,
      ageMs: Math.max(0, Date.now() - markerStat.mtimeMs),
    }
  }

  if (await fileExists(scanCursorPath)) {
    const cursorRaw = await readFile(scanCursorPath, "utf8")
    scanCursor = {
      path: scanCursorPath,
      value: cursorRaw.trim(),
    }
  }

  return {cleanupMarker, scanCursor}
}

function getRetentionRank(entry) {
  if (entry.meta !== null) {
    return entry.meta.mtimeMs
  }
  if (entry.payload !== null) {
    return entry.payload.mtimeMs
  }
  return 0
}

function buildFindings(report) {
  const findings = []

  if (report.retention.overflowEntryCount > 0) {
    const topReclaimTargets = report.targets.byReclaimableBytes
      .filter((target) => target.reclaimableBytes > 0)
      .slice(0, 3)
      .map((target) => `${target.targetName} (${formatBytes(target.reclaimableBytes)})`)

    findings.push(
      `Per-target version cap overflow: ${formatNumber(report.retention.overflowEntryCount)} entries exceed top-${versionsPerTargetLimit}; ` +
      `estimated reclaimable payload size ${formatBytes(report.retention.reclaimablePayloadBytesByVersionCap)}.` +
      (topReclaimTargets.length > 0 ? ` Top reclaim targets: ${topReclaimTargets.join(", ")}.` : ""),
    )
  }

  const sidecarIssues =
    report.health.counts.missing_meta +
    report.health.counts.missing_payload +
    report.health.counts.orphan_mark

  if (sidecarIssues > 0) {
    findings.push(
      `Sidecar integrity issues: ${formatNumber(sidecarIssues)} entries have missing payload/metadata or orphan marks.`,
    )
  }

  if (report.metadata.invalidCount > 0) {
    findings.push(
      `Metadata integrity: ${formatNumber(report.metadata.invalidCount)} metadata files failed to decode.`,
    )
  }

  if (report.anomalies.rows.length > 0) {
    findings.push(
      `Anomalies detected: ${formatNumber(report.anomalies.rows.length)} targets are outliers by reclaim, churn, payload size, or source-count mix.`,
    )
  }

  if (report.markers.cleanupMarker && report.markers.cleanupMarker.ageMs > 2 * 60 * 60 * 1000) {
    findings.push(
      `Cleanup marker age is ${formatDuration(report.markers.cleanupMarker.ageMs)}; cleanup may be lagging expected cadence.`,
    )
  }

  if (report.shards.topPayloadShare > 0.35) {
    findings.push(
      `Shard skew detected: top ${Math.min(10, report.shards.byPayloadBytes.length)} shards hold ${formatPercent(report.shards.topPayloadShare)} of payload bytes.`,
    )
  }

  if (report.efficiencyScore.score < 70) {
    findings.push(
      `Cache efficiency score is ${report.efficiencyScore.score}/100; retention pressure and/or integrity issues are materially high.`,
    )
  }

  if (findings.length === 0) {
    findings.push("No critical retention or integrity anomalies detected.")
  }

  return findings
}

export async function analyzeJarCache(options = {}) {
  const startedAt = Date.now()
  const top = options.top ?? 20
  const bins = options.bins ?? 12
  const strictMeta = options.strictMeta ?? false
  const anomalyTop = options.anomalyTop ?? defaultAnomalyTop
  const paretoThresholdPct = options.paretoThresholdPct ?? defaultParetoThresholdPct
  const heatmapBins = options.heatmapBins ?? defaultHeatmapBins
  const scoreWeights = options.scoreWeights ?? defaultScoreWeights

  const layout = await detectVersionLayout(options.cacheDir ?? resolve(process.cwd(), "out/dev-run/jar-cache"))
  const markers = await readMarkers(layout.versionDir)
  const scanResult = await scanEntries(layout.entriesDir)
  const entries = scanResult.entries

  await parseMetadataForEntries(entries, {strictMeta})

  const healthCounts = {
    complete: 0,
    missing_meta: 0,
    missing_payload: 0,
    orphan_mark: 0,
    missing_both: 0,
  }

  let payloadBytes = 0
  let metadataBytes = 0
  let markFiles = 0
  let payloadFileCount = 0
  let metadataFileCount = 0

  const metadataErrorCounts = new Map()
  let metadataOkCount = 0
  let metadataInvalidCount = 0

  const targetStats = new Map()
  const shardStats = new Map()
  const metadataAgesDays = []
  const largestEntries = []

  for (const entry of entries) {
    const health = classifyEntryHealth(entry)
    healthCounts[health]++

    if (entry.payload) {
      payloadBytes += entry.payload.sizeBytes
      payloadFileCount++
      largestEntries.push({
        entryStem: entry.entryStem,
        shardName: entry.shardName,
        targetName: entry.targetName ?? "(invalid)",
        sizeBytes: entry.payload.sizeBytes,
        payloadPath: entry.payload.path,
      })
    }
    if (entry.meta) {
      metadataBytes += entry.meta.sizeBytes
      metadataFileCount++
      const ageMs = Math.max(0, Date.now() - entry.meta.mtimeMs)
      metadataAgesDays.push(ageMs / (24 * 60 * 60 * 1000))
      if (entry.metadataParse?.ok) {
        metadataOkCount++
      }
      else {
        metadataInvalidCount++
        const reason = entry.metadataParse?.error ?? "unknown"
        metadataErrorCounts.set(reason, (metadataErrorCounts.get(reason) ?? 0) + 1)
      }
    }
    if (entry.mark) {
      markFiles++
    }

    const shard = shardStats.get(entry.shardName) ?? {
      shardName: entry.shardName,
      entryCount: 0,
      payloadBytes: 0,
    }
    shard.entryCount++
    if (entry.payload) {
      shard.payloadBytes += entry.payload.sizeBytes
    }
    shardStats.set(entry.shardName, shard)

    if (entry.targetName !== null && (entry.payload !== null || entry.meta !== null)) {
      const target = targetStats.get(entry.targetName) ?? {
        targetName: entry.targetName,
        entries: [],
        payloadBytes: 0,
      }
      target.entries.push(entry)
      if (entry.payload) {
        target.payloadBytes += entry.payload.sizeBytes
      }
      targetStats.set(entry.targetName, target)
    }
  }

  const targetRows = []
  let overflowEntryCount = 0
  let reclaimablePayloadBytesByVersionCap = 0
  let targetsOverCap = 0

  for (const target of targetStats.values()) {
    target.entries.sort((a, b) => {
      const rankDiff = getRetentionRank(b) - getRetentionRank(a)
      if (rankDiff !== 0) {
        return rankDiff
      }
      const stemDiff = b.entryStem.localeCompare(a.entryStem)
      if (stemDiff !== 0) {
        return stemDiff
      }
      return b.shardName.localeCompare(a.shardName)
    })

    const overflow = target.entries.slice(versionsPerTargetLimit)
    for (const entry of overflow) {
      entry.isOverflowByVersionCap = true
    }

    if (overflow.length > 0) {
      targetsOverCap++
    }

    overflowEntryCount += overflow.length
    const reclaimableForTarget = overflow.reduce((sum, entry) => sum + (entry.payload?.sizeBytes ?? 0), 0)
    reclaimablePayloadBytesByVersionCap += reclaimableForTarget

    const sourceCounts = target.entries
      .filter((entry) => entry.metadataParse?.ok)
      .map((entry) => entry.metadataParse.sourceCount)

    const totalSourceCount = sourceCounts.reduce((sum, value) => sum + value, 0)
    const avgSourceCount = sourceCounts.length === 0 ? 0 : totalSourceCount / sourceCounts.length
    const maxSourceCount = sourceCounts.length === 0 ? 0 : Math.max(...sourceCounts)

    targetRows.push({
      targetName: target.targetName,
      versions: target.entries.length,
      overflow: overflow.length,
      payloadBytes: target.payloadBytes,
      reclaimableBytes: reclaimableForTarget,
      avgSourceCount,
      maxSourceCount,
    })
  }

  const targetRowsByVersionCount = [...targetRows].sort((a, b) => {
    if (b.versions !== a.versions) {
      return b.versions - a.versions
    }
    if (b.payloadBytes !== a.payloadBytes) {
      return b.payloadBytes - a.payloadBytes
    }
    return a.targetName.localeCompare(b.targetName)
  })

  const targetRowsByReclaimableBytes = [...targetRows].sort((a, b) => {
    if (b.reclaimableBytes !== a.reclaimableBytes) {
      return b.reclaimableBytes - a.reclaimableBytes
    }
    if (b.overflow !== a.overflow) {
      return b.overflow - a.overflow
    }
    if (b.versions !== a.versions) {
      return b.versions - a.versions
    }
    if (b.payloadBytes !== a.payloadBytes) {
      return b.payloadBytes - a.payloadBytes
    }
    return a.targetName.localeCompare(b.targetName)
  })

  const shardRows = Array.from(shardStats.values()).sort((a, b) => {
    if (b.payloadBytes !== a.payloadBytes) {
      return b.payloadBytes - a.payloadBytes
    }
    if (b.entryCount !== a.entryCount) {
      return b.entryCount - a.entryCount
    }
    return a.shardName.localeCompare(b.shardName)
  })

  largestEntries.sort((a, b) => b.sizeBytes - a.sizeBytes)

  const topShardPayload = shardRows.slice(0, 10).reduce((sum, shard) => sum + shard.payloadBytes, 0)
  const topPayloadShare = payloadBytes === 0 ? 0 : topShardPayload / payloadBytes

  const metadataErrorRows = Array.from(metadataErrorCounts.entries())
    .map(([reason, count]) => ({reason, count}))
    .sort((a, b) => b.count - a.count)

  const ageHistogram = makeHistogram(metadataAgesDays, bins)

  const anomalyThresholds = {
    versions: robustUpperFence(targetRows.map((target) => target.versions)),
    reclaimableBytes: robustUpperFence(targetRows.map((target) => target.reclaimableBytes)),
    overflowRatio: robustUpperFence(targetRows.map((target) => safeDivide(target.overflow, target.versions))),
    payloadBytes: robustUpperFence(targetRows.map((target) => target.payloadBytes)),
    avgSourceCount: robustUpperFence(targetRows.map((target) => target.avgSourceCount)),
  }

  const anomalies = buildAnomalies(targetRows, anomalyThresholds, anomalyTop)
  const pareto = computePareto(targetRowsByReclaimableBytes, reclaimablePayloadBytesByVersionCap, paretoThresholdPct)
  const sourceInsights = bucketSourceInsights(entries, heatmapBins, top)

  const problematicEntryShare = safeDivide(
    healthCounts.missing_meta +
    healthCounts.missing_payload +
    healthCounts.orphan_mark +
    healthCounts.missing_both,
    entries.length,
  )

  const invalidMetadataShare = safeDivide(metadataInvalidCount, metadataFileCount)
  const overflowPressure = clamp(safeDivide(overflowEntryCount, Math.max(1, entries.length * 0.25)), 0, 1)
  const reclaimPressure = clamp(safeDivide(reclaimablePayloadBytesByVersionCap, payloadBytes), 0, 1)
  const integrityPenalty = clamp(problematicEntryShare * 0.6 + invalidMetadataShare * 0.4, 0, 1)
  const paretoConcentration = reclaimablePayloadBytesByVersionCap > 0 && targetRowsByReclaimableBytes.length > 0
    ? 1 - safeDivide(pareto.targetsToThreshold, targetRowsByReclaimableBytes.length)
    : 0
  const concentrationPenalty = clamp(topPayloadShare * 0.5 + paretoConcentration * 0.5, 0, 1)

  const efficiencyScore = computeEfficiencyScore({
    overflowPressure,
    reclaimPressure,
    integrityPenalty,
    concentrationPenalty,
  }, scoreWeights)

  const report = {
    toolVersion: "2",
    presentation: {
      versionSortRule: "versions desc, payloadBytes desc, targetName asc",
      reclaimSortRule: "reclaimableBytes desc, overflow desc, versions desc, payloadBytes desc, targetName asc",
      generatedFor: "agnostic",
    },
    generatedAt: new Date().toISOString(),
    cacheDir: layout.cacheRootDir,
    versionDir: layout.versionDir,
    entriesDir: layout.entriesDir,
    scanDurationMs: Date.now() - startedAt,
    overview: {
      totalEntries: entries.length,
      totalTargets: targetStats.size,
      totalShards: scanResult.shardNames.length,
      payloadFileCount,
      metadataFileCount,
      markFileCount: markFiles,
      payloadBytes,
      metadataBytes,
    },
    markers,
    health: {
      counts: healthCounts,
      problematicEntryCount:
        healthCounts.missing_meta +
        healthCounts.missing_payload +
        healthCounts.orphan_mark +
        healthCounts.missing_both,
    },
    retention: {
      versionsPerTargetLimit,
      targetsOverCap,
      overflowEntryCount,
      reclaimablePayloadBytesByVersionCap,
    },
    metadata: {
      okCount: metadataOkCount,
      invalidCount: metadataInvalidCount,
      errorReasons: metadataErrorRows,
    },
    ageProfile: {
      binUnit: "days",
      bins: ageHistogram,
    },
    shards: {
      byPayloadBytes: shardRows,
      topPayloadShare,
    },
    targets: {
      byVersionCount: targetRowsByVersionCount,
      byReclaimableBytes: targetRowsByReclaimableBytes,
    },
    anomalies: {
      thresholds: anomalyThresholds,
      rows: anomalies,
    },
    pareto,
    efficiencyScore,
    sourceInsights,
    largestEntries,
  }

  report.findings = buildFindings(report)
  return report
}

export function formatHumanReport(report, options = {}) {
  const top = options.top ?? 20
  const useColor = options.useColor ?? false
  const lines = []

  const section = (title) => {
    lines.push("")
    lines.push(colorize(title, ansi.cyan, useColor))
  }

  lines.push(colorize("Jar Cache Analyzer", ansi.green, useColor))
  lines.push(`  Cache root: ${report.cacheDir}`)
  lines.push(`  Version dir: ${report.versionDir}`)
  lines.push(`  Generated: ${report.generatedAt}`)
  lines.push(`  Scan time: ${formatDuration(report.scanDurationMs)}`)

  section("Overview")
  lines.push(`  Entries: ${formatNumber(report.overview.totalEntries)} across ${formatNumber(report.overview.totalShards)} shards and ${formatNumber(report.overview.totalTargets)} targets`)
  lines.push(`  Payload: ${formatBytes(report.overview.payloadBytes)} in ${formatNumber(report.overview.payloadFileCount)} files`)
  lines.push(`  Metadata: ${formatBytes(report.overview.metadataBytes)} in ${formatNumber(report.overview.metadataFileCount)} files`)
  lines.push(`  Mark files: ${formatNumber(report.overview.markFileCount)}`)
  if (report.markers.cleanupMarker) {
    lines.push(`  Last cleanup marker age: ${formatDuration(report.markers.cleanupMarker.ageMs)}`)
  }
  else {
    lines.push("  Last cleanup marker: missing")
  }
  if (report.markers.scanCursor) {
    lines.push(`  Scan cursor: ${report.markers.scanCursor.value || "(empty)"}`)
  }
  else {
    lines.push("  Scan cursor: missing")
  }

  section("Entry Health")
  const healthRows = [
    {label: "complete", value: report.health.counts.complete},
    {label: "missing_meta", value: report.health.counts.missing_meta},
    {label: "missing_payload", value: report.health.counts.missing_payload},
    {label: "orphan_mark", value: report.health.counts.orphan_mark},
    {label: "missing_both", value: report.health.counts.missing_both},
  ]
  lines.push(...renderBarChart(healthRows, {formatValue: (value) => formatNumber(value)}))

  section("Retention Pressure")
  lines.push(`  Targets over top-${versionsPerTargetLimit}: ${formatNumber(report.retention.targetsOverCap)}`)
  lines.push(`  Overflow entries: ${formatNumber(report.retention.overflowEntryCount)}`)
  lines.push(`  Estimated reclaimable payload (cap): ${formatBytes(report.retention.reclaimablePayloadBytesByVersionCap)}`)

  lines.push("  Top by reclaimable payload:")
  lines.push(...renderTable(
    [
      {key: "target", title: "Target", maxWidth: 40},
      {key: "overflow", title: "Overflow", align: "right"},
      {key: "versions", title: "Versions", align: "right"},
      {key: "payload", title: "Payload", align: "right"},
      {key: "reclaim", title: "Reclaim", align: "right"},
      {key: "avgSrc", title: "AvgSrc", align: "right"},
    ],
    report.targets.byReclaimableBytes.slice(0, top).map((item) => ({
      target: item.targetName,
      overflow: formatNumber(item.overflow),
      versions: formatNumber(item.versions),
      payload: formatBytes(item.payloadBytes),
      reclaim: formatBytes(item.reclaimableBytes),
      avgSrc: item.avgSourceCount.toFixed(1),
    })),
  ))

  lines.push("  Top by version count:")
  lines.push(...renderTable(
    [
      {key: "target", title: "Target", maxWidth: 40},
      {key: "versions", title: "Versions", align: "right"},
      {key: "overflow", title: "Overflow", align: "right"},
      {key: "payload", title: "Payload", align: "right"},
      {key: "reclaim", title: "Reclaim", align: "right"},
    ],
    report.targets.byVersionCount.slice(0, top).map((item) => ({
      target: item.targetName,
      versions: formatNumber(item.versions),
      overflow: formatNumber(item.overflow),
      payload: formatBytes(item.payloadBytes),
      reclaim: formatBytes(item.reclaimableBytes),
    })),
  ))

  section("Anomalies")
  const anomalyRows = Array.isArray(report.anomalies?.rows) ? report.anomalies.rows : []
  lines.push(`  Detected target anomalies: ${formatNumber(anomalyRows.length)}`)
  lines.push(...renderTable(
    [
      {key: "severity", title: "Severity", align: "right"},
      {key: "target", title: "Target", maxWidth: 36},
      {key: "reasons", title: "Reasons", maxWidth: 40},
      {key: "reclaim", title: "Reclaim", align: "right"},
      {key: "versions", title: "Versions", align: "right"},
      {key: "avgSrc", title: "AvgSrc", align: "right"},
    ],
    anomalyRows.slice(0, top).map((item) => ({
      severity: item.severity.toFixed(2),
      target: item.targetName,
      reasons: item.reasons.map((reason) => reason.split("_").join(" ")).join(", "),
      reclaim: formatBytes(item.reclaimableBytes),
      versions: formatNumber(item.versions),
      avgSrc: item.avgSourceCount.toFixed(1),
    })),
  ))

  section("Pareto (Reclaimable Bytes)")
  lines.push(`  Threshold: ${report.pareto.targetCoverageThresholdPct.toFixed(1)}% reclaimable coverage`)
  lines.push(`  Targets needed: ${formatNumber(report.pareto.targetsToThreshold)} (${formatPercent(safeDivide(report.pareto.targetsToThreshold, Math.max(1, report.targets.byReclaimableBytes.length)))} of targets)`)
  lines.push(`  Reclaim share at threshold: ${formatPercent(report.pareto.reclaimableShareAtThreshold)}`)
  lines.push(...renderTable(
    [
      {key: "rank", title: "#", align: "right"},
      {key: "target", title: "Target", maxWidth: 36},
      {key: "reclaim", title: "Reclaim", align: "right"},
      {key: "cum", title: "Cumulative", align: "right"},
    ],
    report.pareto.cumulativeRows.slice(0, top).map((item, index) => ({
      rank: formatNumber(index + 1),
      target: item.targetName,
      reclaim: formatBytes(item.reclaimableBytes),
      cum: formatPercent(item.cumulativeShare),
    })),
  ))

  section("Efficiency Score")
  const scoreBarWidth = 32
  const scoreBarFilled = Math.round((report.efficiencyScore.score / 100) * scoreBarWidth)
  const scoreBar = `${"#".repeat(scoreBarFilled)}${".".repeat(Math.max(0, scoreBarWidth - scoreBarFilled))}`
  lines.push(`  Score: ${report.efficiencyScore.score}/100 [${scoreBar}]`)
  lines.push(...renderTable(
    [
      {key: "component", title: "Component", maxWidth: 20},
      {key: "value", title: "Value", align: "right"},
      {key: "weighted", title: "Weighted", align: "right"},
    ],
    [
      {
        component: "overflow pressure",
        value: formatPercent(report.efficiencyScore.components.overflowPressure),
        weighted: formatPercent(report.efficiencyScore.penalties.overflow),
      },
      {
        component: "reclaim pressure",
        value: formatPercent(report.efficiencyScore.components.reclaimPressure),
        weighted: formatPercent(report.efficiencyScore.penalties.reclaim),
      },
      {
        component: "integrity penalty",
        value: formatPercent(report.efficiencyScore.components.integrityPenalty),
        weighted: formatPercent(report.efficiencyScore.penalties.integrity),
      },
      {
        component: "concentration penalty",
        value: formatPercent(report.efficiencyScore.components.concentrationPenalty),
        weighted: formatPercent(report.efficiencyScore.penalties.concentration),
      },
      {
        component: "total penalty",
        value: formatPercent(report.efficiencyScore.penalties.total),
        weighted: "-",
      },
    ],
  ))

  section("Source Count Insights")
  lines.push(`  Metadata coverage: ${formatPercent(report.sourceInsights.coveragePct)} of payload entries`)
  lines.push(`  Correlation (source-count vs payload): ${report.sourceInsights.correlations.sourceCountVsPayloadPearson.toFixed(3)}`)
  lines.push("  Source-count bucket payload heatmap:")
  lines.push(...renderBarChart(
    report.sourceInsights.buckets.map((bucket) => ({
      label: `${bucket.minSourceCount}-${bucket.maxSourceCount}`,
      value: bucket.payloadBytes,
    })),
    {formatValue: (value) => formatBytes(value), width: 24, maxLabelLength: 20},
  ))
  lines.push(...renderTable(
    [
      {key: "bucket", title: "Bucket", maxWidth: 20},
      {key: "entries", title: "Entries", align: "right"},
      {key: "payload", title: "Payload", align: "right"},
      {key: "avg", title: "AvgPayload", align: "right"},
      {key: "reclaim", title: "Reclaim", align: "right"},
    ],
    report.sourceInsights.buckets.map((bucket) => ({
      bucket: `${bucket.minSourceCount}-${bucket.maxSourceCount}`,
      entries: formatNumber(bucket.entryCount),
      payload: formatBytes(bucket.payloadBytes),
      avg: formatBytes(bucket.avgPayloadBytes),
      reclaim: formatBytes(bucket.reclaimableBytes),
    })),
  ))
  lines.push("  Source-count outliers:")
  lines.push(...renderTable(
    [
      {key: "target", title: "Target", maxWidth: 34},
      {key: "sourceCount", title: "Sources", align: "right"},
      {key: "payload", title: "Payload", align: "right"},
      {key: "reclaim", title: "Reclaim", align: "right"},
      {key: "entry", title: "Entry", maxWidth: 34},
    ],
    report.sourceInsights.topOutliers.slice(0, top).map((item) => ({
      target: item.targetName,
      sourceCount: formatNumber(item.sourceCount),
      payload: formatBytes(item.payloadBytes),
      reclaim: formatBytes(item.reclaimableBytes),
      entry: `${item.shardName}/${item.entryStem}`,
    })),
  ))

  section("Age Profile (Metadata mtime)")
  lines.push(...renderBarChart(
    report.ageProfile.bins.map((bin) => ({
      label: `${bin.from.toFixed(1)}-${bin.to.toFixed(1)}d`,
      value: bin.count,
    })),
    {formatValue: (value) => formatNumber(value)},
  ))

  section("Shard Skew")
  lines.push(`  Top 10 shard payload share: ${formatPercent(report.shards.topPayloadShare)}`)
  lines.push(...renderBarChart(
    report.shards.byPayloadBytes.slice(0, top).map((shard) => ({
      label: shard.shardName,
      value: shard.payloadBytes,
    })),
    {formatValue: (value) => formatBytes(value)},
  ))

  section("Metadata Integrity")
  lines.push(...renderBarChart(
    [
      {label: "ok", value: report.metadata.okCount},
      {label: "invalid", value: report.metadata.invalidCount},
    ],
    {formatValue: (value) => formatNumber(value)},
  ))
  if (report.metadata.errorReasons.length > 0) {
    lines.push("  Top decode errors:")
    lines.push(...renderTable(
      [
        {key: "count", title: "Count", align: "right"},
        {key: "reason", title: "Reason", maxWidth: 120},
      ],
      report.metadata.errorReasons.slice(0, top).map((item) => ({
        count: formatNumber(item.count),
        reason: item.reason,
      })),
    ))
  }

  section("Top Largest Entries")
  lines.push(...renderTable(
    [
      {key: "size", title: "Size", align: "right"},
      {key: "target", title: "Target", maxWidth: 40},
      {key: "shard", title: "Shard"},
      {key: "stem", title: "Stem", maxWidth: 60},
    ],
    report.largestEntries.slice(0, top).map((item) => ({
      size: formatBytes(item.sizeBytes),
      target: item.targetName,
      shard: item.shardName,
      stem: item.entryStem,
    })),
  ))

  section("Actionable Findings")
  for (const finding of report.findings) {
    lines.push(`  - ${finding}`)
  }

  lines.push("")
  return lines.join("\n")
}

export function formatMarkdownReport(report, options = {}) {
  const top = options.top ?? 10
  const lines = []
  const topByReclaim = report.targets.byReclaimableBytes.slice(0, top)
  const topByVersions = report.targets.byVersionCount.slice(0, top)
  const totalReclaimable = report.retention.reclaimablePayloadBytesByVersionCap
  const maxVersions = topByVersions[0]?.versions ?? 0

  lines.push("# Jar Cache Report")
  lines.push("")
  lines.push(`- Cache root: \`${report.cacheDir}\``)
  lines.push(`- Version dir: \`${report.versionDir}\``)
  lines.push(`- Generated: ${report.generatedAt}`)
  lines.push(`- Scan time: ${formatDuration(report.scanDurationMs)}`)

  lines.push("")
  lines.push("## Executive Summary")
  lines.push("")
  lines.push(...renderMarkdownTable(
    [
      {key: "metric", title: "Metric"},
      {key: "value", title: "Value", align: "right"},
    ],
    [
      {metric: "Total entries", value: formatNumber(report.overview.totalEntries)},
      {metric: "Total payload", value: formatBytes(report.overview.payloadBytes)},
      {metric: "Targets over top-3", value: formatNumber(report.retention.targetsOverCap)},
      {metric: "Overflow entries", value: formatNumber(report.retention.overflowEntryCount)},
      {metric: "Reclaimable payload", value: formatBytes(report.retention.reclaimablePayloadBytesByVersionCap)},
      {metric: "Efficiency score", value: `${report.efficiencyScore.score}/100`},
      {metric: "Cleanup marker age", value: report.markers.cleanupMarker ? formatDuration(report.markers.cleanupMarker.ageMs) : "missing"},
    ],
  ))

  lines.push("")
  lines.push("## Top Offenders")
  lines.push("")
  lines.push(`### By Reclaimable Bytes (Top ${top})`)
  lines.push("")
  lines.push(`_Sort: ${report.presentation?.reclaimSortRule ?? "reclaimableBytes desc"}_`)
  lines.push("")
  lines.push(...renderMarkdownTable(
    [
      {key: "rank", title: "Rank", align: "right"},
      {key: "target", title: "Target"},
      {key: "versions", title: "Versions", align: "right"},
      {key: "overflow", title: "Overflow", align: "right"},
      {key: "reclaim", title: "Reclaimable", align: "right"},
      {key: "reclaimShare", title: "Reclaim Share", align: "right"},
      {key: "payload", title: "Payload", align: "right"},
      {key: "avgSrc", title: "Avg Sources", align: "right"},
    ],
    topByReclaim.map((item, index) => ({
      rank: index + 1,
      target: item.targetName,
      versions: formatNumber(item.versions),
      overflow: formatNumber(item.overflow),
      reclaim: formatBytes(item.reclaimableBytes),
      reclaimShare: formatPercent(safeDivide(item.reclaimableBytes, totalReclaimable)),
      payload: formatBytes(item.payloadBytes),
      avgSrc: item.avgSourceCount.toFixed(1),
    })),
  ))

  lines.push("")
  lines.push(`### By Version Count (Top ${top})`)
  lines.push("")
  lines.push(`_Sort: ${report.presentation?.versionSortRule ?? "versions desc"}_`)
  lines.push("")
  lines.push(...renderMarkdownTable(
    [
      {key: "rank", title: "Rank", align: "right"},
      {key: "target", title: "Target"},
      {key: "versions", title: "Versions", align: "right"},
      {key: "maxShare", title: "% of Max", align: "right"},
      {key: "overflow", title: "Overflow", align: "right"},
      {key: "overflowRatio", title: "Overflow Ratio", align: "right"},
      {key: "payload", title: "Payload", align: "right"},
      {key: "reclaim", title: "Reclaimable", align: "right"},
    ],
    topByVersions.map((item, index) => ({
      rank: index + 1,
      target: item.targetName,
      versions: formatNumber(item.versions),
      maxShare: formatPercent(safeDivide(item.versions, Math.max(1, maxVersions))),
      overflow: formatNumber(item.overflow),
      overflowRatio: formatPercent(safeDivide(item.overflow, Math.max(1, item.versions))),
      payload: formatBytes(item.payloadBytes),
      reclaim: formatBytes(item.reclaimableBytes),
    })),
  ))

  lines.push("")
  lines.push("## Anomalies")
  lines.push("")
  const anomalyRows = Array.isArray(report.anomalies?.rows) ? report.anomalies.rows : []
  lines.push(...renderMarkdownTable(
    [
      {key: "severity", title: "Severity", align: "right"},
      {key: "target", title: "Target"},
      {key: "reasons", title: "Reasons"},
      {key: "reclaim", title: "Reclaimable", align: "right"},
      {key: "versions", title: "Versions", align: "right"},
    ],
    anomalyRows.slice(0, top).map((item) => ({
      severity: item.severity.toFixed(2),
      target: item.targetName,
      reasons: item.reasons.join(", "),
      reclaim: formatBytes(item.reclaimableBytes),
      versions: formatNumber(item.versions),
    })),
  ))

  lines.push("")
  lines.push("## Pareto")
  lines.push("")
  lines.push(`- Threshold: ${report.pareto.targetCoverageThresholdPct.toFixed(1)}%`)
  lines.push(`- Targets needed: ${formatNumber(report.pareto.targetsToThreshold)} / ${formatNumber(report.targets.byReclaimableBytes.length)}`)
  lines.push(`- Reclaim share at threshold: ${formatPercent(report.pareto.reclaimableShareAtThreshold)}`)
  lines.push("")
  lines.push(...renderMarkdownTable(
    [
      {key: "rank", title: "Rank", align: "right"},
      {key: "target", title: "Target"},
      {key: "reclaim", title: "Reclaimable", align: "right"},
      {key: "cumulative", title: "Cumulative", align: "right"},
    ],
    report.pareto.cumulativeRows.slice(0, top).map((item, index) => ({
      rank: index + 1,
      target: item.targetName,
      reclaim: formatBytes(item.reclaimableBytes),
      cumulative: formatPercent(item.cumulativeShare),
    })),
  ))

  lines.push("")
  lines.push("## Source Count Insights")
  lines.push("")
  lines.push(`- Metadata coverage: ${formatPercent(report.sourceInsights.coveragePct)}`)
  lines.push(`- Correlation (source-count vs payload): ${report.sourceInsights.correlations.sourceCountVsPayloadPearson.toFixed(3)}`)
  lines.push("")
  lines.push(...renderMarkdownTable(
    [
      {key: "bucket", title: "Bucket"},
      {key: "entries", title: "Entries", align: "right"},
      {key: "payload", title: "Payload", align: "right"},
      {key: "avg", title: "Avg Payload", align: "right"},
      {key: "reclaim", title: "Reclaimable", align: "right"},
      {key: "share", title: "Payload Share", align: "right"},
    ],
    report.sourceInsights.buckets.map((bucket, index, all) => ({
      bucket: `Q${index + 1}/${all.length} (sources ${bucket.minSourceCount}-${bucket.maxSourceCount})`,
      entries: formatNumber(bucket.entryCount),
      payload: formatBytes(bucket.payloadBytes),
      avg: formatBytes(bucket.avgPayloadBytes),
      reclaim: formatBytes(bucket.reclaimableBytes),
      share: formatPercent(safeDivide(bucket.payloadBytes, report.overview.payloadBytes)),
    })),
  ))

  lines.push("")
  lines.push("## Shard Distribution")
  lines.push("")
  lines.push(`- Top 10 shard payload share: ${formatPercent(report.shards.topPayloadShare)}`)
  lines.push("")
  lines.push(...renderMarkdownTable(
    [
      {key: "shard", title: "Shard"},
      {key: "entries", title: "Entries", align: "right"},
      {key: "payload", title: "Payload", align: "right"},
    ],
    report.shards.byPayloadBytes.slice(0, top).map((shard) => ({
      shard: shard.shardName,
      entries: formatNumber(shard.entryCount),
      payload: formatBytes(shard.payloadBytes),
    })),
  ))

  lines.push("")
  lines.push("## Metadata Integrity")
  lines.push("")
  lines.push(...renderMarkdownTable(
    [
      {key: "status", title: "Status"},
      {key: "count", title: "Count", align: "right"},
    ],
    [
      {status: "ok", count: formatNumber(report.metadata.okCount)},
      {status: "invalid", count: formatNumber(report.metadata.invalidCount)},
    ],
  ))

  lines.push("")
  lines.push("## Largest Entries")
  lines.push("")
  lines.push(...renderMarkdownTable(
    [
      {key: "size", title: "Size", align: "right"},
      {key: "target", title: "Target"},
      {key: "shard", title: "Shard"},
      {key: "entry", title: "Entry"},
    ],
    report.largestEntries.slice(0, top).map((item) => ({
      size: formatBytes(item.sizeBytes),
      target: item.targetName,
      shard: item.shardName,
      entry: item.entryStem,
    })),
  ))

  const actionRows = report.targets.byReclaimableBytes
    .filter((item) => item.reclaimableBytes > 0)
    .slice(0, top)
    .map((item) => {
      const reclaimShare = safeDivide(item.reclaimableBytes, totalReclaimable)
      const priority = reclaimShare >= 0.1 ? "P1" : reclaimShare >= 0.03 ? "P2" : "P3"
      return {
        priority,
        target: item.targetName,
        reason: item.versions > versionsPerTargetLimit
          ? `version churn (${item.versions} versions)`
          : "reclaimable payload concentration",
        reclaim: formatBytes(item.reclaimableBytes),
        action: item.versions > versionsPerTargetLimit
          ? `investigate producer churn; keep top-${versionsPerTargetLimit}`
          : "review target churn and retention relevance",
        confidence: item.versions > versionsPerTargetLimit ? "High" : "Medium",
      }
    })

  lines.push("")
  lines.push("## Action Plan")
  lines.push("")
  lines.push(...renderMarkdownTable(
    [
      {key: "priority", title: "Priority"},
      {key: "target", title: "Target"},
      {key: "reason", title: "Why Flagged"},
      {key: "reclaim", title: "Est. Reclaim", align: "right"},
      {key: "action", title: "Suggested Action"},
      {key: "confidence", title: "Confidence"},
    ],
    actionRows.length > 0 ? actionRows : [{
      priority: "P3",
      target: "(none)",
      reason: "No reclaimable entries above cap",
      reclaim: "0 B",
      action: "No retention action needed",
      confidence: "High",
    }],
  ))

  lines.push("")
  return lines.join("\n")
}

function createDefaultIo() {
  return {
    stdout: (message) => process.stdout.write(`${message}\n`),
    stderr: (message) => process.stderr.write(`${message}\n`),
  }
}

export async function runCli(argv = (process["argv"] ?? []).slice(2), io = createDefaultIo()) {
  let args
  try {
    args = parseArguments(argv)
  }
  catch (error) {
    if (error instanceof UsageError) {
      io.stderr(`${error.message}\n\n${printUsage()}`)
      return usageExitCode
    }
    io.stderr(String(error))
    return commandExitCode
  }

  if (args.help) {
    io.stdout(printUsage())
    return 0
  }

  try {
    const report = await analyzeJarCache({
      cacheDir: args.cacheDir,
      top: args.top,
      bins: args.bins,
      anomalyTop: args.anomalyTop,
      paretoThresholdPct: args.paretoThresholdPct,
      heatmapBins: args.heatmapBins,
      scoreWeights: args.scoreWeights,
      strictMeta: args.strictMeta,
    })

    const markdownReport = formatMarkdownReport(report, {top: args.top})

    if (args.json) {
      io.stdout(JSON.stringify(report, null, 2))
    }
    else {
      const humanOutput = args.useColor
        ? formatHumanReport(report, {top: args.top, useColor: true})
        : formatHumanReport(report, {top: args.top, useColor: false})
      io.stdout(humanOutput)
    }

    if (args.writeMarkdownFile) {
      const markdownFilePath = args.markdownFile ?? join(report.cacheDir, defaultMarkdownReportFileName)
      await writeFile(markdownFilePath, `${markdownReport}\n`, "utf8")
      if (!args.json) {
        io.stdout(`Markdown report written to ${markdownFilePath}`)
      }
    }

    if (args.jsonFile) {
      await writeFile(args.jsonFile, JSON.stringify(report, null, 2) + "\n", "utf8")
      if (!args.json) {
        io.stdout(`JSON report written to ${args.jsonFile}`)
      }
    }

    return 0
  }
  catch (error) {
    io.stderr(error instanceof Error ? `Error: ${error.message}` : `Error: ${String(error)}`)
    return commandExitCode
  }
}

if (isMainModule()) {
  const exitCode = await runCli()
  process.exit(exitCode)
}
