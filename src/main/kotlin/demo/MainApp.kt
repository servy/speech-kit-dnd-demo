package demo

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage

class MainApp : Application() {
    override fun start(primaryStage: Stage) {
        val loader = FXMLLoader()
        loader.location = MainApp::class.java.getResource("view/main.fxml")
        val rootLayout = loader.load<BorderPane>()

        val scene = Scene(rootLayout)
        primaryStage.scene = scene
        primaryStage.show()
    }
}

fun main(args: Array<String>) {
    Application.launch(MainApp::class.java, *args)
}