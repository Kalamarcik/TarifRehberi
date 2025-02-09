module com.example.tarifrehberi {
    requires javafx.fxml;
    requires java.sql;
    requires org.controlsfx.controls;


    opens com.example.tarifrehberi to javafx.fxml;
    exports com.example.tarifrehberi;
}