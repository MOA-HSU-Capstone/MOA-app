package com.example.a20260310.data.local

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * 여러 AAC/M4A 등 세그먼트를 디코딩해 PCM을 이어 붙인 뒤 하나의 WAV로 저장한다.
 * [MeetingSessionViewModel] 업로드 경로와 동일한 처리.
 */
object AudioSegmentMerger {

    fun mergeSegmentsToWav(segments: List<File>): File {
        require(segments.isNotEmpty()) { "No segment files to merge." }
        val output =
            File.createTempFile(
                "moa_merged_${System.currentTimeMillis()}_",
                ".wav",
                segments.first().parentFile,
            )
        FileOutputStream(output).use { fos ->
            fos.write(ByteArray(44))
            var totalPcmBytes = 0L
            var sampleRateHz = 16_000
            var channelCount = 1
            var bitsPerSample = 16
            var hasAudio = false

            segments.forEach { segment ->
                val extractor = MediaExtractor()
                var codec: MediaCodec? = null
                try {
                    extractor.setDataSource(segment.absolutePath)
                    val trackIndex =
                        (0 until extractor.trackCount).firstOrNull { idx ->
                            extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)
                                ?.startsWith("audio/") == true
                        } ?: return@forEach
                    extractor.selectTrack(trackIndex)
                    val format = extractor.getTrackFormat(trackIndex)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: return@forEach

                    val sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val bps =
                        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            when (format.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                                2 -> 16
                                4 -> 32
                                else -> 16
                            }
                        } else {
                            16
                        }

                    if (!hasAudio) {
                        sampleRateHz = sr
                        channelCount = ch
                        bitsPerSample = bps
                        hasAudio = true
                    } else {
                        require(sampleRateHz == sr && channelCount == ch) {
                            "Segment audio format mismatch."
                        }
                    }

                    codec = MediaCodec.createDecoderByType(mime)
                    codec.configure(format, null, null, 0)
                    codec.start()

                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false

                    while (!outputDone) {
                        if (!inputDone) {
                            val inputIndex = codec.dequeueInputBuffer(10_000)
                            if (inputIndex >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        sampleSize,
                                        extractor.sampleTime,
                                        0,
                                    )
                                    extractor.advance()
                                }
                            }
                        }

                        val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                        when {
                            outputIndex >= 0 -> {
                                val outputBuffer = codec.getOutputBuffer(outputIndex)
                                if (outputBuffer != null && info.size > 0) {
                                    outputBuffer.position(info.offset)
                                    outputBuffer.limit(info.offset + info.size)
                                    val tmp = ByteArray(min(64 * 1024, info.size))
                                    while (outputBuffer.hasRemaining()) {
                                        val toRead = min(tmp.size, outputBuffer.remaining())
                                        outputBuffer.get(tmp, 0, toRead)
                                        fos.write(tmp, 0, toRead)
                                    }
                                    totalPcmBytes += info.size.toLong()
                                }
                                codec.releaseOutputBuffer(outputIndex, false)
                                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                }
                            }
                            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val outFormat = codec?.outputFormat ?: continue
                                if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                    bitsPerSample =
                                        when (outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                                            2 -> 16
                                            4 -> 32
                                            else -> bitsPerSample
                                        }
                                }
                            }
                        }
                    }
                } finally {
                    try {
                        codec?.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        codec?.release()
                    } catch (_: Exception) {
                    }
                    extractor.release()
                }
            }

            require(totalPcmBytes > 0L) { "No decodable PCM data from audio segments." }
            writeWavHeader(
                output = output,
                pcmDataSize = totalPcmBytes,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                bitsPerSample = bitsPerSample,
            )
        }
        return output
    }
}

private fun writeWavHeader(
    output: File,
    pcmDataSize: Long,
    sampleRateHz: Int,
    channelCount: Int,
    bitsPerSample: Int,
) {
    val byteRate = sampleRateHz * channelCount * bitsPerSample / 8
    val blockAlign = channelCount * bitsPerSample / 8
    RandomAccessFile(output, "rw").use { raf ->
        raf.seek(0)
        raf.writeBytes("RIFF")
        raf.writeIntLE((36L + pcmDataSize).toInt())
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.writeIntLE(16)
        raf.writeShortLE(if (bitsPerSample == 32) 3 else 1)
        raf.writeShortLE(channelCount.toShort().toInt())
        raf.writeIntLE(sampleRateHz)
        raf.writeIntLE(byteRate)
        raf.writeShortLE(blockAlign.toShort().toInt())
        raf.writeShortLE(bitsPerSample.toShort().toInt())
        raf.writeBytes("data")
        raf.writeIntLE(pcmDataSize.toInt())
    }
}

private fun RandomAccessFile.writeIntLE(value: Int) {
    write(value and 0xFF)
    write(value shr 8 and 0xFF)
    write(value shr 16 and 0xFF)
    write(value shr 24 and 0xFF)
}

private fun RandomAccessFile.writeShortLE(value: Int) {
    write(value and 0xFF)
    write(value shr 8 and 0xFF)
}
