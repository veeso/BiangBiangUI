@testable import BiangBiangUI
import Testing

// MARK: - Fake transliterators

/// Maps every character in the span to "x", joined by single spaces.
/// "我在" → "x x", "工作" → "x x", "我喜欢" → "x x x"
private struct FakeLatin: Transliterator {
    func transliterate(_ s: String) -> String {
        s.map { _ in "x" }.joined(separator: " ")
    }
}

/// Maps every character to "y", joined by single spaces.
private struct FakeY: Transliterator {
    func transliterate(_ s: String) -> String {
        s.map { _ in "y" }.joined(separator: " ")
    }
}

// MARK: - Range constants

/// CJK Unified Ideographs: U+4E00–U+9FFF
private let cjk: [ClosedRange<UInt32>] = [0x4E00 ... 0x9FFF]

/// CJK + CJK Extension A (U+3400–U+4DBF) for multi-range tests.
private let cjkPlusExtA: [ClosedRange<UInt32>] = [0x3400 ... 0x4DBF, 0x4E00 ... 0x9FFF]

// MARK: - Tests

@Test func returnsNilWhenNoScript() {
    // "Pizza123" contains no CJK code points → nil
    #expect(TextProcessingEngine(scriptRanges: cjk, transliterator: FakeLatin()).process("Pizza123") == nil)
}

@Test func detectsScript() {
    // "你好Pizza" contains CJK (你 U+4F60, 好 U+597D) → true
    #expect(TextProcessingEngine(scriptRanges: cjk, transliterator: FakeLatin()).containsScript("你好Pizza") == true)
}

@Test func doesNotDetectScriptInPureASCII() {
    #expect(TextProcessingEngine(scriptRanges: cjk, transliterator: FakeLatin()).containsScript("Hello world") == false)
}

@Test func insertsSpacesAroundLatinRuns() {
    // Input: "我在NASA工作."
    // Regex finds two spans (reversed): "工作" then "我在"
    //
    // Pass 1 — replace "工作":
    //   prevChar = 'A' (ASCII letter, not space, not punctuation) → needsLeadingSpace = true
    //   nextChar = '.' → isASCII=true, isLetter=false, isNumber=false → needsTrailingSpace = false
    //   FakeLatin("工作") = "x x"
    //   replacement = " x x"
    //   result after pass 1: "我在NASA x x."
    //
    // Pass 2 — replace "我在":
    //   lowerBound == startIndex → needsLeadingSpace = false
    //   nextChar = 'N' → isASCII=true, isLetter=true → needsTrailingSpace = true
    //   FakeLatin("我在") = "x x"
    //   replacement = "x x "
    //   result after pass 2: "x x NASA x x."
    //
    // Post-pass: no space-before-punctuation to remove, no double spaces, trim → "x x NASA x x."
    let e = TextProcessingEngine(scriptRanges: cjk, transliterator: FakeLatin())
    #expect(e.process("我在NASA工作.") == "x x NASA x x.")
}

@Test func preservesEmojiAndPunctuation() {
    // Input: "我喜欢🥟"
    // One span: "我喜欢"
    //
    // Pass 1 — replace "我喜欢":
    //   lowerBound == startIndex → needsLeadingSpace = false
    //   nextChar = '🥟' → isASCII=false → needsTrailingSpace = false
    //   FakeLatin("我喜欢") = "x x x"
    //   replacement = "x x x"
    //   result: "x x x🥟"
    //
    // Post-pass: no changes, trim → "x x x🥟"
    let e = TextProcessingEngine(scriptRanges: cjk, transliterator: FakeLatin())
    #expect(e.process("我喜欢🥟") == "x x x🥟")
}

@Test func leadingSpanAtStringStart_noLeadingSpace() {
    // Input: "你好"  — single span at string start, no surrounding text
    //   lowerBound == startIndex → needsLeadingSpace = false
    //   upperBound == endIndex → needsTrailingSpace = false
    //   FakeLatin("你好") = "x x"
    //   result: "x x"
    let e = TextProcessingEngine(scriptRanges: cjk, transliterator: FakeLatin())
    #expect(e.process("你好") == "x x")
}

@Test func punctuationPreventsLeadingSpace() {
    // Input: "Hello,你好"
    //   prevChar = ',' (in .,!?;: set) → needsLeadingSpace = false
    //   nextChar = (end of string) → needsTrailingSpace = false
    //   FakeLatin("你好") = "x x"
    //   result: "Hello,x x"
    //   Post-pass: no changes, trim → "Hello,x x"
    let e = TextProcessingEngine(scriptRanges: cjk, transliterator: FakeLatin())
    #expect(e.process("Hello,你好") == "Hello,x x")
}

@Test func trailingSpaceBeforeASCIIDigit() {
    // Input: "我5"
    //   '5' is ASCII digit → needsTrailingSpace? NO wait — '5' comes AFTER the span.
    //   Actually: "我5":
    //     span = "我", nextChar = '5', '5'.isASCII && '5'.isNumber → needsTrailingSpace = true
    //     lowerBound == startIndex → needsLeadingSpace = false
    //     FakeLatin("我") = "x"
    //     replacement = "x "
    //     result: "x 5"
    //   Post-pass: no changes, trim → "x 5"
    let e = TextProcessingEngine(scriptRanges: cjk, transliterator: FakeLatin())
    #expect(e.process("我5") == "x 5")
}

@Test func multiRangeDetection() {
    // CJK Extension A character 㐀 (U+3400) should be detected with cjkPlusExtA
    // but NOT with cjk alone (U+4E00...U+9FFF)
    let engineSingle = TextProcessingEngine(scriptRanges: cjk, transliterator: FakeLatin())
    let engineMulti = TextProcessingEngine(scriptRanges: cjkPlusExtA, transliterator: FakeLatin())

    // 㐀 is U+3400, outside cjk range
    let textWithExtA = "\u{3400}" // 㐀
    #expect(engineSingle.containsScript(textWithExtA) == false)
    #expect(engineMulti.containsScript(textWithExtA) == true)
}

@Test func spacesCleanedUpBeforePunctuation() {
    // Input: "你好 ." — after transliteration we'd have "x x ." → cleanup removes space before '.'
    // But the reference uses regex cleanup: \s+([.,!?;:]) → $1
    //
    // Input: "你好."
    //   span = "你好", nextChar = '.' → not ASCII letter/digit → needsTrailingSpace = false
    //   prevChar = (start) → needsLeadingSpace = false
    //   FakeLatin("你好") = "x x"
    //   replacement = "x x"
    //   result: "x x."
    //   Post-pass: no trailing space before '.', trim → "x x."
    //
    // Now test the cleanup path is actually triggered. Input: "你好 ." has a space+dot.
    // BUT: the space is literal, not injected by the engine.
    // Let's use a case that naturally produces space-then-punct via the algorithm:
    //   Input: "你好 ." — the span "你好" is followed by " .", nextChar = ' ' (space),
    //   ' '.isASCII && ' '.isLetter=false, ' '.isNumber=false → needsTrailingSpace = false.
    //   replacement = "x x", result = "x x ."
    //   Post-pass cleanup: \s+([.]) → "." → result = "x x."
    //   trim → "x x."
    //
    // The only way to naturally trigger space-before-punct is to have a literal space in the input.
    let e = TextProcessingEngine(scriptRanges: cjk, transliterator: FakeLatin())
    #expect(e.process("你好 .") == "x x.")
}
