// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {describe, it} from "node:test"
import {equal, match, ok, throws} from "node:assert/strict"
import {Buffer} from "node:buffer"
import {mkdir, mkdtemp, readFile, rm, stat, utimes, writeFile} from "node:fs/promises"
import {tmpdir} from "node:os"
import {join} from "node:path"
import {analyzeJarCache, formatHumanReport, formatMarkdownReport, parseMetadataBuffer, runCli} from "./jar-cache-analyze.mjs"

const metadataMagic = 0x4A434D31
const metadataSchemaVersion = 2

/**
 * @param {{size: number, hash: bigint, nativeFileCount: number, nativeBlob: Buffer}[]} records
 */
function createMetadataBuffer(records) {
  const payloadSize = records.reduce((sum, record) => sum + 20 + record.nativeBlob.length, 0)
  const buffer = Buffer.alloc(12 + payloadSize)
  let offset = 0

  buffer.writeInt32BE(metadataMagic, offset)
  offset += 4
  buffer.writeInt32BE(metadataSchemaVersion, offset)
  offset += 4
  buffer.writeInt32BE(records.length, offset)
  offset += 4

  for (const record of records) {
    buffer.writeInt32BE(record.size, offset)
    offset += 4
    buffer.writeBigInt64BE(record.hash, offset)
    offset += 8
    buffer.writeInt32BE(record.nativeFileCount, offset)
    offset += 4
    buffer.writeInt32BE(record.nativeBlob.length, offset)
    offset += 4
    record.nativeBlob.copy(buffer, offset)
    offset += record.nativeBlob.length
  }

  return buffer
}

function createMetadataBufferWithSourceCount(sourceCount) {
  const records = Array.from({length: sourceCount}, (_, index) => ({
    size: index + 1,
    hash: BigInt(index + 1),
    nativeFileCount: 0,
    nativeBlob: Buffer.alloc(0),
  }))
  return createMetadataBuffer(records)
}

async function createFixtureRoot() {
  return mkdtemp(join(tmpdir(), "jar-cache-analyze-test-"))
}

async function createCacheSkeleton(rootDir) {
  const cacheDir = join(rootDir, "cache")
  const entriesDir = join(cacheDir, "v0", "entries")
  await mkdir(entriesDir, {recursive: true})
  return {cacheDir, entriesDir}
}

async function pathExists(path) {
  try {
    await stat(path)
    return true
  }
  catch {
    return false
  }
}

function buildEntryStem(index, targetName) {
  const value = String(index).padStart(6, "0")
  return `aa${value}-bb${value}__${targetName}`
}

async function writeEntryFileSet(entriesDir, shardName, entryStem, options = {}) {
  const shardDir = join(entriesDir, shardName)
  await mkdir(shardDir, {recursive: true})

  const payloadPath = join(shardDir, entryStem)
  const metadataPath = `${payloadPath}.meta`
  const markPath = `${payloadPath}.mark`

  const payloadMtimeMs = options["payloadMtimeMs"]
  const metadataMtimeMs = options["metadataMtimeMs"]

  if (options.payload !== false) {
    const payloadSize = options.payloadSize ?? 64
    await writeFile(payloadPath, Buffer.alloc(payloadSize, 7))
    if (payloadMtimeMs !== undefined) {
      const mtimeDate = new Date(payloadMtimeMs)
      await utimes(payloadPath, mtimeDate, mtimeDate)
    }
  }

  if (options.metadata !== false) {
    const metadataBuffer = options.metadataBuffer ?? createMetadataBuffer([
      {size: 1, hash: 1n, nativeFileCount: 0, nativeBlob: Buffer.alloc(0)},
    ])
    await writeFile(metadataPath, metadataBuffer)
    if (metadataMtimeMs !== undefined) {
      const mtimeDate = new Date(metadataMtimeMs)
      await utimes(metadataPath, mtimeDate, mtimeDate)
    }
  }

  if (options.mark === true) {
    await writeFile(markPath, "")
  }
}

