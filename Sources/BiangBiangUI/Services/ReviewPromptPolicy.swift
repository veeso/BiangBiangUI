import Foundation

/// Pure, side-effect-free decision logic for the "rate the app" prompt.
public enum ReviewPromptPolicy {
    /// Minimum cold launches before the prompt is eligible to show.
    public static let launchThreshold = 3
    /// Counter cap to avoid unbounded writes.
    public static let launchCap = 5

    public static func nextLaunchCount(_ current: Int) -> Int {
        min(current + 1, launchCap)
    }

    public static func shouldShow(launchCount: Int, dismissed: Bool) -> Bool {
        !dismissed && launchCount >= launchThreshold
    }
}
