//
//  QuranMatcher.swift
//  ArabicExample
//
//  Ported verbatim from the Harakat-Lens iOS app. Matching logic unchanged.
//

import Foundation

struct QuranMatch: Identifiable, Equatable {
    enum Kind { case exact, fuzzy }
    let ayah: QuranAyah
    let score: Double
    let kind: Kind
    var id: String {
        ayah.id
    }
}

actor QuranMatcher {
    private let dataset: QuranDataset
    private let normalizer = ArabicNormalizer(mode: .matching)
    private let minTokenLength = 2
    private let candidateCap = 600
    private let rareTokenLimit = 5
    private let scoreThreshold = 0.74
    private let strongTokenLength = 4
    private let minFitLength = 8

    init(dataset: QuranDataset) {
        self.dataset = dataset
    }

    func match(_ rawArabic: String) async -> QuranMatch? {
        let norm = normalizer.normalize(rawArabic)
        let tokens = norm.split(separator: " ").filter { $0.count >= minTokenLength }
        guard !tokens.isEmpty else { return nil }
        let hasStrongToken = tokens.contains { $0.count >= strongTokenLength }
        guard tokens.count >= 2 || hasStrongToken else { return nil }

        let all = await dataset.all
        let tokenIndex = await dataset.tokenIndex
        guard !all.isEmpty else { return nil }

        // 1. Exact containment pass (bidirectional) — dataset is already in surah/ayah order.
        for ayah in all
            where ayah.normalized.contains(norm) || norm.contains(ayah.normalized)
        {
            return QuranMatch(ayah: ayah, score: 1.0, kind: .exact)
        }

        // 2. Candidate set from rarest tokens.
        let ranked = tokens
            .map { token -> (String, Int) in
                let key = String(token)
                return (key, tokenIndex[key]?.count ?? .max)
            }
            .sorted { $0.1 < $1.1 }
            .prefix(rareTokenLimit)

        var candidateSet = Set<Int>()
        for (key, _) in ranked {
            for index in tokenIndex[key] ?? [] {
                candidateSet.insert(index)
                if candidateSet.count >= candidateCap { break }
            }
            if candidateSet.count >= candidateCap { break }
        }
        if candidateSet.isEmpty { return nil }

        // 3. Substring-fit Levenshtein on candidates.
        let normChars = Array(norm)
        var best: QuranMatch?
        for index in candidateSet {
            guard index < all.count else { continue }
            let ayah = all[index]
            let ayahChars = Array(ayah.normalized)
            let shorter = normChars.count <= ayahChars.count ? normChars : ayahChars
            let longer = normChars.count <= ayahChars.count ? ayahChars : normChars
            // Avoid spurious high scores from very short fragments that
            // happen to fit inside an unrelated ayah.
            guard shorter.count >= minFitLength else { continue }
            let distance = Self.substringFitDistance(short: shorter, long: longer)
            let score = 1.0 - Double(distance) / Double(shorter.count)
            if score >= scoreThreshold,
               score > (best?.score ?? scoreThreshold - 0.001)
            {
                best = QuranMatch(ayah: ayah, score: score, kind: .fuzzy)
            }
        }
        return best
    }

    /// Min edit distance from `short` to any substring of `long`.
    /// Free start (first row zero) and free end (min of last row).
    static func substringFitDistance(short: [Character], long: [Character]) -> Int {
        if short.isEmpty { return 0 }
        if long.isEmpty { return short.count }

        var prev = Array(repeating: 0, count: long.count + 1)
        var curr = Array(repeating: 0, count: long.count + 1)

        for i in 1 ... short.count {
            curr[0] = i
            for j in 1 ... long.count {
                let cost = short[i - 1] == long[j - 1] ? 0 : 1
                curr[j] = min(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            swap(&prev, &curr)
        }
        return prev.min() ?? short.count
    }

    /// Iterative two-row Levenshtein.
    static func levenshtein(_ a: String, _ b: String) -> Int {
        let aChars = Array(a)
        let bChars = Array(b)
        if aChars.isEmpty { return bChars.count }
        if bChars.isEmpty { return aChars.count }

        var prev = Array(0 ... bChars.count)
        var curr = Array(repeating: 0, count: bChars.count + 1)

        for i in 1 ... aChars.count {
            curr[0] = i
            for j in 1 ... bChars.count {
                let cost = aChars[i - 1] == bChars[j - 1] ? 0 : 1
                curr[j] = min(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            swap(&prev, &curr)
        }
        return prev[bChars.count]
    }
}
