// Regenerates `config/` and `sources.bzl` of this directory from a libaom release.
//
// The run configures libaom with cmake twice, for arm64 and for x86_64, with the options of the recorder. It copies
// the config headers that cmake writes, and it groups the C sources of `compile_commands.json` by the SIMD flags of each
// file. The x86_64 run needs nasm, because the cmake of libaom refuses x86 without an assembler.
//
//   bun community/tools/vm/aom/update.mjs --source <libaom-x.y.z> --nasm <nasm> [--bindgen <bindgen>]
//
// `<libaom-x.y.z>` is the extracted release tarball of the version that `avl.MODULE.bazel` pins. With `--bindgen`, the
// run also writes `src/bindings.rs` of the crate `avl-aom-sys`: the calls and the constants of the encoder that the
// recorder uses, and their types.

import { execFileSync } from 'node:child_process'
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, relative, resolve } from 'node:path'
import { parseArgs } from 'node:util'

const HERE = import.meta.dirname

// The libaom options of the recorder: an 8-bit AV1 encoder without a decoder. `CONFIG_SVT_AV1` is off, so no source
// of the BSD 3-Clause Clear License is built. `CONFIG_PIC` is on, because a Linux binary of the recorder is a PIE.
const OPTIONS = [
  '-DCMAKE_BUILD_TYPE=Release',
  '-DCMAKE_EXPORT_COMPILE_COMMANDS=1',
  '-DCONFIG_AV1_DECODER=0',
  '-DCONFIG_AV1_HIGHBITDEPTH=0',
  '-DCONFIG_PIC=1',
  '-DCONFIG_SVT_AV1=0',
  '-DCONFIG_LIBYUV=0',
  '-DCONFIG_WEBM_IO=0',
  '-DENABLE_DOCS=0',
  '-DENABLE_EXAMPLES=0',
  '-DENABLE_TESTDATA=0',
  '-DENABLE_TESTS=0',
  '-DENABLE_TOOLS=0',
]

// SVE is off: no Apple CPU has it, and the toolchain gains nothing from a second arm64 path to test.
const ARCHES = {
  arm64: ['-DENABLE_SVE=0', '-DENABLE_SVE2=0'],
  x86_64: ['-DCMAKE_OSX_ARCHITECTURES=x86_64', '-DAOM_TARGET_CPU=x86_64'],
}

const CONFIG_FILES = ['aom_config.asm', 'aom_config.h', 'aom_dsp_rtcd.h', 'aom_scale_rtcd.h', 'aom_version.h', 'av1_rtcd.h']

