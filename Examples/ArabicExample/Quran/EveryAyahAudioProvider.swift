//
//  EveryAyahAudioProvider.swift
//  ArabicExample
//
//  The EveryAyah-streaming part of Harakat-Lens's `AudioPlayerService`,
//  ported into a library `AudioProvider`. Streams
//  `https://everyayah.com/data/Alafasy_128kbps/SSSAAA.mp3` for an ayah and
//  falls back to system TTS for arbitrary text. AVPlayer / synthesizer
//  plumbing and the KVO/notification observers are ported verbatim.
//

import AVFoundation
import BiangBiangUI
import Foundation
import Observation

@MainActor
@Observable
public final class EveryAyahAudioProvider: NSObject, AudioProvider, AVSpeechSynthesizerDelegate {
    enum State: Equatable {
        case idle
        case loadingAyah(surah: Int, ayah: Int)
        case playingAyah(surah: Int, ayah: Int)
        case speakingTTS
    }

    private(set) var state: State = .idle

    private let synthesizer = AVSpeechSynthesizer()
    @ObservationIgnored private var player: AVPlayer?
    @ObservationIgnored private var endObserver: NSObjectProtocol?
    @ObservationIgnored private var statusObservation: NSKeyValueObservation?

    private let reciter = "Alafasy_128kbps"

    override public init() {
        super.init()
        synthesizer.delegate = self
    }

    // MARK: - AudioProvider

    /// Library entry point. When `text` parses as `"surah:ayah"` (the form
    /// `QuranAyah.id` produces) the recitation is streamed from EveryAyah;
    /// otherwise the text is spoken with the system synthesizer in the given
    /// language (falling back to Arabic).
    public func play(text: String, languageCode: String?) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        if let ref = Self.parseAyahReference(trimmed) {
            playAyah(surah: ref.surah, ayah: ref.ayah)
        } else {
            speak(trimmed, languageCode: languageCode ?? "ar")
        }
    }

    public var isPlaying: Bool {
        state != .idle
    }

    // MARK: - System TTS fallback

    func speak(_ text: String, languageCode: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        stop()
        configureSession()
        let utterance = AVSpeechUtterance(string: trimmed)
        utterance.voice = AVSpeechSynthesisVoice(language: languageCode)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        state = .speakingTTS
        synthesizer.speak(utterance)
    }

    // MARK: - EveryAyah streaming (ported from AudioPlayerService)

    func playAyah(surah: Int, ayah: Int) {
        stop()
        guard let url = ayahURL(surah: surah, ayah: ayah) else { return }
        configureSession()
        let item = AVPlayerItem(url: url)
        let newPlayer = AVPlayer(playerItem: item)
        player = newPlayer
        state = .loadingAyah(surah: surah, ayah: ayah)

        statusObservation = item.observe(\.status, options: [.new]) { observedItem, _ in
            let status = observedItem.status
            Task { @MainActor [weak self] in
                guard let self else { return }
                switch status {
                case .readyToPlay:
                    if case .loadingAyah = self.state {
                        self.state = .playingAyah(surah: surah, ayah: ayah)
                        self.player?.play()
                    }
                case .failed:
                    self.stop()
                default:
                    break
                }
            }
        }

        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { _ in
            Task { @MainActor [weak self] in
                self?.stop()
            }
        }
    }

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

    func isPlayingAyah(surah: Int, ayah: Int) -> Bool {
        switch state {
        case let .loadingAyah(s, a), let .playingAyah(s, a):
            return s == surah && a == ayah
        default:
            return false
        }
    }

    func isLoadingAyah(surah: Int, ayah: Int) -> Bool {
        if case let .loadingAyah(s, a) = state {
            return s == surah && a == ayah
        }
        return false
    }

    // MARK: - Helpers

    private func configureSession() {
        #if os(iOS)
            let session = AVAudioSession.sharedInstance()
            try? session.setCategory(.playback, mode: .spokenAudio, options: [])
            try? session.setActive(true, options: [])
        #endif
    }

    private func ayahURL(surah: Int, ayah: Int) -> URL? {
        let s = String(format: "%03d", surah)
        let a = String(format: "%03d", ayah)
        return URL(string: "https://everyayah.com/data/\(reciter)/\(s)\(a).mp3")
    }

    private static func parseAyahReference(_ text: String) -> (surah: Int, ayah: Int)? {
        let parts = text.split(separator: ":")
        guard parts.count == 2,
              let surah = Int(parts[0].trimmingCharacters(in: .whitespaces)),
              let ayah = Int(parts[1].trimmingCharacters(in: .whitespaces))
        else { return nil }
        return (surah, ayah)
    }

    // MARK: - AVSpeechSynthesizerDelegate

    public nonisolated func speechSynthesizer(
        _: AVSpeechSynthesizer, didFinish _: AVSpeechUtterance
    ) {
        Task { @MainActor in
            if case .speakingTTS = state { state = .idle }
        }
    }

    public nonisolated func speechSynthesizer(
        _: AVSpeechSynthesizer, didCancel _: AVSpeechUtterance
    ) {
        Task { @MainActor in
            if case .speakingTTS = state { state = .idle }
        }
    }
}
