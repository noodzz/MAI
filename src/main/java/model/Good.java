package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.google.gson.annotations.Expose;


public class Good {
    @Expose
    private String id;
    @Expose
    private int weight;
    @Expose
    private List<String> incompatibilities;

    private boolean isAssigned; // Флаг для пометки распределенных товаров


    public Good(String id, int weight, List<String> incompatibilities) {
        this.id = id;
        this.weight = weight;
        this.incompatibilities = incompatibilities;
        this.isAssigned = false; // По умолчанию товар не распределен

    }

    // Геттеры и сеттеры
    public String getId() { return id; }
    public int getWeight() { return weight; }
    public boolean isAssigned() { return isAssigned; }
    public void setAssigned(boolean assigned) { isAssigned = assigned; }

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
    public String normalizeId(String id) {
        return id.replaceAll("(_part\\d+|_batch\\d+|_unit\\d+|_box\\d+)$", "");
    }
    @Override
    public String toString() {
        return "Good{" +
                "id='" + id + '\'' +
                ", weight=" + weight +
                ", incompatibilities=" + incompatibilities +
                '}';
    }
}

