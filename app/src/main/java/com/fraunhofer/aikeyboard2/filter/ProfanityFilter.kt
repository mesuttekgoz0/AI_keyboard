package com.fraunhofer.aikeyboard2.filter

import android.content.Context
import com.fraunhofer.aikeyboard2.data.WordRepository
import java.util.Locale

/**
 * Küfür/yasaklı kelime filtresi.
 *
 * Optimizasyonlar:
 *  - Yasaklı kelimeler yüklenirken normalleştirilir → isProfane() O(n) yerine O(k) olur
 *  - normalizeTurkishChars sade bir char-table dönüşümü kullanır
 *  - profaneWords değişmeyene kadar tekrar yükleme yapılmaz (dirty flag)
 */
object ProfanityFilter {

    /** Normalize edilmiş yasaklı kelime seti — yükleme sırasında bir kez hazırlanır. */
    private var normalizedProfane: Set<String> = emptySet()

    /** Son yüklenen ham kelime sayısı — gereksiz yeniden yüklemeyi önler. */
    private var loadedHash: Int = -1

    // Türkçe → Latin karakter dönüşüm tablosu (allocation-free)
    private val TR_LOWER = charArrayOf('ı','İ','ğ','Ğ','ü','Ü','ş','Ş','ö','Ö','ç','Ç')
    private val TR_EQUIV = charArrayOf('i','i','g','g','u','u','s','s','o','o','c','c')

    /**
     * Kelime listesini repository'den yükler.
     * Kelime seti değişmemişse (hash aynıysa) yeniden yüklemez.
     */
    fun loadFromRepository(context: Context) {
        val repo = WordRepository(context)
        val wordSet = repo.getWordSet()
        val hash = wordSet.hashCode()
        if (hash == loadedHash) return          // zaten güncel

        loadedHash = hash
        val trLocale = Locale("tr", "TR")
        normalizedProfane = wordSet.mapTo(HashSet(wordSet.size * 2)) { word ->
            normalizeTurkish(word.lowercase(trLocale))
        }
    }

    /**
     * Verilen kelimenin yasaklı olup olmadığını kontrol eder.
     * Zaman karmaşıklığı: O(k·n) → k = kelime uzunluğu, n = yasaklı set boyutu
     * (normalizeEdilmiş set sayesinde her kelime için normalizasyon tekrarı yok)
     */
    fun isProfane(word: String): Boolean {
        if (word.isBlank() || normalizedProfane.isEmpty()) return false
        val normalized = normalizeTurkish(word.lowercase(Locale("tr", "TR")))
        return normalizedProfane.any { profane ->
            normalized == profane || normalized.contains(profane)
        }
    }

    /** Kelimeyi sansürler. */
    fun censor(@Suppress("UNUSED_PARAMETER") word: String): String = "***"

    /**
     * Türkçe karakterleri Latin eşdeğerleriyle değiştirir.
     * StringBuilder kullanarak allocation sayısını minimize eder.
     */
    private fun normalizeTurkish(input: String): String {
        var result: StringBuilder? = null
        for (i in input.indices) {
            val c = input[i]
            val idx = TR_LOWER.indexOf(c)
            if (idx >= 0) {
                if (result == null) result = StringBuilder(input.substring(0, i))
                result.append(TR_EQUIV[idx])
            } else {
                result?.append(c)
            }
        }
        return result?.toString() ?: input
    }
}
