package model;

import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class DistributionAlgorithm {
    private final List<Good> goods;
    private final Map<String, AID> vehicleAgents;
    private final Logger logger;
    private final Agent agent;
    private final AID serverAgent;

    /**
     * Конструктор алгоритма распределения
     *
     * @param goods список товаров для распределения
     * @param vehicleAgents карта имен транспортных агентов и их идентификаторов
     * @param logger логгер для записи сообщений
     */
    public DistributionAlgorithm(List<Good> goods, Map<String, AID> vehicleAgents, Logger logger, Agent agent, AID serverAgent) {
        this.goods = new ArrayList<>(goods);
        this.vehicleAgents = vehicleAgents;
        this.logger = logger;
        this.agent = agent;
        this.serverAgent = serverAgent;
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
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Расчет целевого веса на каждый транспорт
        int numVehicles = vehicleAgents.size();
        int targetWeightPerVehicle = totalWeight / numVehicles;
        logger.info("Целевой вес на каждый транспорт: " + targetWeightPerVehicle);
        try {
            Thread.sleep(2000);
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
        Map<String, List<Good>> finalDistribution = checkAndFixIncompatibilities(initialDistribution);
        List<Good> unassignedGoods = new ArrayList<>();
        for (Good good : goods) {
            if (!good.isAssigned() && !isGoodSplitAndAssigned(good, finalDistribution)) { // Учитываем только нераспределенные товары
                unassignedGoods.add(good);
            }
        }

        if (!unassignedGoods.isEmpty()) {
            logger.warning("Некоторые товары не удалось распределить: " + unassignedGoods);
        }

        return finalDistribution;
    }
    /**
     * Проверяет, были ли все части товара распределены.
     *
     * @param good товар для проверки
     * @param distribution текущее распределение
     * @return true, если все части товара распределены, иначе false
     */
    private boolean isGoodSplitAndAssigned(Good good, Map<String, List<Good>> distribution) {
        String normalizedGoodId = good.normalizeId(good.getId());

        for (List<Good> assignedGoods : distribution.values()) {
            for (Good assignedGood : assignedGoods) {
                if (normalizedGoodId.equals(assignedGood.normalizeId(assignedGood.getId()))) {
                    assignedGood.setAssigned(true);  // Обновляем статус
                    return true;
                }
            }
        }
        return false;
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

        ACLMessage startGreedyNotification = new ACLMessage(ACLMessage.INFORM);
        startGreedyNotification.addReceiver(serverAgent);
        startGreedyNotification.setContent("NOTIFICATION: Начало жадного алгоритма распределения.");
        agent.send(startGreedyNotification);

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

                ACLMessage assignmentNotification = new ACLMessage(ACLMessage.INFORM);
                assignmentNotification.addReceiver(serverAgent);
                assignmentNotification.setContent("NOTIFICATION: Товар " + good.getId() + " назначен транспорту " + targetVehicle);
                agent.send(assignmentNotification);
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Логирование результатов
        logTotalWeights(distribution);

        ACLMessage endGreedyNotification = new ACLMessage(ACLMessage.INFORM);
        endGreedyNotification.addReceiver(serverAgent);
        endGreedyNotification.setContent("NOTIFICATION: Жадный алгоритм распределения завершен.");
        agent.send(endGreedyNotification);
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
        List<Good> newGoods = new ArrayList<>();

        for (Good good : incompatibleGoods) {
            if (good.getWeight() > 1) {
                logger.info("Разделение товара " + good.getId() + " на части");

                List<Good> parts = good.split(new int[]{good.getWeight() / 2, good.getWeight() - good.getWeight() / 2});

                for (Good part : parts) {
                    assignGoodToCompatibleVehicle(part, distribution);
                    part.setAssigned(true);
                    newGoods.add(part);
                }
            } else {
                newGoods.add(good);
            }
        }

        goods.clear(); // Очищаем список
        goods.addAll(newGoods);
        logTotalWeights(distribution);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * Назначение товара совместимому транспорту
     *
     * @param good товар для назначения
     * @param distribution текущее распределение
     * @return true, если товар успешно назначен, иначе false
     */
    private boolean assignGoodToCompatibleVehicle(Good good, Map<String, List<Good>> distribution) {
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
                    good.setAssigned(true);
                    assigned = true;
                    try {
                        Thread.sleep(2000);
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
        }
        return assigned;
    }
    private void logTotalWeights(Map<String, List<Good>> distribution) {
        logger.info("Итоговые веса транспортных средств:");

        for (Map.Entry<String, List<Good>> entry : distribution.entrySet()) {
            int totalWeight = entry.getValue().stream().mapToInt(Good::getWeight).sum();
            logger.info("Транспорт " + entry.getKey() + " имеет общий вес товаров: " + totalWeight);

            ACLMessage vehicleNotification = new ACLMessage(ACLMessage.INFORM);
            vehicleNotification.addReceiver(serverAgent);
            vehicleNotification.setContent("NOTIFICATION: Транспорт " + entry.getKey() + " имеет общий вес товаров: " + totalWeight);
            agent.send(vehicleNotification);
        }
    }



}
