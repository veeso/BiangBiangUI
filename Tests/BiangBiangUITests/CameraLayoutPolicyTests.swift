@testable import BiangBiangUI
import Testing
import UIKit

struct CameraLayoutPolicyTests {
    @Test func splitTruthTable() {
        let cases: [(CGFloat, CGFloat, Bool, Bool)] = [
            (744, 1024, true, false), (932, 430, true, false),
            (1024, 744, true, true), (1024, 744, false, false),
            (1000, 499, true, false), (1000, 500, true, true),
            (1000, 501, true, true), (500, 500, true, false),
            (501, 500, true, true), (0, 0, true, false),
        ]
        for (width, height, builtIn, expected) in cases {
            #expect(CameraLayoutPolicy.isSplit(
                size: CGSize(width: width, height: height),
                isBuiltInDisplay: builtIn
            ) == expected)
        }
    }

    @Test func physicalSideUsesNamedOrientation() {
        let right = CameraLayoutPolicy.lensOnRightOrientation
        let opposite: UIInterfaceOrientation =
            right == .landscapeLeft ? .landscapeRight : .landscapeLeft
        #expect(CameraLayoutPolicy.cameraSide(for: right) == .right)
        #expect(CameraLayoutPolicy.cameraSide(for: opposite) == .left)
        #expect(CameraLayoutPolicy.cameraSide(for: .unknown) == .right)
    }
}
