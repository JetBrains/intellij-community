package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

/**
 * Controls whether and where the original (source) token is re-emitted alongside derived tokens.
 *
 * **Important:** [WordSplittingTokenFilter] handles these options via explicit `if` checks rather
 * than an exhaustive `when`. If a new variant is added here, all switch sites must be updated manually.
 */
enum class PassthroughOptions {
  NoPassthrough,
  PassthroughFirst,
  PassthroughLast,
}