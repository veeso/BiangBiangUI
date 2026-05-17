//
//  CopyToast.swift
//  BiangBiangUI
//
//  Brief confirmation toast shown after copying recognized text.
//

import SwiftUI

/// A brief toast overlay confirming a copy action.
///
/// The caller must supply the `message` string (e.g. from `config.strings.textCopied`).
public struct CopyToast: View {
    let message: String

    public init(message: String) {
        self.message = message
    }

    public var body: some View {
        Text(message)
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(.ultraThinMaterial)
            .clipShape(.rect(cornerRadius: AppDesign.cornerRadius))
            .shadow(radius: 6)
    }
}
