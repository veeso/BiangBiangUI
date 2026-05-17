//
//  CameraZoom.swift
//  BiangBiangUI
//

import CoreGraphics
import Foundation

/// Filter the candidate UI zoom presets to those supported by the device.
public func availablePresets(
    maxUIZoom: CGFloat,
    candidates: [CGFloat] = [1.0, 2.0, 5.0]
) -> [CGFloat] {
    candidates.filter { $0 <= maxUIZoom }
}

/// Clamp a zoom factor to a closed range.
public func clampZoom(_ value: CGFloat, min lower: CGFloat, max upper: CGFloat) -> CGFloat {
    Swift.min(Swift.max(value, lower), upper)
}

/// Translate a UI zoom factor (1.0 == standard wide lens) into the device's
/// `videoZoomFactor` value. On a virtual device like `builtInTripleCamera`,
/// `videoZoomFactor = 1.0` corresponds to the ultra-wide lens. The first
/// switch-over factor (typically `2.0`) is the boundary at which the wide lens
/// kicks in. Pass `1.0` for devices without a virtual switch-over.
public func uiZoomToDeviceZoom(_ uiZoom: CGFloat, switchOverFactor: CGFloat) -> CGFloat {
    uiZoom * switchOverFactor
}

/// Inverse of `uiZoomToDeviceZoom`.
public func deviceZoomToUIZoom(_ deviceZoom: CGFloat, switchOverFactor: CGFloat) -> CGFloat {
    deviceZoom / switchOverFactor
}
