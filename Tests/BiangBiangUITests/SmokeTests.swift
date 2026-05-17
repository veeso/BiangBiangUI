import Testing
@testable import BiangBiangUI

@Test func libraryVersionIsSemver() {
    let parts = BiangBiangUI.version.split(separator: ".")
    #expect(parts.count == 3)
}