// The cmake targets whose objects are not a part of the library: the tools and their helpers, the real-time rate
// control library, and the no-op sources. The config source is left out too, and `aom.BUILD.bazel` writes its own.
const LEFT_OUT_TARGETS = new Set([
  'aom_av1_rc', 'aom_common_app_util', 'aom_decoder_app_util', 'aom_encoder_app_util', 'aom_encoder_stats', 'aom_pc',
  'aom_usage_exit', 'aom_version', 'aomenc',
])
const LEFT_OUT = [/^gen_src\//, /^config\//]

const { values } = parseArgs({ options: { source: { type: 'string' }, nasm: { type: 'string' }, bindgen: { type: 'string' } } })
if (!values.source || !values.nasm) {
  throw new Error('usage: update.mjs --source <libaom-x.y.z> --nasm <nasm>')
}
const vendor = resolve(values.source)
const work = mkdtempSync(join(tmpdir(), 'aom-config-'))

/** @type {Record<string, Map<string, string>>} the SIMD flag of each source, by arch */
const flagsByArch = {}
for (const [arch, options] of Object.entries(ARCHES)) {
  const build = join(work, arch)
  mkdirSync(build)
  execFileSync('cmake', [vendor, ...OPTIONS, ...options, `-DCMAKE_ASM_NASM_COMPILER=${resolve(values.nasm)}`], { cwd: build, stdio: 'ignore' })
  const target = join(HERE, 'config', arch, 'config')
  mkdirSync(target, { recursive: true })
  for (const file of CONFIG_FILES) {
    copyFileSync(join(build, 'config', file), join(target, file))
  }
  const sources = new Map()
  for (const entry of JSON.parse(readFileSync(join(build, 'compile_commands.json'), 'utf8'))) {
    const file = relative(vendor, entry.file).split('\\').join('/')
    const target = entry.command.match(/ -o CMakeFiles\/([^/]+)\.dir\//)?.[1]
    if (file.startsWith('..') || LEFT_OUT_TARGETS.has(target) || LEFT_OUT.some(pattern => pattern.test(file))) {
      continue
    }
    const simd = (entry.command.match(/ (-m(?:sse|ssse|avx)[\w.]*|-march=\S+)/g) ?? []).map(flag => flag.trim())
    sources.set(file, simd.join(' '))
  }
  flagsByArch[arch] = sources
}
// cmake writes no compile command for nasm, so the assembly sources come from the make rules of the x86_64 targets.
const targets = join(work, 'x86_64', 'CMakeFiles')
const asm = [...new Set(readdirSync(targets)
  .filter(target => existsSync(join(targets, target, 'build.make')))
  .flatMap(target => [...readFileSync(join(targets, target, 'build.make'), 'utf8').matchAll(/\.dir\/(\S+\.asm)\.o:/g)].map(match => match[1])))]
  .filter(file => existsSync(join(vendor, file)))
  .sort()
rmSync(work, { recursive: true, force: true })

const common = [...flagsByArch.arm64].filter(([file, flags]) => flags === '' && flagsByArch.x86_64.get(file) === '').map(([file]) => file)
const own = arch => [...flagsByArch[arch]].filter(([file]) => !common.includes(file))
const byFlags = arch => {
  const groups = {}
  for (const [file, flags] of own(arch)) {
    ;(groups[flags] ??= []).push(file)
  }
  return Object.fromEntries(Object.entries(groups).sort().map(([flags, files]) => [flags, files.sort()]))
}

const list = files => files.length === 0 ? '[]' : `[\n${files.map(file => `    "${file}",\n`).join('')}]`
const dict = groups => `{\n${Object.entries(groups).map(([flags, files]) =>
  `    "${flags}": [\n${files.map(file => `        "${file}",\n`).join('')}    ],\n`).join('')}}`

writeFileSync(join(HERE, 'sources.bzl'), `"""The libaom sources of the recorder, by arch and by SIMD flag. Generated by \`update.mjs\`; do not edit."""

# The C sources that both arches compile without a SIMD flag.
AOM_SOURCES = ${list(common.sort())}

# The C sources of arm64 only, by the flag that each needs. An empty key needs no flag.
AOM_ARM64_SOURCES = ${dict(byFlags('arm64'))}

# The C sources of x86_64 only, by the flag that each needs. An empty key needs no flag.
AOM_X86_64_SOURCES = ${dict(byFlags('x86_64'))}

# The nasm sources of x86_64.
AOM_X86_64_ASM = ${list(asm)}
`)

if (values.bindgen) {
  const header = join(work, 'bindings.h')
  mkdirSync(work, { recursive: true })
  writeFileSync(header, '#include "aom/aom_encoder.h"\n#include "aom/aomcx.h"\n')
  const bindings = execFileSync(values.bindgen, [
    header,
    '--use-core',
    '--no-doc-comments',
    '--no-prepend-enum-name',
    '--default-enum-style', 'consts',
    '--allowlist-function', 'aom_codec_(av1_cx|enc_config_default|enc_init_ver|control|encode|get_cx_data|destroy|error|error_detail|version_str)',
    '--allowlist-function', 'aom_img_wrap',
    '--allowlist-var', 'AOM_(ENCODER_ABI_VERSION|USAGE_ALL_INTRA|CODEC_USE_PSNR)',
    '--allowlist-type', 'aome_enc_control_id|aom_tune_metric|aom_codec_err_t|aom_codec_cx_pkt_kind|aom_img_fmt|aom_bit_depth|aom_rc_mode|aom_color_range|aom_color_primaries|aom_transfer_characteristics|aom_matrix_coefficients',
    '--',
    `-I${vendor}`,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] })
  rmSync(work, { recursive: true, force: true })
  writeFileSync(join(HERE, '..', 'crates', 'avl-aom-sys', 'src', 'bindings.rs'),
    `// Generated by \`community/tools/vm/aom/update.mjs --bindgen\` from the headers of ${relative(join(vendor, '..'), vendor)}; do not edit.\n\n${bindings}`)
}
