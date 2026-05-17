//
//  SystemTTSAudioProvider.swift
//  BiangBiangUI
//

import AVFoundation
import Foundation
import Observation

/// AVSpeechSynthesizer-backed implementation of `AudioProvider`.
///
/// Ported from `AudioPlayerService` in BiangBiang Hanzi. The only
/// generalisation is replacing the app-specific `Language` enum with
/// an open `languageCode: String?` BCP-47 parameter so the class is
/// usable with any language profile.
@Observable
@MainActor
public final class SystemTTSAudioProvider: NSObject, AudioProvider, AVSpeechSynthesizerDelegate {
    // MARK: - State

    public enum State: Equatable {
        case idle
        case speaking
    }

    public private(set) var state: State = .idle

    // MARK: - Private

    private let synthesizer = AVSpeechSynthesizer()
    // The reference keeps player/endObserver/statusObservation for
    // future audio-file playback plumbing; preserved here for parity.
    @ObservationIgnored private var player: AVPlayer?
    @ObservationIgnored private var endObserver: NSObjectProtocol?
    @ObservationIgnored private var statusObservation: NSKeyValueObservation?

    // MARK: - Init

    override public init() {
        super.init()
        // Swift 6 strict concurrency: delegate assignment inside init is safe;
        // the synthesizer cannot call back before super.init completes.
        synthesizer.delegate = self
    }

    // MARK: - AudioProvider

    /// Speaks `text` aloud using the system speech synthesizer.
    ///
    /// Any in-progress playback is stopped first. Empty or whitespace-only
    /// input is ignored. When `languageCode` is `nil` the call is a no-op
    /// (this covers the "TTS hidden when no variant is selected" case).
    ///
    /// On success, `state` transitions to `.speaking` and returns to `.idle`
    /// when the utterance finishes or is cancelled.
    ///
    /// - Parameters:
    ///   - text: The text to synthesize.
    ///   - languageCode: BCP-47 language tag (e.g. `"zh-CN"`, `"ar-SA"`).
    ///                   Pass `nil` to suppress speech.
    public func play(text: String, languageCode: String?) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let languageCode else { return }
        stop()
        configureSession()
        let utterance = AVSpeechUtterance(string: trimmed)
        utterance.voice = AVSpeechSynthesisVoice(language: languageCode)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        state = .speaking
        synthesizer.speak(utterance)
    }

    /// Stops any active speech or audio playback and resets `state` to `.idle`.
    ///
    /// Safe to call when nothing is playing. Tears down the player, KVO
    /// observation, and end-of-playback notification observer.
    public func stop() {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        player?.pause()
        player = nil
        statusObservation?.invalidate()
        statusObservation = nil
        if let observer = endObserver {
            NotificationCenter.default.removeObserver(observer)
            endObserver = nil
        }
        state = .idle
    }

    /// `true` while an utterance is actively being spoken.
    public var isPlaying: Bool {
        state == .speaking
    }

    // MARK: - Private helpers

    /// Activates the shared audio session for spoken-audio playback (iOS only).
    private func configureSession() {
        #if os(iOS)
            let session = AVAudioSession.sharedInstance()
            try? session.setCategory(.playback, mode: .spokenAudio, options: [])
            try? session.setActive(true, options: [])
        #endif
    }

    // MARK: - AVSpeechSynthesizerDelegate

    public nonisolated func speechSynthesizer(
        _: AVSpeechSynthesizer, didFinish _: AVSpeechUtterance
    ) {
        Task { @MainActor in
            if case .speaking = state { state = .idle }
        }
    }

    public nonisolated func speechSynthesizer(
        _: AVSpeechSynthesizer, didCancel _: AVSpeechUtterance
    ) {
        Task { @MainActor in
            if case .speaking = state { state = .idle }
        }
    }
}
