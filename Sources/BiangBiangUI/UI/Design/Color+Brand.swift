//
//  Color+Brand.swift
//  BiangBiangUI
//
//  Bridges `Branding.accentColorHex` (a plain string in the SwiftUI-free
//  Config layer) to a SwiftUI `Color`. This is the seam apps use to pass
//  their brand colour: set `Branding.accentColorHex` and every config-driven
//  control (tint, active camera buttons, overlay checkmark) picks it up.
//

#if canImport(UIKit)
    import SwiftUI

    public extension Color {
        /// Decodes a `#RGB`, `#RRGGBB`, or `#RRGGBBAA` hex string. Falls back to
        /// `.accentColor` when the string is malformed so a typo never renders
        /// an invisible control.
        init(hex: String) {
            let raw = hex.trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "#", with: "")

            var value: UInt64 = 0
            guard Scanner(string: raw).scanHexInt64(&value) else {
                self = .accentColor
                return
            }

            let r, g, b, a: Double
            switch raw.count {
            case 3: // RGB (12-bit)
                r = Double((value >> 8) & 0xF) / 15
                g = Double((value >> 4) & 0xF) / 15
                b = Double(value & 0xF) / 15
                a = 1
            case 6: // RRGGBB
                r = Double((value >> 16) & 0xFF) / 255
                g = Double((value >> 8) & 0xFF) / 255
                b = Double(value & 0xFF) / 255
                a = 1
            case 8: // RRGGBBAA
                r = Double((value >> 24) & 0xFF) / 255
                g = Double((value >> 16) & 0xFF) / 255
                b = Double((value >> 8) & 0xFF) / 255
                a = Double(value & 0xFF) / 255
            default:
                self = .accentColor
                return
            }

            self = Color(.sRGB, red: r, green: g, blue: b, opacity: a)
        }
    }

    public extension Branding {
        /// The brand accent colour, decoded from `accentColorHex`.
        var accentColor: Color {
            Color(hex: accentColorHex)
        }
    }
#endif
