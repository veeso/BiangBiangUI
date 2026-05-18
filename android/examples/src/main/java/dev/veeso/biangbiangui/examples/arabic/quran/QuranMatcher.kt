package dev.veeso.biangbiangui.examples.arabic.quran

import dev.veeso.biangbiangui.examples.arabic.ArabicNormalizer
import dev.veeso.biangbiangui.examples.arabic.NormalizationMode

/**
 * Ported verbatim from the Harakat-Lens iOS app and the already-complete iOS
 * `ArabicExample/Quran/QuranMatcher`. Matching logic and all thresholds /
 * constants are unchanged from the iOS port.
 */
data class QuranMatch(
    val ayah: QuranAyah,
    val score: Double,
    val kind: Kind,
) {
    enum class Kind { EXACT, FUZZY }

    val id: String get() = ayah.id
}

class QuranMatcher(private val dataset: QuranDataset) {

    private val normalizer = ArabicNormalizer(NormalizationMode.MATCHING)
    private val minTokenLength = 2
    private val candidateCap = 600
    private val rareTokenLimit = 5
    private val scoreThreshold = 0.74
    private val strongTokenLength = 4
    private val minFitLength = 8

    suspend fun match(rawArabic: String): QuranMatch? {
        val norm = normalizer.normalize(rawArabic)
        val tokens = norm.split(" ").filter { it.length >= minTokenLength }
        if (tokens.isEmpty()) return null
        val hasStrongToken = tokens.any { it.length >= strongTokenLength }
        if (tokens.size < 2 && !hasStrongToken) return null

        dataset.loadIfNeeded()
        val all = dataset.all
        val tokenIndex = dataset.tokenIndex
        if (all.isEmpty()) return null

        // 1. Exact containment pass (bidirectional) — dataset is already in
        // surah/ayah order.
        for (ayah in all) {
            if (ayah.normalized.contains(norm) || norm.contains(ayah.normalized)) {
                return QuranMatch(ayah = ayah, score = 1.0, kind = QuranMatch.Kind.EXACT)
            }
        }

        // 2. Candidate set from rarest tokens.
        val ranked = tokens
            .map { token -> token to (tokenIndex[token]?.size ?: Int.MAX_VALUE) }
            .sortedBy { it.second }
            .take(rareTokenLimit)

        val candidateSet = LinkedHashSet<Int>()
        run {
            for ((key, _) in ranked) {
                for (index in tokenIndex[key] ?: emptyList()) {
                    candidateSet.add(index)
                    if (candidateSet.size >= candidateCap) return@run
                }
                if (candidateSet.size >= candidateCap) return@run
            }
        }
        if (candidateSet.isEmpty()) return null

        // 3. Substring-fit Levenshtein on candidates.
        val normChars = norm.toCharArray()
        var best: QuranMatch? = null
        for (index in candidateSet) {
            if (index >= all.size) continue
            val ayah = all[index]
            val ayahChars = ayah.normalized.toCharArray()
            val shorter = if (normChars.size <= ayahChars.size) normChars else ayahChars
            val longer = if (normChars.size <= ayahChars.size) ayahChars else normChars
            // Avoid spurious high scores from very short fragments that
            // happen to fit inside an unrelated ayah.
            if (shorter.size < minFitLength) continue
            val distance = substringFitDistance(shorter, longer)
            val score = 1.0 - distance.toDouble() / shorter.size.toDouble()
            if (score >= scoreThreshold &&
                score > (best?.score ?: (scoreThreshold - 0.001))
            ) {
                best = QuranMatch(ayah = ayah, score = score, kind = QuranMatch.Kind.FUZZY)
            }
        }
        return best
    }

    companion object {
        /**
         * Min edit distance from `short` to any substring of `long`.
         * Free start (first row zero) and free end (min of last row).
         */
        fun substringFitDistance(short: CharArray, long: CharArray): Int {
            if (short.isEmpty()) return 0
            if (long.isEmpty()) return short.size

            var prev = IntArray(long.size + 1)
            var curr = IntArray(long.size + 1)

            for (i in 1..short.size) {
                curr[0] = i
                for (j in 1..long.size) {
                    val cost = if (short[i - 1] == long[j - 1]) 0 else 1
                    curr[j] = minOf(
                        curr[j - 1] + 1,
                        prev[j] + 1,
                        prev[j - 1] + cost,
                    )
                }
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return prev.min()
        }

        /** Iterative two-row Levenshtein. */
        fun levenshtein(a: String, b: String): Int {
            val aChars = a.toCharArray()
            val bChars = b.toCharArray()
            if (aChars.isEmpty()) return bChars.size
            if (bChars.isEmpty()) return aChars.size

            var prev = IntArray(bChars.size + 1) { it }
            var curr = IntArray(bChars.size + 1)

            for (i in 1..aChars.size) {
                curr[0] = i
                for (j in 1..bChars.size) {
                    val cost = if (aChars[i - 1] == bChars[j - 1]) 0 else 1
                    curr[j] = minOf(
                        curr[j - 1] + 1,
                        prev[j] + 1,
                        prev[j - 1] + cost,
                    )
                }
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return prev[bChars.size]
        }
    }
}