describe("jar-cache-analyze metadata parsing", () => {
  it("parses valid metadata", () => {
    const buffer = createMetadataBuffer([
      {size: 10, hash: 123n, nativeFileCount: 2, nativeBlob: Buffer.from("a\u0000b")},
      {size: 20, hash: 456n, nativeFileCount: 0, nativeBlob: Buffer.alloc(0)},
    ])

    const parsed = parseMetadataBuffer(buffer)
    equal(parsed.schemaVersion, metadataSchemaVersion)
    equal(parsed.sourceCount, 2)
    equal(parsed.nativeFileCountTotal, 2)
    equal(parsed.nativeBlobBytesTotal, 3)
  })

  it("fails on invalid metadata magic", () => {
    const buffer = createMetadataBuffer([{size: 1, hash: 1n, nativeFileCount: 0, nativeBlob: Buffer.alloc(0)}])
    buffer.writeInt32BE(0x12345678, 0)
    throws(() => parseMetadataBuffer(buffer), /Unknown metadata magic/)
  })
})

describe("jar-cache-analyze aggregation", () => {
  it("computes retention overflow, health, and metadata error stats", async () => {
    const rootDir = await createFixtureRoot()
    try {
      const {cacheDir, entriesDir} = await createCacheSkeleton(rootDir)
      const now = Date.now()

      await writeEntryFileSet(entriesDir, "aa", buildEntryStem(1, "plugin.jar"), {
        payloadSize: 100,
        metadataMtimeMs: now - 4 * 24 * 60 * 60 * 1000,
      })
      await writeEntryFileSet(entriesDir, "aa", buildEntryStem(2, "plugin.jar"), {
        payloadSize: 110,
        metadataMtimeMs: now - 3 * 24 * 60 * 60 * 1000,
      })
      await writeEntryFileSet(entriesDir, "aa", buildEntryStem(3, "plugin.jar"), {
        payloadSize: 120,
        metadataMtimeMs: now - 2 * 24 * 60 * 60 * 1000,
      })
      await writeEntryFileSet(entriesDir, "aa", buildEntryStem(4, "plugin.jar"), {
        payloadSize: 130,
        metadataMtimeMs: now - 1 * 24 * 60 * 60 * 1000,
      })

      await writeEntryFileSet(entriesDir, "ab", buildEntryStem(10, "broken.jar"), {
        payloadSize: 42,
        metadataBuffer: Buffer.from("broken"),
      })

      await writeEntryFileSet(entriesDir, "ac", buildEntryStem(11, "orphan.jar"), {
        payload: false,
        metadata: false,
        mark: true,
      })

      const report = await analyzeJarCache({
        cacheDir,
        top: 10,
        bins: 6,
      })

      equal(report.retention.targetsOverCap, 1)
      equal(report.retention.overflowEntryCount, 1)
      equal(report.retention.reclaimablePayloadBytesByVersionCap, 100)
      equal(report.health.counts.orphan_mark, 1)
      equal(report.metadata.invalidCount, 1)
      ok(report.findings.length > 0)

      const human = formatHumanReport(report, {top: 5, useColor: false})
      match(human, /Entry Health/)
      match(human, /Retention Pressure/)
      match(human, /Anomalies/)
      match(human, /Pareto \(Reclaimable Bytes\)/)
      match(human, /Efficiency Score/)
      match(human, /Source Count Insights/)
      match(human, /Top Largest Entries/)
      match(human, /#/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("supports --json output and strict metadata mode", async () => {
    const rootDir = await createFixtureRoot()
    try {
      const {cacheDir, entriesDir} = await createCacheSkeleton(rootDir)
      await writeEntryFileSet(entriesDir, "aa", buildEntryStem(1, "ok.jar"), {
        payloadSize: 50,
      })
      await writeEntryFileSet(entriesDir, "aa", buildEntryStem(2, "bad.jar"), {
        payloadSize: 30,
        metadataBuffer: Buffer.from("broken"),
      })

      const out = []
      const err = []
      const jsonCode = await runCli([
        "--cache-dir",
        cacheDir,
        "--json",
      ], {
        stdout: (value) => out.push(value),
        stderr: (value) => err.push(value),
      })

      equal(jsonCode, 0)
      equal(err.length, 0)
      const parsedReport = JSON.parse(out.join("\n"))
      equal(parsedReport.overview.totalEntries, 2)
      equal(parsedReport.metadata.invalidCount, 1)

      const defaultMarkdownPath = join(cacheDir, "jar-cache-report.md")
      const markdownText = await readFile(defaultMarkdownPath, "utf8")
      match(markdownText, /# Jar Cache Report/)
      match(markdownText, /## Executive Summary/)
      match(markdownText, /\| Metric \| Value \|/)

      const strictErrors = []
      const strictCode = await runCli([
        "--cache-dir",
        cacheDir,
        "--strict-meta",
      ], {
        stdout: () => {},
        stderr: (value) => strictErrors.push(value),
      })

      equal(strictCode, 3)
      ok(strictErrors.some((line) => line.includes("Invalid metadata")))
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("writes markdown report by default and supports markdown flags", async () => {
    const rootDir = await createFixtureRoot()
    try {
      const {cacheDir, entriesDir} = await createCacheSkeleton(rootDir)
      await writeEntryFileSet(entriesDir, "aa", buildEntryStem(1, "default-md.jar"), {
        payloadSize: 64,
      })

      const markdownPath = join(cacheDir, "jar-cache-report.md")

      const defaultOut = []
      const defaultCode = await runCli([
        "--cache-dir",
        cacheDir,
      ], {
        stdout: (value) => defaultOut.push(value),
        stderr: () => {},
      })

      equal(defaultCode, 0)
      ok(defaultOut.some((line) => line.includes("Markdown report written to")))
      ok(await pathExists(markdownPath))

      await rm(markdownPath, {force: true})

      const noMdCode = await runCli([
        "--cache-dir",
        cacheDir,
        "--no-md-file",
      ], {
        stdout: () => {},
        stderr: () => {},
      })

      equal(noMdCode, 0)
      equal(await pathExists(markdownPath), false)

      const customMarkdownPath = join(rootDir, "custom-report.md")
      const customCode = await runCli([
        "--cache-dir",
        cacheDir,
        "--md-file",
        customMarkdownPath,
      ], {
        stdout: () => {},
        stderr: () => {},
      })

      equal(customCode, 0)
      ok(await pathExists(customMarkdownPath))
      const customMarkdownText = await readFile(customMarkdownPath, "utf8")
      match(customMarkdownText, /## Top Offenders/)
      match(customMarkdownText, /### By Reclaimable Bytes/)
      match(customMarkdownText, /\| Rank \| Target \| Versions \| Overflow \| Reclaimable \|/)

      await rm(customMarkdownPath, {force: true})
      const disabledOverrideCode = await runCli([
        "--cache-dir",
        cacheDir,
        "--md-file",
        customMarkdownPath,
        "--no-md-file",
      ], {
        stdout: () => {},
        stderr: () => {},
      })

      equal(disabledOverrideCode, 0)
      equal(await pathExists(customMarkdownPath), false)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("computes reclaim-first ranking, pareto, efficiency score, and source-count insights", async () => {
    const rootDir = await createFixtureRoot()
    try {
      const {cacheDir, entriesDir} = await createCacheSkeleton(rootDir)
      const now = Date.now()

      for (let i = 0; i < 6; i++) {
        await writeEntryFileSet(entriesDir, "aa", buildEntryStem(100 + i, "many-small.jar"), {
          payloadSize: 10,
          metadataMtimeMs: now - i * 1000,
          metadataBuffer: createMetadataBufferWithSourceCount(2 + i),
        })
      }

      const hugePayloads = [500, 480, 460, 440]
      for (let i = 0; i < hugePayloads.length; i++) {
        await writeEntryFileSet(entriesDir, "ab", buildEntryStem(200 + i, "few-huge.jar"), {
          payloadSize: hugePayloads[i],
          metadataMtimeMs: now - i * 1000,
          metadataBuffer: createMetadataBufferWithSourceCount(40 + i * 5),
        })
      }

      for (let i = 0; i < 8; i++) {
        await writeEntryFileSet(entriesDir, "ac", buildEntryStem(300 + i, `baseline-${i}.jar`), {
          payloadSize: 20,
          metadataMtimeMs: now - i * 1000,
          metadataBuffer: createMetadataBufferWithSourceCount(1),
        })
      }

      const report = await analyzeJarCache({
        cacheDir,
        top: 10,
        bins: 6,
        anomalyTop: 10,
        paretoThresholdPct: 80,
        heatmapBins: 4,
        scoreWeights: {
          overflow: 30,
          reclaim: 40,
          integrity: 20,
          concentration: 10,
        },
      })

      equal(report.targets.byVersionCount[0].targetName, "many-small.jar")
      equal(report.targets.byReclaimableBytes[0].targetName, "few-huge.jar")
      equal(report.targets.byReclaimableBytes[0].reclaimableBytes, 440)
      equal(report.targets.byVersionCount[0].reclaimableBytes, 30)

      ok(report.anomalies.rows.length > 0)
      ok(report.anomalies.rows.some((row) => row.targetName === "few-huge.jar"))

      equal(report.pareto.targetCoverageThresholdPct, 80)
      equal(report.pareto.targetsToThreshold, 1)
      ok(report.pareto.reclaimableShareAtThreshold >= 0.8)

      ok(report.efficiencyScore.score >= 0)
      ok(report.efficiencyScore.score <= 100)

      equal(report.sourceInsights.coveragePct, 1)
      equal(report.sourceInsights.buckets.length, 4)
      ok(report.sourceInsights.topOutliers.length > 0)

      const human = formatHumanReport(report, {top: 5, useColor: false})
      match(human, /Top by reclaimable payload/)
      match(human, /Top by version count/)
      match(human, /Anomalies/)
      match(human, /Pareto \(Reclaimable Bytes\)/)
      match(human, /Efficiency Score/)
      match(human, /Source Count Insights/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("uses payload tie-break for version-count ranking and emits markdown dashboard", async () => {
    const rootDir = await createFixtureRoot()
    try {
      const {cacheDir, entriesDir} = await createCacheSkeleton(rootDir)
      const now = Date.now()

      const highReclaimLowPayload = [10, 10, 10, 100]
      for (let i = 0; i < highReclaimLowPayload.length; i++) {
        await writeEntryFileSet(entriesDir, "aa", buildEntryStem(10 + i, "reclaim-first.jar"), {
          payloadSize: highReclaimLowPayload[i],
          metadataMtimeMs: now - i * 1000,
          metadataBuffer: createMetadataBufferWithSourceCount(1),
        })
      }

      const lowReclaimHighPayload = [90, 90, 90, 1]
      for (let i = 0; i < lowReclaimHighPayload.length; i++) {
        await writeEntryFileSet(entriesDir, "ab", buildEntryStem(20 + i, "payload-first.jar"), {
          payloadSize: lowReclaimHighPayload[i],
          metadataMtimeMs: now - i * 1000,
          metadataBuffer: createMetadataBufferWithSourceCount(1),
        })
      }

      const report = await analyzeJarCache({cacheDir, top: 10})

      equal(report.targets.byVersionCount[0].targetName, "payload-first.jar")
      equal(report.targets.byVersionCount[1].targetName, "reclaim-first.jar")
      ok(report.targets.byReclaimableBytes[0].targetName === "reclaim-first.jar")

      const markdown = formatMarkdownReport(report, {top: 5})
      match(markdown, /## Executive Summary/)
      match(markdown, /## Top Offenders/)
      match(markdown, /_Sort: versions desc, payloadBytes desc, targetName asc_/)
      match(markdown, /## Action Plan/)
    }
    finally {
      await rm(rootDir, {recursive: true, force: true})
    }
  })

  it("rejects bad CLI arguments", async () => {
    const errors = []
    const code = await runCli(["--top", "0"], {
      stdout: () => {},
      stderr: (value) => errors.push(value),
    })

    equal(code, 2)
    ok(errors.some((line) => line.includes("positive integer")))

    const paretoErrors = []
    const paretoCode = await runCli(["--pareto-threshold", "150"], {
      stdout: () => {},
      stderr: (value) => paretoErrors.push(value),
    })

    equal(paretoCode, 2)
    ok(paretoErrors.some((line) => line.includes("percentage")))

    const weightsErrors = []
    const weightsCode = await runCli(["--score-weights", '{"overflow":-1}'], {
      stdout: () => {},
      stderr: (value) => weightsErrors.push(value),
    })

    equal(weightsCode, 2)
    ok(weightsErrors.some((line) => line.includes("Invalid score weight")))

    const markdownErrors = []
    const markdownCode = await runCli(["--md-file"], {
      stdout: () => {},
      stderr: (value) => markdownErrors.push(value),
    })

    equal(markdownCode, 2)
    ok(markdownErrors.some((line) => line.includes("Missing value for --md-file")))
  })
})
