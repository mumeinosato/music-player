package com.mumeinosato.musicplayer.data

import android.content.Context
import com.mumeinosato.musicplayer.ui.Track
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 音声ファイル・曲リスト(library.csv)・hash を internal storage に持つ。
 * 再生は必ずここにあるファイルから行う。UI の曲リストも library.csv から組み立てる。
 */
class MusicRepository(
    context: Context,
    val api: ServerApi = ServerApi(),
    private val r2: R2Client = R2Client(),
) {
    private val musicDir = File(context.filesDir, "music").apply { mkdirs() }
    private val csvFile = File(context.filesDir, "library.csv")
    private val hashFile = File(context.filesDir, "library.hash")

    fun fileOf(id: String): File = File(musicDir, "$id.opus")

    /** library.csv の曲のうち、ダウンロード済みで再生できるものだけ返す */
    fun localTracks(): List<Track> =
        readCsv().filter { (id, _) -> fileOf(id).exists() }
            .map { (id, name) -> Track(title = name.ifEmpty { id }, id = id) }

    /**
     * 保存済み hash で /latest を取り、add をダウンロード・remove を削除して、
     * all_id / all_name から library.csv を作り直す。ダウンロードに失敗した曲数を返す。
     */
    fun fetchLatest(onProgress: (String) -> Unit = {}): Int {
        val diff = api.getLatest(readHash())
        if (diff.upToDate) return 0

        // ID はファイル名になるので、安全なものだけ扱う
        diff.remove.filter(::isSafeId).forEach { fileOf(it).delete() }

        val targets = diff.add.filter(::isSafeId)
        var failed = 0
        targets.forEachIndexed { i, id ->
            onProgress("Downloading ${i + 1}/${targets.size}")
            try {
                r2.download("$id.opus", fileOf(id))
            } catch (e: Exception) {
                failed++
            }
        }

        val rows = diff.allIds.mapIndexed { i, id -> id to diff.allNames.getOrNull(i).orEmpty() }
            .filter { (id, _) -> isSafeId(id) }
        writeCsv(rows)

        // 最新リストに無い曲はローカルから消す（未知 hash で全件 add になった場合の取りこぼし対策）
        val keep = rows.mapTo(HashSet()) { (id, _) -> "$id.opus" }
        // 中断で残った .part も一緒に消す
        musicDir.listFiles { f -> f.extension == "part" || (f.extension == "opus" && f.name !in keep) }
            ?.forEach { it.delete() }

        // 失敗があるときは hash を進めない。次回また同じ差分が返るので再取得される
        if (failed == 0) writeAtomic(hashFile, diff.hash)
        return failed
    }

    private fun readHash(): String = if (hashFile.exists()) hashFile.readText().trim() else ""

    // --- CSV: 1行 = "id,name"。name は必要なら "" で囲み、中の " は "" にする ---

    private fun writeCsv(rows: List<Pair<String, String>>) {
        writeAtomic(csvFile, rows.joinToString("\n") { (id, name) -> "$id,${escape(name)}" })
    }

    private fun readCsv(): List<Pair<String, String>> {
        if (!csvFile.exists()) return emptyList()
        return csvFile.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
            val comma = line.indexOf(',')
            if (comma <= 0) return@mapNotNull null
            val id = line.substring(0, comma)
            if (!isSafeId(id)) return@mapNotNull null
            id to unescape(line.substring(comma + 1))
        }
    }

    private fun escape(s: String): String {
        val flat = s.replace("\r", " ").replace("\n", " ")
        return if (flat.any { it == ',' || it == '"' }) "\"" + flat.replace("\"", "\"\"") + "\"" else flat
    }

    private fun unescape(s: String): String =
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s.substring(1, s.length - 1).replace("\"\"", "\"")
        } else {
            s
        }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        // renameTo は失敗しても false を返すだけなので、例外になる move を使う
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun isSafeId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,64}"))
}
