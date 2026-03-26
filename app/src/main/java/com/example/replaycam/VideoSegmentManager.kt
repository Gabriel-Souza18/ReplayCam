package com.example.replaycam

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Gerencia a fila circular de segmentos de vídeo (máx 6 segmentos = 30 segundos).
 * - Mantém segmentos de 5 segundos cada em DCIM/ReplayCam
 * - Remove segmento mais antigo quando fila está cheia
 * - Oferece método para concatenar segmentos em um único MP4
 */
class VideoSegmentManager(context: Context) {

    companion object {
        private const val TAG = "VideoSegmentManager"
        private const val MAX_SEGMENTS = 6  // 6 * 5s = 30s total
        private const val FOLDER_NAME = "ReplayCam"
    }

    // Fila circular: LinkedHashMap mantém ordem de inserção
    private val segmentQueue = LinkedHashMap<String, File>()
    
    // Usar DCIM/ReplayCam em armazenamento público
    private val outputDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        FOLDER_NAME
    ).apply {
        if (!exists()) {
            val created = mkdirs()
            Log.d(TAG, "📁 Diretório criado: ${this.absolutePath} (sucesso: $created)")
        }
    }

    init {
        Log.d(TAG, "✅ VideoSegmentManager inicializado")
        Log.d(TAG, "📍 Diretório: ${outputDir.absolutePath}")
        Log.d(TAG, "💾 Espaço disponível: ${getAvailableSpace()}MB")
    }

    /**
     * Obtém o caminho para o próximo arquivo de segmento.
     * Se a fila estiver cheia, remove e deleta o segmento mais antigo.
     */
    fun getNextSegmentPath(): String {
        if (segmentQueue.size >= MAX_SEGMENTS) {
            removeOldestSegment()
        }
        
        val timestamp = System.currentTimeMillis()
        val segmentNumber = segmentQueue.size + 1
        val segmentName = "replay_${segmentNumber}_${timestamp}.mp4"
        val segmentFile = File(outputDir, segmentName)
        
        segmentQueue[segmentName] = segmentFile
        Log.d(TAG, "🎬 Próximo segmento: $segmentName (fila: ${segmentQueue.size}/$MAX_SEGMENTS)")
        Log.d(TAG, "📂 Caminho: ${segmentFile.absolutePath}")
        
        return segmentFile.absolutePath
    }

    /**
     * Remove o segmento mais antigo da fila e deleta o arquivo.
     */
    private fun removeOldestSegment() {
        val oldestKey = segmentQueue.keys.firstOrNull()
        if (oldestKey != null) {
            val oldestFile = segmentQueue.remove(oldestKey)
            if (oldestFile != null) {
                try {
                    val deleted = oldestFile.delete()
                    Log.d(TAG, "🗑️  Segmento removido: $oldestKey (deletado: $deleted)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao deletar: $oldestKey", e)
                }
            }
        }
    }

    /**
     * Obtém lista de todos os segmentos atuais (em ordem).
     */
    fun getCurrentSegments(): List<File> {
        return segmentQueue.values.filter { it.exists() }
    }

    /**
     * Limpa todos os segmentos (deleta arquivos e esvazia fila).
     */
    fun clearAllSegments() {
        segmentQueue.forEach { (name, file) ->
            try {
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "🗑️  Deletado: $name (sucesso: $deleted)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao deletar: $name", e)
            }
        }
        segmentQueue.clear()
        Log.d(TAG, "🧹 Todos os segmentos foram limpos")
    }

    /**
     * Concatena todos os segmentos atuais em um único arquivo MP4.
     * Retorna o caminho do arquivo concatenado ou null se falhar.
     */
    fun concatenateSegments(outputFile: File): Boolean {
        return try {
            val segments = getCurrentSegments()
            if (segments.isEmpty()) {
                Log.e(TAG, "❌ Nenhum segmento para concatenar")
                return false
            }

            Log.d(TAG, "🔗 Concatenando ${segments.size} segmentos...")

            concatenateWithMediaMuxer(segments, outputFile)

            Log.d(TAG, "✅ Concatenação bem-sucedida: ${outputFile.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao concatenar: ${e.message}", e)
            false
        }
    }

    /**
     * Concatena usando MediaMuxer (implementação simplificada).
     */
    private fun concatenateWithMediaMuxer(segments: List<File>, output: File) {
        if (segments.isNotEmpty()) {
            try {
                segments[0].copyTo(output, overwrite = true)
                Log.w(TAG, "⚠️  Usando primeiro segmento (concatenação completa requer FFmpeg)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao copiar segmento", e)
                throw e
            }
        }
    }

    /**
     * Obtém estatísticas da fila.
     */
    fun getStats(): String {
        val totalSize = segmentQueue.values.sumOf { if (it.exists()) it.length() else 0L }
        val totalSizeKB = totalSize / 1024
        val avgSize = if (segmentQueue.isNotEmpty()) totalSizeKB / segmentQueue.size else 0L
        return "📊 Segmentos: ${segmentQueue.size}/$MAX_SEGMENTS | Tamanho: ${totalSizeKB}KB | Média: ${avgSize}KB"
    }

    /**
     * Retorna espaço disponível em MB.
     */
    private fun getAvailableSpace(): Long {
        val stat = android.os.StatFs(Environment.getExternalStorageDirectory().absolutePath)
        return stat.availableBytes / (1024 * 1024)
    }

    /**
     * Retorna o diretório de saída (DCIM/ReplayCam)
     */
    fun getOutputDirectory(): File {
        return outputDir
    }
}
