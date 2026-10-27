@testable import BiangBiangUI
import SwiftUI
import Testing
import UIKit

@MainActor
private final class PaneRecord {
    var views: [String: UIView] = [:]
    var directions: [String: LayoutDirection] = [:]
    var cameraCreations = 0
}

private struct PaneProbe: UIViewRepresentable {
    let name: String
    let record: PaneRecord
    @Environment(\.layoutDirection) private var direction

    func makeUIView(context _: Context) -> UIView {
        let view = UIView()
        record.views[name] = view
        if name == "camera" {
            record.cameraCreations += 1
        }
        return view
    }

    func updateUIView(_: UIView, context _: Context) {
        record.directions[name] = direction
    }
}

@MainActor @Suite(.serialized)
struct CameraSplitLayoutTests {
    @Test func geometrySideAndDirectionChangesPreserveCamera() async throws {
        let record = PaneRecord()
        func content(_ size: CGSize, _ builtIn: Bool,
                     _ side: CameraLayoutPolicy.Side) -> some View
        {
            CameraSplitLayout(isBuiltInDisplay: builtIn, cameraSide: side) {
                PaneProbe(name: "log", record: record)
            } camera: {
                PaneProbe(name: "camera", record: record)
            }
            .frame(width: size.width, height: size.height)
            .environment(\.layoutDirection, .rightToLeft)
        }
        let wide = CGSize(width: 1024, height: 744)
        let host = UIHostingController(rootView: content(wide, true, .right))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 1400, height: 1400))
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; window.rootViewController = nil }
        let cases: [(CGSize, Bool, CameraLayoutPolicy.Side, Bool)] = [
            (wide, true, .right, true), (wide, true, .left, true),
            (CGSize(width: 744, height: 1024), true, .right, false),
            (wide, false, .right, false), (wide, true, .right, true),
        ]
        for (size, builtIn, side, split) in cases {
            host.rootView = content(size, builtIn, side)
            for _ in 0 ..< 10 {
                host.view.setNeedsLayout()
                host.view.layoutIfNeeded()
                await Task.yield()
            }
            let camera = try #require(record.views["camera"])
            let cameraFrame = camera.convert(camera.bounds, to: host.view)
            #expect(abs(cameraFrame.width - size.width / (split ? 2 : 1)) < 1)
            #expect(record.cameraCreations == 1)
            #expect(record.directions["camera"] == .rightToLeft)
            let log = record.views["log"]
            #expect((log?.window != nil) == split)
            if split, let log {
                let logFrame = log.convert(log.bounds, to: host.view)
                #expect(abs(logFrame.width - cameraFrame.width) < 1)
                #expect((cameraFrame.minX > logFrame.minX) == (side == .right))
                #expect(record.directions["log"] == .rightToLeft)
            }
        }
    }
}
