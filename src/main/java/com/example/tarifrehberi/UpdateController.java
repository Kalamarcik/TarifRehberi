package com.example.tarifrehberi;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.sql.Connection;
import java.util.*;

public class UpdateController {

    private final Connection conn;
    private final Database db;
    private Runnable onRecipeUpdatedCallback;


    public UpdateController(Connection conn, Database db) {
        this.conn = conn;
        this.db = db;
    }

    private Dialog<ButtonType> createDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Tarifi Güncelle");
        dialog.getDialogPane().setStyle("-fx-background-color: #efe3f2;");
        return dialog;
    }

    private HBox createHeader() {
        Image image = new Image(getClass().getResourceAsStream("/icons/rewrite.png"));
        ImageView imageView2 = new ImageView(image);
        imageView2.setFitWidth(40);
        imageView2.setFitHeight(40);

        Label headerLabel = new Label("Tarifi güncellemek için bilgileri girin:");
        headerLabel.setStyle("-fx-background-color: #7a1e9c; -fx-text-fill: white; -fx-padding: 10; -fx-font-size: 16px;");
        headerLabel.setAlignment(Pos.CENTER);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10);
        header.getChildren().addAll(headerLabel, spacer, imageView2);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #7a1e9c; -fx-padding: 10;");
        header.setMaxWidth(Double.MAX_VALUE);

        return header;
    }

    private ListView<HBox> setupIngredients(int tarifID) {
        ListView<HBox> ingredientsListView = new ListView<>();
        ingredientsListView.setPrefHeight(150);

        List<String> ingredientDetails = db.getIngredientDetailsForRecipe(conn, tarifID);
        for (String detail : ingredientDetails) {
            String[] parts = detail.split(" - ");
            String name = parts[0];
            String[] amountParts = parts[1].split(" ");
            String amount = amountParts[0];
            String unit = amountParts[1];

            TextField ingredientNameField = new TextField(name);
            ingredientNameField.setEditable(false);
            TextField ingredientAmountField = new TextField(amount);
            Label ingredientUnitLabel = new Label(unit);

            HBox hbox = new HBox(5, ingredientNameField, ingredientAmountField, ingredientUnitLabel);
            ingredientsListView.getItems().add(hbox);
        }
        return ingredientsListView;
    }

    private HBox createIngredientInputBox(ListView<HBox> ingredientsListView, int tarifID, Dialog<ButtonType> dialog) {
        TextField newIngredientField = new TextField();
        newIngredientField.setPromptText("Yeni malzeme ekle");

        // Mevcut malzemeleri göstermek için ComboBox
        ComboBox<String> existingIngredientsComboBox = new ComboBox<>();
        List<String> existingIngredients = db.getIngredientsFromDatabase(conn); // Veritabanından mevcut malzemeleri çekin
        existingIngredientsComboBox.getItems().addAll(existingIngredients);
        existingIngredientsComboBox.setPromptText("Mevcut malzeme seçin");

        // malzeme seçiildiğinde TextFielda yazdır
        existingIngredientsComboBox.setOnAction(e -> {
            String selectedIngredient = existingIngredientsComboBox.getValue();
            newIngredientField.setText(selectedIngredient);
        });

        TextField newAmountField = new TextField();
        newAmountField.setPromptText("Miktar");

        ComboBox<String> newUnitComboBox = new ComboBox<>();
        newUnitComboBox.getItems().addAll("adet", "kilogram", "litre","çay kaşığı","tatlı kaşığı","çorba kaşığı");
        newUnitComboBox.setPromptText("Birim seçin");

        Button addIngredientButton = new Button("Malzeme Ekle");
        addIngredientButton.setOnAction(e -> {
            String newIngredient = newIngredientField.getText().trim();
            String amountText = newAmountField.getText().trim().replace(',', '.');
            String selectedUnit = newUnitComboBox.getValue();

            if (newIngredient.isEmpty() || amountText.isEmpty() || selectedUnit == null) {
                showToastInDialog("Lütfen tüm alanları doldurun.", dialog);
                return;
            }

            boolean isDuplicate = ingredientsListView.getItems().stream()
                    .anyMatch(hbox -> ((TextField) hbox.getChildren().get(0)).getText()
                            .equalsIgnoreCase(newIngredient));

            if (isDuplicate) {
                showToastInDialog("Bu malzeme zaten mevcut!", dialog);
                return;
            }

            try {
                double amount = Double.parseDouble(amountText);

                // Malzemeyi oluştur veya ID'sini al
                int malzemeID = db.getOrCreateIngredient(conn, newIngredient, selectedUnit);
                if (malzemeID == -1) {
                    showToastInDialog("Malzeme eklenemedi.", dialog);
                    return;
                }

                // Tarif ve malzeme ilişkisini ekle
                db.addIngredientToRecipe(conn, tarifID, malzemeID, amount);

                // UI'ya ekliyoruz
                TextField addedIngredientNameField = new TextField(newIngredient);
                addedIngredientNameField.setEditable(false);
                TextField addedIngredientAmountField = new TextField(String.format("%.2f", amount));
                addedIngredientAmountField.setEditable(false);
                Label addedIngredientUnitLabel = new Label(selectedUnit);

                HBox hbox = new HBox(5, addedIngredientNameField, addedIngredientAmountField, addedIngredientUnitLabel);
                ingredientsListView.getItems().add(hbox);

                // Alanları temizliyoruz
                newIngredientField.clear();
                newAmountField.clear();
                newUnitComboBox.setValue(null);
                existingIngredientsComboBox.setValue(null);

                showToastInDialog("Malzeme başarıyla eklendi.", dialog);
            } catch (NumberFormatException ex) {
                showToastInDialog("Geçersiz miktar. Lütfen sayısal bir değer girin.", dialog);
            }
        });

        Button removeIngredientButton = new Button("Seçili Malzemeyi Sil");
        removeIngredientButton.setOnAction(e -> {
            HBox selectedIngredient = ingredientsListView.getSelectionModel().getSelectedItem();
            if (selectedIngredient != null) {
                TextField nameField = (TextField) selectedIngredient.getChildren().get(0);
                String malzemeAdi = nameField.getText();

                db.deleteIngredientFromRecipe(conn, tarifID, malzemeAdi);
                ingredientsListView.getItems().remove(selectedIngredient);
                showToastInDialog("Malzeme başarıyla silindi.", dialog);
            } else {
                showToastInDialog("Lütfen silmek için bir malzeme seçin.", dialog);
            }
        });

        return new HBox(10, existingIngredientsComboBox, newIngredientField, newAmountField, newUnitComboBox, addIngredientButton, removeIngredientButton);
    }
    public void setOnRecipeUpdatedCallback(Runnable callback) {
        this.onRecipeUpdatedCallback = callback;
    }
    public void showUpdateDialog(int tarifID, String recipeName, String category, int preparationTime, String instructions) {

        Dialog<ButtonType> dialog = createDialog();

        HBox header = createHeader();
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setHeader(header);
        dialogPane.setStyle("-fx-background-color: #efe3f2;");

        TextField recipeNameField = new TextField(recipeName);
        ComboBox<String> categoryComboBox = new ComboBox<>(FXCollections.observableArrayList(db.getCategoriesFromRecipes(conn)));
        categoryComboBox.setValue(category);

        TextField preparationTimeField = new TextField(String.valueOf(preparationTime));
        TextArea instructionsField = new TextArea(formatInstructions(instructions));

        ListView<HBox> ingredientsListView = setupIngredients(tarifID);
        HBox ingredientInputBox = createIngredientInputBox(ingredientsListView, tarifID, dialog);

        VBox centerContent = new VBox(10,
                new Label("Tarif Adı:"), recipeNameField,
                new Label("Kategori:"), categoryComboBox,
                new Label("Hazırlama Süresi (dakika):"), preparationTimeField,
                new Label("Talimatlar:"), instructionsField,
                new Label("Malzemeler:"), ingredientsListView,
                new Label("Yeni Malzeme Ekle:"), ingredientInputBox);
        centerContent.setStyle("-fx-padding: 10;");
        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(centerContent);

        dialogPane.setContent(borderPane);
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setText("İptal");
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Tarifi Güncelle");
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!handleUpdateRecipe(dialog, tarifID, recipeNameField, categoryComboBox, preparationTimeField, instructionsField, ingredientsListView)) {
                event.consume(); // Eğer handleUpdateRecipe false dönerse, dialog kapanmayacak
            }
        });

        dialog.showAndWait();
    }

    private boolean handleUpdateRecipe(Dialog<ButtonType> dialog, int tarifID, TextField recipeNameField,
                                       ComboBox<String> categoryComboBox, TextField preparationTimeField,
                                       TextArea instructionsField, ListView<HBox> ingredientsListView) {
        if (recipeNameField.getText().isEmpty() || categoryComboBox.getValue() == null ||
                preparationTimeField.getText().isEmpty() || instructionsField.getText().isEmpty() ||
                ingredientsListView.getItems().isEmpty()) {
            showToastInDialog("Lütfen tüm alanları doldurun.", dialog);
            return false;
        }

        String newName = recipeNameField.getText();
        String newCategory = categoryComboBox.getValue();
        int newPreparationTime;
        try {
            newPreparationTime = Integer.parseInt(preparationTimeField.getText());
        } catch (NumberFormatException e) {
            showToastInDialog("Geçersiz hazırlama süresi. Lütfen sayısal bir değer girin.", dialog);
            return false;
        }
        String newInstructions = instructionsField.getText();

        List<Map<String, Object>> updatedIngredients = new ArrayList<>();
        for (HBox hbox : ingredientsListView.getItems()) {
            TextField nameField = (TextField) hbox.getChildren().get(0);
            TextField amountField = (TextField) hbox.getChildren().get(1);
            Label unitLabel = (Label) hbox.getChildren().get(2);

            Map<String, Object> ingredient = new HashMap<>();
            ingredient.put("name", nameField.getText());
            try {
                ingredient.put("amount", Double.parseDouble(amountField.getText().replace(',', '.')));
            } catch (NumberFormatException e) {
                showToastInDialog("Geçersiz miktar: " + nameField.getText(), dialog);
                return false;
            }
            ingredient.put("unit", unitLabel.getText());
            updatedIngredients.add(ingredient);
        }

        db.updateRecipe(conn, tarifID, newName, newCategory, newPreparationTime, newInstructions);
        db.updateRecipeIngredients(conn, tarifID, updatedIngredients);

        showToastInDialog("Tarif başarıyla güncellendi.", dialog);

        if (onRecipeUpdatedCallback != null) {
            onRecipeUpdatedCallback.run();
        }

        return true; // Tarif güncellendi, yani dialog kapanabilir
    }
    private String formatInstructions(String instructions) {
        StringBuilder formattedInstructions = new StringBuilder();
        String[] lines = instructions.split("\\d+\\.");

        for (int i = 1; i < lines.length; i++) {
            formattedInstructions.append(i).append(".").append(lines[i].trim()).append("\n");
        }

        return formattedInstructions.toString().trim();
    }

    // Toast mesajını diyalog içinde sol alt köşede gösteren metod (3 saniye)
    private void showToastInDialog(String message, Dialog<ButtonType> dialog) {
        Label toastLabel = new Label(message);
        toastLabel.setStyle("-fx-text-fill: green; -fx-padding: 10px;");
        toastLabel.setAlignment(Pos.CENTER_LEFT);

        BorderPane dialogPaneContent = (BorderPane) dialog.getDialogPane().getContent();
        dialogPaneContent.setBottom(toastLabel);
        BorderPane.setAlignment(toastLabel, Pos.BOTTOM_LEFT);

        // 3 saniye sonra mesajı gizlemek içn
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(event -> dialogPaneContent.setBottom(null));
        pause.play();
}

}