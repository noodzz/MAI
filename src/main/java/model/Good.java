package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Good {
    private String id;
    private int weight;
    private List<String> incompatibilities;

    public Good(String id, int weight, List<String> incompatibilities) {
        this.id = id;
        this.weight = weight;
        this.incompatibilities = incompatibilities;
    }

    // Геттеры и сеттеры
    public String getId() { return id; }
    public int getWeight() { return weight; }


    // Метод для разделения товара на части
    public List<Good> split(int[] partWeights) {
        if (Arrays.stream(partWeights).sum() != weight) {
            throw new IllegalArgumentException("Сумма весов частей должна быть равна весу товара");
        }

        List<Good> parts = new ArrayList<>();
        for (int i = 0; i < partWeights.length; i++) {
            parts.add(new Good(id + "_part" + i, partWeights[i], incompatibilities));
        }
        return parts;
    }

    // Проверка на совместимость с другим товаром
    public boolean isCompatibleWith(Good other) {
        String thisId = normalizeId(this.id);
        String otherId = normalizeId(other.id);

        return !this.incompatibilities.contains(otherId) && !other.incompatibilities.contains(thisId);
    }
    private String normalizeId(String id) {
        return id.replaceAll("(_part\\d+|_batch\\d+|_unit\\d+|_box\\d+)$", "");
    }
}

