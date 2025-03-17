package model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import messages.AssignmentMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Класс, реализующий алгоритм распределения товаров между транспортными агентами.
 * Отвечает только за логику распределения, не зависит от конкретной реализации агентов.
 */
public class DistributionAlgorithm {
    private final List<Good> goods;
    private final Map<String, AID> vehicleAgents;
    private final Logger logger;
    private final Agent myAgent;

    /**
     * Конструктор алгоритма распределения
     *
     * @param goods список товаров для распределения
     * @param vehicleAgents карта имен транспортных агентов и их идентификаторов
     * @param logger логгер для записи сообщений
     */
    public DistributionAlgorithm(Agent myAgent, List<Good> goods, Map<String, AID> vehicleAgents, Logger logger) {
        this.myAgent = myAgent;
        this.goods = new ArrayList<>(goods);
        this.vehicleAgents = vehicleAgents;
        this.logger = logger;
    }

    /**
     * Метод для распределения товаров между транспортными агентами
     *
     * @return карта назначений товаров по транспортным агентам
     */
    public Map<String, List<Good>> distributeGoods() {
        logger.info("Начало процесса распределения товаров");

        // Подсчет общего веса товаров
        int totalWeight = goods.stream().mapToInt(Good::getWeight).sum();
        logger.info("Общий вес всех товаров: " + totalWeight);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Расчет целевого веса на каждый транспорт
        int numVehicles = vehicleAgents.size();
        int targetWeightPerVehicle = totalWeight / numVehicles;
        logger.info("Целевой вес на каждый транспорт: " + targetWeightPerVehicle);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Сортировка товаров по весу (от большего к меньшему)
        List<Good> sortedGoods = new ArrayList<>(goods);
        sortedGoods.sort((g1, g2) -> Integer.compare(g2.getWeight(), g1.getWeight()));

        // Создание начального распределения
        Map<String, List<Good>> initialDistribution = new HashMap<>();
        for (String vehicle : vehicleAgents.keySet()) {
            initialDistribution.put(vehicle, new ArrayList<>());
        }

        // Первичное распределение товаров (жадный алгоритм)
        distributeGoodsGreedy(sortedGoods, initialDistribution, targetWeightPerVehicle);

        // Проверка и корректировка совместимости товаров
        return checkAndFixIncompatibilities(initialDistribution);
    }

