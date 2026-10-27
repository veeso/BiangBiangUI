import SwiftUI

struct CameraSplitLayout<SidePane: View, CameraPane: View>: View {
    let isBuiltInDisplay: Bool
    let cameraSide: CameraLayoutPolicy.Side
    @ViewBuilder let sidePane: SidePane
    @ViewBuilder let camera: CameraPane
    @Environment(\.layoutDirection) private var inheritedLayoutDirection

    var body: some View {
        GeometryReader { geometry in
            let isSplit = CameraLayoutPolicy.isSplit(
                size: geometry.size,
                isBuiltInDisplay: isBuiltInDisplay
            )
            let paneWidth = isSplit ? geometry.size.width / 2 : geometry.size.width
            let containerLayoutDirection: LayoutDirection =
                cameraSide == .right ? .leftToRight : .rightToLeft

            HStack(spacing: 0) {
                if isSplit {
                    sidePane
                        .frame(width: paneWidth, height: geometry.size.height)
                        .environment(\.layoutDirection, inheritedLayoutDirection)
                }

                camera
                    .frame(width: paneWidth, height: geometry.size.height)
                    .clipped()
                    .contentShape(Rectangle())
                    .environment(\.layoutDirection, inheritedLayoutDirection)
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .environment(\.layoutDirection, containerLayoutDirection)
        }
    }
}
