import CoreGraphics
import UIKit

enum CameraLayoutPolicy {
    enum Side: Equatable {
        case left
        case right
    }

    static let minShortEdge: CGFloat = 500
    static let lensOnRightOrientation: UIInterfaceOrientation = .landscapeLeft

    static func isSplit(size: CGSize, isBuiltInDisplay: Bool) -> Bool {
        isBuiltInDisplay
            && size.width > size.height
            && min(size.width, size.height) >= minShortEdge
    }

    static func cameraSide(for orientation: UIInterfaceOrientation) -> Side {
        guard orientation == .landscapeLeft || orientation == .landscapeRight else {
            return .right
        }
        return orientation == lensOnRightOrientation ? .right : .left
    }
}