    /**
     * Жадный алгоритм распределения товаров
     *
     * @param sortedGoods отсортированный список товаров
     * @param distribution текущее распределение
     * @param targetWeight целевой вес на каждый транспорт
     */
    private void distributeGoodsGreedy(List<Good> sortedGoods, Map<String, List<Good>> distribution, int targetWeight) {
        logger.info("Применение жадного алгоритма распределения");

        // Карта текущих весов транспортов
        Map<String, Integer> currentWeights = new HashMap<>();
        for (String vehicleName : distribution.keySet()) {
            currentWeights.put(vehicleName, 0);
        }

        // Распределение товаров
        for (Good good : sortedGoods) {
            // Находим транспорт с наименьшим текущим весом
            String targetVehicle = currentWeights.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (targetVehicle != null) {
                // Добавляем товар к транспорту
                distribution.get(targetVehicle).add(good);
                // Обновляем текущий вес
                currentWeights.put(targetVehicle, currentWeights.get(targetVehicle) + good.getWeight());
                logger.info("Товар " + good.getId() + " назначен транспорту " + targetVehicle);
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Логирование результатов
        for (Map.Entry<String, List<Good>> entry : distribution.entrySet()) {
            int weight = entry.getValue().stream().mapToInt(Good::getWeight).sum();
            logger.info("Транспорт " + entry.getKey() + " получил товары общим весом " + weight);
        }
    }

    /**
     * Проверка и исправление несовместимостей
     *
     * @param distribution начальное распределение
     * @return новое распределение без несовместимостей
     */
    private Map<String, List<Good>> checkAndFixIncompatibilities(Map<String, List<Good>> distribution) {
        logger.info("Проверка совместимости товаров...");
        Map<String, List<Good>> newDistribution = new HashMap<>();
        List<Good> incompatibleGoods = new ArrayList<>(); // Список для несовместимых товаров

        for (Map.Entry<String, List<Good>> entry : distribution.entrySet()) {
            List<Good> compatibleGoods = new ArrayList<>();
            for (Good good : entry.getValue()) {
                boolean compatible = true;
                for (Good existing : compatibleGoods) {
                    if (!good.isCompatibleWith(existing)) {
                        logger.warning("Несовместимость: " + good.getId() + " и " + existing.getId());
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    compatibleGoods.add(good);
                } else {
                    incompatibleGoods.add(good); // Добавляем несовместимый товар в список
                }

            }
            newDistribution.put(entry.getKey(), compatibleGoods);
        }
        if (!incompatibleGoods.isEmpty()) {
            handleIncompatibleGoods(incompatibleGoods, newDistribution);
        }
        return newDistribution;
    }

    /**
     * Обработка несовместимых товаров
     *
     * @param incompatibleGoods список несовместимых товаров
     * @param distribution текущее распределение
     */
    private void handleIncompatibleGoods(List<Good> incompatibleGoods, Map<String, List<Good>> distribution) {
        logger.info("Обработка несовместимых товаров: " + incompatibleGoods.size() + " товаров");

        for (Good good : incompatibleGoods) {
            // Если вес товара больше 1, можно разделить
            if (good.getWeight() > 1) {
                logger.info("Разделение товара " + good.getId() + " на части");

                // Разделяем товар на две части
                List<Good> parts = good.split(new int[]{good.getWeight() / 2, good.getWeight() - good.getWeight() / 2});

                // Пытаемся распределить части по разным транспортам
                for (Good part : parts) {
                    assignGoodToCompatibleVehicle(part, distribution);
                }
            } else {
                // Если товар нельзя разделить, пытаемся найти подходящий транспорт
                assignGoodToCompatibleVehicle(good, distribution);
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Назначение товара совместимому транспорту
     *
     * @param good товар для назначения
     * @param distribution текущее распределение
     */
    private void assignGoodToCompatibleVehicle(Good good, Map<String, List<Good>> distribution) {
        logger.info("Поиск совместимого транспорта для товара " + good.getId());
        int maxAttempts = 3;
        int attempts = 0;
        boolean assigned = false;

        while (attempts < maxAttempts && !assigned) {
            for (Map.Entry<String, List<Good>> entry : distribution.entrySet()) {
                String vehicleName = entry.getKey();
                List<Good> vehicleGoods = entry.getValue();

                boolean isCompatible = vehicleGoods.stream()
                        .allMatch(existingGood -> good.isCompatibleWith(existingGood));

                if (isCompatible) {
                    vehicleGoods.add(good);
                    logger.info("Товар " + good.getId() + " назначен транспорту " + vehicleName);
                    assigned = true;
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
            attempts++;
            if (!assigned) {
                logger.warning("Попытка " + attempts + ": не найдено совместимого транспорта для " + good.getId());
            }
        }

        if (!assigned) {
            logger.severe("Не удалось распределить товар " + good.getId() + " после " + maxAttempts + " попыток.");

            // Отправляем запрос в ModelAgent на создание новой машины
            requestNewVehicleForGood(good);
        }
    }

    /**
     * Создает сообщения для отправки транспортным агентам
     *
     * @param distribution распределение товаров
     * @return карта сообщений для каждого транспорта
     */
    public Map<String, ACLMessage> createAssignmentMessages(Map<String, List<Good>> distribution) {
        Map<String, ACLMessage> messages = new HashMap<>();
        Gson gson = new GsonBuilder().create();

        for (Map.Entry<String, List<Good>> entry : distribution.entrySet()) {
            AssignmentMessage assignment = new AssignmentMessage(
                    "ASSIGNMENT",
                    entry.getValue(),
                    entry.getKey()
            );

            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.setContent(gson.toJson(assignment)); // Явная сериализация в JSON
            messages.put(entry.getKey(), msg);
        }
        return messages;
    }
    /**
     * Запрос на создание нового транспортного агента у ModelAgent
     */
    private void requestNewVehicleForGood(Good good) {
        logger.warning("Товар " + good.getId() + " не подошел ни одному транспорту! Запрос на создание нового транспорта.");

        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.addReceiver(new AID("ModelAgent", AID.ISLOCALNAME));
        request.setContent("NEW_VEHICLE:" + good.getId());

        sendMessage(request);
    }

    /**
     * Отправка сообщения ModelAgent
     */
    private void sendMessage(ACLMessage msg) {
        myAgent.send(msg);
    }


}
