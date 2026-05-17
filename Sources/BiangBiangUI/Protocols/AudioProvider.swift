import SwiftUI

@MainActor
public protocol AudioProvider: AnyObject {
    /// Speak/stream the given text/result. `languageCode` is the variant's BCP-47 code.
    func play(text: String, languageCode: String?)
    func stop()
    var isPlaying: Bool { get }
}
