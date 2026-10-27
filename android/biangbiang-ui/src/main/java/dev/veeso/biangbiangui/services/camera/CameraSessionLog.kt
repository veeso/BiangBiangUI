package dev.veeso.biangbiangui.services.camera

import java.util.UUID

class CameraSessionLog private constructor(entries: List<Entry>) {
    companion object {
        const val CAPACITY = 50

        // Explicit Unicode White_Space set, mirrored in iOS; platform defaults differ.
        private const val BOUNDARY_WHITESPACE =
            "\u0009\u000A\u000B\u000C\u000D\u0020\u0085\u00A0\u1680" +
                "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A" +
                "\u2028\u2029\u202F\u205F\u3000"
    }

    data class Entry(val id: UUID, val original: String, val transliteration: String)

    private val mutableEntries = entries.toMutableList()
    val entries: List<Entry> get() = mutableEntries.toList()

    constructor() : this(emptyList())

    fun ingest(pairs: List<Pair<String, String>>): List<UUID> {
        val insertedIds = mutableListOf<UUID>()
        pairs.forEach { (rawOriginal, transliteration) ->
            val original = rawOriginal.trim { it in BOUNDARY_WHITESPACE }
            if (original.isEmpty() || mutableEntries.any { it.original == original }) return@forEach

            val entry = Entry(UUID.randomUUID(), original, transliteration)
            mutableEntries.add(0, entry)
            insertedIds.add(entry.id)
            while (mutableEntries.size > CAPACITY) mutableEntries.removeAt(mutableEntries.lastIndex)
        }
        return insertedIds
    }

    fun clear() {
        mutableEntries.clear()
    }

    fun copy(): CameraSessionLog = CameraSessionLog(mutableEntries.toList())
}
