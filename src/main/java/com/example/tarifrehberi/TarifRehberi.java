package com.example.tarifrehberi;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Objects;

public class TarifRehberi extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(TarifRehberi.class.getResource("AnaSayfa.fxml"));
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root, 1280, 720);
        stage.setTitle("Tarif Rehberi");
        Image image = new Image(Objects.requireNonNull(TarifRehberi.class.getResource("/icons/CookingBook.png")).toExternalForm());
        stage.getIcons().add(image);
        stage.setScene(scene);
        //stage.setFullScreen(true);
        stage.show();
    }
    public static void main(String[] args) {
        launch();}
}