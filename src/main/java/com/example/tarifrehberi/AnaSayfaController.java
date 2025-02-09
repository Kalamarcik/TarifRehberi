package com.example.tarifrehberi;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Stage;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class AnaSayfaController implements Initializable{

    @FXML private TextField arama;
    @FXML private GridPane recipeGrid;
    @FXML private  AnchorPane anchor;
    @FXML private ComboBox<String> sortComboBox;
    @FXML private VBox filterVBox;
    @FXML private TreeView<Object> treeView;
    @FXML public Button treeviewButton;
    @FXML private Button malzemeAramaButonu;
    @FXML TextField minCostField;
    @FXML TextField maxCostField;
    private VBox ingredientSearchVBox;
    private Stage stage;
    int anchorYukeskligi = 0;
    private Connection conn;
    private UpdateController updateController;
    private TarifEkle tarifEkle;
    private MaterialDialog materialDialog;
    private FlowPane ingredientFlowPane;

    Database db = new Database();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        connectToDatabase();
        updateController = new UpdateController(conn, db);
        tarifEkle = new TarifEkle(db, conn);
        materialDialog = new MaterialDialog();

        createIngredientSearchVBox();

        loadRecipes("");

        if (arama != null) {
            arama.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue) {
                    arama.setFocusTraversable(false);
                }
            });
        }

        arama.textProperty().addListener((observable, oldValue, newValue) -> loadRecipes(newValue));
        sortComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> loadRecipes(arama.getText()));

        setupTreeViewListeners();
        setupCostFieldListeners();


        tarifEkle.setOnRecipeAddedCallback(() -> {
            showToast("Yeni tarif başarıyla eklendi.");
            loadRecipes(arama.getText());
        });

        updateController.setOnRecipeUpdatedCallback(() -> {
            showToast("Tarif başarıyla güncellendi.");
            loadRecipes(arama.getText());
        });

        materialDialog.setOnMaterialAddedCallback(() -> {
            showToast("Yeni malzeme başarıyla eklendi.");
            updateIngredientSearchVBox();
        });


    }


    private void loadRecipes(String search) {
        int satir=0;
        String sort = sortComboBox.getSelectionModel().getSelectedItem();
        String categoryFilter = getSelectedCategories();
        int ingredientCount = getSelectedIngredientCount();
        double[] costRange = getCostRange();
        List<String> conditions = new ArrayList<>();
        if (costRange != null) {
            if (costRange[0] != -1 && costRange[1] != -1) {
                conditions.add("ToplamMaliyet BETWEEN " + costRange[0] + " AND " + costRange[1]);
            } else if (costRange[0] != -1) {
                conditions.add("ToplamMaliyet >= " + costRange[0]);
            } else if (costRange[1] != -1) {
                conditions.add("ToplamMaliyet <= " + costRange[1]);
            }
        }

        if (categoryFilter == null || categoryFilter.isEmpty()) {
            recipeGrid.getChildren().clear();
            return;
        }

        try {
            Statement stmt = conn.createStatement();
            StringBuilder query = new StringBuilder(
                    "SELECT t.*, " +
                            "(SELECT SUM(tm.MalzemeMiktar * m.BirimFiyat) " +
                            "FROM TarifMalzeme tm " +
                            "JOIN Malzemeler m ON tm.MalzemeID = m.MalzemeID " +
                            "WHERE tm.TarifID = t.TarifID) AS ToplamMaliyet, " +
                            "(SELECT COUNT(*) FROM TarifMalzeme tm WHERE tm.TarifID = t.TarifID) AS MalzemeSayisi " +
                            "FROM Tarifler t"
            );


            if (!search.isEmpty()) {
                conditions.add("t.TarifAdi LIKE '%" + search + "%'");
            }

            if (categoryFilter != null && !categoryFilter.isEmpty()) {
                conditions.add("t.Kategori IN ('" + categoryFilter.replace(", ", "','") + "')");
            }

            if (ingredientCount > 0) {
                if (ingredientCount == Integer.MAX_VALUE) {
                    conditions.add("MalzemeSayisi >= 9");
                } else {
                    conditions.add("MalzemeSayisi = " + ingredientCount);
                }
            }

            if (costRange != null) {
                conditions.add("ToplamMaliyet BETWEEN " + costRange[0] + " AND " + costRange[1]);
            }

            if (!conditions.isEmpty()) {
                query.append(" WHERE ").append(String.join(" AND ", conditions));
            }


            if (sort != null) {
                switch (sort) {
                    case "-" -> query.append(" ORDER BY TarifID ASC");
                    case "Hazırlama Süresi (Azalan)" -> query.append(" ORDER BY HazirlamaSuresi DESC");
                    case "Hazırlama Şuresi (Artan)" -> query.append(" ORDER BY HazirlamaSuresi ASC");
                    case "Maliyet (Artan)" -> query.append(" ORDER BY ToplamMaliyet ASC");
                    case "Maliyet (Azalan)" -> query.append(" ORDER BY ToplamMaliyet DESC");
                }
            }


            //   recipeGrid.getRowConstraints().clear();
            ResultSet rs = stmt.executeQuery(query.toString());
            recipeGrid.getChildren().clear();
            int row = 0;
            int column = 0;

            while (rs.next()) {
                satir++;
                String recipeName = rs.getString("TarifAdi");
                String category = rs.getString("Kategori");
                int preparationTime = rs.getInt("HazirlamaSuresi");
                String Tarif = rs.getString("Talimatlar");
                int tarifID = rs.getInt("TarifID");
                double toplamMaliyet = rs.getDouble("ToplamMaliyet");

                boolean tarifYapilabilir = true;
                double eksikMaliyet = 0.0;

                String malzemeKontrolQuery = "SELECT tm.MalzemeMiktar, m.ToplamMiktar, m.BirimFiyat " +
                        "FROM TarifMalzeme tm " +
                        "JOIN Malzemeler m ON tm.MalzemeID = m.MalzemeID " +
                        "WHERE tm.TarifID = ?";

                PreparedStatement pstmt = conn.prepareStatement(malzemeKontrolQuery);
                pstmt.setInt(1, tarifID);
                ResultSet malzemeRs = pstmt.executeQuery();

                while (malzemeRs.next()) {
                    double gerekliMiktar = malzemeRs.getDouble("MalzemeMiktar");
                    double mevcutMiktar = malzemeRs.getDouble("ToplamMiktar");
                    double birimFiyat = malzemeRs.getDouble("BirimFiyat");

                    if (gerekliMiktar > mevcutMiktar) {
                        tarifYapilabilir = false;
                        eksikMaliyet += (gerekliMiktar - mevcutMiktar) * birimFiyat;
                    }
                }


                Label nameLabel = new Label(recipeName);
                nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
                Label categoryLabel = new Label(category);
                Label timeLabel = new Label(preparationTime + " dakika");
                Label costLabel;

                if (tarifYapilabilir) {
                    costLabel = new Label(String.format("Maliyet: %.2f ₺", toplamMaliyet));
                    costLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #006400;");
                } else {
                    costLabel = new Label(String.format("Maliyet: %.2f₺   |||   Eksik Maliyet: %.2f₺", toplamMaliyet, eksikMaliyet));
                    costLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #FF0000;");

                }

                VBox recipeBox = new VBox(nameLabel, categoryLabel, timeLabel,costLabel);
                for (int i = 0; i < (int)Math.ceil((double)satir / 3); i++){

                    RowConstraints rowConstraints = new RowConstraints();
                    rowConstraints.setMinHeight(150.0);
                    rowConstraints.setPrefHeight(150.0);
                    recipeGrid.getRowConstraints().add(rowConstraints);
                    anchor.setPrefHeight(150*(int)Math.ceil((double)satir / 3));
                    anchorYukeskligi = 150*(int)Math.ceil((double)satir / 3);
                }

                HBox buttonBox = new HBox(30);
                Button deleteButton = new Button();
                deleteButton.setStyle("-fx-background-color:transparent;");
                ImageView imageView = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/delete.png"))));
                imageView.setFitHeight(40);
                imageView.setFitWidth(40);
                deleteButton.setMaxWidth(30);
                deleteButton.setMaxHeight(30);
                deleteButton.setGraphic(imageView);
                deleteButton.setPadding(new Insets(20, 0, 0, 0));

                Button updateButton = new Button();
                updateButton.setStyle("-fx-background-color:transparent;");
                ImageView imageView0 = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/rewrite.png"))));
                imageView0.setFitHeight(40);
                imageView0.setFitWidth(40);
                updateButton.setMaxWidth(30);
                updateButton.setMaxHeight(30);
                updateButton.setGraphic(imageView0);
                updateButton.setPadding(new Insets(25, 0, 0, 0));

                deleteButton.setOnAction(event -> { //delete butonuna tıklandıgında sil
                    db.deleteRecipe(conn, tarifID);
                    loadRecipes(arama.getText());
                });

                updateButton.setOnAction(event -> {
                    // Butona basıldığında dialoğu göster
                    updateController.showUpdateDialog(tarifID, recipeName, category, preparationTime, Tarif);
                });

                buttonBox.getChildren().addAll(updateButton, deleteButton);
                recipeBox.getChildren().add(buttonBox);
                recipeGrid.add(recipeBox, column, row);

                recipeBox.setOnMouseClicked(event -> displayRecipeDetails(tarifID,recipeName, category, preparationTime, Tarif)); //tarife tıklandıgında detay cıksın
                column++;
                if (column == 3) {
                    column = 0;
                    row++;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void displayRecipeDetails(int tarifID, String recipeName, String category, int preparationTime, String instructions) {
        UpdateController updateController = new UpdateController(conn, db);

        recipeGrid.setVisible(false);

        AnchorPane detailPane = new AnchorPane();
        detailPane.setStyle("-fx-background-color: #FFFFFF;");
        detailPane.setPadding(new Insets(20));

        Image backgroundImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/recipebackground.jpg")));
        ImageView backgroundImageView = new ImageView(backgroundImage);
        backgroundImageView.setFitWidth(1000);
        backgroundImageView.setFitHeight(615);
        backgroundImageView.setOpacity(0.15);
        detailPane.getChildren().add(backgroundImageView);

        Label nameLabel = new Label(recipeName);
        nameLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        Label categoryLabel = new Label("Kategori: " + category);
        categoryLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label timeLabel = new Label("Hazırlama Süresi: " + preparationTime + " dakika");
        timeLabel.setStyle("-fx-font-size: 16px");

        double toplamMaliyet = 0.0;
        double eksikMaliyet = 0.0;
        boolean tarifYapilabilir = true;

        try {
            String malzemeKontrolQuery = "SELECT tm.MalzemeMiktar, m.ToplamMiktar, m.BirimFiyat " +
                    "FROM TarifMalzeme tm " +
                    "JOIN Malzemeler m ON tm.MalzemeID = m.MalzemeID " +
                    "WHERE tm.TarifID = ?";

            PreparedStatement pstmt = conn.prepareStatement(malzemeKontrolQuery);
            pstmt.setInt(1, tarifID);
            ResultSet malzemeRs = pstmt.executeQuery();

            while (malzemeRs.next()) {
                double gerekliMiktar = malzemeRs.getDouble("MalzemeMiktar");
                double mevcutMiktar = malzemeRs.getDouble("ToplamMiktar");
                double birimFiyat = malzemeRs.getDouble("BirimFiyat");

                toplamMaliyet += gerekliMiktar * birimFiyat;

                if (gerekliMiktar > mevcutMiktar) {
                    tarifYapilabilir = false;
                    eksikMaliyet += (gerekliMiktar - mevcutMiktar) * birimFiyat;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Label costLabel = new Label(String.format("Toplam Maliyet: %.2f ₺", toplamMaliyet));
        costLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label missingCostLabel = new Label();
        if (!tarifYapilabilir) {
            missingCostLabel.setText(String.format("Eksik Maliyet: %.2f ₺", eksikMaliyet));
            missingCostLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FF0000;");
        }

        Label instructionsLabel = new Label("Talimatlar");
        instructionsLabel.setStyle("-fx-font-size: 16px;-fx-font-weight: bold;");
        instructionsLabel.setPadding(new Insets(20, 0, 0, 140));
        Separator separator = new Separator();
        separator.setPrefWidth(500);

        Label instructionsLabel0 = new Label(formatInstructions(instructions));
        instructionsLabel0.setStyle("-fx-font-size: 14px;-fx-font-weight: bold;");
        instructionsLabel0.setWrapText(true);
        instructionsLabel0.setMaxWidth(500);

        VBox leftVBox = new VBox(10);
        leftVBox.setPadding(new Insets(20));
        leftVBox.setMaxWidth(500);
        leftVBox.getChildren().addAll(nameLabel, categoryLabel, timeLabel, costLabel, missingCostLabel, instructionsLabel, separator, instructionsLabel0);
        VBox rightVBox = new VBox(10);
        rightVBox.setPadding(new Insets(20));
        rightVBox.setAlignment(Pos.TOP_LEFT);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button editButton = new Button();
        editButton.setStyle("-fx-background-color:transparent;");
        ImageView imageView0 = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/rewrite.png"))));
        imageView0.setFitHeight(40);
        imageView0.setFitWidth(40);
        editButton.setMaxWidth(30);
        editButton.setMaxHeight(30);
        editButton.setGraphic(imageView0);

        Button deleteButton = new Button();
        deleteButton.setStyle("-fx-background-color:transparent;");
        ImageView imageView1 = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/delete.png"))));
        imageView1.setFitHeight(40);
        imageView1.setFitWidth(40);
        deleteButton.setMaxWidth(30);
        deleteButton.setMaxHeight(30);
        deleteButton.setGraphic(imageView1);

        Button homeButton = new Button();
        homeButton.setStyle("-fx-background-color:transparent;");
        ImageView imageView2 = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/home.png"))));
        imageView2.setFitHeight(40);
        imageView2.setFitWidth(40);
        homeButton.setMaxWidth(40);
        homeButton.setMaxHeight(40);
        homeButton.setGraphic(imageView2);

        buttonBox.getChildren().addAll(editButton, deleteButton, homeButton);
        leftVBox.getChildren().add(buttonBox);

        Label ingredientsTitle = new Label("Malzemeler");
        ingredientsTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        rightVBox.getChildren().add(ingredientsTitle);

        try {
            String ingredientsQuery = "SELECT m.MalzemeAdi, tm.MalzemeMiktar, m.MalzemeBirim " +
                    "FROM TarifMalzeme tm " +
                    "JOIN Malzemeler m ON tm.MalzemeID = m.MalzemeID " +
                    "WHERE tm.TarifID = ?";
            PreparedStatement pstmt = conn.prepareStatement(ingredientsQuery);
            pstmt.setInt(1, tarifID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String malzemeAdi = rs.getString("MalzemeAdi");
                double miktar = rs.getDouble("MalzemeMiktar");
                String birim = rs.getString("MalzemeBirim");

                String formattedMiktar;
                if (miktar == (int) miktar) {
                    formattedMiktar = String.format("%d", (int) miktar);
                } else {
                    formattedMiktar = String.format("%.2f", miktar);
                }

                Label ingredientLabel = new Label(String.format("• %s: %s %s", malzemeAdi, formattedMiktar, birim));
                ingredientLabel.setStyle("-fx-font-size: 14px;");
                rightVBox.getChildren().add(ingredientLabel);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        HBox contentBox = new HBox(20);
        contentBox.getChildren().addAll(leftVBox, rightVBox);

        homeButton.setOnAction(event -> { //Bu butona tıklandığında detayları gösteren detailPane kaldırılıyor
            // ve tariflerin listesi yeniden yüklenir.
            // Ayrıca diğer arayüz bileşenleri görünür hale gelir.
            anchor.setPrefHeight(anchorYukeskligi);
            anchor.setMinHeight(anchorYukeskligi);
            toggleUIElements(true);
            malzemeAramaButonu.setVisible(true);


            anchor.getChildren().remove(detailPane);
            loadRecipes(arama.getText());
            recipeGrid.setVisible(true);
        });


        editButton.setOnAction(event -> {
            // UpdateController'daki showUpdateDialog metodunu çağır
            updateController.showUpdateDialog(tarifID, recipeName, category, preparationTime, instructions);
            loadRecipes(arama.getText());

            // Detay görünümünden çıkış ve listeyi tekrar yüklemek için:
            anchor.setPrefHeight(anchorYukeskligi);
            anchor.setMinHeight(anchorYukeskligi);
            toggleUIElements(true);
            malzemeAramaButonu.setVisible(true);
            anchor.getChildren().remove(detailPane);
            recipeGrid.setVisible(true);
        });

        deleteButton.setOnAction(event -> {
            // tarif veritabanından silinir ve tariflerin listesi yeniden yüklenir.
            db.deleteRecipe(conn, tarifID);
            loadRecipes(arama.getText());
            anchor.setPrefHeight(anchorYukeskligi);
            anchor.setMinHeight(anchorYukeskligi);
            toggleUIElements(true);
            malzemeAramaButonu.setVisible(true);
            anchor.getChildren().remove(detailPane);
            recipeGrid.setVisible(true);
        });


        VBox mainVBox = new VBox(20);
        mainVBox.getChildren().addAll(contentBox, buttonBox);
        detailPane.getChildren().add(mainVBox);

        AnchorPane.setTopAnchor(mainVBox, 0.0);
        AnchorPane.setRightAnchor(mainVBox, 0.0);
        AnchorPane.setBottomAnchor(mainVBox, 0.0);
        AnchorPane.setLeftAnchor(mainVBox, 0.0);

        anchor.setPrefHeight(615);
        anchor.setMinHeight(615);
        anchor.setMaxHeight(615);
        toggleUIElements(false);
        malzemeAramaButonu.setVisible(false);
        anchor.getChildren().add(detailPane);
    }


    @FXML
    private void handleTreeViewButtonAction(ActionEvent event) {
        filterVBox.setVisible(!filterVBox.isVisible());
    }

    @FXML
    private void handleAddRecipe() {
        tarifEkle.showAddRecipeDialog();
    }

    @FXML
    private void handleAddMaterial() {
        materialDialog.showAddMaterialDialog();

    }


    private String formatInstructions(String instructions) {
        StringBuilder formattedInstructions = new StringBuilder();
        String[] lines = instructions.split("\\d+\\.");

        for (int i = 1; i < lines.length; i++) {
            formattedInstructions.append(i).append(".").append(lines[i].trim()).append("\n");
        }

        return formattedInstructions.toString().trim();
    }

    private void connectToDatabase() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:./identifier.sqlite");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void showToast(String message) {
        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        Label label = new Label(message);
        label.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8); " +
                "-fx-text-fill: white; " +
                "-fx-padding: 10px; " +
                "-fx-border-radius: 5px; ");

        popup.getContent().add(label);

        stage = (Stage) anchor.getScene().getWindow();

        popup.setOnShown(e -> {
            popup.setX(stage.getX() + stage.getWidth()/2 - popup.getWidth()/2);
            popup.setY(stage.getY() + stage.getHeight() - 50);
        });

        Platform.runLater(() -> {
            popup.show(stage);

            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Platform.runLater(popup::hide);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }
    @FXML
    private void handleIngredientSearchButtonAction(ActionEvent event) {
        if (ingredientSearchVBox == null) {
            createIngredientSearchVBox();
        }
        ingredientSearchVBox.setVisible(!ingredientSearchVBox.isVisible());
    }

    private void createIngredientSearchVBox() {
        ingredientSearchVBox = new VBox(10);
        ingredientSearchVBox.setStyle("-fx-background-color: #efe3f2; -fx-padding: 10; -fx-border-color: #5A4FB8; -fx-border-width: 10;");
        ingredientSearchVBox.setVisible(false);

        Label titleLabel = new Label("Malzeme Seçimi");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(300);

        ingredientFlowPane = new FlowPane();
        ingredientFlowPane.setHgap(10);
        ingredientFlowPane.setVgap(10);
        ingredientFlowPane.setPrefWrapLength(200);
        ingredientFlowPane.setStyle("-fx-background-color: #efe3f2");
        updateIngredientSearchVBox();
        scrollPane.setContent(ingredientFlowPane);

        List<String> ingredients = db.getIngredientsFromDatabase(conn);
        for (String ingredient : ingredients) {
            CheckBox checkBox = new CheckBox(ingredient);
            ingredientFlowPane.getChildren().add(checkBox);
        }

        scrollPane.setContent(ingredientFlowPane);

        HBox buttonBox = new HBox(10);
        Button searchButton = new Button("Tarifleri Ara");
        searchButton.setOnAction(e -> searchRecipesByIngredients());

        Button cancelButton = new Button("İptal Et");
        cancelButton.setOnAction(e -> {
            ingredientSearchVBox.setVisible(false);
            loadRecipes(arama.getText());
            toggleUIElements(true);
        });

        buttonBox.getChildren().addAll(searchButton, cancelButton);

        ingredientSearchVBox.getChildren().addAll(titleLabel, scrollPane, buttonBox);

        AnchorPane.setTopAnchor(ingredientSearchVBox, 0.0);
        AnchorPane.setRightAnchor(ingredientSearchVBox, 0.0);
        ingredientSearchVBox.setMaxHeight(400);
        ingredientSearchVBox.setMaxWidth(250);

        anchor.getChildren().add(ingredientSearchVBox);
    }

    private void updateIngredientSearchVBox() {
        List<String> ingredients = db.getIngredientsFromDatabase(conn);
        ingredientFlowPane.getChildren().clear();
        for (String ingredient : ingredients) {
            CheckBox checkBox = new CheckBox(ingredient);
            ingredientFlowPane.getChildren().add(checkBox);
        }
    }
    private void searchRecipesByIngredients() {
        List<String> selectedIngredients = ((FlowPane) ((ScrollPane) ingredientSearchVBox.getChildren().get(1)).getContent())
                .getChildren().stream()
                .filter(node -> node instanceof CheckBox)
                .map(node -> (CheckBox) node)
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .collect(Collectors.toList());

        if (selectedIngredients.isEmpty()) {
            showToast("Lütfen en az bir malzeme seçin.");
            return;
        }

        List<Map.Entry<String, Double>> matchingRecipes = db.getMatchingRecipes(conn, selectedIngredients);
        displayMatchingRecipes(matchingRecipes);
        //  ingredientSearchVBox.setVisible(false); // aradıktan sonra vbox'ı kapatmak için

        toggleUIElements(false);
    }


    private void displayMatchingRecipes(List<Map.Entry<String, Double>> matchingRecipes) {
        recipeGrid.getChildren().clear();
        int row = 0;
        int column = 0;
        for (Map.Entry<String, Double> entry : matchingRecipes) {
            String recipeName = entry.getKey();
            double matchPercentage = entry.getValue();

            VBox recipeBox = createRecipeBox(recipeName, matchPercentage);
            recipeGrid.add(recipeBox, column, row);

            column++;
            if (column == 3) {
                column = 0;
                row++;
            }
        }
    }


    private VBox createRecipeBox(String recipeName, double matchPercentage) {
        Label nameLabel = new Label(recipeName);
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label percentageLabel = new Label(String.format("Eşleşme: %.2f%%", matchPercentage));


        String color = calculateColor(matchPercentage);
        percentageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + color + ";");

        VBox recipeBox = new VBox(5, nameLabel, percentageLabel);
        recipeBox.setStyle("-fx-padding: 10; -fx-border-color: #cccccc; -fx-border-width: 1;");


        recipeBox.setOnMouseClicked(event -> {
            try {
                String query = "SELECT TarifID, Kategori, HazirlamaSuresi, Talimatlar FROM Tarifler WHERE TarifAdi = ?";
                PreparedStatement pstmt = conn.prepareStatement(query);
                pstmt.setString(1, recipeName);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    int tarifID = rs.getInt("TarifID");
                    String category = rs.getString("Kategori");
                    int preparationTime = rs.getInt("HazirlamaSuresi");
                    String instructions = rs.getString("Talimatlar");

                    displayRecipeDetails(tarifID, recipeName, category, preparationTime, instructions);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                showToast("Tarif detayları yüklenirken bir hata oluştu.");
            }
        });

        return recipeBox;
    }

    private String calculateColor(double percentage) {
        int red = (int) (255 * (100 - percentage) / 100);
        int green = (int) (255 * percentage / 100);
        return String.format("#%02X%02X00", red, green);
    }
    private void toggleUIElements(boolean visible) {
        sortComboBox.setVisible(visible);
        arama.setVisible(visible);
        treeviewButton.setVisible(visible);

    }

    //Filtre


    private String getSelectedCategories() {
        StringBuilder categories = new StringBuilder();

        TreeItem<Object> root = treeView.getRoot();
        TreeItem<Object> categoriesItem = root.getChildren().stream()
                .filter(item -> "Kategoriler".equals(item.getValue()))
                .findFirst()
                .orElse(null);

        if (categoriesItem != null) {
            for (TreeItem<Object> categoryItem : categoriesItem.getChildren()) {
                if (categoryItem.getValue() instanceof HBox) {
                    HBox hBox = (HBox) categoryItem.getValue();
                    CheckBox checkBox = (CheckBox) hBox.getChildren().get(0);
                    Label label = (Label) hBox.getChildren().get(1);

                    if (checkBox.isSelected()) {
                        if (categories.length() > 0) {
                            categories.append(", ");
                        }
                        categories.append(label.getText());
                    }
                }
            }
        }

        return categories.length() > 0 ? categories.toString() : null;
    }

    private int getSelectedIngredientCount() {
        TreeItem<Object> root = treeView.getRoot();
        TreeItem<Object> ingredientCountItem = root.getChildren().stream()
                .filter(item -> "Malzeme Sayısı".equals(item.getValue()))
                .findFirst()
                .orElse(null);

        if (ingredientCountItem != null) {
            for (TreeItem<Object> countItem : ingredientCountItem.getChildren()) {
                Object value = countItem.getValue();
                if (value instanceof HBox) {
                    HBox hBox = (HBox) value;
                    RadioButton radioButton = (RadioButton) hBox.getChildren().get(0);
                    Label label = (Label) hBox.getChildren().get(1);

                    if (radioButton.isSelected()) {
                        String countText = label.getText().split(" ")[0];
                        if ("Tümü".equals(countText)) {
                            return -1;
                        } else if ("9+".equals(countText)) {
                            return Integer.MAX_VALUE;
                        } else {
                            try {
                                return Integer.parseInt(countText);
                            } catch (NumberFormatException e) {
                                System.err.println("Geçersiz malzeme sayısı: " + countText);
                                return -1;
                            }
                        }
                    }
                }
            }
        }

        return -1;
    }

    private double[] getCostRange() {
        double min = -1;
        double max = -1;

        if (!minCostField.getText().isEmpty()) {
            try {
                min = Double.parseDouble(minCostField.getText());
            } catch (NumberFormatException e) {
            }
        }

        if (!maxCostField.getText().isEmpty()) {
            try {
                max = Double.parseDouble(maxCostField.getText());
            } catch (NumberFormatException e) {
            }
        }

        if (min == -1 && max == -1) {
            return null;
        } else {
            if (min != -1 && max == -1) {
                return new double[]{min, Double.MAX_VALUE};
            } else {
                return new double[]{min, max};
            }
        }
    }

    private void setupTreeViewListeners() {
        TreeItem<Object> root = treeView.getRoot();
        if (root != null && root.getValue().equals("Filtreler")) {
            for (TreeItem<Object> filterItem : root.getChildren()) {
                if (filterItem.getValue().equals("Kategoriler")) {
                    setupCategoryListeners(filterItem);
                } else if (filterItem.getValue().equals("Malzeme Sayısı")) {
                    setupIngredientCountListeners(filterItem);
                } else if (filterItem.getValue().equals("Maliyet")) {
                    setupCostRangeListener(filterItem);
                }
            }
        }
    }

    private void setupCostRangeListener(TreeItem<Object> costItem) {
        if (!costItem.getChildren().isEmpty()) {
            TreeItem<Object> costRangeItem = costItem.getChildren().get(0);
            if (costRangeItem.getValue() instanceof HBox) {
                HBox hBox = (HBox) costRangeItem.getValue();
                TextField minField = (TextField) hBox.getChildren().get(1);
                TextField maxField = (TextField) hBox.getChildren().get(3);

                minField.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (isValidCostInput(newValue)) {
                        loadRecipes(arama.getText());
                    }
                });

                maxField.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (isValidCostInput(newValue)) {
                        loadRecipes(arama.getText());
                    }
                });
            }
        }
    }

    private boolean isValidCostInput(String input) {
        if (input.isEmpty()) {
            return true;
        }
        try {
            Double.parseDouble(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void setupIngredientCountListeners(TreeItem<Object> ingredientCountItem) {
        ToggleGroup group = new ToggleGroup();
        for (TreeItem<Object> countItem : ingredientCountItem.getChildren()) {
            if (countItem.getValue() instanceof HBox) {
                HBox hBox = (HBox) countItem.getValue();
                RadioButton radioButton = (RadioButton) hBox.getChildren().get(0);
                radioButton.setToggleGroup(group);
                radioButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    if (newValue) {
                        loadRecipes(arama.getText());
                    }
                });
            }
        }
    }

    private void setupCategoryListeners(TreeItem<Object> categoriesItem) {
        for (TreeItem<Object> categoryItem : categoriesItem.getChildren()) {
            if (categoryItem.getValue() instanceof HBox) {
                HBox hBox = (HBox) categoryItem.getValue();
                CheckBox checkBox = (CheckBox) hBox.getChildren().get(0);
                checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    loadRecipes(arama.getText());
                });
            }
        }
    }

    private void setupCostFieldListeners() {
        minCostField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (isValidCostInput(newValue)) {
                loadRecipes(arama.getText());
            }
        });

        maxCostField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (isValidCostInput(newValue)) {
                loadRecipes(arama.getText());
            }
   });
}

}