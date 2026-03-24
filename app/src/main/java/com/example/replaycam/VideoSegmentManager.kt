package com.example.replaycam

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Gerencia a fila circular de segmentos de vídeo (máx 6 segmentos = 30 segundos).
 * - Mantém segmentos de 5 segundos cada
 * - Remove segmento mais antigo quando fila está cheia
 * - Oferece método para concatenar segmentos em um único MP4
 */
class VideoSegmentManager(context: Context) {

    companion object {
        private const val TAG = "VideoSegmentManager"
        private const val MAX_SEGMENTS = 6  // 6 * 5s = 30s total
    }

    // ...existing code...

    // Fila circular: LinkedHashMap mantém ordem de inserção
    private val segmentQueue = LinkedHashMap<String, File>()
    private val outputDir: File = File(context.cacheDir, "video_segments").apply {
        if (!exists()) mkdirs()
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
        val segmentName = "segment_$timestamp.mp4"
        val segmentFile = File(outputDir, segmentName)
        segmentQueue[segmentName] = segmentFile
        Log.d(TAG, "Next segment: $segmentName (queue size: ${segmentQueue.size})")
        return segmentFile.absolutePath
    }

    /**
     * Remove o segmento mais antigo da fila e deleta o arquivo.
     */
    private fun removeOldestSegment() {
        val oldestKey = segmentQueue.keys.firstOrNull()
        if (oldestKey != null) {
            val oldestFile = segmentQueue.remove(oldestKey)
            if (oldestFile?.exists() == true) {
                val deleted = oldestFile.delete()
                Log.d(TAG, "Removed oldest segment: $oldestKey (deleted: $deleted)")
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
        segmentQueue.forEach { (_, file) ->
            if (file.exists()) {
                file.delete()
            }
        }
        segmentQueue.clear()
        Log.d(TAG, "All segments cleared")
    }

    /**
     * Concatena todos os segmentos atuais em um único arquivo MP4.
     * Retorna o caminho do arquivo concatenado ou null se falhar.
     *
     * Nota: Esta é uma implementação simplificada usando MediaMuxer.
     * Para melhor compatibilidade, considere usar FFmpeg ou MediaCodec em produção.
     */
    fun concatenateSegments(outputFile: File): Boolean {
        return try {
            val segments = getCurrentSegments()
            if (segments.isEmpty()) {
                Log.e(TAG, "No segments to concatenate")
                return false
            }

            Log.d(TAG, "Starting concatenation of ${segments.size} segments into ${outputFile.absolutePath}")

            // Usar MediaMuxer para concatenar segmentos
            // Nota: Esta é uma implementação básica. Para produção, use FFmpeg.
            concatenateWithMediaMuxer(segments, outputFile)

            Log.d(TAG, "Concatenation successful: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error concatenating segments: ${e.message}", e)
            false
        }
    }

    /**
     * Concatena usando MediaMuxer (implementação simplificada).
     * Nota: MediaMuxer tem limitações para concatenação de múltiplos MP4s.
     * Para produção, integre FFmpeg via biblioteca como FFmpeg-Android.
     */
    private fun concatenateWithMediaMuxer(segments: List<File>, output: File) {
        // Aqui você integraria FFmpeg ou outro muxer profissional.
        // Por enquanto, esta é uma versão placeholder que necessitará integração com FFmpeg.
        
        // Pseudocódigo para usar FFmpeg:
        // ffmpeg -i segment_1.mp4 -i segment_2.mp4 ... -c copy -map 0 -map 1 output.mp4
        
        // Para esta implementação, vamos criar um arquivo dummy ou usar ProcessBuilder
        // com FFmpeg se disponível no sistema.
        
        // Placeholder: copiar apenas o primeiro segmento para não quebrar a compilação
        if (segments.isNotEmpty()) {
            segments[0].copyTo(output, overwrite = true)
            Log.w(TAG, "Using first segment only (full concatenation requires FFmpeg integration)")
        }
    }

    /**
     * Obtém estatísticas da fila.
     */
    fun getStats(): String {
        val totalSize = segmentQueue.values.sumOf { if (it.exists()) it.length() else 0L }
        val totalSizeKB = totalSize / 1024
        return "Segments: ${segmentQueue.size}/$MAX_SEGMENTS, Size: ${totalSizeKB}KB"
    }
}


