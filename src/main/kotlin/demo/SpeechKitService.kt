package demo

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.ByteArrayOutputStream
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

/**
 * Demo Yandex speech kit service
 */
class SpeechKitService {
    private val log = LoggerFactory.getLogger(javaClass)
    private val key = "86f093de-85c3-4f9d-83d5-00a67ff02c03"
    private val userId = UUID.randomUUID().toString().replace("-", "")
    private val topic = "queries"
    private val limitBytes = 1024L * 1024
    private val threadExecutor = Executors.newSingleThreadExecutor { r ->
        val result = Thread(r)
        result.isDaemon = true
        result
    }

    /**
     * Starts capturing audio from the default audio device for speech recognition.
     * This method is not blocking and will return shortly.
     *
     * Returns a [StoppableAudioReader] instance that can be used to stop the recording.
     * Recording lasts until it is stopped, or until maximum recording length (~30sec)
     * is reached.
     *
     * Note, that [onResult] or [onError] are called asynchronously from another thread
     * after this method returns.
     *
     * @param onResult function, that is called after successful speech recognition with
     * the highest confidence speech recognition variant
     * @param onError function that is called if something fails during speech capture
     * or speech recognition with the exception object
     */
    fun startCapturingAudioForRecognition(onResult: (String) -> Unit, onError: (Exception) -> Unit): StoppableAudioReader {
        log.info("Preparing audio capture")
        val audioFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                16000F, 16, 1, 2,
                16000F, false)

        val line = AudioSystem.getTargetDataLine(audioFormat)
        line.open(audioFormat)
        line.start()

        val url = URL("https://asr.yandex.net/asr_xml?uuid=${userId}&key=${key}&topic=${topic}")
        val connection = (url.openConnection() as HttpsURLConnection)
        connection.setRequestMethod("POST")
        connection.setChunkedStreamingMode(4096)
        connection.setRequestProperty("Content-Type", "audio/x-pcm;bit=16;rate=16000")
        connection.setRequestProperty("Transfer-Encoding", "chunked")
        connection.setDoOutput(true)
        connection.connect()
        val outputStream = connection.outputStream
        val stopFlag = AtomicBoolean(false)

        log.info("Starting capture thread")
        val future = threadExecutor.submit {
            try {
                try {
                    outputStream.use { outputStream ->
                        line.use { line ->
                            var bytesSent = 0L
                            val buffer = ByteArray(4096)
                            while (!stopFlag.get() && bytesSent < limitBytes) {
                                val bytesRead = line.read(buffer, 0, buffer.size)
                                val bytesLeft = limitBytes - bytesSent
                                val bytesToSend = if (bytesLeft >= bytesRead) bytesRead else bytesLeft.toInt()
                                outputStream.write(buffer, 0, bytesToSend)
                                bytesSent += bytesToSend
                            }
                            if (bytesSent >= limitBytes) {
                                log.info("Stopping audio capture because limit is reached")
                            }
                        }
                    }
                    connection.inputStream.use {
                        if (connection.responseCode != 200) {
                            throw RuntimeException("Response failed with code ${connection.responseCode}, message: ${connection.responseMessage}")
                        } else {
                            connection.inputStream.use { stream ->
                                val receivedXml =  stream.bufferedReader().use { reader ->
                                    reader.readText()
                                }
                                log.info("Received xml:\n${receivedXml}")
                                onResult(extractHighConfidenceResult(receivedXml))
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                log.error("Error in audio writing thread", e)
                onError(e)
            }
        }

        return object: StoppableAudioReader {
            override fun finish() {
                log.info("Stopping audio capture because finish called")
                stopFlag.set(true) // signal the thread to stop
                future.get() // wait for it to finish
            }
        }
    }

    private val objectMapper = XmlMapper()

    /**
     * xml example:
     *
     * <?xml version="1.0" encoding="utf-8"?>
     * <recognitionResults success="1">
     * <variant confidence="0.05">д 8 + d 6</variant>
     * <variant confidence="0">8 + d 6</variant>
     * <variant confidence="0">d 8 + d 6</variant>
     * <variant confidence="0">д 8 + д 6</variant>
     * <variant confidence="0">8 + д 6</variant>
     * </recognitionResults>
     */
    private fun extractHighConfidenceResult(xml: String): String {
        val parsedResults = objectMapper.readValue(xml, RecognitionResults::class.java)
        if (parsedResults.success == 0 || parsedResults.variant.isEmpty()) {
            throw RuntimeException("Failed to recognize audio")
        }
        return parsedResults.variant.first().text
    }

    /**
     * Plays provided text as audio via speech kit voice generation to the
     * system default audio device. This method is asynhronous and returns
     * immediately, while the audio starts playing as soon as it is generated
     * in a separate thread.
     *
     * @param text to play as an audio
     */
    fun playAudio(text: String) {
        threadExecutor.submit {
            try {
                val url = URL("https://tts.voicetech.yandex.net/generate" +
                        "?key=${key}" +
                        "&format=wav" +
                        "&lang=ru-RU" +
                        "&speaker=alyss")

                val body = ("text=" + URLEncoder.encode(text, "UTF-8")).toByteArray()

                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Length", body.size.toString())
                connection.doOutput = true
                connection.outputStream.use { stream ->
                    stream.write(body)
                }
                val inMemoryOutputStream = ByteArrayOutputStream()
                connection.connect()
                try {
                    if (connection.responseCode != 200) {
                        throw RuntimeException("Response failed with code ${connection.responseCode}, message: ${connection.responseMessage}")
                    }
                    connection.inputStream.use { stream ->
                        IOUtils.copy(stream, inMemoryOutputStream)
                    }
                } finally {
                    connection.disconnect()
                }
                val inMemoryInputStream = ByteArrayInputStream(inMemoryOutputStream.toByteArray())
                val audioStream = AudioSystem.getAudioInputStream(inMemoryInputStream)
                val clip = AudioSystem.getClip()
                try {
                    clip.open(audioStream)
                    clip.framePosition = 0
                    clip.addLineListener { event ->
                        if (event.type == LineEvent.Type.STOP) {
                            clip.close()
                            audioStream.close()
                            inMemoryInputStream.close()
                        }
                    }
                    clip.start()
                } catch (e: Exception) {
                    clip.close()
                    audioStream.close()
                    inMemoryInputStream.close()
                    throw RuntimeException(e)
                }
            } catch (e: Exception) {
                log.error("Error rendering audio", e)
            }
        }
    }
}

interface StoppableAudioReader {
    /**
     * Call this to stop audio recording for speech recognition. This method
     * blocks until the recording is actually stopped and result or error
     * is actually submitted to onResult or onError callback.
     */
    fun finish()
}

class RecognitionResults {
    var success: Int = 0

    @JacksonXmlElementWrapper(localName = "variant", useWrapping = false)
    var variant: Array<RecognitionVariant> = arrayOf()
}

class RecognitionVariant {
    var confidence: Double = 0.0
    @JacksonXmlText
    var text: String = ""
}