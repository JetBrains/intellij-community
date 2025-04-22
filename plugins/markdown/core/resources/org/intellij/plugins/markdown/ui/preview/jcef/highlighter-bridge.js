// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
const entities = { amp: '&', gt: '>', lt: '<', quot: '"' };

function checksum53(s) {
  let h1 = 0xDEADBEEF;
  let h2 = 0x41C6CE57;

  s = s.normalize();

  for (let i = 0; i < s.length; ++i) {
    const ch = s.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }

  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);

  return (4294967296 * (2097151 & h2) + (h1 >>> 0)).toString(16).toUpperCase().padStart(14, '0');
}

function decodeEntities(text) {
  return text.replace(/&(amp|gt|lt|quot);/g, (_0, _1, p2) => entities[p2] || '');
}

function addSourceRange(tag, start, end) {
  return tag.replace(/^(<\w+)/, `$1 md-src-pos="${start}..${end}"`)
}

function addSourceRanges(html, offset) {
  // It is most certainly necessary!
  // noinspection RegExpUnnecessaryNonCapturingGroup
  const chunks = html.split(/(?<=(?:>|\r\n|\r|\n))|(?=<)/);
  const nesting = [];
  let wasNewLine = false;

  for (let i = 0; i < chunks.length; ++i) {
    const chunk = chunks[i];
    const isNewLine = /[\n\r]/.test(chunk);

    if (chunk.startsWith('</')) {
      const match = nesting.pop();

      if (match)
        chunks[match.line] = addSourceRange(chunks[match.line], match.offset, offset);
    }
    else if (chunk.startsWith('<')) {
      nesting.push({ line: i, offset });
    }
    else {
      const decodedChunk = decodeEntities(chunk);

      if (nesting.length > 0 && !isNewLine && !wasNewLine)
        offset += decodedChunk.length;
      else if (chunk) {
        chunks[i] = addSourceRange(`<span>${chunk}</span>`, offset, offset + decodedChunk.length);
        offset += decodedChunk.length;
      }
    }

    wasNewLine = isNewLine
  }

  return chunks.join('')
}

const cachedCode = new Map();
const cachedCodeTime = new Map();

function reTag(node, id) {
  node?.setAttribute('id', 'cfid-' + id);
  node?.setAttribute('data-visited', 'true');
  node?.classList.add('cfid');
}

let dataTransferUrl = null;

function parseCodeFence(id) {
  let code = document.getElementById('cfid-' + id)?.parentElement

  if (!code || !hljs)
    return;

  const content = code.textContent;
  let language = (Array.from(code.classList).find(c => c.startsWith('language-')) || '').substring(9);
  const key = checksum53(`${content};${language}`);

  if (cachedCode.has(key)) {
    code.innerHTML = cachedCode.get(key);
    cachedCodeTime.set(key, performance.now());
    reTag(code.firstChild, id);

    return;
  }

  let innerHTML = '';

  if (language === 'json5')
    language = 'jsonc';

  if (language) {
    try {
      innerHTML = hljs.highlight(content, { language }).value;
    }
    catch {}
  }

  if (!innerHTML) {
    try {
      innerHTML = hljs.highlightAuto(content).value;
    }
    catch {}
  }

  if (innerHTML) {
    const sourceOffset = parseInt((/^(\d+)\.\./.exec(code.getAttribute('md-src-pos') || '') || ['', '0'])[1]);

    if (sourceOffset)
      innerHTML = addSourceRanges(innerHTML, sourceOffset);

    code.innerHTML = innerHTML;
    cachedCode.set(key, innerHTML);
    cachedCodeTime.set(key, performance.now());
    reTag(code.firstChild, id);
    console.info(`highlighter:${id}:${checksum53(content)}:${innerHTML}`);
  }
}

let fenceScanSkipCount = 0;

function lookForCodeFences() {
  const fences = Array.from(document.querySelectorAll('span.cfid')).filter(e => !e.getAttribute('data-visited'));

  if (fences.length === 0 && ++fenceScanSkipCount > 20)
    return;

  fenceScanSkipCount = 0;

  for (const fence of fences)
    parseCodeFence(parseInt((fence.id || '0').replace('cfid-', '')));

  if (cachedCode.size > 250) {
    const now = performance.now();
    const times = Array.from(cachedCodeTime.values()).sort((a, b) => b - a);
    const oldestAllowed = times[249];
    const keys = Array.from(cachedCodeTime.keys()).filter(k => cachedCodeTime.get(k) < oldestAllowed);

    keys.forEach(k => { cachedCode.delete(k); cachedCodeTime.delete(k) });
  }

  setTimeout(lookForCodeFences, 500);
}

lookForCodeFences();
