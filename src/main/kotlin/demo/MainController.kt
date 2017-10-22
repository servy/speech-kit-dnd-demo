package demo

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextArea
import org.slf4j.LoggerFactory

class MainController {
    @FXML
    private lateinit var recordButton: Button
    @FXML
    private lateinit var resultArea: TextArea

    private val speechKitIntegration = SpeechKitService()
    private val dndCalculator = DndCalculator()
    private var currentRecording: StoppableAudioReader? = null

    private val log = LoggerFactory.getLogger(javaClass)

    @FXML
    private fun buttonClicked() {
        val currentRecording = this.currentRecording
        if (currentRecording == null) {
            // not recording - start new recording
            this.currentRecording = speechKitIntegration.startCapturingAudioForRecognition(
                    { result -> Platform.runLater { onSpeechRecognition(result) } },
                    { error -> Platform.runLater { onSpeechRecognitionError(error) } })
            recordButton.text = "Stop"
        } else {
            // recording - stop the recording
            currentRecording.finish()
        }
    }

    private fun onSpeechRecognition(result: String) {
        try {
            resultArea.text += "Speech recognition result: ${result}\n"
            currentRecording = null
            recordButton.text = "Record"

            val dndInfo = dndCalculator.calculate(result)

            resultArea.text += "Calculated info: ${dndInfo}\n"
            val formula = dndInfo.text.replace("-", " минус ")
                    .replace("+", " плюс ")
            val text = "Результат для ${formula}: " +
                    "минимум ${dndInfo.min}, " +
                    "максимум ${dndInfo.max}, " +
                    "в среднем ${dndInfo.average}. " +
                    "У меня выпало ${dndInfo.generated}!"
            speechKitIntegration.playAudio(text)
            resultArea.text += "Saying: ${text}\n"
        } catch (e: Exception) {
            log.error("Error in speech recognition result processing", e)
            resultArea.text += "Error after getting speech recognition result: ${e.javaClass.name}: ${e.message}\n"
            sayErrorHappened()
        }
    }

    private fun onSpeechRecognitionError(error: Exception) {
        resultArea.text += "Got error: ${error.javaClass.name}: ${error.message}\n"
        currentRecording = null
        recordButton.text = "Record"
        sayErrorHappened()
    }

    private fun sayErrorHappened() {
        speechKitIntegration.playAudio("Ой, что-то пошло не так.")
    }
}
