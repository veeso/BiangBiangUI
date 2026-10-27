@testable import BiangBiangUI
import Testing

struct CameraSessionLogTests {
    @Test func insertionAndTrimmedDuplicatesKeepIdentityAndPosition() {
        var log = CameraSessionLog()
        let ids = log.ingest([
            (" 你\n", "ni"), ("好", "hao"), ("你", "ignored"),
        ])
        #expect(log.entries.map(\.original) == ["好", "你"])
        #expect(log.entries.map(\.id) == Array(ids.reversed()))
        let before = log
        #expect(log.ingest([("你", "changed"), (" 好 ", "changed")]).isEmpty)
        #expect(log == before)
        #expect(log.ingest([]).isEmpty)
    }

    @Test func capEvictsOldestAndAllowsEvictedTextAgain() {
        var log = CameraSessionLog()
        _ = log.ingest((0 ..< 51).map { (String($0), "t\($0)") })
        #expect(log.entries.count == 50)
        #expect(log.entries.first?.original == "50")
        #expect(log.entries.last?.original == "1")
        #expect(log.ingest([("0", "again")]).count == 1)
        #expect(log.entries.first?.original == "0")
        #expect(log.entries.last?.original == "2")
    }

    @Test func clearStartsFreshAndIgnoresBlankOriginals() {
        var log = CameraSessionLog()
        #expect(log.ingest([(" \n", "ignored")]).isEmpty)
        let old = log.ingest([("你", "ni")])
        log.clear()
        #expect(log.entries.isEmpty)
        #expect(log.ingest([("你", "ni")]) != old)
    }
}

struct CameraSessionLogWhitespaceTests {
    @Test(arguments: ["\u{0009}", "\u{000A}", "\u{000B}", "\u{000C}", "\u{000D}", "\u{0020}", "\u{0085}", "\u{00A0}", "\u{1680}", "\u{2000}", "\u{2001}", "\u{2002}", "\u{2003}", "\u{2004}", "\u{2005}", "\u{2006}", "\u{2007}", "\u{2008}", "\u{2009}", "\u{200A}", "\u{2028}", "\u{2029}", "\u{202F}", "\u{205F}", "\u{3000}"])
    func unicodeWhitespaceIsTrimmed(_ boundary: String) {
        var log = CameraSessionLog()
        let wrapped = boundary + "你" + boundary
        #expect(log.ingest([(boundary, "ignored")]).isEmpty)
        _ = log.ingest([(wrapped, "ni")])
        #expect(log.entries.map(\.original) == ["你"])
        let before = log
        #expect(log.ingest([("你", "changed")]).isEmpty)
        #expect(log == before)
        let interior = "你" + boundary + "好"
        _ = log.ingest([(interior, "ni hao")])
        #expect(log.entries.first?.original == interior)
    }

    @Test(arguments: ["\u{0000}", "\u{0008}", "\u{000E}", "\u{001C}", "\u{001D}", "\u{001E}", "\u{001F}", "\u{0084}", "\u{0086}", "\u{180E}", "\u{200B}", "\u{200C}", "\u{2060}", "\u{FEFF}", "\u{1F600}"])
    func nonWhitespaceIsPreserved(_ boundary: String) {
        var log = CameraSessionLog()
        let wrapped = boundary + "你" + boundary
        _ = log.ingest([(wrapped, "ni"), ("你", "ni")])
        #expect(log.entries.map(\.original) == ["你", wrapped])
        let interior = "你" + boundary + "好"
        _ = log.ingest([(interior, "ni hao")])
        #expect(log.entries.first?.original == interior)
    }
}
