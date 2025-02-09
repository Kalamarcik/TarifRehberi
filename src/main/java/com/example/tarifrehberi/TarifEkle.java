package com.example.tarifrehberi;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import java.sql.Connection;
import java.util.*;

public class TarifEkle {
    private final Database db;
    private final Connection conn;
    private Runnable onRecipeAddedCallback;

    public TarifEkle(Database db, Connection conn) {
        this.db = db;
        this.conn = conn;
    }

    public void showAddRecipeDialog() {
        Map<String, Map<String, Object>> ingredientMap = new HashMap<>();
        String defaultRecipeName = "";
        String defaultCategory = "Ana Yemek";
        int defaultPreparationTime = 0;
        String defaultInstructions = "";

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Yeni Tarif Ekle");

        Image image = new Image(getClass().getResourceAsStream("/icons/TarifEkle.png"));
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(40);
        imageView.setFitHeight(40);

        Label headerLabel = new Label("Yeni tarif eklemek için bilgileri girin:");
        headerLabel.setStyle("-fx-background-color: #7a1e9c; -fx-text-fill: white; -fx-padding: 10; -fx-font-size: 16px;");
        headerLabel.setAlignment(Pos.CENTER);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10);
        header.getChildren().addAll(headerLabel, spacer, imageView);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #7a1e9c; -fx-padding: 10;");
        header.setMaxWidth(Double.MAX_VALUE);

        dialog.getDialogPane().setHeader(header);
        dialog.getDialogPane().setStyle("-fx-background-color: #efe3f2;");

        TextField recipeNameField = new TextField(defaultRecipeName);
        ComboBox<String> categoryComboBox = new ComboBox<>();
        List<String> categories = db.getCategoriesFromRecipes(conn);
        categoryComboBox.getItems().addAll(categories);
        categoryComboBox.setValue(defaultCategory);

        TextField preparationTimeField = new TextField(String.valueOf(defaultPreparationTime));
        TextArea instructionsField = new TextArea(defaultInstructions);

        ListView<HBox> ingredientsListView = new ListView<>();
        ingredientsListView.setPrefHeight(150);



        TextField newIngredientField = new TextField();
        newIngredientField.setPromptText("Yeni malzeme ekle");

        ComboBox<String> existingIngredientsComboBox = new ComboBox<>();
        List<String> existingIngredients = db.getIngredientsFromDatabase(conn);
        existingIngredientsComboBox.getItems().addAll(existingIngredients);
        existingIngredientsComboBox.setPromptText("Mevcut malzeme seçin");

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
                    .anyMatch(hbox -> ((TextField) hbox.getChildren().get(0)).getText().equalsIgnoreCase(newIngredient));

            if (isDuplicate) {
                showToastInDialog("Bu malzeme zaten mevcut!", dialog);
                return;
            }

            try {
                float amount = Float.parseFloat(amountText);

                Map<String, Object> ingredientDetail = new HashMap<>();
                ingredientDetail.put("miktar", amount);
                ingredientDetail.put("birim", selectedUnit);
                ingredientMap.put(newIngredient, ingredientDetail);

                TextField addedIngredientNameField = new TextField(newIngredient);
                addedIngredientNameField.setEditable(false);
                TextField addedIngredientAmountField = new TextField(String.format("%.2f", amount));
                addedIngredientAmountField.setEditable(false);
                Label addedIngredientUnitLabel = new Label(selectedUnit);

                HBox hbox = new HBox(5, addedIngredientNameField, addedIngredientAmountField, addedIngredientUnitLabel);
                ingredientsListView.getItems().add(hbox);

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

                ingredientMap.remove(malzemeAdi);
                ingredientsListView.getItems().remove(selectedIngredient);
                showToastInDialog("Malzeme başarıyla silindi.", dialog);
            } else {
                showToastInDialog("Lütfen silmek için bir malzeme seçin.", dialog);
            }
        });

        HBox ingredientInputBox = new HBox(10, existingIngredientsComboBox, newIngredientField, newAmountField, newUnitComboBox, addIngredientButton, removeIngredientButton);

        VBox vbox = new VBox(10);
        vbox.getChildren().addAll(
                new Label("Tarif Adı:"), recipeNameField,
                new Label("Kategori:"), categoryComboBox,
                new Label("Hazırlama Süresi (dakika):"), preparationTimeField,
                new Label("Talimatlar:"), instructionsField,
                new Label("Malzemeler:"), ingredientsListView,
                new Label("Yeni Malzeme Ekle:"), ingredientInputBox
        );
        dialog.getDialogPane().setContent(vbox);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setText("İptal");
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Tarifi Ekle");
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!handleAddRecipe(dialog, recipeNameField, categoryComboBox, preparationTimeField, instructionsField, ingredientMap)) {
                event.consume(); // Eğer handleAddRecipe false dönerse, dialog kapanmayacak
            }
        });

        dialog.showAndWait();
    }

    public void setOnRecipeAddedCallback(Runnable callback) {
        this.onRecipeAddedCallback = callback;
    }

    private boolean handleAddRecipe(Dialog<ButtonType> dialog, TextField recipeNameField, ComboBox<String> categoryComboBox, TextField preparationTimeField, TextArea instructionsField, Map<String, Map<String, Object>> ingredientMap) {
        if (recipeNameField.getText().isEmpty() || categoryComboBox.getValue() == null || preparationTimeField.getText().isEmpty() || instructionsField.getText().isEmpty() || ingredientMap.isEmpty()) {
            showToastInDialog("Lütfen tüm alanları doldurun.", dialog);
            return false;
        }
        if (db.isDuplicateRecipe(conn, recipeNameField.getText())) {
            showToastInDialog("Tarif zaten mevcut. Lütfen farklı bir tarif adı girin.", dialog);
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

        String newInstructions = formatInstructions(instructionsField.getText());

        db.addRecipe(conn, newName, newCategory, newPreparationTime, newInstructions, ingredientMap);

        if (onRecipeAddedCallback != null) {
            onRecipeAddedCallback.run();
        }

        return true;
    }

    private void showToastInDialog(String message, Dialog<?> dialog) {
        Label toastLabel = new Label(message);
        toastLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8); " +
                "-fx-text-fill: white; " +
                "-fx-padding: 10px; " +
                "-fx-border-radius: 5px;");
        toastLabel.setAlignment(Pos.CENTER);
        toastLabel.setMaxWidth(Double.MAX_VALUE);

        VBox dialogContent = (VBox) dialog.getDialogPane().getContent();
        dialogContent.getChildren().add(toastLabel);

        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> dialogContent.getChildren().remove(toastLabel));
        pause.play();
    }

    private String formatInstructions(String instructions) {
        StringBuilder formattedInstructions = new StringBuilder();
        String[] lines = instructions.split("\\d+\\.");

        for (int i = 1; i < lines.length; i++) {
            formattedInstructions.append(i).append(".").append(lines[i].trim()).append("\n");
        }

        return formattedInstructions.toString().trim();
}

}