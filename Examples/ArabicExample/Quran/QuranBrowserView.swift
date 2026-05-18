//
//  QuranBrowserView.swift
//  ArabicExample
//
//  Minimal Quran browser backing the plugin's tab: a searchable list of
//  ayat loaded from `QuranDataset`. Not a Harakat port — Harakat has no
//  standalone browser screen — but kept deliberately small so the plugin's
//  `tabs` hook has real content to render.
//

import SwiftUI

struct QuranBrowserView: View {
    @State private var ayat: [QuranAyah] = []
    @State private var surahNames: [Int: SurahName] = [:]
    @State private var query = ""
    @State private var loaded = false

    private var filtered: [QuranAyah] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return Array(ayat.prefix(200)) }
        return ayat.filter {
            $0.normalized.contains(q) || $0.translationEn.localizedCaseInsensitiveContains(q)
        }
        .prefix(200)
        .map { $0 }
    }

    var body: some View {
        NavigationStack {
            List(filtered) { ayah in
                VStack(alignment: .leading, spacing: 4) {
                    Text("Surah \(ayah.surah) · Ayah \(ayah.ayah)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(ayah.text)
                        .font(.title3)
                        .multilineTextAlignment(.trailing)
                        .environment(\.layoutDirection, .rightToLeft)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                    Text(ayah.translationEn)
                        .font(.subheadline)
                }
            }
            .navigationTitle("Quran")
            .searchable(text: $query)
        }
        .task {
            guard !loaded else { return }
            loaded = true
            let ds = QuranDataset.shared
            await ds.loadIfNeeded()
            ayat = await ds.all
            surahNames = await ds.surahNames
        }
    }
}
