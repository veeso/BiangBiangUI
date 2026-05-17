//
//  SectionView.swift
//  BiangBiangUI
//
//  Reusable titled section with a single trailing action button.
//

import SwiftUI

/// A titled section container with a single trailing action button.
public struct SectionView<Content: View>: View {
    let title: String
    let actionLabel: String
    let actionIcon: String
    let action: () -> Void
    @ViewBuilder let content: Content

    public init(
        title: String,
        actionLabel: String,
        actionIcon: String,
        action: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.actionLabel = actionLabel
        self.actionIcon = actionIcon
        self.action = action
        self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title)
                    .font(.headline)
                Spacer()
                Button(actionLabel, systemImage: actionIcon, action: action)
            }
            content
        }
    }
}
