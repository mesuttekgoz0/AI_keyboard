package com.fraunhofer.aikeyboard2.autocomplete

import android.content.Context
import com.fraunhofer.aikeyboard2.data.WordRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.min


/**
 * Performanslı Türkçe Otomatik Düzeltme ve Öneri Motoru.
 *
 * Özellikler:
 *  - Trie (Ön Ek Ağacı) ile <1ms sürede kelime tamamlama.
 *  - Levenshtein Mesafe algoritması ile typo (yazım hatası) düzeltme.
 *  - Kullanıcının eklediği özel sözlük kelimelerini de otomatik kapsar.
 */
object AutoCorrectEngine {

    private class TrieNode {
        val children = HashMap<Char, TrieNode>(4)
        var isWord: Boolean = false
        var word: String = ""
        var frequency: Int = 0
    }

    private val root = TrieNode()
    private val allWords = ArrayList<Pair<String, String>>(1000) // (raw, normalized)
    private var isLoaded = false
    private val trLocale = Locale("tr", "TR")

    /**
     * Sözlük dosyası ve Kullanıcı Özel Sözlüğünü tek seferde Trie yapısına yükler.
     */
    fun loadDictionary(context: Context) {
        if (isLoaded) return

        try {
            val isr = InputStreamReader(context.assets.open("turkish_words.txt"), "UTF-8")
            val reader = BufferedReader(isr)
            var line: String? = reader.readLine()
            var rank = 10000

            while (line != null) {
                val trimmed = line.trim().lowercase(trLocale)
                if (trimmed.isNotEmpty()) {
                    insertWord(trimmed, rank--)
                }
                line = reader.readLine()
            }
            reader.close()

            // Kullanıcının eklediği kelimeleri de ekle
            val repoWords = WordRepository(context).getWordSet()
            for (w in repoWords) {
                val trimmed = w.trim().lowercase(trLocale)
                if (trimmed.isNotEmpty()) {
                    insertWord(trimmed, 20000) // Kullanıcı kelimeleri öncelikli
                }
            }

            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun insertWord(word: String, freq: Int) {
        var curr = root
        for (c in word) {
            curr = curr.children.getOrPut(c) { TrieNode() }
        }
        curr.isWord = true
        curr.word = word
        curr.frequency = freq
        allWords.add(word to normalize(word))
    }

    /**
     * Girilen metne göre 3 adet en uygun öneri döner.
     *  - Slot 1 (Sol): Alternatif veya Tamamlayıcı
     *  - Slot 2 (Orta): En Yüksek İhtimal (Oto-Düzeltme Adayı)
     *  - Slot 3 (Sağ): Alternatif / Yazım Hatası Düzeltmesi
     */
    fun getSuggestions(input: String, limit: Int = 3): List<String> {
        val trimmed = input.trim().lowercase(trLocale)
        if (trimmed.isEmpty() || !isLoaded) return emptyList()

        val results = LinkedHashSet<String>()

        // 1. Doğrudan Ön Ek (Prefix) Araması
        val prefixMatches = findPrefixMatches(trimmed, limit = 5)
        for (match in prefixMatches) {
            results.add(match)
            if (results.size >= limit) break
        }

        // 2. Eğer yeterli ön ek eşleşmesi yoksa, Levenshtein (Typo) Araması yap
        if (results.size < limit) {
            val normalizedInput = normalize(trimmed)
            val typoMatches = findTypoMatches(trimmed, normalizedInput, limit = limit - results.size)
            results.addAll(typoMatches)
        }

        val list = results.toList()
        if (list.isEmpty()) return emptyList()

        // 3 Slot Düzenlemesi: [ Alt1, Best (Orta), Alt2 ]
        return when (list.size) {
            1 -> listOf(list[0])
            2 -> listOf(list[1], list[0]) // En iyi orta slotta
            else -> listOf(list[1], list[0], list[2]) // list[0] en iyi -> orta slota yerleştir
        }
    }

    /**
     * Trie üzerinde ön ek araması yapar.
     */
    private fun findPrefixMatches(prefix: String, limit: Int): List<String> {
        var curr = root
        for (c in prefix) {
            curr = curr.children[c] ?: return emptyList()
        }

        val matches = ArrayList<Pair<String, Int>>()
        collectAll(curr, matches)

        return matches
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }

    private fun collectAll(node: TrieNode, list: MutableList<Pair<String, Int>>) {
        if (node.isWord) {
            list.add(node.word to node.frequency)
        }
        for (child in node.children.values) {
            collectAll(child, list)
        }
    }

    /**
     * Levenshtein mesafesi ile en yakın kelimeleri bulur.
     */
    private fun findTypoMatches(rawInput: String, normInput: String, limit: Int): List<String> {
        val maxDist = if (rawInput.length <= 4) 1 else 2
        val candidates = ArrayList<Pair<String, Int>>()

        for ((word, normWord) in allWords) {
            if (kotlin.math.abs(normInput.length - normWord.length) > maxDist) continue

            val dist = levenshtein(normInput, normWord)
            if (dist <= maxDist) {
                candidates.add(word to dist)
            }
        }

        return candidates
            .sortedBy { it.second }
            .map { it.first }
            .take(limit)
    }

    /**
     * Levenshtein Mesafe Hesaplayıcı (Edit Distance)
     */
    private fun levenshtein(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[len1][len2]
    }

    private fun normalize(str: String): String {
        return str.replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
    }
}
