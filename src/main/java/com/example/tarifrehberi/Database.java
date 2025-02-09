package com.example.tarifrehberi;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class Database {


    // Veritabanı yolu.VT bu dosya üzerinden çalıyor
    private static final String DB_URL = "jdbc:sqlite:./identifier.sqlite";

    // Veritabanı bağlantısını oluşturma
    public static Connection connect() {
        Connection conn = null;
        try {
            // SQLite bağlantısı oluşturulusun
            conn = DriverManager.getConnection(DB_URL); // Bağlantıyı oluştur
            System.out.println("Veritabanına bağlanıldı.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
    }


    public void addRecipe(Connection conn, String tarifAdi, String kategori, int hazirlamaSuresi, String talimatlar, Map<String, Map<String, Object>> malzemeler) {
        try {
            conn.setAutoCommit(false);

            String query = "INSERT INTO Tarifler (TarifAdi, Kategori, HazirlamaSuresi, Talimatlar) VALUES (?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setString(1, tarifAdi);
            pstmt.setString(2, kategori);
            pstmt.setInt(3, hazirlamaSuresi);
            pstmt.setString(4, talimatlar);

            int affectedRows = pstmt.executeUpdate();
            System.out.println("Affected rows: " + affectedRows);

            if (affectedRows == 0) {
                throw new SQLException("Tarif oluşturulamadı, hiçbir satır etkilenmedi.");
            }

            int tarifID;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    tarifID = rs.getInt(1);
                    System.out.println("Oluşturulan TarifID: " + tarifID);
                } else {
                    throw new SQLException("Tarif oluşturuldu ancak ID alınamadı.");
                }
            }

            // Malzemeleri ekle
            for (Map.Entry<String, Map<String, Object>> entry : malzemeler.entrySet()) {
                String malzemeAdi = entry.getKey();
                Map<String, Object> malzemeDetay = entry.getValue();
                Float miktar = (Float) malzemeDetay.get("miktar");
                String birim = (String) malzemeDetay.get("birim");

                int malzemeID = getOrCreateIngredient(conn, malzemeAdi, birim);
                addRecipeIngredient(conn, tarifID, malzemeID, miktar);
            }

            conn.commit();
            System.out.println("Tarif Başarıyla Eklendi");
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            System.out.println("SQL Exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }


    // Malzeme ekleme veya mevcut malzemenin ID'sini alma
    public int getOrCreateIngredient(Connection conn, String malzemeAdi, String birim) {
        String checkQuery = "SELECT MalzemeID FROM Malzemeler WHERE MalzemeAdi = ?";
        String insertQuery = "INSERT INTO Malzemeler (MalzemeAdi, ToplamMiktar, MalzemeBirim, BirimFiyat) VALUES (?, 0, ?, 10.0)";

        try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
            // Mevcut malzemeyi kontrol et
            checkStmt.setString(1, malzemeAdi);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    int malzemeId = rs.getInt("MalzemeID");
                    System.out.println("Mevcut malzeme bulundu. MalzemeID: " + malzemeId);
                    return malzemeId;
                }
            }

            // Malzeme yoksa, yeni ekle
            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, malzemeAdi);
                insertStmt.setString(2, birim);
                int affectedRows = insertStmt.executeUpdate();

                if (affectedRows == 0) {
                    throw new SQLException("Malzeme oluşturulamadı, hiçbir satır etkilenmedi.");
                }

                try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newMalzemeId = generatedKeys.getInt(1);
                        System.out.println("Yeni malzeme eklendi. MalzemeID: " + newMalzemeId);
                        return newMalzemeId;
                    }
                }

                // getGeneratedKeys başarısız olursa, son eklenen ID'yi almak için
                try (Statement stmt = conn.createStatement();
                     ResultSet lastIdRs = stmt.executeQuery("SELECT last_insert_rowid() as last_id")) {
                    if (lastIdRs.next()) {
                        int lastId = lastIdRs.getInt("last_id");
                        System.out.println("Son eklenen malzeme ID'si manuel olarak alındı: " + lastId);
                        return lastId;
                    }
                }

                throw new SQLException("Malzeme oluşturuldu ancak ID alınamadı.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SQL Hatası: " + e.getMessage());
        }
        return -1;
    }

    public void addMalzeme(Connection conn, String malzemeAdi, String toplamMiktar, Double birimFiyat, String malzemeBirim) {
        // Varsayılan değerleri kontrol et
        if (toplamMiktar == null || toplamMiktar.isEmpty()) {
            toplamMiktar = "1"; // Varsayılan değer
        }
        if (birimFiyat == null || birimFiyat <= 0) {
            birimFiyat = 10.0; // Varsayılan değer
        }

        String insertSQL = "INSERT INTO Malzemeler (MalzemeAdi, ToplamMiktar, MalzemeBirim, BirimFiyat) VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, malzemeAdi);
            pstmt.setString(2, toplamMiktar); // Miktarı String olarak ayarladık
            pstmt.setString(3, malzemeBirim); // Birimi MalzemeBirim olarak ayarladık
            pstmt.setDouble(4, birimFiyat);

            pstmt.executeUpdate();
            System.out.println("Malzeme başarıyla eklendi.");
        } catch (SQLException e) {
            System.out.println("Malzeme eklenirken hata: " + e.getMessage());
        }
    }



    // Tarif-Malzeme ilişkisi ekleme yani bir tarifin hangi malzemeden ne kadar içerdiğini
    public void addRecipeIngredient(Connection conn, int tarifID, int malzemeID, float miktar) {
        try {
            String query = "INSERT INTO TarifMalzeme (TarifID, MalzemeID, MalzemeMiktar) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setInt(1, tarifID);
            pstmt.setInt(2, malzemeID);
            pstmt.setFloat(3, miktar);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.out.println(e);
        }
    }



    //  tarifin malzemelerini güncellemek için bir metot
    public void updateRecipeIngredients(Connection conn, int recipeId, List<Map<String, Object>> ingredients) {
        try {
            Map<String, Integer> existingIngredients = new HashMap<>();
            String selectSql = "SELECT m.MalzemeID, m.MalzemeAdi FROM TarifMalzeme tm JOIN Malzemeler m ON tm.MalzemeID = m.MalzemeID WHERE tm.TarifID = ?";
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setInt(1, recipeId);
                ResultSet rs = selectStmt.executeQuery();
                while (rs.next()) {
                    existingIngredients.put(rs.getString("MalzemeAdi"), rs.getInt("MalzemeID"));
                }
            }


            String upsertSql = "INSERT INTO TarifMalzeme (TarifID, MalzemeID, MalzemeMiktar) VALUES (?, ?, ?) ON CONFLICT (TarifID, MalzemeID) DO UPDATE SET MalzemeMiktar = ?";
            try (PreparedStatement upsertStmt = conn.prepareStatement(upsertSql)) {
                for (Map<String, Object> ingredient : ingredients) {
                    String ingredientName = (String) ingredient.get("name");
                    double amount = (double) ingredient.get("amount");
                    String unit = (String) ingredient.get("unit");

                    int ingredientId = existingIngredients.getOrDefault(ingredientName, -1);
                    if (ingredientId == -1) {
                        ingredientId = getOrCreateIngredient(conn, ingredientName, unit);
                    }

                    upsertStmt.setInt(1, recipeId);
                    upsertStmt.setInt(2, ingredientId);
                    upsertStmt.setDouble(3, amount);
                    upsertStmt.setDouble(4, amount);
                    upsertStmt.addBatch();
                }
                upsertStmt.executeBatch();
            }


            Set<String> updatedIngredientNames = ingredients.stream().map(i -> (String)i.get("name")).collect(Collectors.toSet());
            String deleteSql = "DELETE FROM TarifMalzeme WHERE TarifID = ? AND MalzemeID = ?";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                for (Map.Entry<String, Integer> entry : existingIngredients.entrySet()) {
                    if (!updatedIngredientNames.contains(entry.getKey())) {
                        deleteStmt.setInt(1, recipeId);
                        deleteStmt.setInt(2, entry.getValue());
                        deleteStmt.addBatch();
                    }
                }
                deleteStmt.executeBatch();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getIngredientDetailsForRecipe(Connection conn, int tarifID) {
        List<String> ingredientDetails = new ArrayList<>();
        String query = "SELECT m.MalzemeAdi, tm.MalzemeMiktar, m.MalzemeBirim " +
                "FROM TarifMalzeme tm " +
                "JOIN Malzemeler m ON tm.MalzemeID = m.MalzemeID " +
                "WHERE tm.TarifID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, tarifID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String malzemeAdi = rs.getString("MalzemeAdi");
                double miktar = rs.getDouble("MalzemeMiktar");
                String birim = rs.getString("MalzemeBirim");

                String detail = String.format("%s - %.2f %s", malzemeAdi, miktar, birim);
                ingredientDetails.add(detail);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return ingredientDetails;
    }


    public void updateRecipe(Connection conn, int tarifID, String yeniAdi, String yeniKategori, int yeniSure, String yeniTalimatlar) {
        try {
            String query = "UPDATE Tarifler SET TarifAdi = ?, Kategori = ?, HazirlamaSuresi = ?, Talimatlar = ? WHERE TarifID = ?";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setString(1, yeniAdi);
            pstmt.setString(2, yeniKategori);
            pstmt.setInt(3, yeniSure);
            pstmt.setString(4, yeniTalimatlar);
            pstmt.setInt(5, tarifID);
            pstmt.executeUpdate();
            System.out.println("Tarif güncellendi.");
        } catch (Exception e) {
            System.out.println(e);
        }
    }


    public void deleteRecipe(Connection conn, int tarifID) {
        try {
            String query = "DELETE FROM Tarifler WHERE TarifID = ?";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setInt(1, tarifID);
            pstmt.executeUpdate();
            System.out.println("Tarif silindi.");
        } catch (Exception e) {
            System.out.println(e);
        }
    }


    public List<String> getCategoriesFromRecipes(Connection conn) {
        List<String> categories = new ArrayList<>();
        if (conn == null) {
            System.out.println("Database connection is null");
            return categories;
        }
        String sql = "SELECT DISTINCT Kategori FROM Tarifler";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                categories.add(rs.getString("Kategori"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return categories;
    }


    // duplicate kontrolü
    public boolean isDuplicateRecipe(Connection conn, String tarifAdi) {
        try {
            String query = "SELECT COUNT(*) FROM Tarifler WHERE LOWER(TarifAdi) = LOWER(?)";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setString(1, tarifAdi);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0; // Tarif zaten varsa true döndürcek
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return false;
    }



    public void deleteIngredientFromRecipe(Connection conn, int tarifID, String malzemeAdi) {
        String query = "DELETE FROM TarifMalzeme WHERE TarifID = ? AND MalzemeID = (SELECT MalzemeID FROM Malzemeler WHERE MalzemeAdi = ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, tarifID);
            pstmt.setString(2, malzemeAdi);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

//    q

    public void addIngredientToRecipe(Connection conn, int tarifID, int malzemeID, double miktar) {
        String query = "INSERT INTO TarifMalzeme (TarifID, MalzemeID, MalzemeMiktar) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, tarifID);
            pstmt.setInt(2, malzemeID);
            pstmt.setDouble(3, miktar);
            int affectedRows = pstmt.executeUpdate();

            System.out.println("TarifMalzeme ekleme sonucu: " + affectedRows + " satır etkilendi.");

            if (affectedRows == 0) {
                throw new SQLException("Malzeme tarife eklenemedi, hiçbir satır etkilenmedi.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("TarifMalzeme ekleme hatası: " + e.getMessage());
        }
    }

    // Belirli bir tarifin malzemelerini veritabanından alma
    public List<String> getIngredientsFromDatabase(Connection conn) {
        List<String> ingredients = new ArrayList<>();
        String sql = "SELECT MalzemeAdi FROM Malzemeler"; // Tüm malzemeler

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ingredients.add(rs.getString("MalzemeAdi")); // Malzeme adını listeye eklE
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return ingredients; // Malzeme listesini döndür
    }


    public List<Map.Entry<String, Double>> getMatchingRecipes(Connection conn, List<String> userIngredients) {
        Map<String, Double> recipeMatches = new HashMap<>();
        String placeholders = String.join(",", Collections.nCopies(userIngredients.size(), "?"));
        String query = "SELECT t.TarifID, t.TarifAdi, " +
                "COUNT(DISTINCT CASE WHEN m.MalzemeAdi IN (" + placeholders + ") THEN m.MalzemeID END) as MatchingIngredients, " +
                "COUNT(DISTINCT tm.MalzemeID) as TotalIngredients " +
                "FROM Tarifler t " +
                "JOIN TarifMalzeme tm ON t.TarifID = tm.TarifID " +
                "JOIN Malzemeler m ON tm.MalzemeID = m.MalzemeID " +
                "GROUP BY t.TarifID, t.TarifAdi";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            for (int i = 0; i < userIngredients.size(); i++) {
                pstmt.setString(i + 1, userIngredients.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String recipeName = rs.getString("TarifAdi");
                    int matchingIngredients = rs.getInt("MatchingIngredients");
                    int totalIngredients = rs.getInt("TotalIngredients");
                    double matchPercentage = (double) matchingIngredients / totalIngredients * 100;
                    recipeMatches.put(recipeName, matchPercentage);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        List<Map.Entry<String, Double>> sortedMatches = new ArrayList<>(recipeMatches.entrySet());
        sortedMatches.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        return sortedMatches;
}

}