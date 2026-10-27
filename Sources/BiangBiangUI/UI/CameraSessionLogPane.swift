import SwiftUI

struct CameraSessionLogPane: View {
    let log: CameraSessionLog
    let strings: UIStrings
    let accent: Color
    let highlightedIDs: Set<UUID>
    let onCopy: (CameraSessionLog.Entry) -> Void
    let onSave: (CameraSessionLog.Entry) -> Void
    let onClear: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(strings.cameraLogTitle) · \(log.entries.count)")
                    .font(.headline)
                    .foregroundStyle(.white)
                Spacer(minLength: AppDesign.stackSpacing)
                Button(action: onClear) {
                    Text(strings.cameraLogClear)
                        .frame(minWidth: AppDesign.tapTarget, minHeight: AppDesign.tapTarget)
                        .contentShape(Rectangle())
                }
                .foregroundStyle(accent)
                .accessibilityLabel(strings.cameraLogClear)
            }
            .padding(AppDesign.horizontalPadding)

            if log.entries.isEmpty {
                Spacer(minLength: 0)
                Text(strings.cameraLogEmpty)
                    .font(.body)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white.opacity(0.72))
                    .padding(AppDesign.horizontalPadding)
                Spacer(minLength: 0)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: AppDesign.stackSpacing) {
                        ForEach(log.entries) { entry in
                            CameraSessionLogRow(
                                entry: entry,
                                strings: strings,
                                accent: accent,
                                isHighlighted: highlightedIDs.contains(entry.id),
                                reduceMotion: reduceMotion,
                                onCopy: { onCopy(entry) },
                                onSave: { onSave(entry) }
                            )
                        }
                    }
                    .padding(.horizontal, AppDesign.horizontalPadding)
                    .padding(.vertical, AppDesign.stackSpacing)
                }
            }

            Text(strings.tapToCopyLongPressToSave)
                .font(.caption2)
                .multilineTextAlignment(.center)
                .foregroundStyle(.white.opacity(0.62))
                .padding(AppDesign.stackSpacing)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}

struct CameraSessionLogRow: View {
    let entry: CameraSessionLog.Entry
    let strings: UIStrings
    let accent: Color
    let isHighlighted: Bool
    let reduceMotion: Bool
    let onCopy: () -> Void
    let onSave: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: AppDesign.stackSpacing) {
            VStack(alignment: .leading, spacing: 4) {
                Text(entry.original)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.leading)
                Text(entry.transliteration)
                    .font(.subheadline)
                    .foregroundStyle(accent)
                    .multilineTextAlignment(.leading)
            }
            Spacer(minLength: 4)
            Image(systemName: "doc.on.doc")
                .font(.body)
                .foregroundStyle(.white.opacity(0.72))
                .accessibilityHidden(true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(AppDesign.stackSpacing)
        .background {
            RoundedRectangle(cornerRadius: AppDesign.cornerRadiusCompact)
                .fill(isHighlighted ? accent.opacity(0.2) : Color.white.opacity(0.06))
        }
        .contentShape(Rectangle())
        .gesture(
            LongPressGesture(minimumDuration: 0.3)
                .exclusively(before: TapGesture())
                .onEnded(handleGesture)
        )
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { onCopy() }
        .animation(
            reduceMotion ? nil : .easeOut(duration: AppDesign.toastAnimation),
            value: isHighlighted
        )
        .accessibilityLabel("\(entry.original), \(entry.transliteration)")
        .accessibilityHint(strings.tapToCopyLongPressToSave)
        .accessibilityActions {
            ForEach(Array(namedActions.enumerated()), id: \.offset) { _, action in
                Button(action.title, action: action.perform)
            }
        }
    }

    var namedActions: [(title: String, perform: () -> Void)] {
        [(strings.copy, onCopy), (strings.save, onSave)]
    }

    func handleGesture(_ result: ExclusiveGesture<LongPressGesture, TapGesture>.Value) {
        switch result {
        case .first: onSave()
        case .second: onCopy()
        }
    }
}
