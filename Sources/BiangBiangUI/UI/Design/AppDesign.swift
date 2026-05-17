//
//  AppDesign.swift
//  BiangBiangUI
//
//  Shared design constants used across the app for a consistent look.
//

import Foundation

public enum AppDesign {
    /// Default corner radius for cards and rounded containers.
    public static let cornerRadius: CGFloat = 12
    /// Larger corner radius for prominent containers (toasts, banners).
    public static let cornerRadiusLarge: CGFloat = 16
    /// Compact corner radius for small surfaces (overlay labels).
    public static let cornerRadiusCompact: CGFloat = 6

    /// Spacing between sections within a screen.
    public static let sectionSpacing: CGFloat = 20
    /// Standard horizontal padding for content edges.
    public static let horizontalPadding: CGFloat = 20
    /// Padding above the bottom safe area for floating controls.
    public static let bottomToolbarPadding: CGFloat = 40
    /// Inner spacing for stacks of related controls.
    public static let stackSpacing: CGFloat = 12

    /// Standard tap target size (HIG minimum).
    public static let tapTarget: CGFloat = 44

    /// Default duration for short interaction animations.
    public static let shortAnimation: Double = 0.2
    /// Toast fade duration.
    public static let toastAnimation: Double = 0.25

    /// Standard shadow radius for floating buttons.
    public static let buttonShadow: CGFloat = 4
}
