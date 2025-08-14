import data from "../work/browser-compat-data.json" with { type: "json" };
import { computeBaseline } from "compute-baseline";
import { Compat } from "compute-baseline/browser-compat-data";
import * as readline from 'readline';

const compat = new Compat(data);

// Create readline interface to read from stdin
const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

// Process each line from stdin
rl.on('line', (line) => {
  if (line.trim()) {
    try {
      const result = computeBaseline({
        compatKeys: [line],
        checkAncestors: true
      }, compat);

      // Output the result as a single line to stdout
      console.log(JSON.stringify(result));
    } catch (e) {
      if ((e.message?.indexOf("is unindexable at") ?? -1) > 0) {
        console.error(e.message)
      } else {
        console.error(e)
      }
      console.log("\"null\"")
    }
  }
});

// Handle end of input
rl.on('close', () => {
  process.exit(0);
});
