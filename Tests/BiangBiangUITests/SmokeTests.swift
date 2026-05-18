@testable import BiangBiangUI
import Testing

@Test func libraryVersionIsSemver() {
    let parts = BiangBiangUI.version.split(separator: ".")
    #expect(parts.count == 3)
}
